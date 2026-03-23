# MenuKit API

A "just works" framework for extending Minecraft's inventory, screen, and HUD systems. Declare what you want in a single builder chain — MenuKit handles all mixins, lifecycle, persistence, client/server sync, and creative mode support internally.

**Zero boilerplate. Zero mixins to write. Zero lifecycle management.**

---

## Core Concepts

MenuKit has three layers:

| Layer | What it does | Key classes |
|-------|-------------|-------------|
| **Panels** | Slots + buttons attached to existing menus (inventory, chests) | `MKPanel` |
| **Screens** | Standalone container screens with custom layouts | `MKPanel` with `.screen()` |
| **HUD** | Persistent overlays on the game HUD | `MKHudPanel` |

All three share the same builder pattern and "just work" philosophy.

---

## 1. Panels — Extending Existing Menus

Add custom slots and buttons to vanilla container screens (inventory, chests, etc.).

### Basic panel with slots

```java
MKPanel.builder("my_equipment")
    .showIn(MKContext.SURVIVAL_INVENTORY, MKContext.CREATIVE_INVENTORY)
    .posRight(4, 4)              // 4px right of container, 4px from top
    .padding(4)
    .gap(2)                      // 2px between auto-positioned children
    .autoSize()                  // size wraps content
    .style(MKPanel.Style.RAISED) // 9-slice beveled background
    .slot()                      // auto-positioned (no x,y needed with gap)
        .filter(stack -> stack.is(Items.ELYTRA))
        .maxStack(1)
        .ghostIcon(() -> MY_ELYTRA_ICON)
        .done()
    .slot()                      // auto-positioned below the first
        .maxStack(1)
        .done()
    .build();
```

That's it. MenuKit:
- Creates an `MKContainer` to store items
- Adds `MKSlot` objects to the `InventoryMenu` (both client and server)
- Renders the panel background and slot backgrounds
- Handles creative mode automatically (same panel works in survival and creative)
- Persists items to player NBT (survives world save/load)

### Buttons

```java
MKPanel.builder("controls")
    .showIn(MKContext.ALL_INVENTORIES)
    .posRight(4, 4)
    .padding(4).gap(2).autoSize()
    .style(MKPanel.Style.RAISED)
    .button()
        .label("Click Me")
        .onClick(btn -> doSomething())
        .done()
    .button()
        .label("Toggle")
        .toggle()
        .onToggle((btn, pressed) -> setEnabled(pressed))
        .done()
    .button()
        .label("Sleek Blue")
        .sleek()                     // panel-colored fill, blue hover
        .onClick(btn -> doMore())
        .done()
    .build();
```

### Button features

| Method | Description |
|--------|-------------|
| `.label("text")` | Button text |
| `.size(w, h)` | Explicit size (auto-sizes to text if omitted) |
| `.onClick(btn -> ...)` | Click handler |
| `.toggle()` | Makes it a toggle button (pressed/unpressed state) |
| `.onToggle((btn, on) -> ...)` | Toggle state change handler |
| `.group(myGroup)` | Radio group (only one active at a time) |
| `.opensScreen("name")` | Opens a MenuKit standalone screen |
| `.opensScreen(() -> new MyScreen())` | Opens any vanilla Screen |
| `.togglesPanel("name")` | Shows/hides another panel |
| `.goesBack()` | Returns to the previous screen |
| `.closesScreen()` | Closes to game world |

### Panel positioning

Panels position relative to the container edge, so they adapt to different container sizes (survival vs creative):

```java
.posRight(4, 30)    // 4px right of container, 30px from top
.posLeft(4, 30)     // 4px left of container
.posAbove(30, 4)    // 30px from left, 4px above container
.posBelow(30, 4)    // 30px from left, 4px below container
```

### Auto-layout

Use `.gap(int)` to auto-position children vertically with spacing. No manual x,y needed:

```java
.gap(2)                // 2px between each child
.slot().done()         // auto y=0
.slot().done()         // auto y=20 (18 slot + 2 gap)
.button().label("Go").done()  // auto y=40
```

Use `.horizontal()` to stack left-to-right instead. Manual `.slot(x, y)` and `.button(x, y)` still work for explicit positioning.

### Panel styles

