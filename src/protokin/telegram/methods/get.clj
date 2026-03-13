(ns protokin.telegram.methods.get
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [protokin.security.crypto :as crypto]
    [protokin.security.session :as session]
    [protokin.system.db :as db]
    [protokin.telegram.methods.common :as common])
  (:import
    (java.time LocalDate YearMonth)
    (java.time.format DateTimeParseException)))

(defn- decode-expense
  [dek row]
  (let [payload (crypto/decrypt-expense-payload dek (:ciphertext row) (:nonce row) (:aad row))]
    (assoc row :payload payload)))

(defn- summary-text
  [year-month total-cents count by-category]
  (let [header (str "Expense summary for " (common/month-format year-month))
        total (str "Total: " (common/format-money total-cents))
        count-line (str "Transactions: " count)
        category-lines (if (seq by-category)
                         (str/join
                           "\n"
                           (map (fn [[category cents tx-count]]
                                  (str "- " category ": " (common/format-money cents)
                                       " (" tx-count ")"))
                                by-category))
                         "- No expense data")]
    (str/join "\n" [header total count-line "By category:" category-lines])))

(defn handle-categories!
  [{:keys [client]} row]
  (common/send-text! client (:chat-id row) (common/categories-help-text)))

(defn- csv-escape
  [v]
  (let [s (str (or v ""))]
    (if (re-find #"[\",\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- rows->csv
  [decoded]
  (let [header ["short_id" "expense_date" "amount" "category" "transaction_name" "notes"]
        lines (map (fn [r]
                     (let [payload (:payload r)]
                       (str/join
                         ","
                         (map csv-escape
                              [(:short_id r)
                               (str (:expense_date r))
                               (quot (long (get payload :amount-cents 0)) 100)
                               (get payload :category-key)
                               (get payload :transaction-name)
                               (get payload :notes)]))))
                   decoded)]
    (str (str/join "," header) "\n" (str/join "\n" lines) "\n")))

(defn- decode-expenses
  [dek rows]
  (map #(decode-expense dek %) rows))

(defn- csv-file
  [user month-str]
  (let [f (io/file "tmp" (str "expenses-" (:telegram_user_id user) "-" month-str ".csv"))]
    (io/make-parents f)
    f))

(defn handle-review-month!
  [{:keys [client ds user]} arg-month row]
  (let [dek (session/active-dek (:id user))]
    (if-not dek
      (common/send-text! client (:chat-id row) "Session locked. Run /unlock first.")
      (let [zone (common/user-zone user)
            window (common/month-window zone arg-month)]
        (if-not window
          (common/send-text! client (:chat-id row) "Invalid month format. Use /review YYYY-MM.")
          (let [rows (db/expenses-in-range
                       ds
                       {:user-id (:id user)
                        :start-date (:start-date window)
                        :end-date (:end-date window)})
                decoded (map #(decode-expense dek %) rows)
                total-cents (reduce (fn [acc r]
                                      (+ acc (long (get-in r [:payload :amount-cents] 0))))
                                    0
                                    decoded)
                count-expenses (count decoded)
                by-category (->> decoded
                                 (group-by #(get-in % [:payload :category-key] "uncategorized"))
                                 (map (fn [[category items]]
                                        [category
                                         (reduce (fn [acc r]
                                                   (+ acc (long (get-in r [:payload :amount-cents] 0))))
                                                 0
                                                 items)
                                         (count items)]))
                                 (sort-by second >))]
            (common/send-text! client (:chat-id row)
                               (summary-text (:year-month window) total-cents count-expenses by-category))))))))

(defn handle-review-per-day!
  [{:keys [client ds user]} arg-day row]
  (let [dek (session/active-dek (:id user))]
    (if-not dek
      (common/send-text! client (:chat-id row) "Session locked. Run /unlock first.")
      (let [zone (common/user-zone user)
            day  (common/parse-expense-date arg-day zone)]
        (if-not day
          (common/send-text! client (:chat-id row) "Invalid day format. Use /review YYYY-MM-DD.")
          (let [rows (db/expenses-by-day ds {:user-id (:id user) :expense-date day})]
            (if (seq rows)
              (let [decoded (map #(decode-expense dek %) rows)
                    lines (map (fn [r]
                                 (str (:short_id r) " | "
                                      (common/format-money (get-in r [:payload :amount-cents] 0)) " | "
                                      (get-in r [:payload :category-key] "unknown") " | "
                                      (get-in r [:payload :transaction-name] "-")))
                               decoded)]
                (common/send-text! client (:chat-id row)
                                   (str "Expenses on " day ":\n" (str/join "\n" lines))))
              (common/send-text! client (:chat-id row)
                                 (str "No expenses found on " day ".")))))))))

(defn handle-review-csv!
  [{:keys [client ds user]} row]
  (let [tokens (str/split (or (:message-text row) "") #"\s+")
        arg-month (second tokens)
        dek (session/active-dek (:id user))]
    (if-not dek
      (common/send-text! client (:chat-id row) "Session locked. Run /unlock first.")
      (let [zone (common/user-zone user)
            window (common/month-window zone arg-month)]
          (if-not window
            (common/send-text! client (:chat-id row) "Invalid month. Use /review-csv YYYY-MM.")
            (let [rows (db/expenses-in-range
                        ds
                        {:user-id (:id user)
                         :start-date (:start-date window)
                         :end-date (:end-date window)})]
              (if (empty? rows)
                (common/send-text! client (:chat-id row)
                                   (str "No expenses found for " arg-month "."))
                (try
                  (let [decoded (decode-expenses dek rows)
                        csv-content (rows->csv decoded)
                        report-file (csv-file user (or arg-month (str (:year-month window))))]
                    (spit report-file csv-content)
                    (common/send-document!
                     client
                     (:chat-id row)
                     report-file
                     (str "Expense details " arg-month))
                    (log/info {:event :review-csv-sent
                               :user-id (:id user)
                               :month arg-month
                               :rows (count rows)}))
                  (catch Exception e
                    (log/error e {:event :review-csv-failed
                                  :user-id (:id user)
                                  :month arg-month})
                    (common/send-text! client (:chat-id row)
                                       "Failed to generate CSV. Please try again."))
                  (finally
                    (let [f (csv-file user arg-month)]
                      (when (.exists f)
                        (.delete f))))))))))))

(defn handle-review!
  [{:keys [client] :as ctx} row]
  (let [tokens (str/split (or (:message-text row) "") #"\s+")
        arg (second tokens)]
    (cond
      (nil? arg) (common/send-text! client (:chat-id row) "Select command below or you can do it yourself like this: /review YYYY-MM, /review YYYY-MM-DD"
                                    :reply_markup
                                    {:inline_keyboard
                                     [[{:text "Review this month" :callback_data (str "review-month:" (YearMonth/now))}
                                       {:text "Review today" :callback_data (str "review-day:" (LocalDate/now))}]]})
      (re-matches #"\d{4}-\d{2}$" arg) (handle-review-month! ctx arg row)
      (re-matches #"\d{4}-\d{2}-\d{2}$" arg) (handle-review-per-day! ctx arg row)
      :else (common/send-text! client (:chat-id row)
                               "Invalid review format. Use /review YYYY-MM or /review YYYY-MM-DD."))))

(defn handle-review-chart!
  [{:keys [client]} row]
  (common/send-text! client (:chat-id row)
                     "Chart reply is planned next. For now use /review for text summary."))

(def routes
  [["/categories" handle-categories!]
   ["/review" handle-review!]
   ["/reviewcsv" handle-review-csv!]
   #_["/review-chart" handle-review-chart!]])
