(ns protokin.system.core
  (:require
    [com.stuartsierra.component :as component]
    [protokin.system.bot :as bot]
    [protokin.system.config :as config]
    [protokin.system.db :as db]
    [protokin.system.http :as http]))

(defn system
  "Build the system map."
  []
  (let [cfg (config/load-config)
        env-bot-token (System/getenv "TELEGRAM_BOT_TOKEN")
        env-db-spec (db/env->db-spec)
        db-spec (or env-db-spec (:postgres cfg))
        bot-token (or env-bot-token (:telegram-bot-token cfg))]
    (component/system-map
      :db (db/map->DbComponent {:db-spec db-spec})
      :http (component/using (http/map->HttpComponent {}) [])
      :bot (component/using (bot/map->BotComponent {:bot-token bot-token}) [:db :http]))))
