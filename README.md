# MenuKit

MenuKit is a UI library for Fabric mods. It provides the parts of a Minecraft interface (buttons, sliders, tabs, panels, HUD readouts) and places them on screen. A mod builds its menus and overlays with it instead of writing vanilla screen code from scratch.

It runs on the client and the server. The UI draws on the client, and libraries built on MenuKit run server code too. A mod that only draws UI can stay client-only. For slots, add [SlotKit](https://modrinth.com/mod/slotkit), which is built on MenuKit. It finds and acts on the slots in any menu, and builds real slots and custom menus.

Components: buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, tooltips, icons, item displays (with an optional coloured outline), progress bars, dividers, scroll containers, runtime rows (`Flow`), collapsible sections and tabs. Every component has a builder with the same vocabulary (`at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state`, `onClick`). Every control that shows a value reads and writes a field the mod owns.

What it does:

- Places UI in five contexts with one set of components: the HUD, container screens, other vanilla screens, anchors, and standalone screens. An anchor is a rectangle a mod names and supplies on any screen, such as the pause menu's row of buttons.
- Groups components into panels. A panel renders, takes input, and shows or hides as a unit.
- Takes a panel's placement from one declaration on the panel. Sizes the panel to fit at any GUI scale, wrapping and scrolling as needed.
- Lets several mods add UI to the same screen. Panels sharing a region stack in priority order instead of overlapping, in the same order on every launch.
- Makes what a panel covers inert. An opaque panel takes every point of its rectangle, and a see-through one takes only its controls. A slot or widget under a panel does not highlight, click or show a tooltip.
- Gives its controls vanilla's click sound, narration and keyboard focus, and greys a whole panel at once with one `disabledWhen`.
- Draws each element for the kind of screen it is on. Inventories and chests get MenuKit's grey look, and Options, Controls and similar screens get vanilla's pause-menu look. A standalone screen declares its kind once, when it is made. Text is coloured by role (heading, body, muted, disabled) so it reads on either kind.
- Draws a cycle strip beside the hotbar for any mod that turns a ring of stacks through the held slot, with a slide on each turn.
- Lets a settings page pick items. An item picker is a slot whose item is a setting value. The player chooses it from a searchable list, and it is never taken from the inventory.

Requires Fabric and Fabric API.

## Install

Minecraft 26.3 is unobfuscated, so MenuKit goes on the plain `implementation` configuration:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:7.1.0+26.3'
}
```

Declare it in `fabric.mod.json`, with both bounds ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)):

```json
"depends": { "menukit": ">=7.1.0 <8.0.0" }
```

## Example

A HUD readout, registered once from the client entry point:

```java
HudPanel.builder("mymod:readout")
        .region(InsideRegion.CENTER)
        .padding(4)
        .style(PanelStyle.NONE)
        .visibleWhen(() -> Minecraft.getInstance().gui.screen() == null)
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

## The public API

