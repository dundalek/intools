# InTools: Interactive Tools

The goal of the InTools project is to develop interactive tools for various tasks.

Before 2025 the approach of exploration was to build [TUIs](#tuis).
The reason being lower fidelity requiring less effort to build one-off tools.

The rise Claude Code and Claude 4 models getting good is changing the paradigm.
Now generating web-based tools with low effort became viable even for ad-hoc throwaway mini apps.

It will be interesting to see what kind of platforms for AI-generated tools will emerge.
Many vendors are rushing with AI/vibe coding platforms,
they often have same limitations like previous era no-code platforms.

I have a feeling that REPL with optional GUI might fit well as a good substrate keeping the complexity down.
I imagine several possible archetypes based on the task and purpose.

## Archetype Overview

- CLI script 
  - for quick automation
- TUI
  - interactive tools, developer-oriented, keeping flow
- Single-file HTML app
  - simple one-off graphical tools
- Web-based local server
  - Experimental:
    - REBL-based (Portlet)
    - Notebook-based (Clerklet)
- (Local-first) web app
  - CRUDs, persistent data, user collaboration

## Comparison


|                      | persistent data | file access | mobile access | multi-user collab | rich graphics | interactive |
| -------------------- | :-------------: | :---------: | :-----------: | :---------------: | ------------- | ----------- |
| CLI script           |       yes       |     yes     |   limited 1)   |        no         | no            | no          |
| TUI                  |       yes       |     yes     |   limited 1)   |        no         | no            | yes         |
| Single-file HTML app |      no 2)       |  limited 3)  |      yes      |        no         | yes           | yes         |
| Web-based local server (Portlet)            |       yes       |     yes     |   limited 4)   |        no         | yes           | yes         |
| Local-first web app  |       yes       |  limited 3)  |      yes      |        yes        | yes           | yes         |


Notes:
1) Can be accessed using mobile terminal emulators, but smartphone keyboard is usually more limited.
2) Local storage is not a reliable place to store data permanently, can get wiped out.
3) Limited file access traditionally uploading/downloading. Although some browsers support more powerful APIs.
4) Mobile access requires a server and needs to involve auth like reverse proxy or tunnels.


## Showcase

