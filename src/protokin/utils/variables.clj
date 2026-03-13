(ns protokin.utils.variables
  (:require
    [clojure.string :as str]))

(def built-in-categories
  {"food"          "Food & Drink"
   "groceries"     "Groceries"
   "transport"     "Transport"
   "travel"        "Travel"
   "shopping"      "Shopping"
   "bills"         "Bills"
   "utilities"     "Utilities"
   "health"        "Health"
   "beauty"        "Personal Care"
   "entertainment" "Entertainment"
   "hobbies"       "Toys & Hobbies"
   "sport"         "Sports & Fitness"
   "gifts"         "Gift & Donations"
   "family"        "Family & Kids"
   "education"     "Education"
   "savings"       "Savings & Investments"
   "tools"         "Tools & Software"
   "work"          "Work Expenses"
   "other"         "Other"})

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
