(ns protokin.telegram.handler-example)

(defn extract-message-event
  "Return sender + message map from a Telegram update.
  Returns nil when the update does not contain a regular message."
  [update]
  (let [message (get update :message)
        from (get message :from)
        text (get message :text)]
    (when (and from text)
      {:from {:id (get from :id)
              :username (get from :username)
              :first-name (get from :first_name)
              :last-name (get from :last_name)}
       :message text})))

(defn handle-update
  "Example handler function to be used as :on-update.
  It catches incoming message updates and returns normalized data."
  [_ctx update]
  (extract-message-event update))
