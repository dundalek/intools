(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {djblue/portal {:mvn/version "0.59.1"}}})

(ns lshwin.server
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [portal.api :as p]
   [portal.viewer :as pv]
   [lshwin.core :as core]
   [lshwin.viewer :as-alias viewer]))

(defn get-hardware-info []
  (try
    (let [{:keys [out exit]} (shell {:out :string :continue true} "lshw" "-json")]
      (if (zero? exit)
        (json/parse-string out true)
        (do
          (println "Warning: lshw may not be installed or requires sudo privileges")
          {:error "Failed to run lshw" :message "You may need to install lshw or run with sudo"})))
    (catch Exception e
      {:error "Exception running lshw" :message (ex-message e)})))

(defn create-initial-state []
  (pv/default {:data (get-hardware-info)}
              ::viewer/root))

(defonce !app-state (atom nil))

(defn load-viewer []
  (let [source-path (str (fs/path (fs/parent *file*) "src/lshwin/core.cljc"))]
    (p/eval-str (slurp source-path)))
  (let [source-path (str (fs/path (fs/parent *file*) "src/lshwin/viewer.cljs"))]
    (p/eval-str (slurp source-path))))

(defn open []
  (let [initial-state (create-initial-state)]
    (reset! !app-state initial-state)
    (p/open {:value !app-state
             :on-load load-viewer})))

(defn -main [& _args]
  (open)
  @(promise))

(comment
  (def data (get-hardware-info))
  (def items (core/flatten-children data))

  (->> (core/flatten-children data)
       (map :class)
       (frequencies)
       (sort-by val)
       (reverse))

  (->> (core/flatten-children data)
       (filter (comp #{"bus"} :class))
       (map #(update % :children count)))

  (->> (core/flatten-children data)
       (mapcat keys)
       (frequencies)
       (sort-by val)
       (reverse))

  (->> (core/flatten-children data)
       count)

  (->> items
       (filter #(seq (:children %)))
       (remove (comp #{"bus" "bridge" "system"} :class)))
       ; (map :class)
       ; (frequencies)
       ; (sort-by val)
       ; (reverse))

  ;; Test expand-bridge-children
  (def test-data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge"
                              :children [{:id "child1" :class "memory"}
                                         {:id "child2" :class "processor"}]}
                             {:id "disk1" :class "disk"}]})

  (core/expand-bridge-children test-data))
