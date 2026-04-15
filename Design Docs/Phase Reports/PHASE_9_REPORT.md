# Phase 9 Report — Audit-Surfaced Specializations

Phase 9 ships the two specializations named in the Phase 8 handoff brief: icon-only Button (`Button.icon(...)`) and state-linked Toggle (`Toggle.linked(...)`). Both follow a factor-then-specialize pattern — factor the parent element into protected hooks first, then add the subclass factory that overrides those hooks. All five canonical contracts verify at the Phase 9 endpoint.

Light phase relative to Phase 8. Two specializations, four implementation commits, plus two infrastructure commits that surfaced as natural consequences of the specialization work.

---

## What was done

**Two specializations shipped:**

1. **`Button.icon(x, y, size, Identifier, onClick)` + supplier overload** — square button with a centered sprite, 2px panel-border inset, ~40% alpha dim when disabled. Returns a package-private `IconButton` subclass overriding `renderContent` to paint a sprite via direct `blitSprite`. Fixed and supplier sprite forms ship uniformly per Convention 2.

2. **`Toggle.linked(x, y, w, h, BooleanSupplier, Runnable)`** — consumer-owned-state Toggle variant. Supplier drives render every frame; Runnable fires on user click so consumer can update their state. Returns a package-private `LinkedToggle` subclass overriding both state hooks.

**Factoring pre-work on each parent element:**

- **Button** gained protected `renderBackground(ctx, sx, sy)` and `renderContent(ctx, sx, sy)` hooks. `render()` itself is now `final` and orchestrates hover-state update, background paint, content paint, and tooltip dispatch in that order. Phase 8's customization-verification question is resolved by this factoring — consumer subclasses can override either hook independently.

- **Toggle** gained protected `currentState()` and `applyState(boolean)` hooks. `applyState` commits a state transition **atomically** — it both commits internal state (base) and fires the consumer callback. A private `toggleTo` orchestration helper handles short-circuit-on-same-state and delegates to `applyState`. `render()` caches `currentState()` once per frame as a local, per the documented contract.

**Stable-extension-point contract formalized** for all four hooks (`renderBackground`, `renderContent`, `currentState`, `applyState`). Their signatures and semantic contracts are maintained across MenuKit versions; consumer subclasses can rely on them.

**Two design docs drafted, reviewed, locked** — both at `Design Docs/Element Design Docs/`:
- `BUTTON_ICON_DESIGN_DOC.md`
- `TOGGLE_LINKED_DESIGN_DOC.md`

**Infrastructure additions (landed alongside specialization work):**

- `/mkverify elements` subcommand opens a dedicated element-demo screen. New `ElementDemoHandler` (one panel, no slots, no player-inventory panel) and `ElementDemoScreen` paired as sibling infrastructure to the existing contract-verification harness. Used for the final Phase 9 verification round and continues to serve Phase 10+ visual work.

