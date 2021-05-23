
# Spotin

Spotin is a Spotify client for the terminal (TUI) which focuses on user convenience and being intuitive to use.  
It is a part of the [InTools](https://github.com/dundalek/intools) project aiming to create a collection of interactive TUI tools.

[add screenshot here]

## Features

- List playlists and play songs from them
- View and play artists/albums
- Control playback, volume and seek
- Toggle shuffle and repeat
- List and select devices

Extra features not in official clients:
- Open random playlist
- Search inside albums/playlists
- Make a new playlist by mixing tracks from multiple playlists

## Install

Install [Node.js](https://nodejs.org/) and install spotin with:
```sh
npm i -g spotin
```

Start it with (official client or [spotifyd](https://github.com/Spotifyd/spotifyd) needs to be running):
```sh
spotin
```

**Set up Spotify API access**

For now the process is a bit manual, more automated way is yet to be implemented in future releases.

1. Make sure to have Spotify premium account, playback control does not work with free accounts.
2. Open [Spotify dashboard](https://developer.spotify.com/dashboard/applications) to create a new app
    1. Click `Create an App` and fill out the info
    2. You will get `Client ID` and `Client Secret` which will be needed later.
    3. Click `Edit Settings`
    4. Add `http://localhost:8888/callback` to Redirect URIs
    5. Scroll to the bottom and click `Save`
3. Copy the `Client ID`, replace `YOUR_CLIENT_ID_HERE` in the following link and open it:

https://accounts.spotify.com/authorize?response_type=code&scope=playlist-read-private%20playlist-read-collaborative%20playlist-modify-public%20playlist-modify-private%20user-read-playback-state%20user-modify-playback-state%20user-read-currently-playing%20user-library-read&redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Fcallback&client_id=YOUR_CLIENT_ID_HERE

4. Once you grant permissions the browser will redirect to a following page:  
   http://localhost:8888/callback?code=A_REALLY_LONG_CODE_HERE
5. Copy the `A_REALLY_LONG_CODE_HERE` part which is the `SPOTIFY_REFRESH_TOKEN` to be used bellow.
6. Set up the credentials as environment variables:

```
export SPOTIFY_CLIENT_ID="your-client-id"
export SPOTIFY_CLIENT_SECRET="your-client-secret"
export SPOTIFY_REFRESH_TOKEN="your-refresh-token"
```
To store credentials permanently they can be added to shell configuration like `.bashrc`. Another alternative could be using a tool like [direnv](https://github.com/direnv/direnv).

However, I recommend a more secure way like using OS keychain or CLI integration with a password manager.  
Example of using GNOME Keyring with a wrapper script:
```sh
#!/usr/bin/env bash
source <(secret-tool lookup spotify envrc)
/path/to/spotin
```


## Usage

Spotin aims to be intuitive to use without requiring to first read long manuals. You should be able to use all of the functionality only by remembering few basic shortcuts.

### Shortcuts
- `tab` switch focus to another panel
- `x` open context menu
- `up` / `down` arrow keys to navigate selection
- `enter` to select or confirm
- `esc` to cancel or go back
- `/` search in panel or menu

Reference of common global shortcuts is also always displayed at the bottom:

[add screenshot here]

### Action menu

The menu is designed to be easy and accommodating for beginners but without sacrificing speed and usability for advanced users. The point is that level of efficiency will rise gradually over time with low initial time investment and without extra effort to memorize. There are multiple ways to run actions based on the familiarity level:

- **Navigate in menu**  
  Great to discover available actions for the first time. The benefit over manuals is that you can select an action with arrow keys and run it right from the menu by pressing `enter`.
- **Search in menu**  
  If the number of actions is overwhelming, `/` can be used to search and filter down available actions. Once you find desired action press `enter` to run it.  
  Search can be also used to quickly run actions that do not have global shortcuts assigned. For example to toggle shuffle mode you can type `x/shu⏎`, which can be quicker than clicking buttons in GUI programs.
- **Use a shortcut in menu**  
  Shortcuts are shown in the menu next to actions, so after getting memory refreshed by seeing the menu you can just hit the shortcut key right from the menu.
- **Use a global shortcut**  
  The most efficient way is to press the shortcut key directly. List of shortcuts at the bottom help with that and over time other commonly used shortcuts will stick in memory as well.

### Mixing playlists

This is a feature missing in the official client that keeps being requested [over](https://community.spotify.com/t5/Closed-Ideas/Play-multiple-playlists/idi-p/229) and [over](https://community.spotify.com/t5/Live-Ideas/Playlists-Play-multiple-selected-Playlists/idi-p/1240798) without being considered.  
Now with Spotin it is finally possible, this is how you can create a mixed playlist:

- Press `space` in the playlist panel to toggle playlist selection.
- Then select `mix` action from the menu and a new playlist containing 50 songs randomly picked from selected playlists will be created.

### Tips

- Pressing `enter` on a selected playlist opens it, you can still browser for other playlists in case it was not the one you were looking for. Pressing `enter` again on already opened playlist will play it.
- When you jump to currently playing track with `.`, the track gets selected and menu actions will be applied to it. This is going to be useful for quickly adding it to liked songs or playlists in the future.

## Limitations and workarounds

- Needs Spotify premium account to work.
- It does not play songs itself, official client or [spotifyd](https://github.com/Spotifyd/spotifyd) needs to run in background.
- There is no APIs to fetch generated playlist like Discover Weekly, Release Radar, Daily mixes and radios.  
  As a workaround it is possible to follow them in official client which will make them appear and be playable as regular playlists.


Features still under development:
- Global search for artists/albums/songs/playlists
- Browsing Library - browsing saved artists/albums/songs
- Managing Library - adding/removing liked songs, adding/removing songs in playlists
- Adding items to queue
- Better looking progress indicator
- Pagination support to show all albums/tracks when there is a large number of them
- Improved playback synchronization, seeking is a bit funky
- More efficient caching
- It is still early stages, there are bugs to be fixed
- Podcasts - I don't use it myself but can consider supporting it if there is a demand

## Other projects

- [lazygit](https://github.com/jesseduffield/lazygit) - not a Spotify client, but I find lazygit to have a top UX among TUI programs. I drew a big inspiration from it and recommend checking it out.
- [spotify-tui](https://github.com/Rigellute/spotify-tui) - Spotify client built in Rust
- [ncspot](https://github.com/hrkfdn/ncspot) - another client in Rust which has the advantage of playing the songs itself without official client or spotifyd.


## Development

All commands need to be run in the root of the repo.

Development:
```sh
# Install dependencies
yarn

# Start a watch mode with hot-reload
scripts/dev spotin

# Run the dev build
node modules/spotin/build/spotin.js
```

Release build:
```sh
scripts/build spotin

node modules/spotin/build/spotin.js
```

## License

MIT
