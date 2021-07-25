(ns intools.spotin.components.device-menu
  (:require [ink :refer [Box Text]]))

(defn device-item [{:keys [name type is_active]} {:keys [is-selected]}]
  [:> Box
   [:> Text {:bold is-selected
             :color (when is-selected "green")
             :wrap "truncate-end"}
    (if is_active "* " "  ")
    name " " type]])
