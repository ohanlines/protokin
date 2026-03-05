(ns protokin.api.response
  (:require
    [cheshire.core :as json]))

(def json-content-type
  "application/json; charset=utf-8")

(defn json-response
  [status payload]
  (try
    {:status status
     :headers {"Content-Type" json-content-type}
     :body (json/generate-string payload)}
    (catch Exception _
      {:status 500
       :headers {"Content-Type" json-content-type}
       :body (json/generate-string {:error "serialization-failed"})})))

