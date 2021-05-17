(ns intools.spotin.components.playlists-panel
  (:require [ink :refer [Box Text]]
            [intools.hooks :as hooks]
            [intools.views :refer [uncontrolled-text-input use-selectable-list-controlled]]
            [react]))

(defn playlist-item [{:keys [name]} {:keys [is-selected is-active]}]
  [:> Text {:bold is-selected
            :color (cond
                     is-active "yellow"
                     is-selected "green")
            :wrap "truncate-end"}
   name])

(defn playlists-panel [{:keys [focus-id selected-playlist-id playlists is-searching
                               on-activate on-menu on-search-change on-search-cancel]}]
  (let [[selected-index on-select] (react/useState 0)
        [selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)
                          value (op selected id)]
                      (set-selected value)))

        {:keys [selected-index is-focused]}
        (use-selectable-list-controlled {:focus-id focus-id
                                         :selected-index selected-index
                                         :on-select on-select
                                         :items playlists
                                         :on-activate #(on-activate %)
                                         :on-toggle on-toggle
                                         :auto-focus true})

        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (or (:height viewport) (count playlists))
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-items (->> playlists (drop offset) (take viewport-height))]

    (react/useEffect
     (fn []
       (on-select 0)
       js/undefined)
     #js [(count playlists)])

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
             :flex-grow 1}
     (when is-searching
       [:> Box {:flex-direction "column"
                :height 3}
        [:> Text {:wrap "truncate-end"}
         "Search playlists:"]
        [:f> uncontrolled-text-input {:focus is-focused
                                      :on-change on-search-change
                                      :on-cancel on-search-cancel}]])
     [:> Box {:flex-direction "column"
              :flex-grow 1
              :ref box-ref}
      (->> displayed-items
           (map-indexed
            (fn [idx {:keys [id] :as item}]
              ^{:key idx}
              [playlist-item item {:is-selected (= idx (- selected-index offset))
                                   :is-active (contains? selected id)}])))]]))
