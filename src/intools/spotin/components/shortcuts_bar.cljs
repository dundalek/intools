(ns intools.spotin.components.shortcuts-bar
  (:require [clojure.string :as str]
            [ink :refer [Box Text]]))

(defn shortcuts-bar [{:keys [actions]}]
  [:> Box
   [:> Text
    "x: menu, "
    (->> actions
         (filter :shortcut)
         (map (fn [{:keys [shortcut name]}]
                (str shortcut ": " name)))
         (str/join ", "))
    ", u: back"
    ", q: quit"]])
