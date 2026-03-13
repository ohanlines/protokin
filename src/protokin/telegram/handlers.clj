(ns protokin.telegram.handlers
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [marksto.clj-tg-bot-api.core :as tg]
    [protokin.system.db :as db]
    [protokin.system.state :as state]
    [protokin.telegram.methods.add :as add-method]
    [protokin.telegram.methods.common :as common]
    [protokin.telegram.methods.delete :as delete-method]
    [protokin.telegram.methods.get :as gm]
    [protokin.telegram.methods.security :as security-method]
    [protokin.telegram.methods.update :as update-method]))

(defn- update->event
  [update]
  (let [message (:message update)
        callback (:callback_query update)
        callback-message (:message callback)
        callback-from (:from callback)
        callback-chat (:chat callback-message)]
    (cond
      (some? callback)
      {:event-type :callback
       :raw update
       :update-id (:update_id update)
       :chat-id (:id callback-chat)
       :chat-type (:type callback-chat)
       :message-id (:message_id callback-message)
       :user-id (:id callback-from)
       :username (:username callback-from)
       :first-name (:first_name callback-from)
       :last-name (:last_name callback-from)
       :callback-id (:id callback)
       :callback-data (:data callback)}

      (some? message)
      {:event-type :message
       :raw update
       :update-id (:update_id update)
       :chat-id (get-in update [:message :chat :id])
       :chat-type (get-in update [:message :chat :type])
       :message-id (get-in update [:message :message_id])
       :user-id (get-in update [:message :from :id])
       :username (get-in update [:message :from :username])
       :first-name (get-in update [:message :from :first_name])
       :last-name (get-in update [:message :from :last_name])
       :message-text (get-in update [:message :text])}

      :else
      {:event-type :unsupported
       :raw update
       :update-id (:update_id update)})))

(defn- command
  [text]
  (when (and text (str/starts-with? text "/"))
    (first (str/split text #"\s+"))))

(defn- handle-start!
  [{:keys [client]} row]
  (common/send-text! client (:chat-id row)
                     (str "Welcome.\n"
                          "1) Set passphrase: /setpass <your-passphrase>\n"
                          "2) Unlock each session: /unlock <your-passphrase>\n"
                          "3) Add expense: /add 15000 food lunch")))

(def route-pairs
  (vec
    (concat
      [["/start" handle-start!]]
      security-method/routes
      gm/routes
      add-method/routes
      update-method/routes
      delete-method/routes)))

(defn- assert-no-duplicate-routes!
  [pairs]
  (let [cmds (map first pairs)
        freq (frequencies cmds)
        duplicates (->> freq (filter (fn [[_ c]] (> c 1))) (map first) sort vec)]
    (when (seq duplicates)
      (throw (ex-info "Duplicate telegram routes" {:duplicates duplicates})))
    pairs))

(def routes
  (assert-no-duplicate-routes! route-pairs))

(def route-map
  (into {} routes))

(def user-required-commands
  #{"/setpass" "/unlock" "/lock" "/changepass" "/deleteaccount"
    "/add" "/review" "/reviewcsv" "/delete"})

(defn- handle-command!
  [{:keys [client ds user] :as ctx} row]
  (let [cmd (command (:message-text row))
        handler (get route-map cmd)]
    (cond
      (nil? cmd) nil

      (nil? handler)
      (common/send-text! client (:chat-id row)
                         "Unknown command. Use /start for help.")

      (and (contains? user-required-commands cmd) (nil? user))
      (common/send-text! client (:chat-id row) "Please run /start first.")

      :else
      (handler {:client client :ds ds :user user} row))))

(defn- parse-callback-data
  [data]
  (when (seq data)
    (let [[action arg] (str/split data #":" 2)]
      {:action action
       :arg arg})))

(defn- handle-callback!
  [{:keys [client ds user] :as ctx} row]
  (let [data (:callback-data row)
        {:keys [action arg]} (parse-callback-data data)]
    (log/info {:action action :arg arg})
    (cond
      (nil? data) nil

      (nil? user)
      (when (:chat-id row)
        (common/send-text! client (:chat-id row) "Please run /start first."))

      (= action "review-month")
      (gm/handle-review-month! ctx arg row)

      (= action "review-day")
      (gm/handle-review-per-day! ctx nil row)

      :else
      (when (:chat-id row)
        (common/send-text! client (:chat-id row) "Unknown action.")))))

(defn handle-update
  "Process one Telegram update. Returns a normalized map for logging."
  [{:keys [client db] :as _ctx} update]
  (let [ds        (:ds db)
        row       (update->event update)
        update-id (:update-id row)]
    (when-not ds
      (throw (ex-info "Missing datasource in handler context" {:event :missing-ds})))
    (when update-id
      (if-not (db/claim-update! ds update-id)
        (do
          (state/inc-metric! :updates-skipped-duplicate)
          (log/info {:event :duplicate-update :update-id update-id})
          nil)
        (do
          (state/inc-metric! :updates-handled)
          (case (:event-type row)
            :message  (let [cmd  (command (:message-text row))
                            user (if (= "/start" cmd)
                                   (db/upsert-user!
                                    ds
                                    {:telegram-user-id (:user-id row)
                                     :username         (:username row)
                                     :first-name       (:first-name row)
                                     :last-name        (:last-name row)})
                                   (db/find-user-by-telegram-id ds (:user-id row)))]
                        (when (and (= "private" (:chat-type row)) (:message-text row))
                          (handle-command! {:client client :ds ds :user user} row))
                        row)
            :callback (let [user (db/find-user-by-telegram-id ds (:user-id row))]
                        (try
                          (handle-callback! {:client client :ds ds :user user} row)
                          (catch Exception e
                            (log/error e {:event       :callback-handler-error
                                          :update-id   update-id
                                          :callback-id (:callback-id row)}))
                          (finally
                            (when (:callback-id row)
                              (tg/make-request! client :answer-callback-query
                                                {:callback-query-id (:callback-id row)
                                                 :text              "Done"}))))
                        row)
            (do (log/info {:event     :unsupported-update
                         :update-id update-id})
              row)))))))
