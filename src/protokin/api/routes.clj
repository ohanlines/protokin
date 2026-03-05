(ns protokin.api.routes
  (:require
    [io.pedestal.http.route :as route]
    [protokin.api.response :as response]
    [protokin.security.session :as session]
    [protokin.system.state :as state]))

(defn health
  [_request]
  (response/json-response 200 {:status "ok"}))

(defn ready
  [_request]
  (let [ready? (state/ready?)]
    (response/json-response (if ready? 200 503)
                            {:ready ready?})))

(defn metrics
  [_request]
  (response/json-response 200
                          (assoc (state/metrics-snapshot)
                                 :active-unlocked-sessions (session/active-count))))

(defn- authorized-admin?
  [request]
  (let [expected (System/getenv "ADMIN_TOKEN")
        provided (get-in request [:headers "x-admin-token"])]
    (and (seq expected) (= expected provided))))

(defn internal-unlocks
  [request]
  (if-not (authorized-admin? request)
    (response/json-response 401 {:error "unauthorized"})
    (response/json-response 200
                            {:unlocked_users_count (session/active-count)
                             :ts (str (java.time.Instant/now))})))

(def routes
  (route/expand-routes
    #{["/health" :get health :route-name :health]
      ["/ready" :get ready :route-name :ready]
      ["/metrics" :get metrics :route-name :metrics]
      ["/internal/unlocks" :get internal-unlocks :route-name :internal-unlocks]}))
