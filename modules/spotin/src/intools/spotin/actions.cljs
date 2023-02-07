(ns intools.spotin.actions)

(def action-separator
  {:name ""})

(def player-actions
  [{:id :play-pause
    :name "play/pause"
    :shortcut "z"}
   {:id :next
    :name "next"
    :shortcut "n"
    :event [:spotin/dispatch-fx :next]}
   {:id :previous
    :name "previous"
    :shortcut "b"
    :event [:spotin/player-previous]}
   {:id :shuffle
    :name "shuffle"
    :shortcut "s"
    :event [:spotin/dispatch-fx :spotin/player-toggle-shuffle]}
   {:id :repeat
    :name "repeat"
    :shortcut "p"
    :event [:spotin/dispatch-fx :spotin/player-toggle-repeat]}
   {:id :spotin/open-currently-playing
    :name "currently playing"
    :shortcut "."
    :event [:spotin/open-currently-playing]}
   {:id :spotin/player-volume-up
    :name "volume up 10%"
    :shortcut "+"
    :event [:spotin/dispatch-fx :spotin/player-volume-up]}
   {:id :spotin/player-volume-down
    :name "volume down 10%"
    :shortcut "-"
    :event [:spotin/dispatch-fx :spotin/player-volume-down]}
   {:id :spotin/player-seek-forward
    :name "seek forward 10s"
    :shortcut ">"
    :event [:spotin/dispatch-fx :spotin/player-seek-forward]}
   {:id :spotin/player-seek-backward
    :name "seek back 10s"
    :shortcut "<"
    :event [:spotin/dispatch-fx :spotin/player-seek-backward]}
   {:id :spotin/devices
    :name "devices"
    :shortcut "e"
    :event [:spotin/open-devices-menu]}])

(def playlist-actions
  [{:id :playlist-play
    :name "play"}
   {:id :playlist-rename
    :name "rename"}
   {:id :playlist-edit-description
    :name "edit description"}
   {:id :playlist-unfollow
    :name "delete"}
   ;; or private
   ;;{:id :playlist-make-public
   ;; :name "TBD make public"}
   ;;;; Copy Spotify URI / Copy embed code
   ;;{:id :playlist-share
   ;; :name "TBD share"}
   {:id :playlist-open
    :name "open"
    :shortcut "⏎"
    :event [:select-playlist]}
   ;;{:id :playlist-create
   ;; :name "TBD create playlist"}
   ;;{:id :folder-create
   ;; :name "TBD create folder"}
   action-separator
   {:id :spotin/open-random-playlist
    :name "open random"
    :shortcut "o"
    :event [:spotin/open-random-playlist]}
   {:id :spotin/refresh-playlists
    :name "refresh"
    :shortcut "r"}
   {:id :spotin/start-playlist-search
    :name "search"
    :shortcut "/"
    :event [:spotin/start-playlist-search]}])

(def playlists-actions
  [{:id :playlists-mix
    :name "mix"}])

(def track-actions
  [{:name "open artist"
    :event [:spotin/open-track-artist]}
   {:name "open album"
    :event [:spotin/open-track-album]}
   ;; - TBD Go to song radio
   {:name "add to queue"
    :event [:spotin/queue-track]}
   ;;{:id :like
   ;; :name "TBD add to Liked Songs"}
   ;;{:id :add-to-library
   ;; :name "TBD add to playlist"}
   ;;{:name "TBD share"}
     ;;{:name "TBD remove from this playlist"}
   {:name "play"
    :shortcut "⏎"
    :event [:spotin/dispatch-fx :track-play]}])

(def tracks-actions
  (conj track-actions
        {:id :spotin/start-track-search
         :name "search"
         :shortcut "/"
         :event [:spotin/set-track-search ""]}))

(def album-actions
  [{:name "play"
    :event [:spotin/dispatch-fx :album-play]}
   {:name "open"
    :shortcut "⏎"
    :event [:spotin/open-album]}])
   ; {:name "TBD go to album radio"}
   ; {:name "TBD Save to Your Library"}
   ; {:name "TBD Add to playlist"}
   ; {:name "TBD Share"}])

(def artist-tracks-actions
  (into [{:name "play artist"
          :event [:spotin/dispatch-fx :artist-context-play]}]
        ;; dropping the `open artist` actions, it is not useful since we are already on artist's screen
        (drop 1 tracks-actions)))

(def artist-actions
  [{:name "play artist"
    :event [:spotin/dispatch-fx :artist-play]}
   {:name "open"
    :shortcut "⏎"
    :event [:spotin/open-artist]}])
   ;;{:name "TBD Follow"}])
   ;;{:name "TBD Go to artist radio"}
   ;;{:name "TBD Share"}])

(def ^:private included-in-shortcuts-bar?
  #{:play-pause
    :next
    :previous
    :spotin/devices})

(def shortcuts-bar-actions
  (concat [{:shortcut "x"
            :name "menu"}]
          [{:shortcut "/"
            :name "search"}]
          (->> player-actions
               (filter (fn [{:keys [shortcut id]}]
                         (and (some? shortcut)
                              (included-in-shortcuts-bar? id)))))
          [{:shortcut "q"
            :name "quit"}]))
