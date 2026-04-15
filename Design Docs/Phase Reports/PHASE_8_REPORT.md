# Phase 8 Report — Foundational Elements

Phase 8 builds out the foundational element palette: Icon, Divider, ItemDisplay, ProgressBar, Toggle, Checkbox, Radio / RadioGroup, Tooltip. Plus a retroactive supplier variant on TextLabel. All eight new elements ship; all five canonical contracts verify against the Phase 8 endpoint.

---

## What was done

**Eight element design docs drafted, reviewed, locked.** Each went through at least one draft-review-iterate cycle. All docs live at `Design Docs/Element Design Docs/`.

**Eight new element classes implemented:**

1. `core/Icon.java` — sprite rendering primitive with fixed and supplier variants
2. `core/Divider.java` — horizontal/vertical separator factories
3. `core/ItemDisplay.java` — generalized from `hud/MKHudItem` (now deleted)
4. `core/ProgressBar.java` — generalized from `hud/MKHudBar` (now deleted)
5. `core/Toggle.java` — first mutable-state exception element
6. `core/Checkbox.java` — auto-sizing from label, vanilla `icon/checkmark` sprite
7. `core/Radio.java` + `core/RadioGroup.java` — first cross-element composition
8. `core/Tooltip.java` — standalone info box (Form B)

**Form A tooltip setters added to five elements:** Button, Toggle, Checkbox, Radio, Icon each gained `.tooltip(Component)` and `.tooltip(Supplier<Component>)` post-construction setters, plus render-body additions that call `setTooltipForNextFrame` when hovered.

**Retroactive TextLabel supplier variant** added (both-forms constructors per Convention 2; one new builder method).

**HUD subsystem cleanup:** `MKHudItem` and `MKHudBar` deleted; HUD builder `.item()` and `.bar()` methods retarget to core `ItemDisplay` and `ProgressBar`. The HUD subsystem is now one class closer to fully subsumed (MKHudSlot remains as the lone HUD-specific element-like class; MKHudNotification remains intentionally as the animated notification).

**PanelBuilder final method count: ~20.** Four past the originally-named comfortable threshold of 15; within broader tolerance. Pocketed for Phase 12 consolidation evaluation.

---

## Deviations from the plan

None substantive. Each element shipped per its locked design doc with the feedback-incorporated refinements.

Two small deviations worth naming for completeness:

**Form A supplier-only draft rejected.** The Tooltip design draft initially proposed shipping only `Supplier<Component>` on Form A setters. Advisor feedback rejected this — Convention 2 requires both forms uniformly. Final implementation ships both fixed and supplier forms on each of five elements. No convention refinement was blessed for supplier-only variants.

**Phase 9 factoring hooks retroactively removed from Toggle.** Toggle initially shipped with `protected boolean currentState()` and `protected void applyState(boolean)` hooks anticipating Phase 9's `Toggle.linked(...)` variant. Advisor feedback: Phase N ships what Phase N uses. Hooks removed from Toggle; Checkbox (designed later) never shipped them. Phase 9 will factor the hooks when building the linked variants, not speculatively now.

---

## Surprises

**The conventions held.** Across eight elements, no convention required fundamental revision. All refinements narrowed rather than expanded the conventions, and every refinement was scoped to a specific case rather than becoming a blanket exception.

**Vanilla sprite verification paid off twice.** For Checkbox, verification found `icon/checkmark` (9×8 RGBA) — perfect for the element, Convention 6 satisfied without shipping any asset. For Radio, verification confirmed no suitable vanilla sprite exists; drawn fill is the documented fallback with reasoning captured. Both outcomes are right; skipping verification in either direction would have been a mistake.

**`setTooltipForNextFrame` API change in 1.21.11.** What older versions called `renderTooltip(Font, Component, int, int)` was renamed to `setTooltipForNextFrame` in 1.21.11. Caught at compile time during Tooltip Form A implementation; fixed immediately. Worth noting because mapping-version changes in 1.21.11 are still catching refactors.

**Consumer-API surface grew less than expected.** PanelBuilder ends Phase 8 at ~20 methods — four over the comfortable threshold but still workable. Complex-configuration elements (ProgressBar) ship only common-case builder overloads per the Convention 4 refinement, which kept the count bounded.

