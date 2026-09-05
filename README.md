# MenuKit

MenuKit is a client-side UI library for Fabric mods. It provides reusable interface components and the systems that place them on screen. Mods build menus, HUD overlays, and in-game UI with it instead of writing Minecraft interface code from scratch.

Components: buttons, toggles, checkboxes, radio buttons, sliders, dropdowns (single and multi-select), text fields, labels, tooltips, icons, item displays, progress bars, dividers, and scroll containers.

What it does:
- Places UI in four contexts with one set of components: the HUD, vanilla menu screens, named slot groups, and standalone screens.
- Groups components into panels, each a bounded region that renders, takes input, and shows or hides as a unit.
- Positions panels by screen region and resizes them to fit automatically, wrapping and scrolling as needed, at any GUI scale.
- Lets more than one mod add UI to the same screen without conflict: panels sharing a region stack in order instead of overlapping.
- Exposes slots created through MenuKit: Containers to every MenuKit consumer, so a mod can read and address another mod's custom slots.
- Handles modal overlays, click-through prohibition, recipe-book awareness, and cursor stability across screen changes.

MenuKit is client-only. Its types are vanilla types (a MenuKit slot is a real `Slot`). Requires Fabric. For custom container menus and slots with server-synced state, add MenuKit: Containers.

## Install

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit:2.0.0+26.2'
}
```

Declare it in `fabric.mod.json`:

```json
"depends": { "menukit": ">=2.0.0" }
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

## Docs

- [Getting started](docs/getting-started.md): dependency, one HUD panel, one inventory-screen panel.
- [Concepts](docs/concepts.md): panels, elements, regions, the four contexts, slots, addresses.
- [Recipes](docs/recipes.md): the common tasks, with samples from shipping mods.
- [Limits](docs/limits.md): what MenuKit does not do and the open gaps.
- Reference: run `./gradlew javadoc` and open `build/docs/javadoc/index.html`.

The `validator-mk` mod in the same workspace is the reference consumer, with compiling usage of every primitive.

## License

MIT. See `LICENSE`.

## Issues

[github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
