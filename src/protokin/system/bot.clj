(ns protokin.system.bot
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [protokin.system.state :as state]
    [protokin.telegram.core :as telegram]
    [protokin.telegram.handlers :as handlers]
    [protokin.security.session :as session]))

(defrecord BotComponent [bot-token db server poller]
  component/Lifecycle
  (start [this]
    (if (:running? this)
      this
      (let [token (or bot-token (System/getenv "TELEGRAM_BOT_TOKEN"))
            handler {:db db
                     :server server
                     :on-update handlers/handle-update}
            poller (when (seq token)
                     (telegram/start-long-polling! token handler))]
        (state/set-ready! :bot true)
        (log/info {:event :component-started :component :bot})
        (assoc this :bot-token token :running? true :poller poller))))
  (stop [this]
    (when (:running? this)
      (telegram/stop! poller)
      (session/clear-all!))
    (state/set-ready! :bot false)
    (log/info {:event :component-stopped :component :bot})
    (assoc this :running? false :poller nil)))
