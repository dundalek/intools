(ns intools.spotin.stories.stories
  (:require
   [ink :refer [Box Text]]
   [intools.spotin.components.status-bar :as status-bar]))

(defn playback-status-bar-story []
  (let [playback {:is_playing false
                  :progress_ms 0
                  :shuffle_state false
                  :repeat_state "off"
                  :device {:name "Web Browser"
                           :volume_percent 100}
                  :item {:name "My Song"
                         :duration_ms 123000
                         ; :album
                         :artists [{:name "Some artist"}]}}]
    [:> Box {:flex-direction "column"
             :flex-grow 1}
     [:f> status-bar/status-bar {:playback nil
                                 :pending-requests false}]
     [:f> status-bar/status-bar {:playback playback
                                 :pending-requests false}]
     [:f> status-bar/status-bar {:playback playback
                                 :pending-requests true}]]))

(defn album-tracks-panel []
  [:> Box {:padding 1}
   [:> Text "album-tracks-panel"]])
