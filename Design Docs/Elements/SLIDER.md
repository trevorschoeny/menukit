# Slider — Phase 14d-4 design

**Status: round 1 draft, pre-advisor review.**

Continuous-value control with a draggable handle for `MenuContext` + `StandaloneContext` panels. Wraps vanilla `AbstractSliderButton` rather than reimplementing the drag/keyboard/render mechanism.

---

## 1. Intent

Phase 14d-4 ships the next palette item — a continuous-value control. Common consumer use cases: brightness/volume/FOV-style settings, opacity controls, anything-on-a-percent-scale. Bounded design surface (drag tracking + value range + lens), so the phase aims at 1 advisor round + inline.

The 14d-3 entry brief mandated investigating vanilla precedent first ("find the vanilla flag/primitive that already centralizes the behavior"). 14d-4 inherits that discipline. Vanilla `AbstractSliderButton` investigation conclusion: **vanilla AbstractSliderButton is comprehensive and the right thing to wrap.** ~150 LOC of vanilla-tested mechanism covering drag (onClick → onDrag → onRelease), keyboard navigation (Enter to enable + arrows to step + full narration), cursor changes (RESIZE_EW dragging / POINTING_HAND hovering), value clamping ([0.0, 1.0]), sprite rendering (`widget/slider` + `widget/slider_handle` + highlighted variants), live-message text via `getMessage()` / `updateMessage()`, sound on release. Dogfooding angle: MenuKit's role is to MAKE AbstractSliderButton composable into Panel + M8 layout + M9 opacity — not to compete with it.

The advisor brief flagged keyboard + value-display + lens-shape as deferrable / consumer-pulled. Two of those reverse on investigation (vanilla provides them for free; opting OUT would cost more than letting them ride). One stays as advisor's read (Supplier+Consumer lens — confirmed by vanilla's internal model).

---

## 2. Core architectural decision — wrap vanilla AbstractSliderButton

