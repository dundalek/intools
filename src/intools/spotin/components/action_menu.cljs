(ns intools.spotin.components.action-menu
  (:require [ink :refer [Box Text]]
            [intools.views :refer [use-selectable-list]]))

(defn action-item [{:keys [shortcut name]} {:keys [is-selected]}]
  [:> Text {:bold is-selected
            :color (when is-selected "green")
            :wrap "truncate-end"}
   (or shortcut " ") " " name])

(defn action-menu [{:keys [actions on-activate on-cancel]}]
  (let [{:keys [selected-index is-focused]}
        (use-selectable-list {:focus-id "action-menu"
                              :items actions
                              :on-activate on-activate
                              :on-cancel on-cancel
                              :auto-focus true})]
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :width 20}
     (->> actions
          (map-indexed
           (fn [idx item]
             ^{:key idx}
             [action-item item {:is-selected (= idx selected-index)}])))]))

