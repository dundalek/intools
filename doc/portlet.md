
Portlet is an approach for creating custom apps or utilities on top of Portal.

In comparison to traditional rigid apps, the goal is to have highliy customizable UIs, more like a collection of applets - Portal applets - Portlets.
It should be a moldable application substrate.
At the core is the REBL (Read-Eval-Browse-Loop) principle which is an extension of REPL (Read-Eval-Print-Loop).
Code gets evaluated and resulting values are displayed and browsed using viewers.
A diferrence to REPL which prints resulting values in a set way, the user is in control how the values get displayed and can browse nested structures.

Portal provides collection of builtin viewer components, new ones can be created by composing existing or writing custom components.
Values are created by code being evaluated in REPL or user events like clicking a button can trigger evaluation and new values being created and displayed.

Portal is a client-server architecture.
The server (runtime) holds an atom, its value is synchronized to client.
The client in a web app running in browser. Client can communicate by sending RPC messages to the server.

## Client

Write components in ClojureScript cljs files.
Put them in usual `src` or classpath location to make them loadable with io/resource.
Components are Reagent functional components using hiccup syntax for HTML.

Reuse existing Portal components when they exist like buttons.

Commonly used requires are aliased as:

    [portal.ui.api :as pui]
    [portal.ui.rpc :as rpc]

Use `pui/register-viewer!` to register a component in Portal.

    (pui/register-viewer!
     {:name ::profile-tree
      :predicate profile-tree?
      :component profile-tree-component
      :doc "Some viewer description"}))


Use `rpc/call` to send message from client to runtime.
Use `:as-alias` to require namespaces like `(require '[server.some-ns :as-alias some-ns])` to be able to use fully qualified symbols without loading server code on client.

    (rpc/call `some-ns/method {:arg 123})

For values that user might want to different viewers use the `inspector` component and pass default viewer.
That way user has curated default experience, but flexibility to switch to different viewers.
As a rule of thumb logical entities are good candidates to wrap in an inspector.

    [ins/inspector
     {::pv/default ::some-ns/some-viewer}
     value]

**Component Props Pattern:**
Use maps for component props instead of positional arguments to improve readability and maintainability:

    ;; Good - using props map
    [tree-node-view {:node node
                     :selected-node selected-node
                     :on-select on-select
                     :expanded-nodes expanded-nodes
                     :on-toggle on-toggle
                     :level level}]
    
    ;; Avoid - positional arguments
    [tree-node-view node selected-node on-select expanded-nodes on-toggle level]

Stop propagation in event handlers to prevent Portal actions like selecting or toggling items when we want to just dispatch our custom actions.

    (.stopPropagation ev)

Add style `{:style {:height "80vh"}}` to the root viewer component to make it 80% of viewport height, otherwise portal will shrink the viewer and waste available space.

## Server

Commonly used requires are aliased as:

    [portal.api :as p]
    [portal.viewer :as pv]
    [portal.runtime :as prt]

Load custom viewers:

    (p/eval-str (slurp (io/resource "path/to/viewer.cljs")))

In babashka script without classpath use path relative to current script *file*:

    (p/eval-str (slurp (str (fs/path (fs/parent *file*) "src/path/to/viewer.cljs"))))

To load vendored javascript scripts:

    (p/eval-str (pr-str (list 'js/eval (slurp (io/resource "path/to/vendor/script.js"))))))

Use `prt/register!` to register handlers for RPC messages pass a function var.

    (pruntime/register! #'dispatch)

For babashka scripts add inline Portal dep like:

    (require '[babashka.deps :as deps])
    (deps/add-deps '{:deps {djblue/portal {:mvn/version "0.59.1"}}})

Use `:on-load` hook when opening Portal to ensure custom viewers are loaded after browser is ready:

    (p/open {:value !app-state
             :on-load #(p/eval-str (slurp "path/to/viewer.cljs"))})

When running under Babashka add `@(promise)` so that server does not immediately exit.

### State manangement

Use atom to hold state. Wrap the root value with `pv/default` to set metadata to use a given viewer by default:

    (require '[client.some-viewer :as-alias some-viewer])
    (defonce !app-state (pv/default default-state ::some-viewer/viewer)

Use `(p/open {:value !app-state})` to pass custom atom with state instead of the default taplist.