`Slider` is a `PanelElement` that holds an internal `MenuKitSlider` (private subclass of `AbstractSliderButton`, since vanilla's class is abstract). Composition pattern matches TextField's wrap of EditBox.

**Lifecycle (re-uses 14d-3 hooks, no new primitives needed):**

- **Construction** — `Slider` builds its `MenuKitSlider` at construction time with consumer's settings (size, initial value via supplier, label function).
- **Attach** (`onAttach(Screen)`) — registers the wrapped slider via `((ScreenAccessor) screen).menuKit$addWidget(slider)` so vanilla's screen widget pipeline routes keyboard / focus / narration to it. Same pattern as TextField — input-dispatch only, NOT renderables (so panel background can render OVER it without covering the slider; we render manually in element.render() after the panel background draws).
- **Render** — per frame, `Slider` updates `MenuKitSlider.setX/setY` to track the panel's content origin, syncs the internal `value` field from the supplier, then renders the slider manually via `slider.render(graphics, mouseX, mouseY, partialTick)`.
- **Detach** (`onDetach(Screen)`) — unregisters via `menuKit$removeWidget` so vanilla's pipeline cleans up.

**Why wrapping over reimplementing:**

| Concern | Wrap | Reimplement |
|---|---|---|
| Drag tracking | Free (`onClick` → `onDrag` → `onRelease`) | Reimplement press-tracks-release-from-anywhere logic |
| Keyboard nav | Free (Enter to enable + arrows step pixel-by-pixel + full narration) | Reimplement key dispatch + canChangeValue toggle + narration plumbing |
| Cursor changes | Free (`RESIZE_EW` while dragging, `POINTING_HAND` while hovering) | Reimplement per-frame cursor request via `requestCursor` |
| Sprite rendering | Free (`widget/slider*` + handle composite) | Maintain hard-coded sprite IDs + render math |
| Value clamping | Free (`Mth.clamp(d, 0, 1)` in setValue) | Implement clamp + boundary edge-case handling |
| Sound on release | Free (vanilla widget click sound on `onRelease`) | Wire SoundManager.play |
| Future vanilla improvements | Inherited automatically | Manual port |
| Architectural risk | Same lifecycle bridge as TextField (already shipped) | Drag/keyboard/narration edge-case bug risk |

The lifecycle bridge cost is zero — TextField already shipped the primitive (`PanelElement.onAttach/onDetach` + `ScreenAccessor` mixin). Slider is the second consumer of that pattern. No new primitives in this phase.

---

## 3. Inherited primitives — no new mechanism work

Slider is the first phase since lifecycle hooks shipped (14d-3) where the wrap-vanilla-widget pattern can be applied without surfacing new primitive gaps. The infrastructure is in place:

- `PanelElement.onAttach(Screen)` / `onDetach(Screen)` — defaults no-op; wrapped-widget elements override.
- `ScreenAccessor` mixin — exposes `addWidget` / `removeWidget` for input-dispatch-only registration (avoiding the renderables-list "panel background covers widget" trap from 14d-3).
- `MenuKitScreen` / `MenuKitHandledScreen` — already iterate panels' elements at init/removed, fire onAttach/onDetach.
- `ScreenPanelAdapter` — already fires lifecycle hooks for region-based + lambda-path adapters.

**Slider work is purely additive** — one new file (`core/Slider.java`), one builder, one private inner subclass of `AbstractSliderButton`. Total LOC delta on library side: ~200.

---

## 4. API shape

### Builder pattern (matches TextField/ScrollContainer style)

```java
Slider slider = Slider.builder()
    .at(0, 0)
    .size(120, 20)
    .value(() -> myValue, v -> myValue = v)             // Supplier<Double> + DoubleConsumer; required
    .label(v -> Component.literal(String.format(         // Optional; updates per-value-change
            "Brightness: %.0f%%", v * 100)))
    .build();
```

### Lens pattern (Principle 8) — Supplier+Consumer, matching ScrollContainer

`value(Supplier<Double>, DoubleConsumer)` is required. Library reads the supplier each frame to sync the wrapped slider's internal `value` field; library calls the Consumer when the user drags / keys to update the consumer's state.

**Why Supplier+Consumer over TextField's Consumer-only + setValue:**

- ScrollContainer precedent — both ScrollContainer (continuous offset 0-1) and Slider (continuous value 0-1) are continuous-value controls; same lens shape gives a cohesive library.
- Programmatic external updates work cleanly — server pushes a settings update → consumer state changes → slider auto-syncs on next frame via supplier-pull. No imperative setValue dance.
- Reset-to-default fits the pattern naturally — consumer's reset button writes consumer state directly; slider follows.
- Vanilla's internal-value model + `setValue(d)`'s `if (e != this.value) applyValue()` guard means supplier-pull doesn't fire spurious `onChange` callbacks when supplier returns the already-stored value. Idempotent sync.

The TextField pattern (Consumer-only + setValue) was right for TextField specifically because the library held the value (vanilla EditBox stores the string internally with no obvious supplier hook); Slider can use the supplier-pull pattern because the consumer naturally holds value as a `double` and vanilla's `setValue`-with-changed-guard is supplier-pull-friendly.

### In-track label — `.label(DoubleFunction<Component>)`

Vanilla `AbstractSliderButton` renders the message text inside the slider track via `renderScrollingStringOverContents`. The displayed text updates whenever `updateMessage()` is called (which the wrap calls after every value change). This is the canonical vanilla pattern — slider IS the label.

`.label(DoubleFunction<Component>)` consumer-supplied function called each value change to compute the displayed text:

```java
.label(v -> Component.literal(String.format("Volume: %.0f%%", v * 100)))
.label(v -> Component.literal("FOV: " + (int)(30 + v * 80)))   // 30-110 range
.label(v -> Component.empty())                                  // No label (default)
```

Default: `v -> Component.empty()` (no in-track text). Consumer can compose an external TextLabel beside the slider if they want a label OUTSIDE the slider — but in-track is the natural shape.

This **diverges** from the advisor's brief read ("consumer composes a TextLabel beside the slider with supplier-driven text. Library doesn't bake in label rendering."). The divergence is principled: vanilla bakes the label INTO the slider track; following vanilla makes the wrap consistent. External TextLabels remain available for consumer composition; the in-track label is the additional surface.

### Narration — auto-derived from `.label()`

Vanilla's `createNarrationMessage()` returns `"Slider: " + getMessage()` (translated from `gui.narrate.slider`). With our `.label()` wired into `updateMessage()`, narration automatically reads "Slider: Volume: 50%" — the label function output. Works without consumer effort.

No separate `.narrationLabel(Component)` override exposed; vanilla doesn't expose one either, and following vanilla keeps the wrap thin (per Trevor's "follow vanilla anywhere we can" framing).

