# M4 — Vanilla Menu Slot Injection

**Phase 12 mechanism — integration-shaped** (per `Phase 11/POST_PHASE_11.md`).

**Status: Approved. F8/F9 implementation-ready; F15 option (a) approved for Phase 13.**

**Enables:** F8 (equipment panel), F9 (pockets panels), F15 (peek panel UI — newly confirmed as M4 use case after M6 dissolution).

---

## Purpose

MenuKit consumers need real interactive slots inside vanilla menus — slots that participate in vanilla's full slot protocol (drag, shift-click routing, `mayPlace` enforcement, cursor management, `broadcastChanges` sync). Current MenuKit's `SlotGroup` system is handler-construction-time only, tied to `MenuKitScreenHandler`. No mechanism exists to graft slots onto vanilla handlers (`InventoryMenu`, `ChestMenu`, `ShulkerBoxMenu`, etc.).

M4 provides construction-time slot injection: a library utility that consumers call from their handler-construction mixins to add `MenuKitSlot` instances to a vanilla handler via `addSlot()`. The existing `Panel` visibility + `MenuKitSlot` inertness infrastructure handles hidden/shown lifecycle. The existing `SlotGroup` model handles storage binding, interaction policy, and shift-click participation.

---

## Consumer evidence

**F8 — equipment panel** (IP). Two slots (elytra + totem) in `InventoryScreen` + `CreativeModeInventoryScreen`, backed by `PlayerAttachedStorage.forEquipment()`. Per-slot filters (`isElytra || isTotem`), ghost icons, max stack size 1. Permanent for the screen session.

**F9 — pockets panels** (IP). Nine 3-slot panels (one per hotbar slot) backed by `PlayerAttachedStorage.forPocketSlice(hotbarSlot)`. Per-slot disabled toggling via `POCKET_DISABLED` attachment. Permanent for the screen session.

**F15 — peek panel UI** (IP, newly confirmed after M6 dissolution). Up to 64 slots (max bundle capacity; 27 for shulker/ender) backed by a `SimpleContainer` populated from peek packets. Temporary: grafted when peek opens, inert when closed. M6 POC verification showed that peek slots must participate in vanilla's full slot protocol — drag, bidirectional shift-click, native cursor management.

---

## Research findings: vanilla slot internals

Detailed analysis in a prior research pass. Summary of load-bearing facts:

**Three parallel lists must stay synchronized.** `AbstractContainerMenu` holds `slots` (the `Slot` instances), `lastSlots` (previous-tick ItemStacks for change detection), and `remoteSlots` (sync-protocol state). Only `addSlot()` updates all three. Direct `.add()` to `slots` alone desynchronizes and crashes.

**`addSlot()` is safe at construction time.** It appends to all three lists, assigns `slot.index = slots.size()`, and returns the slot. Called from handler constructors by vanilla. Calling it from a `<init>` RETURN mixin is architecturally supported.

**Post-construction dynamic add/remove is broken.** Removing slots from the middle doesn't re-index. `broadcastChanges()` iterates by position with no bounds safety. Client-server slot-count mismatch causes crashes.

**Shift-click routing is hardcoded per handler.** Each handler's `quickMoveStack` override uses literal index ranges (`moveItemStackTo(stack, 9, 45, true)`). Grafted slots appended at the end are outside these ranges. Must be extended via mixin.

**Client-server symmetry is required.** Both sides must construct the same slots in the same order. For construction-time grafting this holds naturally (both sides run the same handler constructor + mixin).

---

## Section 1: F8/F9 — Construction-time permanent grafting

This section is designed to be implementation-ready after review.

### Architecture

A mixin on `InventoryMenu.<init>` RETURN calls a library-provided helper to graft `MenuKitSlot` instances onto the handler. The helper:

1. Creates a `Panel` (consumer-provided, typically static) and attaches it to the handler via a tracking map.
2. For each `SlotGroup` in the consumer's declaration, calls `addSlot(new MenuKitSlot(...))` for each slot in the group. This updates all three parallel lists correctly.
3. Records the grafted slot index range for shift-click routing.

Both client and server construct `InventoryMenu` with the same mixin → same slots in the same order → symmetry holds.

### Key design insight — reuse existing infrastructure

MenuKit already has the full slot-management stack:

