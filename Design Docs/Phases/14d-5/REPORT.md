# Phase 14d-5 — Dropdown — REPORT

**Status: closed. Round 1 design + 1 mid-implementation pivot + smoke green. Single commit.**

Bespoke single-selection dropdown control. No vanilla wrap (vanilla 1.21.11 ships `CycleButton` for in-place cycling, no popover-list widget). Architectural patterns inherited from `CommandSuggestions.SuggestionsList` (chat command autocomplete) for popover dispatch + scroll, plus `BelowOrAboveWidgetTooltipPositioner` for edge-flip placement. Two new PanelElement primitives shipped — `hitTest` + `getActiveOverlayBounds`.

---

## What shipped

**Library (`menukit/`):**

| File | Change | LOC |
|---|---|---|
| `core/Dropdown.java` (new) | Generic `<T>` PanelElement; builder; trigger render with chevron + hover; popover render with edge-flip placement, hover/selection highlights, internal scroll, scrollbar; pass-1 overlay claim via `getActiveOverlayBounds`; pass-2 trigger dispatch via default `hitTest` | ~675 |
| `core/PanelElement.java` | `hitTest(double, double, int, int)` default method (extension hook for interaction beyond layout bounds); `getActiveOverlayBounds()` default method (exclusive-claim modal-area primitive — parallel to M9 panel-level click-eat at element level) | +110 (95→336) |
| `screen/MenuKitScreen.java` | Two-pass click + scroll dispatchers — pass 1: overlay-claim exclusive; pass 2: existing hit-test (unchanged semantic for default-impl elements) | +45 |
| `screen/MenuKitHandledScreen.java` | Same two-pass refactor | +40 |
| `inject/ScreenPanelAdapter.java` | Same two-pass refactor (region-based + lambda-based both inherit) | +20 |
| `verification/ContractVerification.java` | M18 Dropdown builder validation probe (15 cases — required fields, null guards, empty-list throws, builder fluency, successful build smoke); `runAll` invocation | +210 |

**Validator (`validator/`):**

| File | Change | LOC |
|---|---|---|
| `scenarios/smoke/DropdownSmokeScreen.java` (new) | Standalone `MenuKitScreen` subclass — header + supplier-driven Consumer-state TextLabel + Reset Button + Back-to-Hub Button + Dropdown (declared LAST per render-order discipline) with 12 items to trigger scrollbar | ~165 |
| `scenarios/smoke/MenuKitSmokeState.java` | `dropdownSelection` field (volatile String, default "Option 03") | +12 |
| `scenarios/hub/HubHandler.java` | "Dropdown" entry added at TOP of Hub list (newest-first per convention) | +9 |

