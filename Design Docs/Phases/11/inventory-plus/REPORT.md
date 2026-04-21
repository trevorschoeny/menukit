# Phase 11 IP — Report

Close-out for inventory-plus under the Phase 11 / 12 / 13 arc reframing (2026-04-15). Phase 11's gate is *"in-scope features work + deferred features documented + primitive list captures all surfaced needs,"* not full parity with pre-Phase-5 IP.

**Status: COMPLETE.** All five layers shipped (Layers 0a, 0, 1, 2, 3, 4, 5). This report stands alone — someone picking up IP in Phase 13 should be able to read just this document and the linked entries in `POST_PHASE_11.md` / `PUBLIC_API.md` / `COMMON_FRICTIONS.md` to understand what shipped, what didn't, and why.

---

## Arc context

The original Phase 11 plan was *"rebuild IP fully against current MenuKit."* Mid-phase this split into three:

- **Phase 11** (this report) — rebuild as far as current MenuKit allows. When a feature needs a primitive that doesn't exist, defer the feature and file the primitive.
- **Phase 12** — design and ship MenuKit primitives using evidence accumulated across all consumer mods.
- **Phase 13** — complete deferred consumer-mod features against Phase 12's primitives.

The **exception clause** that authorized `SlotIdentity` as a mid-phase library primitive is closed. One MenuKit primitive shipped in Phase 11 (`SlotIdentity`); everything else defers. IP's remaining gaps are captured in `POST_PHASE_11.md` as feature (`F*`) and mechanism (`M*`) entries.

---

## Layer-by-layer summary

### Layer 0a — clean baseline

Pre-refactor cleanup that established IP compiles against current MenuKit with all feature code stubbed. Removed ~100 old-arch import errors (`MKRegion*`, `MKContainer*`, `MKEvent*`, `MKPanel`, `MKButton*`, `MKSlotState*`, `MKItemTips`, etc.). Deleted 3 dead mixins. Stubbed 14 files at the method level with signatures preserved for in-place re-population. Commit: `6247cd3`.

### Layer 0 — attachment foundations

- **`IPPlayerAttachments`** — three Fabric data attachments (`EQUIPMENT` 2-slot, `POCKETS` 27-slot flat, `POCKET_DISABLED` bitmask), each with persistence codec + sync codec, both registered on both sides. `getOrInit*` wrapper pattern per COMMON_FRICTIONS #1.
- **`PlayerAttachedStorage`** — MenuKit `Storage` adapter factories (`forEquipment`, `forPockets`, `forPocketSlice(hotbarSlot)`). Layer 2 passive behaviors read through these; Phase 13 Layer 2-UI (F8/F9) will bind them to SlotGroups once M4 ships.
- **Dev-only `/ip_attach_probe`** command for round-trip verification (write → save → relog → read). Gated on `FabricLoader.isDevelopmentEnvironment()`.

Commit: `932be69`.

### Layer 1 — in-screen keybinds + decorations

Five groups shipped across commits `605641c`, `6b60c7d`, `b004245`, `b7faa4d`, `9294a08`, `74116d9`, `7ed96a7`:

