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
