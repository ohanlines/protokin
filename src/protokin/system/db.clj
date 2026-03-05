(ns protokin.system.db
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [honey.sql :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [protokin.system.state :as state])
  (:import
    (java.sql SQLException)))

(def query-opts {:builder-fn rs/as-unqualified-lower-maps})

(defn normalize-db-spec
  [db-spec]
  (cond-> db-spec
    (and (nil? (:user db-spec)) (:username db-spec))
    (-> (assoc :user (:username db-spec))
        (dissoc :username))))

(defn env->db-spec
  "Build a db spec from environment variables.
  Expected: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD."
  []
  (let [host (System/getenv "DB_HOST")
        port (System/getenv "DB_PORT")
        name (System/getenv "DB_NAME")
        user (System/getenv "DB_USER")
        pass (System/getenv "DB_PASSWORD")]
    (when (and host name user)
      {:dbtype   "postgresql"
       :host     host
       :port     (if (seq port) (Long/parseLong port) 5432)
       :dbname   name
       :user     user
       :password pass})))

(defn datasource
  [db-spec]
  (jdbc/get-datasource db-spec))

(defn verify-connection!
  [db-spec]
  (let [ds (datasource db-spec)
        sql-params (sql/format {:select [[1 :ok]]})]
    (jdbc/execute-one! ds sql-params)))

(defn claim-update!
  "Return true when update_id is newly claimed, false when already processed."
  [ds update-id]
  (let [result (jdbc/execute-one!
                 ds
                 ["insert into tg_updates (update_id) values (?) on conflict do nothing returning update_id"
                  update-id]
                 query-opts)]
    (boolean result)))

(defn upsert-user!
  [ds {:keys [telegram-user-id username first-name last-name]}]
  (jdbc/execute-one!
    ds
    ["insert into users (telegram_user_id, username, first_name, last_name)
      values (?, ?, ?, ?)
      on conflict (telegram_user_id)
      do update set
        username = excluded.username,
        first_name = excluded.first_name,
        last_name = excluded.last_name,
        updated_at = now()
      returning id, telegram_user_id, username, first_name, last_name, default_currency, timezone"
     telegram-user-id username first-name last-name]
    query-opts))

(defn find-user-by-telegram-id
  [ds telegram-user-id]
  (jdbc/execute-one!
    ds
    ["select id, telegram_user_id, username, first_name, last_name, default_currency, timezone
      from users
      where telegram_user_id = ?"
     telegram-user-id]
    query-opts))

(defn find-user-crypto-profile
  [ds user-id]
  (jdbc/execute-one!
    ds
    ["select user_id,
             kdf_algo,
             (kdf_params->>'iterations')::int as kdf_iterations,
             (kdf_params->>'key_length')::int as kdf_key_length,
             kdf_salt,
             wrapped_dek,
             dek_wrap_nonce,
             dek_wrap_aad
      from user_crypto_profiles
      where user_id = ?"
     user-id]
    query-opts))

(defn create-user-crypto-profile!
  [ds {:keys [user-id kdf-algo iterations key-length kdf-salt wrapped-dek dek-wrap-nonce dek-wrap-aad]}]
  (jdbc/execute-one!
    ds
    ["insert into user_crypto_profiles
        (user_id, kdf_algo, kdf_params, kdf_salt, wrapped_dek, dek_wrap_nonce, dek_wrap_aad)
      values (?, ?, jsonb_build_object('iterations', ?, 'key_length', ?), ?, ?, ?, ?)
      on conflict (user_id) do nothing
      returning user_id"
     user-id kdf-algo iterations key-length kdf-salt wrapped-dek dek-wrap-nonce dek-wrap-aad]
    query-opts))

(defn update-user-crypto-profile!
  [ds {:keys [user-id kdf-algo iterations key-length kdf-salt wrapped-dek dek-wrap-nonce dek-wrap-aad]}]
  (jdbc/execute-one!
    ds
    ["update user_crypto_profiles
      set kdf_algo = ?,
          kdf_params = jsonb_build_object('iterations', ?, 'key_length', ?),
          kdf_salt = ?,
          wrapped_dek = ?,
          dek_wrap_nonce = ?,
          dek_wrap_aad = ?,
          updated_at = now()
      where user_id = ?
      returning user_id"
     kdf-algo iterations key-length kdf-salt wrapped-dek dek-wrap-nonce dek-wrap-aad user-id]
    query-opts))

(defn- random-short-id
  []
  (let [alphabet "abcdefghijklmnopqrstuvwxyz0123456789"]
    (apply str
           (repeatedly
             5
             #(nth alphabet (rand-int (count alphabet)))))))

(defn insert-encrypted-expense-with-short-id!
  [ds {:keys [user-id expense-date ciphertext nonce aad source-message-id source-update-id]}]
  (loop [attempt 1]
    (let [short-id (random-short-id)
          result
          (try
            (jdbc/execute-one!
              ds
              ["insert into expenses
                  (user_id, short_id, expense_date, ciphertext, nonce, aad, source_message_id, source_update_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id, user_id, short_id, expense_date"
               user-id short-id expense-date ciphertext nonce aad source-message-id source-update-id]
              query-opts)
            (catch SQLException e
              (let [duplicate-short-id?
                    (and (= "23505" (.getSQLState e))
                         (some-> (.getMessage e)
                                 (.contains "idx_expenses_user_short_id_unique")))]
                (if (and duplicate-short-id? (< attempt 10))
                  :retry
                  (throw e)))))]
      (if (= :retry result)
        (recur (inc attempt))
        result))))

(defn expenses-in-range
  [ds {:keys [user-id start-date end-date]}]
  (jdbc/execute!
    ds
    ["select id, short_id, expense_date, ciphertext, nonce, aad, created_at
      from expenses
      where user_id = ?
        and expense_date >= ?
        and expense_date < ?
        and deleted_at is null
      order by expense_date asc, created_at asc"
     user-id start-date end-date]
    query-opts))

(defn expenses-by-day
  [ds {:keys [user-id expense-date]}]
  (jdbc/execute!
    ds
    ["select id, short_id, expense_date, ciphertext, nonce, aad, created_at
      from expenses
      where user_id = ?
        and expense_date = ?
        and deleted_at is null
      order by created_at asc"
     user-id expense-date]
    query-opts))

(defn soft-delete-by-short-id!
  [ds {:keys [user-id short-id]}]
  (jdbc/execute-one!
    ds
    ["update expenses
      set deleted_at = now(),
          updated_at = now()
      where user_id = ?
        and short_id = ?
        and deleted_at is null
      returning id, short_id"
     user-id short-id]
    query-opts))

(defn delete-user-account!
  [ds {:keys [user-id]}]
  (jdbc/execute-one!
    ds
    ["delete from users where id = ? returning id"
     user-id]
    query-opts))

(defrecord DbComponent [db-spec ds]
  component/Lifecycle
  (start [this]
    (if ds
      this
      (let [spec (normalize-db-spec (or db-spec (env->db-spec)))
            ds* (when spec (datasource spec))]
        (when spec
          (verify-connection! spec))
        (state/set-ready! :db true)
        (log/info {:event :component-started :component :db})
        (assoc this :db-spec spec :ds ds*))))
  (stop [this]
    (state/set-ready! :db false)
    (log/info {:event :component-stopped :component :db})
    (assoc this :ds nil)))
