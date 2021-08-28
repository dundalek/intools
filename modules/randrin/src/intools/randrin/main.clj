(ns intools.randrin.main
  (:require
   [clojure.core.async :as async]
   [intools.membrane :refer [bordered-box horizontal-layout vertical-layout selectable-list]]
   [intools.randrin.app.events]
   [intools.randrin.app.subs]
   [intools.randrin.model :as model]
   [intools.randrin.theme :as theme]
   [membrane.component :as component :refer [defui]]
   [membrane.lanterna :as lanterna :refer [textarea checkbox label rectangle]]
   [membrane.re-frame :as memframe]
   [membrane.ui :as ui :refer [on]]
   [re-frame.core :as rf :refer [reg-event-db reg-event-fx inject-cofx path after reg-sub subscribe dispatch dispatch-sync]])
  (:gen-class))

(defn display-item [display {:keys [is-selected]}]
  (let [{:keys [id connected primary width height modes]} display]
    (ui/with-color (if is-selected
                     theme/red
                     theme/foreground-color)
      (horizontal-layout
       (label id)
       (when width
         (horizontal-layout
          (label width)
          (label "x")
          (label height)))))))

(defn selectable-item [item {:keys [is-selected]}]
  (ui/with-color (if is-selected
                   theme/green
                   theme/foreground-color)
    (label (str "Item " item))))

(defn get-scrollable-offset [previous-offset selected-index height]
  (Math/min
   selected-index
   (Math/max previous-offset
             (- (inc selected-index)
                height))))

(defn scrollable-box [{:keys [focus-id width height items item-component on-activate]}]
  (let [state @(subscribe [:randrin/component-state focus-id])
        update! (fn [& args]
                  [(into [:update :component-state update focus-id] args)])
        is-focused @(subscribe [:randrin/is-focused focus-id])
        selected-index (or (:selected-index state) 0)
        viewport-height (- height 2)
        offset (or (:offset state) 0)
        displayed-selected-index (- selected-index offset)
        displayed-items (->> items (drop offset) (take viewport-height))
        color (if is-focused theme/green theme/foreground-color)]
    (ui/on :key-press (when is-focused
                        (fn [key]
                          (case key
                            :down (update! (fn [{:keys [offset selected-index] :as state}]
                                             (let [selected-index (or selected-index 0)
                                                   selected-index (Math/min (dec (count items))
                                                                            (inc selected-index))
                                                   offset (or offset 0)
                                                   offset (get-scrollable-offset offset selected-index viewport-height)]
                                               (assoc state
                                                      :selected-index selected-index
                                                      :offset offset))))
                            :up (update! (fn [{:keys [offset selected-index] :as state}]
                                           (let [selected-index (or selected-index 0)
                                                 selected-index (Math/max 0 (dec selected-index))
                                                 offset (or offset 0)
                                                 offset (get-scrollable-offset offset selected-index viewport-height)]
                                             (assoc state
                                                    :selected-index selected-index
                                                    :offset offset))))
                            nil)))
           [(ui/with-color color
              (rectangle width height))
            (ui/translate 2 0
                          (ui/with-color theme/blue
                            (label "Title")))
            (ui/padding 1 1
                        (apply vertical-layout
                               (for [[idx item] (map-indexed list displayed-items)]
                                 (item-component item {:is-selected (and is-focused (= idx displayed-selected-index))}))))])))

(defn app []
  (vertical-layout
   (on :terminal-resized
       (fn [new-size]
         (dispatch [:randrin/terminal-resized new-size])
         nil)
       :key-press
       (fn [key]
         (case key
           :left [[:randrin/focus-prev]]
           :right [[:randrin/focus-next]]
           nil))
       nil)

   (when-some [[w h] @(subscribe [:randrin/terminal-size])]
     (let [w (Math/max w 80)
           h (Math/max h 25)
           panel-width (Math/floor (* w 0.33))
           content-width (- w panel-width)]
       (ui/with-color theme/foreground-color
         (horizontal-layout
          (scrollable-box {:focus-id :action-menu
                           :width panel-width
                           :height h
                           :items (range 100)
                           :item-component selectable-item})
          (scrollable-box {:focus-id :display-panel
                           :width content-width
                           :height h
                           :items (range 200)
                           :item-component selectable-item})))))
   #_(vertical-layout
      (ui/with-color theme/foreground-color
        (label (str "Size: " @(subscribe [:randrin/terminal-size]))))
      (ui/with-color theme/green
        (label (str "Active: " @(subscribe [:randrin/focused-id])))))))

(comment
  (do
    (def close-ch (async/chan))
    (dispatch-sync [:randrin/init])
    (lanterna/run
     #(memframe/re-frame-app (app))
     {:close-ch close-ch}))

  ;; Some time later, stop the UI. You can call `lanterna/run` again to start a new UI.
  (async/close! close-ch)

  (re-frame.subs/clear-subscription-cache!))

(defn -main [& _args]
  (dispatch-sync [:randrin/init])
  (lanterna/run-sync #(memframe/re-frame-app (app)))
  (.close System/in)
  (shutdown-agents))
