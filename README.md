# MenuKit

MenuKit is a UI library for Fabric mods. It has the parts of a Minecraft interface (buttons, sliders, tabs, panels, HUD readouts, and settings controls) and places them on the HUD, on vanilla screens, or on screens a mod opens itself. A mod builds its menus and overlays with it instead of writing vanilla screen code from scratch.

I built it for my own mods, then opened it up so other modders can use it too. If you're a player and another mod asked you to install MenuKit, put it in your mods folder alongside that mod and you're done. On its own it changes nothing in the game except a pressed look on buttons while you hold them down.

The UI draws on the client. MenuKit also loads on a server, because libraries built on it run server code, but a mod that only draws UI can stay client-only. For slots, there is [SlotKit](https://modrinth.com/mod/slotkit), a separate library built on MenuKit that finds and acts on the slots in any menu and builds real slots and custom menus.

## Features

- One set of components for five contexts, which are the HUD, container screens, other vanilla screens, anchors, and standalone screens. An anchor is a rectangle a mod names and supplies on any screen, such as the pause menu's column of buttons.
- Buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, info boxes, tooltips, icons, item displays (with an optional colored outline), progress bars, dividers, scroll containers, runtime rows (`Flow`, plain or drawn as cards), collapsible sections, tabs, color fields, item pickers, and confirm and alert dialogs.
- Every builder uses the same method names (`at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state`, `onClick`). Every control that shows a value reads and writes a field the mod owns.
- Components are grouped into panels. A panel renders, takes input, and shows or hides as a unit, and one `disabledWhen` grays the whole panel.
- A panel's placement is one declaration on the panel. The panel sizes itself to fit at any GUI scale, wrapping and scrolling as needed.
- Several mods can add UI to the same screen. Panels that share a region stack in priority order instead of overlapping, in the same order on every launch.
- What a panel covers is inert. An opaque panel takes every point of its rectangle, and a see-through one takes only its controls. A slot or widget under a panel does not highlight, take a click, or show a tooltip.
- Controls have vanilla's click sound, narration, and keyboard focus.
- Each element draws in the look of the screen it is on. Inventories and chests get the Panel look, MenuKit's gray, and Options, Controls, and similar screens get the Default look, vanilla's pause-menu look. Text is colored by role (heading, body, muted, or disabled), so it reads in either look.
- The Panel look follows the active resource pack. Panels take the pack's container art and controls its restyled buttons, with no code in the mod, and a pack can theme MenuKit's own sprites directly.
- On a container screen, several containers can scroll as one list above a fixed inventory, such as a chest with a shulker box or two opened below it. The slots stay vanilla's, and other mods' buttons and recipe viewers keep working around both panels.
- A color field lets a settings page pick a color, from Minecraft's 16 dye colors or from a full picker with a hex field.
- An item picker holds one item or a list of them as a setting value. Its dialog lists every item and variant the way the creative inventory does, and edits names, counts, enchantments, and other components. One shared matcher compares stacks, and nothing is ever taken from the player's inventory.
- A widget made by another library, such as a config library's own control, can sit inside a panel. It scrolls and clips with the panel, and gets clicks, typed keys, focus, and narration.
- Slots clip to the scroll area they sit in, so a slot half past the edge draws, hovers, and takes clicks only where it shows. Slot libraries report their slots to MenuKit, and SlotKit does.
- A cycle strip beside the hotbar is available to any mod that turns a ring of stacks through the held slot. It slides on each turn.
- Tooltips can be hidden, docked, wrapped, kept on screen, or scrolled through one shared check.

## Example

A HUD readout, registered once from the client entry point.

```java
HudPanel.builder("mymod:readout")
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

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.2.0+26.3'
}
```

Declare it in `fabric.mod.json` with both bounds, separated by a space ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)).

