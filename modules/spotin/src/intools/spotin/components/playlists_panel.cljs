(ns intools.spotin.components.playlists-panel
  (:require [ink :refer [Box Spacer Text]]
            [intools.hooks :as hooks]
            [intools.search :as search]
            [intools.spotin.model.spotify :as spotify]
            [intools.views :refer [scroll-status uncontrolled-text-input use-selectable-list-controlled]]
            [react]
            [react-query :refer [QueryClient QueryClientProvider useMutation useQuery useQueryClient]]))

(defn playlist-item [{:keys [name]} {:keys [is-selected is-active is-highlighted]}]
  (let [props {:bold is-selected
               :color (cond
                        is-active "yellow"
                        (or is-selected is-highlighted) "green")}]
    [:> Box
     [:> Text (assoc props :wrap "truncate-end")
      name]
     [Spacer]
     (when is-highlighted
        ;; perhaps use some speaker pictogram from unicode
       [:> Text props " >"])]))

(defn playlists-panel [{:keys [focus-id selected-playlist-id search-query playback-context-uri
                               on-activate on-menu on-search-change on-search-cancel]}]
  (let [query (useQuery "playlists" spotify/get-all-playlists+)
        all-playlists (:items (.-data query))
        playlists (react/useMemo
                   (fn [] (search/filter-by search-query :name all-playlists))
                   #js [all-playlists search-query])
        [selected-index on-select] (react/useState 0)
        [selected set-selected] (react/useState #{})
        is-searching (some? search-query)
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
        viewport-height (or (:height viewport) 0)
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
             :flex-grow 1}
     [:> Box {:height 1}
              ;;:justify-content "center"}
      [:> Text {:dim-color true} "Playlists"]]

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
            (fn [idx {:keys [id uri] :as item}]
              ^{:key id}
              [playlist-item item {:is-selected (= idx (- selected-index offset))
                                   :is-active (contains? selected id)
                                   :is-highlighted (and playback-context-uri
                                                        (= playback-context-uri uri))}])))]
     [scroll-status selected-index playlists]]))