- **Group A — lock keybind + overlay.** `ClientLockStateHolder` + `ServerLockStateHolder`, both keyed by `SlotIdentity` so lock state survives within-session menu transitions. `LockOverlayDecoration` renders a 2px red border on sort-locked slots.
- **Group B1 — sort via click-sequence.** Mid-group **architectural pivot** (commit `b7faa4d`): sort moved from server-side slot mutation to client-composed click sequences. Client computes a permutation via `SortAlgorithm`, sends `ClickSequenceC2SPayload`, server replays each click through vanilla's `menu.clicked(...)`. Benefits: `mayPlace`, quick-move routing, filter, event, and cross-mod slot-click mixins all fire automatically. `IPRegionGroups` sub-partitions vanilla's overbroad `player_inventory` group into synthetic `ip:hotbar` / `ip:main`.
- **Group B2 — shift+double-click bulk-move.** Detection at `mouseClicked` HEAD + suppression of vanilla's paired `mouseReleased` `PICKUP_ALL` dispatch. Second shift-click within 250ms fires a region-scoped `QUICK_MOVE` click sequence. **Sort-lock blocks auto-routing in both directions** — `ServerLockGuard` ThreadLocal + `IPSlotMayPlaceMixin` + `IPSlotGetItemMixin` make locked slots invisible to `moveItemStackTo`. Direct `PICKUP` clicks still work (locks block auto-routing, not hand-placement).
- **Group B3 — move-matching via click sequence.** Destination region is the anchor's; match set is every item type already present in that region. Pulls every source-region stack whose type is in the set. `includeHotbarInMoveMatching` config honoured.
- **Group B5 — settings gear + unified config.** `IPConfigCategory.build()` — YACL category with option groups for Equipment, Sorting, Pockets, Peek. `SettingsGearDecoration` — Pattern 3 panel at top-right of inventory frame. Wired into all three inject mixins (InventoryContainerMixin, RecipeBookMixin, CreativeInventoryMixin) to handle Phase 10 failure modes #1/#2.

Plus the **`SlotIdentity`** primitive shipped to MenuKit mid-phase (commit `8531bd8`) — zero-dependency record `(Container, int containerSlot)` for cross-menu stable slot identity. The sole approved library exception; clause now closed.

### Layer 2 — attachment-dependent features

**Shipped (passive behaviors + server logic + HUD):**

- **Five passive-behavior mixins** populated: `IPCanGlideMixin` (elytra flight from equipment slot 0), `IPTotemMixin` (totem death protection from slot 1 with full effect replication), `IPDeathDropsMixin` (drops equipment + pockets on death, respects `keepInventory`), `IPFallFlyingMixin` (elytra durability swap during flight tick), `IPWingsLayerMixin` (wings rendering from equipment slot via `chestEquipment` swap).
- **`MendingHelper`** rewritten using vanilla's `EnchantmentHelper.modifyDurabilityToRepairFromXp` pattern, with proportional XP accounting matching `ExperienceOrb.repairPlayerItems`.
- **Pocket cycle server handler** — rotates items through `[hotbar_item, pocket[0..2]]` respecting `POCKET_DISABLED` + `config.pocketSlotCount`.
- **Autofill server handler** — reuses existing `AutoFill.execute` (shulker-sourced). Pocket-source autofill deferred with F9 until pockets UI ships.
- **Pocket HUD** — three `ItemDisplay`s at A-shape positions above hotbar, size 10 (pose-matrix scaled), no animation per § 4.3 advisor decision.
- **Keybinds** — autofill (unbound default), pocket-cycle left/right (arrow keys default).

**Deferred (UI primitive gap):** Equipment panel UI (F8), Pockets panels UI (F9) — both blocked on M4 (vanilla menu slot injection primitive). See Path B deferral analysis below.

### Layer 3 — client-side peek (partial ship)

**Shipped (architectural infrastructure):**

- **Six packet types** (`PeekOpenC2S`, `PeekOpenS2C`, `PeekMoveC2S`, `PeekSyncS2C`, `PeekCloseC2S`, `PeekErrorS2C`). Old `PeekC2SPayload` + `PeekS2CPayload` deleted.
- **Stateless server.** `ContainerPeek` re-resolves the peekable per-packet. Source-type branching against vanilla storage: shulker via `DataComponents.CONTAINER`, bundle via `DataComponents.BUNDLE_CONTENTS`, ender via `player.getEnderChestInventory()`.
- **Client session state.** `ContainerPeekClient.PeekSession` holds one active peek at a time. Populated on `PeekOpenS2C`, overwritten on each `PeekSyncS2C`, cleared on `PeekError` or `closePeekClient()`.
- **Cross-mod public API stable** — see `inventory-plus/PUBLIC_API.md`. Five accessors + `PeekSourceType` enum. Shulker-palette's deferred SP-F1 (peek toggle) can reference this surface.
- **Peek keybind registered** in `KeybindDispatch`; consumes key silently so Alt doesn't fall through to vanilla handling.

