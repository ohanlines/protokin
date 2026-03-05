(ns protokin.system.http
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [io.pedestal.http :as http]
    [protokin.api.routes :as api-routes]
    [protokin.system.state :as state]))

(def service
  {::http/routes api-routes/routes
   ::http/type :jetty
   ::http/port 8080
   ::http/join? false})

(defn start!
  ([] (start! service))
  ([svc]
   (http/start (http/create-server svc))))

(defn stop!
  [server]
  (http/stop server))

(defrecord HttpComponent [server-instance]
  component/Lifecycle
  (start [this]
    (if server-instance
      this
      (let [srv (start!)]
        (state/set-ready! :http true)
        (log/info {:event :component-started :component :http})
        (assoc this :server-instance srv))))
  (stop [this]
    (when server-instance
      (stop! server-instance))
    (state/set-ready! :http false)
    (log/info {:event :component-stopped :component :http})
    (assoc this :server-instance nil)))
