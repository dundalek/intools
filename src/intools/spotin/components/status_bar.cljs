(ns intools.spotin.components.status-bar
  (:require [clojure.string :as str]
            [ink :refer [Box Text]]
            [intools.spotin.format :refer [format-duration]]
            [intools.spotin.model.spotify :as spotify]
            [react]))

(defn status-bar []
  (let [[playback set-playback] (react/useState nil)
        {:keys [is_playing progress_ms shuffle_state repeat_state device item]} playback
        {:keys [duration_ms album artists] item-name :name} item]
    (react/useEffect
     (fn []
       (let [on-interval (fn []
                           (-> (spotify/get-player+)
                               (.then (fn [body]
                                        (let [status (js->clj body :keywordize-keys true)]
                                          (set-playback status))))))
             interval-id (js/setInterval on-interval 5000)]
         (on-interval)
         #(js/clearInterval interval-id)))
     #js [])
    [:> Box {:border-style "single"
             :flex-direction "column"
             :padding-x 1}
     (if is_playing
       [:> Box {:justify-content "center"}
        [:> Text {:dim-color true} "playing "]
        [:> Text item-name]
        [:> Text {:dim-color true} " by "]
        [:> Text (str/join ", " (map :name artists))]
        #_[:> Text {:dim-color true} " from "]
        #_[:> Text (:name album)]]
       [:> Box {:justify-content "center"}
        [:> Text {:dim-color true} "stopped"]])
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