**Total:** ~+1290 LOC across 9 files. Heavier than original ~+650 LOC estimate due to the mid-implementation pivot from `hitTest`-claim-everything to the structural overlay primitive (added ~250 LOC across PanelElement, Dropdown, and 3 dispatchers; saved ~70 LOC by simplifying Dropdown's mouseClicked routing).

---

## Smoke results

`DropdownSmokeScreen` (opened from Hub → Dropdown entry, post Test-button click that ran M18):

| Check | Outcome |
|---|---|
| Click trigger → popover opens; chevron flips to ▲; pressed-look on trigger | ✓ |
| Click item in popover → consumer fires; popover closes; trigger label updates next frame; supplier-driven TextLabel below also updates | ✓ |
| Click trigger again while open → popover closes | ✓ |
| Edge-flip placement — popover opens upward when not enough room below | ✓ (Trevor's smoke screen had popover edge-flip; verified) |
| Mouse-wheel scroll inside popover — 12 items + maxVisibleItems=8 → 8 visible at once; scrollbar thumb tracks position | ✓ |
| Selection highlight — current selection's row rendered with brighter overlay than hover | ✓ |
| Hover highlight — only the row under cursor lights up | ✓ |
| **Click on popover item that visually overlaps Reset button (edge-flipped popover) → ONLY item selected; Reset NOT fired** | ✓ (the architectural pivot — see §"Mid-implementation pivot" below) |
| Reset button → consumer-side write (`dropdownSelection = "Option 03"`); next click on trigger shows updated highlight | ✓ |
| Back-to-Hub navigates correctly | ✓ |
| Selection persists across screen close/reopen | ✓ (static state field) |
| Truncate-by-width with ellipsis on overlong item labels | ✓ (not stress-tested; default item labels fit) |

Trevor's smoke verdict: *"Perfect, works great now! Good job."*

---

## Round-1 verdict outcomes

**8 advisor sign-offs, 2 principled divergences validated:**

| Q | Topic | Outcome |
|---|---|---|
| Q1 | Trigger shape — Button-styled with chevron, owned-by-Dropdown | Sign off |
| Q2 | Open-state internal — `volatile boolean open` (mirrors Slider's `dragging`) | Sign off |
| Q3 | Auto-flip placement — open below; flip above if no room; `Mth.clamp` on X-axis | Sign off |
| Q4 | Internal scroll over ScrollContainer composition — popover isn't a Panel | **Divergence sign-off** ((c) parallel implementation; refactor to scroll-helper deferred to N+1 popover-shape consumer) |
| Q5 | `hitTest` primitive over M9 onOutsideClick extension | **Divergence sign-off** (works in both contexts; smaller infra; generalizes) |
| Q5 sub | Dismiss-without-consume semantic | (Originally) sign off — **subsequently revised mid-implementation: deferred** |
| Q6 | Selection lens — `Supplier<T>` + `Consumer<T>`, T.equals identity | Sign off |
| Q7 | Keyboard defer — mouse-only v1; parent-screen-close-on-Esc documented | Sign off |
| Q8 | Items immutable post-build; empty list throws at build (M18 contract) | Sign off |

---

## Mid-implementation pivot — `getActiveOverlayBounds` primitive added

**The most significant finding of this phase.**

Round 1 design + advisor sign-off committed to:
- `hitTest` primitive (existing surface area extension hook)
- Dropdown overrides hitTest to claim *every* click when popover open
- Dropdown's `mouseClicked` routes internally (in trigger / in popover / outside)
- Outside clicks → close popover, return false (dismiss-without-consume)

Trevor smoke-tested the first build and surfaced the bug: when popover edge-flipped above the trigger and visually occluded the Reset button, **clicking a popover item also fired Reset.** The popover wasn't structurally inert — `hitTest` semantics meant Dropdown got dispatched alongside Reset (whichever's `mouseClicked` returned true won), and the dispatcher's forward iteration order let Reset win when it claimed bounds inside the popover area.

My initial fix attempt: reverse element-iteration order in dispatchers (so last-declared element gets first dispatch crack). Trevor flagged this as a hack: *"It should be that everything behind the list panel thing is innately inert."*

**The architectural insight:** popover exclusivity is a *structural* concern (the dispatcher must drop the click for behind elements; element-level claims are insufficient). The cleanest abstraction is **M9-style click-eat at the element level**, not a panel level — a primitive that lets an element declare an exclusive screen-region.

The pivot:
1. **Reverted** reverse-iteration in all 6 dispatch points (the hack).
2. **Added** `PanelElement.getActiveOverlayBounds()` — element returns the screen-rect it claims as exclusive overlay; dispatcher pass 1 routes any input inside it solely to that element (no fallthrough regardless of `mouseClicked` return).
3. **Updated** Dropdown to declare its popover bounds as overlay when open; removed the hitTest-claim-everything hack; removed the dismiss-on-outside routing code.
4. **Two-pass dispatch** in all 6 dispatch points: pass 1 = overlay-claim (new); pass 2 = existing hit-test (unchanged for default-impl elements).
5. **Deferred outside-click-dismiss** to fold-on-evidence — vanilla `SuggestionsList` doesn't auto-dismiss either; click-trigger + Esc are the v1 dismiss paths.

The structural fix is cleaner than the iteration hack on three counts:
- **Solves clickthrough at the right level.** Click on popover item is dropped for behind elements at the dispatcher; doesn't depend on Dropdown's mouseClicked decision.
- **Local concern, local primitive.** Each element declares its own exclusive region; doesn't couple element design to global dispatch order.
- **Generalizes.** Future overlay-style elements (modal context menus, hover popups with embedded actions, expandable inline editors) opt in via the same hook.

Trevor's smoke verdict on the corrected build: *"Perfect, works great now!"*

**Round count:** technically zero rounds beyond round 1 (the advisor sign-off held for the original design); the pivot was an in-implementation finding, not a round-2 advisor cycle. Pattern: round 1 caught the easy decisions (lens shape, items immutability, scroll mechanism); smoke caught the hard architectural assumption (`hitTest` semantics aren't strong enough for true modal-area inertness).

---

## Calibration meta

**New standing rule generalized from the mid-implementation pivot:**

*Modal regions need structural exclusivity, not element-claim semantics.* When an element renders a transient interactive overlay outside its layout bounds (popover, context menu, action chip), the overlay region must be inert-behind STRUCTURALLY (dispatcher drops clicks for behind elements). Element-level claim semantics like "I'll consume this click if it's in my popover" don't prevent behind elements from also receiving it — both elements get dispatched, and consumption order/return-value decides who wins. The right abstraction is M9-style click-eat at the element granularity (`getActiveOverlayBounds`), parallel to M9's panel-level `tracksAsModal`.

**Heuristic:** when a region is supposed to be "inert behind it," ask "does the dispatcher structurally drop the click, or does it depend on an element returning the right value from `mouseClicked`?" The first is the right level; the second is a hack that breaks under edge-flip / overlap.

**Heuristics now in the calibration set (numbered for cross-phase reference):**

1. *Compounding mixins → wrong layer* (14d-1)
2. *Find vanilla's existing primitive before inventing one* (14d-2 / 14d-3 — discovery)
3. *Pre-empted dispatch owns responsibility* (14d-2.5)
4. *Audit existing surface before parallel one* (14d-2.7)
5. *Render order matters when wrapping vanilla widgets — manual in custom pipeline* (14d-3)
6. *Follow vanilla when wrapping — don't cut features inherited for free; don't add API vanilla doesn't have* (14d-4 — composition)
7. *Modal regions need structural exclusivity, not dispatcher-order tweaks or element-claim semantics* (14d-5 — bespoke composition)

**Process meta:** one in-implementation pivot beyond round 1; zero advisor rounds beyond round 1. The pivot was triggered by Trevor's smoke verdict catching an architectural assumption that round 1 didn't surface — a reminder that *smoke tests catch what design discussions can't* (especially edge-cases like edge-flipped popovers overlapping unexpected elements). Bigger LOC than estimated due to the structural-fix path being more invasive than the original element-only design.

**Lesson for future bespoke-composition phases:** when an element renders content outside its layout bounds, ask explicitly during round 1: "does this region need to be inert-behind structurally?" If yes, design via overlay primitive from the start. The `getActiveOverlayBounds` primitive shipped here generalizes — future popover/menu/overlay elements use it directly.

---

## Carried forward (deferred)

- **Outside-click-dismiss** — fold on evidence. Vanilla `SuggestionsList` doesn't auto-dismiss either; click-trigger-toggle + Esc-closes-screen are v1 dismiss paths. Implementation path if folded: pass-3 notification mechanism that fires on EVERY click and lets overlay-active elements respond; or external Pass-1 detection of cursor-not-in-overlay-while-open and notify the element.
- **Keyboard navigation** (arrow-keys cycle, Enter selects, Esc dismisses popover only without closing screen) — defer per Q7 advisor sign-off. No vanilla widget inherited keyboard from; bespoke implementation.
- **Multi-select / Listbox** — separate primitive (MultiselectDropdown). Fold on evidence.
- **Search-as-you-type / autocomplete** — separate primitive (Combobox). Deeper design surface (text input integration + filtered items list).
- **Custom popover anchor** (right-aligned, centered, anchored to other element) — defer; v1 left-aligns with trigger only.
- **Dynamic items list post-build** (`Supplier<List<T>>`) — defer; separate primitive shape (Combobox).
- **Programmatic open/close** (`dropdown.open()` / `dropdown.close()`) — defer per Q2; internal-state for v1.
- **Custom item rendering** (icons + multi-line items) — defer; v1 single-line text rows via `label.apply(item)`.
- **Group/section headers in popover** — defer; v1 flat items list only.
- **Multi-Dropdown panels** — design constraint: "last-declared wins Z-order" applies to multi-Dropdown panels. Future fold if multi-dropdown panels become common: hoist popover render to a separate `renderOverlay` pass on PanelElement (would resolve Z-order without consumer discipline). Not in v1.
- **`renderOverlay` pass for general overlay-Z-order** — same fold as multi-Dropdown above; current discipline = "Dropdown declared LAST among elements."
- **`getActiveOverlayBounds` reuse audit** — when a 2nd consumer of overlay primitive surfaces (e.g., context menu, hover popup with actions), validate the API shape against multi-overlay collision semantics + document any patterns that emerge.

---

## Next phase

**Phase 14 close → Phase 15 consumer migrations.** 14d palette is complete:
- 14d-1: dialogs (modal panels)
- 14d-2: ScrollContainer
- 14d-2.5: M9 Panel Opacity mechanism
- 14d-2.7: test surface cleanup (deleted /mkverify; Test button entry)
- 14d-3: TextField (wraps EditBox + lifecycle hooks)
- 14d-4: Slider (wraps AbstractSliderButton)
- 14d-5: Dropdown (bespoke composition + overlay primitive)

After Phase 14 close: consumer-side migrations off old panel/widget plumbing onto MenuKit primitives. Phase 15 is the validation that the palette is sufficient for real-world consumer needs.