**Deferred (F15 — peek panel UI):** See Process Finding below.

### Layer 4 — public API narrowing

- `ContainerPeekClient` IP-internal methods (open/close/getSession/registerClientHandler) explicitly marked internal in javadoc; five stable cross-mod methods banner-separated.
- `PeekSourceType` enum added (`SHULKER` / `BUNDLE` / `ENDER_CHEST`) with `wireId()` + `fromWireId(int)` bridging to the integer constants on `PeekOpenS2CPayload`.
- `ContainerPeekClient.getSourceType()` return type changed from `int` to `@Nullable PeekSourceType`.
- Dead `getEffectiveSlots()` removed.
- `PUBLIC_API.md` — new file at IP repo root documenting the five-method stable surface + enum + usage patterns + stability contract.

### Layer 5 — cleanup + final verification

- Dead code removed: `network/BulkMoveC2SPayload.java`, `network/MoveMatchingC2SPayload.java` (both superseded by `ClickSequenceC2SPayload` during B1 pivot), `PocketsPanel.java` (only stubs; no live callers — Phase 13 creates a fresh `PocketsDecoration` per F9).
- Old-arch imports scanned across all modules — zero remaining.
- Mixin config files audited: every entry in `inventory-plus.mixins.json` + `inventory-plus-inject.mixins.json` resolves to an existing class.
- `dev/build.gradle` — inventory-plus enabled (was enabled during Layer 0 verification).
- Init log messages updated to reflect Phase 11 complete.
- This report rewritten as standalone close-out.

---

## Cross-mod public API

Stable surface at `inventory-plus/PUBLIC_API.md`. Summary:

- `ContainerPeekClient.isPeeking()` → `boolean`
- `ContainerPeekClient.getPeekedSlot()` → `int` (−1 when not peeking)
- `ContainerPeekClient.getSourceType()` → `@Nullable PeekSourceType`
- `ContainerPeekClient.getPeekTitle()` → `Component`
- `ContainerPeekClient.getPeekedItemStack()` → `ItemStack` (EMPTY when not peeking)
- `PeekSourceType` enum — `SHULKER` (ordinal 0), `BUNDLE` (1), `ENDER_CHEST` (2)

**Phase 11 note:** `isPeeking()` always returns `false` in Phase 11 because `KeybindDispatch.handlePeek` is stubbed pending F15. Consumer mods can reference the API now — calls start returning real values once F15 ships in Phase 13. The API shape is committed.

Shulker-palette's deferred SP-F1 (peek palette toggle) is the primary intended consumer.

---

## Deferral inventory

### Features (F\*)

Full entries in `POST_PHASE_11.md`. Summarized here with Phase 13 ordering hints.

| Entry | Title | Blocker | Phase 13 ship order |
|-------|-------|---------|---------------------|
| F1 | Persistent player-slot lock state across sessions | M1 | After M1 designs |
| F2 | Chest-slot lock state visible across menu reopens | M1 | With F1 (same backing store) |
| F3 | Full-lock (Ctrl+click) | nothing — feature-addition | Independent; any time |
| F4 | Sort consolidation | nothing — feature-complexity | Independent; any time |
| F5 | Creative-mode sort | nothing — feature-scope (creative packet path) | Independent; requires creative-packet-path research |
| F6 | Creative-mode bulk-move | nothing — feature-scope (same as F5) | Likely with F5 (shared research) |
| F7 | Hotbar↔main bulk-move (no container open) | nothing — feature-complexity (vanilla click-protocol edge case) | Independent; may resolve with F3 |
| F8 | Equipment panel UI | M4 | After M4 designs |
| F9 | Pockets panels UI | M4 | With F8 (same primitive) |
| F10 | In-inventory pocket HUD toggle | nothing — speculative | Skip unless requested |
| F15 | Peek panel UI (subsumes F11–F14) | M6 | After M6 designs |

