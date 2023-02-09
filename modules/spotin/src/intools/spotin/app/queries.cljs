(ns intools.spotin.app.queries
  (:require
   [intools.spotin.infrastructure.spotify-client :as spotify-client]
   [intools.spotin.model.spotify :as spotify]
   [react]))

(defn player []
  #js {:queryKey #js ["player"]
       :queryFn #(spotify-client/request+ (spotify/get-player))
       :refetchInterval 5000})

(defn playlists []
  #js {:queryKey #js ["playlists"]
       :queryFn #(spotify/get-all-playlists+ spotify-client/client)})

(defn playlist [playlist-id]
  #js {:queryKey #js ["playlists" playlist-id]
       :queryFn #(spotify-client/request+ (spotify/get-playlist playlist-id))})

(defn playlist-tracks [playlist-id]
  #js {:queryKey #js ["playlist-tracks" playlist-id]
       :queryFn #(spotify/get-playlist-tracks+ spotify-client/client playlist-id)})

(defn artist [artist-id]
  #js {:queryKey #js ["artists" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist artist-id))})

(defn artist-albums [artist-id]
  #js {:queryKey #js ["artist-albums" artist-id]
       :queryFn #(spotify/get-artist-albums+ spotify-client/client artist-id)})

(defn artist-top-tracks [artist-id]
  #js {:queryKey #js ["artist-top-tracks" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist-top-tracks artist-id))})

(defn artist-related-artists [artist-id]
  #js {:queryKey #js ["artist-related-artists" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist-related-artists artist-id))})

(defn devices []
  #js {:queryKey #js ["devices"]
       :queryFn #(spotify-client/request+ (spotify/get-player-devices))})

(defn album [album-id]
  #js {:queryKey #js ["albums" album-id]
       :queryFn #(spotify-client/request+ (spotify/get-album album-id))})

(defn album-tracks [album-id]
  #js {:queryKey #js ["album-tracks" album-id]
       :queryFn #(spotify/get-album-tracks+ spotify-client/client album-id)})
