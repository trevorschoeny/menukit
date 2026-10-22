# MenuKit

MenuKit is a UI library for Fabric mods. It has the parts of a Minecraft interface (buttons, sliders, tabs, panels, HUD readouts, and settings controls) and places them on the HUD, on vanilla screens, or on screens a mod opens itself. A mod builds its menus and overlays with it instead of writing vanilla screen code from scratch.

I built it for my own mods, then opened it up so other modders can use it too. If you're a player and another mod asked you to install MenuKit, put it in your mods folder alongside that mod and you're done. On its own it changes nothing in the game except a pressed look on buttons while you hold them down, and a Move panels key in Controls that does nothing until you bind it.

The UI draws on the client. MenuKit also loads on a server, because libraries built on it run server code, but a mod that only draws UI can stay client-only. For slots, there is [SlotKit](https://modrinth.com/mod/slotkit), a separate library built on MenuKit that finds and acts on the slots in any menu and builds real slots and custom menus.

## Features

- One set of components for five contexts, which are the HUD, container screens, other vanilla screens, anchors, and standalone screens. An anchor is a rectangle a mod names and supplies on any screen, such as the pause menu's column of buttons.
- Buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, info boxes, tooltips, icons, item displays (with an optional colored outline), progress bars, dividers, scroll containers, runtime rows (`Flow`, plain or drawn as cards), collapsible sections, tabs, color fields, item pickers, and confirm and alert dialogs.
- Every builder uses the same method names (`at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state`, `onClick`). Every control that shows a value reads and writes a field the mod owns.
- Components are grouped into panels. A panel renders, takes input, and shows or hides as a unit, and one `disabledWhen` grays the whole panel.
- A panel's placement is one declaration on the panel, and it can differ by game mode, so a panel can sit somewhere else in creative. The panel sizes itself to fit at any GUI scale, wrapping and scrolling as needed.
- Several mods can add UI to the same screen. Panels that share a region stack in priority order instead of overlapping, in the same order on every launch. Mods that work together can also share one panel.
- What a panel covers is inert. An opaque panel takes every point of its rectangle, and a see-through one takes only its controls. A slot or widget under a panel does not highlight, take a click, or show a tooltip.
- Controls have vanilla's click sound, narration, and keyboard focus.
- Each element draws for the kind of screen it is on. Inventories and chests get the Panel look, and Options, Controls, and similar screens get the Default look, which is vanilla's pause-menu look. Text is colored by role (heading, body, muted, or disabled), so it reads on either kind.
- On container screens, a chest and the boxes opened below it can stack in one scrolling list, above an inventory panel that stays put. Other mods' buttons and recipe viewers keep working around both.
- Panels can sit behind everything on a screen, so they never block other mods' buttons.
- Players can move panels. A Move panels key, unbound by default, lets them drag any panel a mod hasn't fixed in place, and MenuKit remembers where they put it.
- A color field lets a settings page pick a color, from Minecraft's 16 dye colors or from a full picker with a hex field.
- An item picker holds one item or a list of them as a setting value. Its dialog lists every item and variant the way the creative inventory does, and edits names, counts, enchantments, and other components. One shared matcher compares stacks, and nothing is ever taken from the player's inventory.
- A widget made by another library, such as a config library's own control, can sit inside a panel. It scrolls and clips with the panel, and gets clicks, typed keys, focus, and narration.
- Slots clip to the scroll area they sit in, so a slot half past the edge draws, hovers, and takes clicks only where it shows. Slot libraries report their slots to MenuKit, and SlotKit does.
- Lists that follow a list the mod owns as it changes, with optional add, remove, reorder, search, and sort controls, and a button that opens a menu of actions for a row.
- Text fields cancel an edit with Escape, without closing the screen.
- A cycle strip beside the hotbar is available to any mod that turns a ring of stacks through the held slot. It slides on each turn.
- A status column on the HUD, between the hearts and the hunger bar, holds small icons from several mods, stacked.
- Tooltips can be hidden, docked, wrapped, kept on screen, or scrolled through one shared check.

## Works with most resource packs

Menus built with MenuKit follow the resource pack you play with, the way Minecraft's own screens do. A panel's frame, background, and slots come from the pack's container art, buttons, toggles, checkboxes, and dropdowns from the pack's own buttons, and text changes color only where the pack's colors would make it hard to read. Mods built on MenuKit get this with no work of their own, and with no pack installed nothing looks different.

- Dark mode packs, high-resolution packs, and packs that recolor or restyle containers all work. Art too detailed to cut up cleanly, such as a fully painted background, keeps MenuKit's own look.
- In a container list, each container keeps its own art, so a shulker box below a chest looks the way it does on its own screen. Where a pack hides container titles, the boxes get their names back once there's more than one.
- With OptiGUI installed, packs that give each shulker box color its own screen apply per box, so a lime box below a chest gets the lime art.
- Pack authors can theme MenuKit directly with its own sprites: `menukit:panel_raised` for panels and the `menukit:control_raised` set for buttons. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md) lists them.

