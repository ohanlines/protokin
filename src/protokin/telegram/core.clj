(ns protokin.telegram.core
  (:require
    [clojure.tools.logging :as log]
    [marksto.clj-tg-bot-api.core :as tg]
    [protokin.system.state :as state]))

(defn start-long-polling!
  "Start a simple long-polling loop using getUpdates.
  `handler` can be a function or a map with :on-update."
  [bot-token handler]
  (let [client (tg/->client {:bot-token bot-token})
        running? (atom true)
        handler-ctx (if (map? handler) (dissoc handler :on-update) {})
        on-update (cond
                    (fn? handler) handler
                    (map? handler) (or (:on-update handler)
                                       (fn [_ctx update]
                                         (println "update:" (:update_id update))))
                    :else (fn [_ctx update]
                            (println "update:" (:update_id update))))
        poller (future
                 (loop [offset 0]
                   (when @running?
                     (let [next-offset
                           (try
                             (let [updates (or (tg/make-request! client :get-updates
                                                                {:offset offset
                                                                 :timeout 30})
                                               [])
                                   last-id (when (seq updates)
                                             (apply max (map :update_id updates)))]
                               (doseq [update updates]
                                 (state/inc-metric! :updates-polled)
                                 (try
                                   (let [result (on-update (assoc handler-ctx :client client) update)]
                                     (when (some? result)
                                       (log/info {:event :update-handled
                                                  :chat-id (get-in update [:message :chat :id])
                                                  :update-id (:update_id update)})))
                                   (catch Exception e
                                     (state/inc-metric! :handler-errors)
                                     (log/error e {:event :update-handler-error
                                                   :update-id (:update_id update)}))))
                               (if last-id (inc last-id) offset))
                             (catch Exception e
                               (state/inc-metric! :handler-errors)
                               (log/error e {:event :polling-loop-error})
                               (Thread/sleep 1000)
                               offset))]
                       (recur next-offset)))))]
    {:client client
     :running? running?
     :poller poller}))

(defn send-message!
  "Stub message sender. Replace with clj-tg-bot-api sendMessage."
  [bot-token chat-id text]
  (let [client (tg/->client {:bot-token bot-token})]
    (tg/make-request! client :send-message {:chat-id chat-id
                                            :text text})))

(defn stop!
  "Stop polling if you started it. Keep a reference to the poller and stop it here."
  [poller]
  (when (map? poller)
    (reset! (:running? poller) false)
    (future-cancel (:poller poller)))
  :ok)
