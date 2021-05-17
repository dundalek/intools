(ns intools.spotin.app.db)

(def default-db
  {:playlists {}
   :playlist-order []
   :playlist-tracks {}
   :playlist-search-query nil
   :selected-playlist nil
   :actions nil
   :actions-search-query nil
   :track-search-query nil
   :active-input-panel nil
   :confirmation-modal nil
   :playback-status nil
   :routes nil})
