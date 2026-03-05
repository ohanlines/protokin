(ns protokin.utils.variables
  (:require
    [clojure.string :as str]))

(def built-in-categories
  {"food" "Food & Drink"
   "transport" "Transport"
   "shopping" "Shopping"
   "bills" "Bills"
   "health" "Health"
   "entertainment" "Entertainment"
   "education" "Education"
   "other" "Other"})

(defn category-label
  [category-key]
  (get built-in-categories category-key))

(defn valid-category?
  [category-key]
  (contains? built-in-categories category-key))

(defn category-list-text
  []
  (->> built-in-categories
       (sort-by key)
       (map (fn [[k v]] (str "- " k " (" v ")")))
       (str/join "\n")))
