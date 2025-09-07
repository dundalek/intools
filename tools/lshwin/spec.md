Create a portlet to display information about hardware in tools/lshwin

Run `lshw -json` to understand the structure of data.

The tool will run `lshw -json` and present the data.

Keep the hiearchy do not flatten items.

The custom viewer for an item
  - will show `class` and `description` as title (fallback to `product` if description is missing).
  - inspector for the property map which user can expand for details.
  - when the item has `children` show them in inspector, map children to set meta to use :lshwin.viewer/hardware-item as default viewer

Add an option to expand-bridge-children, which will transform the data to expand :children of :class "bridge" items in their place (expansion can happen recursively).
It will de displayed in UI as toggle switch, by default it will be on.

Add option to show flat list, by default unchecked false.
using flatten-children function - conditionally called after expand-bridge-children
