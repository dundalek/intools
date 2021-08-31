(ns intools.randrin.theme
  (:require
   [hyperfiddle.rcf :refer [tests]]))

(defn rgb->vec [rgb]
  (let [r (-> rgb
              (bit-shift-right 16)
              (bit-and 0xff))
        g (-> rgb
              (bit-shift-right 8)
              (bit-and 0xff))
        b (bit-and rgb 0xff)]
    [(/ r 255.0)
     (/ g 255.0)
     (/ b 255.0)]))

;; Based on https://github.com/sindresorhus/hyper-snazzy
(def foreground-color (rgb->vec 0xeff0eb))
(def background-color (rgb->vec 0x282a36))
(def red (rgb->vec 0xff5c57))
(def green (rgb->vec 0x5af78e))
(def yellow (rgb->vec 0xf3f99d))
(def blue (rgb->vec 0x57c7ff))
(def magenta (rgb->vec 0xff6ac1))
(def cyan (rgb->vec 0x9aedfe))
(def border-color (rgb->vec 0x222430))
(def cursor-color (rgb->vec 0x97979b))
(def black background-color)
(def white (rgb->vec 0xf1f1f0))
(def light-black (rgb->vec 0x686868))

(comment
  (hyperfiddle.rcf/enable!))

(tests
 (rgb->vec 0x000000) := [0.0 0.0 0.0]
 (rgb->vec 0x333333) := [0.2 0.2 0.2]
 (rgb->vec 0xcccccc) := [0.8 0.8 0.8]
 (rgb->vec 0xffffff) := [1.0 1.0 1.0]
 (rgb->vec 0x330000) := [0.2 0.0 0.0]
 (rgb->vec 0x003300) := [0.0 0.2 0.0]
 (rgb->vec 0x000033) := [0.0 0.0 0.2])
