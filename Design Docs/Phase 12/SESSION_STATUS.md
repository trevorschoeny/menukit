# Phase 12 Session Status — 2026-04-16/17

Comprehensive status dump for fresh-agent pickup and advisor replanning. This session began Phase 12 implementation; the original plan (M6 → M4 → M1 → M5) was significantly revised during execution. The current state diverges enough from the M4 design doc that a fresh plan is warranted.

---

## 1. What shipped (committed to git)

One commit from this session:

| Hash | Description | Scope |
|------|-------------|-------|
| `a448572` | Phase 12: M6 dissolved — peek requires M4 vanilla slot injection; rendering analysis carries forward | MenuKit design doc + IP stub updates |

Contents: M6 design doc marked DISSOLVED at `Design Docs/Phase 12/M6_CLIENT_SIDE_SLOTS.md`. POST_PHASE_11.md M6 status updated. IP stubs (PeekDecoration, KeybindDispatch.handlePeek) updated to reference M4 as blocker instead of M6. No code changes — only documentation.

---

## 2. What's in the working tree (uncommitted)

### MenuKit — new files (untracked)

| File | Status | Description |
|------|--------|-------------|
| `core/StorageContainerAdapter.java` | New, complete | Extracted from MenuKitScreenHandler inner class. Bridges MenuKit `Storage` → vanilla `Container`. ~60 lines. Used by SlotInjector and InventoryMenuMixin. |
| `core/SlotRendering.java` | New, complete | Static utility: `drawSlotBackground`, `drawHoverHighlight`, `drawItem`, `drawGhostIcon`. Carried forward from M6. Compiles. **Not yet visually verified** — render hook doesn't reach InventoryScreen (see §7). |
| `core/GraftedRegion.java` | New, complete | Data carrier: slot index range + Panel + SlotGroups. **May dissolve** — advisor suggested evaluating after F8 works. |
| `core/SlotInjector.java` | New, complete | Grafting utility using AbstractContainerMenuAccessor. **May dissolve** — advisor noted that bare `addSlot()` in the consumer mixin might be sufficient. Currently unused (InventoryMenuMixin calls addSlot directly). |
| `mixin/AbstractContainerMenuAccessor.java` | New, complete | `@Invoker("addSlot")` on AbstractContainerMenu. Currently unused (InventoryMenuMixin calls addSlot via mixin inheritance). May be needed if SlotInjector ships. |
| `mixin/MKHasClickedOutsideMixin.java` | New, **working** | Targets `AbstractRecipeBookScreen.hasClickedOutside`. Returns false when click lands on any active slot. **Verified working** — fixes the PICKUP→THROW misclassification for slots outside the container frame. |

### MenuKit — modified files

| File | Change | Description |
|------|--------|-------------|
| `core/MenuKitSlot.java` | `getItem()` override removed | Removed the data-flow override that returned EMPTY for inert slots. Replaced with a comment explaining why `isActive()` + `mayPlace()` + `mayPickup()` are sufficient. **Not yet verified with /mkverify.** |
| `screen/MenuKitScreenHandler.java` | Inner class removed | `StorageContainerAdapter` extracted to `core/`. Inner class replaced with a comment. Handler now imports from `core/`. Compiles. **Not yet verified with /mkverify.** |
| `menukit.mixins.json` | Two entries added | `AbstractContainerMenuAccessor` (in `mixins` array) + `MKHasClickedOutsideMixin` (in `client` array). |

### MenuKit — new design doc

| File | Status |
|------|--------|
| `Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md` | New, partially stale — see §8. |

### IP — new files

| File | Status | Description |
|------|--------|-------------|
| `mixin/InventoryMenuMixin.java` | New, **working** | Grafts 2 equipment slots onto InventoryMenu at `<init>` RETURN. Uses anonymous Slot subclasses with `mayPlace` (elytra/totem filter) + `getMaxStackSize(1)`. Backed by `StorageContainerAdapter` wrapping `PlayerAttachedStorage.forEquipment`. quickMoveStack mixin routes shift-clicks to main inventory. **Functionally verified.** |

### IP — modified files

| File | Change | Description |
|------|--------|-------------|
| `inject/InventoryContainerMixin.java` | Render hook extended | Added slot-background rendering for grafted slots via `SlotRendering.drawSlotBackground`. **Includes a debug red square.** The rendering does not appear — see §7. |
| `inventory-plus.mixins.json` | One entry added | `InventoryMenuMixin` in `mixins` array (both sides). |
| `inventory-plus-inject.mixins.json` | Unchanged | |

---

## 3. What works right now (verified in dev client)

