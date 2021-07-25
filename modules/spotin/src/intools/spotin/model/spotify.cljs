(ns intools.spotin.model.spotify
  (:require [clojure.string :as str]
            [node-fetch :as fetch]))

(def client-id (.. js/process -env -SPOTIFY_CLIENT_ID))
(def client-secret (.. js/process -env -SPOTIFY_CLIENT_SECRET))
(def refresh-token (.. js/process -env -SPOTIFY_REFRESH_TOKEN))
(def redirect-uri "http://localhost:8888/callback")

(def scopes
  ["playlist-read-private"
   "playlist-read-collaborative"
   "playlist-modify-public"
   "playlist-modify-private"
   "user-read-playback-state"
   "user-modify-playback-state"
   "user-read-currently-playing"
   "user-library-read"])

(defonce ^:dynamic *access-token* nil)
(defonce ^:dynamic *before-request-callback* nil)
(defonce ^:dynamic *after-request-callback* nil)
(defonce ^:dynamic *request-error-callback* nil)

(defn uri->id [uri]
  (-> (str/split uri #":")
      (nth 2)))

(defn authorization-url []
  (str "https://accounts.spotify.com/authorize"
       "?response_type=code"
       "&scope=" (js/encodeURIComponent (str/join " " scopes))
       "&redirect_uri=" (js/encodeURIComponent redirect-uri)
       "&client_id=" (js/encodeURIComponent client-id)))

(defn encode-form-params [form-params]
  (reduce (fn [params [k v]]
            (.append params (name k) v)
            params)
          (js/URLSearchParams.)
          form-params))

(defn fetch-request [{:keys [url body form json method] :as opts}]
  (-> (fetch url (-> (cond-> opts
                       body (assoc :body (js/JSON.stringify body))
                       (and json (not form)) (update :headers assoc :Content-Type "application/json")
                       form (assoc :body (encode-form-params form)))
                     (dissoc :json :form)
                     (clj->js)))
      (.then (fn [response]
               (if (.-ok response)
                 (if (and json (not= (.-status response) 204))
                   (-> (.text response)
                       (.then (fn [text]
                                (if-not (str/blank? text)
                                  (js/JSON.parse text)
                                  response))))
                   response)
                 (throw response))))))

(defn tokens-from-authorization-code+ [code]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :form {:grant_type "authorization_code"
                             :code code
                             :redirect_uri redirect-uri}
                      :json true}]
    (-> (fetch-request auth-options)
        (.then (fn [^js body]
                 (js/console.log body)
                 body)))))

(defn refresh-token+ [rtoken]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :form {:grant_type "refresh_token"
                             :refresh_token rtoken}
                      :json true}]
    (-> (fetch-request auth-options)
        (.then (fn [^js body]
                 (let [token (.-access_token body)]
                   (set! *access-token* token)
                   token))))))

(defn expired-token? [^js e]
  (= (.-status e) 401))

(defn add-authorization-header [opts]
  (update opts :headers assoc :Authorization (str "Bearer " *access-token*)))

