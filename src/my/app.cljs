(ns my.app
  (:require
    [reagent.core :as reagent]
    [reagent.dom.server :as dom-server]))

(defn hello-component [name]
  [:div (str "Hello " name "!")])

(defn html [name]
  (dom-server/render-to-string [hello-component name]))
