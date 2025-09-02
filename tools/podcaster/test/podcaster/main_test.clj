(ns podcaster.main-test
  (:require
   [babashka.fs :as fs]
   [lazytest.core :refer [defdescribe describe it expect]]
   [next.jdbc :as jdbc]
   [podcaster.main :as sut]))

;; These Claude-generated tests have some coverage, but are not very good. Should get human-rewrite.

(def test-episodes
  [{:id 0 :feeditem 101 :feed 1 :title "Episode 1" :pubdate 1000 :feed_title "Podcast A" :author "Author A"}
   {:id 1 :feeditem 102 :feed 1 :title "Episode 2" :pubdate 2000 :feed_title "Podcast A" :author "Author A"}
   {:id 2 :feeditem 103 :feed 2 :title "Episode 3" :pubdate 3000 :feed_title "Podcast B" :author "Author B"}
   {:id 3 :feeditem 104 :feed 2 :title "Episode 4" :pubdate 4000 :feed_title "Podcast B" :author "Author B"}
   {:id 4 :feeditem 105 :feed 1 :title "Episode 5" :pubdate 5000 :feed_title "Podcast A" :author "Author A"}])

(defn- create-test-db [episodes]
  (let [temp-file (fs/create-temp-file {:prefix "test-podcast-" :suffix ".db"})
        db-spec {:dbtype "sqlite" :dbname (str temp-file)}]

    ;; Create schema
    (jdbc/execute! db-spec
                   ["CREATE TABLE Feeds (
                       id INTEGER PRIMARY KEY,
                       title TEXT,
                       author TEXT)"])

    (jdbc/execute! db-spec
                   ["CREATE TABLE FeedItems (
                       id INTEGER PRIMARY KEY,
                       title TEXT,
                       pubDate INTEGER,
                       feed INTEGER)"])

    (jdbc/execute! db-spec
                   ["CREATE TABLE Queue (
                       id INTEGER PRIMARY KEY,
                       feeditem INTEGER,
                       feed INTEGER)"])

    (jdbc/execute! db-spec
                   ["CREATE TABLE FeedMedia (
                       id INTEGER PRIMARY KEY,
                       feeditem INTEGER,
                       duration INTEGER)"])

    ;; Insert test data
    (jdbc/execute! db-spec ["INSERT INTO Feeds (id, title, author) VALUES (1, 'Podcast A', 'Author A')"])
    (jdbc/execute! db-spec ["INSERT INTO Feeds (id, title, author) VALUES (2, 'Podcast B', 'Author B')"])

    (doseq [episode episodes]
      (jdbc/execute! db-spec
                     ["INSERT INTO FeedItems (id, title, pubDate, feed) VALUES (?, ?, ?, ?)"
                      (:feeditem episode) (:title episode) (:pubdate episode) (:feed episode)])
      (jdbc/execute! db-spec
                     ["INSERT INTO Queue (id, feeditem, feed) VALUES (?, ?, ?)"
                      (:id episode) (:feeditem episode) (:feed episode)])
      (jdbc/execute! db-spec
                     ["INSERT INTO FeedMedia (feeditem, duration) VALUES (?, ?)"
                      (:feeditem episode) (+ 1800000 (* (:id episode) 300000))]))  ; ~30-60 min durations

    [temp-file db-spec]))

(defn with-test-db
  "Create a temporary test database, call f with db-path, and cleanup afterwards.
   f is called with a single argument: the database path as a string."
  [episodes f]
  (let [[temp-file db-spec] (create-test-db episodes)
        db-path (str temp-file)]
    (try
      (f db-path)
      (finally
        (fs/delete temp-file)))))

(defdescribe cutoff-calculation
  (it "calculates 2/3 rounded down to nearest 10"
      (expect (= 50 (sut/calculate-cutoff 80)))
      (expect (= 60 (sut/calculate-cutoff 100)))
      (expect (= 100 (sut/calculate-cutoff 150)))
      (expect (= 0 (sut/calculate-cutoff 10)))
      (expect (= 0 (sut/calculate-cutoff 14))))

  (it "handles edge cases"
      (expect (= 0 (sut/calculate-cutoff 0)))
      (expect (= 0 (sut/calculate-cutoff 5)))))

(defdescribe episode-pruning
  (it "returns episodes after cutoff in reverse order"
      (let [queue test-episodes
            cutoff 2
            result (sut/get-episodes-to-prune queue cutoff)]
        (expect (= 3 (count result)))
        (expect (= [105 104 103] (map :feeditem result)))))

  (it "returns empty when cutoff >= queue size"
      (let [queue test-episodes
            cutoff 5
            result (sut/get-episodes-to-prune queue cutoff)]
        (expect (empty? result))))

  (it "returns all episodes when cutoff is 0"
      (let [queue test-episodes
            cutoff 0
            result (sut/get-episodes-to-prune queue cutoff)]
        (expect (= 5 (count result)))
        (expect (= [105 104 103 102 101] (map :feeditem result))))))

(defdescribe database-operations
  (describe "load-queue"
            (it "loads queue with episode and feed information"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [db-spec {:dbtype "sqlite" :dbname db-path}
                          result (sut/load-queue db-spec)]
                      (expect (= 5 (count result)))
                      (expect (= "Episode 1" (:title (first result))))
                      (expect (= "Podcast A" (:feed_title (first result)))))))))

  (describe "update-queue"
            (it "replaces entire queue with new episodes"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [db-spec {:dbtype "sqlite" :dbname db-path}
                          new-queue (take 3 test-episodes)
                          _ (sut/update-queue db-spec new-queue)
                          result (jdbc/execute! db-spec ["SELECT * FROM Queue ORDER BY id"])]
                      (expect (= [[0 101] [1 102] [2 103]] (map (juxt :Queue/id :Queue/feeditem) result))))))))

  (describe "archive operations"
            (it "records archived episodes"
                (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                      archive-db-spec {:dbtype "sqlite" :dbname (str archive-file)}
                      _ (sut/init-archive-db archive-db-spec)
                      episodes-to-archive (take 2 test-episodes)
                      _ (sut/archive-episodes archive-db-spec episodes-to-archive "cutoff")
                      result (jdbc/execute! archive-db-spec ["SELECT * FROM archived_episodes"])]
                  (expect (= 2 (count result)))
                  (expect (= [101 102] (map :archived_episodes/feeditem_id result)))
                  (expect (= "cutoff" (:archived_episodes/reason (first result))))
                  (fs/delete archive-file)))

            (it "records episode bumps"
                (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                      archive-db-spec {:dbtype "sqlite" :dbname (str archive-file)}
                      _ (sut/init-archive-db archive-db-spec)
                      _ (sut/record-bump archive-db-spec 101 5 0)
                      result (jdbc/execute! archive-db-spec ["SELECT * FROM bumped_episodes"])]
                  (expect (= 1 (count result)))
                  (expect (= 101 (:bumped_episodes/feeditem_id (first result))))
                  (expect (= 5 (:bumped_episodes/queue_position_before (first result))))
                  (expect (= 0 (:bumped_episodes/queue_position_after (first result))))
                  (fs/delete archive-file)))))

