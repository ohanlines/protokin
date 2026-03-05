(ns protokin.system.state)

(def readiness
  (atom {:db false
         :http false
         :bot false}))

(def metrics
  (atom {:updates-polled 0
         :updates-handled 0
         :updates-skipped-duplicate 0
         :handler-errors 0}))

(defn set-ready!
  [k v]
  (swap! readiness assoc k (boolean v)))

(defn ready?
  []
  (let [{:keys [db bot]} @readiness]
    (and db bot)))

(defn inc-metric!
  [k]
  (swap! metrics update k (fnil inc 0)))

(defn metrics-snapshot
  []
  @metrics)

(metrics-snapshot)