- **`MenuKitSlot`** — vanilla `Slot` subclass with `panelId`, `groupId`, `localIndex`, and delegation to `SlotGroup`. Inertness via `Panel` visibility: when the panel is hidden, `getItem()` returns EMPTY, `mayPlace()` returns false, `isActive()` returns false. Vanilla's renderer and interaction code sees these as legitimately empty inactive slots.
- **`SlotGroup`** — composable: `Storage` + `InteractionPolicy` + `QuickMoveParticipation` + `shiftClickPriority`. Already has `setFlatIndexRange()` for shift-click routing.
- **`Panel`** — visibility via `showWhen(Supplier<Boolean>)`. Already supplier-driven for injection contexts.
- **`InteractionPolicy`** — `input(predicate)` for filters, `.withMaxStackSize(...)` for stack limits.
- **`PlayerAttachedStorage`** — `forEquipment(playerRef)` (2 slots), `forPocketSlice(playerRef, hotbarSlot)` (3 slots each).

M4 extends this stack to vanilla handlers. The extension is the mixin + grafting utility, not new slot/group/panel infrastructure.

### Slot layout on InventoryMenu

Vanilla `InventoryMenu` slot indices (1.21.11):

| Range | Content |
|-------|---------|
| 0 | Crafting result |
| 1–4 | Crafting grid |
| 5–8 | Armor (head, chest, legs, feet) |
| 9–35 | Main inventory |
| 36–44 | Hotbar |
| 45 | Offhand |

Total: 46 vanilla slots (indices 0–45).

Grafted slots append at the end:

| Range | Content | Storage |
|-------|---------|---------|
| 46–47 | Equipment (elytra, totem) | `PlayerAttachedStorage.forEquipment()` |
| 48–74 | Pockets (9 × 3 slots) | `PlayerAttachedStorage.forPocketSlice(0..8)` |

Total with grafts: 75 slots (indices 0–74). Both client and server construct identically.

### quickMoveStack extension

Vanilla's `InventoryMenu.quickMoveStack` uses hardcoded ranges (0, 5–8, 9–44, 45). Grafted slots at 46–74 are outside all ranges — shift-click from/to them does nothing by default.

**Mixin approach:** `@Inject` at HEAD of `InventoryMenu.quickMoveStack`, cancellable. If the clicked slot index is in the grafted range, handle routing explicitly and cancel vanilla's handler. If not, fall through to vanilla.

**Routing rules for F8/F9:**
- Shift-click FROM equipment slot (46–47) → route to main inventory (9–35), overflow to hotbar (36–44). Same as vanilla armor shift-click pattern.
- Shift-click FROM pocket slot (48–74) → route to main inventory (9–35), overflow to hotbar (36–44).
- Shift-click FROM main/hotbar INTO equipment → only if item matches the slot's filter (elytra/totem). This requires extending the vanilla-direction routing too — when shift-clicking from main inventory, check grafted equipment slots first (before vanilla's armor routing) if the item matches the filter.

**Implementation note:** The `SlotGroup.shiftClickPriority` + `pairedWith` system already handles directional routing for `MenuKitScreenHandler`. M4 can leverage the same model — but for vanilla handlers, the routing mixin implements the priority logic directly rather than delegating to the handler's built-in router.

### Creative mode

In creative mode:
- Client: `containerMenu` = `ItemPickerMenu`; `inventoryMenu` = `InventoryMenu`
- Server: `containerMenu` = `InventoryMenu`

The `InventoryMenu` constructor runs on both sides (it's the player's persistent inventory handler). The mixin grafts equipment/pockets slots onto `InventoryMenu` on both sides — symmetry holds.

Screen-level rendering (where to draw the grafted slots visually) is handled by the existing three-mixin injection surface (`InventoryContainerMixin`, `RecipeBookMixin`, `CreativeInventoryMixin`) using `ScreenPanelAdapter`. The adapter's `ScreenOriginFn` positions the panels; the grafted `MenuKitSlot` instances' `x/y` coordinates determine where vanilla renders each slot.

Canonical-slot routing (from Phase 11): operations that touch grafted slots route through `player.inventoryMenu` (which is `InventoryMenu` on both sides), not `screen.menu` (which may be `ItemPickerMenu` on the client in creative).

### Visibility and inertness

Equipment panel: `Panel.showWhen(() -> config.enableEquipment)`. When hidden → MenuKitSlots are inert (getItem=EMPTY, mayPlace=false, isActive=false). Vanilla renders nothing; shift-click skips them; `broadcastChanges` syncs EMPTY.

Pockets panels: nine separate `Panel` instances, each with `showWhen(() -> isPocketOpen(hotbarSlot))`. Individual pocket panels open/close independently. Inactive pockets are inert.