- **Slot grafting mechanism.** `addSlot()` at `InventoryMenu.<init>` RETURN produces real vanilla Slot instances at indices 46-47. Both client and server construct identically — symmetry holds.
- **hasClickedOutside fix.** Clicks on slots outside the container frame are correctly classified as slot clicks (not "outside" clicks). Items go to cursor on PICKUP instead of being THROW-dropped.
- **Equipment slot filters.** Slot 46 accepts only elytra; slot 47 accepts only totem. Other items rejected via `mayPlace`.
- **Max stack size.** Equipment slots enforce stack size 1.
- **Shift-click routing.** Shift-click from equipment slots moves items to main inventory (9-44).
- **Persistence.** Equipment items survive menu close/reopen and world rejoin (Fabric attachment-backed via `PlayerAttachedStorage`).
- **Existing Phase 11 features.** Sort, bulk-move, move-matching, lock keybinds, settings gear, shulker-palette toggle, sandboxes buttons — all still function. (Note: `/mkverify all` has NOT been run this session.)

---

## 4. What's broken or incomplete right now

- **Slot backgrounds don't render.** The `SlotRendering.drawSlotBackground` call in InventoryContainerMixin's render TAIL hook does not appear on screen. A debug bright-red `graphics.fill()` square in the same hook also doesn't appear. Root cause: both `InventoryScreen` (line 75) and `AbstractRecipeBookScreen` (line 57) override `render()`, so the `@Inject(method = "render", at = @At("TAIL"))` on `AbstractContainerScreen` likely doesn't fire for `InventoryScreen`. The existing Phase 11 decorations (SettingsGear, LockOverlay) render through the same hook — **this needs investigation** (either they work via a different mechanism, or the render override calls super and the TAIL does fire, meaning the issue is elsewhere).
- **No ghost icons.** Equipment slots have no `getNoItemIcon` override — empty slots are invisible. Vanilla's extension point (`Slot.getNoItemIcon() → Identifier`) is the right path; just not wired yet.
- **No slot background visual layer.** The fundamental question: should grafted-slot backgrounds render via a Panel + ScreenPanelAdapter (like SettingsGear), or via manual `graphics.fill()` in a mixin render hook? Trevor's position: it should go through Panels. This is an unresolved design question.
- **Pockets (F9) not started.** Equipment (F8) is the current focus. Pockets require nine 3-slot panels with toggles, disabled-slot gating, and more complex layout.
- **Creative mode untested.** Equipment slots graft onto InventoryMenu (which exists in creative), but creative's screen uses `ItemPickerMenu`. Canonical-slot routing for creative is a Phase 13 concern per the design doc.
- **Debug code in production path.** InventoryContainerMixin has a `graphics.fill(...)` debug red square that should be removed.

---

## 5. MenuKitSlot restructure status

**Partially complete.**

- `StorageContainerAdapter` extracted to `core/` as a standalone class. ✓
- `MenuKitScreenHandler` inner class removed; references the new standalone class via wildcard import. Compiles. ✓
- `MenuKitSlot.getItem()` override removed. The override returned EMPTY for inert (hidden-panel) slots; replaced with a comment explaining why `isActive()` + `mayPlace()` + `mayPickup()` are sufficient. ✓
- **NOT verified.** `/mkverify all` has not been run. Visual verification of MenuKit-native screens has not been done. The hypothesis — that removing the `getItem()` override doesn't break hidden-panel behavior — is untested. The advisor flagged this as a verification gate: "if removing the getItem() override causes hidden-panel slots to leak their contents somewhere (broadcastChanges? slot sync?), add targeted protection."

---

## 6. M4 mechanism status

**Core mechanism: confirmed working.**

- **`addSlot()` at `<init>` RETURN:** Architecturally sound. Slots are real vanilla Slot instances in `containerMenu.slots`. All three parallel lists (`slots`, `lastSlots`, `remoteSlots`) are synchronized correctly because `addSlot()` handles them. Client-server symmetry holds because both sides run the same mixin.
- **`hasClickedOutside` fix:** Working. Mixin on `AbstractRecipeBookScreen.hasClickedOutside` returns false when the click lands on any active slot, preventing the PICKUP→THROW misclassification. Verified in dev client.
- **Shift-click routing:** Working. `quickMoveStack` mixin at HEAD intercepts grafted slot indices and routes to main inventory.
- **Vanilla slot protocol:** Full vanilla behavior — drag, cursor management, `broadcastChanges` sync — all work automatically because the grafted slots are standard Slot instances.

**Library surface: undecided.**

