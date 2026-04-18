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
**Category:** primitive-blocked (visual layer pending M5 region system).
**Blocked on:** M5 (region system for panel positioning). M4 mechanism (slot grafting via `addSlot()`) is **verified working** in Phase 12 — equipment slots graft onto InventoryMenu at construction, accept elytra/totem via `mayPlace`, enforce max stack 1, shift-click routes correctly, persistence works via Fabric attachments. What remains is the visual layer (slot backgrounds, ghost icons, panel frame) which should go through Panels + ScreenPanelAdapter positioned via M5 regions.
**Phase 13 sketch:** Equipment slot grafting is already implemented (IP's `InventoryMenuMixin`). Phase 13 adds the visual backdrop Panel positioned via M5 region (likely `InventoryRegion.LEFT_ALIGN_TOP`). Ghost icons via vanilla's `Slot.getNoItemIcon()` extension point. Config-driven visibility via `Slot.isActive()` override.

**F9 — Pockets panels.**
Nine `Toggle.linked` buttons (one per hotbar slot, RAISED/INSET) + nine 3-slot pocket panels backed by the `POCKETS` attachment, with `POCKET_DISABLED` integration for per-slot disable-toggling via empty-click.
**Category:** primitive-blocked (visual layer pending M5 region system).
**Blocked on:** M5 (region system for panel positioning). M4 mechanism (slot grafting) applies here the same way as F8 — pockets slots graft onto InventoryMenu at construction time, backed by `PlayerAttachedStorage.forPocketSlice(hotbarSlot)`. The grafting mechanism is verified; the visual layer is what remains.
**Phase 13 sketch:** Graft 27 pocket slots (9 × 3) onto InventoryMenu via the same `addSlot()` pattern as F8. Per-slot `mayPlace` for disabled-slot gating. Panel visibility via `Slot.isActive()` returning false when pocket is closed (`openPocketIndex != hotbarSlot`). Nine `Toggle.linked` buttons at hotbar positions. Visual backdrop Panel positioned via M5 region (likely `InventoryRegion.BOTTOM_ALIGN_LEFT` or similar).

**F10 — In-inventory pocket HUD toggle button.**
If eventually desired: a small button in the inventory screen that toggles the pocket HUD's visibility. Not currently in the refactor plan — speculative placeholder if users request it. HUD itself (Layer 2 shipped) exists independently of this.
**Category:** feature-addition.
**Phase 13 sketch:** Small `Toggle.linked` button in the settings region, reading/writing `config.showPocketHud`.

**F15 — Peek panel UI (umbrella — subsumes F11–F14).**
The visible peek panel and all user-facing peek behavior: rendering, click dispatch, sort-within-peek, move-matching-into-peek, drop, double-click-collect. These defer together because they all depend on the same slot injection mechanism.
**Category:** primitive-blocked.
**Blocked on:** M4 (vanilla menu slot injection — option (a) approved: dynamic pre-allocation of peek slots at handler construction based on peekable item count). M6 dissolved in Phase 12; peek needs real vanilla Slot instances, not client-side decoration slots.
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

**Phase 13 sketch:** Uses M4's slot-injection mechanism (option a: dynamic pre-allocation). Per-handler-type mixins scan container contents at construction for peekable items; if any found, graft 64 hidden peek slots (max bundle capacity) backed by a `SimpleContainer`. Server-side statefulness: `PeekSession` tracks which slot is peeked, populates the SimpleContainer on peek-open, clears on peek-close. `broadcastChanges()` handles sync automatically. Panel visibility via `Slot.isActive()` + Panel.showWhen. Peek panel visual backdrop via M5 region. Phase 11's six-packet protocol may partially simplify — `PeekMoveC2SPayload` dissolves (vanilla's `slotClicked` handles mutations); `PeekSyncS2CPayload` dissolves (`broadcastChanges` handles sync). Remaining: `PeekOpenC2S`, `PeekCloseC2S`, `PeekErrorS2C`.

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
**Status:** dissolved in Phase 12. Verification showed peek requires vanilla-native slot instances via M4 (full drag / shift-click / cursor protocol), not client-side decoration slots. No other consumer evidence exists for a client-side slot primitive without peek. Rendering analysis (SlotRendering utility) carries forward to M4.
**Dissolution record:** `Design Docs/Phase 12/M6_CLIENT_SIDE_SLOTS.md` — preserved as historical record with the verification finding and carryforward analysis.

**M5 — Context-scoped region system for panel positioning.**
Design a primitive for mods to declare panel positions by named region rather than pixel coordinates, scoped per-context. Collision arbitration via stacking along region-defined flow axes. Each region is an anchor + flow direction pair; panels attached to the same region stack with gaps along the flow axis.
**Status:** region specs finalized. See `Design Docs/Phase 12/M5_REGION_SPECS.md` for the authoritative region definitions. Implementation pending in Phase 12.
**Region sets (finalized by Trevor):**
- **InventoryContext** — 8 regions using `SIDE_ALIGN_END` naming: `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`. Anchored to the vanilla menu frame; track menu position as it shifts.
- **HudContext** — 9 regions using position naming: `TOP_LEFT`, `TOP_RIGHT`, `TOP_CENTER`, `LEFT_CENTER`, `RIGHT_CENTER`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_CENTER`, `CENTER` (below crosshair, stacks down). Anchored to screen edges.
- **StandaloneScreenContext** — 8 regions matching InventoryContext naming, anchored to the main panel.
**V1 scope:** registration-order stacking, 2px default gap, cutoff on overflow. No priority, no user override.

**Full specs:** `Design Docs/Phase 12/M5_REGION_SPECS.md` — contains region definitions, naming conventions, visual diagrams, flow directions, implementation notes (enum shape, API shape suggestions), and consumer mapping.

**Evidence:** Sandboxes manually offset "13px left of IP's settings gear" to avoid collision. Shulker-palette's ShulkerBoxScreen toggle picks fixed coordinates with no collision awareness. As additional consumers want inventory-screen space, systematic arbitration compounds.

**M4 — Vanilla menu slot injection primitive.**
Design a MenuKit primitive for injecting real interactive slots into a vanilla menu at construction time via `addSlot()`.
**Status:** mechanism confirmed working in Phase 12. Core grafting (`addSlot` at `InventoryMenu.<init>` RETURN), `hasClickedOutside` mixin fix, shift-click routing via `quickMoveStack` mixin — all verified. Visual layer (slot backgrounds, ghost icons via Panel + ScreenPanelAdapter) pending M5 region system. Library surface: `StorageContainerAdapter` + `MKHasClickedOutsideMixin` + `SlotRendering` ship; `SlotInjector` / `GraftedRegion` likely dissolve (consumer calls `addSlot` directly).
**Key Phase 12 findings:**
- Vanilla's Slot extension points (`mayPlace`, `getNoItemIcon`, `getMaxStackSize`, `isActive`) are sufficient. No MenuKitSlot data-flow overrides needed — plain Slot subclasses with 2-3 overrides work.
- `hasClickedOutside` misclassifies clicks on slots outside the container frame (overwrites valid slot index to -999, changing PICKUP to THROW). Fix: mixin returns false when click lands on any active slot.
- Two-layer model: handler layer (real vanilla Slots via addSlot) + visual layer (Panels via ScreenPanelAdapter). They share coordinates but aren't coupled.
- F15 (peek) uses the same mechanism via option (a): dynamic pre-allocation at construction based on peekable item count. 64 hidden slots (max bundle capacity), zero-cost for non-peekable containers.
**Design doc:** `Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md` (partially stale — see `Phase 12/SESSION_STATUS.md` §8 for what changed during implementation).

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
