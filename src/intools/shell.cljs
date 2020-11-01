(ns intools.shell
  (:require #_[clojure.java.shell :refer [sh]]))

(def spawn-sync (.-spawnSync (js/require "child_process")))

(defn sh [cmd & args]
  (let [^js proc (spawn-sync cmd (to-array args) #js{:encoding "utf-8"})]
    {:out (.-stdout proc)
     :err (.-stderr proc)
     :exit (.-status proc)}))