### Range model — normalized 0-1 only in v1

Internal value is normalized [0.0, 1.0]. Consumer maps to their domain externally:

```java
// 30-110 FOV range:
.value(() -> (fov - 30) / 80.0, v -> fov = (int)(30 + v * 80))

// 0-100 percent:
.value(() -> percent / 100.0, v -> percent = (int)(v * 100))
```

`.range(min, max)` builder method NOT shipped in v1. If 2+ consumers ask for the convenience method (eliminating the divide/multiply lines), fold inline. The current cost is one math line per direction; not a hard surface gap.

### Step / discrete — continuous in v1

Continuous (any double in [0, 1]). For discrete (snap-to-N-steps), consumers compose via the lens:

```java
.value(() -> currentStep / 9.0,                           // 10 discrete values 0..9
       v -> currentStep = (int)Math.round(v * 9))
```

`.steps(int)` builder method NOT shipped in v1. Consumer-side snap is one Math.round call; not a hard surface gap. Fold inline if 2+ consumers ask. Vanilla doesn't ship discrete-step natively either — vanilla precedent agrees.

---

## 5. Answers to the 10 load-bearing questions

### 5.1 Vanilla precedent — wrap AbstractSliderButton

Done — see §2. Vanilla provides drag, keyboard, narration, sprites, cursor, sound, value clamping. Wrap inherits all of it.

### 5.2 Range model — normalized 0-1, consumer maps externally

Internal value [0.0, 1.0] (matches vanilla `AbstractSliderButton.value`, matches ScrollContainer). Consumer maps to their domain in the supplier/consumer. Optional `.range(min, max)` deferred until evidence.

### 5.3 Step / discrete-vs-continuous — continuous v1

Continuous (any double in [0, 1]). Consumer-side snap via lens is the v1 path for discrete. Optional `.steps(int)` deferred until evidence.

### 5.4 Lens shape — Supplier+Consumer (matching ScrollContainer)

`Supplier<Double> + DoubleConsumer`. Per-frame supplier-pull keeps the wrapped slider in sync with consumer state for programmatic external updates; consumer accepts on user input. Vanilla's `setValue`-with-changed-guard makes the sync idempotent (no spurious onChange fires).

**No imperative `.setValue(double)` escape hatch.** Consumer-as-source-of-truth means there's no "library holds state, consumer pushes in" gap to fill — consumer just changes their own state and the slider follows. This contrasts with TextField (Consumer-only + setValue), where the library held EditBox state and consumer needed setValue to push in.

### 5.5 Visual style — vanilla SliderButton (inherited from wrap)

Wrap = vanilla style automatically. `widget/slider` + `widget/slider_handle` sprites; full-width track with 8px-wide handle; in-track label text; `widget/slider_highlighted` + `widget/slider_handle_highlighted` for focused / dragging variants. Matches vanilla OptionsScreen sliders pixel-for-pixel.

Different from ScrollContainer's "small handle in inset" (which is the vanilla scrollbar pattern, distinct from slider). Two different vanilla primitives; both used as-is.

### 5.6 Drag tracking mechanism — inherited

Vanilla's `onClick` → sets `dragging = true`, calls `setValueFromMouse`. Vanilla widget framework dispatches `onDrag` while button is held (no MenuKit-side drag-tracking machinery). `onRelease` clears dragging + plays sound.

Critical difference from ScrollContainer's manual-drag-tracking (which had to reach into Fabric's `allowMouseRelease` hook): vanilla `AbstractWidget` already implements drag dispatch internally. Slider doesn't need MenuKit's drag-tracking layer because it doesn't go through MenuKit's input dispatch — vanilla's screen widget pipeline owns it.

### 5.7 Keyboard / accessibility — keep vanilla's built-in

Vanilla provides:
- **Enter / Space** (`KeyEvent.isSelection()`) — toggles `canChangeValue` (the keyboard-edit-mode flag); when true, slider visually highlights to indicate keyboard mode.
- **Left / Right arrow** — when `canChangeValue` is true, steps value by `±1 / (width - 8)` (one pixel of track motion per press).
- **Tab navigation** — inherited via `addWidget` registration; player tabs between sliders, text fields, and other vanilla widgets.
- **Narration** — full screen-reader support via `createNarrationMessage()` + `updateWidgetNarration()` — announces "Slider: Volume: 50%" and the keyboard usage hints.

