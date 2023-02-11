(ns intools.spotin.stories.main
  (:require
   [ink :refer [Box Text]]
   [intools.hooks :as hooks]
   [intools.spotin.components.action-menu :as action-menu]
   [intools.spotin.stories.stories :as stories]
   [react :as react]))

(def sidepanel-width "20%")

(defn story-item [{:keys [name is_active]} {:keys [is-selected]}]
  [:> Box {:padding-x 1}
   [:> Text {:bold is-selected
             :color (when is-selected "green")
             :wrap "truncate-end"}
    ; (if is_active "* " "  ")
    name]])

(defn sidepanel [{:keys [stories on-activate]}]
  [:f> action-menu/action-menu
   {:actions stories
    :item-component story-item
    ; :header [:> Box {:padding-x 2}
    ;          [:> Text {:dim-color true} "Devices"]]
    :width sidepanel-width
    :on-cancel (fn [])
    :on-activate on-activate}])


(defn content [{:keys [component]}]
  [:> Box {:padding 1
           :flex-grow 1}
   [component]])

(def stories
  [{:name "playback-status-bar"
    :component stories/playback-status-bar-story}
   {:name "album-tracks-panel"
    :component stories/album-tracks-panel}])

(defn main []
  (let [size (hooks/use-window-size)
        [active-story set-active-story!] (react/useState (first stories))
        {:keys [component]} active-story]
    [:> Box {:width (:cols size)
             :height (dec (:rows size))}
     [sidepanel {:stories stories
                 :on-activate set-active-story!}]
     [content {:component component}]]))
