# Phase 7 Report — Context Generalization

Phase 7 generalizes `Panel` and `PanelElement` across MenuKit's three rendering contexts (inventory menus, HUDs, standalone screens). The refactor landed cleanly and all five canonical contracts verify against the Phase 7 endpoint with zero regressions.

---

## What was done

**Design.** A design doc was drafted, reviewed with the advisor across one cycle, and locked before any code changes. Three explicit pause points — the `RenderContext` record shape (Option B), the slot-groups structural question (Option d: groups on the handler, not on Panel), and the Phase 7-vs-Phase 8 decision on `MenuKitScreen` — were resolved through review. All three advisor-affirmed positions were carried into implementation.

**Code changes, in five steps:**

1. **`RenderContext` record + unified `PanelElement` signature.** New `core/RenderContext.java` (graphics, originX/Y, mouseX/Y + `hasMouseInput()` / `isHovered` helpers). `PanelElement.render` takes a single `RenderContext` parameter; `mouseClicked` defaults to `false`; `isHovered(ctx)` is a default helper. `Button` and `TextLabel` migrated. `MenuKitHandledScreen` constructs a `RenderContext` per panel in `renderBg`.

2. **Panel slot-groups refactor (Option d).** `Panel` drops its `List<SlotGroup> groups` field and all groups-accepting constructors. `MenuKitScreenHandler` gains a `Map<String, List<SlotGroup>> groupsByPanel` wired from the builder and exposes `getGroupsFor(panelId)`. `MenuKitSlot` now takes `Panel panel` directly (derives `panelId` internally) so inertness reads `panel.isVisible()` without the former `SlotGroup.getPanel()` back-reference. `SlotGroup.panel`, `getPanel`, `setPanel` deleted. Four `panel.getGroups()` call sites in `MenuKitHandledScreen` and `MenuKitScreenHandler.quickMoveStack` replaced with `handler.getGroupsFor(id)`.

3. **HUD subsystem adaptation.** `MKHudElement` interface deleted. `MKHudText`, `MKHudItem`, `MKHudBar`, `MKHudSlot`, `MKHudIcon` adapted to implement `PanelElement` with the core size convention (`getWidth` returns actual width, not right-edge; content-origin positioning via `RenderContext.originX/Y`). `MKHudCustom` class deleted — `.custom()` builder now produces an inline `PanelElement` with a `Consumer<RenderContext>` callback. `MKHudList` and `MKHudGroup` deleted (zero external consumers). `MKHudNotification` retained (HUD-specific, separate render path). `MKHudPanelDef` holds `List<PanelElement>` and computes auto-size as `max(childX + width, childY + height) + padding*2`. `MenuKit.renderHud()` constructs a `RenderContext` per panel with `mouseX = -1` for the no-input-dispatch convention.

4. **`PanelLayout` utility + `MenuKitScreen`.** New `core/PanelBounds.java` (top-level record, context-neutral). New `core/PanelLayout.java` — pure utility: `resolve(panels, sizes, bodyGap, relativeGap, titleHeight)` returns bounds map from declared `PanelPosition` constraints. `MenuKitHandledScreen.computeLayout` rewritten to use the utility; inventory-specific bits (`imageWidth`, `imageHeight`, `inventoryLabelY`) derived as post-processing. New `screen/MenuKitScreen.java` — minimal standalone-screen base class extending vanilla `Screen`, holds `List<Panel>`, sizes panels from element bounds, renders + dispatches clicks in reverse panel order.

5. **Contract verification harness restored and committed.** The Phase 5 verification package (`verification/ContractVerification`, `verification/TestContractHandler`, `verification/TestContractScreen`, `mixin/VerifyMayPlaceMixin`, `mixin/VerifyGetItemMixin`) restored from git history, adapted for Phase 7 (two `hidden.getGroups()` call sites → `handler.getGroupsFor("hidden")`), and kept committed going forward. Doc comments rewritten to reflect permanent infrastructure status. `MenuKit.init()` / `initClient()` wire the harness; `menukit.mixins.json` declares the two verify mixins.

**Verification.** All five canonical contracts re-ran against the Phase 7 endpoint. Full log evidence captured below under "Contract verification evidence."

---

## Deviations from the design

None substantive. Every design decision was followed through without backtracking. One minor thing worth naming: the `MenuKitScreen` centering logic (`computeLayout` offsets by `min/max` across bounds) is new territory that wasn't in the design doc sketch because it emerged during implementation — not a deviation, but an addition. Flagged below as a Phase 8 validation item.

---

## Surprises