**Decision: KEEP vanilla keyboard support.** This **diverges** from the advisor's brief read ("defer keyboard, mouse-only v1, fold on evidence — matches ScrollContainer's deferred-keyboard pattern"). The divergence is principled: vanilla provides keyboard for free with the wrap; opting OUT would require overriding `keyPressed` to no-op, which costs more than letting it ride. ScrollContainer's keyboard deferral was structural — ScrollContainer doesn't wrap a vanilla widget that provides keyboard, so keyboard would have meant new MenuKit-side machinery.

Slider has the opposite shape: keyboard comes for free; cutting it would be active work for negative value. Player-facing accessibility win at no library cost.

### 5.8 Value display — in-track via vanilla `.label(DoubleFunction<Component>)`

Vanilla's `getMessage()` / `updateMessage()` pattern renders text inside the slider track. We expose this as `.label(DoubleFunction<Component>)` builder method that gets called on every value change.

**Decision: in-track label as the primary surface.** This **diverges** from the advisor's brief read ("consumer composes a TextLabel beside the slider with supplier-driven text. Library doesn't bake in label rendering."). The divergence is principled: vanilla DOES bake label rendering into AbstractSliderButton; following vanilla makes the wrap natural. External TextLabels remain available for consumers wanting separate-label patterns; in-track is the additional surface.

Both shapes coexist:
```java
// Pattern A: in-track label (vanilla shape)
Slider slider = Slider.builder()
    .label(v -> Component.literal("Brightness: " + (int)(v * 100) + "%"))
    .build();

// Pattern B: external TextLabel via supplier
Slider slider = Slider.builder().build();
TextLabel label = new TextLabel(0, 24,
    () -> Component.literal("Brightness: " + (int)(brightness * 100) + "%"),
    TextLabel.COLOR_LIGHT, false);
```

### 5.9 Cross-context applicability

| Context | Applies | Reasoning |
|---|---|---|
| **MenuContext** | Yes | Settings panels, brightness/opacity controls, etc. Region-based ScreenPanelAdapter or `MenuKitScreenHandler`. |
| **StandaloneContext** | Yes | `MenuKitScreen`-based screens (e.g., a settings screen with multiple sliders). |
| **SlotGroupContext** | No | Slot-group anchors are for slot-related decorations. Slider on a slot-group anchor is shape-mismatched. |
| **HudContext** | No | HUDs are render-only (no input dispatch). Slider fundamentally requires input. |

Same shape as TextField. Slider doesn't depend on slot mechanics or HUD coordinate space — context-agnostic at the element level. The container determines applicability per Principle 5.

### 5.10 Composition with M9 / M8

**M9 opacity:** Slider inside an opaque panel — drag begins inside the slider's bounds (`onClick` fires on the slider), continues via vanilla's `onDrag` (which doesn't care about MenuKit's input dispatch — it's vanilla widget framework owning the drag), ends on release. Clicks outside the slider but inside the opaque panel don't blur the drag (vanilla's drag mechanism survives focus changes within the screen). Clicks outside the opaque panel: M9's mouse mixin eats the click and dispatches to the opaque panel's elements; the slider doesn't see it (correct — drag is bounded to its own widget instance).

**Verification:** the M9 mouse mixin pre-empts vanilla's `MouseHandler.onButton`. For sliders that live INSIDE an opaque panel, M9 dispatches the click to the panel's adapter elements via `dispatchOpaqueClick`, which calls `element.mouseClicked(...)`. Slider's `mouseClicked` defaults to `false` (`PanelElement` default — not overridden), so the click would fall through to vanilla. But since the slider is registered via `addWidget`, vanilla's screen widget pipeline ALSO sees the click via Fabric's `allowMouseClick` hook → dispatches to the slider's `onClick` directly. **This needs smoke verification** — whether the M9 dispatch + vanilla widget dispatch interact correctly, or whether Slider needs to override `mouseClicked` to manually trigger drag.

If smoke surfaces a gap, the fold is straightforward — Slider's `mouseClicked` returns `slider.mouseClicked(mouseX, mouseY, button)` (forwarding to the wrapped widget's input). This mirrors how TextField currently doesn't need to override `mouseClicked` (focus is handled by vanilla's pipeline). Smoke verifies whether Slider needs the same treatment.

