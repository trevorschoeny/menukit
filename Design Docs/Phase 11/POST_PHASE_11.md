# Phase 11 — Post-Phase-11 Entries

Accumulated feature deferrals (F*) and mechanism candidates (M*) across all consumer-mod refactors. Phase 12 reads the M* entries as design input for MenuKit primitives. Phase 13 completes the F* entries against Phase 12's primitives.

Organized by origin mod. Each entry names what Phase 13 delivers, the mechanism (if any) that enables it, and a rough implementation sketch.

---

## Inventory Plus

### Features

**F1 — Persistent player-slot lock state across sessions.**
Lock state currently lives in `ServerLockStateHolder` / `ClientLockStateHolder` (per-session, keyed by `SlotIdentity`). Survives menu transitions within a session but resets on disconnect. Persistence requires a per-slot state primitive that survives across sessions.
**Blocked on:** M1 (unified per-slot state primitive).
**Phase 13 sketch:** When M1 ships, back lock state by the primitive. Migration: read existing per-session state into the persistent store on first use; clear per-session holder.

**F2 — Chest-slot lock state visible across menu reopens.**
When a chest is reopened, the lock overlay should show which slots the player previously locked. Currently reset because lock state is per-session.
**Blocked on:** M1.
**Phase 13 sketch:** Same backing store as F1. Per-container-slot state keyed by `SlotIdentity`.

**F3 — Full-lock (Ctrl+click) feature.**
A broader lock that blocks direct PICKUP clicks as well as auto-routing. Different semantic from sort-lock (which only blocks `moveItemStackTo` + `QUICK_MOVE`). New feature, not a fix.
**Category:** feature-addition.
**Phase 13 sketch:** New `FullLock` state alongside `SortLock`. `IPSlotMayPlaceMixin` checks both; `IPSlotGetItemMixin` checks both during routing + blocks during PICKUP for full-lock.

**F4 — Sort consolidation.**
After sorting, merge partial stacks of the same item into full stacks before arranging. Currently sort only reorders without consolidating.
**Category:** feature-complexity.
**Phase 13 sketch:** Pre-pass in `SortAlgorithm`: group by item, consolidate to max stack size, then run existing sort. Click sequence computes PICKUP + transfer + DROP clicks for the merge phase.

**F5 — Creative-mode sort.**
Sort in creative inventory. Requires routing through the creative tab's separate packet path.
**Category:** feature-scope (creative packet path).
**Phase 13 sketch:** Investigate whether `ClickSequenceC2SPayload` can route through creative's container, or whether a separate creative-specific payload is needed.

**F6 — Creative-mode bulk-move.**
Same creative-packet-path blocker as F5.
**Category:** feature-scope.

**F7 — Bulk-move within a single player-inventory region (no container open).**
Shift+double-click in the hotbar or main inventory with no container open. Vanilla's `doubleclick` / `lastQuickMoved` interaction produces a one-stack-back-to-anchor bug.
**Category:** feature-complexity (vanilla click-protocol edge case).
**Phase 13 sketch:** May resolve for free if F3 investigation digs into vanilla's double-click state fields. Otherwise defer indefinitely.

**F8 — Equipment panel.**
Two-slot panel (elytra + totem) visible in `InventoryScreen` + `CreativeModeInventoryScreen`, with per-slot filters, ghost icons, and config-driven disable. Passive behaviors (flight, totem, mending, death drops, wings rendering) are already live in Layer 2 — only the in-inventory UI is deferred.
**Category:** primitive-blocked.
**Blocked on:** M4 (vanilla menu slot injection primitive).
**Phase 13 sketch:** Rebuild as `Panel` + 2-slot `SlotGroup` over `PlayerAttachedStorage.forEquipment()`. Per-slot `InteractionPolicy.input(stack -> isElytra(stack) || isTotem(stack))` with `.withMaxStackSize(s -> 1)`. Ghost icon via SlotGroup's `.ghostIcon(Identifier)` option. `PanelStyle.NONE` — blends with vanilla armor column. Per-screen origin resolution in `EquipmentDecoration.originForScreen` (survival vs creative).

