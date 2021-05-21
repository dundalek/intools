(ns intools.spotin.app.core)

(defn current-route [db]
  (-> db :routes peek))

(defn router-navigate [db route]
  (cond-> db
    (not= (current-route db) route)
    (update :routes conj route)))

(defn router-back [db]
  (cond-> db
    (seq (:routes db)) (update :routes pop)))
