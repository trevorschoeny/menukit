# MenuKit

MenuKit is a UI library for Fabric mods. It provides reusable interface components and the systems that place them on screen. Mods build menus, HUD overlays, slots, and in-game UI with it instead of writing Minecraft interface code from scratch.

It ships as two artifacts from this repository:

| Artifact | Runs on | Adds |
|---|---|---|
| `menukit` | Client only | Panels, elements, HUD panels, placement on vanilla screens, standalone screens |
| `menukit-containers` | Client and server, required on both | Created slots, custom container menus, per-slot state, storage attachments |

`menukit-containers` depends on `menukit`. A mod that needs no slots depends on `menukit` alone and stays client-only. A server running Containers requires it on every player's game, checks before the world loads, and tells a player without it which two mods to install.

Components: buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, tooltips, icons, item displays (with a coloured outline), progress bars, dividers, scroll containers, runtime rows (`Flow`), collapsible sections and tabs. Containers adds slots. Every component is built by a builder with the same vocabulary (`at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state`, `onClick`, `style`), and every control that shows a value is a lens onto your own field.

What it does:
- Places UI in five contexts with one set of components: the HUD, container screens, other vanilla screens, named slot groups, and standalone screens. Each context is one host that places, draws and routes its panels the same way.
- Groups components into panels, each a bounded region that renders, takes input, and shows or hides as a unit.
- Positions panels by a placement declared once on the panel, and resizes them to fit automatically, wrapping and scrolling as needed, at any GUI scale.
- Lets more than one mod add UI to the same screen without conflict: panels sharing a region stack in priority order instead of overlapping, the same order on every launch.
- Creates real, server-synced slots as UI components and shows them on every container screen without per-screen setup.
- Publishes every slot group, vanilla or created, under a category any mod can read, with or without a menu open.
- Names each thing that can be done to a slot as an operation. Vanilla's actions become eleven, one per gesture, plain clicks included, and a mod can add its own. Each has a display name and a description. A slot answers for itself first, then its group, then its category, and a locking mod can veto any of them. Every operation checks `SlotOperations.allows` before it acts, and a click a mod sends counts as the operation it was sent for.
- Attaches per-slot state to any slot, private per player or shared across viewers, stored on the slot's owner and readable with `/data get`. The server judges every write a client sends (the channel, the value, the menu, who may write, how often), so a server operator can run it without trusting players.
- Gives its controls vanilla's click sound, narration and keyboard focus, and greys a whole panel or group at once with one `disabledWhen`.
- Handles modal overlays, recipe-book awareness, and cursor stability across screen changes. What a panel covers is inert: an opaque panel takes every point of its rectangle, a see-through one only its buttons and controls, so a slot or widget under a panel neither highlights, clicks nor shows a tooltip.

Its types are vanilla types (a MenuKit slot is a real `Slot`). Requires Fabric.

## Install

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit:5.1.0+26.2'
    // Only if the mod creates slots or custom menus. Pulls in menukit transitively.
    modImplementation 'maven.modrinth:menukit-containers:5.1.0+26.2'
}
```

Declare what you use in `fabric.mod.json`, with both bounds ([why](https://github.com/trevorschoeny/menukit/blob/main/docs/versioning.md)):

```json
"depends": { "menukit": ">=5.1.0 <6.0.0" }
```

```json
"depends": { "menukit": ">=5.1.0 <6.0.0", "menukit-containers": ">=5.1.0 <6.0.0" }
```

## Example

A HUD readout, registered once from the client entry point:

```java
MKHudPanel.builder("mymod:readout")
        .region(InsideRegion.CENTER)            // just below the crosshair
        .padding(4)
        .showWhen(() -> Minecraft.getInstance().gui.screen() == null)
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

Nine synced pocket slots on every container screen, registered once from the common entry point (Containers):

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);

MKCContainerPanel.define("mymod:pockets")
        .at(OutsideRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

## Docs

- [Getting started](https://github.com/trevorschoeny/menukit/blob/main/docs/getting-started.md): dependency, one HUD panel, one inventory-screen panel.
- [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md): panels, elements, placement, the five contexts, claims, slots, addresses.
- [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md): the common tasks, with samples from shipping mods.
- [Limits](https://github.com/trevorschoeny/menukit/blob/main/docs/limits.md): what MenuKit does not do and the open gaps.
- [Reference](https://trevorschoeny.github.io/menukit/): the generated javadoc for both artifacts.

The `validator-mk` and `validator-mkc` mods in the same workspace are the reference consumers, with compiling usage of every primitive.

## Repository layout

- `menukit/`: the `menukit` artifact.
- `menukit-containers/`: the `menukit-containers` artifact.
- `docs/`: the guides above.

Both artifacts build from the workspace root: `./gradlew :menukit:build :menukit-containers:build`.

## Upgrading

### To 6.0.0 (in development)

6.0.0 removes and renames; it is built in phases, and the 6.0.0 migration guide lists every change. The placement change touches most mods: a panel declares where it sits once, with `.position(PanelPosition.region(...).priority(n).offset(dx, dy))`, and the adapters take only `(panel)` or `(panel, padding)`. A HUD panel uses `.region(InsideRegion.X).offset(dx, dy)` where it used `.anchor(MKHudAnchor.X, dx, dy)`. Adapters and HUD panels are declared at init; hide one at runtime with `showWhen`.

The element change touches every mod with UI: elements are built by builders (`Button.builder().label(text).size(w, h).onClick(run).build()`), their positional constructors and `spec(...)` factories are gone, and a control that shows a value takes `state(get, set)` over your field (`Toggle.linked`, `Checkbox.linked` and the stateful constructors are gone). An element's `showWhen` is `visibleWhen` on its builder, `setElementOpaque` is `opaque`, and a custom element's input methods take an `InputContext`.

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

With Containers, every created slot group declares a `SlotGroupCategory`: `SlotSpec.at(id, category)`, `MKCSlots.onto(...).category(...)`, and `PanelBuilder.group(id, category, storage)`. A group without one fails at runtime on the first menu it opens. Each 5.0.0 changelog lists the full set.

### To 4.0.0

Vanilla draws every slot; MenuKit runs no slot pass of its own. Two public shapes changed:

- `CreatedSlotResolver.resolve` returns the live in-menu `Slot` instead of a position. Read `x` and `y` off the returned slot.
- `SlotRendering` keeps the frame helper and its constants. The item-drawing helpers are gone, because vanilla draws the item.

A mod that only builds panels, elements, or slots through the documented builders needs no change. [concepts.md](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md) describes the rendering model.

### To 3.0.0

The Java package moved from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. Replace the prefix in every import. No class or method names changed.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