**F9 — Pockets panels.**
Nine `Toggle.linked` buttons (one per hotbar slot, RAISED/INSET) + nine 3-slot pocket panels backed by the `POCKETS` attachment, with `POCKET_DISABLED` integration for per-slot disable-toggling via empty-click.
**Category:** primitive-blocked.
**Blocked on:** M4.
**Phase 13 sketch:** Per refactor plan § 4.2. Nine `Panel`s with 3-slot `SlotGroup`s backed by `PlayerAttachedStorage.forPocketSlice(hotbarSlot)`. Per-slot `.disabledWhen(pocketIndex >= config.pocketSlotCount)`, `.ghostIcon(BARRIER when disabled)`, `.onEmptyClick(toggleDisabled)`, `.emptyTooltip("Enable/Disable slot")`. `openPocketIndex` lives client-only in `PocketsDecoration` (int, -1 when none open). Nine `Toggle.linked` buttons at hotbar positions — supplier reads `openPocketIndex == i`, click flips it.
**Note on existing files:** `PocketsPanel.java` was deleted during Layer 5 cleanup — it had only stub methods with no live callers. Phase 13 creates a fresh `PocketsDecoration` class rather than reviving `PocketsPanel`. The attachment-layer predicates (`PocketDisabledData.isDisabled(h, p)`, etc.) are the canonical state read path.

**F10 — In-inventory pocket HUD toggle button.**
If eventually desired: a small button in the inventory screen that toggles the pocket HUD's visibility. Not currently in the refactor plan — speculative placeholder if users request it. HUD itself (Layer 2 shipped) exists independently of this.
**Category:** feature-addition.
**Phase 13 sketch:** Small `Toggle.linked` button in the settings region, reading/writing `config.showPocketHud`.