None during implementation. The refactor executed mechanically because the design resolved the hard questions before code was touched — Panel's slot-group question was the biggest architectural commitment and it was resolved in the design doc.

The only thing that could be called a surprise is how cleanly the HUD migration went. Consumer usage of the HUD builder turned out to be narrow (`.text()` and `.custom()` are the only external callers in the entire codebase), which meant the entire HUD subsystem's internals could reshape freely. The palette's deferral of `List` and `Group` had no downstream consumer impact at all — zero callers to migrate. This is useful evidence that the deferral was correctly calibrated: the elements had been shipped speculatively and no real consumer adopted them.

---

## Decisions not in the brief

Four items surfaced during implementation and are worth naming because they'll shape Phase 8+.

**Verification harness status changed from scaffolding to permanent infrastructure.** Phase 5's decision to remove the harness after evidence capture was reconsidered. Phase 7's refactor touched enough of the contract-relevant surface that re-verification was non-trivial to set up from scratch. The harness is now committed and documented as ongoing infrastructure — each subsequent phase can re-run verification cheaply, and phase reports can carry contract-verification evidence as a matter of course.

**`RenderContext` has no `DeltaTracker` field.** Considered and deferred. No currently-shipping element or Phase 8-slated element needs it. HUD `MKHudNotification` — the only stateful HUD element — has its own render path that takes `DeltaTracker` directly. If Phase 8 introduces an animated core element, adding a field to the record is non-breaking.

**HUD builder `.custom()` signature is now `Consumer<RenderContext>`.** The old signature (`(graphics, x, y, w, h, deltaTracker) -> ...`) would have required preserving a redundant `Renderer` functional interface. The new signature is consistent with the library-wide pattern of passing `RenderContext` to any custom render lambda. Flagged in DEFERRED.md for Phase 11 consumer refactor.

**`MenuKitSlot` constructor takes `Panel` instead of `String panelId`.** Signature change that ripples to any consumer hand-constructing slots. No in-tree breakage (builder path is the only constructor caller). Captured in DEFERRED.md for Phase 11.

---

## Contract verification evidence

The `/mkverify` command suite was run against the Phase 7 endpoint. All five contracts **PASS**.

### 1. Composability

```
[Verify.Composability] BEGIN
[Verify.Composability] Phase A — probing 46 vanilla slots in InventoryMenu
[Verify.Composability] Phase A result — 0 MK / 46 vanilla slots, cobble rejected on 46, diamond accepted on 41
[Verify.Composability] Phase B — probing 46 MK slots in MenuKitScreenHandler
[Verify.Composability] Phase B result — 46 MK / 0 vanilla slots, cobble rejected on 46, diamond accepted on 42
[Verify.Composability] VERDICT — mixin fired on both vanilla (46 slots) and MK (46 slots) slot types; global cobblestone filter applied uniformly
[Verify.Composability] END
```

A global `Slot.mayPlace` mixin fired identically on vanilla slots (46 in `InventoryMenu`) and MenuKit slots (46 in `MenuKitScreenHandler`). The cobblestone filter was applied uniformly on all 92 slots. **PASS**.

### 2. Vanilla-slot substitutability

```
[Verify.Substitutability] BEGIN
[Verify.Substitutability] Structural: 46/46 slots pass `instanceof Slot` (46 are MenuKitSlot)
[Verify.Substitutability] Sample slot — class=com.trevorschoeny.menukit.core.MenuKitSlot instanceof Slot=true instanceof MenuKitSlot=true
[Verify.Substitutability] Triggering getItem() on all MK slots (mixin armed)…
[Verify.Substitutability] VERDICT — all 46 MK slots pass `instanceof Slot`, and Slot.getItem RETURN mixin fires on MK slots with the composed return value (including inertness-driven EMPTY for any hidden panel)
[Verify.Substitutability] END
```

All 46 MenuKit slots pass `instanceof Slot`. A global `Slot.getItem` RETURN mixin fired on MenuKit slots and observed the composed return value (including inertness-driven EMPTY for hidden panels). MenuKit slots are, to the ecosystem, vanilla slots. **PASS**.

### 3. Sync-safety

```
[Verify.SyncSafety] BEGIN
[Verify.SyncSafety] Hidden panel covers flat slot range [6..10)
[Verify.SyncSafety] iter 0 — target=true reported=true desync=0
[Verify.SyncSafety] iter 1 — target=false reported=false desync=0
... (10 iterations, all desync=0)
[Verify.SyncSafety] Post-stress (visible) — slot contents: 6=minecraft:diamondx16 7=minecraft:diamondx8 8=minecraft:diamondx2 9=EMPTY
[Verify.SyncSafety] VERDICT — 10 toggles, 0 inconsistencies. PASS — the protocol's view stayed consistent with visibility.
[Verify.SyncSafety] END
```

