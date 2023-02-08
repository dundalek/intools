(ns intools.spotin.model.playlist
  (:require
   [intools.spotin.model.spotify :as spotify]))

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

(defn create-mixed-playlist+ [{:keys [request+] :as client} playlist-ids]
  (-> (map #(spotify/get-playlist-tracks+ client %) playlist-ids)
      (js/Promise.all)
      (.then (fn [bodies]
               (let [track-uris (->> bodies
                                     (map :items)
                                     (generate-mixed-playlist)
                                     (map #(get-in % [:track :uri])))]
                 (-> (request+ (spotify/current-user))
                     (.then (fn [user]
                              ;; TODO: add description - will need to fetch playlists for their name
                              ;;:description (str "Generated from: " (str/join ", " (map :name playlists)))})
                              (let [user-id (:id user)
                                    playlist-name (str "Generated-" (+ 100 (rand-int 900)))]
                                (request+ (spotify/create-playlist user-id {:name playlist-name})))))
                     (.then (fn [body]
                              (let [playlist-id (:id body)]
                                (request+ (spotify/post (str "https://api.spotify.com/v1/playlists/" playlist-id "/tracks")
                                                        {:body {:uris track-uris}})))))))))))