(defdescribe integration-tests
  (describe "create-initial-state"
            (it "creates complete initial state from database"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          state (sut/create-initial-state db-path (str archive-file))]
                      (expect (= 5 (:original-size state)))
                      (expect (= 0 (:cutoff state))) ; 5 * 2/3 = 3.33, rounded down to nearest 10 = 0
                      (expect (= 5 (count (:episodes-to-prune state))))
                      (expect (= [] (:kept-episodes state)))
                      (expect (= 5 (:current-archive-count state)))
                      (expect (contains? state :main-db-spec))
                      (expect (contains? state :archive-db-spec))
                      (fs/delete archive-file)))))

            (it "creates backup database file"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [backup-path (sut/create-backup db-path)]
                      (expect (fs/exists? backup-path))
                      (expect (not= db-path backup-path))
                      (expect (.endsWith backup-path "-updated.db"))
                      (fs/delete backup-path))))))

  (describe "full workflow simulation"
            (it "simulates complete queue management workflow"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))
                          ;; Simulate keeping one episode
                          kept-episodes #{103}
                          episodes-to-archive (remove #(contains? kept-episodes (:feeditem %))
                                                      (:episodes-to-prune initial-state))
                          kept-episode-objects (filter #(contains? kept-episodes (:feeditem %))
                                                       (:episodes-to-prune initial-state))
                          remaining-queue (take (:cutoff initial-state) (:queue initial-state))
                          new-queue (concat kept-episode-objects remaining-queue)]

                      ;; Clean up any previous archive data and archive episodes
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM archived_episodes"])
                      (sut/archive-episodes (:archive-db-spec initial-state) episodes-to-archive "cutoff")

                      ;; Update queue
                      (sut/update-queue (:main-db-spec initial-state) new-queue)

                      ;; Verify results
                      (let [updated-queue (sut/load-queue (:main-db-spec initial-state))
                            archived-episodes (jdbc/execute! (:archive-db-spec initial-state)
                                                             ["SELECT * FROM archived_episodes"])]
                        (expect (= 1 (count updated-queue))) ; Only kept episode remains
                        (expect (= 103 (:feeditem (first updated-queue))))
                        (expect (= 4 (count archived-episodes))) ; 4 episodes archived
                        (expect (= #{101 102 104 105} (set (map :archived_episodes/feeditem_id archived-episodes))))
                        (fs/delete archive-file))))))))

