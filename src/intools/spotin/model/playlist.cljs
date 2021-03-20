(ns intools.spotin.model.playlist
  (:require [intools.spotin.model.spotify :as spotify]
            [clojure.string :as str]))

(defn interleave-all
  "Like interleave, but exhausts all colls instead of stoping on the shortest one."
  [& colls]
  (lazy-seq
    (let [ss (keep seq colls)]
      (when (seq ss)
        (concat (map first ss)
                (apply interleave-all (map rest ss)))))))

(defn generate-mixed-playlist [playlists]
  (->> playlists
       (map shuffle)
       (apply interleave-all)
       (take 50)
       (shuffle)))

(defn create-mixed-playlist+ [playlists]
  (-> (map spotify/get-playlist-tracks+ (map :id playlists))
      (js/Promise.all)
      (.then (fn [bodies]
               (let [track-uris (->> bodies
                                     (map (fn [body]
                                            (->> (-> body (js->clj :keywordize-keys true) :items))))
                                     (generate-mixed-playlist)
                                     (map #(get-in % [:track :uri])))]
                  (.then (spotify/user-id+)
                    (fn [user-id]
                      (.then (spotify/create-playlist+ user-id {:name (str "Generated-" (+ 100 (rand-int 900)))
                                                                :description (str "Generated from: " (str/join ", " (map :name playlists)))})
                        (fn [^js body]
                          (let [playlist-id (.-id body)]
                            (spotify/authorized-post+ (str "https://api.spotify.com/v1/playlists/" playlist-id "/tracks")
                                                    {:uris track-uris})))))))))))