```json
"depends": { "menukit": ">=7.2.0 <8.0.0" }
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

## How to use

This guide follows the order a mod usually meets MenuKit, from a first panel to settings screens. Each part links to the full developer docs.

### The public API

The API is every type in a package named `api` (`com.trevlar.menukit.api.*`), and the version contract covers those. Every other package is internal, marked `@ApiStatus.Internal`, and can change in any release. A task that seems to need an internal type is a gap in the API, so [open an issue](https://github.com/trevorschoeny/menukit/issues) for it.

| Package | Holds |
|---|---|
| `api` | `MK`, the HUD facade, which registers HUD panels and notifications, sends a notification, and reads or sets the recipe book. `Declarations`, the one freeze that MenuKit and every library built on it declare through. |
| `api.element` | Every element, and the contexts they render and take input in |
| `api.layout`, `api.dialog` | Rows and columns placed once, and confirm and alert dialogs |
| `api.panel` | `Panel`, placement (`PanelPosition` and the regions), the adapters that put panels on vanilla screens and around anchors, and `MKScreen` |
| `api.hud` | HUD panels, notifications, and the cycle strip |
| `api.host` | For a library that ships its own screens. It holds the host of a screen's own panels, the hook that routes input to slots drawn in panels, and `SlotClip`, which clips those slots to a scroll area |

### A first panel on the HUD

`HudPanel.builder(id)` registers a panel on the game window, as in the example above. It sits on one of nine `InsideRegion` spots, 4 pixels in from the edges it touches. `.offset(dx, dy)` nudges it, and `.priority(n)` orders it among other panels on the same spot. With `.region(OutsideRegion.X)` a HUD panel sits on that side of the vanilla hotbar instead. HUD panels draw only and take no input. Register them from the client initializer, since registration closes when the client finishes starting. [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md) walks through it, and [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) has a HUD bar and an icon beside the hotbar.

### Panels and elements on a container screen

A `Panel` holds an ordered list of elements, and panels don't nest. Every element is built with `X.builder()`, the settings it needs, and `build()`, and doesn't change after that. A control that shows a value takes `state(get, set)` over the mod's own field and stores nothing itself. A `ScreenPanelAdapter` puts the panel on container screens. With no target it shows on every container screen, and `.on(InventoryScreen.class)` or `.onPlayerInventory()` narrows it. Panel ids are global, so prefix each one with the mod id. To show a panel only some of the time, give it a `visibleWhen` rather than removing its adapter. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) lists every element and the shared builder methods.

### Placement and regions

A panel declares where it sits once, with `Panel.builder(id).position(...)`. `PanelPosition.region(OutsideRegion)` places it outside the screen's frame along one edge, `screenAnchor(InsideRegion)` on one of nine screen spots, `center()` as an overlay on top, `pixel(...)` at an origin read every frame, and `main()` as a standalone screen's centered frame. `.priority(n)` orders panels that share a region (the default is 100), `.gap(n)` sets the space before a panel, and `.offset(dx, dy)` nudges one panel after it is placed. A panel wraps to the width its placement leaves and scrolls when it is taller than its room. The Placement and Region sections of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) have the details.

### Other screens and anchors

`VanillaScreenPanelAdapter` puts a panel on a vanilla screen that isn't a container, such as the pause screen or Options, and needs `.on(...)` or `.onAny()`. An `Anchor` is a rectangle a mod names and supplies per screen with `Anchor.of(id, screen -> box)`, and `AnchorPanelAdapter` places panels around it by the same region rules as a menu frame. A `null` box means the anchor isn't on that screen. Adapters and anchors are declared at init and can't be added or removed during play. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) anchors a row under the pause menu's buttons.

### What a panel covers

A shown panel claims the points it covers, so nothing behind it highlights, takes a click, or shows a tooltip. Panels are opaque by default and claim their whole rectangle. `.opaque(false)` makes a panel see-through, and then only its buttons and other controls block what is behind them. `.popover()` draws a panel above every ordinary panel on the screen, and a modal dialog still opens above that. The Claims section of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) covers the order.

### Containers above the inventory

`ContainerRegion.section(id)` adds a section to a container screen's container region: the chest's own grid, a box opened below it, another below that, all one list above the player's inventory. The list grows, stays centered with the inventory panel while both fit, and scrolls only once the screen is full. The wheel scrolls it from anywhere nothing else wants the wheel, a shift-click into a section scrolled out of view scrolls to it, and carrying an item to its edge scrolls it. The slots are the menu's own, and the screen's bounds are the rectangle around both panels, so other mods' buttons and recipe viewers go around them. A section's `source`, the placed container or item it shows, gives it that container's art under a resource pack and its name. `ContainerRegion.CONTAINERS` and `ContainerRegion.INVENTORY` are anchors for a button by either panel. The Container region section of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region) covers it, and [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#stack-containers-in-one-scrolling-column-above-the-inventory) builds one.

### Standalone screens and looks

A mod's own screen subclasses `MKScreen`. Elements draw in the look of the screen they are on. `ScreenKind.PANEL` is the Panel look, MenuKit's gray, and `ScreenKind.DEFAULT` is the Default look, vanilla's pause-menu look. Panels injected into vanilla screens take the look from the screen. A standalone screen declares its look once, with `super(title, ScreenKind.DEFAULT, panels)`, and one that declares nothing has the Panel look. Before 7.3.0 the two were `ScreenKind.CONTAINER` and `ScreenKind.OPTIONS`, which still work, deprecated, until 8.0.0. Text takes its color from a role (`TextRole.HEADING`, `BODY`, `MUTED`, or `DISABLED`), so it reads in both looks. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) builds a whole settings screen in the Default look.

### Resource packs

A Panel-look panel follows the active resource pack the way vanilla's screens do. Its frame, fill, and slots are cut from the pack's container art, its controls draw the pack's restyled vanilla buttons and checkboxes, and its text changes color only where the pack's fill would make it hard to read. A mod has nothing to do. Pack authors can theme MenuKit directly by overriding `menukit:panel_raised` and the `menukit:control_raised` sprites. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md) has the full list.

### Layout, tabs, and sections

`Row` and `Column` place elements once, at the panel's first layout pass. `Flow` is the runtime row, and `Flow.spacer()` pins a control to the right edge of a settings row at any width. `Flow.builder().boxed()` draws a card behind a row, so a list of them lines up like vanilla's world list. `Tabs` has three modes, `WRAP`, `SIDE_SCROLL`, and `SIDEBAR`, and the selected tab is the mod's own state. `tabColumn(false)` on a `SIDEBAR` menu hides the tab column so a one-page menu looks like its tabbed siblings, and `fitShownTab()` sizes a `Tabs` to the tab it shows. `Section` is a collapsible group. Other mods can add tabs to a named menu with `Tabs.addTo(id, tab)`. The Tabs, Section, and Layout helpers sections of [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) cover them.

### Color settings

`ColorField` shows a swatch and opens a dialog when clicked. The Basic tab has the 16 dye colors by their dye items, and the Custom tab has a saturation and brightness square, a hue bar, a transparency bar with `alpha(true)`, and a hex field. The value is an int, `0xRRGGBB`, or `0xAARRGGBB` with `alpha(true)`. `label(text)` titles the dialog and defaults to "Color", and the swatch defaults to 20 by 20. Changes are written at once, Done keeps them, and Cancel or Escape puts back the color from before. On the HUD there is no dialog, only the swatch. See the [color setting recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-colour-setting).

### Item settings

`ItemPicker` takes one lens. `state` holds one item by kind, `stack` one stack, `stacks` a list of stacks, and `rule` and `rules` stacks that each carry their own match mode. `match(...)` sets how stacks compare. `ITEM` (the default) compares the kind of item only, `EXACT` the exact stack, and `CONTAINS` the item plus at least the components the rule sets. `playerChoosesMatch()` lets the player pick "Any" or "Exactly this" for each slot and needs `rule` or `rules`. A list shows as a wrapping row of slots, 9 to a row by default (`columns(n)` changes it). The dialog has a Search tab and, for `EXACT` and `CONTAINS`, an Edit tab for the name, count, enchantments, and other components in `/give` syntax. In play, `rule.matches(stack)` or `ItemMatch.matches(...)` does the comparing, and `ItemMatch.CODEC` saves a rule. `ItemMatch` is common code, so it works on a server too. See the [item setting recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#an-item-setting).

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

Nothing breaks, and mods built for 7.3.0 work as before. Panel-look panels and controls now follow the active resource pack, so under a pack that restyles containers they may no longer be MenuKit's gray. Art a mod draws itself beside them stays as it is. A mod that uses anything new in 7.4.0 declares `"menukit": ">=7.4.0 <8.0.0"`.

### To 7.3.0

The two looks have new names. `ScreenKind.OPTIONS` is now `ScreenKind.DEFAULT` (the Default look), and `ScreenKind.CONTAINER` is now `ScreenKind.PANEL` (the Panel look). `ScrollContainer.OPTIONS_INSET_X` and `OPTIONS_INSET_Y` are now `DEFAULT_INSET_X` and `DEFAULT_INSET_Y`. The old names still work, deprecated, so mods built for 7.2.0 run as before; they go in 8.0.0. A `switch` over `ScreenKind` names the new constants. A mod that uses anything new in 7.3.0 declares `"menukit": ">=7.3.0 <8.0.0"`.

### To 7.2.0

One change can affect existing code. `ItemPicker`'s protected constructor changed, so a mod that subclasses `ItemPicker` updates its constructor call. Other mods built for 7.1.0 work as before, and an item picker a mod already has opens the new item dialog with no change. A mod that uses anything new in 7.2.0 declares `"menukit": ">=7.2.0 <8.0.0"`.

### To 7.1.0

Nothing breaks, and mods built for 7.0.0 work as before. A mod that uses anything new in 7.1.0 declares `"menukit": ">=7.1.0 <8.0.0"`.

### To 7.0.0

MenuKit keeps no slot code now. Everything about slots moved to SlotKit, a separate library built on MenuKit, and the deprecated parts of 6.x are gone. [Upgrading to 7.0.0](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md) walks through the move, and the [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list. Declare `"menukit": ">=7.0.0 <8.0.0"`.

- Slot groups, categories, slot operations, vetoes, addresses, the window, and `SlotGroupPanelAdapter` are SlotKit's. A mod that uses any of them depends on SlotKit and changes those imports. Nothing else changes for it.
- `Declarations` moved to `com.trevlar.menukit.api.Declarations`.
- `ControlStyle`, `style(ControlStyle)` on `Button`, `Dropdown`, and `DropdownMulti`, `Tabs.SIDEBAR_SCROLLBAR_WIDTH`, and `MKTooltip.MIN_WRAP_WIDTH` are removed. Delete a `style(...)` call, since it has done nothing since 6.1.0. Use `ScrollContainer.TRACK_WIDTH` (6 in the Default look) for the scrollbar width.
- The window's panel and element handles (`Window.panel`, `Window.element`) and the `VISIBILITY` and `OPACITY` keys are gone. Use the panel's own `visibleWhen` and `opaque`.
- `Anchor` and `AnchorPanelAdapter` are new. They place a panel around a rectangle a mod names and supplies on any screen, and SlotKit's slot-group panels are built on them.

### To 6.2.0

`MKTooltip.avoidCoverWhen` docks tooltips at the bottom or top center of the screen instead of choosing a side of the cursor. The method keeps its name, so a mod that registers it needs no change. `MKTooltip.MIN_WRAP_WIDTH` is deprecated and goes in 7.0.0.

### To 6.1.0

Elements draw for the kind of screen they are on (`ScreenKind`), and nothing else picks a look. A mod that uses screen kinds, text roles, or the item picker declares `"menukit": ">=6.1.0 <7.0.0"`.

- `ControlStyle` and `style(ControlStyle)` on `Button`, `Dropdown`, and `DropdownMulti` are deprecated and do nothing, and 7.0.0 removes them. A mod built against 6.0.0 still loads. Delete the call. An element already draws in vanilla's look on Options, Controls, and every other vanilla screen that is not a container, and in MenuKit's look on a container screen.
- Panels injected into a vanilla screen that is not a container (the pause screen, Options, Controls, or a config screen) draw in vanilla's look there. Before 6.1.0 they drew MenuKit's gray. Panels on inventories, chests, and the HUD are unchanged.
- To move a standalone screen to the Default look, vanilla's pause-menu look, declare it when the screen is made, with `super(title, ScreenKind.DEFAULT, panels)`. Give its panels `PanelStyle.NONE`, and replace hand-set label colors (`TextLabel.COLOR_DARK`, `Section.colors`) with roles, which read in both looks. A screen that declares nothing stays as it was. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) builds a whole settings screen this way.

### To 6.0.0

6.0.0 is a consolidation release. It adds little and renames and removes a lot at once, so every mod migrates one time. The [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list. These are the changes most mods meet.

- Public types moved into `api` packages, and Containers has its own root, `com.trevlar.menukit.containers`. Every import changes. The classes in the table below were renamed as well.
- Placement is declared on the panel, as `Panel.builder(id).position(PanelPosition.region(OutsideRegion.X).priority(n).offset(dx, dy))`. The adapters take only `(panel)` or `(panel, padding)`. A HUD panel uses `.region(InsideRegion.X).offset(dx, dy)` where it used `.anchor(MKHudAnchor.X, dx, dy)`.
- Elements are built by builders, for example `Button.builder().label(text).size(w, h).onClick(run).build()`. A control that shows a value takes `state(get, set)` over a field the mod owns.
- Declarations happen at init. Adapters, HUD panels, operations, slot groups, channels, and menus declared after startup throw.
- Custom menus split by side. A menu's slots are common code. How its panels look, and its screen class, are declared on the client with `ClientMenu.of(menu)`.
- A shared slot-state channel names its writers. Without a `canWrite` rule the server refuses every client write to it.
- Item Tips left MenuKit. Durability and food lines on tooltips are a feature of Inventory Plus.

| 5.x | 6.0.0 |
|---|---|
| `MKHudPanel`, `MKHudNotification`, `MKHudIcon`, `MKHudSlot` | `HudPanel`, `HudNotification`, `HudIcon`, `HudSlot` |
| `MKFocus`, `MKText` | `Focus`, `Text` |
| `MKCContainerPanel` | `ContainerPanel` |
| `MKCSlot`, `MKCSlots` | `CreatedSlot`, `CreatedSlots` |
| `MKCMenu` | `CustomMenu` |
| `MKCScreenHandler` | `CustomContainerMenu` |
| `MKCHandledScreen` | `CustomContainerScreen` |
| `MKCBehaviorKeys` | `ContainerKeys` |
| `MKSlotState` | `SlotState` |
| `MKCSlots.renderGroup` | `ClientSlots.renderGroup` |
| `MKCContainerPanel.Builder.chrome(...)`, `.parity(...)` | `ClientContainerPanel.of(id).chrome(...).parity(...)`, on the client |
| `CreatedSlotAdapter.addressOf(panelId, groupId, index)` | `Address.createdSlot(CreatedSlots.groupId(panelId, groupId), index)` |
| `SlotSpec.quickMove(...)`, `MKCBehaviorKeys.QUICK_MOVE` | `SlotSpec.shiftClickOut(false)`, `shiftClickIn(false)`, and `BehaviorKeys.SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN` |
| `WORLD_PICKUP` | `INVENTORY_INSERT` |
| `showWhen(...)` on a panel, a HUD panel, or a container panel | `visibleWhen(...)` |
| `getId()` | `id()` |

### To 5.1.0

Nothing breaks. `SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated for removal in 6.0.0. Declare `BehaviorKeys.SHIFT_CLICK_OUT` and `SHIFT_CLICK_IN` instead.

### To 5.0.0

Four region enums are two, and two bounds records are one. Replace the type names. The constants inside are unchanged.

| Was | Is |
|---|---|
| `MenuRegion`, `SlotGroupRegion` | `OutsideRegion` |
| `HudRegion`, `ScreenRegion` | `InsideRegion` |
| `ScreenBounds`, `SlotGroupBounds` | `Reference` |
| `HudRegion.CENTER_CROSSHAIR_CLEARANCE` | `RegionConstants.CENTER_CROSSHAIR_CLEARANCE` |

With Containers, every created slot group declares a `SlotGroupCategory`, through `SlotSpec.at(id, category)`, `MKCSlots.onto(...).category(...)`, and `PanelBuilder.group(id, category, storage)`. A group without one fails at runtime on the first menu it opens.

### To 4.0.0

Vanilla draws every slot, and MenuKit runs no slot pass of its own. `CreatedSlotResolver.resolve` returned the live in-menu `Slot` instead of a position, and `SlotRendering` kept the frame helper and dropped the item-drawing helpers.

### To 3.0.0

The Java package moved from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. No class or method names changed.

## License

MIT. See `LICENSE`. Report issues at [github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