**F15 — Peek panel UI (umbrella — subsumes F11–F14).**
The visible peek panel and all user-facing peek behavior: rendering, click dispatch, sort-within-peek, move-matching-into-peek, drop, double-click-collect. These defer together because they all depend on the same missing slot primitive.
**Category:** primitive-blocked.
**Blocked on:** M6 (client-side slot primitive for decoration panels). Sort/move-matching sub-features additionally need protocol-design work, but that shape depends on the UI primitive settled by M6.
**What ships in Phase 11 Layer 3 (kept live):**
- Six peek packet types (wire protocol)
- `ContainerPeek` server-side: stateless open/move/close handlers with per-source-type storage access (shulker `CUSTOM_DATA`, bundle `BUNDLE_CONTENTS`, ender inventory)
- `ContainerPeekClient` client session state + S2C receivers
- Cross-mod public API (five stable methods + `PeekSourceType` enum) — see `inventory-plus/PUBLIC_API.md`
- Peek keybind registered; consumes key silently (doesn't conflict with vanilla Alt-handling)

**What's deferred:**
- Visual peek panel rendering
- Click dispatch into peek slots
- Sort / move-matching / drop / double-click-collect actions
- The `handlePeek` keybind behavior that opens a session

**Phase 13 sketch:** When M6 ships, populate `PeekDecoration` with render + click dispatch using the new primitive. Populate `KeybindDispatch.handlePeek` to call `ContainerPeekClient.openPeek(menuSlotIndex)`. The infrastructure underneath (packets / server / session / API) stays unchanged — only the UI layer needs to be rebuilt. Sort and move-matching sub-features once UI shape settles.

**Historical note:** A prior Layer 3 pass hand-rolled peek panel rendering via raw `graphics.fill()` calls and custom hit-testing, violating the conforming-to-primitives principle. Reverted when the architectural mismatch was called out. F15 now properly subsumes what were F11–F14 separate-feature entries; those finer-grained sub-features are implementation choices within F15's scope rather than independently-deferrable items.

### Mechanisms

**M1 — Unified per-slot state primitive.**
Design a MenuKit primitive for persistent per-slot metadata that survives menu transitions and sessions. Keyed by `SlotIdentity`. Enables F1 + F2 + future consumer mod state needs.
**Status:** design input. Phase 12 reads M1 against multi-consumer evidence before designing.
**Evidence from IP:** lock state needs persistent per-slot boolean(s). Sort-lock vs full-lock distinction may suggest typed state rather than a single flag.

**M2 — SlotIdentity.**
`SlotIdentity` record `(Container, int containerSlot)` + static factory `SlotIdentity.of(Slot)`. Zero-dependency primitive for cross-menu stable slot identity.
**Status:** shipped in Phase 11 IP. M1 builds on it.

**M3 — MKFamily removal (or keep-as-is decision).**
Cleanup mechanism. MKFamily groups config categories under shared screens. If removed, Phase 13 refactors consumer-side config screens to stand alone. If kept, no action needed.
**Status:** decision pending. Not blocking features.

**M6 — Client-side slot primitive for decoration panels.**
Design a MenuKit primitive for rendering slot-like interactive elements inside a decoration Panel (Pattern 2/3 injection over vanilla screens), backed by a client-side Storage, not attached to any vanilla menu.
**Status:** dissolved in Phase 12. Verification showed peek requires vanilla-native slot instances via M4 (full drag / shift-click / cursor protocol), not client-side decoration slots. Rendering analysis (SlotRendering utility) carries forward to M4's design. See `Design Docs/Phase 12/M6_CLIENT_SIDE_SLOTS.md` for the dissolution record.
**Architectural distinction from M4:** M4 is for injecting real slots into a vanilla `containerMenu.slots` list (vanilla-menu integration). M6 is for rendering slot-like elements in a client-only decoration panel that never participates in vanilla's slot-click protocol. Different integration contexts; probably different primitive shapes even if they share some rendering code.
**Evidence:** IP's peek panel (F15). Peek slots:
- are client-only (no server menu slot)
- render with vanilla-style slot backgrounds + hover highlights + item decorations
- accept clicks that translate to C2S packets (not vanilla `menu.clicked`)
- are backed by a client-side `Storage` (the peek session's items)

**Shape hints (not design — Phase 12's job):**
- A `PanelElement` subclass or factory, e.g., `Slot.client(Storage, int index, ...)` or `new ClientSlot(...)`
- Renders using vanilla slot sprites (`SLOT_HIGHLIGHT_BACK_SPRITE` / `SLOT_HIGHLIGHT_FRONT_SPRITE`) so the visual matches real slots
- Hit-testing like `Button` does — element handles its own hover state + click dispatch
- Click handler receives `(button, shiftHeld, slotIndex)` and returns whether consumed
- Storage binding for display (read item via `storage.getStack(index)`)

**Relationship to existing primitives:**
- Likely sits in `core/` alongside `Button`, `ItemDisplay`, etc.
- Complements `ItemDisplay` (render-only item) with interactive slot semantics
- Consumer code composes `ClientSlot` instances inside a `Panel`, same as any other `PanelElement`

**Evidence from a hand-rolled attempt:** IP's prior Layer 3 pass rendered peek slots with custom `graphics.fill()` calls. It "worked" visually but looked non-native (missing vanilla hover sprites, wrong highlight color, etc.). Conforming to primitives isn't just aesthetic — the hand-rolled version also had to hand-roll click semantics (hit-testing, hover tracking) that a proper primitive would centralize.

**Non-goals:** M6 is not for making peek slots participate in vanilla's slot-click protocol. Peek is client-side; mutations round-trip through IP's packets. M6 is just the rendering + input-dispatch primitive, not a cross-menu integration.

**Architectural relationship to M1, M4, M5:**
- M1 = persistence-shaped (per-slot state across sessions)
- M4 = integration-shaped (SlotGroup in vanilla menus)
- M5 = layout-shaped (positioning arbitration)
- M6 = rendering-shaped (slot-like primitive for decoration panels)

Four independent mechanism categories; Phase 12 designs each on its own.

**M5 — Context-scoped region system for panel positioning.**
Design a primitive for mods to declare panel positions by named region rather than pixel coordinates, scoped per-context. Collision arbitration via stacking along region-defined flow axes. Each region is an anchor + flow direction pair; panels attached to the same region stack with gaps along the flow axis.
**Status:** design input — decision pending. **Exact region sets per context await Trevor's design decision.** Do not begin implementation.
**Contexts to address:**
- **InventoryContext** — screen-ful inventory UIs (survival inventory, creative inventory, chests, other containers). Panels positioned outside the menu frame, adaptive to menu size and recipe-book-open state.
- **HudContext** — overlaid on gameworld during normal play.
- **StandaloneScreenContext** — MenuKit-native screens not attached to vanilla menus.

**V1 scope (when this ships):**
- Stacking order: registration order
- Stacking spacing: with gaps (default 2px, panel-level override available)
- Overflow: cutoff (panels beyond region capacity don't render)
- No user override, no priority, no graceful overflow

**Out of scope for v1:**
- Vanilla-element-anchored regions (crafting grid, player preview, etc.) — consumers use manual offsets from screen-level regions instead
- Edge-center regions in InventoryContext (ambiguous flow axis)
- Main menu / pause menu regions (not in InventoryContext — those screens aren't in scope)
- Priority-based stacking (v2 concern)
- User-configurable override (v3 concern)
- Gameplay-state-aware HUD regions (chat open, boss bar, etc.)

**Evidence:** Sandboxes manually offset "13px left of IP's settings gear" to avoid collision (single-pair collision solved ad hoc). Shulker-palette's ShulkerBoxScreen toggle similarly picks fixed coordinates with no collision awareness. As additional consumers want top-right inventory space, systematic arbitration compounds.

**Design choice pending:** exact region set per context. InventoryContext is leaning toward four corners (top-left, top-right, bottom-left, bottom-right) with natural flow directions, but the full set including HudContext specifics is not yet committed. **Do not begin implementation until Trevor commits the region sets.**

**Shape of API (tentative, for Phase 12 to finalize):**

```java
// Likely on ScreenPanelAdapter construction or similar
new ScreenPanelAdapter(panel, InventoryRegion.TOP_RIGHT);
// HudPanel analog
MKHudPanel.builder(...).region(HudRegion.TOP_LEFT).build();
```

Manual-coordinate positioning via `ScreenOriginFns` remains supported — regions are additive, not replacement. Consumers bypassing regions bypass collision arbitration.

**Relationship to existing primitives:**
- Layers on top of Phase 10's injection patterns (inventory, standalone, HUD) which are about mixin-level integration mechanics. Regions are about positioning within those integrations; they don't conflict.
- Uses existing `ScreenOriginFns` / `HudAnchor` internally to resolve region names to screen-relative coordinates.

**Architectural distinction from M1, M4, M6:**
- M1 = persistence-shaped (per-slot state that survives sessions)
- M4 = integration-shaped (SlotGroup in vanilla menus)
- M5 = layout-shaped (positioning arbitration across consumer panels)
- M6 = rendering-shaped (slot-like primitive for decoration panels)

Four independent mechanism categories; Phase 12 designs each on its own.

**M4 — Vanilla menu slot injection primitive.**
Design a MenuKit primitive for injecting real interactive slots into a vanilla menu post-construction. Slots back a `Storage` (including `PlayerAttachedStorage`), route clicks through vanilla's dispatch (`menu.clicked`), support filters via `mayPlace`, ghost icons via `setBackground`, shift-click routing policy, and empty-click handling.
**Status:** design input. Phase 12 scopes the shape. Single-consumer evidence (IP) is sufficient — the use case is clear and architecturally well-defined.
**Architectural distinction from M1:** M1 is **persistence-shaped** (per-slot state that survives menu transitions and sessions). M4 is **integration-shaped** (making MenuKit's SlotGroup system work inside vanilla menus). Different categories; Phase 12 designs both independently.
**Surfaced by:** IP Layer 2 verification (2026-04-15). Current MenuKit SlotGroup is handler-construction-time only — tied to `MenuKitScreenHandler`. The refactor plan (§ 4.1, § 4.2) described equipment + pockets panels as "Panel with SlotGroup over Storage" placed in `decoration/`, but no mechanism exists to graft a `SlotGroup` onto vanilla's `InventoryMenu` / `CreativeModeInventoryScreen` menu. `VirtualSlotGroup` + `HandlerRecognizerRegistry` **observe** vanilla slots (used by IP's sort / move-matching) but don't **add** new slots.
**Evidence from IP:** F8 (equipment panel), F9 (pockets panels) both need this. The entire attachment-dependent UI surface is blocked.
**Shape hints (not design — that's Phase 12's job):**
- Likely a mixin or builder helper that runs in `InventoryMenu.<init>` RETURN (and creative equivalent) and appends slots via `addSlot(...)`
- Slots should be `MenuKitSlot` subclasses carrying panel/group identity so existing decoration features (sort-lock, move-matching scope resolution) observe them uniformly
- Visibility hookup: hidden panels return empty + `mayPlace=false` (matching current `MenuKitScreenHandler` hidden-panel semantics)
- Rendering: the injected slot positions come from the Panel's origin resolver, so positioning still lives in consumer code
**Non-goals:** M4 is not about making MenuKit "a mixin toolkit" broadly. It's specifically the slot-injection primitive. Other vanilla-menu decoration patterns (render-only overlays, button attachments) already work via `ScreenPanelAdapter`.

---

## Shulker Palette

### Features

**SP-F1 — Peek palette toggle.**
When IP's peek panel shows a shulker box, a palette toggle button should appear on the peek panel. Reads palette state from the peeked item's CUSTOM_DATA. Sends toggle packet with the peeked slot index.
**Category:** sequencing-blocked (depends on IP Layer 3, not on a missing MenuKit primitive).
**Blocked on:** IP Layer 3 (client-side peek: shulker / ender / bundle panels, peek keybind wiring). IP's peek is currently stubs — `ContainerPeekClient.isPeeking()` returns false, `getPeekedSlot()` returns -1.
**Phase 13 sketch:** When IP's Layer 3 ships and peek is functional, rebuild the peek toggle as a Pattern 2 injection on the peek panel. Same approach as the ShulkerBoxScreen toggle (Panel + Button.icon + ScreenPanelAdapter). `ShulkerPalettePeekCompat` was deleted during Phase 11 cleanup — rewrite from scratch against current APIs.
**Note:** This is NOT primitive-blocked. No Phase 12 mechanism needed. It's a sequencing dependency on IP work.

### Per-Item State Finding

The kickoff predicted per-shulker palette state would surface a new mechanism candidate (sibling or extension of M1). **Negative finding: no new mechanism needed.**

Shulker-palette's per-item state (palette flag) is self-contained via CUSTOM_DATA on the ItemStack:
- Placed block: `trevorMod$isPalette` field on `ShulkerBoxBlockEntity`, synced to client via DataSlot on `ShulkerBoxMenu`.
- Item: `trevormod_palette` boolean in CUSTOM_DATA component, readable anywhere the ItemStack is accessible.
- Peeked item: when IP's peek shows a shulker, the ItemStack is in a real slot in `containerMenu` — CUSTOM_DATA is accessible.

M1 (unified per-slot state) stays scoped to per-slot concerns. Per-item state stored on the item itself doesn't need a library abstraction.
