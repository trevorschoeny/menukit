# Phase 14d-2 — Close-out REPORT (ScrollContainer)

**Status: complete.** ScrollContainer shipped as a `PanelElement` viewport — clipping + scroll offset + scrollbar UI + minimal background, with content as pre-positioned `List<PanelElement>`. Single round + five fold-inline implementation findings; no round 2 needed. M8 thesis successfully reframed for runtime-clipping primitives without licensing animation framework or modal screen stacks.

---

## Executive summary

Phase 14d-2's deliverable was the scroll container per `PHASES.md` §14d. The architecturally load-bearing question was the container-shape conflict with M8's helper-not-container thesis: scroll container can't be a helper (clipping is fundamentally runtime; build-time positioned-element emission can't deliver it). Round 1 advisor verdicted (α-2) viewport — `PanelElement` with internal render dispatch over a viewport showing externally-positioned children.

The thesis reframe (advisor's narrowed framing): *"Panel is the ceiling of layout composition. PanelElements may have internal render dispatch over their own visuals OR over a viewport showing externally-positioned children. The M8 helper-not-container test applies to LAYOUT (build-time positioning); other runtime concerns (clipping, viewport scrolling) are honest sub-passes within an Element."* — narrow enough that animation framework, screen stacks, and sub-screens are not licensed.

What shipped:

- **`ScrollContainer.java`** — public `PanelElement` viewport with builder. Owns clipping (`GuiGraphics.enableScissor`/`disableScissor`), scroll offset (lens pattern via `DoubleSupplier` + `DoubleConsumer`), scrollbar UI (vanilla `container/creative_inventory/scroller` sprite + slot-style inset track), mouse-wheel scroll input, click-and-drag scrollbar. Children are pre-positioned `PanelElement`s — layout composed externally via M8.
- **`PanelElement.mouseScrolled` + `mouseReleased`** defaults — added alongside ScrollContainer; existing elements default false. The deferred work in DEFERRED.md ("PanelElement mouseReleased / mouseDragged hooks") is partially shipped — release plumbed; drag still polls per-frame from RenderContext.
- **`ScreenPanelAdapter.mouseScrolled` + `mouseReleased`** dispatch.
- **`ScreenPanelRegistry`** registers Fabric `allowMouseScroll` + `allowMouseRelease` hooks per-screen. Adds `dispatchModalScroll(...)` + `findModalAtPoint(...)` helpers.
- **Modal-scroll fold-inline** (carry-over from 14d-1's open finding) — `MenuKitModalMouseHandlerMixin.onScroll` updated from wholesale-eat to modal-aware: scroll inside modal bounds dispatches to the modal's adapter; outside is eaten. Same shape as `dispatchModalClick`.
- **`PanelRendering.renderInsetRect(graphics, x, y, w, h)`** — generalizes the existing slot-background pattern (1px shadow/highlight + medium gray fill, no outer border) to arbitrary dimensions. Used for the scrollbar track. Lighter than `PanelStyle.INSET`.
- **`ScrollContainer.viewportWidthFor(int outerWidth)`** — public helper returning `outerWidth - TRACK_WIDTH - SCROLLER_GUTTER`. Consumers use it to size content elements that fit inside the viewport without hard-coding the buffer arithmetic. Smoke uses the same helper.
- **V12 + V13 `/mkverify` probes** — V12 ScrollContainer math + builder validation; V13 modal-scroll dispatch helper. Aggregator now reports 13/13 contracts.
- **Design doc** at `Design Docs/Elements/SCROLL_CONTAINER.md` — full round-1 + implementation-findings record (~700 lines).
- **Phase 14d-2 REPORT** — this file.

---

## Architecture decisions

### (α-2) viewport shape

ScrollContainer is a `PanelElement` with internal render dispatch over a viewport. Children are pre-positioned by the consumer (typically via M8's `Column.at(...).build()`) and handed to ScrollContainer as `List<PanelElement>`. The viewport renders children with a translation by the current scroll offset and clips to its own bounds via vanilla's scissor primitive.

Rejected alternatives:
- **(α-1) Sub-Panel** — would replicate Panel's responsibilities (layout, auto-size, padding, background variants) inside a scroll-aware wrapper. Significant API growth for no clear payoff. Trevor's round-0 refinement caught this before drafting.
- **(α-3) M8-style helper** — clipping is runtime; build-time emission can't deliver. Strawman.

### Thesis reframe (narrowed)

M8's structural test ("does this primitive own a render pass?") was too strict when applied beyond layout. The narrowed reframe scopes the test to layout specifically:

> *"Panel is the ceiling of layout composition. PanelElements may have internal render dispatch over their own visuals OR over a viewport showing externally-positioned children. The M8 helper-not-container test applies to LAYOUT (build-time positioning); other runtime concerns (clipping, viewport scrolling) are honest sub-passes within an Element."*

What this licenses: ScrollContainer + future viewport-shaped primitives (e.g., a hypothetical "minimap" element with its own clipped sub-pass).

What this does NOT license: animation framework (THESIS scope ceiling), modal screen stacks, sub-screens, M8 helpers becoming runtime containers. Each would need its own evidence and own thesis-level conversation.

### Auto-compute `contentHeight` (Q3 verdict — pushback)

Round-1 implementer pull was consumer-declared `contentHeight`, framed as "M8 doesn't auto-size." Trevor's pushback: M8's framing applies to LAYOUT (where children go), not MEASUREMENT (how big the layout is). Panel already auto-measures via `getWidth`/`getHeight`; ScrollContainer measuring content height from children is the same shape, not M8-shape. Consumer-declared as default is a bug-magnet — silent miscalculation when content count changes. Auto-compute runs once at build time over a finite list.

Shipped: auto-compute default + `.contentHeight(int)` explicit override for genuine cases (trailing padding, capped scroll extent, supplier-driven content size).

### Vanilla primitives drive implementation

Per 14d-1's calibration heuristic (*find vanilla's existing primitive before inventing one*):

- **Clipping**: `GuiGraphics.enableScissor(int, int, int, int)` / `disableScissor()` — stack-based GL scissor test. Same primitive vanilla uses for `EditBox` clipping. No per-mod abstraction.
- **Scrollbar handle sprite**: `container/creative_inventory/scroller` + `container/creative_inventory/scroller_disabled` (verified in `CreativeModeInventoryScreen` bytecode).
- **Scrollbar track**: slot-background pattern (1px shadow/highlight + medium gray fill, no outer border) — same pattern vanilla bakes into its menu chrome. Generalized into `PanelRendering.renderInsetRect(...)` helper for arbitrary dimensions.
- **Scroll position**: float-normalized 0.0–1.0 matching `CreativeModeInventoryScreen.scrollOffs`.
- **Constants**: `SCROLLER_WIDTH = 12`, `SCROLLER_HEIGHT = 15` — matching vanilla's same-named constants.

### Buffer constants are library-inherent

All scrollbar-related dimensions are public static finals on ScrollContainer:
- `SCROLLER_WIDTH` (12) — vanilla handle width
- `SCROLLER_HEIGHT` (15) — vanilla handle height
- `SCROLLER_TRACK_PADDING` (1) — px around handle inside track
- `TRACK_WIDTH` (`SCROLLER_WIDTH + 2 × SCROLLER_TRACK_PADDING` = 14) — derived
- `SCROLLER_GUTTER` (4) — px between content and track
- `SCROLL_PIXELS_PER_TICK` (10) — wheel input granularity

Public helper `ScrollContainer.viewportWidthFor(int outerWidth)` returns `outerWidth - TRACK_WIDTH - SCROLLER_GUTTER`. Consumers use this for content sizing; smoke uses this. Buffer changes (e.g., `SCROLLER_GUTTER` 2 → 4 mid-implementation per Trevor's visual-spacing preference) propagate automatically — no smoke-side updates needed. All buffers are inherent to the primitive, not hardcoded into smoke or consumer call sites.

---

## What shipped

### Library — new

| File | Role |
|---|---|
| `core/ScrollContainer.java` | Public `PanelElement` viewport with builder |
| `Design Docs/Elements/SCROLL_CONTAINER.md` | ~700 lines — full round-1 + findings record |
| `Design Docs/Phases/14d-2/REPORT.md` | this file |

### Library — modified

| File | Change |
|---|---|
| `core/PanelElement.java` | + `mouseScrolled` default + `mouseReleased` default (both return false) |
| `core/PanelRendering.java` | + `renderInsetRect(graphics, x, y, w, h)` — generalizes slot-background pattern; `renderSlotBackground` now delegates |
| `inject/ScreenPanelAdapter.java` | + `mouseScrolled` dispatch + `mouseReleased` dispatch (release fires for all elements regardless of cursor) |
| `inject/ScreenPanelRegistry.java` | + `dispatchModalScroll`, `findModalAtPoint`; registers Fabric `allowMouseScroll` + `allowMouseRelease` hooks per-screen |
| `mixin/MenuKitModalMouseHandlerMixin.java` | `onScroll` updated to modal-aware (was wholesale-eat) |
| `verification/ContractVerification.java` | + V12 ScrollContainer probe + V13 modal-scroll dispatch probe + `/mkverify scroll` smoke command + `wireScrollSmoke` panel registration |

### V12 + V13 verification

| Probe | Cases | Result |
|---|---|---|
| **12 M12 ScrollContainer math** | 11 | (smoke verifies) — viewportWidthFor formula, builder validation (3 missing-required-field cases), too-small size, auto-compute contentHeight, explicit override |
| **13 M13 modal-scroll dispatch** | 3 | (smoke verifies) — null-screen guards, helpers don't NPE on null inputs |

`/mkverify` aggregator now reports 13 contracts. Visual smoke covers the integration end-to-end: scrollbar renders correctly with vanilla sprites + slot-style inset track, mouse-wheel scrolls content, click-drag on handle scrolls proportionally, click items inside viewport dispatches with scroll-translated coords, scrollbar matches vanilla creative inventory's appearance.

---

## What didn't ship / deferred

- **Horizontal scroll** — no concrete consumer; different layout semantics. Add on evidence.
- **Dynamic-content scroll lists** (`Supplier<List<T>>` with per-item template) — separate primitive per existing DEFERRED.md "List" entry.
- **Keyboard scroll** (PgUp/PgDn/Home/End) — defer until evidence; especially relevant for accessibility.
- **Scrollbar track click for jump-scroll** — common UX but not load-bearing in v1.
- **Auto-hide scrollbar when content fits** — v1 ships always-visible (with `scroller_disabled` sprite when content fits). Fold-on-evidence.
- **Smooth-scroll animation** — THESIS scope ceiling.
- **Nested scroll containers** — supported but unverified at v1; `enableScissor` is stack-based by vanilla's own contract.

---

## Process notes

**One round + five fold-inline findings.** Round 1 closed cleanly with one verdict pushback (Q3 contentHeight) + one wording nit (§11 nested supported-but-unverified). Implementation surfaced five findings that each yielded a small architectural improvement folded inline rather than warranting round 2:

1. **PanelElement.mouseReleased plumbing** — polling approach (`isLeftPressed`) didn't work in screen-open contexts; switched to event-based via Fabric `allowMouseRelease`. Partially ships the deferred mouseReleased/mouseDragged work.
2. **Vanilla sprite IDs corrected** — `widget/scroller` was a guess; vanilla uses `container/creative_inventory/scroller`. Verified via bytecode.
3. **`PanelStyle.INSET` too heavy for scrollbar track** — generalized slot-background pattern into `PanelRendering.renderInsetRect(...)`.
4. **Track padding around handle** — added `SCROLLER_TRACK_PADDING = 1` so the handle sits inside a slightly larger inset track (matches vanilla appearance).
5. **`viewportWidthFor` public helper** — Trevor's pushback ("buffer should be inherent, not hardcoded") drove the helper. All consumers use it; smoke uses it; buffer changes propagate automatically.

**Calibration heuristics from 14d-1 applied successfully:**

- *"Find the vanilla flag/primitive that already centralizes the behavior."* — applied for clipping (`enableScissor`), scrollbar sprites (`container/creative_inventory/scroller`), scroll position convention (normalized 0.0–1.0), track pattern (slot-background style).
- *"If delivering primitive X requires multiple compounding mixins at the same layer, X needs a different layer."* — N/A this phase; ScrollContainer is a single-element primitive without input-pipeline coverage concerns.

---

## Verification

### Automated (`/mkverify`)

13 library contracts + 5 validator scenarios. All PASS expected:

| Contract | Result |
|---|---|
| 1-9 | (unchanged from 14d-1 close) |
| **10 M10 modal click-eat** | 12/12 ✓ (round-3 fold-inline + modal-scroll fold preserved) |
| **11 M11 dialog builder** | 11/11 ✓ |
| **12 M12 ScrollContainer math** | 11/11 (new) |
| **13 M13 modal-scroll dispatch** | 3/3 (new) |

### Visual smoke (dev-client)

Comprehensive smoke through `/mkverify scroll` panel + creative inventory:

- ScrollContainer renders with correct vanilla sprites + slot-style inset track
- Mouse wheel scrolls content
- Click-and-drag on scrollbar handle scrolls proportionally; drag ends cleanly via `mouseReleased`
- Click items inside viewport at scroll>0 dispatches to the correct item (scroll-translated coords work)
- Click in scrollbar gutter / track is consumed silently (per click-through prohibition principle)
- Scrollbar matches vanilla creative inventory's appearance
- Buffer between content and track is consistent (auto-derived from `SCROLLER_GUTTER` constant; smoke uses `viewportWidthFor` helper)
- Modal+scroll: scroll inside dialog reaches dialog elements; outside dialog eaten

---

## Phase 14d-3 entry conditions

- ScrollContainer shipped + smoke-verified
- M8 thesis successfully reframed for runtime-clipping primitives (without licensing animation/screen-stacks)
- Modal-scroll fold-inline closes the round-3 14d-1 carry-over
- All existing element types untouched
- No regressions across V2-V7 scenarios or 11 other library contracts
- Design doc + phase report committed
- `/mkverify scroll` smoke command kept as dev tooling for regression checks

Phase 14d-3 (per `PHASES.md` §14d sequencing — text input next per implementer brief): real design surface (selection model, IME, validation, submission semantics, read-only mode). Likely the heaviest design conversation of 14d's remaining sub-phases.

---

## Architectural finding for advisor — click-through prohibition (filed in DEFERRED.md)

Trevor named a foundational principle during 14d-2 smoke: *"You should NEVER be able to click through behind a panel to something behind. No click through, no tooltips showing behind, no mouse icon change to clickable, nothing. Everything behind the panel is completely inert."*

This is a generalization of the modal mechanism (which makes modal panels fully opaque to underlying interaction at every level). Currently only modal panels have full opacity; non-modal panels allow click-through (Fabric `allowMouseClick` hook returns `true` even when an element consumed → vanilla still processes the click → slot underneath ALSO sees it). Hover/cursor/tooltip suppression also doesn't apply for non-modal panels.

Filed in DEFERRED.md with five sub-questions for advisor verdict. Affects every primitive shipped from here on; worth resolving before 14d-3+ ships.

---

## Diff summary

~9 files modified, 4 new (ScrollContainer + design doc + REPORT + phase folder), 0 deleted. New library surface ~600 LOC (ScrollContainer ~430, PanelElement +30, PanelRendering +20, ScreenPanelAdapter +30, ScreenPanelRegistry +90, mixin +25). Plus ~250 LOC of probe additions.

**Phase 14d-2 closed.**
