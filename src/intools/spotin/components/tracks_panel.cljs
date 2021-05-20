(ns intools.spotin.components.tracks-panel
  (:require [clojure.string :as str]
            [ink :refer [Box Spacer Text]]
            [intools.hooks :as hooks]
            [intools.spotin.format :refer [format-album-release-year format-duration]]
            [intools.views :refer [scroll-status uncontrolled-text-input use-selectable-list]]
            [react]))

(defn playlist-track-item [track {:keys [is-selected is-highlighted]}]
  (let [{:keys [name duration_ms album artists]} track
        color (when (or is-selected is-highlighted) "green")
        text-style {:bold is-selected :color color}]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") name]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") (str/join ", " (map :name artists))]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") (:name album)]]
     [:> Box {:width 6 :justify-content "flex-end"}
      [:> Text text-style (format-duration duration_ms)]]]))

(defn album-track-item [track {:keys [is-selected is-highlighted]}]
  (let [{:keys [name duration_ms artists]} track
        color (when (or is-selected is-highlighted) "green")
        text-style {:bold is-selected :color color}]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") name]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") (str/join ", " (map :name artists))]]
     [:> Box {:min-width 6 :justify-content "flex-end"}
      [:> Text text-style (format-duration duration_ms)]]]))

(defn playlist-header [{:keys [playlist tracks]}]
  (let [{:keys [name description owner]} playlist]
    [:> Box {:flex-direction "column"
             :margin-bottom 1}
     [:> Box
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:dim-color true} "playlist    "]
       [:> Text {:wrap "truncate-end"} name]]
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:dim-color true} " by "]
       [:> Text {:wrap "truncate-end"} (:display_name owner)]]
      [:> Box
       [:> Text (count tracks)]
       [:> Text {:dim-color true} " songs" #_", 3hr 42 min"]]]
     (when-not (str/blank? description)
       [:> Box
        [:> Text {:dim-color true} "description "]
        [:> Text {:wrap "truncate-end"} description]])]))

(defn album-header [{:keys [album]}]
  (let [{:keys [name artists release_date total_tracks]} album]
    [:> Box {:flex-direction "column"
             :margin-bottom 1}
     [:> Box
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:dim-color true} "album "]
       [:> Text {:wrap "truncate-end"} name]]
      [:> Box
       [:> Text {:dim-color true} "released "]
       [:> Text (format-album-release-year release_date)]]]
     [:> Box
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:dim-color true} "   by "]
       [:> Text {:wrap "truncate-end"} (str/join ", " (map :name artists))]]
      [:> Box
       [:> Text {:dim-color true} "songs " #_", 3hr 42 min"]]
      [:> Box {:min-width 4 :justify-content "flex-end"}
       [:> Text total_tracks]]]]))

(defn tracks-panel [{:keys [focus-id header tracks is-searching playback-item-uri track-item-component
                            on-activate on-menu on-search-change on-search-cancel]}]
  (let [[selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)]
                      (set-selected (op selected id))))
        {:keys [selected-index is-focused]} (use-selectable-list {:focus-id focus-id
                                                                  :items tracks
                                                                  :on-toggle on-toggle
                                                                  :on-activate on-activate
                                                                  :auto-focus true})
        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (or (:height viewport) (count tracks))
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
             :padding-x 1}
     header
     (when is-searching
       [:> Box {:height 2}
        [:> Text "Search tracks: "]
        [:f> uncontrolled-text-input {:focus is-focused
                                      :on-change on-search-change
                                      :on-cancel on-search-cancel}]])
     [:> Box {:flex-direction "column"
              :flex-grow 1
              :ref box-ref}
      (->> displayed-tracks
           (map-indexed
            (fn [idx track]
              ^{:key idx}
              [track-item-component track {:is-selected (= idx (- selected-index offset))
                                           :is-highlighted (and playback-item-uri
                                                                (= playback-item-uri (:uri track)))}])))]
     [scroll-status selected-index tracks]]))