**Related cross-mod deferrals (other consumer mods):**

- **SP-F1** — shulker-palette peek toggle. Sequencing-blocked on F15 (not primitive-blocked). When F15 ships, SP-F1 becomes a straightforward Pattern 2 injection on IP's peek panel.

### Mechanisms (M\*)

Full entries in `POST_PHASE_11.md`. Four independent categories; Phase 12 designs each on its own.

| Entry | Category | Enables | Evidence |
|-------|----------|---------|----------|
| M1 | persistence-shaped — per-slot state across sessions | F1, F2, future per-slot state needs | IP sort-lock; no other consumer surfaced per-slot needs |
| M2 | *shipped* — SlotIdentity | Foundation; M1 builds on it | — |
| M3 | cleanup — MKFamily removal (or keep-as-is) | Phase 13 consumer config refactors IF removed | IP + all four consumer mods use family config; decision pending |
| M4 | integration-shaped — vanilla menu slot injection | F8, F9 | IP equipment + pockets panels |
| M5 | layout-shaped — context-scoped region positioning | Collision-free multi-consumer layout | Sandboxes ↔ IP settings-gear collision (manual offset); shulker-palette toggle at fixed coords |
| M6 | rendering-shaped — client-side slot primitive for decoration panels | F15 (peek panel UI) | IP peek (hand-rolled attempt reverted) |

---

## Frictions captured

See `COMMON_FRICTIONS.md`. IP surfaced 11 entries:

**Fabric attachments:**
1. `AttachmentRegistry.Builder.initializer(...)` doesn't auto-populate — pattern: `getOrInit*` wrapper.
2. `AttachmentSyncPredicate.targetOnly()` emits javac deprecation — works; ignore for now.

**1.21.11 vanilla API changes (Layer 1 pass):**
3. `CommandSourceStack.hasPermission(int)` removed — drop permission check in dev-only commands.
4. `keyPressed` / `mouseClicked` take `KeyEvent` / `MouseButtonEvent` records — audit every mixin signature; compile doesn't catch.
5. `Screen.hasShiftDown()` removed — moved to `InputWithModifiers`; outside event contexts, use `InputConstants.isKeyDown`.

**Mixin method resolution (surfaced during shulker-palette Layer 0a):**
6. `@Inject` with explicit descriptor can't target inherited methods on a subclass — target the parent class with `instanceof` gate.

**1.21.11 vanilla API changes (Layer 2 pass):**
7. `HumanoidRenderState.wingsItem` doesn't exist — field is `chestEquipment`.
8. `EnchantmentHelper.hasMending()` doesn't exist — use `modifyDurabilityToRepairFromXp` for combined check + compute.
9. `GameRules.RULE_KEEPINVENTORY` renamed to `KEEP_INVENTORY`.
10. `GameRules.getBoolean()` replaced by generic `get(GameRule<T>)`.
11. `GameRules` moved from `net.minecraft.world.level` to `net.minecraft.world.level.gamerules`.

Each entry in `COMMON_FRICTIONS.md` includes symptom, cause, consumer pattern, and IP reference.

---

## Process finding — F15 discipline failure

A mid-Layer-3 pass shipped a hand-rolled `PeekDecoration` that drew peek slots via raw `graphics.fill()` calls + custom hit-testing. This violated the conforming-to-primitives principle — peek slots should render with vanilla slot sprites + vanilla-slot-click semantics, but that requires a primitive (M6: client-side slot for decoration panels) that doesn't exist in current MenuKit.

Reverted to a stub on advisor review. The architecturally-correct infrastructure (packets + server + session + public API) stays shipped; only the visible UI layer defers to Phase 13.

**Lesson:** when a primitive gap surfaces mid-feature, the right move is to stop, file the mechanism, stub the feature with clear deferred-comments, and move on. Hand-rolling a workaround:

