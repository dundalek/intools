(ns intools.spotin.model.spotify
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [node-fetch :as fetch]))

(def path (js/require "path"))
(def fsp (js/require "fs/promises"))
(def env-paths (js/require "env-paths"))

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
                                                    (-> (js/Buffer. (str client-id ":" client-secret))
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
                                                    (-> (js/Buffer. (str client-id ":" client-secret))
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

(defn authorized-put+
  ([url] (authorized-put+ url nil))
  ([url body]
   (request-with-auto-refresh+ (cond-> {:method "PUT"
                                        :url url
                                        :json true}
                                 (some? body) (assoc :body (clj->js body))))))

(defn authorized-post+
  ([url] (authorized-post+ url nil))
  ([url body]
   (request-with-auto-refresh+ (cond-> {:method "POST"
                                        :url url
                                        :json true}
                                 (some? body) (assoc :body (clj->js body))))))

(def cache-dir
  (-> (env-paths "spotin" #js {:suffix ""})
      .-cache
      (path.join "v0")))

(defonce ensure-cache-dir+ (delay (.mkdir fsp cache-dir #js {:recursive true})))

(defn cache-path [k]
  (.join path cache-dir k))

(defn load-json+ [path]
  (-> @ensure-cache-dir+
      (.then #(.readFile fsp path "utf-8"))
      (.then #(js/JSON.parse %))))

(defn store-json+ [path body]
  (-> @ensure-cache-dir+
      (.then #(.writeFile fsp path (js/JSON.stringify body)))))

(defn load-cached-json+ [k]
  (load-json+ (cache-path k)))

(defn store-cached-json+ [k body]
  (store-json+ (cache-path k) body))

(defn load-cached-edn+ [k]
  (-> @ensure-cache-dir+
      (.then #(.readFile fsp (cache-path k) "utf-8"))
      (.then #(edn/read-string %))))

(defn store-cached-edn+ [k body]
  (-> @ensure-cache-dir+
      (.then #(.writeFile fsp (cache-path k) (prn-str body)))))

(defn cache-edn+ [k f]
  (-> (js/Promise.resolve)
      (.then f)
      (.then (fn [body]
               (-> (store-cached-edn+ k body)
                   (.then (fn [] body))
                   (.catch (fn [_e] body)))))))

(defn cache-result+ [k f]
  (-> (js/Promise.resolve)
      (.then f)
      (.then (fn [body]
               (-> (store-json+ (cache-path k) body)
                   (.then (fn [] body))
                   (.catch (fn [_e] body)))))))

(defn with-cached-json+ [k f]
  (-> (load-cached-json+ k)
      (.catch (fn [_e]
                (cache-result+ k f)))))

(defn cache-key [url]
  (str/replace url #"[^\w+]" "_"))

(defn authorized-get+ [url]
  (request-with-auto-refresh+ {:method "GET"
                               :url url
                               :json true}))

(defn get-request [url]
  {:method "GET"
   :url url
   :json true})

(defn get+ [url]
  (request-with-auto-refresh+ (get-request url)))

(defn delete-request [url]
  {:method "DELETE"
   :url url
   :json true})

(defn delete+ [url]
  (request-with-auto-refresh+ (delete-request url)))

;; TODO pass query params as map
(defn cached-get+ [url]
  (with-cached-json+ (cache-key url)
    #(get+ url)))

(defn get-playlists []
  (get-request "https://api.spotify.com/v1/me/playlists"))

(defn get-playlists+ []
  (cached-get+ "https://api.spotify.com/v1/me/playlists"))

(defn paginated-get+ [initial-url]
  (let [!items (atom nil)]
    (letfn [(fetch-page+ [url]
              (-> (get+ url)
                  (.then (fn [body]
                           (let [{:keys [items next]} (js->clj body :keywordize-keys true)]
                             (swap! !items concat items)
                             (if next
                               (fetch-page+ next)
                               {:items @!items}))))))]
      (fetch-page+ initial-url))))

(defn get-all-playlists+ []
  (paginated-get+ "https://api.spotify.com/v1/me/playlists?limit=50"))

(defn get-playlist-tracks+ [playlist-id]
  ;; TODO use paginated-get+
  (-> (cached-get+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/tracks?limit=100"))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn playlist-change+ [playlist-id body]
  (authorized-put+ (str "https://api.spotify.com/v1/playlists/" playlist-id) body))

(defn playlist-rename+ [playlist-id name]
  (playlist-change+ playlist-id {:name name}))

(defn playlist-change-description+ [playlist-id description]
  (playlist-change+ playlist-id {:description description}))

(defn playlist-unfollow+ [playlist-id]
  (delete+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/followers")))

(defn get-album+ [album-id]
  (cached-get+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id))))

(defn get-album-tracks+ [album-id]
  ;; TODO use paginated-get+
  (-> (cached-get+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id) "/tracks?limit=50"))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn get-artist+ [artist-id]
  (cached-get+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id))))

(defn get-artist-albums+ [artist-id]
  ;; TODO use paginated-get+
  ;; TODO include appears_on,compilation album groups later, needs thinking about how to fit them in the UI
  (-> (cached-get+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/albums?limit=50&include_groups=album,single"))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn get-artist-top-tracks+ [artist-id]
  (-> (cached-get+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/top-tracks?market=from_token"))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn get-artist-related-artists+ [artist-id]
  (-> (cached-get+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/related-artists"))
      (.then (fn [body] (js->clj body :keywordize-keys true)))))

(defn get-player+ []
  (authorized-get+ "https://api.spotify.com/v1/me/player"))

(defn get-player-devices+ []
  (-> (authorized-get+ "https://api.spotify.com/v1/me/player/devices")
      (.then #(js->clj % :keywordize-keys true))))

(defn create-playlist+ [user-id {:keys [_name _public _collaborative _description] :as opts}]
  (authorized-post+ (str "https://api.spotify.com/v1/users/" user-id "/playlists")
                    (merge {:public false :collaborative false} opts)))

(defn player-transfer+ [device-id]
  (let [opts #js {:device_ids #js [device-id]}]
    (authorized-put+ "https://api.spotify.com/v1/me/player" opts)))

(defn player-volume+ [volume-percent]
  (authorized-put+ (str "https://api.spotify.com/v1/me/player/volume?volume_percent=" (js/encodeURIComponent volume-percent))))

(defn player-seek+ [position-ms]
  (authorized-put+ (str "https://api.spotify.com/v1/me/player/seek?position_ms=" (js/encodeURIComponent position-ms))))

(defn player-play+
  ([] (player-play+ nil))
  ([opts]
   (authorized-put+ "https://api.spotify.com/v1/me/player/play" opts)))

(defn player-pause+ []
  (authorized-put+ "https://api.spotify.com/v1/me/player/pause"))

(defn player-play-pause+ []
  (-> (get-player+)
      (.then (fn [^js body]
               (if (.-is_playing body)
                 (player-pause+)
                 (player-play+))))))

(defn player-shuffle+ [state]
  (authorized-put+ (str "https://api.spotify.com/v1/me/player/shuffle?state="
                        (if state "true" "false"))))

(defn player-toggle-shuffle+ []
  (-> (get-player+)
      (.then (fn [^js body]
               (player-shuffle+ (not (.-shuffle_state body)))))))

;; repeat_state
(def repeat-state-transition
  {"context" "track"
   "track" "off"
   "off" "context"})

(defn player-repeat+ [state]
  (authorized-put+ (str "https://api.spotify.com/v1/me/player/repeat?state=" state)))

(defn player-toggle-repeat+ []
  (-> (get-player+)
      (.then (fn [^js body]
               (player-repeat+ (repeat-state-transition (.-repeat_state body)))))))

(defn player-next+ []
  (authorized-post+ "https://api.spotify.com/v1/me/player/next"))

(defn player-previous+ []
  (authorized-post+ "https://api.spotify.com/v1/me/player/previous"))

(defn user-id+ []
  (-> (authorized-get+ "https://api.spotify.com/v1/me")
      (.then (fn [^js body]
               (.-id body)))))