Per-slot disable within pockets: `InteractionPolicy.input(stack -> !PocketDisabledData.isDisabled(hotbarSlot, pocketIndex))`. When a pocket slot is disabled, the filter rejects insertion; ghost icon shows BARRIER.

### SlotRendering

Grafted slots live outside the vanilla container texture — they need standalone backgrounds. The `SlotRendering` utility designed during M6 applies:

- `drawSlotBackground` — vanilla-accurate 18×18 slot visual (delegates to `PanelRendering.renderSlotBackground`)
- `drawHoverHighlight` — translucent white overlay on hover
- `drawGhostIcon` — dimmed sprite for empty filtered slots (elytra placeholder, totem placeholder)
- `drawItem` — standard item + decorations rendering

These are factored during M4 implementation as static methods on a `SlotRendering` utility class (parallel to `PanelRendering`).

### Verification plan for F8/F9

1. Equipment: two slots visible in survival inventory, elytra in slot 0, totem in slot 1. Ghost icons when empty. Filter rejects non-elytra/non-totem items. Shift-click from equipment → main inventory. Config disable → slots vanish (inert).
2. Pockets: toggle open via hotbar button. Three slots per pocket. Items persist across menu close/reopen (Fabric attachment-backed). Shift-click from pocket → main inventory.
3. Creative: same slots visible in Survival Inventory tab. Canonical-slot routing works.
4. Cross-mod: sort-lock applies to grafted slots (SlotIdentity-keyed). Move-matching scopes include/exclude grafted regions.

---

## Section 2: F15 — Peek slot integration (option (a) approved)

All four options analyzed. Trevor's dynamic pre-allocation refinement applies to option (a).

### Option (a): Dynamic pre-allocation at screen open (primary candidate)

**Concept:** At handler construction time, scan the container's contents for peekable items (shulker, bundle, ender chest). If any exist, pre-allocate a hidden 64-slot peek region via `addSlot()`. Both client and server see the same container contents at construction → same peekable count → same slot allocation → symmetry holds.

**Trevor's refinement:** The extra slots aren't fixed-N on every handler. They're dynamic based on peekable count:
- Zero peekable items → zero extra slots (most containers — no cost)
- One or more peekable items → 64 extra slots (max bundle capacity; covers shulker/ender at 27 with remaining slots disabled). Only one peek at a time, so one region covers all peekable types.

**Why 64, not 27:** The Phase 11 refactor plan (§ 4.4) speced bundles at up to 64 slots. Pre-allocating 27 would truncate large bundle displays. Pre-allocating 64 covers all types; slots beyond effective capacity (27 for shulker/ender, variable for bundle) are disabled via Panel inertness. The cost of 64 entries in the parallel lists is negligible. This is an F15 implementation decision, not an M4 design concern — `SlotInjector` doesn't care about count.

**Lifecycle:**

1. **Construction (both sides):** Mixin on `AbstractContainerMenu.<init>` RETURN (or per-handler mixin). Scan `container.getItem(i)` for each slot. If any `ContainerPeek.isPeekable(stack)`, allocate 64 MenuKitSlots backed by a `SimpleContainer(64)`. Panel starts hidden → slots are inert.
2. **Peek open (server):** Server populates the SimpleContainer with the peeked item's contents. `broadcastChanges()` syncs the items to the client. Server tracks which slot is being peeked (no longer stateless — see below).
3. **Client rendering:** Panel becomes visible → slots are active → vanilla renders them. `ScreenPanelAdapter` positions the panel. `SlotRendering` provides slot backgrounds.
4. **Peek close (server):** Server clears the SimpleContainer. `broadcastChanges()` syncs EMPTY. Panel hides → slots go inert.

**quickMoveStack conditional routing:**
- When peek is active: shift-click from peek slots → player inventory (same routing as shulker/chest). Shift-click from player inventory → peek slots (if item matches — equivalent to shift-clicking into a chest).
- When peek is inactive: grafted slots are inert → `mayPlace` returns false, `getItem` returns EMPTY → shift-click naturally skips them.

**Server statefulness:** Phase 11's peek server was stateless (re-resolves peekable per packet). Pre-allocated slots require the server to track which peek session is active (which slot is peeked, which SimpleContainer backs it). This is a design change from Phase 11's stateless peek model.

Impact: moderate. Server needs a per-player `PeekSession` tracking field. Disconnect cleanup clears it. The six existing packet types may need adjustment (server now writes to SimpleContainer + broadcastChanges instead of sending PeekSyncS2CPayload directly).

