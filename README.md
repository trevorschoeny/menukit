# MenuKit

MenuKit is a UI library for Fabric mods. It provides reusable interface components and the systems that place them on screen. Mods build menus, HUD overlays, slots, and in-game UI with it instead of writing Minecraft interface code from scratch.

It ships as two artifacts from this repository:

| Artifact | Runs on | Adds |
|---|---|---|
| `menukit` | Client only | Panels, elements, HUD panels, placement on vanilla screens, standalone screens |
| `menukit-containers` | Client and server | Created slots, custom container menus, per-slot state, storage attachments |

`menukit-containers` depends on `menukit`. A mod that needs no slots depends on `menukit` alone and stays client-only.

Components: buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, tooltips, icons, item displays, progress bars, dividers, and scroll containers. Containers adds slots.

What it does:
- Places UI in four contexts with one set of components: the HUD, vanilla menu screens, named slot groups, and standalone screens.
- Groups components into panels, each a bounded region that renders, takes input, and shows or hides as a unit.
- Positions panels by screen region and resizes them to fit automatically, wrapping and scrolling as needed, at any GUI scale.
- Lets more than one mod add UI to the same screen without conflict: panels sharing a region stack in order instead of overlapping.
- Creates real, server-synced slots as UI components and shows them on every container screen without per-screen setup.
- Attaches per-slot state to any slot, private per player or shared across viewers, stored on the slot's owner and readable with `/data get`.
- Handles modal overlays, click-through prohibition, recipe-book awareness, and cursor stability across screen changes.

Its types are vanilla types (a MenuKit slot is a real `Slot`). Requires Fabric.

## Install

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit:3.0.0+26.2'
    // Only if the mod creates slots or custom menus. Pulls in menukit transitively.
    modImplementation 'maven.modrinth:menukit-containers:3.0.0+26.2'
}
```

Declare what you use in `fabric.mod.json`:

```json
"depends": { "menukit": ">=3.0.0" }
```

```json
"depends": { "menukit": ">=3.0.0", "menukit-containers": ">=3.0.0" }
```

## Example

A HUD readout, registered once from the client entry point:

```java
MKHudPanel.builder("mymod:readout")
        .anchor(MKHudAnchor.CENTER, 0, 20)
        .autoSize().padding(4)
        .hideInScreen()
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

Nine synced pocket slots on every container screen, registered once from the common entry point (Containers):

```java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);

MKCContainerPanel.define("mymod:pockets")
        .at(MenuRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .addSlot(SlotSpec.at("pockets").count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

## Docs

- [Getting started](docs/getting-started.md): dependency, one HUD panel, one inventory-screen panel.
- [Concepts](docs/concepts.md): panels, elements, regions, the four contexts, slots, addresses.
- [Recipes](docs/recipes.md): the common tasks, with samples from shipping mods.
- [Limits](docs/limits.md): what MenuKit does not do and the open gaps.
- [Reference](https://trevorschoeny.github.io/menukit/): the generated javadoc for both artifacts.

The `validator-mk` and `validator-mkc` mods in the same workspace are the reference consumers, with compiling usage of every primitive.

## Repository layout

- `menukit/`: the `menukit` artifact.
- `menukit-containers/`: the `menukit-containers` artifact.
- `docs/`: the guides above.

Both artifacts build from the workspace root: `./gradlew :menukit:build :menukit-containers:build`.

## Upgrading from 2.x

3.0.0 renames the Java package from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. Replace the prefix in every import. No class or method names changed.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
