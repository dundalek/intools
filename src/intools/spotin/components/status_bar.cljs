(ns intools.spotin.components.status-bar
  (:require ["ink-spinner$default" :as Spinner]
            [clojure.string :as str]
            [ink :refer [Box Text]]
            [intools.spotin.format :refer [format-duration]]
            [react]))

(defn status-bar [{:keys [playback pending-requests]}]
  (let [{:keys [is_playing progress_ms shuffle_state repeat_state device item]} playback
        {:keys [duration_ms album artists] item-name :name} item]
    [:> Box {:border-style "single"
             :flex-direction "column"
             :padding-x 1}
     [:> Box {:justify-content "center"
              :padding-right 2} ; padding to compensate for space taken by spinner
      [:> Text
       (if pending-requests [:> Spinner] " ")
       " "]
      (if is_playing
        [:<>
         [:> Text {:dim-color true} "playing "]
         [:> Text item-name]
         [:> Text {:dim-color true} " by "]
         [:> Text (str/join ", " (map :name artists))]
         #_[:> Text {:dim-color true} " from "]
         #_[:> Text (:name album)]]
        [:> Text {:dim-color true} "stopped"])]
     [:> Box {:margin-top 1}
      [:> Text (:name device)]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color true} "Volume "]
      [:> Text (:volume_percent device) "%"]
      [:> Box {:flex-grow 1}]
      [:> Text (format-duration progress_ms)]
      [:> Text {:dim-color true} " / "]
      [:> Text (format-duration duration_ms)]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color (not shuffle_state)}
       "Shuffle " (if shuffle_state "on" "off")]
      [:> Box {:flex-grow 1}]
      [:> Text {:dim-color (= repeat_state "off")}
       "Repeat " repeat_state]]]))