| Style | Description |
|-------|-------------|
| `RAISED` | 9-slice beveled panel (matches vanilla inventory look, uses custom sprite) |
| `DARK` | Dark panel (uses vanilla's effect_background sprite) |
| `FLAT` | Solid gray fill with border |
| `INSET` | Sunken panel (like slot backgrounds) |
| `CUSTOM` | User-provided 9-slice sprite (via `.customSprite(Identifier)`) |
| `NONE` | Invisible — just positioning, no visual. Zero padding automatically. |

### Show/hide panels

```java
// Panel starts hidden
MKPanel.builder("popup")
    .showIn(MKContext.ALL_INVENTORIES)
    .posRight(4, 4)       // collision avoidance stacks below other panels
    .hidden()                        // invisible until toggled
    .style(MKPanel.Style.RAISED)
    .slot().done()
    .build();

// Button toggles it
MKPanel.builder("main")
    .showIn(MKContext.ALL_INVENTORIES)
    .posRight(4, 4)
    .button()
        .label("Toggle Popup")
        .togglesPanel("popup")       // shows/hides the popup panel
        .done()
    .build();
```

When hidden: slots become inactive (no hover/click), buttons become invisible, panel background doesn't render.

### Shared slots

The same item data can appear in multiple panels/screens/HUD:

```java
// Original slot — data lives here
MKPanel.builder("equipment", InventoryMenu.class)
    .slot(0, 0).done()
    .build();

// Shared slot in another screen — references the same container
MKPanel.builder("settings_screen")
    .screen()
    .sharedSlot("equipment", 0, 0, 0)  // same data as equipment's slot 0
    .build();

// Same item on the HUD
MKHudPanel.builder("equip_hud")
    .item(0, 0, MenuKit.slotItem("equipment", 0))  // reads from same container
    .build();
```

### Persistence callbacks

Items are persisted automatically. For custom data alongside items:

```java
MKPanel.builder("my_feature", InventoryMenu.class)
    .slot(0, 0).done()
    .onSave(output -> {
        output.putBoolean("enabled", myFeature.isEnabled());
        output.putInt("mode", myFeature.getMode());
    })
    .onLoad(input -> {
        myFeature.setEnabled(input.getBooleanOr("enabled", true));
        myFeature.setMode(input.getIntOr("mode", 0));
    })
    .build();
```

### Context visibility (showIn)

Control WHERE a panel appears using `MKContext`:

```java
// Show in specific contexts
.showIn(MKContext.SURVIVAL_INVENTORY, MKContext.CREATIVE_INVENTORY)

// Show everywhere
.showIn(MKContext.ALL)

// Use shortcut groups
.showIn(MKContext.ALL_INVENTORIES)   // survival + all creative
.showIn(MKContext.ALL_STORAGE)       // chest, ender chest, barrel, shulker box, hopper
.showIn(MKContext.ALL_CRAFTING)      // crafting table, stonecutter, smithing, loom, etc.
.showIn(MKContext.ALL_PROCESSING)    // furnace, blast furnace, smoker, brewing stand
```

Available contexts: `SURVIVAL_INVENTORY`, `CREATIVE_INVENTORY`, `CREATIVE_TABS`, `CHEST`, `DOUBLE_CHEST`, `ENDER_CHEST`, `BARREL`, `SHULKER_BOX`, `HOPPER`, `DISPENSER`, `CRAFTING_TABLE`, `STONECUTTER`, `SMITHING_TABLE`, `LOOM`, `CARTOGRAPHY_TABLE`, `GRINDSTONE`, `FURNACE`, `BLAST_FURNACE`, `SMOKER`, `BREWING_STAND`, `ANVIL`, `ENCHANTING_TABLE`, `BEACON`, `VILLAGER_TRADING`, `HORSE_INVENTORY`

### Per-context positioning

Different containers have different layouts. Override positions per context:

```java
.posRight(4, 4)                                     // default for most contexts
.posFor(MKContext.SURVIVAL_INVENTORY, 76, 25)        // absolute override for survival
.posFor(MKContext.CREATIVE_INVENTORY, 15, 10)        // absolute override for creative
```

### Collision avoidance

Panels automatically avoid overlapping with:
- **Status effects** (right side) — panels shift down
- **Creative tabs** (above/below) — panels shift further out
- **Other MKPanels** — later panels shift below earlier ones

Disable with `.allowOverlap()`.

### Button styles

```java
.button().label("Standard").done()                    // STANDARD: vanilla button texture
.button().label("Sleek").sleek().done()               // SLEEK: panel-colored, blue hover
.button().label("Disabled").disabled().done()          // works with both styles
```

### Ghost icons on slots

Show a faded icon when the slot is empty (like vanilla's armor slot icons):

```java
.slot()
    .ghostIcon(() -> Identifier.fromNamespaceAndPath("my-mod", "container/slot/my_icon"))
    .done()
```

Ghost icons can be drawn programmatically or as texture sprites. They automatically hide when an item is in the slot.

---

## 2. Screens — Standalone Container Screens

Full-screen UIs with custom layouts, opened from buttons or programmatically.

### Panel-style screen (like a chest)

```java
MKPanel.builder("my_container")
    .screen()
    .title("My Container")
    .padding(8)
    .style(MKPanel.Style.RAISED)
    .slot(0, 0).done()
    .slot(22, 0).done()
    .button(0, 30).label("Close").closesScreen().done()
    .includePlayerInventory()    // adds player inventory at the bottom
    .build();
```

### Transparent-style screen (like settings)

```java
MKPanel.builder("my_settings")
    .screen()
    .title("Settings")
    .padding(8)
    .style(MKPanel.Style.NONE)   // transparent — blurred world background
    .button(0, 0).label("Option A").toggle().done()
    .button(0, 20).label("Option B").toggle().done()
    .button(0, 50).label("Back").goesBack().done()
    .build();
```

### Opening screens

```java
// From a MenuKit button
.button(0, 0).label("Open").opensScreen("my_container").done()

// From a MenuKit button — any vanilla Screen
.button(0, 0).label("Open").opensScreen(() -> new MyScreen()).done()

// Programmatically
MenuKit.openScreen(player, "my_container");
```

### Navigation

- `.goesBack()` — returns to the previous screen (preserves history)
- `.closesScreen()` — closes to game world
- Mouse position is preserved when going back

---

## 3. HUD — Game Overlay Elements

Persistent UI elements on the game HUD. All values can be dynamic via `Supplier<>`.

### Text display

```java
MKHudPanel.builder("coords")
    .anchor(MKHudAnchor.TOP_LEFT, 4, 4)
    .padding(4).autoSize()
    .style(MKPanel.Style.FLAT)
    .text(0, 0, () -> String.format("X: %.0f", player.getX()))
    .text(0, 12, () -> String.format("Y: %.0f", player.getY()))
    .text(0, 24, () -> String.format("Z: %.0f", player.getZ()))
    .build();
```

### Progress bar

```java
MKHudPanel.builder("health")
    .anchor(MKHudAnchor.BOTTOM_CENTER, 0, -50)
    .padding(2).autoSize()
    .style(MKPanel.Style.NONE)
    .bar(0, 0, 80, 6)
        .value(() -> player.getHealth() / player.getMaxHealth())
        .color(0xFFFF0000)        // fill color
        .bgColor(0xFF550000)      // background color
        .done()
    .build();
```

### Item display

```java
MKHudPanel.builder("held_item")
    .anchor(MKHudAnchor.BOTTOM_RIGHT, -4, -4)
    .padding(4).autoSize()
    .style(MKPanel.Style.RAISED)
    .item(0, 0, () -> player.getMainHandItem())
        // showCount and showDurability are true by default
    .showWhen(() -> !player.getMainHandItem().isEmpty())
    .build();
```

### Notifications

```java
// Register the notification panel
MKHudPanel.builder("damage_alert")
    .anchor(MKHudAnchor.TOP_CENTER, 0, 30)
    .padding(6).autoSize()
    .style(MKPanel.Style.RAISED)
    .notification()              // makes this a notification panel
        .duration(2000)          // visible for 2 seconds
        .fadeOut(500)            // 500ms fade-out
        .slideFrom(MKHudAnchor.TOP_CENTER)  // slides in from top
        .done()
    .text(0, 0, () -> "Ouch!")
    .build();

// Trigger it from anywhere
MenuKit.notify("damage_alert");
```

### HUD anchoring

```java
.anchor(MKHudAnchor.TOP_LEFT, 4, 4)        // top-left corner
.anchor(MKHudAnchor.TOP_CENTER, 0, 10)      // centered at top
.anchor(MKHudAnchor.TOP_RIGHT, -4, 4)       // top-right corner
.anchor(MKHudAnchor.BOTTOM_LEFT, 4, -4)     // bottom-left
.anchor(MKHudAnchor.BOTTOM_CENTER, 0, -50)  // centered above hotbar
.anchor(MKHudAnchor.BOTTOM_RIGHT, -4, -4)   // bottom-right
```

### Visibility conditions

```java
.showWhen(() -> player.getHealth() < 10)  // only show when low health
.hideInScreen()    // hide when any screen is open (default)
.showInScreen()    // keep showing even with screens open
```

### Reading slot data on the HUD

```java
// Show a panel's slot item on the HUD
.item(0, 0, MenuKit.slotItem("my_panel", 0))
```

### Custom rendering

Any element supports a raw render callback for custom drawing:

```java
MKHudPanel.builder("custom")
    .anchor(MKHudAnchor.TOP_RIGHT, -4, 4)
    .size(80, 80)
    .onRender((graphics, x, y, w, h, delta) -> {
        // Full GuiGraphics access — draw anything
        graphics.fill(x, y, x + w, y + h, 0x80000000);
        myRenderer.draw(graphics, x, y);
    })
    .build();
```

---

## API Reference

### MenuKit (static methods)

| Method | Description |
|--------|-------------|
| `MenuKit.init()` | Registers menu types. Call in `onInitialize()` before any `.build()` calls. |
| `MenuKit.openScreen(player, "name")` | Opens a standalone screen |
| `MenuKit.notify("name")` | Triggers a notification |
| `MenuKit.togglePanel("name")` | Shows/hides a panel |
| `MenuKit.showPanel("name")` | Shows a hidden panel |
| `MenuKit.hidePanel("name")` | Hides a panel |
| `MenuKit.isPanelHidden("name")` | Checks panel visibility |
| `MenuKit.slotItem("panel", index)` | Returns `Supplier<ItemStack>` reading from a panel's slot |

### MKPanel.Style

| Style | Description |
|-------|-------------|
| `RAISED` | 9-slice beveled panel matching vanilla inventory (custom sprite) |
| `DARK` | Dark panel using vanilla's `effect_background` sprite |
| `FLAT` | Solid fill with dark border |
| `INSET` | Sunken (dark top-left, light bottom-right) |
| `CUSTOM` | User-provided 9-slice sprite (via `.customSprite()`) |
| `NONE` | Invisible (positioning only, zero padding) |

### MKHudAnchor

| Anchor | Description |
|--------|-------------|
| `TOP_LEFT` | Top-left corner of screen |
| `TOP_CENTER` | Centered at top |
| `TOP_RIGHT` | Top-right corner |
| `BOTTOM_LEFT` | Bottom-left corner |
| `BOTTOM_CENTER` | Centered at bottom |
| `BOTTOM_RIGHT` | Bottom-right corner |

---

## Architecture

MenuKit handles everything internally through these components (users never interact with them):

| Internal component | Purpose |
|-------------------|---------|
| `MKContainer` | `Container` implementation storing items (like `SimpleContainer`) |
| `MKSlot` | `Slot` subclass with filtering, max stack, ghost icons |
| `MKButton` | `AbstractWidget` subclass — STANDARD (vanilla sprites) or SLEEK (blue hover) |
| `MKMenu` | `AbstractContainerMenu` for standalone screens |
| `MKScreen` | `AbstractContainerScreen` with transparent background by default |
| `MKContext` | Enum mapping every vanilla container to a context for `showIn()` |
| `MKMenuMixin` | Adds MKSlots to `InventoryMenu` at construction |
| `MKGenericMenuMixin` | Adds MKSlots to ALL other menus via `addStandardInventorySlots` |
| `MKItemPickerMenuMixin` | Adds MKSlots to creative's `ItemPickerMenu` for item tab interaction |
| `MKScreenMixin` | Adds buttons + renders panels on ALL container screens |
| `MKCreativeMixin` | Repositions slots + fixes click detection in creative mode |
| `MKCreativeSlotPacketMixin` | Enables creative mode slot sync for MKSlot indices |
| `MKHudMixin` | Renders HUD elements during `Gui.render()` |
| `ServerPlayerMixin` | Handles persistence (save/load to player NBT) |
| `SlotPositionAccessor` | `@Mutable @Accessor` for dynamic slot x/y repositioning |

### Client/Server sync

Matches vanilla's pattern exactly:
- **Server** has authoritative `MKContainer` (persisted to NBT)
- **Client** has a synced copy (populated via `broadcastChanges()`)
- MKSlots are real `Slot` objects in the menu — vanilla handles all sync natively
- No custom packets, no manual sync code

### Persistence

Automatic. Items in MKContainers are saved/loaded with the player's NBT data. Custom data via `.onSave()` / `.onLoad()` callbacks.