## Example

A HUD readout, registered once from the client entry point.

```java
HudPanel.builder("mymod:readout", ModeScope.BOTH)
        .region(InsideRegion.CENTER)
        .padding(4)
        .style(PanelStyle.NONE)
        .visibleWhen(() -> Minecraft.getInstance().gui.screen() == null)
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

The text renders directly below the crosshair during play and disappears while any screen is open.

## Getting started

Minecraft 26.3 is unobfuscated, so MenuKit goes on the plain `implementation` configuration.

```javascript
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.4.0+26.3'
}
```

Declare it in `fabric.mod.json` with both bounds, separated by a space ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)).

```json
"depends": { "menukit": ">=7.4.0 <8.0.0" }
```

Players install MenuKit separately. The Trevlar mods list it as a required dependency on their Modrinth pages rather than bundling it.

The sources in the [repository](https://github.com/trevorschoeny/menukit) build as a project of a parent Gradle workspace that is not in the repository, so they don't build on their own yet.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api), which MenuKit uses for its entry points and events.

## Compatibility

- Minecraft 26.3, with Fabric Loader 0.15.0 or newer.
- Required on the client. Optional on the server, unless a mod built on MenuKit needs it there.
- Older releases of Inventory Plus, Keybindery, and a few other Trevlar mods built for earlier MenuKit versions are marked incompatible. Update them alongside MenuKit.

## Used by

- [SlotKit](https://modrinth.com/mod/slotkit)
- [Keybindery](https://modrinth.com/mod/keybindery)
- [Inventory Plus](https://modrinth.com/mod/inventory-plus-trevlar)
- [Sandboxes](https://modrinth.com/mod/sandboxes)
- [Shulker Palette](https://modrinth.com/mod/shulker-palette)
- [Hotbar Pockets](https://modrinth.com/mod/hotbar-pockets)
- [Simple Elytra Slot](https://modrinth.com/mod/simple-elytra-slot)
- [Simple Totem Slot](https://modrinth.com/mod/simple-totem-slot)
- [Headcount](https://modrinth.com/mod/headcount)

## How to use

This guide follows the order a mod usually meets MenuKit, from a first panel to settings screens. Each part links to the full developer docs.

### The public API

The API is every type in a package named `api` (`com.trevlar.menukit.api.*`), and the version contract covers those. Every other package is internal, marked `@ApiStatus.Internal`, and can change in any release. A task that seems to need an internal type is a gap in the API, so [open an issue](https://github.com/trevorschoeny/menukit/issues) for it.

| Package | Holds |
| --- | --- |
| `api` | `MK`, the HUD facade, which registers HUD panels and notifications, sends a notification, and reads or sets the recipe book. `Declarations`, the one freeze that MenuKit and every library built on it declare through. |
| `api.element` | Every element, and the contexts they render and take input in |
| `api.layout`, `api.dialog` | Rows and columns placed once, and confirm and alert dialogs |
| `api.panel` | `Panel`, placement (`Placement`, `PanelPosition` and the regions) and `ModeScope`, the adapters that put panels on vanilla screens and around anchors, the container list (`ContainerRegion`), shared panels (`SharedPanels`), and `MKScreen` |
| `api.hud` | HUD panels, notifications, and the cycle strip |
| `api.host` | For a library that ships its own screens. It holds the host of a screen's own panels, the hook that routes input to slots drawn in panels, and `SlotClip`, which clips those slots to a scroll area |

### A first panel on the HUD

`HudPanel.builder(id, scope)` registers a panel on the game window, as in the example above. The scope is the game mode it shows in: `ModeScope.SURVIVAL`, `CREATIVE`, or `BOTH`. It sits on one of nine `InsideRegion` spots, 4 pixels in from the edges it touches. `.offset(dx, dy)` nudges it, and `.priority(n)` orders it among other panels on the same spot. With `.region(OutsideRegion.X)` a HUD panel sits on that side of the vanilla hotbar instead. HUD panels draw only and take no input. Register them from the client initializer, since registration closes when the client finishes starting. [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md) walks through it, and [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) has a HUD bar and an icon beside the hotbar.

### Panels and elements on a container screen

A `Panel` holds an ordered list of elements, and panels don't nest. Every element is built with `X.builder()`, the settings it needs, and `build()`, and doesn't change after that. A control that shows a value takes `state(get, set)` over the mod's own field and stores nothing itself. A `ScreenPanelAdapter` puts the panel on container screens. With no target it shows on every container screen, and `.on(InventoryScreen.class)` or `.onPlayerInventory()` narrows it. Panel ids are global, so prefix each one with the mod id. To show a panel only some of the time, give it a `visibleWhen` rather than removing its adapter. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) lists every element and the shared builder methods.

### Placement and regions

A panel declares where it sits with `Panel.builder(id).in(scope, placement)`, once for each game mode it shows in: `ModeScope.BOTH`, or `SURVIVAL` and `CREATIVE` apart, so a panel can sit somewhere else in creative. `Placement.screen(position)` places it against the screen, `Placement.around(anchor, position)` around an anchor's box, and `Placement.at(anchor, dx, dy)` at a fixed offset from the box. In a position, `PanelPosition.region(OutsideRegion)` places it outside the screen's frame along one edge, `screenAnchor(InsideRegion)` on one of nine screen spots, `center()` as an overlay on top, `pixel(...)` at an origin read every frame, and `main()` as a standalone screen's centered frame. `.priority(n)` orders panels that share a region (the default is 100), `.gap(n)` sets the space before a panel, and `.offset(dx, dy)` nudges one panel after it is placed. A panel wraps to the width its placement leaves and scrolls when it is taller than its room. The Mode scope, Placement, and Region sections of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) have the details.

### Other screens and anchors

`VanillaScreenPanelAdapter` puts a panel on a vanilla screen that isn't a container, such as the pause screen or Options, and needs `.on(...)` or `.onAny()`. An `Anchor` is a rectangle a mod names and supplies per screen with `Anchor.of(id, screen -> box)`, and a panel placed with `Placement.around(anchor, position)` sits around it by the same region rules as a menu frame, put on screen by an `AnchorPanelAdapter`. A `null` box means the anchor isn't on that screen. Adapters and anchors are declared at init and can't be added or removed during play. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) anchors a row under the pause menu's buttons.

### What a panel covers

A shown panel claims the points it covers, so nothing behind it highlights, takes a click, or shows a tooltip. Panels are opaque by default and claim their whole rectangle. `.opaque(false)` makes a panel see-through, and then only its buttons and other controls block what is behind them. `.popover()` draws a panel above every ordinary panel on the screen, and a modal dialog still opens above that. The Claims section of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) covers the order.

### Containers above the inventory

`ContainerRegion.section(id, scope)` adds a section to a container screen's container list, which stacks the chest's own grid and any boxes opened below it in one scrolling column above the player's inventory. The slots are the menu's own, and the screen's bounds are the rectangle around both panels, so other mods' buttons and recipe viewers go around them. A section's `source(...)`, the placed container or item it shows, gives it that container's art under a resource pack, and its name. `ContainerRegion.CONTAINERS`, `ContainerRegion.INVENTORY`, and `ContainerRegion.anchor(sectionId)` are anchors for a button by the list, by the inventory, or by one box. The Container region section of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region) covers it, and [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#stack-containers-in-one-scrolling-column-above-the-inventory) builds one.

### Panels behind everything

`Panel.Builder.background()` makes a panel part of the screen's own background. Everything else on the screen, other mods' buttons included, draws over it and stays clickable, and the panel gets a click or the wheel only after everything else declined it. The container list's two panels are built this way. See the [background panel recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-panel-behind-everything-on-a-container-screen).

### Shared panels and moving panels

Mods that work together can put their elements in one panel. Each joins it from its client initializer with `SharedPanels.join(id, contribution)` under an agreed id, and MenuKit builds one panel from every contribution, in order. With one mod installed it is an ordinary panel. Players can move panels once they bind MenuKit's Move panels key: they drag a panel where they want it, for both game modes or only the current one, and MenuKit keeps the position by panel id. A mod marks a panel `fixed()` where its place is part of what it means. The [Shared panels](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#shared-panels) and [Moving panels](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#moving-panels) sections of Concepts cover both.

### Standalone screens and screen kinds

A mod's own screen subclasses `MKScreen`. Elements draw for the kind of screen they are on. `ScreenKind.PANEL` is the Panel look, MenuKit's gray, and `ScreenKind.DEFAULT` is the Default look, vanilla's pause-menu look. Panels injected into vanilla screens take the kind from the screen. A standalone screen declares its kind once, with `super(title, ScreenKind.DEFAULT, panels)`, and one that declares nothing is a Panel screen. Text takes its color from a role (`TextRole.HEADING`, `BODY`, `MUTED`, or `DISABLED`), so it reads on both kinds. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) builds a whole settings screen in the pause-menu look.

### Layout, tabs, and sections

`Row` and `Column` place elements once, at the panel's first layout pass. `Flow` is the runtime row, and `Flow.spacer()` pins a control to the right edge of a settings row at any width. `Flow.builder().boxed()` draws a card behind a row, so a list of them lines up like vanilla's world list. `Tabs` has three modes, `WRAP`, `SIDE_SCROLL`, and `SIDEBAR`, and the selected tab is the mod's own state. `tabColumn(false)` on a `SIDEBAR` menu hides the tab column so a one-page menu looks like its tabbed siblings, and `fitShownTab()` sizes a `Tabs` to the tab it shows. `Section` is a collapsible group. Other mods can add tabs to a named menu with `Tabs.addTo(id, tab)`. The Tabs, Section, and Layout helpers sections of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) cover them.

### Color settings

`ColorField` shows a swatch and opens a dialog when clicked, with the 16 dye colors on a Basic tab and a full picker with a hex field on a Custom tab. The value is an int, `0xRRGGBB`, or `0xAARRGGBB` with `alpha(true)`. Changes are written at once, Done keeps them, and Cancel or Escape puts back the color from before. On the HUD there is no dialog, only the swatch. See the [color setting recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-color-setting).

### Item settings

`ItemPicker` takes one lens. `state` holds one item by kind, `stack` one stack, `stacks` a list of stacks, and `rule` and `rules` stacks that each carry their own match mode. `match(...)` sets how stacks compare, by the kind of item only (`ITEM`, the default), by the exact stack (`EXACT`), or by the item plus at least the components the rule sets (`CONTAINS`). The dialog has a Search tab and, for `EXACT` and `CONTAINS`, an Edit tab for components in `/give` syntax. In play, `rule.matches(stack)` or `ItemMatch.matches(...)` does the comparing, and `ItemMatch.CODEC` saves a rule. `ItemMatch` is common code, so it works on a server too. See the [item setting recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#an-item-setting).

### Widgets from other libraries

`WidgetHost.builder().widget(supplier).size(w, h)` puts any vanilla-style widget in a panel. The host moves it with the panel and its scroll, clips it to its box, and passes it clicks, drags, the wheel, typed keys, focus, and narration. A widget that isn't a vanilla `LayoutElement` needs a `place(...)` hook, and one that opens a list below itself needs `overflow()` to draw it over everything. See the [widget host recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#host-a-widget-another-library-made).

### Tooltips

`MKTooltip.hideWhen(...)` hides every tooltip, or only item tooltips or only the others, while a condition holds. `wrapWhen`, `clampWhen`, and `avoidCoverWhen` keep tooltips on screen and out of the way, and `onWheel` with `scrollBy` lets a mod scroll a long one. All of them are off unless a mod registers them from client init. The Claims section of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) explains each switch.

### The cycle strip

`HudCycleStrip` draws a small hotbar just right of the vanilla one, showing a ring of stacks that turns through the held slot. A mod writes a source that returns the ring for a hotbar slot, registers it once with `HudCycleStrip.register(...)`, and calls `slide` after the ring turns. [Cycle strip](https://github.com/trevorschoeny/menukit/blob/main/docs/cycle-strip.md) has the full guide.

### Slots

MenuKit keeps no slot code. A mod that needs slot groups, created slots, or custom menus depends on SlotKit as well. A slot library that places slots in panels reports each one with `SlotClip.shown`, so the slot clips to its scroll area. SlotKit's slot elements already do this.

### Limits and versioning

[Limits](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md) lists what MenuKit doesn't do and the open gaps. [Versioning](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md) explains what a version number promises and why both bounds matter. The [reference](https://trevorschoeny.github.io/menukit/) is the generated javadoc for the `api` packages. Every Java sample in the README and in `docs/` is compiled against the release before it ships.

## Upgrading

### To 7.4.0

Mods built for 7.3.0 work as before, with two exceptions. A `switch` over `OutsideRegion` needs cases for the new `LEFT_CENTER` and `RIGHT_CENTER`, and a custom element that draws past its own box isn't drawn while that box is wholly out of view. The forms that don't say which game mode a panel is for, such as `Panel.Builder.position(p)` (now `in(ModeScope.BOTH, Placement.screen(p))`), are deprecated and go in 8.0.0. A resource pack that themed MenuKit's buttons through `menukit:panel_raised` themes them through `menukit:control_raised` now. A mod that uses anything new in 7.4.0 declares `"menukit": ">=7.4.0 <8.0.0"`. [Upgrading](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#to-740) has every replacement and both checks.

Older releases, from 7.3.0 back to 3.0.0, are in [Upgrading from older versions](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading.md).

## License

MIT. See `LICENSE`. Report issues at [github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
