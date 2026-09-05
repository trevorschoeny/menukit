# Getting started

This page takes a Fabric mod from no UI to two visible panels: one on the HUD and one on the inventory screen. It uses MenuKit alone. Slots and custom menus need MenuKit: Containers, covered in [recipes.md](recipes.md).

Prerequisites: a Fabric mod project on Minecraft 26.2 with a client entry point.

## 1. Add the dependency

Add the Modrinth maven and the MenuKit artifact to `build.gradle`:

```gradle
repositories {
    maven { url 'https://api.modrinth.com/maven' }
}
dependencies {
    modImplementation 'maven.modrinth:menukit:2.0.0+26.2'
}
```

Declare the dependency in `fabric.mod.json`:

```json
"depends": { "menukit": ">=2.0.0" }
```

## 2. Register a HUD panel

Call this once from the client entry point. The panel renders every frame while the condition returns true.

```java
// Source: hive-sight, hud/HiveLook.java (trimmed to the panel call)
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.hud.MKHudAnchor;
import com.trevorschoeny.menukit.hud.MKHudPanel;

MKHudPanel.builder("mymod:readout")
        .anchor(MKHudAnchor.CENTER, 0, 20)
        .autoSize().padding(4)
        .style(PanelStyle.NONE)
        .hideInScreen()
        .showWhen(() -> true)
        .text(0, 0, () -> "Hello from MenuKit")
        .build();
```

Result: the text renders 20 pixels below the crosshair during gameplay and disappears while any screen is open.

`build()` registers the panel. HUD panels have no unregister call. Gate a panel with `showWhen` instead.

## 3. Register a panel on the inventory screen

Call this once from the client entry point. The adapter registers itself in its constructor.

```java
// Source: validator-mk, MkValidatorMkClient.java (trimmed)
import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.inject.ScreenPanelAdapter;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

Panel panel = Panel.builder("mymod:controls")
        .add(new Button(0, 0, 90, 16, Component.literal("Press"), b -> {}))
        .build();

new ScreenPanelAdapter(panel, MenuRegion.RIGHT_ALIGN_TOP.priority(10))
        .on(InventoryScreen.class);
```

Result: a 90 by 16 button renders in the top right gutter of the survival inventory screen.

Without `.on(...)` the panel renders on every container screen. Call `unregister()` on the adapter to remove it.

## 4. Run

Start the client with the mod's `runClient` task. Open the inventory to see the button. Close it to see the HUD text.

## Next

- [concepts.md](concepts.md) defines Panel, element, region, and the four contexts.
- [recipes.md](recipes.md) covers the five common tasks, including slots.
- [limits.md](limits.md) lists what MenuKit does not do.
- [Reference](https://trevorschoeny.github.io/menukit/) is the generated javadoc for every public type.
