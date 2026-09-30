# MenuKit

MenuKit is a UI library for Fabric mods. It gives you the parts of a Minecraft interface (buttons, sliders, tabs, panels, HUD readouts, slots) and places them on screen for you, so a mod builds its menus and overlays without writing vanilla screen code from scratch.

It ships as two artifacts from this repository:

| Artifact | Runs on | Adds |
|---|---|---|
| `menukit` | Client and server (the UI draws on the client) | Panels, elements, HUD panels, placement on vanilla screens, standalone screens, slot groups and slot operations |
| `menukit-containers` | Client and server, required on both | Created slots, custom container menus, per-slot state, storage attachments |

`menukit-containers` depends on `menukit`. A mod that adds no slots depends on `menukit` alone and can stay client-only. A server running Containers requires it on every player's game: it checks before the world loads and tells a player without it which two mods to install.

Components: buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, tooltips, icons, item displays (with an optional coloured outline), progress bars, dividers, scroll containers, runtime rows (`Flow`), collapsible sections and tabs. Containers adds slots. Every component is built by a builder with the same vocabulary (`at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state`, `onClick`, `style`), and every control that shows a value reads and writes your own field.

What it does:
- Places UI in five contexts with one set of components: the HUD, container screens, other vanilla screens, named slot groups, and standalone screens.
- Groups components into panels. A panel renders, takes input, and shows or hides as a unit.
- Places a panel from one declaration on the panel, and sizes it to fit, wrapping and scrolling as needed, at any GUI scale.
- Lets several mods add UI to the same screen. Panels sharing a region stack in priority order instead of overlapping, in the same order on every launch.
- Makes what a panel covers inert. An opaque panel takes every point of its rectangle, a see-through one only its controls, so a slot or widget under a panel does not highlight, click or show a tooltip.
- Publishes every slot group, vanilla or created, under a category any mod can read, with or without a menu open.
- Names each thing that can be done to a slot as an operation. Vanilla's actions are eleven, one per gesture, and a mod can add its own. A slot answers for itself first, then its group, then its category, and a locking mod can veto any operation.
- Creates real, server-synced slots as UI components and shows them on every container screen without per-screen setup (Containers).
- Attaches per-slot state to any slot, private per player or shared, stored on the slot's owner. The server judges every write a client sends (Containers).
- Gives its controls vanilla's click sound, narration and keyboard focus, and greys a whole panel at once with one `disabledWhen`.

Its types are vanilla types. A created slot is a real `Slot`, and a custom menu is a real `AbstractContainerMenu`. Requires Fabric and Fabric API.

## Install

Minecraft 26.2 is unobfuscated, so MenuKit goes on the plain `implementation` configuration:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:6.0.0+26.2'
    // Only if the mod creates slots or custom menus. Pulls in menukit.
    implementation 'maven.modrinth:menukit-containers:6.0.0+26.2'
}
```

Declare what you use in `fabric.mod.json`, with both bounds ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)):

```json
"depends": { "menukit": ">=6.0.0 <7.0.0" }
```

```json
"depends": { "menukit": ">=6.0.0 <7.0.0", "menukit-containers": ">=6.0.0 <7.0.0" }
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

