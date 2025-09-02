Implement a podcaster portlet inside `tools/podcaster` using the Portlet approach using guidelines from @doc/portlet.md

The goal of the tool is to solve the problem of podcast queue overload.
AntennaPod podcast player is used to get new podcast episodes in Inbox.
The user selects episodes from Inbox and adds them to Queue.
The problem is that Queue grows indefinitely over time and becomes unmanageable.
The player app does not support advanced queuing operations, so we will create complementary utility tool.

The tool will work by loading sqlite export from AntennaPod, a source database.
User will be presented with a UI showing list of episodes from the bottom of the queue to be pruned.

When episodes are forgotten and archived, they will be removed from the queue in the source database.
Once user is done, the database will be copied and imported back to the podcast player app.

Keep the implementation as simple as possible, this a quickly made single-purpose tool. Make assumptions and hardcode decisions.

### Queue

By default the cutoff will be 2/3 of the queue size rounded down to nearest 10. For example:
80 .. 50
100 .. 60
150 .. 100

### Keeping/Bumping

The user can select to "keep" episodes, which will move/bump them to the top of the queue.
Store the bumps in archive database - which episode and datetime. (to record data in case we add a feature in the future to resurface old episodes based on time with exponential curve ala spaced repetition)

### Forgetting and archive

The episodes below a cutoff line that were not bumped will be forgotten.
We want to avoid feeling of FOMO, we will keep an archive of forgotten episodes in a separate sqlite db archive.db
We can stored only feeditem ids, we will assume user will only archive podcasts and not remove historical data.

## Usage

Run passing a database file as `./podcaster path/to/AntennaPodBackup.db`

### User Interface

Implement a custom web-based portal viewer components.

Show details: original queue size, target size, number of currently kept items.
Represent the cutoff to the user as a number of items to be archived.
Buttons to increase/decrease number of archived items by 10.

Queue will be shown in a list of items below cutoff in reversed order (bottom items first).
Each item will have a keep button.
Show cutoff marker in the list to visually distinguish episodes that will be archived above marker vs kept below marker.

Implement the queue list as a separate component, register it as a viewer and then display it in UI using portal inspector with default viewer metadata, so that user can switch to view the queue in a different way.
Make the queue list itself scrollable, avoid scrolling statistics and controls away.

Show all episodes in the queue list.
Since we are displaying in reverse order, episodes to be kept will be below episodes to be archived.
The cutoff marker needs to be moved between them in the middle and it will show:
Episodes above will be archived
Episodes below will be kept

show neutral background for episodes to be archived and soft green background for episodes to be kept

Then button to Save changes, which will update the queue in database.
After Saving changes reload the queue.

Add Archived screen that will list archived episodes.

Add navigation bar to the top to switch between screens.

Add screen to list bumped episodes.

## Implementation details

Implement in Clojure.

File structue:
src/podcaster/main.clj - will contain -main function, tool has a small scope, should be able to fit logic in a single file
src/podcaster/viewer.cljs - for client side components

State of the app will be kept in memory.
Use atom to hold state as described in portlet context.
Database will be upated explicitly after user action like pressing a button.

kept-episodes as vector so that order is preserved when bumping to the top

Use next.jdbc and sqlite-jdbc for DB access.

Store archive in `archive.db` inside `(fs/xdg-data-home "podcaster-portlet")` directory.

Before writing to a database for the first time in a session create a copy once from dbname.db to dbname-updated.db.

There is a backup dababase available at tmp/AntennaPodBackup-2025-08-25.db to explore schema.
There is a checkout tmp/AntennaPod/ available to analyze source code for details.

Run tests with `clojure -M:test`
Generate coverage report with `clojure -X:coverage`

## Testing

- add tests using lazytest which supports babashka runner
	- https://github.com/noahtheduke/lazytest
	- test the events and logic
	- have integration test, which will use real sqlite database with seeded sample data, apply operations and verify values returned from reading functions
  - ensure good test coverage

## Design

### Database Analysis and Schema Design

**1.1 AntennaPod Database Schema Analysis** ✓
Analysis of `tmp/AntennaPodBackup-2025-08-25.db` reveals:
- **Queue**: Simple table with `id` (position), `feeditem`, and `feed` columns. 
  - Queue ordering: `id` field determines position (0 = top, higher = bottom)
- **FeedItems**: Episode metadata (title, pubDate, description, image_url, etc.)
  - `read` field: Episode completion status (-1=NEW, 0=UNPLAYED, 1=PLAYED/FINISHED)
- **Feeds**: Podcast feed information (title, custom_title, author, description, etc.) 
- **FeedMedia**: Media file details (duration, file_url, position, played_duration)
  - `position`: Current playback position in milliseconds
  - `played_duration`: Cumulative time actually played
  - Episodes marked PLAYED when position ≥ (duration - 30 seconds)

**Queue Reordering Implementation** (from AntennaPod source analysis):
- **Complete table replacement strategy**: No incremental position updates
- Reordering process: `DELETE FROM Queue` → re-insert all items with new sequential IDs
- All operations wrapped in database transactions for consistency
- Methods like `moveQueueItem()` modify in-memory list, then replace entire Queue table
- This prevents position gaps/duplicates and ensures atomic operations

**1.2 Archive Database Design (`archive.db`)**
```sql
-- Archive of forgotten episodes
CREATE TABLE archived_episodes (
                                id INTEGER PRIMARY KEY,
                                feeditem_id INTEGER NOT NULL,
                                archived_at INTEGER NOT NULL, -- unix timestamp
                                reason TEXT -- 'cutoff', 'manual', etc.)
;

-- Track bump actions for future analysis
CREATE TABLE bumped_episodes (
                            id INTEGER PRIMARY KEY,
                            feeditem_id INTEGER NOT NULL,
                            bumped_at INTEGER NOT NULL,
                            queue_position_before INTEGER,
                            queue_position_after INTEGER)
;
```
