# ScrollContainer — design doc

**Phase 14d-2 element — palette additions** (per `PHASES.md` §14d).

**Status: round 1 closed; implementation complete; smoke green. Five fold-inline findings during implementation (no round 2 needed). Ready for commit.**

**Load-bearing framing (PHASES.md §14d verbatim):**

> **Scroll container** — clipped region with scroll input

Scroll container is the first 14d primitive that explicitly conflicts with M8's helper-not-container thesis ("Panel is the ceiling of composition"). The architecturally load-bearing work in this doc is engaging that conflict honestly: deciding which side of the line scroll container lives on, and reframing the thesis where the conflict reveals the original framing was layout-specific.

This doc batches into one element (no batched siblings; scroll container has substantial design surface of its own).

---

## 1. Purpose

Consumer mods building UI with content larger than its allotted region — long settings lists, item-list dropdowns, long dialog bodies, icon palettes — currently have no library primitive for scrollable display. The hand-rolled alternative is per-consumer pagination (Panel-with-show-when-page=N) or per-consumer mixin into vanilla scroll machinery.

ScrollContainer ships the missing primitive: a clipped viewport hosting pre-positioned `PanelElement`s, with scroll input (mouse wheel + click-drag scrollbar) that updates a consumer-managed scroll offset.

The narrow purpose: *clip + scroll the rendering of pre-positioned children.* Layout of those children is M8's job; auto-sizing is Panel's job; ScrollContainer owns clipping + scroll offset + scrollbar UI + minimal viewport background.

---

## 2. Consumer evidence

### Static-content scroll cases

- **Settings screens with many options.** Hypothetical IP config screen with 20+ toggles — pagination feels heavy; scroll feels native.
- **Long dialog bodies.** ConfirmDialog with multi-line body that exceeds reasonable dialog height (e.g., release-notes dialog, terms-of-use acknowledge). Today body is single-line; multi-line + scroll resolves both.
- **Information panels.** Sandboxes "all sandboxes" listing, IP "all loadouts" listing — long lists that should scroll rather than paginate.

### Future-dependency consumer

- **Dropdown popup body (14d-5).** Dropdown's option list, when long, should scroll inside the popup. ScrollContainer is the dependency.

**Rule of Three check:**
- Static content: 3+ concrete cases (settings, long dialog body, info listings) ✓
- Dropdown dependency: deferred until 14d-5 surfaces concrete demand

v1 ships ScrollContainer for static content. Dynamic-content scroll lists (variable item count from `Supplier<List<T>>`) is filed deferred per the existing DEFERRED.md "List (dynamic repeated content)" entry — that primitive's design surface includes more than just scroll.

---

## 3. Scope