- violates the conforming-to-primitives principle
- ships visually non-conforming code (users notice)
- needs to be thrown away when the primitive lands anyway
- obscures the real gap (makes Phase 12 think the primitive is less urgent)

**Signal:** if you find yourself reaching for raw `graphics.fill()` where a primitive should exist, building custom hit-testing where `Button`/`Slot` semantics should apply, or hand-rolling panel backgrounds where `PanelStyle` should work — that's the signal to stop and file the mechanism. The workaround is slower than the deferral because the deferral is a real deliverable and the workaround is disposable.

Captured as global instruction in `CLAUDE.md` ("Architectural Discipline First, Functionality Second") and in per-project memory.

---

## Phase 12 handoff — mechanism design sequence

Four independent mechanism categories, different design pressures:

**M1 — per-slot state persistence.** Evidence from IP lock state (session-scoped, needs persistence). Other consumer mods didn't surface per-slot needs, so single-consumer evidence. Design-question: what shape does per-slot state take? Typed state (per-feature key-value) or opaque blob? `SlotIdentity`-keyed (already shipped) but persistence scope needs a decision (per-world? per-account?). **Sequencing: any time; lowest-pressure.**

**M4 — vanilla menu slot injection.** Evidence from IP equipment + pockets panels (F8 + F9). Architecturally constrained: vanilla's slot sync protocol treats `menu.slots` as immutable after construction. Primitive must inject slots at menu construction time via a mixin helper, with `MenuKitSlot` subclasses carrying panel/group identity so existing decoration features (sort-lock, move-matching scope resolution) observe them uniformly. **Sequencing: design earlier rather than later** — F8 + F9 are the most user-visible IP deferrals (attachment-dependent UI is invisible without them). Also enables more shulker-palette-style cross-mod integration patterns.

**M5 — context-scoped region positioning.** Evidence from sandboxes ↔ IP settings-gear coordinate collision; shulker-palette toggle at fixed coords. Design choice pending (exact region set per context). Trevor committed to thinking through region sets deliberately rather than Phase 12 auto-filing them. **Sequencing: after Trevor commits the region sets.** Do not begin implementation until that design decision lands.

**M6 — client-side slot primitive for decoration panels.** Evidence from IP peek (F15; hand-rolled attempt reverted). Architecturally distinct from M4 (different integration context — M6 is for decoration-panel render surface, not vanilla-menu integration). Likely sits in `core/` alongside `Button` and `ItemDisplay`. **Sequencing: independent of M1/M4/M5** — peek is the single unambiguous consumer. Can ship before or after M4 without dependency.

**Non-sequenced:** M3 (MKFamily disposition) is a decision, not a design. Can resolve any time; doesn't block any feature.

