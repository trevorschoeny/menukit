# Getting started

This page takes a Fabric mod from no UI to two visible panels: one on the HUD and one on the inventory screen. It uses MenuKit alone. Slots and custom menus need MenuKit: Containers, covered in [recipes.md](recipes.md).

Prerequisites: a Fabric mod project on Minecraft 26.2 with a client entry point.

## 1. Add the dependency

Add the Modrinth maven and the MenuKit artifact to `build.gradle`. Minecraft 26.2 is unobfuscated, so the dependency goes on `implementation`:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    implementation 'maven.modrinth:menukit:6.0.0+26.2'
}
```

Declare the dependency in `fabric.mod.json`:

```json
"depends": { "menukit": ">=6.0.0 <7.0.0" }
```

Declare both bounds. The lower bound guarantees the API you call exists. The upper bound stops the next breaking MenuKit from loading against a jar built before it existed, which would fail at a class load in the middle of a play session instead of at launch.

Separate the bounds with a space. Fabric accepts a comma here, and the predicate then matches no version at all, which disables your mod without an error message.

[Versioning](versioning.md) has the full contract. Import only from the `api` packages (`com.trevlar.menukit.api.*`); the rest is internal and may change in any release.

## 2. Register a HUD panel

Call this once from the client entry point. The panel renders every frame while its condition holds.

```java
import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.Minecraft;

HudPanel.builder("mymod:readout")
        .region(InsideRegion.CENTER)
        .padding(4)
        .style(PanelStyle.NONE)
        .visibleWhen(() -> Minecraft.getInstance().gui.screen() == null)
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

Result: the text renders just below the crosshair during play and disappears while any screen is open.

A HUD panel sits on one of nine `InsideRegion` spots of the game window, 4 pixels in from the edges it touches. `.offset(dx, dy)` nudges it and `.priority(n)` orders it among other panels on the same spot. `build()` registers the panel and returns it. Register from your client initializer, since registration closes when the client finishes starting. There is no unregister; gate a panel with `visibleWhen` instead.

## 3. Register a panel on the inventory screen

Call this once from the client entry point. The adapter registers itself when it is constructed.

```java
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

Panel panel = Panel.builder("mymod:controls")
        .add(Button.builder().label(Component.literal("Press")).size(90, 16).onClick(() -> {}).build())
        .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP).priority(10))
        .build();

new ScreenPanelAdapter(panel).on(InventoryScreen.class);
```

Result: a 90 by 16 button renders in the top right gutter of the survival inventory screen.

Every element is built the same way: `X.builder()`, the settings it needs, then `build()` (see [Builders and one vocabulary](concepts.md#builders-and-one-vocabulary)). A control that shows a value takes a `state(get, set)` over your own field, for example `Toggle.builder().state(config::sort, config::setSort)`.

The panel says where it sits (`position`); the adapter says which screens it appears on. Without `.on(...)` the panel renders on every container screen. Declare adapters from your client initializer. To show a panel only some of the time, give it a `visibleWhen` rather than removing its adapter.

## 4. Run

Start the client with the mod's `runClient` task. Open the inventory to see the button. Close it to see the HUD text.

## Next

- [concepts.md](concepts.md) defines panels, elements, placement and the five contexts.
- [recipes.md](recipes.md) covers the common tasks, including slots.
- [limits.md](limits.md) lists what MenuKit does not do.
- The [reference](https://trevorschoeny.github.io/menukit/) is the generated javadoc for every public type in both artifacts.