**M8 layout:** Slider is a `PanelElement` with fixed width/height (consumer-declared via `.size(w, h)`). Composes naturally with Column/Row/Grid. Auto-sizing the wrapping Panel works (per the panel-auto-size convention) — the panel sizes to fit the slider plus other elements.

---

## 6. Composition with the testing convention (14d-2.7)

New Slider smoke registers as ONE Hub entry in validator (no new chat command, no library-side scaffolding).

**Smoke surface shape: standalone screen** (per the 14d-3 lesson — inventory-decoration smokes risk clipping; standalone gives breathing room for slider + label + companion controls).

**Validator surface (~3 files touched):**

- `MenuKitSmokeState.java` — add `static volatile double sliderValue = 0.5;` (consumer-state mirror).
- `SliderSmokeScreen.java` (new, ~80 LOC) — `MenuKitScreen` subclass with header + Slider (with `.label()` showing live value) + supplier-driven TextLabel below (proves Supplier+Consumer round-trip works from the consumer side too) + Reset-to-default Button + Back-to-Hub Button.
- `HubHandler.java` — add `addButton(...)` block at the TOP for "Slider (continuous value)" → `() -> Minecraft.getInstance().setScreen(new SliderSmokeScreen())`.

**Pure-logic auto-tests added to `ContractVerification.runAll`:**

- **M17 Slider builder validation** (~10 cases) — required fields (size, value lens), null guards, chainable builder returns, IllegalStateException on missing required.

(M16 was TextField builder validation, shipped 14d-3.)

Convention's structural test sentence holds: one smoke screen (validator), one Hub entry (validator), one auto-check probe (library) — no new chat command, no library-side test scaffolding.

---

## 7. Implementation outline

| File | Role | LOC |
|---|---|---|
| `core/Slider.java` (new) | PanelElement subclass; builder; wraps internal MenuKitSlider; `onAttach`/`onDetach` lifecycle + per-frame supplier-pull | ~200 |
| `verification/ContractVerification.java` (modify) | M17 Slider builder validation probe | +50 |
| `validator/.../scenarios/smoke/MenuKitSmokeState.java` (modify) | sliderValue field | +1 |
| `validator/.../scenarios/smoke/SliderSmokeScreen.java` (new) | Standalone smoke screen with Slider + value label + Reset + Back-to-Hub | ~80 |
| `validator/.../scenarios/hub/HubHandler.java` (modify) | Add Hub entry at top of list (newest-first) | +5 |
| `Design Docs/Elements/SLIDER.md` | This file | (new) |
| `Design Docs/PHASES.md` | Marker advance: 14d-3 closed → 14d-4 (slider) shipped | +5 / -1 |
| `Design Docs/Phases/14d-4/REPORT.md` | Phase report on close | (new) |

Total approximate: ~+340 LOC, ~7 files touched. Lighter than TextField (~+450 LOC, ~12 files) because no new primitives — lifecycle infrastructure already in place.

---

## 8. Open questions for advisor verdict

