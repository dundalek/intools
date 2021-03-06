(ns intools.spotin.components.action-menu
  (:require [ink :refer [Box Text]]
            [intools.views :refer [uncontrolled-text-input use-selectable-list-controlled]]
            [react]))

(defn action-item [{:keys [shortcut name]} {:keys [is-selected]}]
  [:> Box
   [:> Text {:bold is-selected
             :color (when is-selected "green")
             :wrap "truncate-end"}
    (or shortcut " ") " " name]])

(defn action-menu [{:keys [actions is-searching item-component header width
                           on-activate on-cancel on-search-change on-search-cancel]
                    :or {item-component action-item}}]
  (let [[selected-index on-select] (react/useState 0)
        {:keys [is-focused]} (use-selectable-list-controlled {:focus-id "action-menu"
                                                              :auto-focus true
                                                              :items actions
                                                              :selected-index selected-index
                                                              :on-select on-select
                                                              :on-activate on-activate
                                                              :on-cancel #(if is-searching
                                                                            (on-search-cancel)
                                                                            (on-cancel))})]
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :width width}
     header
     (when is-searching
       [:> Box {:flex-direction "column"
                :height 3}
        [:> Text {:wrap "truncate-end"}
         "Search actions:"]
        [:f> uncontrolled-text-input {:on-change on-search-change
                                      :focus is-focused}]])
     (->> actions
          (map-indexed
           (fn [idx item]
             ^{:key idx}
             [item-component item {:is-selected (= idx selected-index)}])))]))

