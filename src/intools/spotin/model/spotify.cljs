(ns intools.spotin.model.spotify
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def fsp (js/require "fs/promises"))
(def rp (js/require "request-promise-native"))

(def client-id (.. js/process -env -SPOTIFY_CLIENT_ID))
(def client-secret (.. js/process -env -SPOTIFY_CLIENT_SECRET))
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

(def refresh-token (.. js/process -env -SPOTIFY_REFRESH_TOKEN))

(defonce ^:dynamic *access-token* nil)
(defonce ^:dynamic *before-request-callback* nil)
(defonce ^:dynamic *after-request-callback* nil)

(defn authorization-url []
  (str "https://accounts.spotify.com/authorize"
       "?response_type=code"
       "&client_id=" client-id
       "&scope=" (js/encodeURIComponent (str/join " " scopes))
       "&redirect_uri=" redirect-uri))

(defn tokens-from-authorization-code+ [code]
  (let [auth-options (clj->js
                      {:method "POST"
                       :url "https://accounts.spotify.com/api/token"
                       :headers {:Authorization (str "Basic "
                                                     (-> (js/Buffer. (str client-id ":" client-secret))
                                                         (.toString "base64")))}
                       :form {:grant_type "authorization_code"
                              :code code
                              :redirect_uri redirect-uri}
                       :json true})]
    (-> (rp auth-options)
        (.then (fn [^js body]
                 (js/console.log body)
                 body)))))

(defn refresh-token+ [rtoken]
  (let [auth-options (clj->js
                      {:method "POST"
                       :url "https://accounts.spotify.com/api/token"
                       :headers {:Authorization (str "Basic "
                                                     (-> (js/Buffer. (str client-id ":" client-secret))
                                                         (.toString "base64")))}
                       :form {:grant_type "refresh_token"
                              :refresh_token rtoken}
                       :json true})]
    (-> (rp auth-options)
        (.then (fn [^js body]
                 (let [token (.-access_token body)]
                   (set! *access-token* token)
                   token))))))
        ; (.catch (fn [err]
        ;           (println "Request error:" err))))))

(defn expired-token? [^js e]
  (and (= (.-statusCode e) 401)
       (= (some-> e .-error .-error .-message) "The access token expired")))

(defn add-authorization-header [opts]
  (update opts :headers assoc :Authorization (str "Bearer " *access-token*)))

(defn authorized-request+ [opts]
  (-> (js/Promise.resolve)
      (.then #(rp (clj->js (add-authorization-header opts))))))

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
      (.finally #(when *after-request-callback*
                   (*after-request-callback* opts)))))

(def request+ request-with-auto-refresh+)

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

(defn cache-path [k]
  (str ".cache/" k ".json"))

(defn load-json+ [path]
  (-> (.readFile fsp path "utf-8")
      (.then #(js/JSON.parse %))))

(defn store-json+ [path body]
  (.writeFile fsp path (js/JSON.stringify body nil 2)))

(defn load-cached-json+ [k]
  (load-json+ (cache-path k)))

(defn store-cached-json+ [k body]
  (store-json+ (cache-path k) body))

(defn load-cached-edn+ [k]
  (-> (.readFile fsp (cache-path k) "utf-8")
      (.then #(edn/read-string %))))

(defn store-cached-edn+ [k body]
  (.writeFile fsp (cache-path k) (prn-str body)))

(defn cache-edn+ [k f]
  (-> (js/Promise.resolve)
      (.then f)
      (.then (fn [body]
               (-> (store-cached-edn+ k body)
                   (.then (fn [] body)))))))

(defn cache-result+ [k f]
  (-> (f)
      (.then (fn [body]
               (-> (store-json+ (cache-path k) body)
                   (.then (fn [] body)))))))

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

(defn get-all-playlists+ []
  (let [!items (atom nil)]
    (letfn [(fetch-page+ [url]
              (-> (get+ url)
                  (.then (fn [body]
                           (let [{:keys [items next]} (js->clj body :keywordize-keys true)]
                             (swap! !items concat items)
                             (if next
                               (fetch-page+ next)
                               {:items @!items}))))))]
      (fetch-page+ "https://api.spotify.com/v1/me/playlists?limit=50"))))

(defn get-playlist+ [playlist-id]
  (cached-get+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))))

(defn get-playlist-tracks+ [playlist-id]
  (cached-get+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/tracks")))

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
  (cached-get+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id) "/tracks")))

(defn get-player+ []
  (authorized-get+ "https://api.spotify.com/v1/me/player"))

(defn get-player-devices+ []
  (-> (authorized-get+ "https://api.spotify.com/v1/me/player/devices")
      (.then #(js->clj % :keywordize-keys true))))

;; TODO pagination
;; :tracks :next
;; (when next)
;; concat :items

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