- **One new element kind:** `ScrollContainer implements PanelElement`. New file `core/ScrollContainer.java`.
- **Clipped viewport** via `GuiGraphics.enableScissor` / `disableScissor` (vanilla's GL scissor primitive, stack-based, cheap).
- **Vertical scroll only in v1.** Horizontal-scroll deferred per Principle 11 (no concrete consumer evidence; horizontal-scroll has different layout semantics — content typically "wraps" rather than "extends right").
- **Scroll position as consumer state** per Principle 8 — `DoubleSupplier` + `DoubleConsumer` callback. `0.0` is fully scrolled-up; `1.0` is fully scrolled-down (normalized). Library reads supplier per-frame; consumer mutates state in callback.
- **Scroll input v1**: mouse wheel + click-drag on scrollbar handle. Defer keyboard PgUp/PgDn until evidence.
- **Vanilla scrollbar visual** — uses `widget/scroller` + `widget/scroller_disabled` sprite identifiers from vanilla atlas (same sprites `CreativeModeInventoryScreen` uses).
- **Content is pre-positioned `List<PanelElement>`** — consumer composes via M8 (`Column.at(...).build()`) or hand-positions. ScrollContainer renders the list at clipped coords with scroll-offset translation.
- **Modal-aware scroll dispatch** — fold-inline fix for round-3 14d-1's wholesale-eat-scroll: scroll inside modal bounds reaches the modal's elements (lets ScrollContainer-inside-dialog work); scroll outside is eaten.

**Deferred:**
- Horizontal scroll (no concrete consumer)
- Dynamic-content scroll lists (`Supplier<List<T>>` + per-item template) — separate primitive per existing DEFERRED.md "List" entry
- Keyboard scroll (PgUp/PgDn/Home/End) — v1 mouse-only
- Auto-hide scrollbar when content fits — surface as advisor question §10
- Smooth-scroll animation — per THESIS scope ceiling (no animation framework beyond notifications)

---

## 4. Architectural decisions

§4.1 (container shape) and §4.2 (thesis reframe) are load-bearing. The rest are concrete decisions with clearer answers.

### 4.1 Container shape — three resolution shapes

ScrollContainer is the first primitive that explicitly conflicts with M8's helper-not-container framing. M8 §4.1's structural test sentence:

> *Does this primitive own a render pass?* A helper computes positions at build time; Panel owns the render pass. A container would itself render children. If a proposed layout primitive requires a runtime render dispatch to paint its children, it is a container, and the design fails — Panel stops being the ceiling of composition.

ScrollContainer can't pass that test as-stated: clipping is fundamentally runtime (every frame the renderer needs to know what to clip), so build-time positioned-element emission can't deliver the primitive. Three shapes considered:

**(α-1) ScrollContainer as sub-Panel.** Owns layout, auto-size, padding, background, scroll, clipping. Effectively replicates Panel's responsibilities inside a scroll-aware wrapper. Reframes thesis to "Panel is the ceiling for static composition; ScrollContainer is the runtime-container exception."

- **Cost:** expanding library scope. ScrollContainer becomes a parallel surface to Panel — auto-size, padding, background variants, slot groups (?), all replicated. Significant API growth.
- **Benefit:** ergonomic — consumer just hands ScrollContainer a list of elements; ScrollContainer does layout + clipping.

**(α-2) ScrollContainer as viewport.** Holds pre-positioned `PanelElement`s. Owns *only* clipping + scroll offset + scrollbar UI + minimal background. Doesn't compute layout, auto-size, or padding.

- **Cost:** consumer composes content layout via M8 (or hand-positioning) before passing to ScrollContainer. Mild ergonomic cost.
- **Benefit:** preserves "Panel is the ceiling" more strictly. ScrollContainer doesn't re-implement Panel's responsibilities; it just clips. Single-responsibility primitive. Composes naturally with M8 — `Column.at(0, 0)...build()` produces pre-positioned children, hand list to ScrollContainer.

**(α-3) ScrollContainer as M8-style helper.** Hard to see how this delivers — clipping is runtime; build-time emission can't produce a clipped sub-region. Strawman.

**Verdict: (α-2) viewport.** ScrollContainer is a `PanelElement` with internal render dispatch over a viewport showing externally-positioned children. Single-responsibility: clipping + scroll. Layout stays in M8. Auto-size + padding stay on Panel.

The element-positioning surface stays in M8; the clipping surface stays in ScrollContainer. Different problems, different tools.

### 4.2 Thesis reframe (narrowed)

(α-2) requires a reframe of M8's helper-not-container test. Round-1 advisor's narrowed framing:

> *"Panel is the ceiling of layout composition. PanelElements may have internal render dispatch over their own visuals OR over a viewport showing externally-positioned children. The M8 helper-not-container test applies to LAYOUT (build-time positioning); other runtime concerns (clipping, viewport scrolling) are honest sub-passes within an Element."*

This narrowing is honest about M8's scope — M8 was about layout. M8's structural test says "compute positions at build time when build-time computation suffices." For clipping, build-time can't deliver because the clip mask depends on the scroll offset, which is runtime state. Different problem; different tool.

**What this DOES license:**
- ScrollContainer as a `PanelElement` with internal sub-render over a viewport
- Future viewport-shaped primitives (e.g., a "minimap" element with its own render pass into a clipped sub-region)
- ProgressBar / ItemDisplay / Button keeping their existing internal render dispatch over their own visuals (they always had this; the reframe just names it explicitly)

**What this DOES NOT license (intentional narrowing):**
- General "Element-with-render-pass" as a category that absorbs animation framework (THESIS scope ceiling)
- Modal screen stacks (different concern; its own architectural shape)
- Sub-screens (would violate THESIS "Panel is the ceiling")
- M8 helpers becoming runtime containers (Row/Column stay build-time helpers)

Each of those would need its own evidence and own thesis-level conversation.

### 4.3 Clipping mechanism — vanilla `GuiGraphics.enableScissor`

Vanilla pattern (verified in 1.21.11 bytecode):
- `GuiGraphics.enableScissor(int x1, int y1, int x2, int y2)` — pushes a scissor rectangle onto the graphics' scissor stack. Subsequent draw calls clip to the rectangle (GL scissor test). Stack-based — supports nested scissors.
- `GuiGraphics.disableScissor()` — pops the scissor stack.
- `GuiGraphics.containsPointInScissor(int, int)` — tests if a point is inside the current scissor rect.

ScrollContainer's render path:
1. Render minimal background (frame border / panel-style chrome)
2. `enableScissor(viewport bounds)` — viewport is the content area inside the scrollbar gutter
3. For each child element: render with translated coords (origin shifted by scroll offset)
4. `disableScissor()` — pop the scissor
5. Render scrollbar handle outside the scissored region (full visible regardless of clip)

Use vanilla's primitive directly — no per-mod scissor abstraction (heuristic from 14d-1: *find the vanilla flag/primitive that already centralizes the behavior*). `enableScissor` is the right level — cheap, stack-based, exactly what's needed.

### 4.4 Scroll position state — lens pattern per Principle 8

Scroll position is consumer state. Library reads via `DoubleSupplier`; consumer mutates state in the `DoubleConsumer` callback fired on scroll events.

**Normalized vs pixel.** Two conventions:
- **Normalized (0.0 to 1.0):** scroll position as fraction of "how far through the content." Vanilla `CreativeModeInventoryScreen.scrollOffs` uses this. Library-friendly because it's resolution-independent.
- **Pixel offset:** scroll position as an absolute Y pixel offset.

**Verdict: normalized (0.0 to 1.0).** Matches vanilla. Library converts normalized to pixel offset internally based on `contentHeight - viewportHeight`. Consumer state is a single `double` between 0 and 1, easy to persist / animate / reset.

Edge cases:
- Content shorter than viewport: scrollbar disabled; supplier value irrelevant; scroll input no-ops
- Content exactly equal to viewport: same — disabled
- Supplier returns out-of-range value (negative or > 1.0): library clamps silently (matching ProgressBar's clamping convention)

### 4.5 Scroll input sources — v1: mouse wheel + click-drag

**v1 input set:**
- **Mouse wheel** — vertical scroll within ScrollContainer bounds. Updates scroll offset by a fixed amount per "tick" of wheel input. Tick size = one element row, configurable later.
- **Click-and-drag on scrollbar handle** — direct manipulation. Click on scrollbar handle starts drag; drag updates offset proportionally to drag distance.

**Defer:**
- Keyboard PgUp/PgDn/Home/End — wait for consumer evidence (especially for accessibility)
- Click on scrollbar track (jump scroll) — common UX pattern but not load-bearing in v1

**Modal interaction:** scroll inside modal bounds should reach the ScrollContainer; scroll outside is eaten. See §4.9.

### 4.6 Scrollbar rendering — vanilla sprites

ScrollContainer uses vanilla atlas sprites:
- `widget/scroller` — scrollbar handle, enabled
- `widget/scroller_disabled` — scrollbar handle, disabled (when content fits viewport)

Vanilla `CreativeModeInventoryScreen` uses these. Reusing them ensures resource-pack compatibility and visual consistency with vanilla scrollbars.

**Always-visible scrollbar in v1.** Auto-hide-when-content-fits is a UX nicety but adds runtime conditional rendering. v1 always renders the scrollbar (handle disabled when content fits). Consumer who wants auto-hide opts into a builder method `.autoHide(true)` if/when added — surface as §10 question.

**Scrollbar position:** right edge of ScrollContainer, occupying `SCROLLER_WIDTH = 12` pixels (vanilla constant). The viewport is `width - SCROLLER_WIDTH` wide.

### 4.7 Content layout — M8 composition

ScrollContainer accepts `List<PanelElement>` of pre-positioned children. The consumer composes layout via M8:

```java
List<PanelElement> content = Column.at(0, 0).spacing(2)
    .add(Button.spec(60, 20, Component.literal("Option 1"), this::pickOne))
    .add(Button.spec(60, 20, Component.literal("Option 2"), this::pickTwo))
    .add(Button.spec(60, 20, Component.literal("Option 3"), this::pickThree))
    // ... many more
    .build();

PanelElement scrollList = ScrollContainer.builder()
    .at(8, 8).size(80, 60)
    .content(content)
    .contentHeight(N * 22 - 2)  // 22 per item (20 height + 2 spacing); minus trailing spacing
    .scrollOffset(this::scrollOffset, this::setScrollOffset)
    .build();

panels.add(new Panel("settings", List.of(scrollList), ...));
```

**`contentHeight` is auto-computed by default** from `max(child.childY + child.height)` over children, with `.contentHeight(int)` as an explicit override. Round-1 advisor pushback on the original consumer-declared default landed cleanly: M8's "consumer composes layout, library doesn't auto-size" applies to LAYOUT (where children go), not MEASUREMENT (how big the resulting layout is). Panel already measures via auto-size; ScrollContainer measuring content height from children is structurally the same shape, not an M8 violation. Consumer-declared as default is a bug-magnet — silent miscalculation when content count changes. Auto-compute runs once at build time over a finite list; trivial cost, correct by construction.

The explicit `.contentHeight(int)` override stays for cases consumers genuinely want it: content larger than children (trailing padding for a comfortable scroll-past-end), content smaller than children (capped scroll extent), supplier-driven content size where the auto-compute is wrong.

**Coordinate convention:** child `childX`/`childY` are relative to the viewport origin (not the screen). ScrollContainer translates by `scrollOffset * (contentHeight - viewportHeight)` Y-pixels when rendering children. The render-time translation is the only mutation; children's `childX`/`childY` are still "fixed at construction" (Principle 4).

### 4.8 Cross-context applicability

| Context | Applies? | Reason |
|---|---|---|
| **MenuContext** | Yes | Decoration panels can include scrollable lists. Settings screens, info displays, etc. |
| **StandaloneContext** | Yes | MenuKit-native screens with long content benefit from scrolling. Sandboxes selector if list grows large. |
| **SlotGroupContext** | **No** | Anchor mismatch + scope mismatch. SlotGroupContext panels are bounded by slot-group bounds, which already constrains size; scroll isn't the fit. |
| **HudContext** | **No** | HUDs are render-only (no input dispatch per CONTEXTS.md). Scroll input requires mouse wheel events. Static "scroll-frozen" HUD lists aren't a real use case — consumers wanting indicator lists use HUD-native patterns (multiple stacked HUD panels). |

ScrollContainer is `PanelElement` so it's structurally usable in any context that accepts elements; cross-context applicability is about whether scroll is meaningful. v1 documents the supported contexts explicitly.

### 4.9 Modal-aware scroll dispatch (fold-inline finding)

**Round-3 14d-1's `MenuKitModalMouseHandlerMixin.onScroll`** wholesale eats scroll events when modal is up:

```java
private void menukit$eatModalScroll(...) {
    if (mc != null && ScreenPanelRegistry.hasAnyVisibleModal()) {
        ci.cancel();
    }
}
```

This was correct for 14d-1's scope — dialogs had no scrollable content, scroll-while-modal was always something to suppress. But ScrollContainer-inside-modal (e.g., a long-content dialog with a scrollable body) requires scroll INSIDE modal bounds to reach the modal's elements.

**Fold-inline fix in 14d-2:** parallel `dispatchModalClick` shape — scroll inside modal bounds passes through to the modal's elements; scroll outside is eaten. Approximate change:

```java
private void menukit$eatModalScroll(...) {
    if (mc == null || !ScreenPanelRegistry.hasAnyVisibleModal()) return;
    // Compute scaled coords from current mouse position
    double sx = ...; double sy = ...;
    if (ScreenPanelRegistry.isInsideAnyVisibleModal(mc.screen, sx, sy)) {
        return; // Scroll inside modal — let it through to the modal's elements
    }
    ci.cancel(); // Outside modal — eat
}
```

`isInsideAnyVisibleModal(...)` is a new helper extracted from `dispatchModalClick`'s logic (the inside-bounds check is already there). Shared decision; clean refactor.

**Fold-inline shape per 14d-1's process discipline:** drop-on-break-style finding surfaces during 14d-2 implementation; advisor verdict inline rather than new round. Estimated 5-10 LOC.

If implementation surfaces this is harder than estimated (some vanilla quirk in scroll-event coord computation, or scroll dispatched through a path that bypasses the modal bounds check), surface as architectural finding.

---

## 5. Consumer API — before / after

### Before (no ScrollContainer)

```java
// Consumer hand-rolls pagination — Panel-with-page-N visibility flags
private int currentPage = 0;
private static final int PAGE_SIZE = 5;

List<List<PanelElement>> pages = paginate(allOptions, PAGE_SIZE);
List<Panel> pagePanels = new ArrayList<>();
for (int i = 0; i < pages.size(); i++) {
    final int pageIdx = i;
    Panel page = new Panel("page-" + i, pages.get(i), ...)
        .showWhen(() -> currentPage == pageIdx);
    pagePanels.add(page);
}
// Plus prev/next navigation buttons, bounds-checking, etc.
```

Or a per-mod scissor mixin that the consumer maintains themselves.

### After (ScrollContainer)

```java
List<PanelElement> content = Column.at(0, 0).spacing(2)
    .add(Button.spec(80, 20, Component.literal("Option 1"), this::pickOne))
    .add(Button.spec(80, 20, Component.literal("Option 2"), this::pickTwo))
    // ... N options ...
    .build();

PanelElement scrollList = ScrollContainer.builder()
    .at(8, 8).size(96, 80)  // 96 wide (80 viewport + 12 scrollbar + 4 padding); 80 tall
    .content(content)
    .contentHeight(N * 22 - 2)
    .scrollOffset(() -> scrollOffsetState, v -> scrollOffsetState = v)
    .build();
```

Composition + clipping + scroll-input + scrollbar all primitive-supplied.

---

## 6. Library surface

### New files

- `core/ScrollContainer.java` — public final class implementing `PanelElement`. Uses vanilla `GuiGraphics.enableScissor` + `widget/scroller` sprite. Contains nested `Builder`.

### Modified files

- `inject/ScreenPanelRegistry.java` — add `isInsideAnyVisibleModal(Screen, double, double)` helper extracted from `dispatchModalClick`'s coord-check logic (used by both click and scroll dispatch).
- `mixin/MenuKitModalMouseHandlerMixin.java` — `menukit$eatModalScroll` updated to pass-through scrolls inside modal bounds (fold-inline finding §4.9).

### Lines of code estimate

- `ScrollContainer.java`: ~250 lines (builder + render with scissor + scroll-input handling + scrollbar render + javadoc)
- `ScreenPanelRegistry.java`: +20 lines (extract `isInsideAnyVisibleModal` helper)
- `MenuKitModalMouseHandlerMixin.java`: +10 lines (modal-aware scroll dispatch)

Total new+modified: ~280 LOC.

---

## 7. Migration plan — Phase 14d-2

14d-2 ships:
- `ScrollContainer` element + Builder
- Modal-aware scroll dispatch (fold-inline finding from 14d-1)

No consumer migration. ScrollContainer is additive.

Phase 14d-5 (Dropdown) consumes ScrollContainer for its popup body when option lists are long.

---

## 8. Verification plan

### 8.1 `/mkverify` aggregator probe — V12 ScrollContainer math

Pure-logic probe (no live screen needed):
- Build a ScrollContainer with synthetic content; verify viewport bounds calculation
- Verify scroll-offset clamping (out-of-range supplier values clamp to [0, 1])
- Verify scrollbar handle size calculation (= viewport / contentHeight, clamped)
- Verify "content fits viewport" detection (handle disabled when contentHeight ≤ viewportHeight)
- Verify scroll-translation math (childY at scroll=0 vs childY at scroll=1)

### 8.2 Modal-scroll fold-inline probe — V13

Pure-logic probe extending V10:
- Test `isInsideAnyVisibleModal(screen, x, y)` decision: returns true for click coords inside any visible modal panel; false otherwise
- Truth table for modal-scroll dispatch: modal-up + inside-modal → pass through; modal-up + outside-modal → eat; no-modal → pass through

### 8.3 Integration smoke (dev-client)

- `/mkverify` should add a `scroll` subcommand (similar to `dialog`) that opens a test scroll container with N items in survival/creative inventory
- Smoke check: scroll wheel inside container scrolls; click-drag on scrollbar scrolls; clicks on items inside container dispatch normally; content outside viewport bounds is clipped
- Modal+scroll smoke: dialog with a (manually-composed) scrollable body — scroll inside dialog should scroll the body; scroll outside dialog eaten

---

## 9. Library vs consumer boundary

**Library provides:**
- `ScrollContainer` element with builder
- Vanilla scissor-based clipping
- Vanilla scrollbar sprite rendering
- Scroll input handling (wheel + scrollbar drag)
- Scroll offset state management via supplier+callback (lens pattern)
- Modal-aware scroll dispatch (modal eats scroll outside; passes through inside)

**Consumers provide:**
- Pre-positioned `List<PanelElement>` (composed via M8 or hand-positioned)
- `contentHeight` (total Y-extent of content)
- Scroll offset state (supplier + callback per Principle 8)
- ScrollContainer dimensions (viewport size)

**Library does NOT provide:**
- Layout of children — composed via M8
- Auto-size of viewport — consumer declares dimensions explicitly
- Background chrome — minimal frame only; consumer wraps in styled Panel if richer chrome wanted
- Horizontal scroll — deferred
- Dynamic content (Supplier<List<T>>) — separate primitive (DEFERRED.md "List")
- Keyboard scroll input — deferred to evidence
- Auto-hide-when-content-fits scrollbar — surface as advisor question §10
- Smooth-scroll animation — THESIS scope ceiling

---

## 10. Round 1 verdicts

Round 1 closed with three advisor verdicts (one approving, one approving an implementer pull, one with substantive pushback that improved the design) plus one wording nit folded into §11.

### Advisor verdicts

1. **Container shape (§4.1) — (α-2) viewport approved.** Drafting didn't surface (α-1)-forcing cases. Thesis reframe (§4.2) is exactly the narrowed framing — explicit about what it does and doesn't license. Sign-off.

2. **Auto-hide scrollbar (§4.6) — always-visible v1 approved (implementer pull stands).** Predictability for consumer + player; matches vanilla `CreativeModeInventoryScreen` pattern; simpler implementation. `.autoHide(true)` folds in post-evidence if multiple consumers ask.

3. **`contentHeight` (§4.7) — pushback accepted: auto-compute default + explicit override.** The implementer pull was consumer-declared, framed as "M8 doesn't auto-size." Round-1 pushback landed: M8's framing applies to LAYOUT (where children go), not MEASUREMENT (how big the layout is). Panel already auto-measures via `getWidth/getHeight`; ScrollContainer measuring content height from children is the same shape, not M8-shape. Consumer-declared as default is a bug-magnet (silent miscalculation when content count changes); auto-compute runs once at build time. Explicit `.contentHeight(int)` override stays for genuine cases (trailing padding, capped scroll extent, supplier-driven size). Folded into §4.7.

### Implementer pulls signed off

- Scroll input v1 = mouse wheel + click-drag scrollbar (§4.5)
- Scroll position normalized 0.0-1.0 (§4.4) — matches vanilla `scrollOffs`
- Modal-aware scroll dispatch fold-inline via `ScreenPanelRegistry.isInsideAnyVisibleModal(...)` helper extraction (§4.9)

### §11 wording nit (folded inline)

Nested scroll containers were originally documented as "untested." Re-framed to "supported but unverified at v1" — `enableScissor` is stack-based by vanilla's own contract, so nested scissors should work; no concrete consumer means no smoke verification. Issue would be a defect, not an unsupported case. Folded into §11.

### Implementation findings (post-design, fold-inline during smoke)

Five findings surfaced during implementation; each yielded a small architectural improvement that folded inline rather than warranting round 2. Recorded for future-reader provenance.

1. **PanelElement.mouseReleased plumbing.** Round-1 design assumed scroll input could be handled via mouse-state polling (`MouseHandler.isLeftPressed`). Smoke disproved: `isLeftPressed` returns `false` on the first render frame after click for in-screen contexts. Switched to event-based: added `PanelElement.mouseReleased` default + `ScreenPanelAdapter.mouseReleased` dispatch + Fabric `ScreenMouseEvents.allowMouseRelease` registration. ScrollContainer ends drag in `mouseReleased`. The deferred `mouseReleased`/`mouseDragged` PanelElement work that was filed in DEFERRED.md is now partially shipped — release plumbed, drag still polls per-frame via `RenderContext.mouseY()`.

2. **Vanilla sprite IDs corrected.** Round-1 design stated `widget/scroller` as the scrollbar handle sprite; that was a guess. Verified vanilla `CreativeModeInventoryScreen` bytecode — actual IDs are `container/creative_inventory/scroller` + `container/creative_inventory/scroller_disabled`. Updated. Heuristic from 14d-1 calibration applied (find the vanilla primitive before inventing one).

3. **`PanelStyle.INSET` too heavy for scrollbar track.** First implementation rendered the track via `PanelRendering.renderPanel(..., PanelStyle.INSET)`. Visually correct semantic (recessed area) but too heavily beveled — visible outer black border + 2px frame. Vanilla's scrollbar track uses the slot-background pattern: 1px shadow on top/left, 1px highlight on bottom/right, medium gray fill, no outer border. Generalized the existing `renderSlotBackground` into `PanelRendering.renderInsetRect(graphics, x, y, w, h)` for arbitrary dimensions. ScrollContainer renders track via that helper.

4. **Track padding around handle.** First implementation rendered track exactly the same width as the handle (12px). Vanilla has 1px padding around the handle inside the track (track is 14px, handle is 12px, 1px on each side). Added `SCROLLER_TRACK_PADDING = 1` constant + derived `TRACK_WIDTH = SCROLLER_WIDTH + 2 × SCROLLER_TRACK_PADDING`. Handle Y offset math updated to keep handle inside the track padding.

5. **Public `viewportWidthFor(int outerWidth)` helper.** Smoke initially had hard-coded button width (78px) computed manually for outer width 90. Trevor pushback: "the spacing should be consistent and 'just work' for any implementation; this buffer should be inherent to scroll, not hard-coded into the test." Added public static helper `ScrollContainer.viewportWidthFor(int)` that returns `outerWidth - TRACK_WIDTH - SCROLLER_GUTTER`. Smoke uses the helper; consumers writing real UI use the same helper. Buffer changes (e.g., bumping `SCROLLER_GUTTER` from 2 to 4 per Trevor's request) propagate automatically — no smoke-side updates needed.

Plus minor adjustments: `SCROLLER_GUTTER` 2 → 4 px (Trevor's visual-spacing preference); auto-compute `contentHeight` from children (Q3 verdict); inert-RenderContext propagation through ScrollContainer's child render (children inside a non-modal panel that's behind a modal correctly inherit the inert state via the RenderContext sentinel).

Round 1 closes. Implementation green. Phase ready to commit.

---

## 11. Non-goals / out of scope

- **Horizontal scroll.** No concrete consumer; different layout semantics. Add on evidence.
- **Dynamic-content scroll lists** (`Supplier<List<T>>` with per-item template). Existing DEFERRED.md "List" entry; separate primitive.
- **Keyboard scroll** (PgUp/PgDn/Home/End). Defer until evidence; especially relevant for accessibility.
- **Scrollbar track click for jump-scroll.** Common UX but not load-bearing in v1.
- **Smooth-scroll animation.** THESIS scope ceiling — no animation framework beyond notifications.
- **Sub-pixel scroll precision.** Library-level scroll offset is `double`; rendering rounds to integer pixel positions (matches vanilla).
- **Nested scroll containers.** Supported but unverified at v1 — `enableScissor` is stack-based by vanilla's own contract, so nested scissors should work; no concrete consumer means no smoke verification. If a consumer hits an issue, that's a defect (not an unsupported case). Add verification cost when evidence surfaces.

---

## 12. Summary

ScrollContainer ships as a **`PanelElement` viewport** — clipping + scroll offset + scrollbar UI + minimal background, with content as pre-positioned `List<PanelElement>`. Layout stays in M8; scroll stays in ScrollContainer. Single-responsibility primitive.

The (α-2) viewport shape requires a narrow thesis reframe: *Panel is the ceiling of layout composition; PanelElements may have internal render dispatch over their own visuals OR over a viewport showing externally-positioned children.* The reframe is honest about M8's layout-specific scope and doesn't license general "Element-with-render-pass" as a category absorbing animation, screen stacks, etc.

Vanilla primitives drive the implementation: `GuiGraphics.enableScissor` for clipping, `widget/scroller` sprite for scrollbar handle, normalized 0.0-1.0 scroll position matching `CreativeModeInventoryScreen.scrollOffs`. *Heuristic from 14d-1 calibration: find vanilla's existing primitive before inventing one.*

Modal-aware scroll dispatch folds inline as a 14d-1 carry-over fix — round-3's wholesale-eat-scroll updated to "eat outside modal; pass inside" via shared `isInsideAnyVisibleModal` helper. Estimated ~10 LOC.

**Status: round 1 closed; advisor-approved.** Three advisor verdicts resolved in §10 (container shape ✓, auto-hide ✓, contentHeight pushback accepted → auto-compute default + explicit override). Three implementer pulls signed off. §11 wording nit folded. Implementation can begin: ~280 LOC across new ScrollContainer + small changes to ScreenPanelRegistry + MenuKitModalMouseHandlerMixin. V12 + V13 verification probes alongside.
