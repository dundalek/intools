(ns podcaster.main
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [portal.api :as p]
   [portal.runtime :as prt]
   [portal.viewer :as pv]))

(def ^:private archive-dir (fs/path (fs/xdg-data-home "podcaster-portlet")))
(def ^:private archive-db-path (fs/path archive-dir "archive.db"))

(defn- ensure-archive-dir []
  (fs/create-dirs archive-dir))

(defn init-archive-db [db-spec]
  (ensure-archive-dir)
  (jdbc/execute! db-spec
                 ["CREATE TABLE IF NOT EXISTS archived_episodes (
                     id INTEGER PRIMARY KEY,
                     feeditem_id INTEGER NOT NULL,
                     archived_at INTEGER NOT NULL,
                     reason TEXT)"])
  (jdbc/execute! db-spec
                 ["CREATE TABLE IF NOT EXISTS bumped_episodes (
                     id INTEGER PRIMARY KEY,
                     feeditem_id INTEGER NOT NULL,
                     bumped_at INTEGER NOT NULL,
                     queue_position_before INTEGER,
                     queue_position_after INTEGER)"]))

(defn create-backup [db-path]
  (let [backup-path (str (fs/strip-ext db-path) "-updated.db")]
    (when-not (fs/exists? backup-path)
      (fs/copy db-path backup-path))
    backup-path))

(defn load-queue [db-spec]
  (jdbc/execute! db-spec
                 ["SELECT q.id, q.feeditem, q.feed,
                   fi.title, fi.pubDate as pubdate,
                   f.title as feed_title, f.author,
                   fm.duration
                   FROM Queue q
                   LEFT JOIN FeedItems fi ON q.feeditem = fi.id
                   LEFT JOIN Feeds f ON q.feed = f.id
                   LEFT JOIN FeedMedia fm ON fi.id = fm.feeditem
                   ORDER BY q.id"]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(defn load-archived-episodes [main-db-spec archive-db-spec]
  (let [archived-ids (->> (jdbc/execute! archive-db-spec
                                         ["SELECT feeditem_id, archived_at, reason FROM archived_episodes ORDER BY archived_at DESC"]
                                         {:builder-fn rs/as-unqualified-lower-maps}))
        feeditem-ids (map :feeditem_id archived-ids)]
    (if (empty? feeditem-ids)
      []
      (let [placeholders (clojure.string/join "," (repeat (count feeditem-ids) "?"))
            episode-data (jdbc/execute! main-db-spec
                                        (concat [(str "SELECT fi.id as feeditem_id, fi.title, fi.pubDate as pubdate,
                                                        f.title as feed_title, f.author,
                                                        fm.duration
                                                        FROM FeedItems fi
                                                        LEFT JOIN Feeds f ON fi.feed = f.id
                                                        LEFT JOIN FeedMedia fm ON fi.id = fm.feeditem
                                                        WHERE fi.id IN (" placeholders ")")]
                                                feeditem-ids)
                                        {:builder-fn rs/as-unqualified-lower-maps})
            episode-map (zipmap (map :feeditem_id episode-data) episode-data)]
        (->> archived-ids
             (map (fn [archived-info]
                    (merge archived-info (get episode-map (:feeditem_id archived-info)))))
             (remove #(nil? (:title %))))))))

(defn load-bumped-episodes [main-db-spec archive-db-spec]
  (let [bumped-ids (->> (jdbc/execute! archive-db-spec
                                       ["SELECT feeditem_id, bumped_at, queue_position_before, queue_position_after FROM bumped_episodes ORDER BY bumped_at DESC"]
                                       {:builder-fn rs/as-unqualified-lower-maps}))
        feeditem-ids (map :feeditem_id bumped-ids)]
    (if (empty? feeditem-ids)
      []
      (let [placeholders (clojure.string/join "," (repeat (count feeditem-ids) "?"))
            episode-data (jdbc/execute! main-db-spec
                                        (concat [(str "SELECT fi.id as feeditem_id, fi.title, fi.pubDate as pubdate,
                                                        f.title as feed_title, f.author,
                                                        fm.duration
                                                        FROM FeedItems fi
                                                        LEFT JOIN Feeds f ON fi.feed = f.id
                                                        LEFT JOIN FeedMedia fm ON fi.id = fm.feeditem
                                                        WHERE fi.id IN (" placeholders ")")]
                                                feeditem-ids)
                                        {:builder-fn rs/as-unqualified-lower-maps})
            episode-map (zipmap (map :feeditem_id episode-data) episode-data)]
        (->> bumped-ids
             (map (fn [bumped-info]
                    (merge bumped-info (get episode-map (:feeditem_id bumped-info)))))
             (remove #(nil? (:title %))))))))

(defn calculate-cutoff [queue-size]
  (let [two-thirds (* queue-size 2/3)]
    (* 10 (int (/ two-thirds 10)))))

(defn get-episodes-to-prune [queue cutoff]
  (->> queue
       (drop cutoff)
       (reverse)))

(defn archive-episodes [archive-db-spec episodes reason]
  (when (seq episodes)
    (let [params (mapv (fn [episode]
                         [(:feeditem episode)
                          (System/currentTimeMillis)
                          reason])
                       episodes)]
      (jdbc/execute-batch! archive-db-spec
                           "INSERT INTO archived_episodes (feeditem_id, archived_at, reason) VALUES (?, ?, ?)"
                           params
                           {}))))

(defn record-bump [archive-db-spec feeditem-id old-pos new-pos]
  (jdbc/execute! archive-db-spec
                 ["INSERT INTO bumped_episodes (feeditem_id, bumped_at, queue_position_before, queue_position_after)
                   VALUES (?, ?, ?, ?)"
                  feeditem-id
                  (System/currentTimeMillis)
                  old-pos
                  new-pos]))

(defn update-queue [main-db-spec new-queue]
  (jdbc/with-transaction [tx main-db-spec]
    (jdbc/execute! tx ["DELETE FROM Queue"])
    (when (seq new-queue)
      (let [params (mapv (fn [[idx episode]]
                           [idx (:feeditem episode) (:feed episode)])
                         (map-indexed vector new-queue))]
        (jdbc/execute-batch! tx
                             "INSERT INTO Queue (id, feeditem, feed) VALUES (?, ?, ?)"
                             params
                             {})))))

(defn- create-queue-state [main-db-spec archive-db-spec]
  (let [queue (load-queue main-db-spec)
        queue-size (count queue)
        cutoff (calculate-cutoff queue-size)
        episodes-to-prune (get-episodes-to-prune queue cutoff)]
        ;archived-episodes (load-archived-episodes main-db-spec archive-db-spec)]
    {:queue queue
     :original-size queue-size
     :cutoff cutoff
     :episodes-to-prune episodes-to-prune
     :kept-episodes []
     :current-archive-count (count episodes-to-prune)
     :archived-episodes nil
     :bumped-episodes nil
     :current-screen :queue}))

(defn create-initial-state
  ([db-path]
   (create-initial-state db-path archive-db-path))
  ([db-path archive-db-path]
   (let [backup-path (create-backup db-path)
         main-db-spec {:dbtype "sqlite" :dbname backup-path}
         archive-db-spec {:dbtype "sqlite" :dbname (str archive-db-path)}]
     (init-archive-db archive-db-spec)
     (let [queue-state (create-queue-state main-db-spec archive-db-spec)]
       (assoc queue-state
              :db-path backup-path
              :main-db-spec main-db-spec
              :archive-db-spec archive-db-spec)))))

(declare dispatch)

(defonce !app-state (atom nil))

(defn- load-viewer []
  (let [viewer-path (io/resource "podcaster/viewer.cljs")]
    (p/eval-str (slurp viewer-path))))

(defn open [db-path]
  (let [initial-state (with-meta (create-initial-state db-path)
                        {::pv/default :podcaster.viewer/main})]
    (reset! !app-state initial-state)
    (prt/register! #'dispatch)
    (p/open {:value !app-state
             :on-load load-viewer})))

(defn- -main [& args]
  (when-not (seq args)
    (println "Usage: ./podcaster path/to/AntennaPodBackup.db")
    (System/exit 1))

  (let [db-path (first args)]
    (open db-path)
    @(promise)))

(defn dispatch [[method data]]
  (case method
    ::adjust-cutoff
    (let [new-cutoff (:cutoff data)
          queue (:queue @!app-state)
          new-episodes-to-prune (get-episodes-to-prune queue new-cutoff)]
      (swap! !app-state assoc
             :cutoff new-cutoff
             :episodes-to-prune new-episodes-to-prune
             :current-archive-count (count new-episodes-to-prune)))

    ::keep-episode
    (let [episode-id (:episode-id data)]
      (swap! !app-state update :kept-episodes conj episode-id))

    ::unkeep-episode
    (let [episode-id (:episode-id data)]
      (swap! !app-state update :kept-episodes #(vec (remove #{episode-id} %))))

    ::save-changes
    (let [{:keys [main-db-spec archive-db-spec queue episodes-to-prune kept-episodes cutoff]} @!app-state
          kept-episodes-set (set kept-episodes)
          episodes-to-archive (remove #(contains? kept-episodes-set (:feeditem %)) episodes-to-prune)
          kept-episode-objects (filter #(contains? kept-episodes-set (:feeditem %)) episodes-to-prune)
          remaining-queue (take cutoff queue)
          new-queue (concat kept-episode-objects remaining-queue)]

      (init-archive-db archive-db-spec)
      (archive-episodes archive-db-spec episodes-to-archive "cutoff")

      (doseq [[new-pos episode] (map-indexed vector kept-episode-objects)]
        (let [old-pos (+ cutoff (.indexOf episodes-to-prune episode))]
          (record-bump archive-db-spec (:feeditem episode) old-pos new-pos)))

      (update-queue main-db-spec new-queue)
      (println (format "Archived %d episodes, kept %d episodes at top of queue"
                       (count episodes-to-archive)
                       (count kept-episode-objects)))

      ;; Reload the queue after saving changes
      (let [new-queue-state (create-queue-state main-db-spec archive-db-spec)]
        (swap! !app-state merge new-queue-state)))

    ::switch-screen
    (let [screen (:screen data)]
      (swap! !app-state assoc :current-screen screen)
      ;; Refresh archived episodes when switching to archived screen
      (when (= screen :archived)
        (let [{:keys [main-db-spec archive-db-spec]} @!app-state
              archived-episodes (load-archived-episodes main-db-spec archive-db-spec)]
          (swap! !app-state assoc :archived-episodes archived-episodes)))
      ;; Refresh bumped episodes when switching to bumped screen
      (when (= screen :bumped)
        (let [{:keys [main-db-spec archive-db-spec]} @!app-state
              bumped-episodes (load-bumped-episodes main-db-spec archive-db-spec)]
          (swap! !app-state assoc :bumped-episodes bumped-episodes))))

    (println "Unknown method:" method)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

(comment
  (open "tmp/AntennaPodBackup-2025-08-25-updated.db")
  (load-viewer)

  (create-initial-state "tmp/AntennaPodBackup-2025-08-25-updated.db")

  (let [main-db-spec {:dbtype "sqlite" :dbname "tmp/AntennaPodBackup-2025-08-25-updated.db"}
        archive-db-spec {:dbtype "sqlite" :dbname (str archive-db-path)}]
    (load-archived-episodes main-db-spec archive-db-spec)))
