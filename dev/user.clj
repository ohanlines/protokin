(ns user
  (:require
    [com.stuartsierra.component :as component]
    [com.stuartsierra.component.repl :as repl]
    [protokin.system.core :as system]
    [protokin.system.migrations :as migrations]))

(repl/set-init
  (fn [& _]
    (system/system)))

(defn start []
  (repl/start))

(defn stop []
  (repl/stop))

(defn reset []
  (repl/reset))

(defn migrate []
  (migrations/migrate!))

(defn rollback []
  (migrations/rollback!))
