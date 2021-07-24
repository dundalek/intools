(ns intools.spotin.app.db)

(def default-db
  {:playlist-search-query nil
   :albums {}
   :album-tracks {}
   :artists {}
   :actions nil
   :actions-search-query nil
   :track-search-query nil
   :active-input-panel nil
   :confirmation-modal nil
   :devices-menu false
   :playback-status nil
   :routes nil
   :playback-request-id nil
   :pending-requests 0
   :error nil})