(defn authorized-request+ [opts]
  (-> (js/Promise.resolve)
      (.then #(fetch-request (add-authorization-header opts)))))

(defn request-with-auto-refresh+ [opts]
  (-> (js/Promise.resolve)
      (.then #(when *before-request-callback*
                (*before-request-callback* opts)))
      (.then #(when-not *access-token*
                (refresh-token+ refresh-token)))
      (.then #(authorized-request+ opts))
      (.catch (fn [e]
                (if (expired-token? e)
                  ;; We might get multiple refreshes for concurrent requests
                  (-> (refresh-token+ refresh-token)
                      (.then #(authorized-request+ opts)))
                  (throw e))))
      (.catch (fn [e]
                (when *request-error-callback*
                  (*request-error-callback* e opts))
                (throw e)))
      (.finally #(when *after-request-callback*
                   (*after-request-callback* opts)))))

(defn put+
  ([url] (put+ url nil))
  ([url body]
   (request-with-auto-refresh+ (cond-> {:method "PUT"
                                        :url url
                                        :json true}
                                 (some? body) (assoc :body (clj->js body))))))

(defn post+
  ([url] (post+ url nil))
  ([url body]
   (request-with-auto-refresh+ (cond-> {:method "POST"
                                        :url url
                                        :json true}
                                 (some? body) (assoc :body (clj->js body))))))

(defn get-request [url]
  {:method "GET"
   :url url
   :json true})

(defn get+ [url]
  (request-with-auto-refresh+ (get-request url)))

(defn get-clj+ [url]
  (-> (request-with-auto-refresh+ (get-request url))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn delete-request [url]
  {:method "DELETE"
   :url url
   :json true})

(defn delete+ [url]
  (request-with-auto-refresh+ (delete-request url)))

(defn get-playlists []
  (get-request "https://api.spotify.com/v1/me/playlists"))

(defn paginated-get+ [initial-url]
  (let [!items (atom nil)]
    (letfn [(fetch-page+ [url]
              (-> (get-clj+ url)
                  (.then (fn [{:keys [items next]}]
                           (swap! !items concat items)
                           (if next
                             (fetch-page+ next)
                             {:items @!items})))))]
      (fetch-page+ initial-url))))

(defn get-playlist+ [playlist-id]
  (get-clj+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))))

(defn get-all-playlists+ []
  (paginated-get+ "https://api.spotify.com/v1/me/playlists?limit=50"))

(defn get-playlist-tracks+ [playlist-id]
  ;; TODO use paginated-get+
  (get-clj+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/tracks?limit=100")))

(defn playlist-change+ [playlist-id body]
  (put+ (str "https://api.spotify.com/v1/playlists/" playlist-id) body))

(defn playlist-rename+ [playlist-id name]
  (playlist-change+ playlist-id {:name name}))

(defn playlist-change-description+ [playlist-id description]
  (playlist-change+ playlist-id {:description description}))

(defn playlist-unfollow+ [playlist-id]
  (delete+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/followers")))

(defn get-album+ [album-id]
  (get-clj+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id))))

(defn get-album-tracks+ [album-id]
  ;; TODO use paginated-get+
  (get-clj+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id) "/tracks?limit=50")))

(defn get-artist+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id))))

(defn get-artist-albums+ [artist-id]
  ;; TODO use paginated-get+
  ;; TODO include appears_on,compilation album groups later, needs thinking about how to fit them in the UI
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/albums?limit=50&include_groups=album,single")))

(defn get-artist-top-tracks+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/top-tracks?market=from_token")))

(defn get-artist-related-artists+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/related-artists")))

(defn get-player+ []
  (get-clj+ "https://api.spotify.com/v1/me/player"))

(defn get-player-devices+ []
  (get-clj+ "https://api.spotify.com/v1/me/player/devices"))

(defn create-playlist+ [user-id {:keys [_name _public _collaborative _description] :as opts}]
  (post+ (str "https://api.spotify.com/v1/users/" user-id "/playlists"
              (merge {:public false :collaborative false} opts))))

(defn player-transfer+ [device-id]
  (let [opts #js {:device_ids #js [device-id]}]
    (put+ "https://api.spotify.com/v1/me/player" opts)))

(defn player-volume+ [volume-percent]
  (put+ (str "https://api.spotify.com/v1/me/player/volume?volume_percent=" (js/encodeURIComponent volume-percent))))

(defn player-seek+ [position-ms]
  (put+ (str "https://api.spotify.com/v1/me/player/seek?position_ms=" (js/encodeURIComponent position-ms))))

(defn player-play+
  ([] (player-play+ nil))
  ([opts]
   (put+ "https://api.spotify.com/v1/me/player/play" opts)))

(defn player-pause+ []
  (put+ "https://api.spotify.com/v1/me/player/pause"))

(defn player-play-pause+ []
  (-> (get-player+)
      (.then (fn [{:keys [is_playing]}]
               (if is_playing
                 (player-pause+)
                 (player-play+))))))

(defn player-shuffle+ [state]
  (put+ (str "https://api.spotify.com/v1/me/player/shuffle?state="
             (if state "true" "false"))))

(defn player-toggle-shuffle+ []
  (-> (get-player+)
      (.then (fn [{:keys [shuffle_state]}]
               (player-shuffle+ (not shuffle_state))))))

;; repeat_state
(def repeat-state-transition
  {"context" "track"
   "track" "off"
   "off" "context"})

(defn player-repeat+ [state]
  (put+ (str "https://api.spotify.com/v1/me/player/repeat?state=" state)))

(defn player-toggle-repeat+ []
  (-> (get-player+)
      (.then (fn [{:keys [repeat_state]}]
               (player-repeat+ (repeat-state-transition repeat_state))))))

(defn player-next+ []
  (post+ "https://api.spotify.com/v1/me/player/next"))

(defn player-previous+ []
  (post+ "https://api.spotify.com/v1/me/player/previous"))

(defn user-id+ []
  (-> (get+ "https://api.spotify.com/v1/me")
      (.then (fn [^js body]
               (.-id body)))))