Ten rapid visibility toggles on the hidden panel produced zero desyncs. Real storage contents (`diamond×16`, `×8`, `×2`, `EMPTY`) correctly restored on final visibility. The sync-safety discipline holds through the refactor that moved slot groups from Panel to Handler. **PASS**.

### 4. Uniform abstraction

```
[Verify.Uniform] BEGIN
[Verify.Uniform] Phase A — menu=net.minecraft.world.inventory.InventoryMenu slotCount=46
[Verify.Uniform] Phase A — findGroup(slot 0) → SlotGroupLike (observed VirtualSlotGroup) id='container' canAccept(DIAMOND)=true qmp=BOTH
[Verify.Uniform] Phase A — recognize() → 3 VirtualSlotGroup(s)
[Verify.Uniform] Phase A   group id='container' size=1 policy-accepts-diamond=true
[Verify.Uniform] Phase A   group id='container_1' size=4 policy-accepts-diamond=true
[Verify.Uniform] Phase A   group id='player_inventory' size=41 policy-accepts-diamond=true
[Verify.Uniform] Phase B — menu=com.trevorschoeny.menukit.screen.MenuKitScreenHandler slotCount=46
[Verify.Uniform] Phase B — findGroup(slot 0) → SlotGroupLike (native SlotGroup) id='container' canAccept(DIAMOND)=true qmp=BOTH
[Verify.Uniform] Phase B — recognize() → 0 VirtualSlotGroup(s)
[Verify.Uniform] VERDICT — same findGroup() API used against both vanilla (InventoryMenu) and MenuKit (MenuKitScreenHandler) handlers; both return Optional<SlotGroupLike>, concrete implementations (VirtualSlotGroup vs SlotGroup) transparent to caller
[Verify.Uniform] END
```

`HandlerRecognizerRegistry.findGroup(menu, slot)` returned an `Optional<SlotGroupLike>` uniformly across both vanilla (observed `VirtualSlotGroup`) and MenuKit (native `SlotGroup`) handlers. Consumer API is invariant under handler type. **PASS**.

### 5. Inertness

```
[Verify.Inertness] BEGIN
[Verify.Inertness] Phase A — hidden (isVisible=false)
[Verify.Inertness] HIDDEN  slot 6 — getItem.empty=true active=false mayPlace(DIAMOND)=false mayPickup=false isInert=true → OK (fully inert)
[Verify.Inertness] HIDDEN  slot 7 — getItem.empty=true active=false mayPlace(DIAMOND)=false mayPickup=false isInert=true → OK (fully inert)
[Verify.Inertness] HIDDEN  slot 8 — getItem.empty=true active=false mayPlace(DIAMOND)=false mayPickup=false isInert=true → OK (fully inert)
[Verify.Inertness] HIDDEN  slot 9 — getItem.empty=true active=false mayPlace(DIAMOND)=false mayPickup=false isInert=true → OK (fully inert)
[Verify.Inertness] Phase A result — 4/4 hidden slots fully inert
[Verify.Inertness] Phase B — visible (isVisible=true)
[Verify.Inertness] VISIBLE slot 6 — getItem.empty=false active=true mayPlace(DIAMOND)=true mayPickup=true isInert=false content=minecraft:diamondx16
[Verify.Inertness] VISIBLE slot 7 — getItem.empty=false active=true mayPlace(DIAMOND)=true mayPickup=true isInert=false content=minecraft:diamondx8
[Verify.Inertness] VISIBLE slot 8 — getItem.empty=false active=true mayPlace(DIAMOND)=true mayPickup=true isInert=false content=minecraft:diamondx2
[Verify.Inertness] VISIBLE slot 9 — getItem.empty=true active=true mayPlace(DIAMOND)=true mayPickup=true isInert=false content=EMPTY
[Verify.Inertness] Phase B result — 4/4 slots flipped to active+non-inert
[Verify.Inertness] VERDICT — inertness holds: hidden slots report fully inert (4/4 OK); visible slots flip back (4/4 restored)
[Verify.Inertness] END
```

All four hidden-panel slots reported fully inert: `getItem.empty=true`, `active=false`, `mayPlace=false`, `mayPickup=false`, `isInert=true`. After toggling the panel visible, all four flipped back with real storage contents exposed. The inertness discipline is preserved through the `MenuKitSlot.panel` reference change (Phase 7 moved the back-reference from `SlotGroup.getPanel()` to a direct `Panel` field on `MenuKitSlot`). **PASS**.

