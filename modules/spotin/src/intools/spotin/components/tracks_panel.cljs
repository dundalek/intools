(ns intools.spotin.components.tracks-panel
  (:require [clojure.string :as str]
            [ink :refer [Box Text]]
            [intools.spotin.format :refer [format-album-release-year format-duration playback-indicator]]
            [intools.views :refer [scroll-status use-scrollable-box]]
            [react]))

(defn playlist-track-item [track {:keys [is-selected is-highlighted]}]
  (let [{:keys [name duration_ms album artists]} track
        color (when (or is-selected is-highlighted) "green")
        text-style {:bold is-selected :color color}]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") (str (when is-highlighted playback-indicator) " " name)]]
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
      [:> Text (assoc text-style :wrap "truncate-end") (str (when is-highlighted playback-indicator) " " name)]]
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text (assoc text-style :wrap "truncate-end") (str/join ", " (map :name artists))]]
     [:> Box {:min-width 6 :justify-content "flex-end"}
      [:> Text text-style (format-duration duration_ms)]]]))

(defn playlist-header [{:keys [playlist tracks]}]
  (let [{:keys [name description owner]} playlist]
    [:> Box {:flex-direction "column"
             :margin-bottom 1
             :padding-left 1}
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
             :margin-bottom 1
             :padding-left 1}
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

(defn tracks-panel [{:keys [focus-id header tracks playback-item-uri track-item-component
                            on-activate on-menu]}]
  (let [{:keys [box-ref is-focused selected-index select displayed-selected-index displayed-items]}
        (use-scrollable-box {:focus-id focus-id
                             :items tracks
                             :on-activate on-activate
                             :auto-focus true})]

    (react/useEffect
     (fn []
       (when playback-item-uri
         (when-some [index (->> tracks
                                (keep-indexed (fn [idx {:keys [uri]}]
                                                (when (= uri playback-item-uri)
                                                  idx)))
                                (first))]
           (select index)))
       js/undefined)
     #js [(empty? tracks)])

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
             :padding-right 1}
     header
     [:> Box {:flex-direction "column"
              :flex-grow 1
              :ref box-ref}
      (->> displayed-items
           (map-indexed
            (fn [idx {:keys [id] :as track}]
              ^{:key id}
              [track-item-component track {:is-selected (= idx displayed-selected-index)
                                           :is-highlighted (and playback-item-uri
                                                                (= playback-item-uri (:uri track)))}])))]
     [scroll-status selected-index tracks]]))
