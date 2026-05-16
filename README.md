# MenuKit

UI library for Fabric mods. HUD panels, widgets, layouts, modal panels on vanilla menus, region anchoring, click-through prohibition, recipe-book awareness — all client-only.

## What it is

MenuKit is a Fabric-side UI library for mod authors. It provides primitives for putting UI on top of Minecraft:

- **HUD panels** that overlay the game world (waypoint markers, status displays, debug overlays)
- **Decoration panels** on vanilla menu screens (settings buttons next to the inventory, info displays on storage UIs)
- **Standalone screens** built from MenuKit primitives instead of vanilla widget code
- **Region anchoring** so panels position themselves relative to screen edges and other panels — multiple mods coexist without overlapping
- **Modal overlays** with proper click-through prohibition
- **Recipe-book awareness** so panels respect the player's recipe-book state

MenuKit is pure client-side. If you only need UI, depend on MenuKit alone. If you need custom container menus with slots, see **[MenuKit: Containers](https://github.com/trevorschoeny/menukit-containers)** — the slot extension built on MenuKit.

## Install

Add the Modrinth Maven repository and the MenuKit dependency to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}

dependencies {
    modImplementation 'maven.modrinth:menukit:1.0.0'
}
```

And declare the dependency in your `src/main/resources/fabric.mod.json`:

```json
"depends": {
    "menukit": "*"
}
```

That's it. MenuKit is client-only (`environment: client`), so its weight on your mod is zero on the server side.

## Quickstart

### A HUD panel

```java
// In your ClientModInitializer
public void onInitializeClient() {
    MKHudPanel.builder("coords")
        .anchor(MKHudAnchor.TOP_LEFT, 4, 4)
        .padding(4).autoSize()
        .style(PanelStyle.RAISED)
        .text(0, 0, () -> "X: " + (int) Minecraft.getInstance().player.getX())
        .text(0, 12, () -> "Y: " + (int) Minecraft.getInstance().player.getY())
        .build();
}
```

### A decoration panel on a vanilla menu screen

```java
// In your ClientModInitializer
List<PanelElement> elements = List.of(
    new Button(0, 0, 50, 16, Component.literal("Settings"),
        btn -> openSettings()));

Panel buttonPanel = new Panel(
    "myapp-settings-button",
    elements,
    /*visible=*/ true,
    PanelStyle.NONE,
    PanelPosition.BODY,
    /*toggleKey=*/ -1);

new ScreenPanelAdapter(buttonPanel, MenuRegion.RIGHT_ALIGN_TOP, /*padding=*/ 0)
    .on(InventoryScreen.class, CreativeModeInventoryScreen.class);
```

The button appears in the upper-right of vanilla `InventoryScreen` and `CreativeModeInventoryScreen`. If another MenuKit-using mod also drops a panel in `RIGHT_ALIGN_TOP`, both stack vertically — no manual coordination required.

## Feature highlights

- **Region anchoring system.** Eight edge regions (LEFT/RIGHT × ALIGN_TOP/BOTTOM, TOP/BOTTOM × ALIGN_LEFT/RIGHT) plus CENTER. Panels stack in the region's flow direction. Multi-mod-friendly by design — deterministic stacking order across mods via alphabetical-by-modId with optional priority override.
- **Element library.** `Button`, `Toggle`, `Checkbox`, `Radio`, `RadioGroup`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `Slider`, `TextField`, `TextLabel`, `Tooltip`, `ScrollContainer`, `Dropdown`.
- **Modal overlays.** Proper click-through prohibition over vanilla menus. Modals respect Esc, focus traversal, and z-ordering.
- **Recipe-book awareness.** Panels track the recipe-book panel's open/closed state so they don't fight for screen real estate.
- **Auto-wrap + auto-scroll.** `TextLabel.setWrapWidth()` triggers automatic line wrapping; `ScrollContainer` provides scrollable regions with mouse-wheel input.
- **Universal cursor capture.** Compensates for vanilla teleporting the cursor to window center on screen transitions.
- **Stable, opinionated layout.** Pinned-width / pinned-height stacking primitives keep panels predictable across resolutions.

## MenuKit alone vs. MenuKit + Containers

Use **MenuKit** alone if you're building:
- HUD overlays (waypoints, debug, status)
- Decoration panels on vanilla menus (settings buttons, info displays)
- Custom screens built from MenuKit elements

Use **MenuKit + [MenuKit: Containers](https://github.com/trevorschoeny/menukit-containers)** if you're building:
- Custom container menus (storage blocks with new slot layouts)
- Per-slot state that needs server authority
- Anything that touches slot handlers

MenuKit: Containers depends on MenuKit; MenuKit doesn't depend on MenuKit: Containers. The partition is enforced at compile time.

## License

MIT. See `LICENSE`.

## Issues

File issues at [github.com/trevorschoeny/menukit/issues](https://github.com/trevorschoeny/menukit/issues).