**Mid-session peekable changes:** A shulker placed into a chest AFTER the menu opened won't be peekable until the menu reopens (can't add slots post-construction). This is acceptable — containers don't change peekable-ness frequently, and "reopen to see new peek targets" is an intuitive constraint.

**Disconnect cleanup:** Server clears the peek session on player disconnect. SimpleContainer is per-handler-instance, so it's garbage-collected with the handler.

**Cost analysis:**
- Architectural: moderate (server statefulness change, per-handler mixin)
- UX: good (vanilla-native feel, full slot protocol)
- Phase 11 protocol impact: moderate (PeekSyncS2CPayload may be replaced by broadcastChanges for grid items; PeekMoveC2SPayload may be replaced by vanilla slotClicked)
- Cross-mod compatibility: good (slots are standard MenuKitSlots; other mods' Slot mixins compose naturally)

### Option (b): Fixed count on every handler

Same as (a) but always allocates 64 peek slots, regardless of container contents.

**Pro:** Simpler construction logic (no peekable scan). **Con:** Every handler in the game gets 27 extra slots — three extra NonNullList entries per slot × every open menu. Wasteful for the vast majority of containers that have no peekable items.

**Why (a) is preferred:** Trevor's dynamic refinement scopes the allocation to containers that actually need it. The construction-time scan is cheap (`ContainerPeek.isPeekable` is a few `instanceof` checks per slot). The savings are large (most containers: zero extra slots).

### Option (c): Peek overlay menu — separate from containerMenu

Open a second `AbstractContainerMenu` alongside the active one. The peek panel lives in the overlay menu with its own slot list and sync protocol.

**Problems:**
- Vanilla supports one `containerMenu` at a time per player. Opening a second replaces the first.
- Reimplements the full slot-sync lifecycle for a temporary overlay.
- Two menus means two separate slot-click dispatch paths — confusing interaction model.
- Doesn't integrate with the parent screen's rendering (peek should appear as a panel within the inventory screen, not a separate screen).

**Verdict:** Rejected. The single-menu-at-a-time constraint is architectural, not a workaround-able limitation.

### Option (d): Handler replacement — close + reopen with peek slots

When peek opens, server closes the current handler and opens a new one that includes the original slots plus 27 peek slots. Full vanilla protocol — the new handler is a first-class menu.

**Problems:**
- UX regression: the screen closes and reopens visibly. For chest/shulker containers, this means a brief flicker + loss of cursor position + scroll state reset.
- Closing the underlying container handler may trigger vanilla side effects (chest close animation, block entity unlock).
- The "replacement handler" must replicate the original handler's slot layout exactly plus append peek slots — fragile for arbitrary container types.
- Each peek open/close is a full handler round-trip (client receives open-screen packet, constructs new screen, etc.).

**Verdict:** Rejected. UX cost is too high for a "quick peek" interaction.

### Recommendation

**Option (a)** with Trevor's dynamic-count refinement. The pre-allocation approach is architecturally sound (works within vanilla's static-slot-layout assumption), the dynamic count keeps cost near zero for non-peekable containers, and the existing MenuKitSlot + Panel inertness infrastructure handles the hidden/shown lifecycle with no new mechanism.

The server-statefulness change is the main cost. Phase 11's stateless peek was a deliberate choice, and this reverses it. The justification: stateless peek with client-side-only slots (M6) was tested and dissolved. The alternative that works (real slots in containerMenu) inherently requires server-side session tracking. The statefulness is earned by necessity, not added speculatively.

---

## Library vs consumer boundary

### What belongs in MenuKit (M4 library primitive)

**Slot grafting utility:** A helper that consumers call from their handler-construction mixin. Takes a handler, a Panel, SlotGroup declarations, and screen-position coordinates. Calls `addSlot(new MenuKitSlot(...))` for each slot, synchronizing all three parallel lists correctly. Records the grafted slot range. Returns a `GraftedRegion` handle for quickMoveStack routing.

**quickMoveStack routing helper:** A utility method that the consumer's quickMoveStack mixin calls. Given a slot index and a `GraftedRegion`, determines whether the slot is in a grafted range and routes accordingly (using `SlotGroup.shiftClickPriority` + `pairedWith` semantics). Returns whether it handled the shift-click (so the mixin can cancel or fall through to vanilla).

**SlotRendering:** Static utility for slot-background, hover-highlight, ghost-icon, and item rendering. Used by any consumer rendering grafted slots outside the container texture.

