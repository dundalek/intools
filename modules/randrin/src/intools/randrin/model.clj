(ns intools.randrin.model
  (:require [clojure.string :as str]
            [clojure.java.shell :refer [sh]]
            [hyperfiddle.rcf :refer [tests]]))

(def screen-pattern #"Screen (\d+): minimum (\d+) x (\d+), current (\d+) x (\d+), maximum (\d+) x (\d+)")
(def connected-display-pattern #"([^\s]+) connected (primary)? ?(\d+)x(\d+)\+(\d+)\+(\d+) ([^ ]+)? ?\(normal left inverted right x axis y axis\) (\d+)mm x (\d+)mm")
(def disconnected-display-pattern #"([^\s]+) disconnected \(normal left inverted right x axis y axis\)")
(def modeline-pattern #" +(\d+)x(\d+)(i)? +(.*)")
(def mode-pattern #"(\d+\.\d+)( |\*)( |\+)")

(defn section-by [f coll]
  (lazy-seq
   (when-some [s (seq coll)]
     (let [run (cons (first s) (take-while #(not (f %)) (next s)))]
       (cons run (section-by f (lazy-seq (drop (count run) s))))))))

(defn- parse-screen [s]
  (when-some [[_ id min-width min-height current-width current-height max-width max-height] (re-matches screen-pattern s)]
    {:id id
     :type :screen
     :min-width min-width
     :min-height min-height
     :current-width current-width
     :current-height current-height
     :max-width max-width
     :max-height max-height}))
     ; :min {:width min-width
           ; :height min-height}
     ; :current {:width current-width
               ; :height current-height}
     ; :max {:width max-width
           ; :height max-height}}))

(defn- parse-connected-display [s]
  (when-some [[_ id primary width height offset-x offset-y direction _physical-width _physical-height] (re-matches connected-display-pattern s)]
    {:id id
     :type :display
     :connected true
     :primary (some? primary)
     :direction (or direction "normal")
     :width width
     :height height
     :offset-x offset-x
     :offset-y offset-y}))

(defn- parse-disconnected-display [s]
  (when-some [[_ id] (re-matches disconnected-display-pattern s)]
    {:id id
     :type :display
     :connected false}))

(defn- parse-mode-match [[_ frequency current preferred]]
  {:frequency frequency
   :current (= current "*")
   :preferred (= preferred "+")})

(defn- parse-modeline [s]
  (when-some [[_ width height _ line] (re-matches modeline-pattern s)]
    (let [modes (->> (re-seq mode-pattern line)
                     (map parse-mode-match)
                     (into []))]
      {:width width
       :height height
       :modes modes})))

(defn- parse-line [s]
  (or (parse-screen s)
      (parse-connected-display s)
      (parse-disconnected-display s)
      (parse-modeline s)))

(defn- process-display [[display & modes]]
  (assoc display :modes modes))

(defn- process-screen [[screen & items]]
  (assoc screen
         :displays (->> items
                        (section-by (comp #{:display} :type))
                        (map process-display))))

(defn- process-xrandr [lines]
  (->> lines
       (map parse-line)
       (section-by (comp #{:screen} :type))
       (map process-screen)))

(defn- parse-xrandr [s]
  (->> s
       (str/split-lines)
       (process-xrandr)))

(defn list-screens []
  (-> (sh "xrandr" "--query")
      :out
      parse-xrandr))

(comment
  (->> (parse-xrandr (slurp "xrandr-query.txt"))
       first
       :displays
       (map (fn [{:keys [id modes]}]
              [id (count modes)])))

  (list-screens))

(tests
 (hyperfiddle.rcf/enable!)

 (parse-screen "Screen 0: minimum 320 x 200, current 4480 x 1440, maximum 16384 x 16384")
 :=
 {:id "0",
  :type :screen
  :min-width "320",
  :min-height "200",
  :current-width "4480",
  :current-height "1440",
  :max-width "16384",
  :max-height "16384"}

 (parse-connected-display "eDP-1 connected 1920x1080+2560+198 (normal left inverted right x axis y axis) 309mm x 174mm")
 :=
 {:id "eDP-1",
  :type :display
  :connected true
  :primary false,
  :direction "normal",
  :width "1920"
  :height "1080",
  :offset-x "2560",
  :offset-y "198"}

 (parse-connected-display "DP-1 connected primary 2560x1440+0+0 (normal left inverted right x axis y axis) 597mm x 336mm")
 :=
 {:id "DP-1",
  :type :display
  :connected true
  :primary true,
  :direction "normal",
  :width "2560"
  :height "1440",
  :offset-x "0",
  :offset-y "0"}

 (parse-connected-display "eDP-1 connected primary 1920x1080+2560+207 inverted (normal left inverted right x axis y axis) 309mm x 174mm")
 :=
 {:id "eDP-1",
  :type :display
  :connected true,
  :primary true,
  :direction "inverted",
  :width "1920",
  :height "1080",
  :offset-x "2560",
  :offset-y "207"}

 "eDP-1 connected (normal left inverted right x axis y axis)"

 (parse-disconnected-display "HDMI-1 disconnected (normal left inverted right x axis y axis)")
 :=
 {:id "HDMI-1" :type :display :connected false})

 ; (parse-modeline "   1920x1080     60.03 +  60.01*   59.97    59.96    40.05    59.93  ")
 ; "   1680x1050     59.95    59.88  "
 ; "   2560x1440     59.95*+"
 ; "   1920x1080i    60.00    50.00    59.94  "

 ; (re-seq mode-pattern "60.03 +  60.01*   59.97    59.96    40.05    59.93  "))

