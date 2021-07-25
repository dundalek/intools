(ns intools.spotin.components.shortcuts-bar
  (:require [ink :refer [Box Text]]))

(def separator [:> Text "  "])

(defn shortcut-item [{:keys [shortcut name]}]
  [:<>
   [:> Text {:dim-color true} "["]
   [:> Text {:color "green"} shortcut]
   [:> Text {:dim-color true} "]"]
   [:> Text name]])

(defn shortcuts-bar [{:keys [actions]}]
  [:> Box {:justify-content "center"
           :padding-x 1
           :height 1}
   [:> Text {:wrap "truncate-end"}
    (->> actions
         (butlast)
         (map (fn [item]
                [shortcut-item item]))
         (interpose separator)
         (into [:<>]))]
   separator
   [shortcut-item (last actions)]])
