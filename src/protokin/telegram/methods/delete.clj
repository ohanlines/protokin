(ns protokin.telegram.methods.delete
  (:require
    [clojure.string :as str]
    [protokin.security.session :as session]
    [protokin.system.db :as db]
    [protokin.telegram.methods.common :as common]))

(defn- parse-short-id
  [message-text]
  (let [tokens (str/split (or message-text "") #"\s+")
        short-id (some-> (second tokens) str/lower-case str/trim)]
    (when (seq short-id) short-id)))

(defn handle-delete!
  [{:keys [client ds user]} row]
  (let [short-id (parse-short-id (:message-text row))
        dek (session/active-dek (:id user))]
    (cond
      (nil? dek)
      (common/send-text! client (:chat-id row) "Session locked. Run /unlock first.")

      (nil? short-id)
      (common/send-text! client (:chat-id row) "Usage: /delete <short_id>")

      (not (re-matches #"^[a-z0-9]{5}$" short-id))
      (common/send-text! client (:chat-id row) "Invalid short_id format. Example: /delete a1b2c")

      :else
      (let [deleted (db/soft-delete-by-short-id! ds {:user-id (:id user)
                                                     :short-id short-id})]
        (if deleted
          (common/send-text! client (:chat-id row)
                             (str "Deleted expense with short_id: " (:short_id deleted)))
          (common/send-text! client (:chat-id row)
                             (str "Expense not found for short_id " short-id
                                  ". Use /review YYYY-MM-DD to check IDs first.")))))))

(def routes
  [["/delete" handle-delete!]])
