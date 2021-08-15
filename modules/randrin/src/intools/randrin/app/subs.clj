(ns intools.randrin.app.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :randrin/displays
  (fn [db _]
    (-> db :screen :displays)))

(reg-sub :items
  (fn [db _]
    (:items db)))

(reg-sub :name
  (fn [db _]
    (:name db)))

(reg-sub :list-state
  (fn [db _]
    (:state db)))

(reg-sub :randrin/display-list-state
  (fn [db _]
    (:display-list-state db)))