### Summary

| Contract | Result |
|---|---|
| 1. Composability | PASS |
| 2. Vanilla-slot substitutability | PASS |
| 3. Sync-safety | PASS |
| 4. Uniform abstraction | PASS |
| 5. Inertness | PASS |

Zero regressions. The Phase 7 refactor preserves all five canonical contracts.

---

## Outstanding concerns

No architectural questions block Phase 8. Three items flagged for future work, each with a clear home.

**MenuKitScreen centering math needs visual validation.** The minimum standalone-screen base class computes `min/max` across resolved bounds and offsets to center the layout. No standalone-screen consumer exists yet to exercise it visually. The natural validation moment is Phase 8, when the first foundational elements land and a simple standalone-screen demo becomes useful for testing. Not a concern for Phase 7's completion — Phase 5 contract verification does not exercise standalone screens.

**`MenuKitScreen` lacks `PanelOwner` implementation.** Standalone screens have no sync pass or visibility-change listener, so `setOwner` is not called for panels in a standalone screen. If a future event-on-visibility-toggle pattern arrives (e.g., for animating panel appearance/disappearance), `MenuKitScreen` will implement `PanelOwner` at that time. Flagged as a conscious defer, not a gap.

**Phase 8 keyboard input routing.** `MenuKitScreen` does not currently route `keyPressed` to elements. No current element needs keys. When Phase 8's `Toggle` / `Checkbox` / `Radio` / future `TextField` land, keyboard routing in `MenuKitScreen` (and possibly `MenuKitHandledScreen`) becomes relevant. This is Phase 8's design work, not a Phase 7 gap.

---

## Handoff notes for Phase 8

Phase 8 builds foundational elements (per the palette): Toggle, Checkbox, Radio/RadioGroup, Divider, Icon, ItemDisplay, ProgressBar, Tooltip. Six practical items from Phase 7's landing.

**1. The element signature is stable.** `PanelElement` + `RenderContext` is the shape every new element implements. `Button` and `TextLabel` are the reference implementations for interactive and render-only cases. Use `ctx.isHovered(childX, childY, width, height)` or the default `isHovered(ctx)` rather than hand-rolling hover tests.

**2. The HUD-specific element classes are still in place.** `MKHudText`, `MKHudItem`, `MKHudBar`, `MKHudSlot`, `MKHudIcon` implement `PanelElement` but are HUD-flavored (their builders live on `MKHudPanel`). Phase 8 graduates `MKHudItem → ItemDisplay`, `MKHudBar → ProgressBar`, `MKHudIcon → Icon` per the palette; the HUD builder methods (`.item()`, `.bar()`, `.icon()`) retarget to the new core elements. `MKHudText` gets subsumed into a scale-variant of the core `TextLabel`. `MKHudSlot` likely becomes an `ItemDisplay` variant that renders a hotbar-style sprite background.

**3. `MenuKitScreen` is the validation target for foundational elements.** Standalone screens with foundational elements are the first real test of whether the generalization holds visually. Phase 8 should build at least one minimal standalone screen (even a throwaway in the dev module) to exercise element composition, layout, and visual correctness. This also validates the centering math flagged above.

**4. The verification harness runs after every phase.** The `/mkverify` suite is permanent infrastructure. Phase 8's completion criteria should include running the five contracts and capturing evidence in the Phase 8 report. Consistent with the new pattern.

**5. `MenuKit.init()` wires the harness.** If Phase 8 adds initialization needing early registration, add it before the harness init line — the harness's MenuType registration happens in `init()` and must stay early so the MenuType is available to other subsystems.

**6. DEFERRED.md captures the Phase 7 consumer-facing API changes.** When Phase 11 consumer refactors begin, those notes tell IP / shulker-palette / sandboxes what signatures changed (`MenuKitSlot` constructor, `panel.getGroups()` removal, `PanelElement.render` signature, `MKHudElement` → `PanelElement`, HUD builder `.custom()` signature). Worth a pass when Phase 11 kicks off.

---

## What comes next

Phase 8: foundational elements. Design doc per element before code per the brief. Target palette: Toggle, Checkbox, Radio/RadioGroup, Divider, Icon, ItemDisplay, ProgressBar, Tooltip. Implementation follows the pattern established in Phase 7 — design, review, implement, verify contracts.

Phase 7's canonical documents (THESIS, CONTEXTS, PALETTE) continue to govern. The element palette drives Phase 8's scope; the thesis drives its restraint; the contexts document drives per-context behavior decisions.

Phase 7 is locked.
