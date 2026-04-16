# Phase 11 IP — Report

Close-out for inventory-plus under the Phase 11 / 12 / 13 arc reframing (2026-04-15). Phase 11's gate is *"in-scope features work + deferred features documented + primitive list captures all surfaced needs,"* not full parity with pre-Phase-5 IP.

---

## Arc context

The original Phase 11 plan was "rebuild IP fully against current MenuKit." Mid-phase this split into three:

- **Phase 11 (this report)** — rebuild as far as current MenuKit allows. When a feature needs a primitive that doesn't exist, defer the feature and file the primitive.
- **Phase 12** — design and ship MenuKit primitives using evidence accumulated across all consumers.
- **Phase 13** — complete deferred consumer-mod features against Phase 12's primitives.

The exception clause that authorized `SlotIdentity` mid-phase is closed. One primitive (`SlotIdentity`) shipped; the rest defer. IP's remaining gaps are captured in `POST_PHASE_11.md` as feature (F\*) and mechanism (M\*) entries.

---

## Shipped in Phase 11

### Layer 0 — attachment foundations

- `IPPlayerAttachments` — Fabric data attachments for equipment, pockets, pocket-disabled state. Registered on both sides; per-player persistent + synced to client.
- Dev-only `/ip_attach_probe` command for round-trip verification. Confirmed: write → save → relog → read survives.
- Pattern for future attachment-backed per-player data captured in `COMMON_FRICTIONS.md` § 1 (`initializer(...)` doesn't auto-populate; `getOrInit*` wrapper contract).

### Layer 1 Group A — lock keybind + overlay

- Lock keybind toggles sort-lock state on the hovered slot. Visual overlay renders a lock sprite on sort-locked slots.
- `SortLockC2SPayload` mirrors client toggles to server-side `ServerLockStateHolder`. Disconnect clears per-player state.
- `ClientLockStateHolder` + `ServerLockStateHolder` both keyed by `SlotIdentity` so lock state survives menu transitions that share the underlying container (within-session).

### Layer 1 Group B1 — sort via click-sequence

- **Architectural pivot** (2026-04-15): sort moved from server-side slot mutation to client-composed click sequences. Client computes a permutation, sends `ClickSequenceC2SPayload`, server replays each click through vanilla's `menu.clicked(...)`. Benefits: `mayPlace` + filter + event + cross-mod slot-click mixins all fire automatically.
- `SortAlgorithm.computeSortClicks` — stable-sort permutation via 3-click PICKUP swaps. Consolidation deferred (F4).
- `IPRegionGroups` sub-partitioning: vanilla's overbroad `player_inventory` group splits into synthetic `ip:hotbar` / `ip:main` for sort scope.
- Creative inventory supplementary mixin fixes Phase 10 silent-inert failure mode #1 for `keyPressed`.
- Text-input gate (`KeybindDispatch.hasActiveTextInput`) walks children for visible `EditBox` so creative's search field + any consumer-mod text input receives keystrokes normally.

### Layer 1 Group B2 — shift+double-click bulk-move

- Detection at `mouseClicked` HEAD + suppression of vanilla's paired `mouseReleased` PICKUP_ALL dispatch. Second shift-click within a 250ms window fires a region-scoped `QUICK_MOVE` click sequence.
- Source-set picks based on anchor position + menu shape: player anchor with chest open → all player storage; container anchor → that container group; player anchor no container → anchor sub-region (`ip:hotbar` or `ip:main`, deferred F7).
- **Sort-lock blocks auto-routing in both directions** (new semantic). `IPMenuClickedMixin` + `IPMoveItemStackToMixin` bracket vanilla's dispatch with a `ServerLockGuard` ThreadLocal; `IPSlotMayPlaceMixin` returns false for locked slots during routing, and `IPSlotGetItemMixin` returns EMPTY so vanilla's merge phase (no `mayPlace` check) also skips them. Direct `PICKUP` clicks unaffected — locks block auto-routing, not hand-placement. Client + server lock holders both consulted so prediction matches authority (no one-frame flicker).

### Layer 1 Group B3 — move-matching via click sequence

- Keybind fires a `QUICK_MOVE` click sequence. Destination region is the anchor's; match set is every item type already present in that region (not the hovered item). Pulls every source-region stack whose type is in the set.
- `includeHotbarInMoveMatching` config honoured on the container-anchored direction.
- No-container-open case is a no-op (hotbar ↔ main deemed not compelling enough; same click-protocol quirks as F7).

### Layer 1 Group B5 — settings gear + config category

- `IPConfigCategory.build()` — YACL category with option groups for Equipment, Sorting, Pockets, Peek. Bindings point directly at `InventoryPlusConfig` fields; saves go through the family's composite save handler.
- `SettingsGearDecoration` — Pattern 3 panel at top-right of inventory frame. Click opens `family.buildConfigScreen(screen, "inventory-plus")` focused on IP's tab.
- Wired into `InventoryContainerMixin` + `CreativeInventoryMixin` + `RecipeBookMixin` (fixes Phase 10 failure mode #2 for survival inventory's overriding render). MenuKit's Phase-10 corner-button example disabled for dev-test clarity.

### SlotIdentity primitive (MenuKit library)

- Shipped mid-Phase-11 as the one advisor-approved library exception. Record `(Container, int containerSlot)` + static factory `SlotIdentity.of(Slot)`. Zero-dependency primitive for cross-menu stable slot identity. Exception clause now closed.

---

## Deferred features (see `POST_PHASE_11.md`)

| Entry | Title | Category |
|-------|-------|----------|
| F1 | Persistent player-slot lock state across sessions | primitive-blocked (M1) |
| F2 | Chest-slot lock state visible across menu reopens | primitive-blocked (M1) |
| F3 | Full-lock (Ctrl+click) feature | feature-addition |
| F4 | Sort consolidation | feature-complexity |
| F5 | Creative-mode sort | feature-scope (creative packet path) |
| F6 | Creative-mode bulk-move | feature-scope (same as F5) |
| F7 | Bulk-move within a single player-inventory region (no container open) | feature-complexity (vanilla click-protocol edge case) |

Each entry names what Phase 13 delivers, the mechanism (if any) that enables it, and a rough implementation sketch.

---

## Mechanisms filed for Phase 12 (see `POST_PHASE_11.md`)

| Entry | Title | Status |
|-------|-------|--------|
| M1 | Unified per-slot state primitive | design input — enables F1 + F2 + future consumer mod state needs |
| M2 | SlotIdentity | **shipped** — historical record; M1 builds on it |
| M3 | MKFamily removal (or keep-as-is decision) | cleanup mechanism; resolution affects consumer-side refactor in Phase 13, not feature delivery |

Phase 12 reads M1 against multi-consumer evidence (IP + shulker-palette + sandboxes + agreeable-allays) before designing.

---

## Not-shipped-yet-in-plan (non-deferral)

Items the refactor plan lists for IP Phase 11 but scoped for later layers:

- **Layer 2** — attachment-dependent features: autofill, auto-restock, auto-replace, auto-route, mending helper, deep-arrow search, pocket cycle. All stubbed to no-op; mixin signatures preserved for in-place re-population.
- **Layer 3** — client-side peek: shulker / ender / bundle panels, peek keybind wiring, peek participation in sort + move-matching. Current `handlePeek` is a log-only stub.
- **Sort + move-matching buttons** (§ 4.5 of refactor plan) — per-region buttons above slot grids. Current keybinds cover the functionality; buttons are an alternative UI surface not yet implemented. Low priority; keybinds are the primary interaction path.

None of these are blocked. They're sequenced layers of the existing plan, to be done in subsequent Phase 11 slices or post-Phase-11 as the arc dictates.

---

## Frictions surfaced (see `COMMON_FRICTIONS.md`)

1. **`AttachmentRegistry.Builder.initializer(...)` doesn't auto-populate** — pattern: `getOrInit*` wrapper.
2. **`AttachmentSyncPredicate.targetOnly()` deprecation warning** — works; ignore for now.
3. **`CommandSourceStack.hasPermission(int)` removed in 1.21.11** — drop permission check in dev-only commands; find replacement if op-gate needed.
4. **`keyPressed` / `mouseClicked` take `KeyEvent` / `MouseButtonEvent` records in 1.21.11** — audit every such mixin; compile doesn't catch, Mixin applicator rejects at boot.
5. **`Screen.hasShiftDown()` removed in 1.21.11** — moved to `InputWithModifiers`. Outside event contexts, poll GLFW directly via `InputConstants.isKeyDown`.

---

## Architectural notes for Phase 13

- **Click-sequence architecture holds.** Sort, bulk-move, and move-matching all use the same `ClickSequenceC2SPayload` shape. Server replays via `menu.clicked`; vanilla semantics fire automatically. Future click-model features (consolidation, Ctrl+click full-lock) should extend this pattern rather than reimplement mutation paths.
- **Canonical-slot routing via `SlotIdentity`** solved creative's two-menu split for IP operations. Keep routing through `player.containerMenu` + `IPRegionGroups.canonicalSlot` for any future feature that mutates through the click sequence.
- **Sort-lock is auto-routing-scoped.** It blocks `moveItemStackTo` + `QUICK_MOVE` destination, not direct `PICKUP`. Phase 13's full-lock (F3) is a separate primitive with broader semantics; don't merge them.
- **Three mixin classes for inventory injection** — `InventoryContainerMixin` (primary on `AbstractContainerScreen`), `RecipeBookMixin` (supplementary on `AbstractRecipeBookScreen`, gated `instanceof InventoryScreen`), `CreativeInventoryMixin` (supplementary on `CreativeModeInventoryScreen`). Each feature adds its hooks to the same three classes. Phase 10 failure modes #1 (silent-inert) and #2 (render z-order) are handled by the supplementaries.

---

## Open questions for Phase 13 entry

- **M1 design.** Multi-consumer evidence should be collected before Phase 12 picks a shape. Once shulker-palette, sandboxes, and agreeable-allays file their per-slot / per-item state needs in their own `POST_PHASE_11.md`, Phase 12 designs once against the full set.
- **M3 resolution (MKFamily removal).** Not blocking any feature; decision can wait. If removed, Phase 13 refactors consumer-side config screens to stand alone.
- **F5 / F6 creative scope.** Are creative-mode sort + bulk-move worth the creative-packet-path code duplication? Low priority in practice (most inventory management happens in survival); may defer indefinitely if usage shows survival-only is enough.
- **F7 hotbar↔main no-container bulk-move.** May resolve for free if F3 (full-lock) investigation digs into vanilla's double-click state fields (`doubleclick`, `lastQuickMoved`). Otherwise defer indefinitely — move-matching keybind covers the "all matching" use case.

---

## Scope assessment

Phase 11 IP's in-scope deliverables landed. Six features deferred with clear reasons (primitive-blocked / scope / complexity / feature-addition). One library primitive shipped; one mechanism (M1) filed for Phase 12 design; one cleanup mechanism (M3) flagged for decision.

IP is ready for Phase 13 once Phase 12's M1 primitive ships. Layer 2 + Layer 3 IP work (attachment-dependent features, client-side peek) is unblocked and can proceed on the current MenuKit surface whenever scheduling permits.

Next consumer mod: **shulker-palette** under the same framing. Expected to surface per-item state as a new mechanism candidate.