**Suggested order for Phase 12** if tackling serially: M6 (simplest, clearest use case) → M4 (unblocks the most-visible deferrals F8/F9) → M1 (smallest scope among remaining) → M5 (awaits Trevor's region-set decision).

---

## Phase 13 handoff — feature dependency graph

Features unblock in this dependency order. Independent features ship in any order.

**M1 ships →**
- F1 (persistent lock state)
- F2 (chest-slot lock visibility across reopens) — same backing store as F1

**M4 ships →**
- F8 (equipment panel UI)
- F9 (pockets panels UI) — same primitive pattern as F8

**M6 ships →**
- F15 (peek panel UI) — and transitively SP-F1 (shulker-palette peek toggle) in shulker-palette's Phase 13 work

**M3 decision →**
- No feature unblocked; may trigger consumer-side config refactoring (cosmetic)

**Already-unblocked (no primitive dependency; schedule-permitting):**
- F3 (full-lock Ctrl+click) — feature-addition, click-sequence-friendly
- F4 (sort consolidation) — feature-complexity, sort-only
- F5 + F6 (creative sort + bulk-move) — requires creative-packet-path research; low priority
- F7 (hotbar↔main bulk-move) — may resolve via F3 investigation; otherwise defer
- F10 (pocket HUD toggle button) — speculative; skip unless requested

**Feature groupings that ship together naturally:**
- (F1, F2) — same M1-backed store
- (F8, F9) — same M4-backed SlotGroup pattern
- (F5, F6) — same creative-packet-path research
- (F15 + SP-F1) — IP's peek UI enables shulker-palette's peek toggle

---

## Architectural notes for Phase 13

Preserve these Layer 1–3 architectural decisions when implementing Phase 13 features:

- **Click-sequence architecture holds.** Sort, bulk-move, move-matching all use `ClickSequenceC2SPayload`. Server replays via `menu.clicked`; vanilla semantics fire automatically. Future click-model features (consolidation F4, Ctrl+click full-lock F3) should extend this pattern rather than reimplement mutation paths.
- **Canonical-slot routing via `SlotIdentity`.** Solves creative's two-menu split (ItemPickerMenu vs InventoryMenu) for IP operations. Keep routing through `player.containerMenu` + `IPRegionGroups.canonicalSlot` for any feature that mutates through the click sequence. **Exception for creative-specific features:** in `CreativeModeInventoryScreen`, client's `player.containerMenu` is `ItemPickerMenu` but server's is `InventoryMenu`. Route through `player.inventoryMenu` when addressing inventory slots from creative.
- **Sort-lock is auto-routing-scoped.** Blocks `moveItemStackTo` + `QUICK_MOVE` destinations, not direct `PICKUP`. F3 (full-lock) is a separate primitive with broader semantics; don't merge them.
- **Three-mixin inventory injection surface:**
  - `InventoryContainerMixin` — primary on `AbstractContainerScreen`
  - `RecipeBookMixin` — supplementary on `AbstractRecipeBookScreen`, gated `instanceof InventoryScreen` (Phase 10 failure mode #2 fix)
  - `CreativeInventoryMixin` — supplementary on `CreativeModeInventoryScreen` (Phase 10 failure mode #1 fix)
  Each feature adds its hooks to the same three classes.
- **Passive-behavior mixins use `getOrInit*`, not `get*`.** Hard contract per COMMON_FRICTIONS #1. Any caller that must not skip on first access (which is all passive-behavior mixins) uses the init-on-read wrapper.
- **Peek server handlers branch on `sourceType` directly.** No `PeekItemSource` abstraction — server handlers inline storage access against vanilla components. Add new peekable types by adding a case to the switch in `ContainerPeek.readContents` / `writeContents` / `detectSourceType`.

---

## Scope assessment

Phase 11 IP's in-scope deliverables landed. Layer summaries:

- Layer 0a: clean baseline ✓
- Layer 0: attachment foundations ✓
- Layer 1: all five groups (A, B1, B2, B3, B5) ✓ + `SlotIdentity` primitive shipped
- Layer 2: passive behaviors, cycle, autofill, HUD ✓; equipment + pockets UI deferred (F8, F9)
- Layer 3: packets, stateless server, session state, cross-mod API ✓; panel UI deferred (F15)
- Layer 4: public API narrowed + `PeekSourceType` enum + `PUBLIC_API.md` ✓
- Layer 5: dead-code cleanup + mixin audit + final verification ✓

Eleven features deferred with clear reasons (primitive-blocked ×4: F1/F2/F8/F9/F15; feature-scope ×2: F5/F6; feature-complexity ×3: F3/F4/F7; feature-addition ×1: F10). One library primitive shipped (M2). Five mechanism candidates filed for Phase 12 design (M1/M3/M4/M5/M6), each with architectural category, evidence from implementation pressure, and shape hints.

Phase 11 consumer-mod arc (IP + shulker-palette + sandboxes + agreeable-allays) complete. IP is the primary mechanism-evidence source; other mods corroborate M5 (layout) and consume M6 transitively (shulker-palette SP-F1 → F15 → M6).

**IP is ready for Phase 13.** All deferred features have primitive or sequencing dependencies that read directly against Phase 12's design queue. The "pick up a deferral" work is small and mechanical once the relevant mechanism ships.
