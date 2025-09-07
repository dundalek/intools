(ns lshwin.core)

(defn flatten-children
  "Recursively flatten nested children into a single sequence"
  [item]
  (if-let [children (:children item)]
    (concat [item] (mapcat flatten-children children))
    [item]))

(defn expand-bridge-children
  "Transform data to expand :children of :class 'bridge' items in their place.
   Expansion happens recursively."
  [data]
  (letfn [(expand [{:keys [class children] :as item}]
            (if (= class "bridge")
              (mapcat expand children)
              [(cond-> item
                 children
                 (assoc :children (mapcat expand children)))]))]
    (first (expand data))))
