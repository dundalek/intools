(ns intools.randrin.app.events
  (:require [intools.randrin.app.db :as db]
            [intools.randrin.model :as model]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))

(reg-event-fx :randrin/init
  (fn [_ _]
    ;; TODO: use cofx for xrandr data
    {:db (-> db/default-db
             (assoc :screen (-> (model/list-screens) first)))}))

(reg-event-db :update
  (fn [db [_ & args]]
    (apply update db args)))

