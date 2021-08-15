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

(reg-sub :randrin/focus-manager
  (fn [db _]
    (:focus-manager db)))

(reg-sub :randrin/focused-id
  :<- [:randrin/focus-manager]
  (fn [focus-manager _]
    (or (:active-id focus-manager)
        (first (:focusables focus-manager)))))

(reg-sub :randrin/is-focused
  :<- [:randrin/focused-id]
  (fn [focused-id [_ id]]
    (= focused-id id)))

