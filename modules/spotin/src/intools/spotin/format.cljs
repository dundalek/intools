(ns intools.spotin.format
  (:require [clojure.string :as str]))

;; perhaps use some speaker pictogram from unicode
(def playback-indicator ">")

(defn format-duration [ms]
  (let [sec (Math/floor (/ ms 1000))
        seconds (mod sec 60)
        minutes (quot sec 60)]
    (str minutes ":" (when (< seconds 10) "0") seconds)))

(defn format-album-release-year [release-date]
  ;; release-date is a YYYY-MM-DD string, pick the year and discard the rest
  (some-> release-date (str/replace #"-.*$" "")))
