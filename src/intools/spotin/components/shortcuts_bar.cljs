(ns intools.spotin.components.shortcuts-bar
  (:require [ink :refer [Box Text]]))

(defn shortcuts-bar [{:keys [actions]}]
  [:> Box {:justify-content "center"}
   [:> Text
    (->> actions
         (map (fn [{:keys [shortcut name]}]
                [:<>
                 [:> Text {:dim-color true} "["]
                 [:> Text {:color "green"} shortcut]
                 [:> Text {:dim-color true} "]"]
                 [:> Text name]]))
         (interpose "  "))]])
