(ns intools.spotin.components.action-menu
  (:require [ink :refer [Box Text]]
            [intools.spotin.components.input-bar :refer [input-bar]]
            [intools.views :refer [use-selectable-list-controlled]]
            [react]))

(defn action-item [{:keys [shortcut name]} {:keys [is-selected]}]
  [:> Text {:bold is-selected
            :color (when is-selected "green")
            :wrap "truncate-end"}
   (or shortcut " ") " " name])

(defn action-menu [{:keys [actions is-searching on-activate on-cancel on-search-change on-search-cancel]}]
  (let [[selected-index on-select] (react/useState 0)
        {:keys [selected-index is-focused]}
        (use-selectable-list-controlled {:focus-id "action-menu"
                                         :items actions
                                         :selected-index selected-index
                                         :on-select on-select
                                         :on-activate on-activate
                                         :on-cancel on-cancel
                                         :auto-focus true})]
    (react/useEffect
     (fn []
       (on-select 0)
       js/undefined)
     #js [(count actions)])

    [:> Box {:flex-direction "column"
             :width 20}
     (when is-searching
       [:f> input-bar {:focus-id "action-menu-search-bar"
                       :label "Search actions:"
                       :on-change on-search-change
                       :on-cancel on-search-cancel}])
     [:> Box {:flex-direction "column"
              :border-style "single"
              :border-color (when is-focused "green")
              :flex-grow 1}
      (->> actions
           (map-indexed
            (fn [idx item]
              ^{:key idx}
              [action-item item {:is-selected (= idx selected-index)}])))]]))