---

## Decisions not in the brief

The phases brief named Phase 8's scope but not specific architectural positions. The following positions were established during Phase 8 and will shape Phase 9+.

### Convention evolution

Six conventions, established through Icon and pressure-tested through every subsequent element:

1. **Constructor shape** `(childX, childY, [width, height,] content, [callback])` — dimensions optional for content-sizing elements; single-int size for square-only elements; trailing coordinator-reference slot when elements reference external coordinators (Radio).
2. **Supplier variants** for variable content (data-vs-configuration distinction — data is supplier-capable, configuration is fixed). Both fixed and supplier forms ship uniformly.
3. **Render-only elements inherit defaults** for `isVisible`, `mouseClicked`, `isHovered`. Interactive elements override only what they need.
4. **One builder method per element**, with complex-configuration elements (≥4 config params) shipping common-case overloads only. Full configuration via `.element(new Element(...))`.
5. **No factory methods except for direction-choice cases** (Divider is the sole exercise).
6. **Vanilla textures for MenuKit's own default visuals** — verify vanilla first, use clever compositing where possible, ship custom textures only when genuinely necessary.

**New refinement:** *"Elements may expose post-construction configuration setter methods for optional, orthogonal features (e.g., `.tooltip(...)`). Setters are intended to be called exactly once during the construction chain. They do not fire callbacks or trigger re-renders; they are not runtime state mutators. Features central to the element's behavior belong in the constructor, not as setters."*

### Architectural positions

- **Mutable-state exception scope** — Toggle, Checkbox, Radio (via RadioGroup). All three share the "element-owned boolean, state changes don't affect structural shape" justification. Exception is narrow; not extended elsewhere.
- **Coordinator-as-plain-object precedent** — RadioGroup is not a PanelElement. Establishes the pattern for any future cross-element coordination (dropdowns, tab bars, segmented controls): coordinators are plain objects, not PanelElements. Preserves "Panel is the ceiling of composition" as load-bearing.
- **Customization verification outcome** — Button (and Toggle after the same check) are extensible via non-final class and non-final methods. Full render override works; partial customization requires duplication until Phase 9 factors `renderBackground` / `renderContent` hooks as part of icon-only Button work. No `AbstractPanelElement` helper shipped; pain is modest, defer until consumer evidence.

### Vanilla sprite decisions (Convention 6 empirical)

- **Checkbox** — uses vanilla `icon/checkmark` (9×8 RGBA). Ships no custom asset. Resource packs adapt.
- **Radio** — verified no suitable vanilla sprite (no `icon/dot`, `icon/radio`, or similar; `icon/unseen_notification` was semantically wrong). Uses drawn fill (4×4 centered square). Convention 6 N/A for this element.
- **Divider, ProgressBar** — pure fill rendering; Convention 6 N/A (no texture involved; same pattern confirmed across multiple elements).
- **Toggle** — uses MenuKit's existing PanelStyle vocabulary (RAISED/INSET) rather than custom switch sprite. Visual legibility accepted as trade-off; fallback path to indicator or custom sprite documented if in-game testing reveals ambiguity.

### Stabilizing but not-yet-consolidated styling

`0xFF404040` (dark gray text on panel) appears in Divider, Checkbox, Radio, Tooltip Form B. Forming a de facto "MenuKit default text-on-panel color." **Pocketed for Phase 12** — a shared `StyleDefaults` or similar constants class would consolidate these. Not Phase 8 scope.

---

## Contract verification evidence

Five contracts, all PASS. Full log evidence from the Phase 8 endpoint:

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

Zero regressions across Phase 8's eight new elements and five tooltip-setter additions.

---

## Outstanding concerns

No architectural questions block Phase 9. Four items flagged for future phases, each with a clear home.

**Toggle visual legibility.** RAISED/INSET differentiation without a custom sprite was an explicit Phase 8 trade-off. Reconsideration trigger: in-game testing or Phase 11 consumer feedback revealing isolated unlabeled toggles are confusingly button-like. Fallback: subtle internal indicator or custom sprite on real demand. Not blocking anything; captured as a Phase 9+ polish opportunity.

