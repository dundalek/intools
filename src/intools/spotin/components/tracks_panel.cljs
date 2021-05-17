(ns intools.spotin.components.tracks-panel
  (:require [clojure.string :as str]
            [ink :refer [Box Spacer Text]]
            [intools.hooks :as hooks]
            [intools.spotin.format :refer [format-duration]]
            [intools.views :refer [use-selectable-list]]
            [react]))

(defn track-item [{:keys [track]} {:keys [is-selected]}]
  (let [{:keys [name duration_ms album artists]} track]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} (str/join ", " (map :name artists))]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} (:name album)]]
     [:> Box {:width 6 :justify-content "flex-end"}
      [:> Text {:bold is-selected :color (when is-selected "green")} (format-duration duration_ms)]]]))

(defn playlist-header [{:keys [playlist]}]
  (let [{:keys [name description owner tracks]} playlist]
    [:> Box {:flex-direction "column"
             :margin-bottom 1}
     [:> Box
      [:> Text {:dim-color true} "playlist    "]
      [:> Text name]
      [:> Spacer]
      [:> Text {:dim-color true} "by "]
      [:> Text (:display_name owner)]
      [:> Spacer]
      [:> Text (:total tracks) " songs" #_", 3hr 42 min"]]
     (when-not (str/blank? description)
       [:> Box
        [:> Text {:dim-color true} "description "]
        [:> Text description]])]))

(defn tracks-panel [{:keys [playlist tracks on-activate on-menu]}]
  (let [[selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)]
                      (set-selected (op selected id))))
        {:keys [selected-index is-focused]} (use-selectable-list {:items tracks
                                                                  :on-toggle on-toggle
                                                                  :on-activate on-activate
                                                                  :auto-focus true})
        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (- (or (:height viewport) (count tracks))
                           4
                           (if (str/blank? (:description playlist)) 0 1))
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-tracks (->> tracks (drop offset) (take viewport-height))]
    (ink/useInput
     (fn [input _key]
       (when is-focused
         (case input
           "x" (when on-menu (on-menu (nth tracks selected-index)))
           nil))))
    [:> Box {:flex-direction "column"
             :flex-grow 1
             :border-style "single"
             :border-color (when is-focused "green")
             :padding-x 1
             :ref box-ref}
     #_[:> Box [:> Text selected-index " of " (count tracks) " offset=" offset " viewport=" viewport-height]]
     (when playlist [playlist-header {:playlist playlist}])
     (->> displayed-tracks
          (map-indexed
           (fn [idx {:keys [id] :as item}]
             ^{:key idx}
             [track-item item {:is-selected (= idx (- selected-index offset))
                               :is-active (contains? selected id)}])))]))

