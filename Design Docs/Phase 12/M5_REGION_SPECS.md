Left side: `LEFT_ALIGN_TOP` starts at top, flows down. `LEFT_ALIGN_BOTTOM` starts at bottom, flows up.

Right side: `RIGHT_ALIGN_TOP` starts at top, flows down. `RIGHT_ALIGN_BOTTOM` starts at bottom, flows up.

Top side: `TOP_ALIGN_LEFT` starts at left, flows right. `TOP_ALIGN_RIGHT` starts at right, flows left.

Bottom side: `BOTTOM_ALIGN_LEFT` starts at left, flows right. `BOTTOM_ALIGN_RIGHT` starts at right, flows left.

### Positioning behavior

- Regions track `leftPos`, `topPos`, `imageWidth`, `imageHeight` of the container screen. When the recipe book opens and the inventory slides right, all regions move with it.
- Side regions (LEFT, RIGHT) position panels adjacent to the menu frame's edge with a small gap (2px from the frame edge to the first panel, then 2px between stacked panels).
- Top/bottom regions position panels adjacent to the menu frame's top/bottom edge with the same gap behavior.

---

## HUD Context — 9 regions

Positioned relative to the screen edges during normal gameplay (no menu open). The screen is the full game window.

| Region | Anchor position | Flow direction | Example consumer |
|--------|----------------|----------------|------------------|
| `TOP_LEFT` | Top-left corner of the screen | Down ↓ | Debug info area |
| `TOP_RIGHT` | Top-right corner of the screen | Down ↓ | Effects, scoreboard |
| `TOP_CENTER` | Top-center of the screen | Down ↓ | Titles, notifications |
| `LEFT_CENTER` | Left side, vertically centered | Down ↓ | — |
| `RIGHT_CENTER` | Right side, vertically centered | Down ↓ | — |
| `BOTTOM_LEFT` | Bottom-left corner of the screen | Up ↑ | Above chat |
| `BOTTOM_RIGHT` | Bottom-right corner of the screen | Up ↑ | Item pickup log area |
| `BOTTOM_CENTER` | Bottom-center, above hotbar | Up ↑ | Pocket HUD |
| `CENTER` | Below the crosshair, horizontally centered | Down ↓ | Agreeable-allays action hint |

### CENTER region behavior

`CENTER` is positioned below the crosshair and stacks downward like any other region. No special single-panel constraint — normal stacking applies. Mod consumers are expected to use this region responsibly (transient/contextual panels, not persistent UI). This is a social contract, not a technical enforcement.

### HUD-specific notes

- HUD regions don't track a menu frame (there is none). They track screen edges and screen center.
- Vanilla's own HUD elements (hotbar, XP bar, health, hunger, boss bar, chat) are not managed by the region system. Consumer panels may overlap with vanilla HUD elements. This is a v1 limitation — consumers accept it and position accordingly.
- Corner regions have a small inset from the screen edges (matching vanilla's debug screen positioning convention).

---

## Standalone Screen Context — 8 regions

Standalone screens are MenuKit-native screens built with `MenuKitScreenHandler`. The main panel is the screen's primary content. Other mods (or the same mod) can attach additional panels around the main panel using the same side-and-alignment model as inventory context.

The main panel is treated the same way inventory context treats the vanilla menu frame — 8 regions positioned outside it.

| Region | Anchor position | Flow direction |
|--------|----------------|----------------|
| `LEFT_ALIGN_TOP` | Top of the left side, outside the main panel | Down ↓ |
| `LEFT_ALIGN_BOTTOM` | Bottom of the left side, outside the main panel | Up ↑ |
| `RIGHT_ALIGN_TOP` | Top of the right side, outside the main panel | Down ↓ |
| `RIGHT_ALIGN_BOTTOM` | Bottom of the right side, outside the main panel | Up ↑ |
| `TOP_ALIGN_LEFT` | Left end of the top side, outside the main panel | Right → |
| `TOP_ALIGN_RIGHT` | Right end of the top side, outside the main panel | Left ← |
| `BOTTOM_ALIGN_LEFT` | Left end of the bottom side, outside the main panel | Right → |
| `BOTTOM_ALIGN_RIGHT` | Right end of the bottom side, outside the main panel | Left ← |

### Standalone-specific notes

- Regions track the main panel's position and dimensions, same as inventory regions track the menu frame.
- Same naming convention as inventory context (`SIDE_ALIGN_END`). The enum values are shared — the context determines which frame (menu or main panel) the regions attach to.
- If the standalone screen has no main panel (unusual), regions have nothing to anchor to. Behavior: no regions active, consumers fall back to manual positioning.

---

## Implementation notes

### Enum shape (suggested)

Inventory and standalone share the same 8 region names (they're the same spatial concept, just anchored to different frames). HUD has its own 9 names. Two approaches:

**(a) Single enum with context-awareness:**
```java
public enum PanelRegion {
    // Shared by Inventory + Standalone (8)
    LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT,
    // HUD-only (additional 1)
    CENTER;
}
```

**(b) Per-context enums:**
```java
public enum InventoryRegion { LEFT_ALIGN_TOP, ... } // 8
public enum HudRegion { TOP_LEFT, TOP_CENTER, ..., CENTER } // 9
public enum StandaloneRegion { LEFT_ALIGN_TOP, ... } // 8
```

Option (b) is more type-safe — a consumer can't accidentally register an inventory panel in a HUD region. Option (a) is simpler — one enum, context inferred from usage. The design doc should pick one. Lean: **(b)** for type safety since the regions have different naming conventions (inventory uses `SIDE_ALIGN_END`, HUD uses `POSITION` names) and different anchor semantics.

### API shape (suggested, for design doc to finalize)

```java
// Inventory context — on ScreenPanelAdapter or similar
panel.region(InventoryRegion.RIGHT_ALIGN_TOP);

// HUD context — on MKHudPanel builder
MKHudPanel.builder("pocket_hud")
    .region(HudRegion.BOTTOM_CENTER)
    .build();

// Standalone context — on Panel within a MenuKitScreenHandler screen
panel.region(StandaloneRegion.LEFT_ALIGN_TOP);
```

These are suggestions for the design doc, not final API decisions. The design doc should finalize the exact API shape.

---

## What this enables in Phase 13

- **13a:** Migrate settings gear, sandboxes buttons, shulker-palette toggle, lock overlay, pocket HUD from hardcoded coordinates to named regions
- **13b:** F8/F9 equipment and pockets panels positioned via regions from the start (no hardcoded coordinates to migrate later)
- **13c:** F15 peek panel positioned via region
- Future consumer mods attach panels without hardcoding inter-mod coordinate offsets

---

## Summary

| Context | Region count | Naming convention | Anchor frame |
|---------|-------------|-------------------|--------------|
| Inventory | 8 | `SIDE_ALIGN_END` | Vanilla menu frame |
| HUD | 9 | `POSITION` (+ CENTER) | Screen edges + crosshair |
| Standalone | 8 | `SIDE_ALIGN_END` (shared with inventory) | Main panel |

Total: 25 named regions across 3 contexts. V1 scope: registration-order stacking, 2px gap default, cutoff overflow. No priority, no user override, no vanilla-HUD-element awareness.