**Q1 (§4 / §5.4 lens shape — Supplier+Consumer).** Use `Supplier<Double> + DoubleConsumer` (matches ScrollContainer; per-frame supplier-pull keeps slider in sync with consumer state). No imperative `setValue` escape hatch (consumer-as-source-of-truth means there's no gap to fill).

Implementer pull: **Supplier+Consumer; no setValue**. Matches advisor's read.

**Q2 (§4 / §5.8 in-track label — `.label(DoubleFunction<Component>)`).** Expose vanilla's `getMessage()`/`updateMessage()` pattern as a builder method. Diverges from advisor's brief read ("library doesn't bake in label rendering; consumer composes external TextLabel"). Justification: vanilla DOES bake label rendering into AbstractSliderButton; following vanilla makes the wrap natural. External TextLabels remain available; in-track is the additional surface.

Implementer pull: **ship `.label(DoubleFunction<Component>)`**; diverges from advisor's brief; reasoning above.

**Q3 (§5.7 keyboard support — keep vanilla's built-in).** Vanilla provides Enter-to-enable + arrow-keys-to-step + narration for free. Advisor's brief said "defer keyboard, mouse-only v1." Diverges: cutting vanilla's keyboard would be active override work for negative value; keeping it is the no-op that delivers accessibility for free.

Implementer pull: **keep vanilla's keyboard support**. Diverges from advisor's brief; reasoning above.

**Q4 (§4 / §5.2 range model — normalized 0-1; defer `.range(min, max)`).** v1 ships normalized only; consumer maps externally with one-line math. Optional `.range(min, max)` builder method folded inline if 2+ consumers ask.

Implementer pull: **normalized v1; defer `.range`**. Matches advisor's read.

**Q5 (§4 / §5.3 step/discrete — continuous v1; defer `.steps(int)`).** v1 ships continuous; consumer-side snap via lens (Math.round) is a one-line workaround. Optional `.steps(int)` folded inline if evidence emerges.

Implementer pull: **continuous v1; defer `.steps`**. Matches advisor's read; vanilla precedent is also continuous.

**Q6 (§5.10 M9 + click dispatch interaction — verify in smoke).** Slider lives inside potentially-opaque panels. M9's mouse mixin pre-empts vanilla's onButton dispatch. Need to verify: does the M9 dispatch path correctly route clicks to the slider via vanilla's widget pipeline (`addWidget` registration), or does Slider need to override `mouseClicked` to manually forward to the wrapped widget? Smoke is the proof. If smoke surfaces a gap, fold is straightforward (Slider.mouseClicked → slider.mouseClicked forwarding).

Implementer pull: **verify in smoke; fold inline if needed**. Same shape as TextField's Q6 in 14d-3 (which passed without intervention).

**Q7 (§4 visibility-driven attach/detach — defer per 14d-3 precedent).** v1 fires onAttach at screen init regardless of panel visibility; onDetach at screen close. Mid-screen visibility changes don't re-attach. Same gotcha + same recommended consumer pattern (blur via `screen.setFocused(null)` before hiding panel) as TextField.

Implementer pull: **defer; reuse TextField's gotcha-doc text**. Already-deferred pattern from 14d-3.

**Q8 (Phase scope — 14d-4 = Slider only, separate from 14d-5 Dropdown).** Per advisor's entry brief — confirmed. Dropdown has more design surface (popover + option list + click-outside-closes + opacity composition + z-order); separate phase. Slider is bounded; ships clean.

Implementer pull: **single-element phase; matches advisor's read**.

---

## 9. What this design does NOT do

- **`.range(min, max)` builder convenience** — deferred; one-line math workaround in consumer. Fold inline on evidence.
- **`.steps(int)` discrete-snap convenience** — deferred; consumer-side snap via Math.round in onChange is the workaround. Fold inline on evidence.
- **Imperative `.setValue(double)` escape hatch** — not needed (Supplier+Consumer means consumer owns state; just write to your own state). If a use case surfaces where library-internal-state push is genuinely needed, fold on evidence.
- **Custom handle / track sprites** — vanilla sprites only in v1. Per-consumer sprite override would be a primitive gap; fold on evidence.
- **Custom handle width / height** — vanilla constants (8px handle, 20px DEFAULT_HEIGHT) only. Different handle sizes would require reimplementing render path; defer.
- **Two-handle range slider** (e.g., min-max selection) — separate primitive; nothing to do with single-handle slider. Filed in DEFERRED.md if/when surfaced.
- **Vertical orientation** — vanilla AbstractSliderButton is horizontal-only. Vertical would require reimplementing render + drag math. Defer.
- **Modals containing sliders** — same M9 keyboard-mixin trap as TextField (modal eats keystrokes including arrow keys). Defer to evidence; same fold pattern as TextField's deferred Q4.
- **Visibility-driven lifecycle** — same TextField gotcha; same recommended consumer pattern. Defer.

---

## 10. Standing by

Round-1 design ready for advisor verdict on Q1–Q8. Implementer pulls per above. Three Qs (Q2, Q3, Q5) diverge from advisor's brief read; reasoning surfaced explicitly for advisor to push back or accept. Five Qs (Q1, Q4, Q6, Q7, Q8) match advisor's read or are confirmation requests.

Aim: 1 round + inline. Vanilla precedent investigated thoroughly; design follows the centralizes-the-behavior heuristic; no new primitives required (lifecycle hooks already shipped 14d-3); scope bounded.
