(ns intools.spotin.components.status-bar
  (:require ["ink-spinner$default" :as Spinner]
            [clojure.string :as str]
            [ink :refer [Box Text]]
            [intools.spotin.format :refer [format-duration]]
            [react]))

(defn use-adjusted-time-progress [is_playing progress_ms duration_ms]
  (let [[adjusted-progress set-adjusted-progress] (react/useState progress_ms)
        last-updated-time (react/useRef nil)]
    (react/useEffect
     (fn []
       (set-adjusted-progress progress_ms)
       (if is_playing
         (let [interval-id (js/setInterval
                            (fn []
                              (let [time-offset (- (js/Date.now) (.-current last-updated-time))
                                    adjusted (cond-> progress_ms
                                               (pos? time-offset) (+ time-offset)
                                               duration_ms (Math/min duration_ms))]
                                (set-adjusted-progress adjusted)))
                            1000)]
           (set! (.-current last-updated-time) (js/Date.now))
           #(js/clearInterval interval-id))
         js/undefined))
     #js [is_playing progress_ms duration_ms])

    adjusted-progress))

(defn status-bar [{:keys [playback pending-requests]}]
  (let [{:keys [is_playing progress_ms shuffle_state repeat_state device item]} playback
        {:keys [duration_ms album artists] item-name :name} item
        adjusted-progress-ms (use-adjusted-time-progress is_playing progress_ms duration_ms)]

    [:> Box {:border-style "single"
             :flex-direction "column"
             :padding-x 1}
     [:> Box {:justify-content "center"
              :padding-right 2} ; padding to compensate for space taken by spinner
      [:> Text
       (if pending-requests [:> Spinner] " ")
       " "]
      (cond
        is_playing [:> Text {:dim-color true} "playing"]
        item-name [:> Text {:dim-color true} "paused"]
        :else [:> Text {:dim-color true} "stopped"])
      (when item-name
        [:<>
         [:> Text " " item-name]
         [:> Text {:dim-color true} " by "]
         [:> Text (str/join ", " (map :name artists))]
         #_[:> Text {:dim-color true} " from "]
         #_[:> Text (:name album)]])]
     [:> Box {:margin-top 1}
      [:> Text (:name device)]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color true} "Volume "]
      [:> Text (:volume_percent device) "%"]
      [:> Box {:flex-grow 1}]
      [:> Text (format-duration adjusted-progress-ms)]
      [:> Text {:dim-color true} " / "]
      [:> Text (format-duration duration_ms)]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color true} "Shuffle "]
      [:> Text {:dim-color (not shuffle_state)} (if shuffle_state "on" "off")]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color true} "Repeat "]
      [:> Text {:dim-color (= repeat_state "off")} repeat_state]]]))