**Rationale:** These are general capabilities. Any consumer that wants to add slots to a vanilla handler (equipment, pockets, peek, future mods) uses the same grafting + routing pattern. The mechanism is reusable; the specific slots are consumer-owned.

### What belongs in the consumer (IP-owned)

**Handler mixins:** Per-handler-class mixins (`InventoryMenuMixin`, `ChestMenuMixin`, etc.) that call the library's grafting utility at `<init>` RETURN. These are compile-time declarations that name specific handlers — the library can't provide them generically.

**quickMoveStack mixins:** Per-handler-class mixins that intercept `quickMoveStack` and delegate to the library's routing helper. Same reason — handler-specific.

**Peek lifecycle management:** The decision of when to show/hide peek panels, how to populate the SimpleContainer, and the statefulness protocol. This is IP-specific.

**F8/F9 panel declarations:** Which slots to graft, which Storage to back them, which InteractionPolicy to apply, which ghost icons to show. Consumer-specific.

**Dynamic peekable scan:** The logic that counts peekable items at construction time to determine whether to allocate peek slots. IP-specific (other consumers would have different allocation criteria).

### Summary

MenuKit provides the **mechanism** (graft slots, route shift-clicks, render standalone slots). IP provides the **policy** (which handlers, which slots, which lifecycle, which interaction rules). Library-not-platform discipline: the library ships general building blocks; consumers compose them.

---

## Integration points with existing MenuKit

| Artifact | Role in M4 |
|----------|------------|
| `MenuKitSlot` | Grafted slot instances. Carries panel/group identity, delegates behavior to SlotGroup, inertness via Panel visibility. No changes needed — used as-is. |
| `SlotGroup` | Behavioral composition for grafted slot groups. Storage + InteractionPolicy + QuickMoveParticipation. No changes needed. |
| `Panel` | Visibility management for grafted panels. `showWhen()` for supplier-driven show/hide. No changes needed. |
| `InteractionPolicy` | Per-slot filters for equipment (elytra/totem predicate), pockets (disabled-slot gating), peek (free). No changes. |
| `Storage` | Backing for grafted slots. `PlayerAttachedStorage` for F8/F9; `SimpleContainer`-based `Storage` adapter for F15. May need a new `SimpleContainerStorage` adapter in MenuKit. |
| `PanelRendering` | `renderSlotBackground()` called by `SlotRendering` for vanilla-accurate slot visuals. No changes. |
| `HandlerRecognizerRegistry` / `VirtualSlotGroup` | Existing "observe vanilla slots" system. M4's grafted slots should be recognizable by the recognizer (they're real Slot instances in `containerMenu.slots`). May need registry update to classify grafted regions. |

**New MenuKit artifacts (M4 ships these):**
- `SlotInjector` (or similar) — the grafting utility. Lives in a new `inject/` or `core/` location.
- `GraftedRegion` — handle returned by the injector. Carries slot index range for quickMoveStack routing.
- `SlotRendering` — static utility for slot visuals (carried forward from M6 analysis).
- `SimpleContainerStorage` — `Storage` adapter wrapping a `SimpleContainer` for F15's ephemeral backing. Or consumers construct their own adapter (it's ~20 lines).

---

## Scope boundary — what M4 does not do

- **Not a mixin toolkit.** M4 provides the grafting utility and routing helpers. Consumers write the actual mixins (handler-specific compile-time declarations).
- **Not a full handler replacement.** M4 grafts slots onto existing handlers; it doesn't replace or wrap them.
- **Not post-construction dynamic.** Slots are grafted at handler construction only. Mid-session add/remove is architecturally unsupported by vanilla's slot protocol.
- **No drag-and-drop customization.** Vanilla's drag protocol (hold-click + sweep across slots) works automatically for grafted MenuKitSlots — they're real Slot instances in `containerMenu.slots`. M4 doesn't customize drag behavior.
- **No per-mod slot-index reservation.** Grafted slots get the next available indices after vanilla's slots. If multiple mods graft onto the same handler, their indices depend on mixin load order. M4 doesn't mediate inter-mod slot-index conflicts (low risk — few mods graft slots onto vanilla handlers).

---

## Phase 13 handoff

**F8 + F9 (equipment + pockets UI):** When M4 ships, IP builds `InventoryMenuMixin` that calls `SlotInjector.graft(...)` at `<init>` RETURN for equipment (2 slots) + pockets (27 slots). Screen-level rendering via `ScreenPanelAdapter`. Full shift-click routing. Config-driven visibility.

