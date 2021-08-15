(ns intools.randrin.main
  (:require
   [clojure.core.async :as async]
   [intools.membrane :refer [bordered-box vertical-layout selectable-list]]
   [intools.randrin.app.events]
   [intools.randrin.app.subs]
   [intools.randrin.model :as model]
   [membrane.component :as component :refer [defui]]
   [membrane.lanterna :as lanterna :refer [textarea checkbox label rectangle]]
   [membrane.re-frame :as memframe]
   [membrane.ui :as ui :refer [horizontal-layout on]]
   [re-frame.core :as rf :refer [reg-event-db reg-event-fx inject-cofx path after reg-sub subscribe dispatch dispatch-sync]])
  (:gen-class))

(defn display-item [display {:keys [is-selected]}]
  (let [{:keys [id connected primary width height modes]} display]
    (ui/with-color (if is-selected
                     [1 0 0]
                     [1 1 1])
      (horizontal-layout
       (label id)
       (when width
         (horizontal-layout
          (label width)
          (label "x")
          (label height)))))))

(defn selectable-item [item {:keys [is-selected]}]
  (ui/with-color (if is-selected
                   [1 0 0]
                   [1 1 1])
    (label (str "Item " item))))

(defn app []
  (horizontal-layout
   (vertical-layout
    (ui/with-color [1 1 1]
      (bordered-box
       (ui/with-color [0 1 0]
         (label "Title"))
       (selectable-list {:items @(subscribe [:randrin/displays])
                         :state @(subscribe [:randrin/display-list-state])
                         :update! (fn [& args]
                                    [(into [:update :display-list-state] args)])
                         :item-component display-item}))))
   (vertical-layout
    (selectable-list {:items @(subscribe [:items])
                      :state @(subscribe [:list-state])
                      :update! (fn [& args]
                                 [(into [:update :state] args)])
                      :item-component selectable-item})
    [(ui/with-color [0 1 0]
       #_(rectangle 10 5)
       (ui/rounded-rectangle 10 5 0))
     (ui/translate 1 1
                   (ui/with-color [0 0 1]
                     (label "Hello")))]
    #_(ui/with-color [1 1 1]
        (label (str "Type: " (some? lanterna/*screen*))))
    #_(ui/with-color [1 1 1]
        (apply horizontal-layout
               (let [size (.getTerminalSize lanterna/*screen*)]
                 [(label (.getRows size))
                  (label (.getColumns size))]))))))

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