- `MenuKitHandledScreen.computePanelSize` now factors in element bounds, not just slot-group dimensions. Latent architectural bug: panels with only elements (enabled by Phase 7's context generalization but never exercised until the demo screen) computed to just `2 * PANEL_PADDING` — effectively invisible with elements rendering below the panel background. Element bounds now contribute to max-width/height alongside slot groups. Existing inventory-menu panels unaffected (always have slot groups).

- `MenuKitHandledScreen.renderLabels` skips the vanilla "Inventory" label when the handler has no panel with id `"player"`. Screens that deliberately omit the player-inventory panel no longer show a floating label for content they don't render.

**PanelBuilder method count: still ~20.** No new builder methods in Phase 9 — specializations are accessed via `.element(Button.icon(...))` and `.element(Toggle.linked(...))` per the Phase 9 no-new-builder-method principle.

---

## Commits

Six commits on main across Phase 9:

```
9e2efc7 MK: Phase 9 — Toggle.linked factory + LinkedToggle subclass
17f87d8 MK: Panel size factors in elements; skip inventory label w/o player panel
679aa88 MK: /mkverify elements — dedicated visual-demo screen
ab52697 MK: Phase 9 — Toggle state-handling factoring + design doc
fc0a623 MK: Phase 9 — Button.icon factory + IconButton subclass
8c797f7 MK: Phase 9 — Button.render factoring + design doc
```

Each commit independently compilable, bisectable, and visually verified before commit.

---

## Deviations from the plan

None substantive in the locked designs; both specializations shipped as the advisor-reviewed design docs specified.

Two mid-design adjustments worth naming:

**Button.icon 2px inset confirmed after user review.** During visual verification, user suggested 3px might look better. Size-dependency analysis showed 3px would eat too much icon at the audit's small button sizes (9×9 shulker-palette, 11×11 sandboxes). Held at 2px fixed default; configurable inset deferred until Phase 11 surfaces demand.

**Toggle.linked callback type `Runnable`, not `Consumer<Boolean>`.** The draft design proposed `Consumer<Boolean>` for consistency with base Toggle's callback. Advisor pushed back: Toggle.linked doesn't own the state — the consumer does, and already knows what they want to flip. Passing `newState` through the callback is redundant in the common case and doesn't generalize when the supplier returns something computed rather than a raw field. Runnable is the cleaner primitive. If Phase 11 consumer refactors reveal genuine demand for new-state-in-callback, `Runnable → Consumer<Boolean>` is a one-field migration.

---

## Surprises

**Convention 5 and callback-fire orchestration coupled through Toggle.linked's Runnable decision.** Draft had `toggleTo` firing `onToggle.accept(newState)` after `applyState` committed — fine when callback types matched between base and linked. The Runnable switch revealed that `toggleTo` couldn't uniformly fire "the callback" anymore. Resolution: fold callback-fire into `applyState` itself. Expanded applyState contract to "commit state AND fire the consumer callback" — both happen atomically because they represent the same "transition is real now" moment. The factoring now honors an architectural truth that the original design implied independence for.

**computePanelSize had a latent bug that only surfaced with element-only panels.** Phase 7 generalized Panel to be context-neutral; element-only panels were enabled but never exercised until the `/mkverify elements` demo screen built one. The panel computed to tiny default size with elements rendering outside its visual background. Fixed by making elements contribute to panel dimensions alongside slot groups.

**The `/mkverify elements` detour was small and high-value.** ~15 minutes of infrastructure (two new files, command wiring, MenuType registration) produced a dedicated visual-testing surface that eliminated the "test elements clashing with container slots and player inventory" UX friction that had been accumulating across multiple Phase 9 verification rounds. Permanent fixture for Phase 10+ visual work.

---

## Decisions not in the brief

### Convention 5 refinement — factory methods for specializations

Phase 8's Convention 5: *"No factory methods except for direction-choice cases (Divider is the sole exercise)."*

Phase 9 specializations (`Button.icon`, `Toggle.linked`) are factory methods. They are structurally different from preset-value shortcuts and warranted a refined convention:

> *Factory methods are permitted when they return a specialization subclass with structurally different construction — different fields, different render behavior, different rendering semantics. Factory methods are NOT permitted as preset-value shortcuts over the primary constructor. The distinguishing test: does the factory return a different concrete type, or the same type with different default values? Different type = factory permitted; same type = use the constructor.*

Application:
- `Button.icon(...)` — returns `IconButton` subclass with different `renderContent`. **Permitted.** ✓
- `Toggle.linked(...)` — returns `LinkedToggle` subclass with overridden state hooks. **Permitted.** ✓
- `Divider.horizontal(...)` / `Divider.vertical(...)` — direction-choice case; already blessed in Phase 8. ✓
- Hypothetical `Button.primary(...)` (same class, preset style) — **not permitted**; use the constructor.

**Locked in Phase 9** and carried into the memory file for Phase 10-12.

### Stable extension points as a formal concept

Phase 8 addressed the Button customization question with "non-final class, non-final methods; consumer subclasses at their own risk." Phase 9 elevated this: the four new hooks (`renderBackground`, `renderContent`, `currentState`, `applyState`) are formally documented as **stable consumer-facing extension points** with signatures and semantic contracts maintained across MenuKit versions. Consumer subclasses can rely on them; internal refactoring is bounded by the stability promise.

This is an architectural commitment, not just documentation. Phase 10-12 decisions about Button and Toggle refactoring must respect these hooks. Future element specializations that follow the factor-then-specialize template should make the same stability commitment when introducing protected hooks.

### applyState's expanded atomicity contract

Phase 9 discovered through design review that **state commit and callback notification are architecturally coupled** — they happen together at the same "transition is real now" moment. The expanded `applyState` contract reflects this:

> *Commits a state transition. Subclasses define what "commit" means for their state-ownership model. Called after the short-circuit no-op check passes. Implementations should be atomic — the state transition and the callback notification are conceptually a single event.*

Base Toggle's implementation: mutate field + fire Consumer<Boolean>. LinkedToggle's: fire Runnable (no internal commit). Both uphold the atomicity contract.

### Factor-then-specialize as a repeatable template

Both Phase 9 specializations followed the same structural pattern:
1. Factor the parent element into protected hooks capturing the variation points.
2. Mark the parent class non-final; hooks are `protected`.
3. Add a static factory on the parent that returns a package-private subclass overriding the hooks.
4. Document the hooks as stable extension points.

This is **a proven template for future element specializations.** If Phase 11 reveals demand for `Checkbox.linked`, `Radio.linked`, icon-only variants of other elements, or consumer-side specializations, the template is mechanical. Phase 12 documentation should capture this pattern explicitly — it's the shape of how MenuKit elements grow variants without API bloat or convention violations.

### Self-healing behavior as a documented property

Toggle.linked has an emergent property worth naming: if a consumer's callback fails to update their state, the next frame's render reads the supplier and shows the unchanged state — the toggle visually snaps back. Documented as "self-healing" in the `Toggle.linked` factory javadoc. Consumers reaching for the linked variant see it right where they make the decision to use it; base Toggle users don't need to see it (base's internal state can't diverge from itself).

---

## Contract verification evidence

All five contracts PASS at the Phase 9 endpoint.

### 1. Composability

```
[Verify.Composability] Phase A result — 0 MK / 46 vanilla slots, cobble rejected on 46, diamond accepted on 41
[Verify.Composability] Phase B result — 46 MK / 0 vanilla slots, cobble rejected on 46, diamond accepted on 42
[Verify.Composability] VERDICT — mixin fired on both vanilla (46 slots) and MK (46 slots) slot types; global cobblestone filter applied uniformly
```

**PASS.**

### 2. Vanilla-slot substitutability

```
[Verify.Substitutability] Structural: 46/46 slots pass `instanceof Slot` (46 are MenuKitSlot)
[Verify.Substitutability] Sample slot — class=com.trevorschoeny.menukit.core.MenuKitSlot instanceof Slot=true instanceof MenuKitSlot=true
[Verify.Substitutability] VERDICT — all 46 MK slots pass `instanceof Slot`, and Slot.getItem RETURN mixin fires on MK slots with the composed return value (including inertness-driven EMPTY for any hidden panel)
```

**PASS.**

### 3. Sync-safety

```
[Verify.SyncSafety] Hidden panel covers flat slot range [6..10)
[Verify.SyncSafety] iter 0..9 — all target=reported, desync=0 (10 iterations)
[Verify.SyncSafety] Post-stress (visible) — slot contents: 6=minecraft:diamondx16 7=minecraft:diamondx8 8=minecraft:diamondx2 9=EMPTY
[Verify.SyncSafety] VERDICT — 10 toggles, 0 inconsistencies. PASS — the protocol's view stayed consistent with visibility.
```

**PASS.**

### 4. Uniform abstraction

```
[Verify.Uniform] VERDICT — same findGroup() API used against both vanilla (InventoryMenu) and MenuKit (MenuKitScreenHandler) handlers; both return Optional<SlotGroupLike>, concrete implementations (VirtualSlotGroup vs SlotGroup) transparent to caller
```

**PASS.**

### 5. Inertness

```
[Verify.Inertness] HIDDEN  slot 6-9 — getItem.empty=true active=false mayPlace(DIAMOND)=false mayPickup=false isInert=true → OK (fully inert)
[Verify.Inertness] Phase A result — 4/4 hidden slots fully inert
[Verify.Inertness] VISIBLE slot 6-9 — getItem.empty=false active=true mayPlace(DIAMOND)=true mayPickup=true isInert=false
[Verify.Inertness] Phase B result — 4/4 slots flipped to active+non-inert
[Verify.Inertness] VERDICT — inertness holds: hidden slots report fully inert (4/4 OK); visible slots flip back (4/4 restored)
```

**PASS.**

### Summary

| Contract | Result |
|---|---|
| 1. Composability | PASS |
| 2. Vanilla-slot substitutability | PASS |
| 3. Sync-safety | PASS |
| 4. Uniform abstraction | PASS |
| 5. Inertness | PASS |

Zero regressions across Phase 9's two specializations, two factoring refactors, and two infrastructure additions.

---

## Outstanding concerns

Small items flagged for future phases. None block Phase 10.

**`"player"` magic string for inventory-label gating.** `MenuKitHandledScreen.renderLabels` conditionally draws the vanilla inventory label by checking `panelBounds.get("player") != null`. Relies on a convention that the player-inventory panel has id `"player"` — which is already the existing convention for the `inventoryLabelY` positioning code. Not a new coupling, but the magic string surface grew slightly. Worth revisiting if Phase 11 consumer refactors reveal cases where the convention is awkward.

**`disabledWhen` on `Button.icon` and `Toggle.linked`.** Neither factory has a `disabledWhen` overload — matching the posture of their parent element's builder-method forms. Consumers wanting a disabled-predicate subclass the parent class directly using the new hooks. Cheap now that the hooks exist; flag for Phase 11 if consumer demand surfaces.

**Runnable → Consumer<Boolean> migration signal for Toggle.linked.** If Phase 11 consumer refactors reveal that consumers repeatedly query the supplier inside their Runnable callback to determine new-state (e.g., `() -> { boolean newVal = !config.autoSort; config.autoSort = newVal; doSomethingWith(newVal); }`), that's evidence for migrating the callback type. Until then, Runnable is the cleaner primitive.

**Specialization demand signals for Phase 11:**
- `Checkbox.linked` / `Radio.linked` — if consumer mods need them, template is mechanical.
- Icon-only variants of other elements — if consumer mods surface the pattern, the render-hook factoring template applies.
- Animated Toggle transitions — not currently scoped; surface only if real demand appears.

**PanelBuilder method count held at ~20.** No new builder methods in Phase 9. Phase 12 consolidation evaluation still pending; count is stable.

---

## Handoff notes for Phase 10

Phase 10 builds the **injection patterns** — the consumer-facing machinery for decorating vanilla screens with MenuKit elements. Per the Phase 5 audit findings, three of the four consumer mods (sandboxes, agreeable-allays, shulker-palette) fit the "inject UI into vanilla screens" pattern rather than "build your own screen." Currently MenuKit has first-class "build your own screen" (`MenuKitScreenHandler.builder`) but nothing equivalent for "decorate a vanilla screen."

**Key design questions for Phase 10:**
- What's the mixin hook shape for vanilla-screen decoration?
- How does predicate-based visibility work (by context, region, or runtime state)?
- Do decorated vanilla screens get the same element palette as MenuKit-native screens, or a subset?
- How does the consumer identify the vanilla screen they want to decorate?

The old architecture provided this via `MKPanel.builder().showIn(...)` + `MenuKit.buttonAttachment()`. The new architecture has neither a mixin hook for this nor a documented consumer pattern. Phase 10 decides whether the library ships the machinery or documents a consumer recipe, per library-not-platform discipline.

**Phase 9 context that shapes Phase 10:**
- The `/mkverify elements` demo surface is available for visual testing Phase 10's injection work against a known-good MenuKit-native baseline.
- The factor-then-specialize template established in Phase 9 applies to any element variants Phase 10 introduces for injection contexts.
- The stable-extension-point contract applies to any new hooks Phase 10 adds to existing elements or introduces on new injection-pattern classes.

**Verification harness runs at Phase 10 completion** per established cadence. `/mkverify` subcommands capture evidence; Phase 10 report carries it.

**DEFERRED.md captures Phase 9 API consumer-facing changes** under the Post-MenuKit/Phase 11 section. Not Phase 9 scope but worth flagging for Phase 11's consumer refactors:

- Two new static factories ship: `Button.icon(...)` (2 forms) and `Toggle.linked(...)`.
- Button and Toggle gained protected extension hooks (`renderBackground`/`renderContent` on Button; `currentState`/`applyState` on Toggle). Consumer subclasses can use them.
- `Button.render(RenderContext)` is now `final`. Consumer subclasses that previously overrode `render()` directly (none known in-tree; all consumer mods disabled) need to migrate to the protected hooks. This is the blessed path.
- `MenuKitHandledScreen.computePanelSize` now factors in elements. Any panel with elements extending beyond its slot-grid now renders at the larger size. Not breaking for existing consumers (their panels have slot groups that already drive size).
- `MenuKitHandledScreen.renderLabels` skips the inventory label when no `"player"` panel exists. Existing consumers (all have player panels) unaffected.

---

## What comes next

Phase 10: injection patterns. The largest remaining pre-consumer-refactor design piece, based on the Phase 5 audit's revealed majority use case.

Phase 8's canonical documents (THESIS, CONTEXTS, PALETTE) continue to govern. Convention evolution stable — six conventions with the Phase 8 post-construction-setter refinement and the Phase 9 factory-for-specialization refinement, both narrow extensions rather than fundamental revisions.

**Phase 9 is locked.** Two audit-surfaced specializations ship. Two factoring refactors establish stable extension-point contracts. Two infrastructure fixes surfaced as natural byproducts. Five canonical contracts verify. Zero regressions. The library now has the specializations its palette promised and a repeatable template for growing more.
