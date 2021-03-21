(ns intools.spotin.app.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :db
  (fn [db _]
    db))

(reg-sub :spotin/current-route
  (fn [db _]
    (-> db :routes peek)))

(reg-sub :spotin/album-by-id
  (fn [db [_ album-id]]
    (-> db :albums (get album-id))))
