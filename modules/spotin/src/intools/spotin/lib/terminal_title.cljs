(ns intools.spotin.lib.terminal-title
  (:require
   ["ink" :as ink]
   ["react" :as react]))

(defn terminal-title [title]
  (str "\u001B]0;" title "\u0007"))

(defn use-terminal-title [title]
  (let [write (.-write (ink/useStdout))]
    (react/useEffect
     (fn []
       (write (terminal-title title))
       js/undefined)
     #js [title])))
