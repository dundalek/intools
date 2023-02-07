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

(defn open-input-panel [db panel]
  (assoc db :active-input-panel panel))

(defn close-input-panel [db]
  (assoc db :active-input-panel nil))
