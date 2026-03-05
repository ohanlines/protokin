(ns protokin.telegram.methods.security
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [protokin.security.crypto :as crypto]
    [protokin.security.session :as session]
    [protokin.system.db :as db]
    [protokin.telegram.methods.common :as common]))

(defn- rest-arg
  [message-text cmd]
  (let [prefix (str cmd " ")]
    (when (str/starts-with? (or message-text "") prefix)
      (some-> message-text (subs (count prefix)) str/trim not-empty))))

(defn- min-passphrase?
  [passphrase]
  (>= (count (or passphrase "")) 8))

(defn- wrap-aad
  [user-id]
  (.getBytes (str "user:" user-id) "UTF-8"))

(defn- require-crypto-profile
  [ds user]
  (db/find-user-crypto-profile ds (:id user)))

(defn handle-set-passphrase!
  [{:keys [client ds user]} row]
  (let [chat-id (:chat-id row)
        passphrase (rest-arg (:message-text row) "/set-passphrase")
        profile (require-crypto-profile ds user)]
    (cond
      (nil? passphrase)
      (common/send-text! client chat-id "Usage: /set-passphrase <min-8-char-passphrase>")

      (not (min-passphrase? passphrase))
      (common/send-text! client chat-id "Passphrase must be at least 8 characters.")

      profile
      (common/send-text! client chat-id "Passphrase already set. Use /unlock or /change-passphrase.")

      :else
      (let [{:keys [algo iterations key-length]} crypto/default-kdf
            kdf-salt (crypto/random-bytes 16)
            dek (crypto/generate-dek)
            kek (crypto/derive-kek passphrase kdf-salt {:iterations iterations
                                                        :key-length key-length})
            aad (wrap-aad (:id user))
            {:keys [ciphertext nonce]} (crypto/wrap-dek kek dek aad)
            created (db/create-user-crypto-profile!
                      ds
                      {:user-id (:id user)
                       :kdf-algo algo
                       :iterations iterations
                       :key-length key-length
                       :kdf-salt kdf-salt
                       :wrapped-dek ciphertext
                       :dek-wrap-nonce nonce
                       :dek-wrap-aad aad})]
        (if created
          (do
            (session/unlock! (:id user) dek)
            (common/send-text! client chat-id "Passphrase set. Session unlocked for 15 minutes."))
          (common/send-text! client chat-id "Passphrase is already configured."))))))

(defn handle-unlock!
  [{:keys [client ds user]} row]
  (let [chat-id (:chat-id row)
        passphrase (rest-arg (:message-text row) "/unlock")
        profile (require-crypto-profile ds user)]
    (cond
      (nil? passphrase)
      (common/send-text! client chat-id "Usage: /unlock <your-passphrase>")

      (nil? profile)
      (common/send-text! client chat-id "No passphrase yet. Run /set-passphrase first.")

      :else
      (try
        (let [kek (crypto/derive-kek passphrase
                                     (:kdf_salt profile)
                                     {:iterations (:kdf_iterations profile)
                                      :key-length (:kdf_key_length profile)})
              dek (crypto/unwrap-dek kek
                                     (:wrapped_dek profile)
                                     (:dek_wrap_nonce profile)
                                     (:dek_wrap_aad profile))]
          (session/unlock! (:id user) dek)
          (common/send-text! client chat-id "Unlocked. You can use /add, /review, and /delete now."))
        (catch Exception e
          (log/info e {:event :unlock-failed :user-id (:id user)})
          (common/send-text! client chat-id "Wrong passphrase."))))))

(defn handle-lock!
  [{:keys [client user]} row]
  (session/lock! (:id user))
  (common/send-text! client (:chat-id row) "Locked."))

(defn- parse-change-passphrase
  [message-text]
  (let [parts (str/split (or message-text "") #"\s+" 3)
        old-pass (second parts)
        new-pass (nth parts 2 nil)]
    {:old old-pass :new new-pass}))

(defn handle-change-passphrase!
  [{:keys [client ds user]} row]
  (let [chat-id (:chat-id row)
        {:keys [old new]} (parse-change-passphrase (:message-text row))
        profile (require-crypto-profile ds user)]
    (cond
      (or (nil? old) (nil? new))
      (common/send-text! client chat-id "Usage: /change-passphrase <old> <new>")

      (not (min-passphrase? new))
      (common/send-text! client chat-id "New passphrase must be at least 8 characters.")

      (nil? profile)
      (common/send-text! client chat-id "No passphrase yet. Run /set-passphrase first.")

      :else
      (try
        (let [old-kek (crypto/derive-kek old
                                         (:kdf_salt profile)
                                         {:iterations (:kdf_iterations profile)
                                          :key-length (:kdf_key_length profile)})
              dek (crypto/unwrap-dek old-kek
                                     (:wrapped_dek profile)
                                     (:dek_wrap_nonce profile)
                                     (:dek_wrap_aad profile))
              {:keys [algo iterations key-length]} crypto/default-kdf
              new-salt (crypto/random-bytes 16)
              new-kek (crypto/derive-kek new new-salt {:iterations iterations
                                                       :key-length key-length})
              aad (wrap-aad (:id user))
              {:keys [ciphertext nonce]} (crypto/wrap-dek new-kek dek aad)]
          (db/update-user-crypto-profile!
            ds
            {:user-id (:id user)
             :kdf-algo algo
             :iterations iterations
             :key-length key-length
             :kdf-salt new-salt
             :wrapped-dek ciphertext
             :dek-wrap-nonce nonce
             :dek-wrap-aad aad})
          (session/unlock! (:id user) dek)
          (common/send-text! client chat-id "Passphrase updated."))
        (catch Exception e
          (log/info e {:event :change-passphrase-failed :user-id (:id user)})
          (common/send-text! client chat-id "Failed to change passphrase. Check your old passphrase."))))))

(defn handle-delete-account!
  [{:keys [client ds user]} row]
  (let [parts (str/split (or (:message-text row) "") #"\s+" 2)
        confirm (second parts)]
    (if (= "CONFIRM" confirm)
      (let [deleted (db/delete-user-account! ds {:user-id (:id user)})]
        (session/lock! (:id user))
        (if deleted
          (common/send-text! client (:chat-id row) "Account deleted with all encrypted expense data.")
          (common/send-text! client (:chat-id row) "Account was not found.")))
      (common/send-text! client (:chat-id row) "Usage: /delete-account CONFIRM"))))

(def routes
  [["/set-passphrase" handle-set-passphrase!]
   ["/unlock" handle-unlock!]
   ["/lock" handle-lock!]
   ["/change-passphrase" handle-change-passphrase!]
   ["/delete-account" handle-delete-account!]])
