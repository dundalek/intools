(ns intools.spotin.components.playlists-panel
  (:require [react]
            [ink :refer [Box Text]]
            [intools.views :refer [use-selectable-list-controlled]]
            [intools.hooks :as hooks]))

(defn playlist-item [{:keys [name]} {:keys [is-selected is-active]}]
  [:> Text {:bold is-selected
            :color (cond
                     is-active "yellow"
                     is-selected "green")
            :wrap "truncate-end"}
   name])

(defn playlists-panel [{:keys [selected-playlist-id playlists on-activate on-menu]}]
  (let [[selected-index on-select] (react/useState 0)
        [selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)
                          value (op selected id)]
                      (set-selected value)))

        {:keys [selected-index is-focused]}
        (use-selectable-list-controlled {:selected-index selected-index
                                         :on-select on-select
                                         :items playlists
                                         :on-activate #(on-activate %)
                                         :on-toggle on-toggle
                                         :auto-focus true})

        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (- (or (:height viewport) (count playlists)) 2) ; -2 to compensate for borders
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-items (->> playlists (drop offset) (take viewport-height))]
    (react/useEffect
     (fn []
       (when selected-playlist-id
         (when-some [index (->> playlists
                                (keep-indexed (fn [idx {:keys [id]}]
                                                (when (= id selected-playlist-id)
                                                  idx)))
                                (first))]
           (on-select index)))
       js/undefined)
     #js [selected-playlist-id])
    (ink/useInput
     (fn [input _key]
       (when is-focused
         (case input
           "x" (when on-menu (on-menu (nth playlists selected-index) selected))
           nil))))
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :flex-grow 1
             :ref box-ref}
     (->> displayed-items
          (map-indexed
           (fn [idx {:keys [id] :as item}]
             ^{:key idx}
             [playlist-item item {:is-selected (= idx (- selected-index offset))
                                  :is-active (contains? selected id)}])))]))