The API is every type in a package named `api`: `com.trevlar.menukit.api.*`. The version contract covers those. Every other package is internal, marked `@ApiStatus.Internal`, and can change in any release. A task that seems to need an internal type is a gap in the API. [Open an issue](https://github.com/trevorschoeny/menukit/issues) for it.

| Package | Holds |
|---|---|
| `api` | `MK`, the HUD facade. It registers HUD panels and notifications, sends a notification, and reads or sets the recipe book. `Declarations`, the one freeze that MenuKit and every library built on it declare through. |
| `api.element` | Every element, and the contexts they render and take input in |
| `api.layout`, `api.dialog` | Rows and columns placed once; confirm and alert dialogs |
| `api.panel` | `Panel`, placement (`PanelPosition` and the regions), the adapters that put panels on vanilla screens and around anchors, `MKScreen` |
| `api.hud` | HUD panels, notifications and the cycle strip |
| `api.host` | For a library that ships its own screens: the host of a screen's own panels, and the hook that routes input to slots drawn in panels |

## Docs

- [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md): the dependency, one HUD panel, one inventory-screen panel.
- [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md): panels, elements, placement, the five contexts, claims.
- [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md): the common tasks.
- [Cycle strip](https://github.com/trevorschoeny/menukit/blob/main/docs/cycle-strip.md): the small hotbar beside the vanilla one that shows a ring of stacks turning through the held slot.
- [Limits](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md): what MenuKit does not do, and the open gaps.
- [Versioning](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md): what a version number promises.
- [Upgrading to 7.0.0](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md): moving a mod from MenuKit 6, and finding out whether it needs SlotKit.
- [Reference](https://trevorschoeny.github.io/menukit/): the generated javadoc for the `api` packages.

Every Java sample in this README and in `docs/` is compiled against the release before it ships.

## Repository layout

- `menukit/`: the `menukit` mod.
- `docs/`: the guides above.

These sources build as a project of a parent Gradle workspace that is not in this repository. They do not build on their own yet.

## Upgrading

### To 7.1.0

Nothing to change. 7.1.0 adds the cycle strip ([guide](https://github.com/trevorschoeny/menukit/blob/main/docs/cycle-strip.md)). A mod that uses it declares `"menukit": ">=7.1.0 <8.0.0"`.

### To 7.0.0

MenuKit keeps no slot code now. Everything about slots moved to SlotKit, a separate library built on MenuKit, and the deprecated parts of 6.x are gone. [Upgrading to 7.0.0](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md) walks through the move, and the [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list. Declare `"menukit": ">=7.0.0 <8.0.0"`.

- Slot groups, categories, slot operations, vetoes, addresses, the window and `SlotGroupPanelAdapter` are SlotKit's. A mod that uses any of them depends on SlotKit and changes those imports. Nothing else changes for it.
- `Declarations` moved to `com.trevlar.menukit.api.Declarations`.
- `ControlStyle`, `style(ControlStyle)` on `Button`, `Dropdown` and `DropdownMulti`, `Tabs.SIDEBAR_SCROLLBAR_WIDTH` and `MKTooltip.MIN_WRAP_WIDTH` are removed. Delete a `style(...)` call; it did nothing since 6.1.0. Use `ScrollContainer.TRACK_WIDTH` (6 on an options screen) for the scrollbar width.
- The window's panel and element handles (`Window.panel`, `Window.element`) and the `VISIBILITY` and `OPACITY` keys are gone. Use the panel's own `visibleWhen` and `opaque`.
- New: `Anchor` and `AnchorPanelAdapter` place a panel around a rectangle a mod names and supplies on any screen. SlotKit's slot-group panels are built on them.

### To 6.2.0

`MKTooltip.avoidCoverWhen` docks tooltips at the bottom or top centre of the screen instead of choosing a side of the cursor. The method keeps its name, so a mod that registers it needs no change. `MKTooltip.MIN_WRAP_WIDTH` is deprecated and goes in 7.0.0.

### To 6.1.0

Elements draw for the kind of screen they are on (`ScreenKind`), and nothing else picks a look. A mod that uses screen kinds, text roles or the item picker declares `"menukit": ">=6.1.0 <7.0.0"`.

- `ControlStyle` and `style(ControlStyle)` on `Button`, `Dropdown` and `DropdownMulti` are deprecated and do nothing, and 7.0.0 removes them. A mod built against 6.0.0 still loads. Delete the call. An element already draws in vanilla's look on Options, Controls and every other vanilla screen that is not a container, and in MenuKit's look on a container screen.
- Panels injected into a vanilla screen that is not a container (the pause screen, Options, Controls, a config screen) draw in vanilla's look there. Before 6.1.0 they drew MenuKit's grey. Panels on inventories, chests and the HUD are unchanged.
- To move a standalone screen to the pause-menu look, declare the kind when the screen is made, with `super(title, ScreenKind.OPTIONS, panels)`. Give its panels `PanelStyle.NONE`, and replace hand-set label colours (`TextLabel.COLOR_DARK`, `Section.colors`) with roles, which read on both kinds. A screen that declares nothing stays as it was. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) builds a whole settings screen this way.

### To 6.0.0

6.0.0 is a consolidation release. It adds little, and renames and removes a lot at once, so every mod migrates one time. The [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list. The changes most mods meet:

- Public types moved into `api` packages, and Containers has its own root, `com.trevlar.menukit.containers`. Every import changes. The classes in the table below were renamed as well.
- Placement is declared on the panel, as `Panel.builder(id).position(PanelPosition.region(OutsideRegion.X).priority(n).offset(dx, dy))`. The adapters take only `(panel)` or `(panel, padding)`. A HUD panel uses `.region(InsideRegion.X).offset(dx, dy)` where it used `.anchor(MKHudAnchor.X, dx, dy)`.
- Elements are built by builders, for example `Button.builder().label(text).size(w, h).onClick(run).build()`. A control that shows a value takes `state(get, set)` over a field the mod owns.
- Declarations happen at init. Adapters, HUD panels, operations, slot groups, channels and menus declared after startup throw.
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
| `SlotSpec.quickMove(...)`, `MKCBehaviorKeys.QUICK_MOVE` | `SlotSpec.shiftClickOut(false)`, `shiftClickIn(false)`; `BehaviorKeys.SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN` |
| `WORLD_PICKUP` | `INVENTORY_INSERT` |
| `showWhen(...)` on a panel, a HUD panel or a container panel | `visibleWhen(...)` |
| `getId()` | `id()` |

### To 5.1.0

Nothing breaks. `SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated for removal in 6.0.0. Declare `BehaviorKeys.SHIFT_CLICK_OUT` and `SHIFT_CLICK_IN` instead.

### To 5.0.0

Four region enums are two, and two bounds records are one. Replace the type names; the constants inside are unchanged.

| Was | Is |
|---|---|
| `MenuRegion`, `SlotGroupRegion` | `OutsideRegion` |
| `HudRegion`, `ScreenRegion` | `InsideRegion` |
| `ScreenBounds`, `SlotGroupBounds` | `Reference` |
| `HudRegion.CENTER_CROSSHAIR_CLEARANCE` | `RegionConstants.CENTER_CROSSHAIR_CLEARANCE` |

With Containers, every created slot group declares a `SlotGroupCategory`: `SlotSpec.at(id, category)`, `MKCSlots.onto(...).category(...)`, and `PanelBuilder.group(id, category, storage)`. A group without one fails at runtime on the first menu it opens.

### To 4.0.0

Vanilla draws every slot, and MenuKit runs no slot pass of its own. `CreatedSlotResolver.resolve` returned the live in-menu `Slot` instead of a position, and `SlotRendering` kept the frame helper and dropped the item-drawing helpers.

### To 3.0.0

The Java package moved from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. No class or method names changed.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