The advisor said to build F8/F9 with the minimum viable approach, then evaluate what's reusable. Current state:
- `SlotInjector` — written but unused (InventoryMenuMixin calls `addSlot` directly). May dissolve.
- `GraftedRegion` — written but unused (InventoryMenuMixin tracks its own index range). May dissolve.
- `StorageContainerAdapter` — used and needed. Ships as library artifact.
- `SlotRendering` — written, compiles, not visually verified. Ships as library artifact.
- `AbstractContainerMenuAccessor` — written but unused (mixin calls addSlot via inheritance). May dissolve.
- `MKHasClickedOutsideMixin` — working, needed. Ships as library artifact.

---

## 7. M4 visual layer status

**Blocked. The render hook approach failed.**

**What was attempted:** Rendering slot backgrounds via `SlotRendering.drawSlotBackground()` inside `InventoryContainerMixin`'s `@Inject(method = "render", at = @At("TAIL"))` hook.

**What failed:** Neither the slot backgrounds nor a debug bright-red `graphics.fill()` square appear on screen. The render code either doesn't execute or renders somewhere invisible.

**Probable cause:** Both `InventoryScreen` (line 75) and `AbstractRecipeBookScreen` (line 57) override `render()`. The TAIL injection on `AbstractContainerScreen.render` may not fire for `InventoryScreen` if the override doesn't call `super.render()` or calls it in a way that prevents the injection from reaching the actual screen.

**Contradiction to investigate:** Phase 11's `SettingsGearDecoration` and `LockOverlayDecoration` render from the SAME hook (`inventoryPlus$render` at render TAIL) and they work. Either: (a) they work via a different mechanism I'm not seeing, (b) the render override DOES call super and the hook fires but my specific code has a bug, or (c) something changed during this session's edits that broke the hook.

**Trevor's position:** Slot backgrounds should render through the Panel + ScreenPanelAdapter system (same as SettingsGear), not through manual `graphics.fill()` in the mixin. This is architecturally cleaner and uses the proven rendering path.

**Open design question:** How do M4's grafted vanilla Slot instances (handler level) integrate with MenuKit's Panel rendering system (screen level)? The two layers need to be connected — the Panel needs to render backgrounds at the grafted slots' positions, and those positions must stay in sync with `Slot.x`/`Slot.y` (which vanilla uses for hit-testing and item rendering).

---

## 8. Design docs — current accuracy

### `Design Docs/Phase 12/M6_CLIENT_SIDE_SLOTS.md`

**Accurate.** Marked DISSOLVED with the verification finding preserved. Historical record for M4's design input. No updates needed.

### `Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md`

**Partially stale.** Status says "Approved" which is correct for the design direction. But several implementation findings aren't reflected:

- **hasClickedOutside root cause** — not in the design doc. This is a critical implementation finding: vanilla's `hasClickedOutside` misclassifies clicks on slots outside the container frame, changing PICKUP to THROW. The fix (mixin on `AbstractRecipeBookScreen.hasClickedOutside`) should be documented.
- **Render-hook failure** — the design doc assumes slot backgrounds render via `SlotRendering` in a mixin render hook. This approach failed. The doc should note the failure and the open question about Panel integration.
- **MenuKitSlot restructure** — described in the advisor briefing but not in the design doc. The doc references MenuKitSlot + SlotGroup + Panel for the grafted slots; the restructure changes how MenuKitSlot works internally.
- **SlotInjector / GraftedRegion dissolution** — the design doc describes these as library artifacts. The advisor's later briefing suggested they may dissolve. The doc should flag this.
- **Slot.x/y mutability** — the doc flagged this as a verification item. Verified: `SlotPositionAccessor` already makes them mutable. Should be updated.
- **Bundle slot count** — updated to 64 (from 27). This is accurate.
- **Six-packet protocol note** — added correctly. Accurate.

---

## 9. POST_PHASE_11.md — current accuracy

### Changed entries

| Entry | Change |
|-------|--------|
| **M6** | Status changed from "design input" to "dissolved in Phase 12." Committed in `a448572`. Accurate. |

### Unchanged entries that should update after this session's work lands

| Entry | Needed update |
|-------|---------------|
| **M4** | Status should change from "design input" to "in progress — mechanism confirmed, visual layer blocked." |
| **F8** | Should note: grafting mechanism verified, filter/persistence working, visual layer (slot backgrounds) pending. |
| **F15** | Should note: option (a) approved (dynamic pre-allocation), but implementation is Phase 13. M6 dissolution record already references this. |

### No new entries filed this session.

---

## 10. Architectural findings from this session

### Finding 1: hasClickedOutside misclassifies slots outside the container frame

**Root cause:** `AbstractContainerScreen.mouseClicked` calls `hasClickedOutside()` and uses the result to override the slot index: if `hasClickedOutside` returns true, the slot index `k` is overwritten from the valid slot index to `-999`. This changes `ClickType` from PICKUP to THROW, causing `player.drop()` — items physically drop as entities instead of going to the cursor.

