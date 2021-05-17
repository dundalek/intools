(ns intools.spotin.components.album-panel
  (:require [clojure.string :as str]
            [ink :refer [Box Spacer Text]]))

(defn album-header [{:keys [name artists release_date total_tracks]}]
  [:> Box {:flex-direction "column"
           :margin-bottom 1}
   [:> Box
    [:> Text {:dim-color true} "album    "]
    [:> Text {:wrap "truncate-end"} name]
    [:> Spacer]
    [:> Text {:dim-color true} "by "]
    [:> Text {:wrap "truncate-end"} (str/join ", " (map :name artists))]
    [:> Spacer]
    [:> Text {:dim-color true} "released "]
        ;; TODO format release date to show only year
    [:> Text release_date]
    [:> Spacer]
    [:> Text total_tracks " songs" #_", 3hr 42 min"]]])

(defn album-panel [{:keys [album]}]
  [:> Box {:flex-direction "column"
           :flex-grow 1
           :border-style "single"}
   [album-header album]])
