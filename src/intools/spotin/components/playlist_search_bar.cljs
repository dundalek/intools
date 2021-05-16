(ns intools.spotin.components.playlist-search-bar
  (:require [intools.views :refer [uncontrolled-text-input]]
            [intools.hooks :as hooks]
            [ink :refer [Box Text]]))

(defn playlist-search-bar [{:keys [on-change on-cancel]}]
  (let [{:keys [is-focused]} (hooks/use-focus {:id "playlist-search-bar"
                                               :auto-focus true})]
    [:> Box {:border-style "single"
               :border-color (when is-focused "green")
                             :flex-direction "column"
             :height 4}
      [:> Box
       [:> Text "Search playlists:"]]
      [:> Box
       ;; Using uncontrolled input because going through re-frame (even with dispatch-sync)
       ;; seems to lag and results in mistyping
       [:f> uncontrolled-text-input {;;:on-submit on-submit
                                     :on-change on-change
                                     :on-cancel on-cancel
                                     :focus is-focused}]]]))
