(ns protokin.core
  (:gen-class)
  (:require
    [com.stuartsierra.component :as component]
    [protokin.system.core :as system]))

(defn -main
  [& _args]
  (let [sys (component/start (system/system))]
    (println "Protokin started.")
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. #(component/stop sys)))))