**PanelBuilder method count.** Final 20 methods, past the comfortable 15 threshold but workable. Phase 12 evaluates whether consolidation (sub-builders, grouped accessors) is worth doing. Not Phase 8 scope.

**Shared styling constants.** `0xFF404040`, `0xFF808080` (disabled label), `0xFF606060` (indicator) each appear in multiple elements. Consolidation candidate for Phase 12.

**MKHudSlot remaining.** After ItemDisplay and ProgressBar subsumed MKHudItem and MKHudBar, MKHudSlot is the only HUD-specific element-like class. A hotbar-sprite-background ItemDisplay variant is plausibly Phase 9 or 10 work if shulker-palette or another consumer reveals demand.

---

## Handoff notes for Phase 9

Phase 9 builds audit-surfaced specializations. Per the palette: icon-only Button, state-linked Toggle, icon-swap-by-state (covered by Icon's supplier variant — already shipped), dynamic tooltip (covered by Tooltip Form A — already shipped), dynamic text content (covered by TextLabel supplier variant — already shipped).

**Three concrete specializations remain for Phase 9:**

1. **Icon-only Button.** Palette sketch: `Button.icon(x, y, size, Identifier sprite, Consumer<Button> onClick)`. Phase 9's first work: factor Button's `render()` into protected `renderBackground(ctx, sx, sy)` and `renderContent(ctx, sx, sy)` hooks. Then `Button.icon(...)` returns a subclass overriding `renderContent` to paint a centered Icon. Consumer-side Button subclasses get the same hooks as a free side effect — the customization-verification question answered in Phase 8 is satisfied by Phase 9's factoring.

2. **State-linked Toggle (`Toggle.linked`).** Palette sketch: `Toggle.linked(x, y, w, h, BooleanSupplier state, Runnable onToggle)`. Phase 9 factors protected `currentState()` / `applyState(boolean)` hooks on Toggle at the time of building the linked variant. Persistence framing captured in DEFERRED.md — MenuKit does not ship a persistence abstraction; state-linked is the answer.

3. **State-linked Checkbox is NOT Phase 9 scope** unless real consumer demand surfaces during Phase 9 or later. The palette names `Toggle.linked` as the state-linked specialization; Checkbox inherits the pattern conceptually but doesn't need a `.linked` variant shipped until demand is real. Shipping it speculatively would violate the palette-driven scope discipline that has held across Phase 8. Phase 9's concrete work is **two specializations** (icon-only Button, state-linked Toggle), not three.

**No new builder methods in Phase 9.** Per the principle established in Phase 8's ProgressBar review: Phase 9 specializations are factory variants on existing element types, not new elements. They should be accessed via `.element(Button.icon(...))` and `.element(Toggle.linked(...))`, not via new builder methods like `.iconButton(...)` or `.linkedToggle(...)`. This keeps PanelBuilder's ~20-method count stable.

**Verification harness runs at Phase 9 completion.** Same pattern as Phase 7 and 8. `/mkverify` subcommands capture evidence; Phase 9 report carries it.

**DEFERRED.md captures Phase 8 API consumer-facing changes** under the Post-MenuKit/Phase 11 section — not Phase 8 scope but worth flagging for Phase 11's consumer refactors:

- Five elements gained `.tooltip(...)` setters (both fixed and supplier forms). Not breaking; new optional feature.
- TextLabel gained `Supplier<Component>` constructors. Not breaking; new optional feature.
- MKHudItem and MKHudBar deleted; consumers accessing via HUD builder `.item()` / `.bar()` keep working (methods produce ItemDisplay / ProgressBar internally). Consumers that directly imported MKHudItem or MKHudBar (none at time of writing) would break.
- `MKHudBar.Direction` moved to `ProgressBar.Direction` — breaking if any consumer imported the enum directly (none at time of writing).

---

## What comes next

Phase 9: audit-surfaced specializations. Three remaining pieces of concrete work (icon-only Button, state-linked Toggle, possibly state-linked Checkbox). The per-element design doc pattern continues.

Phase 8's canonical documents (THESIS, CONTEXTS, PALETTE) continue to govern. Convention evolution is stable — six conventions with a handful of narrow refinements, no fundamental revisions.

**Phase 8 is locked.** Eight foundational elements ship. Five canonical contracts verify. The library now has the primitive set its thesis promised.
