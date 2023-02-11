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

(defn open-confirmation-modal [db opts]
  (assoc db :confirmation-modal opts))

(defn close-confirmation-modal [db]
  (assoc db :confirmation-modal nil))

(defn open-action-menu [db actions]
  (assoc db :actions actions))

(defn close-action-menu [db]
  (assoc db
         :actions nil
         :actions-search-query nil))