Nine synced pocket slots on every container screen, registered once from the common entry point (Containers):

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);
```

```java
ContainerPanel.define("mymod:pockets")
        .at(OutsideRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

## The public API

The API is every type in a package named `api`: `com.trevlar.menukit.api.*` and `com.trevlar.menukit.containers.api.*`. The version contract covers those. Every other package is internal, marked `@ApiStatus.Internal`, and can change in any release. If a task seems to need an internal type, [open an issue](https://github.com/trevorschoeny/menukit/issues), since that is a gap in the API.

| Package | Holds |
|---|---|
| `api.element` | Every element, and the contexts they render and take input in |
| `api.layout`, `api.dialog` | Build-time rows and columns; confirm and alert dialogs |
| `api.panel` | `Panel`, placement (`PanelPosition` and the regions), the adapters that put panels on vanilla screens, `MKScreen` |
| `api.hud` | HUD panels and notifications |
| `api.slot` | Slot groups, categories, sets, and `Storage` |
| `api.window` | Addresses, the window, slot operations, gates and vetoes |
| `containers.api.slot` | Created slots, container panels, `SlotSpec` |
| `containers.api.storage` | Storage attachments |
| `containers.api.state` | Slot-state channels |
| `containers.api.menu` | Custom container menus and their screens |

## Docs

- [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md): the dependency, one HUD panel, one inventory-screen panel.
- [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md): panels, elements, placement, the five contexts, claims, slots, addresses.
- [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md): the common tasks.
- [Limits](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md): what MenuKit does not do, and the open gaps.
- [Versioning](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md): what a version number promises.
- [Reference](https://trevorschoeny.github.io/menukit/): the generated javadoc for the `api` packages.

The build compiles every Java sample in this README and in `docs/`. The `validator-mk` and `validator-mkc` mods in the same workspace are the reference consumers and use every primitive.

## Repository layout

- `menukit/`: the `menukit` artifact.
- `menukit-containers/`: the `menukit-containers` artifact.
- `docs/`: the guides above.

Both artifacts build from the workspace root: `./gradlew :menukit:build :menukit-containers:build`.

## Upgrading

### To 6.0.0

6.0.0 is a consolidation release. It adds little, and renames and removes a lot at once, so every mod migrates one time. The [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list. The changes most mods meet:

- Public types moved into `api` packages, and Containers has its own root, `com.trevlar.menukit.containers`. Every import changes. The classes in the table below were renamed as well.
- Placement is declared on the panel. Use `Panel.builder(id).position(PanelPosition.region(OutsideRegion.X).priority(n).offset(dx, dy))`, and the adapters take only `(panel)` or `(panel, padding)`. A HUD panel uses `.region(InsideRegion.X).offset(dx, dy)` where it used `.anchor(MKHudAnchor.X, dx, dy)`.
- Elements are built by builders. For example `Button.builder().label(text).size(w, h).onClick(run).build()`. A control that shows a value takes `state(get, set)` over your field.
- Declarations happen at init. Adapters, HUD panels, operations, slot groups, channels and menus declared after startup throw.
- Custom menus split by side. A menu's slots are common code. How its panels look, and its screen class, are declared on the client with `ClientMenu.of(menu)`.
- A shared slot-state channel names its writers. Without a `canWrite` rule the server refuses every client write to it.
- Item Tips left MenuKit. Durability and food lines on tooltips are now a feature of Inventory Plus.

| 5.x | 6.0.0 |
|---|---|
| `MKHudPanel`, `MKHudNotification` | `HudPanel`, `HudNotification` |
| `MKFocus`, `MKText` | `Focus`, `Text` |
| `MKCContainerPanel` | `ContainerPanel` |
| `MKCSlot`, `MKCSlots` | `CreatedSlot`, `CreatedSlots` |
| `MKCMenu` | `CustomMenu` |
| `MKCScreenHandler` | `CustomContainerMenu` |
| `MKCHandledScreen` | `CustomContainerScreen` |
| `MKCBehaviorKeys` | `ContainerKeys` |
| `MKSlotState` | `SlotState` |
| `showWhen(...)` on a panel, a HUD panel or a container panel | `visibleWhen(...)` |
| `getId()` | `id()` |

### To 5.1.0

Nothing breaks. `SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated for removal in 6.0.0; declare `BehaviorKeys.SHIFT_CLICK_OUT` and `SHIFT_CLICK_IN` instead.

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

Vanilla draws every slot; MenuKit runs no slot pass of its own. `CreatedSlotResolver.resolve` returned the live in-menu `Slot` instead of a position, and `SlotRendering` kept the frame helper and dropped the item-drawing helpers.

### To 3.0.0

The Java package moved from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. No class or method names changed.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
