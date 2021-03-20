(ns intools.hooks
  (:require [react]
            [ink]))

(def enterAltScreenCommand "\u001b[?1049h")
(def leaveAltScreenCommand "\u001b[?1049l")

(defn use-fullscreen []
  (let [write (-> (ink/useStdout) .-write)]
    (react/useEffect
     (fn []
       (write enterAltScreenCommand)
       #(write leaveAltScreenCommand))
     #js [])))

(defn get-window-size []
  {:cols (.-stdout.columns js/process)
   :rows (.-stdout.rows js/process)})

(defn use-window-size []
  (let [[size set-size] (react/useState (get-window-size))]
    (react/useEffect
      (fn []
        (let [on-resize #(set-size (get-window-size))]
          (.on (.-stdout js/process) "resize" on-resize)
          #(.off (.-stdout js/process) "resize" on-resize)))
      #js [])
    size))

(defn use-ref-size [box-ref]
  (let [window-size (use-window-size)
        [viewport set-viewport] (react/useState nil)]
    (react/useEffect
      (fn []
        (set-viewport (js->clj (ink/measureElement (.-current box-ref))
                               :keywordize-keys true))
        js/undefined)
      #js [window-size])
    viewport))

(defn use-scrollable-offset [{:keys [selected-index height]}]
  (let [[offset set-offset] (react/useState 0)]
    (react/useEffect
      (fn []
        (when height
          (set-offset
            (Math/min
              selected-index
              (Math/max offset
                        (- (inc selected-index)
                           height)))))
        js/undefined)
      #js [selected-index height])
    offset))