- Portlets
  - [File Browser](./tools/file-browser/) - simple demo to browse filesystem
  - [List Hardware](./tools/lshwin/) - view output of [lshw](https://github.com/lyonel/lshw) visually
  - [Podcaster](./tools/podcaster/) - manage queue of podcast episodes
  - [Daba](https://github.com/dundalek/daba) - database client, based on similar ideas as portlets
- TUIs
  - [Spotin](./modules/spotin/) - client for Spotify (cljs react-ink)
  - [Resticin](./modules/resticin/) - interactive viewer for [restic](https://restic.net) backup tool (cljs react-ink) [WIP]
  - [Randrin](./modules/randrin/)  - manage monitors/displays based on xrandr (clj membrane lanterna) [WIP]


| | | |
| - | - | - |
| ![](./doc/img/file-browser.avif) | ![](./doc/img/lshwin.avif) | ![](./doc/img/podcaster.avif)  |
| File Browser | List Hardware | Podcaster |
| ![](./doc/img/spotin.avif) | | |
| Spotin | | |

## Archetypes

### CLI script

Scripts for quick automation and one-off tasks.
They can work with files, pipe with other CLI tools, and can be scheduled via cron or triggered by events.

Using [Babashka](https://babashka.org) because Bash has footguns and gets messy,
and Python has no regards for backwards compatibility.

### TUIs

Interactive terminal-based tools.
TUIs have lower fidelity graphics than web apps, but sufficient for many workflows.
Using keyboard-driven navigation for speed is great to stay in flow, with no context switch to browser or GUI.

The motivation is summarized in [Combining CLI and GUI](https://dundalek.com/entropic/combining-cli-and-gui/):

> The command-line interface (CLI) is a powerful way to control computers. However, it is also quite difficult to learn and more complicated to use. Graphical user interfaces (GUI) offer alternative approach that is more intuitive and easier to use but they loose some power. My goal is to explore an approach that takes power from CLI and intuitiveness from GUIs.

Why are Terminal user interfaces (TUIs) an interesting choice for research and exploration?
- TUIs are on the edge between command line and graphical interfaces, therefore blending the best of both is a natural extension.
- Because visual representation of TUIs is constrained it lends itself to more focus on the underlying data model [fostering more creativity and innovation](https://hbr.org/2019/11/why-constraints-are-good-for-innovation). However, once good data models are in place, nothing prevents to build GUIs on top.
- Learnings about TUI possibilities will inform the design of the next-generation UI for the [closh shell](https://github.com/dundalek/closh).

The approach is to develop a variety of tools. Eventually common parts can be abstracted and evolved into a toolkit of of high-level primitives to allow building new tools even effortless. A good benchmark to strive for is to be able to create any interactive tool in not much more characters than it takes to write a manual page.

### Single-file HTML app

Simple one-off graphical tools that run in a browser without a backend.
A single file with no build step, works on any device with a browser and is easy to share by sending the file or hosting statically.

Rich graphics and modern UI via CSS.
Good for calculators, converters, visualizers, one-off data exploration, throwaway tools, and prototypes.
Data lives mainly in memory.
Can use localStorage, but for data that need to be stored permanently it is better to use the archetype with local-first sync engine.

File access is limited, usually just submit and download. Some browers support additional APIs like [File System Access API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API) or [input webkitdirectory](https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/webkitdirectory).

Using [Solid.js](https://www.solidjs.com/) with [Tagged Template Literals](https://github.com/solidjs/solid/blob/main/packages/solid/html/README.md) for small size and declarative reactive model without a build step.
One example of this archetype are [simonw's tools](https://tools.simonwillison.net/).

### (Local-first) web app

Full-featured web apps with persistent data and optional multi-user collaboration.

Local-first apps are particularly suitable for customized homecooked software,
as they usually don't require to manage servers, are good for syncing across multiples devices, work offline, with PWA can have app-like experience even on mobile devices.

Good for CRUD applications, collaborative tools, personal productivity apps, and tools requiring multi-device sync.

Currently experimenting with [InstantDB](https://www.instantdb.com/).
When using the managed backed the app can be totally client-side served from static file hosting without needing to maintain a server.

### Web-based local server 

Running server on localhost and using browser for GUI.
Good for tools that need deeper access to system or files, and need richer visualization than CLI/TUI.
Portal and notebooks are kinds of web-based local server apps with interesting approaches.

#### REBL-based (Portlet)

[Portal](https://github.com/djblue/portal) is a web-based tool for displaying and navigating through data.
The distinguishing feature is that Portal provides a way to switch used viewer components to visualize data in different ways.
There are many builtin viewers and custom ones can be created.

This is where LLMs come in to generate custom viewers to provide better visualization and interaction tailored to the use case.
We can generate custom apps (applets) based on Portal - Portlets.

There is a [Portlet description](./doc/portlet.md) crafted to be used in context/prompts to single-shot generate custom tools/apps.

#### Notebook-based (Clerklet)

[Clerk](https://github.com/nextjournal/clerk) notebooks are represented as source code, great target for agents to generate, can be less boilerplate than vibing a dashboard app. 

In comparison to traditional notebook implementations like IPython or Jupyter, Clerk tracks graph of dependencies and efficiently re-evaluates affected cells to avoid showing stale results.

Example: [Metrics reports](https://github.com/dundalek/stratify?tab=readme-ov-file#metrics-reports).

## Experiments

An issue is that to this day software applications are still not very well integrated together.
Extension to functionality in cases when APIs are available requires serious engineering effort.
As a result only most common use cases with the lowest common denominator are being invested in, while a long-tail of niche tasks remains underserved.

- One inspiring vision is outlined in [Magic Ink](http://worrydream.com/MagicInk/#engineering_inference_from_the_environment) by Bret Victor where modules can provide inference to accomplish novel tasks without programming them in.  
- Another source of ideas can be found in [Away with Applications](https://dundalek.com/entropic/enso-launcher/) by Aza Raskin describing a potential of a command-driven computing system.

## License

MIT
