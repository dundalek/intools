(ns intools.spotin.components.shortcuts-bar
  (:require [ink :refer [Box Text]]
            [clojure.string :as str]))

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
