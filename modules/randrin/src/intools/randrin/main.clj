(ns intools.randrin.main
  (:require [clojure.core.async :as async]
            [intools.randrin.model :as model]
            [intools.membrane :refer [bordered-box vertical-layout selectable-list]]
            [membrane.component :as component :refer [defui]]
            [membrane.lanterna :as lanterna :refer [textarea checkbox label rectangle]]
            [membrane.ui :as ui :refer [horizontal-layout on]])
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

(def default-state {:state {:selected 0}
                    :items (range 10)
                    :display-list-state {:selected 0}
                    :screen nil})

(defonce todo-state (atom default-state))

(comment
  (reset! todo-state default-state))

(defn update-handler [key]
  (fn [& args]
    (apply swap! todo-state update key args)
    nil))

(defn update-in-handler [path]
  (fn [& args]
    (apply swap! todo-state update-in path args)
    nil))

(defui todo-app [{:keys [screen items state display-list-state]}]
  (horizontal-layout
   (vertical-layout
    (ui/with-color [1 1 1]
      (bordered-box
       (ui/with-color [0 1 0]
         (label "Title"))
       (selectable-list {:items (:displays screen)
                         :state display-list-state
                         :update! (update-handler :display-list-state)
                         :item-component display-item}))))
   (vertical-layout
    #_(label (pr-str display-list-state))
    (selectable-list {:items items
                      :state state
                      :update! (update-handler :state)
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
    (swap! todo-state assoc :screen (-> (model/list-screens) first))
    (def close-ch (async/chan))
    (lanterna/run (component/make-app #'todo-app todo-state)
                  {:in membrane.lanterna/in
                   :out membrane.lanterna/out}))

  ;; Some time later, stop the UI. You can call `lanterna/run` again to start a new UI.
  (async/close! close-ch))

(defn -main [& args]
  (lanterna/run-sync (component/make-app #'todo-app todo-state)))
  ;; (component/run-ui-sync )
  ;; (.close System/in)
  ;; (shutdown-agents)

