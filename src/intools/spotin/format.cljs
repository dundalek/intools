(ns intools.spotin.format)

(defn format-duration [ms]
  (let [sec (Math/floor (/ ms 1000))
        seconds (mod sec 60)
        minutes (quot sec 60)]
    (str minutes ":" (when (< seconds 10) "0") seconds)))
