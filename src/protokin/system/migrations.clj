(ns protokin.system.migrations
  (:require
    [protokin.system.config :as config]
    [protokin.system.db :as db]
    [ragtime.jdbc :as rag-jdbc]
    [ragtime.repl :as rag-repl]))

(defn- db-spec
  []
  (let [cfg (config/load-config)
        spec (db/normalize-db-spec (or (db/env->db-spec) (:postgres cfg)))]
    (when-not spec
      (throw (ex-info "Missing postgres db spec for migrations" {:event :missing-db-spec})))
    spec))

(defn migration-config
  []
  {:datastore (rag-jdbc/sql-database (db-spec))
   :migrations (rag-jdbc/load-resources "migrations")})

(defn migrate!
  []
  (rag-repl/migrate (migration-config)))

(defn rollback!
  []
  (rag-repl/rollback (migration-config)))