**Why it happens:** `hasClickedOutside` checks `mouseX < leftPos || mouseY < topPos || mouseX >= leftPos + imageWidth || mouseY >= topPos + imageHeight`. Slots outside the container frame (x < 0 or x >= imageWidth) are correctly found by `getHoveredSlot` (which has no frame restriction) but then overridden to -999 by `hasClickedOutside`.

**Fix:** Mixin on `AbstractRecipeBookScreen.hasClickedOutside` (not `AbstractContainerScreen` — because `AbstractRecipeBookScreen` overrides it, and `InventoryScreen` inherits from there). Returns false when the click lands on any active slot's bounds. **Verified working.**

**Scope note:** The fix must also be applied to `AbstractContainerScreen.hasClickedOutside` (for non-recipe-book screens like chests) and `CreativeModeInventoryScreen.hasClickedOutside` (for creative). Currently only `AbstractRecipeBookScreen` is covered.

### Finding 2: M6 (client-side slot primitive) dissolves

Peek needs vanilla's full slot protocol (drag, bidirectional shift-click, native cursor management). A client-side primitive with custom click dispatch doesn't produce native behavior. Without peek, ClientSlot has no consumer evidence. M6 dissolved; peek is an M4 use case.

### Finding 3: Vanilla's Slot extension points are sufficient

Vanilla's `Slot` class has well-designed extension points: `mayPlace`, `mayPickup`, `getMaxStackSize`, `isActive`, `getNoItemIcon`. These are all you need for equipment-style filtered slots. No MenuKitSlot abstraction was needed for the F8 diagnostic — anonymous Slot subclasses with 2-3 overrides each worked perfectly.

### Finding 4: Two-layer model for grafted slots

Grafted slots have two layers:
- **Handler layer** (M4 mechanism): real vanilla Slot instances in `containerMenu.slots`, created via `addSlot()` at construction time. Vanilla handles all interaction (click dispatch, drag, shift-click, cursor, sync).
- **Visual layer** (Panel rendering): slot backgrounds, ghost icons, panel frames rendered via MenuKit's Panel + ScreenPanelAdapter system. Vanilla only renders items at `Slot.x`/`Slot.y` (via `renderSlot`); everything else (backgrounds, highlights, panel chrome) is the consumer's visual layer.

These two layers are currently disconnected. Connecting them — so a Panel "knows about" the grafted slots it visually represents — is the open design question.

### Finding 5: Slot.x/y are mutable

MenuKit already has `SlotPositionAccessor` with `@Mutable @Accessor` on `Slot.x` and `Slot.y`. Slot positions can be updated at runtime. This resolves the F15 concern (peek panel position not known at construction time).

### Finding 6: render TAIL hook may not fire for InventoryScreen

Both `InventoryScreen` and `AbstractRecipeBookScreen` override `render()`. A `@Inject(method = "render", at = @At("TAIL"))` on `AbstractContainerScreen` may not reach `InventoryScreen`. This needs investigation — it contradicts the fact that Phase 11 decorations (SettingsGear, LockOverlay) render from the same hook.

---

## 11. What I'd do next if continuing

1. **Investigate the render hook.** Why does the TAIL injection on `AbstractContainerScreen.render` not produce visible output for InventoryScreen? Read `AbstractRecipeBookScreen.render()` and `InventoryScreen.render()` to trace the call chain. Check whether they call `super.render()` and where the TAIL fires relative to the screen's actual visible output. This is blocking all visual work.

2. **Verify the MenuKitSlot restructure.** Run `/mkverify all`. Visually check at least one MenuKit-native screen. Confirm hidden-panel slots don't leak items. This is a verification gate — must pass before the restructure can ship.

3. **Decide the visual-layer architecture.** Should grafted-slot backgrounds render through a Panel + ScreenPanelAdapter (consistent with existing inventory-context rendering) or through a different mechanism? Trevor's preference is Panels. The advisor should weigh in on how the handler-level slots connect to the screen-level Panel.

4. **Wire ghost icons.** Override `getNoItemIcon()` on the equipment Slot subclasses to return elytra/totem sprites. This is vanilla's extension point for empty-slot placeholders and doesn't depend on the visual-layer architecture decision.

5. **Clean up experimental code.** Remove the debug red square from InventoryContainerMixin. Evaluate whether SlotInjector, GraftedRegion, and AbstractContainerMenuAccessor earn their keep or should be deleted.

6. **Commit when stable.** Three logical commits: (a) MenuKit library: hasClickedOutside fix + MenuKitSlot restructure + StorageContainerAdapter extraction, (b) MenuKit library: SlotRendering + any visual-layer primitives that survive evaluation, (c) IP consumer: F8 equipment slot grafting + rendering.
