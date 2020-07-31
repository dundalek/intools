(ns my.app
  (:require
   #_[goog.object :as gobj]
   [reagent.core :as r]
   [ink :refer [Text render]]))
    ; [reagent.dom.server :as dom-server]))

; (defn hello-component [name]
;   [:div (str "Hello " name "!")])
;
; (defn html [name]
;   (dom-server/render-to-string [hello-component name]))
;
; (gobj/set goog.global "myfn" html)

(defn demo []
  [:> Text {:color "red"} "Hello world Bla"])

(defn -main []
  (render (r/as-element [demo])))
  ;(render (r/as-element [demo])))
  ; (println (html arg)))


(defn ^:dev/after-load reload! []
  ; (println "Code updated.")
  (-main))