(defdescribe event-dispatching
  (describe "dispatch function"
            (it "handles ::adjust-cutoff event"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      (it "dispatches adjust cutoff event"
                          (sut/dispatch [::sut/adjust-cutoff {:cutoff 2}])

                          (let [updated-state @sut/!app-state]
                            (expect (= 2 (:cutoff updated-state)))
                            (expect (= 3 (count (:episodes-to-prune updated-state))))
                            (expect (= 3 (:current-archive-count updated-state)))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))

            (it "handles ::keep-episode event"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      (it "dispatches keep episode event"
                          (sut/dispatch [::sut/keep-episode {:episode-id 103}])

                          (let [updated-state @sut/!app-state]
                            (expect (= [103] (:kept-episodes updated-state)))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))

            (it "handles ::unkeep-episode event"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state (assoc initial-state :kept-episodes [103 104]))

                      (it "dispatches unkeep episode event"
                          (sut/dispatch [::sut/unkeep-episode {:episode-id 103}])

                          (let [updated-state @sut/!app-state]
                            (expect (= [104] (:kept-episodes updated-state)))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))

            (it "handles ::save-changes event"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      ;; Clean up any existing archive data first
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM archived_episodes"])
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM bumped_episodes"])

                      ;; Set cutoff to 2 and keep episode 104
                      (reset! sut/!app-state (-> initial-state
                                                 (assoc :cutoff 2)
                                                 (assoc :kept-episodes [104])
                                                 (assoc :episodes-to-prune (drop 2 test-episodes))))

                      ;; Capture output to verify logging
                      (let [output (java.io.StringWriter.)]
                        (binding [*out* output]
                          (sut/dispatch [::sut/save-changes {}]))

                        (it "verifies output message is printed"
                            (expect (.contains (str output) "Archived"))
                            (expect (.contains (str output) "kept"))))

                      ;; Verify database changes
                      (let [updated-queue (sut/load-queue (:main-db-spec @sut/!app-state))
                            archived-episodes (jdbc/execute! (:archive-db-spec @sut/!app-state)
                                                             ["SELECT * FROM archived_episodes"])
                            episode-bumps (jdbc/execute! (:archive-db-spec @sut/!app-state)
                                                         ["SELECT * FROM bumped_episodes"])]
                        (it "should have kept episode at start + original first 2 episodes"
                            (expect (= 3 (count updated-queue)))
                            (expect (= 104 (:feeditem (first updated-queue)))) ; kept episode moved to front
                            (expect (= [101 102] (map :feeditem (rest updated-queue))))) ; original first 2 remain

                        (it "should archive episodes 103 and 105"
                            (expect (= 2 (count archived-episodes)))
                            (expect (= #{103 105} (set (map :archived_episodes/feeditem_id archived-episodes)))))

                        (it "should record episode 104 bump with correct positions"
                            (expect (= 1 (count episode-bumps)))
                            (expect (= 104 (:bumped_episodes/feeditem_id (first episode-bumps))))
                            (expect (= 3 (:bumped_episodes/queue_position_before (first episode-bumps)))) ; Was at position 3 (cutoff 2 + index 1)
                            (expect (= 0 (:bumped_episodes/queue_position_after (first episode-bumps)))))) ; Moved to position 0)

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path @sut/!app-state))
                        (fs/delete (:db-path @sut/!app-state)))
                      (fs/delete archive-file)))))

            (it "handles multiple episode bumps with correct queue positions"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      ;; Clean up any existing archive data first
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM archived_episodes"])
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM bumped_episodes"])

                      ;; Set cutoff to 1 and keep episodes 103 and 105 (in that order)
                      (reset! sut/!app-state (-> initial-state
                                                 (assoc :cutoff 1)
                                                 (assoc :kept-episodes [103 105])
                                                 (assoc :episodes-to-prune (drop 1 test-episodes))))

                      (sut/dispatch [::sut/save-changes {}])

                      ;; Verify database changes
                      (let [updated-queue (sut/load-queue (:main-db-spec @sut/!app-state))
                            episode-bumps (jdbc/execute! (:archive-db-spec @sut/!app-state)
                                                         ["SELECT * FROM bumped_episodes ORDER BY feeditem_id"])]
                        (it "should have kept episodes at correct positions in queue"
                            (expect (= 3 (count updated-queue)))
                            (expect (= [103 105 101] (map :feeditem updated-queue))))

                        (it "should record both episode bumps with correct positions"
                            (expect (= 2 (count episode-bumps)))
                            ;; Episode 103: was at position 2 (cutoff 1 + index 1), moved to position 0
                            (expect (= 103 (:bumped_episodes/feeditem_id (first episode-bumps))))
                            (expect (= 2 (:bumped_episodes/queue_position_before (first episode-bumps))))
                            (expect (= 0 (:bumped_episodes/queue_position_after (first episode-bumps))))
                            ;; Episode 105: was at position 4 (cutoff 1 + index 3), moved to position 1
                            (expect (= 105 (:bumped_episodes/feeditem_id (second episode-bumps))))
                            (expect (= 4 (:bumped_episodes/queue_position_before (second episode-bumps))))
                            (expect (= 1 (:bumped_episodes/queue_position_after (second episode-bumps))))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path @sut/!app-state))
                        (fs/delete (:db-path @sut/!app-state)))
                      (fs/delete archive-file)))))

            (it "handles unknown method gracefully"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      ;; Capture output to verify error logging
                      (let [output (java.io.StringWriter.)]
                        (binding [*out* output]
                          (sut/dispatch [::unknown-method {}]))

                        (expect (.contains (str output) "Unknown method")))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))))

