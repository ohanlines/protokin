(ns protokin.security.session)

(defonce ^:private unlocked-sessions (atom {}))

(def default-ttl-ms
  (* 15 60 1000))

(defn unlock!
  [user-id dek]
  (swap! unlocked-sessions assoc user-id {:dek dek
                                          :expires-at (+ (System/currentTimeMillis) default-ttl-ms)})
  :ok)

(defn lock!
  [user-id]
  (swap! unlocked-sessions dissoc user-id)
  :ok)

(defn active-dek
  [user-id]
  (let [{:keys [dek expires-at]} (get @unlocked-sessions user-id)
        now (System/currentTimeMillis)]
    (cond
      (nil? dek) nil
      (<= expires-at now) (do (lock! user-id) nil)
      :else (do
              (swap! unlocked-sessions assoc user-id {:dek dek
                                                      :expires-at (+ now default-ttl-ms)})
              dek))))

(defn active-count
  []
  (let [now (System/currentTimeMillis)]
    (swap! unlocked-sessions
           (fn [sessions]
             (into {}
                   (filter (fn [[_ {:keys [expires-at]}]]
                             (> (long (or expires-at 0)) now))
                           sessions))))
    (count @unlocked-sessions)))

(defn clear-all!
  []
  (reset! unlocked-sessions {})
  :ok)
