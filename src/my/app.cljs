(ns my.app
  (:require
    [goog.object :as gobj]
    [reagent.core :as reagent]
    [reagent.dom.server :as dom-server]))

(defn hello-component [name]
  [:div (str "Hello " name "!")])

(defn html [name]
  (dom-server/render-to-string [hello-component name]))

(defn -main [arg]
  (println (html arg)))

(gobj/set goog.global "myfn" html)
