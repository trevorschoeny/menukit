# MenuKit

Zero-boilerplate UI and inventory framework for Fabric mods.

MenuKit gives you a declarative builder API for adding custom storage, panels, slots, buttons, and HUD elements to Minecraft — without touching a single mixin. Define your containers, declare your slot groups, and let MenuKit handle the wiring.

---

## ⚠️ Documentation status

**The [GitHub wiki](https://github.com/trevorschoeny/menukit/wiki) currently documents the v1 API (`MenuKit.container(...)`) and is out of date.** A full wiki rewrite is planned. Until then, **this README is the authoritative quick-start for the current v2 API**, and the source of truth for API shape is the Javadoc on the public classes in `com.trevorschoeny.menukit`.

---

## Quick start (v2 API)

### 1. Declare a slot group

A **slot group** is MenuKit's primary declaration unit — it combines what was previously spread across container, region, and slot definitions into a single fluent call.

```java
// A 2-slot equipment container that follows the player and is
// stored in player NBT automatically.
MenuKit.slotGroup("equipment")
    .slots(2)
    .playerBound()
    .register();

// A 27-slot backpack, shift-click-enabled.
MenuKit.slotGroup("backpack")
    .slots(27)
    .playerBound()
    .shiftIn()
    .register();

// A furnace fuel slot with an item filter and shift-click-in.
MenuKit.slotGroup("fuel")
    .slots(1)
    .filter(stack -> stack.getBurnTime() > 0)
    .shiftIn()
    .instanceBound()
    .register();
```

### 2. Access the container at runtime

```java
// Get the container for a specific player (off-menu).
MKContainer equipment = MenuKit.getContainerForPlayer(
    "equipment", player.getUUID(), isServer);

ItemStack elytra = equipment.getItem(0);
equipment.setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));

// Listen for changes.
equipment.onChange(() -> {
    // Container contents changed — update UI, sync state, etc.
});
```

### 3. Iterate the player's full storage (off-menu)

```java
// Every slot — vanilla hotbar, main, armor, offhand, plus all
// player-bound MK containers like pockets and equipment.
MKInventory.forEachPlayerSlot(player, (loc, stack) -> {
    if (hasMending(stack)) { /* ... */ }
    return true;  // continue iteration
});

// Only pickup-eligible containers (excludes .excludeFromAutoPickup() groups).
List<MKContainer> targets = MKInventory.getAutoPickupContainers(player);
```

---

## Builder cheat sheet

### Binding (who owns the data)

| Method | Storage |
|---|---|
| `.playerBound()` | Player NBT — follows the player |
| `.instanceBound()` | World SavedData, keyed by block position |
| `.ephemeral()` | In-memory, not persisted (for external-source binding) |

### Persistence (what happens on close)

| Method | Behavior |
|---|---|
| `.persistent()` | Items stay (default) |
| `.transientItems()` | Items eject to inventory on close (crafting-grid style) |
| `.outputOnly()` | Read-only — items can be taken but not placed |

### Transfer rules

| Method | Effect |
|---|---|
| `.shiftIn()` | Allow shift-click IN (default off) |
| `.shiftIn(predicate)` | Conditional shift-click IN |
| `.noShiftOut()` | Disallow shift-click OUT |
| `.shiftOutOnly()` | Only out, never in |
| **`.excludeFromAutoPickup()`** | **Opt out of vanilla item pickup routing (player-bound only, default on)** |

### Slot rules

| Method | Effect |
|---|---|
| `.filter(pred)` | Item filter applied to every slot in the group |
| `.maxStack(n)` | Per-slot max stack override |

### Block-entity break behavior (instance-bound only)

| Method | Effect |
|---|---|
| `.dropsOnBreak()` | Items spill into the world (default, chest-style) |
| `.retainedOnBreak()` | Contents transfer to the dropped item's NBT (shulker-style) |

### Classification (for UI feature eligibility)

```java
.type(MKContainerType.SIMPLE)     // generic storage, sortable
.type(MKContainerType.CRAFTING)   // crafting grids
.type(MKContainerType.PROCESSING) // furnace I/O, stonecutter, etc.
.type(MKContainerType.EQUIPMENT)  // armor slots, equipment panels
.type(MKContainerType.HOTBAR)     // 9-slot hotbar
```

---

## Where things live

- **Slot group builder** — `com.trevorschoeny.menukit.widget.MKSlotGroupBuilder`
- **Off-menu iteration helpers** — `com.trevorschoeny.menukit.data.MKInventory`
- **Container proxy** — `com.trevorschoeny.menukit.container.MKContainer`
- **Main entry point** — `com.trevorschoeny.menukit.MenuKit`

Javadoc on the public classes above is the authoritative reference for the v2 API.

---

## Architectural decisions

MenuKit's design decisions are tracked in the project's `.cairn/decisions/` directory. Notable ones for API consumers:

- **002** — MenuKit as library, not gameplay (MK never holds gameplay logic)
- **003** — Work with vanilla (extend, don't replace)
- **006** — Declarative builder API (the fluent pattern you see above)
- **007** — Precise mixin injection (no `@Overwrite`, use vanilla entry points)
- **010** — Auto-pickup flag on player-bound containers
