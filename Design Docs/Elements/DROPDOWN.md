# Dropdown — Phase 14d-5 design

**Status: round 1 draft, pre-advisor review.**

Single-selection dropdown control — trigger button + popover with selectable item list. For `MenuContext` + `StandaloneContext` panels. Last UI palette item in 14d (before Phase 14 close → Phase 15 consumer migrations).

---

## 1. Intent

Phase 14d-5 ships the last 14d palette item — single-select dropdown. Common consumer use cases: settings enums (game mode, difficulty, language), filter selectors, predefined option pickers. **Heavier design surface than 14d-3/14d-4** because there's no clean vanilla wrap (`CycleButton` cycles in place; vanilla 1.21.11 doesn't ship a popover-list dropdown widget). Dropdown is **bespoke composition** of MenuKit primitives + vanilla rendering — the load-bearing question is how clicks on the popover area get dispatched (since popover renders OUTSIDE Dropdown's element bounds).

The 14d-3 / 14d-4 wrap-vanilla heuristic (heuristic 6 — *Follow Vanilla When Wrapping*) doesn't apply to a single widget here, but applies to *patterns* — vanilla's `CommandSuggestions.SuggestionsList` is the popover-list precedent (chat command autocomplete) and provides the dispatch shape, scroll mechanism, and edge-detection placement Dropdown should follow.

---

## 2. Vanilla precedent investigation

Three vanilla classes investigated end-to-end:

### `CycleButton<T>` (~270 LOC)

In-place value cycler used in OptionsScreen (graphics quality, etc.). Cycles through a values list on each click. **Not a popover-list** — value swaps in place inside the button. Provides API-shape inspiration:
- Generic `<T>` type parameter
- `Function<T, Component> valueStringifier` — same shape as Dropdown's `.label(Function<T, Component>)`
- `withValues(Collection<T>)` — same as Dropdown's `.items(List<T>)`
- `OnValueChange<T>` callback — same shape as `Consumer<T>`
- `Builder<T>` pattern with required + optional setters

CycleButton lacks the popover/click-outside-closes shape that distinguishes Dropdown. We borrow the API shape; the popover mechanism is bespoke.

### `CommandSuggestions` + `SuggestionsList` (~600 LOC)

Chat command autocomplete — the popover-list precedent. **Architectural insights:**

- **NOT a vanilla widget.** `CommandSuggestions` is a plain Java class held as a field by the host screen (typically `ChatScreen`). The host explicitly delegates `mouseClicked` / `mouseScrolled` / `keyPressed` / `render` to it. No `addRenderableWidget` / `addWidget` registration.
- **Inner `SuggestionsList`** owns popover rendering + input dispatch. Hit-test against an internal `Rect2i`:
  ```java
  public boolean mouseClicked(int i, int j) {
      if (!this.rect.contains(i, j)) {
          return false;  // outside popover → don't consume; let host continue
      } else {
          this.select(k);
          this.useSuggestion();
          return true;   // inside popover → consume
      }
  }
  ```
- **Position computed at open time** with edge-detection:
  ```java
  int j = Mth.clamp(input.getScreenX(...), 0, ...);
  int k = anchorToBottom ? screen.height - 12 : 72;  // flip above/below
  ```
- **Internal scroll** via `offset` field, `mouseScrolled` mutates offset bounded by `suggestionLineLimit`. ~10 LOC of scroll logic.
- **Hover highlight** via fill rect against cursor coords + row position. ~10 LOC.
- **Cursor request** via `guiGraphics.requestCursor(CursorTypes.POINTING_HAND)` when cursor is inside rect.

Key takeaway: vanilla solves popover dispatch by giving the host explicit ownership of the click stream. We can't use that pattern directly (we're hooked into vanilla screens via `ScreenPanelAdapter` or `MenuKitScreen`, not "owning" a host screen) — but we can apply the same shape inside Dropdown via a new primitive that lets a `PanelElement` opt into receiving clicks outside its layout bounds.