(defdescribe state-management
  (describe "application state transitions"
            (it "handles multiple cutoff adjustments"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      (it "has initial state with cutoff 0 (5 episodes * 2/3 = 3.33, rounded to 0)"
                          (expect (= 0 (:cutoff @sut/!app-state)))
                          (expect (= 5 (:current-archive-count @sut/!app-state))))

                      (it "adjusts to cutoff 1"
                          (sut/dispatch [::sut/adjust-cutoff {:cutoff 1}])
                          (expect (= 1 (:cutoff @sut/!app-state)))
                          (expect (= 4 (:current-archive-count @sut/!app-state))))

                      (it "adjusts to cutoff 3"
                          (sut/dispatch [::sut/adjust-cutoff {:cutoff 3}])
                          (expect (= 3 (:cutoff @sut/!app-state)))
                          (expect (= 2 (:current-archive-count @sut/!app-state))))

                      (it "adjusts to cutoff 5 (no episodes to prune)"
                          (sut/dispatch [::sut/adjust-cutoff {:cutoff 5}])
                          (expect (= 5 (:cutoff @sut/!app-state)))
                          (expect (= 0 (:current-archive-count @sut/!app-state))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))

            (it "handles multiple episode keep/unkeep operations"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      (it "keeps multiple episodes"
                          (sut/dispatch [::sut/keep-episode {:episode-id 103}])
                          (sut/dispatch [::sut/keep-episode {:episode-id 104}])
                          (sut/dispatch [::sut/keep-episode {:episode-id 105}])

                          (expect (= [103 104 105] (:kept-episodes @sut/!app-state))))

                      (it "unkeeps one episode"
                          (sut/dispatch [::sut/unkeep-episode {:episode-id 104}])

                          (expect (= [103 105] (:kept-episodes @sut/!app-state))))

                      (it "tries to unkeep non-existent episode"
                          (sut/dispatch [::sut/unkeep-episode {:episode-id 999}])

                          (expect (= [103 105] (:kept-episodes @sut/!app-state))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))))

(defdescribe edge-cases
  (describe "edge case scenarios"
            (it "handles empty queue"
                (with-test-db []
                  (fn [db-path]
                    (let [initial-state (sut/create-initial-state db-path)]
                      (reset! sut/!app-state initial-state)

                      (expect (= 0 (:original-size @sut/!app-state)))
                      (expect (= 0 (:cutoff @sut/!app-state)))
                      (expect (= 0 (count (:episodes-to-prune @sut/!app-state))))

                      ;; Save changes with empty queue should work
                      (sut/dispatch [::sut/save-changes {}])

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))))))

            (it "handles single episode queue"
                (with-test-db [(first test-episodes)]
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      (reset! sut/!app-state initial-state)

                      (expect (= 1 (:original-size @sut/!app-state)))
                      (expect (= 0 (:cutoff @sut/!app-state))) ; 1 * 2/3 = 0.67, rounded to 0
                      (expect (= 1 (count (:episodes-to-prune @sut/!app-state))))

                      (it "keeps the single episode"
                          (sut/dispatch [::sut/keep-episode {:episode-id 101}])
                          (sut/dispatch [::sut/save-changes {}]))

                      (it "verifies episode remains in queue"
                          (let [updated-queue (sut/load-queue (:main-db-spec @sut/!app-state))]
                            (expect (= 1 (count updated-queue)))
                            (expect (= 101 (:feeditem (first updated-queue))))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))

            (it "handles all episodes kept scenario"
                (with-test-db test-episodes
                  (fn [db-path]
                    (let [archive-file (fs/create-temp-file {:prefix "test-archive-" :suffix ".db"})
                          initial-state (sut/create-initial-state db-path (str archive-file))]
                      ;; Clean up any existing archive data first
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM archived_episodes"])
                      (jdbc/execute! (:archive-db-spec initial-state) ["DELETE FROM bumped_episodes"])

                      (reset! sut/!app-state (assoc initial-state :cutoff 2))
                      (sut/dispatch [::sut/adjust-cutoff {:cutoff 2}]) ; 3 episodes to prune

                      (it "keeps all episodes that would be pruned"
                          (sut/dispatch [::sut/keep-episode {:episode-id 103}])
                          (sut/dispatch [::sut/keep-episode {:episode-id 104}])
                          (sut/dispatch [::sut/keep-episode {:episode-id 105}])

                          (sut/dispatch [::sut/save-changes {}]))

                      (it "verifies all 5 episodes remain, with kept ones moved to front"
                          (let [updated-queue (sut/load-queue (:main-db-spec @sut/!app-state))]
                            (expect (= 5 (count updated-queue)))
                            (expect (= [105 104 103] (map :feeditem (take 3 updated-queue))))
                            (expect (= [101 102] (map :feeditem (drop 3 updated-queue))))))

                      (it "verifies no episodes are archived from this test run"
                          (let [archived-episodes (jdbc/execute! (:archive-db-spec @sut/!app-state)
                                                                 ["SELECT * FROM archived_episodes"])]
                            (expect (empty? archived-episodes))))

                      ;; Cleanup archive db
                      (when (fs/exists? (:db-path initial-state))
                        (fs/delete (:db-path initial-state)))
                      (fs/delete archive-file)))))))
