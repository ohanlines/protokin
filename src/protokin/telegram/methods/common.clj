(ns protokin.telegram.methods.common
  (:require
    [clojure.string :as str]
    [marksto.clj-tg-bot-api.core :as tg]
    [protokin.utils.variables :as vars])
  (:import
    (java.time LocalDate YearMonth ZoneId)
    (java.time.format DateTimeFormatter DateTimeParseException)))

(defn send-text!
  [client chat-id text & options]
  (let [opts (reduce (fn [acc [k v]]
                (assoc acc k v))
              {}
              [options])]
    (when (and client chat-id (seq text))
      (tg/make-request! client :send-message (merge {:chat-id chat-id :text text} opts)))))

(defn send-document!
  [client chat-id document caption]
  (when (and client chat-id document)
    (tg/make-request! client :send-document
                      {:chat-id chat-id
                       :caption caption
                       :document document})))


(defn user-zone
  [user-row]
  (try
    (ZoneId/of (:timezone user-row))
    (catch Exception _
      (ZoneId/of "Asia/Jakarta"))))

(defn parse-expense-date
  [date-str zone]
  (if (seq date-str)
    (try
      (LocalDate/parse date-str)
      (catch DateTimeParseException _
        nil))
    (LocalDate/now zone)))

(defn month-window
  [zone yyyy-mm]
  (let [ym (if (seq yyyy-mm)
             (try
               (YearMonth/parse yyyy-mm)
               (catch DateTimeParseException _
                 nil))
             (YearMonth/now zone))]
    (when ym
      {:year-month ym
       :start-date (.atDay ym 1)
       :end-date (.atDay (.plusMonths ym 1) 1)})))

(defn format-money
  [amount-cents]
  (format "Rp%,d" (quot (long (or amount-cents 0)) 100)))

(defn categories-help-text
  []
  (str "Available categories:\n" (vars/category-list-text)))

(defn month-format
  [year-month]
  (.format year-month (DateTimeFormatter/ofPattern "yyyy-MM")))
