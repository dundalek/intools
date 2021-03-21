(ns intools.spotin.app.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]
            [intools.spotin.app.db :as db]))

(reg-event-db :intitialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db :set-playlists
  (fn [db [_ playlists]]
    (assoc db
           :playlist-order (map :id playlists)
           :playlists (->> playlists
                           (reduce (fn [m {:keys [id] :as item}]
                                      (assoc m id item))
                                   {})))))

(reg-event-db :set-playlist-tracks
  (fn [db [_ playlist-id tracks]]
    (assoc-in db [:playlist-tracks playlist-id] tracks)))

(reg-event-db :set-selected-playlist
  (fn [db [_ playlist-id]]
    (assoc db :selected-playlist playlist-id)))

(reg-event-db :open-action-menu
  (fn [db [_ menu]]
    (assoc db :actions menu)))

(reg-event-db :close-action-menu
  (fn [db _]
    (assoc db :actions nil)))

(reg-event-db :open-input-panel
  (fn [db [_ data]]
    (assoc db :active-input-panel data)))

(reg-event-db :close-input-panel
  (fn [db _]
    (assoc db :active-input-panel nil)))

(reg-event-db :playlist-rename
  (fn [db [_ arg]]
    (assoc db :active-input-panel {:type :playlist-rename
                                      :arg arg})))

(reg-event-db :playlist-edit-description
  (fn [db [_ arg]]
    (assoc db :active-input-panel {:type :playlist-edit-description
                                   :arg arg})))

(reg-event-fx :run-action
  (fn [_ [_ {:keys [id arg]}]]
    {id arg}))
