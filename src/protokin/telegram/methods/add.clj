(ns protokin.telegram.methods.add
  (:require
    [clojure.string :as str]
    [protokin.security.crypto :as crypto]
    [protokin.security.session :as session]
    [protokin.system.db :as db]
    [protokin.telegram.methods.common :as common]
    [protokin.utils.variables :as vars]))

(defn- parse-amount
  [raw]
  (when raw
    (let [s (-> raw
                str/lower-case
                (str/replace #"rp" "")
                (str/replace #"[,_\s]" ""))
          digits (re-matches #"\d+" s)]
      (when digits
        (Long/parseLong digits)))))

(defn- parse-options
  [parts]
  (reduce
    (fn [acc raw-part]
      (let [part (str/trim raw-part)]
        (if-let [[_ k v] (re-matches #"(?i)^(note|date)\s*=\s*(.+)$" part)]
          (assoc acc (keyword (str/lower-case k)) (str/trim v))
          acc)))
    {}
    parts))

(defn- parse-add-command
  [message-text]
  (let [without-command (str/trim (str/replace-first message-text #"^/add\s+" ""))
        sections (map str/trim (str/split without-command #";"))
        main-part (first sections)
        options (parse-options (rest sections))
        main-tokens (str/split main-part #"\s+" 3)
        [raw-amount raw-category transaction-name] main-tokens]
    {:amount (parse-amount raw-amount)
     :category-key (some-> raw-category str/lower-case str/trim)
     :transaction-name (some-> transaction-name str/trim)
     :notes (:note options)
     :date (:date options)}))

(defn handle-add!
  [{:keys [client ds user]} row]
  (let [{:keys [amount category-key transaction-name notes date]} (parse-add-command (:message-text row))
        zone (common/user-zone user)
        expense-date (common/parse-expense-date date zone)
        dek (session/active-dek (:id user))]
    (cond
      (nil? dek)
      (common/send-text! client (:chat-id row) "Session locked. Run /unlock first.")

      (not amount)
      (common/send-text! client (:chat-id row) "Invalid amount. Example: /add 15000 food lunch")

      (<= amount 0)
      (common/send-text! client (:chat-id row) "Amount must be greater than 0.")

      (not (seq transaction-name))
      (common/send-text! client (:chat-id row) "Missing transaction name. Example: /add 15000 food lunch")

      (not (vars/valid-category? category-key))
      (common/send-text! client (:chat-id row)
                         (str "Unknown category: " category-key "\n" (common/categories-help-text)))

      (nil? expense-date)
      (common/send-text! client (:chat-id row) "Invalid date format. Use YYYY-MM-DD.")

      :else
      (let [amount-cents (* amount 100)
            payload {:amount-cents amount-cents
                     :category-key category-key
                     :transaction-name transaction-name
                     :notes notes}
            encrypted (crypto/encrypt-expense-payload dek payload nil)
            saved (db/insert-encrypted-expense-with-short-id!
                    ds
                    {:user-id (:id user)
                     :expense-date expense-date
                     :ciphertext (:ciphertext encrypted)
                     :nonce (:nonce encrypted)
                     :aad (:aad encrypted)
                     :source-message-id (:message-id row)
                     :source-update-id (:update-id row)})]
        (common/send-text! client (:chat-id row)
                           (str "Saved: " (:short_id saved) " | "
                                transaction-name " | "
                                (common/format-money amount-cents) " | "
                                category-key " | "
                                expense-date))))))

(def routes
  [["/add" handle-add!]])