**F15 (peek panel UI):** IP builds per-handler-type mixins for peek slot pre-allocation. Server-side statefulness added to `ContainerPeek`. SimpleContainer populated on peek open; `broadcastChanges` syncs. Panel visibility driven by peek session state.

**Packet protocol simplification (Phase 13 observation, not M4 scope).** If peek slots are real vanilla Slot instances backed by a SimpleContainer, vanilla's `slotClicked` dispatch handles mutations and `broadcastChanges` handles sync. The Phase 11 six-packet protocol partially dissolves:
- **Survives:** `PeekOpenC2S` (populate SimpleContainer), `PeekCloseC2S` (clear it), `PeekErrorS2C` (server rejection).
- **May simplify:** `PeekOpenS2C` (server populates SimpleContainer → `broadcastChanges` syncs items; explicit item-list payload may be unnecessary).
- **May dissolve:** `PeekMoveC2SPayload` (vanilla `slotClicked` handles mutations), `PeekSyncS2CPayload` (vanilla `broadcastChanges` handles sync).

Naming this now prevents Phase 13 surprise. The exact simplification depends on how vanilla's slot-click dispatch integrates with the pre-allocated SimpleContainer.

**SP-F1 (shulker-palette peek toggle):** Transitively unblocked when F15 ships. Same cross-mod integration surface as before.

---

## Implementation flags

- **`addSlot(Slot)` is `protected` on `AbstractContainerMenu`.** Accessible from a mixin that extends `AbstractContainerMenu` in its class declaration.
- **`InventoryMenu` constructor signature.** Verify exact 1.21.11 parameter list for the mixin's `@Inject` target. Likely `(Inventory, boolean, Player)` but may differ in mapped names.
- **`quickMoveStack` method name.** Verify the 1.21.11 mapped name — may be `quickMoveStack` or a remapped equivalent.
- **`Slot.x` and `Slot.y` positioning.** Noted as `public final` in vanilla — **verify whether truly `final` in 1.21.11 mapped sources.** For F8/F9 this is not a concern (fixed positions known at construction time). For F15, peek panel position depends on runtime state (which item is peeked, which screen is open). If `Slot.x`/`Slot.y` are mutable, update them at peek-open time. If truly final, peek panel renders at a fixed position relative to the screen (set at construction). Vanilla's `AbstractContainerScreen.findSlot()` uses `Slot.x`/`Slot.y` for click hit-testing — rendered position must match or clicks won't land. **Implementation-time verification item.**
- **`Slot.isActive()` override in `MenuKitSlot`.** Returns false when panel is hidden. Vanilla's `AbstractContainerScreen` skips rendering and hover-detection for inactive slots. This is the existing inertness mechanism.
- **`SimpleContainer` for peek.** Standard vanilla class — `new SimpleContainer(27)` creates a 27-slot ephemeral container. Implements `Container`. Can be wrapped in a `Storage` adapter for `SlotGroup` consumption.
- **`broadcastChanges()` for peek sync.** When server modifies the SimpleContainer (peek open/move/close), calling `broadcastChanges()` on the handler syncs the changed slots to the client via vanilla's slot-sync protocol. This replaces PeekSyncS2CPayload for grid items.

---

## Summary

**Primitive:** Construction-time slot injection via `addSlot()` in handler-construction mixins. `MenuKitSlot` + `SlotGroup` + `Panel` inertness infrastructure handles behavior, interaction policy, and visibility — no new slot mechanism needed. `SlotRendering` utility handles standalone visuals.

**Two consumer paths:**
- **F8/F9 (permanent, settled):** Mixin on `InventoryMenu.<init>` RETURN. Equipment (2 slots) + pockets (27 slots) backed by `PlayerAttachedStorage`. `quickMoveStack` extended via mixin. Implementation-ready after review.
- **F15 (temporary, decision needed):** Dynamic pre-allocation at construction based on peekable count (option a, recommended). Server statefulness required. Four options analyzed; (a) is preferred.

**Library ships:** `SlotInjector` (grafting utility), `GraftedRegion` (index range handle), `SlotRendering` (visual utility), quickMoveStack routing helper. **Consumer ships:** handler mixins, panel declarations, lifecycle management, peek protocol adjustments.

**Additive to MenuKit.** Existing `MenuKitSlot`, `SlotGroup`, `Panel`, `InteractionPolicy`, `Storage` used as-is. No breaking changes. Existing consumers continue working unchanged.

**Status: Approved.** F8/F9 section is implementation-ready; F15 option (a) approved for Phase 13 implementation against M4's shipped primitive.