### `Tooltip` (~50 LOC)

Just data + cached split. Placement is not in `Tooltip` — it's in `ClientTooltipPositioner` implementations (`DefaultTooltipPositioner`, `BelowOrAboveWidgetTooltipPositioner`, `MenuTooltipPositioner`). The `BelowOrAboveWidgetTooltipPositioner` does the same edge-flip logic Dropdown needs — useful reference for Q3.

---

## 3. Core architectural decision — bespoke composition (no wrap)

Dropdown is a **single `PanelElement`** that:

- **Trigger** — renders a Button-styled box at element bounds with selection label + chevron indicator. Click toggles popover open/closed.
- **Popover** — when open, renders an option list directly via `ctx.graphics()` calls, OUTSIDE the element's layout bounds. Hover highlight on items; selection highlight on current value; chevron-arrow indicators when scrollable.
- **Internal state** — `volatile boolean open` (matches Slider's `dragging` precedent). `int scrollOffset` if list exceeds `maxVisibleItems`. Both internal-only; not consumer-meaningful.
- **Selection lens** — `Supplier<T>` + `Consumer<T>` (matches Slider/ScrollContainer; T.equals() identity).

**No vanilla wrap.** No subclass of any widget. No `addWidget` registration. Pure MenuKit composition + direct graphics rendering for the popover.

**Why bespoke vs popover-as-Panel:** the cleanest unified approach across MenuContext + StandaloneContext is for Dropdown to own its full render + dispatch. Popover-as-separate-Panel (advisor's brief Q5 path, "Path A" below) would require dynamic panel registration in StandaloneContext — new infrastructure for one consumer. Bespoke composition with a small primitive addition (§4) avoids that.

---

## 4. New primitives — `PanelElement.hitTest(...)` + `PanelElement.getActiveOverlayBounds(...)`

Two load-bearing primitive additions. The `hitTest` change refactors inline bounds-checks in three dispatchers into a method call (default preserves existing behavior). The `getActiveOverlayBounds` change adds two-pass dispatch with an exclusive-claim first pass — the parallel to M9's panel-level modal click-eat at the element level.

**Why two primitives?** Trevor's smoke surfaced the architectural insight: with `hitTest` alone, "click on popover item" can clickthrough to a button visually behind the popover (because the button's layout bounds may overlap the popover's screen region). Fixing that via reverse-iteration of element dispatch order was a hack — the real fix is to give the popover a structural exclusive-claim like M9 modals have for panels. `getActiveOverlayBounds` is that primitive.

### Current dispatcher (representative — `MenuKitScreen`)

```java
double relX = mouseX - contentX - element.getChildX();
double relY = mouseY - contentY - element.getChildY();
if (relX >= 0 && relX < element.getWidth()
        && relY >= 0 && relY < element.getHeight()) {
    if (element.mouseClicked(mouseX, mouseY, button)) {
        return true;
    }
}
```

### Proposed dispatcher

```java
if (element.hitTest(mouseX, mouseY, contentX, contentY)) {
    if (element.mouseClicked(mouseX, mouseY, button)) {
        return true;
    }
}
```

### Default `PanelElement.hitTest`

```java
default boolean hitTest(double mouseX, double mouseY, int contentX, int contentY) {
    int sx = contentX + getChildX();
    int sy = contentY + getChildY();
    return mouseX >= sx && mouseX < sx + getWidth()
        && mouseY >= sy && mouseY < sy + getHeight();
}
```

Identical semantic to the inline check. Existing elements (Button, TextField, Slider, ScrollContainer, etc.) inherit unchanged.

### `PanelElement.getActiveOverlayBounds()`

```java
default int @Nullable [] getActiveOverlayBounds() {
    return null;     // No overlay — existing elements unaffected
}
```

Dropdown overrides:

```java
@Override
public int @Nullable [] getActiveOverlayBounds() {
    if (!open) return null;
    return computePopoverBounds(lastTriggerScreenX, lastTriggerScreenY);
}
```

When popover is open, returns the popover's screen-space bounds. The dispatchers' Pass-1 routes ANY click (or scroll) inside this region exclusively to Dropdown — elements behind (visually occluded) stay innately inert.

### Two-pass dispatch in screens (six dispatch points updated)

Three dispatchers, two methods each (click + scroll):
- `MenuKitScreen.dispatchElementClick` + `dispatchElementScroll`
- `MenuKitHandledScreen.dispatchElementClick` + `dispatchElementScroll`
- `ScreenPanelAdapter.mouseClicked` + `mouseScrolled`

Each rewritten as:
```java
// Pass 1: active-overlay exclusive claims
for (each visible element) {
    int[] overlay = element.getActiveOverlayBounds();
    if (overlay != null && pointInRect(mouseX, mouseY, overlay)) {
        element.mouseClicked(mouseX, mouseY, button);
        return true;     // exclusive — no further dispatch
    }
}
// Pass 2: normal hit-test dispatch (existing semantic — unchanged)
for (each visible element) {
    if (element.hitTest(...)) {
        if (element.mouseClicked(...)) return true;
    }
}
```

**Behavioral impact on existing elements:** none. All existing elements return null from `getActiveOverlayBounds` (default impl), so Pass 1 always misses, and Pass 2 runs identically to pre-14d-5.

### Why this beats the reverse-iteration alternative

An earlier draft proposed reversing element iteration in dispatchers (so last-declared = first dispatched = Dropdown wins clicks). That approach was a hack:

1. **Doesn't actually solve clickthrough.** Reversed iteration just means Dropdown gets first crack — but if Dropdown decides not to consume (e.g., for the dismiss-on-outside semantic), the click still falls through to behind elements. So clickthrough still happens; the user still sees a Reset Button activate when clicking a popover item.
2. **Wrong abstraction level.** Dispatch order is a global iteration concern; popover exclusivity is an element-local concern. Conflating them via iteration-order tweaks couples element design to dispatcher behavior.
3. **Doesn't generalize.** Other "I claim this region exclusively" elements (modal context menus, hover popups with embedded actions) would each need their own iteration-order workaround.

The overlay primitive solves all three: it's structural (the click is dropped at the dispatcher level for behind elements), local (each element declares its own exclusive region), and generalizes (any future overlay-style element opts in via the same hook).

### Dropdown's `hitTest` override

Dropdown does NOT override `hitTest` — it inherits the default (layout-bounds = trigger only). The popover doesn't extend `hitTest` because it claims exclusivity via `getActiveOverlayBounds` (Pass 1) instead. Trigger clicks dispatch via Pass 2 normal hit-test.

`mouseClicked` routes:
- Pass 1 (in popover bounds, popover open) → select item, close, consume.
- Pass 2 (in trigger bounds) → toggle popover open/close.
- Outside both → never reaches Dropdown. Popover stays open.

**Outside-click-dismiss is deferred to fold-on-evidence.** Vanilla `SuggestionsList` doesn't auto-dismiss either. v1 dismiss paths:
- Click trigger again to toggle close.
- Click any popover item to select-and-close.
- Press Esc to close the parent screen (which detaches all elements).

If a consumer wants outside-click dismiss, they can add a sibling Button + handler — or fold the feature inline when a second consumer surfaces the need.

### Why this primitive over alternatives

Considered three other shapes, all rejected:

**(α) Popover-as-Panel + M9 `onOutsideClick`** (advisor's Q5 recommendation). Requires dynamic Panel registration which doesn't currently exist in StandaloneContext. Adding that infrastructure to support one consumer (Dropdown) is heavier than the `hitTest` primitive. Also: M9 onOutsideClick fires for clicks outside *the panel containing dropdown*, not outside *the popover area*, so the bounds semantic doesn't match.

**(β) Internal Vanilla widget via `addWidget`** (Slider/TextField pattern). Vanilla iterates `Screen.children()` AFTER `MenuKitScreen.mouseClicked` fires (which dispatches PanelElements first). PanelElements would consume popover-area clicks before our widget ever sees them. Doesn't work.

**(γ) Dynamic-bounds — Dropdown's `getWidth/getHeight` change when open.** Works for hit-test, but breaks panel auto-size logic (panel grows when dropdown opens, layout reflows). Dispatcher uses width/height for hit-test; same source for layout. Splitting layout-bounds vs interaction-bounds is essentially what `hitTest` does, but cleaner.

The `hitTest` primitive is small (1 method on PanelElement, 3 dispatcher refactors of ~3 LOC each) and generalizes — any future element wanting interaction beyond layout bounds (tooltips with click targets, expandable inline editors) can opt in via the same hook.

---

## 5. API shape

### Builder pattern (matches Slider/TextField style)

```java
Dropdown<GameMode> dropdown = Dropdown.<GameMode>builder()
    .at(0, 0)
    .triggerSize(120, 20)
    .items(List.of(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE))
    .label(gm -> Component.literal(gm.name()))
    .selection(() -> currentMode, m -> currentMode = m)
    .maxVisibleItems(8)                            // optional; default 8
    .build();
```

### Lens pattern (Principle 8) — Supplier+Consumer over T

`selection(Supplier<T>, Consumer<T>)` is required. Library reads supplier each frame to render the current selection in the trigger label; library calls consumer when user picks an item.

Per-frame supplier-pull is idempotent — Dropdown stores no value internally; supplier IS the source of truth, like Slider's lens. Programmatic selection updates (server sync, reset-to-default) work transparently — consumer changes their state, next frame trigger label updates.

Selection identity via `T.equals()`. `null` supplier values OK — trigger renders empty label. Items list defines the selection domain; consumer can supply T not in items but it just won't highlight as "current" in popover (and reset-to-default by setting state to a non-items value would make trigger show empty).

### Items + label

```java
.items(List<T>)                       // required; immutable post-build
.label(Function<T, Component>)        // required; renders both trigger label
                                      //   AND popover item labels
```

`label` does double duty: trigger shows `label.apply(currentSelection)`; popover items show `label.apply(item)` per row. Vanilla `CycleButton` uses the same single-function shape.

### Trigger size + popover position

```java
.triggerSize(width, height)           // required; vanilla-default-style 120x20
```

Popover position is **always computed at open time** based on trigger's current screen-space position + screen height. Default: open BELOW trigger; if available space below < popover height, flip and open ABOVE trigger. No `.openDirection()` builder option in v1 (per advisor's brief Q3 — AUTO only).

Popover width = trigger width (matches `CycleButton` + standard dropdown UX). Popover height = `min(items.size, maxVisibleItems) * ROW_HEIGHT + 2` (1px top/bottom border).

### Optional builder methods

```java
.at(x, y)                             // panel-local trigger position; default (0, 0)
.maxVisibleItems(int)                 // default 8; cap with internal scroll for longer lists
```

---

## 6. Answers to the 8 load-bearing questions

### 6.1 (Q1) Trigger shape — Button-styled with selection label + chevron

Dropdown owns its trigger render. Renders a Button-styled background (PanelStyle hover state on hover; pressed-look while popover open) with text label (current selection via `label.apply(selection)`) + chevron indicator on the right edge (`▼` when closed, `▲` when open / opening upward). Cursor: `POINTING_HAND` on hover.

**Diverges from CycleButton** which extends AbstractButton — Dropdown can't extend Button (different click semantic) and doesn't need narration plumbing as deep as vanilla buttons (defer keyboard per Q7). Render-only Button-style is sufficient.

Trigger NOT separately composable. Dropdown OWNS its trigger; consumer gets the whole composite.

### 6.2 (Q2) Open-state location — internal volatile boolean

`volatile boolean open` field on Dropdown. Same precedent as Slider's `dragging`. Open state isn't consumer-meaningful — just a UI mode the dropdown manages. Lifting to Supplier+Consumer would be over-API for v1. Defer if evidence emerges (e.g., consumer wants programmatic open/close — currently fold-on-evidence).

### 6.3 (Q3) Popover position — auto-detect direction; default below

Open direction determined at open time:
- Default: below trigger (popover renders at `triggerY + triggerHeight`)
- If `triggerY + triggerHeight + popoverHeight > screen.height` → flip to above (popover renders at `triggerY - popoverHeight`)
- If popoverHeight is taller than screen height AT ALL → render at top with overflow at bottom (extreme-edge-case fallback; document)

No `.openDirection()` builder option in v1. AUTO only. Defer override to evidence.

X-axis: popover left-aligns with trigger (`popoverX = triggerX`). If popover would overflow screen-right, clamp to `screen.width - popoverWidth`. Same shape as `CommandSuggestions` `Mth.clamp`.

### 6.4 (Q4) Long-list path — internal scroll up to `maxVisibleItems` cap

`maxVisibleItems(int)` builder param, default 8. Popover renders the first `maxVisibleItems` items; if items.size > cap, scrollbar renders on right edge of popover; mouse wheel scrolls when cursor inside popover bounds; scroll math identical to ScrollContainer (normalized 0-1 offset translated to row-index).

**Implementation: internal to Dropdown (~50 LOC for scroll logic)** rather than composing ScrollContainer. Composing ScrollContainer would require the popover to be a Panel with element children — but popover isn't a Panel (per §3 bespoke composition). Direct render of items + scrollbar + wheel handling matches the `CommandSuggestions.SuggestionsList` precedent (which has its own `offset` field + `mouseScrolled`).

This isn't ScrollContainer reuse — it's parallel implementation of the same pattern. Acceptable v1 cost; ScrollContainer-as-render-helper is a possible future fold if a third popover-shape consumer surfaces.

### 6.5 (Q5) Click-outside-closes mechanism — `hitTest` primitive (DIVERGES from advisor's brief)

**Advisor's recommendation:** extend M9 panel with `.onOutsideClick(Runnable)`; Dropdown popover marks itself M9-tracked; M9 dispatches dismiss callback before consumption.

**This design's path:** new `PanelElement.hitTest(...)` primitive (§4); Dropdown overrides to claim all clicks when open; Dropdown's `mouseClicked` routes internally.

**Reasoning for divergence:**

1. **Works in both contexts.** Path A (M9 onOutsideClick) requires popover-as-Panel + dynamic Panel registration. M9 dispatch only knows about panels registered via ScreenPanelAdapter — works in MenuContext, doesn't reach StandaloneContext panels (which are constructor-fixed in `MenuKitScreen`). Path B (`hitTest`) works identically in both contexts via the existing element dispatchers.

2. **Smaller infrastructure addition.** `hitTest` is one new method on PanelElement + 3 ~3-LOC dispatcher refactors. M9 onOutsideClick + dynamic Panel registration in MenuKitScreen would be a larger primitive surface for one consumer.

3. **Scope mismatch in M9 onOutsideClick semantic.** M9 onOutsideClick fires for clicks outside *the M9-tracked panel's bounds*. Dropdown's "outside" is *the popover area*, not the panel containing dropdown. To make M9 onOutsideClick work for Dropdown, the popover would need to be its own Panel — which means dynamic Panel registration (back to point 1).

4. **`hitTest` generalizes.** Future elements wanting interaction beyond layout bounds (expandable inline editors, hover-with-click-targets, tooltips with action buttons) can opt in via the same primitive. M9 onOutsideClick remains a possible separate addition for true panel-level dismiss patterns (popover menus that span multiple panels) — fold on evidence.

**Open to advisor pushback** if there's a use case `hitTest` doesn't cover. Worth concrete examination — the advisor's brief noted Q5 as the "most architecturally interesting question."

**Dismiss-without-consume semantic** (return false from Dropdown.mouseClicked when click is outside popover): clicking another button while dropdown is open closes dropdown AND activates the button. Matches typical web UI; differs from macOS native menus (first click closes, second click activates). Worth surfacing — may differ from Trevor's preference.

### 6.6 (Q6) Selection lens — `Supplier<T>` + `Consumer<T>`

Generic-typed lens pair, matches Slider/ScrollContainer. Identity via `T.equals()`. `null` supplier values OK; trigger renders empty (or "—" placeholder — TBD in implementation). Consumer is called with the selected T on click; trigger label updates next frame via supplier-pull.

No imperative `setValue(T)` escape hatch (matches Slider; consumer-as-source-of-truth).

For evidence-driven keyed identity (e.g., consumer-supplied `Function<T, Object> idFn` for cases where T's equals doesn't match selection semantic): defer fold-on-evidence. Common case (enums, simple types) works with default `equals`.

### 6.7 (Q7) Keyboard — defer mouse-only v1

No vanilla widget to inherit keyboard from (heuristic 6 doesn't apply since not wrapping). Keyboard nav for popover (arrow keys cycle items, Enter selects, Esc dismisses) is meaningful but bespoke implementation. Defer to evidence — same scope decision as ScrollContainer's deferred-keyboard.

**Esc-to-dismiss specifically:** Dropdown isn't `tracksAsModal`, so M9's keyboard mixin doesn't eat keys. Esc reaches vanilla normally → vanilla closes screen → screen close detaches Dropdown via onDetach → Dropdown's open-state cleared. Side effect: parent screen also closes. For "Esc dismisses popover only without closing screen" semantic — that's a fold-on-evidence consumer-pulled feature.

For v1: mouse-only. Document the parent-screen-close behavior explicitly.

### 6.8 (Q8) Items list shape — immutable `List<T>` post-build

`items(List<T>)` is required and immutable post-build. Dynamic items (filtered autocomplete, server-pushed list updates) defer to a separate primitive — same way TextField/Slider don't mutate config post-build.

**Defer:** `items(Supplier<List<T>>)` for dynamic lists — that's actually a separate primitive (Autocomplete / Combobox), not a Dropdown variant. Future fold.

---

## 7. Composition with the testing convention (14d-2.7)

New Dropdown smoke registers as ONE Hub entry in validator.

**Smoke surface shape: standalone screen** (per 14d-3 / 14d-4 lessons — inventory-decoration smokes risk clipping; standalone gives popover breathing room for the edge-flip demo).

**Validator surface (~3 files touched):**

- `MenuKitSmokeState.java` — add `static volatile GameMode dropdownSelection = GameMode.SURVIVAL` (or similar enum lens-target).
- `DropdownSmokeScreen.java` (new, ~100 LOC) — `MenuKitScreen` subclass with header + Dropdown (declared LAST in elements for render Z-order; popover renders on top of other elements) + supplier-driven Consumer-state TextLabel below + Reset-to-default Button + Back-to-Hub Button.
- `HubHandler.java` — add `addButton(...)` block at the TOP for "Dropdown" → `setScreen(new DropdownSmokeScreen())`.

**Pure-logic auto-tests added to `ContractVerification.runAll`:**

- **M18 Dropdown builder validation** (~10 cases) — required fields (triggerSize, items, label, selection lens), null guards, items-empty validation, chainable builder returns.

Convention's structural test sentence holds: one smoke screen (validator), one Hub entry (validator), one auto-check probe (library). No new chat command, no library-side test scaffolding.

**Render-order documentation note in DropdownSmokeScreen:** explain why Dropdown is declared LAST in panel elements (declaration order = render order in MenuKitScreen; popover draws absolute via direct `ctx.graphics()` calls; declaration-last means popover draws on top of all other elements in panel).

### Render-order discipline (sharp edge of bespoke composition)

Because the popover renders via direct `ctx.graphics()` calls inside Dropdown's `render` method (rather than as a separate render pass), it follows normal element declaration order — **later-declared elements paint on top of the popover and clip it visually**.

Two rules every consumer must follow:

1. **Dropdown must be declared LAST among elements in any panel containing it** — otherwise later elements paint on top of the popover when it opens.
2. **If multiple Dropdowns coexist in a panel, the LAST-declared one wins Z-order** — earlier dropdowns' popovers paint under later elements (including other Dropdowns' triggers/popovers).

Document this in `Slider.java`-style class JavaDoc + in `DropdownSmokeScreen.java` as inline comment. Future fold-on-evidence: if multi-dropdown panels become common, hoist popover render to a separate `renderOverlay` pass on PanelElement (would cleanly resolve Z-order without consumer discipline). Not in v1 — single new primitive (`hitTest`) is the right scope.

---

## 8. Implementation outline

| File | Role | LOC |
|---|---|---|
| `core/Dropdown.java` (new) | Generic `<T>` PanelElement; builder; trigger render + hover/cursor; popover render with edge-flip + scroll; `hitTest` override + mouseClicked routing; mouseScrolled for popover scroll; lifecycle no-ops (no widget registration needed) | ~350 |
| `core/PanelElement.java` (modify) | Add `hitTest(double, double, int, int)` default method | +20 |
| `screen/MenuKitScreen.java` (modify) | Replace inline bounds-check in `dispatchElementClick` + `dispatchElementScroll` with `element.hitTest(...)` calls | -10 / +10 |
| `screen/MenuKitHandledScreen.java` (modify) | Same refactor for inventory-menu dispatch | -10 / +10 |
| `inject/ScreenPanelAdapter.java` (modify) | Same refactor for region-based panel dispatch | -10 / +10 |
| `verification/ContractVerification.java` (modify) | M18 Dropdown builder validation probe (10 cases) | +130 |
| `validator/.../scenarios/smoke/MenuKitSmokeState.java` (modify) | dropdownSelection field | +9 |
| `validator/.../scenarios/smoke/DropdownSmokeScreen.java` (new) | Standalone smoke screen — header + Dropdown + Consumer-state label + Reset + Back-to-Hub | ~100 |
| `validator/.../scenarios/hub/HubHandler.java` (modify) | Add Hub entry at top of list | +8 |
| `Design Docs/Elements/DROPDOWN.md` | This file | (new) |
| `Design Docs/PHASES.md` | Marker advance: 14d-4 closed → 14d-5 (Dropdown) shipped | +5 / -1 |
| `Design Docs/Phases/14d-5/REPORT.md` | Phase report on close | (new) |

Total approximate: ~+650 LOC, ~10 files touched. Heavier than Slider (~+510, 5 files) because of the dispatcher refactor (3 files) + bespoke popover render (no vanilla wrap to inherit). Lighter than M9 mechanism work (~+1050, 12 files).

---

## 9. Open questions for advisor verdict

**Q1 — Trigger shape (Button-styled with chevron, owned-by-Dropdown).** Implementer pull: confirmed; matches CycleButton API shape with our render-only Button styling.

**Q2 — Open-state location (internal volatile boolean).** Implementer pull: confirmed.

**Q3 — Auto-flip placement (open below; flip to above if no room; AUTO only v1).** Implementer pull: confirmed; matches `BelowOrAboveWidgetTooltipPositioner` precedent.

**Q4 — Long-list path (internal scroll up to `maxVisibleItems` cap, default 8).** Implementer pull: confirmed; internal scroll (~50 LOC) rather than ScrollContainer composition. Reasoning: popover isn't a Panel, so ScrollContainer-as-PanelElement doesn't compose; internal scroll matches `CommandSuggestions.SuggestionsList` precedent. **Worth examining:** is parallel implementation of scroll mechanics acceptable, or should ScrollContainer be refactored into a pluggable scroll-helper for both Dropdown and Panel-internal use? My pull: ship parallel for v1; refactor if a third popover consumer (e.g., context menu) surfaces.

**Q5 — Click-outside-closes mechanism — `hitTest` primitive (DIVERGES from your brief's M9 onOutsideClick).** Implementer pull: **`hitTest` over M9 extension**. Reasoning per §6.5:
- Works in both MenuContext + StandaloneContext (M9 dispatch is MenuContext-only)
- Smaller infrastructure addition (1 method + 3 dispatcher refactors) vs M9 extension + dynamic panel registration in StandaloneContext
- Generalizes to other "interaction-beyond-layout-bounds" use cases
- Open to pushback if there's a Path A use case `hitTest` doesn't cover

**Sub-question on dismiss semantic:** click-outside should be **dismiss-without-consume** (close dropdown AND allow underlying click to activate)? Or **dismiss-and-consume** (first click closes, second click activates — macOS menu semantic)? My pull: **dismiss-without-consume** (web UI standard; less surprising for typical settings flows). Worth explicit advisor verdict since it shapes user expectation.

**Q6 — Selection lens (`Supplier<T>` + `Consumer<T>`, T.equals identity).** Implementer pull: confirmed; matches Slider/ScrollContainer precedent.

**Q7 — Keyboard (defer mouse-only v1).** Implementer pull: confirmed; document parent-screen-close-on-Esc behavior. Fold on evidence for popover-only-Esc.

**Q8 — Items shape (immutable `List<T>` post-build).** Implementer pull: confirmed; dynamic items is a separate primitive (Autocomplete/Combobox) for fold-on-evidence.

---

## 10. What this design does NOT do

- **Multi-select** — separate primitive (MultiselectDropdown / Listbox); fold on evidence.
- **Search-as-you-type / autocomplete** — separate primitive (Combobox / Autocomplete); deeper design surface (text input integration + filtered items list).
- **Custom popover anchor (right-aligned, centered, anchored to other element)** — defer; v1 left-aligns with trigger only.
- **Keyboard navigation** (arrow keys cycle, Enter selects, Esc dismisses popover only) — defer per Q7.
- **Dynamic items list post-build** (`Supplier<List<T>>`) — defer; separate primitive shape.
- **Programmatic open/close** (`dropdown.open()` / `dropdown.close()` from consumer code) — defer per Q2; internal-state for v1.
- **Custom item rendering** (icons + multi-line items, etc.) — defer; v1 single-line text rows via `label.apply(item)`.
- **Group/section headers in popover** — defer; v1 flat items list only.
- **Scroll-via-keyboard** — moot per Q7; keyboard deferred entirely.
- **Visibility-driven attach/detach** — Dropdown has no vanilla widget to register; visibility-driven lifecycle isn't a concern (no addWidget cleanup needed mid-screen-life).
- **Modal dropdowns** — same M9 keyboard-mixin caveat as TextField/Slider; not relevant since Dropdown defers keyboard. Mouse drag/click works inside modals (M9 mouse dispatch handles).

---

## 11. Standing by

Round-1 design ready for advisor verdict on Q1–Q8. Implementer pulls per above. **Q5 is the load-bearing divergence** — `hitTest` primitive vs M9 onOutsideClick. Reasoning surfaced explicitly; open to pushback if Path A's evidence is stronger than I'm seeing.

Aim: 1 round + inline (matching 14d-3 / 14d-4 cadence). The investigation depth (CycleButton + CommandSuggestions + Tooltip end-to-end) was load-bearing for the bespoke-composition decision; design follows the host-screen-owns-dispatch precedent from `CommandSuggestions.SuggestionsList` while adapting it to MenuKit's PanelElement model via the new `hitTest` primitive.
