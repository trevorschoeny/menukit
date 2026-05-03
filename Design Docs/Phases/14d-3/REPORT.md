# Phase 14d-3 — Close-out REPORT (TextField)

**Status: complete.** TextField shipped as a `PanelElement` wrapping vanilla `EditBox` via composition. Selection model, IME, validation, copy/paste, word navigation, cursor blink, focus — all inherited from EditBox without reinvention. New `PanelElement.onAttach` / `onDetach` lifecycle hooks added; `MenuKitScreen` / `MenuKitHandledScreen` / `ScreenPanelRegistry` all dispatch them. One round + two smoke fold-inline findings (standalone-screen UX + render-order/panel-cover bug).

---

## Executive summary

Phase 14d-3 ships text input — single-line editable field. Heaviest design surface in 14d (selection model, IME, validation, focus, submission, read-only mode). Advisor's entry brief mandated *"find the vanilla flag/primitive that already centralizes the behavior"* aggressively for this phase; investigation confirmed vanilla `EditBox` is comprehensive (~600 LOC of tested mechanism) and the right thing to wrap.

**Core architectural decision: wrap vanilla EditBox via `TextField` PanelElement adapter.**

Inherited cleanly from EditBox:
- Selection model — `cursorPos` + `highlightPos` (anchor + caret)
- IME — transparent via vanilla's charTyped pipeline (CharacterEvent routes to focused widget)
- Validation — `Predicate<String>` filter
- Copy/paste/cut/select-all — platform-correct via `KeyEvent.isCopy/Paste/Cut`
- Word navigation — Ctrl+arrow/delete
- 300ms cursor blink, hint text, suggestion text, IBEAM hover cursor
- Read-only mode (`editable(false)`)

MenuKit's role narrows to layout integration + lifecycle (Library-not-platform clean: vanilla owns the input mechanism; library owns where the EditBox lives in a Panel).

**Round 1 closed cleanly** with six advisor sign-offs + one push on Q5 (added `setValue` imperative escape hatch) + two doc-note nits (DEFERRED entry for modals-with-text-input + javadoc gotcha for visibility-hide). All folded inline before implementation.

---

## Architecture decisions

### Wrap, don't reimplement (Q2 verdict)

EditBox's IME handling alone justifies wrapping. CharacterEvent isn't routed through MenuKit's input dispatch today; reimplementing IME would require plumbing CharacterEvent into the PanelElement protocol — substantial primitive gap. Wrapping inherits IME for free via vanilla's `Screen.charTyped → focused widget.charTyped` pipeline.

### PanelElement lifecycle hooks (new primitive)

`PanelElement.onAttach(Screen)` + `onDetach(Screen)` defaults no-op. Fired by:
- `MenuKitScreen` at init/removed (StandaloneContext)
- `MenuKitHandledScreen` at init/removed (MenuContext-native)
- `ScreenPanelRegistry.onScreenInit` at init via Fabric `ScreenEvents.AFTER_INIT`; detach via `ScreenEvents.remove(screen)` (region-based MenuContext)
- `ScreenPanelRegistry.registerLambdaActive` / `unregisterLambdaActive` (lambda-path)

TextField overrides to register/unregister its wrapped `EditBox` for vanilla's input pipeline (charTyped/keyPressed/focus/tab navigation).

### `Screen.addWidget` via accessor mixin (smoke fold-inline finding #2)

TextField uses `Screen.addWidget` (children + narratables only — NOT renderables) via new `ScreenAccessor` mixin (`@Invoker`). Renders the EditBox manually in `render()` AFTER the panel background.

Reason: registering as a renderable (via `addRenderableWidget` or Fabric's `Screens.getButtons`) draws the EditBox during `super.render`, which runs BEFORE panel backgrounds in `MenuKitScreen.render`. Result: panel background covers the EditBox visually (hover-cursor still works since hit-testing uses bounds, but the text field is invisible). Surfaced via smoke; folded inline.

### Standalone smoke screen, not inventory decoration (smoke fold-inline finding #1)

Initial smoke wireup placed the TextField panel at `LEFT_ALIGN_BOTTOM` of inventory. Field clipped against inventory chrome — invisible. Switched to dedicated `TextFieldSmokeScreen` (MenuKitScreen subclass) with header + field + Clear + Back-to-Hub buttons. Hub entry changed from toggle to button (opens screen client-side via `setScreen`).

### Submission via subclass (Q3 verdict)

`MenuKitEditBox extends EditBox` — private nested class in TextField. Overrides `keyPressed` to capture Enter (via `KeyEvent.isConfirmation()`) and fire registered `onSubmit` callback. Per-element scoped (only affects MenuKit's wrapped EditBoxes); subclass over mixin.

### Lens + imperative escape (Q5 push)

- **Lens** (canonical): `onChange(Consumer<String>)` fires on every value mutation. Consumer holds the value; library writes via callback.
- **Imperative escape**: `TextField.setValue(String)` for programmatic mutation (Clear button, undo/reset, server-pushed update). Same precedent as Panel `showWhen`/`setVisible`.

Original Q5 was Consumer-only; advisor pushed back: lens-only API has no programmatic write path. Folded inline pre-implementation.

### Modals-with-text-input deferred (Q4 verdict)

v1 ships TextField for non-modal panels. Inside a `tracksAsModal` panel, M9's keyboard mixin eats keystrokes (except Escape) before vanilla's pipeline routes them to the focused widget. Refining the modal-keyboard mixin is a known fold-on-evidence pattern (same shape as 14d-2.5's symmetric press/release). Filed in `DEFERRED.md` 14d-3 follow-ons with trigger condition + ~15 LOC estimate.

### Visibility-driven lifecycle deferred (Q7 verdict + javadoc gotcha)

v1 fires onAttach at screen init regardless of panel.isVisible(); onDetach at screen close. Hide/show transitions mid-screen don't fire detach/re-attach. Vanilla widget framework handles invisible widgets correctly via setVisible. Javadoc gotcha note added per advisor: consumers should blur the field before hiding the panel.

---

## What shipped

### Library (menukit/)

| File | Role |
|---|---|
| `core/TextField.java` (new, ~280 LOC) | PanelElement wrapping EditBox. Builder pattern, lens onChange + onSubmit, setValue imperative escape, MenuKitEditBox private subclass for Enter capture |
| `core/PanelElement.java` (modify, +50 LOC) | Added `onAttach(Screen)` + `onDetach(Screen)` defaults |
| `screen/MenuKitScreen.java` (modify, +25 LOC) | onAttach/onDetach loop at init/removed |
| `screen/MenuKitHandledScreen.java` (modify, +25 LOC) | Same |
| `inject/ScreenPanelRegistry.java` (modify, +30 LOC) | onAttach in onScreenInit; onDetach via ScreenEvents.remove; lambda-path lifecycle in registerLambdaActive/unregister |
| `mixin/ScreenAccessor.java` (new) | `@Invoker` for `Screen.addWidget` + `removeWidget` |
| `verification/ContractVerification.java` (modify, +180 LOC) | M16 TextField builder validation probe (~12 cases) |

### Validator (validator/)

| File | Role |
|---|---|
| `scenarios/smoke/MenuKitSmokeState.java` (modify) | Added `textFieldValue` (per-keystroke) + `textFieldLastSubmitted` (Enter-only) lens targets |
| `scenarios/smoke/TextFieldSmokeScreen.java` (new) | Dedicated MenuKitScreen with header + TextField + supplier-driven "Last submitted" label + Clear + Back-to-Hub |
| `scenarios/hub/HubHandler.java` (modify) | TextField entry as button (opens TextFieldSmokeScreen client-side via setScreen) at top of list (newest-first) |

### Documentation

- `Design Docs/Elements/TEXT_FIELD.md` — full round-1 design + Q1–Q7 verdicts + inline folds
- `Design Docs/Phases/14d-3/REPORT.md` — this file
- `Design Docs/DEFERRED.md` — 14d-3 follow-ons (modals-with-text-input + visibility-driven lifecycle)
- `Design Docs/PHASES.md` — current marker advanced 14d-2.7 → 14d-3

### V16 verification

| Probe | Cases | Coverage |
|---|---|---|
| **M16 TextField builder** | ~12 | Required-field validation (size missing/zero/negative), maxLength validation, null guards on label/initialValue/hint/filter/onChange/onSubmit, builder fluency (chainable returns) |

The Test button's contract sweep now reports **18 library contracts**.

V17 (TextField value lifecycle) deferred — TextField construction touches `Minecraft.getInstance().font` which isn't safely accessible from server thread. Same scoping as V11 dialog-builder probe. Visual smoke covers value lifecycle + onChange + onSubmit + setValue.

---

## What didn't ship / deferred

- **Modals containing TextField** — M9 keyboard mixin eats keystrokes when `tracksAsModal` is up. Filed in DEFERRED.md with trigger + ~15 LOC fold-inline estimate.
- **Visibility-driven onAttach/onDetach** — v1 fires at screen lifecycle boundaries only. Filed in DEFERRED.md with javadoc gotcha for consumers.
- **Multi-line TextArea** — separate primitive; existing DEFERRED.md entry.
- **Auto-complete / dropdown suggestions** — vanilla `CommandSuggestions` exists; out of scope.
- **Validation + error display** — filter only gates input; consumer renders their own error label adjacent.

---

## Process notes

**One round + two smoke fold-inline findings.** Round 1 closed with six advisor sign-offs + one push (Q5 setValue) + two doc-note nits (Q4 DEFERRED + Q7 javadoc). All folded inline before implementation. Implementation surfaced two smoke findings:

1. **Standalone screen vs inventory decoration** (smoke #1) — initial wireup at `LEFT_ALIGN_BOTTOM` clipped the TextField against inventory chrome. Switched to dedicated `TextFieldSmokeScreen` (MenuKitScreen subclass) per Trevor's smoke feedback. Hub entry changed from toggle to button.

2. **`Screen.addWidget` via accessor mixin** (smoke #2) — initial implementation used Fabric's `Screens.getButtons(screen).add(editBox)` which adds to the renderables list. EditBox rendered during `super.render` (BEFORE panel backgrounds in `MenuKitScreen.render`), got covered by the panel background. Hover-cursor change still worked (hit-test uses bounds). Surfaced via smoke ("cursor changes but no field visible"). Created `ScreenAccessor` mixin with `@Invoker` for `Screen.addWidget`/`removeWidget`. TextField now registers for input dispatch only, renders the EditBox manually in `render()` after the panel background.

**Calibration heuristic re-applied:** *"Find the vanilla flag/primitive that already centralizes the behavior"* — EditBox investigated end-to-end before drafting; the wrap decision saved substantial primitive-gap-fold work (no CharacterEvent plumbing into PanelElement, no copy/paste platform-specific code, no cursor-blink timing). The advisor flagged this phase as the heaviest 14d design surface; the wrap decision turned it into a tractable adapter problem.

**New calibration heuristic surfaced:** *"Render order matters when wrapping vanilla widgets — auto-render via the screen's renderable list draws BEFORE custom-pipeline rendering. If the custom pipeline draws backgrounds, the widget gets covered. Manual render in the custom pipeline is the right shape."* Saving as inline lesson.

---

## Verification

### Automated

Inventory Test button runs 18 library contracts (was 17) + 5 validator scenario aggregators. All PASS expected:

| Contract | Result |
|---|---|
| 1–14 | Unchanged from 14d-2.7 close |
| **15 M15 lambda lifecycle** | 5/5 ✓ |
| **16 M16 TextField builder** (new) | 12/12 |

Plus 5 validator scenario aggregators (V2/V3/V5.5/V6/V7) — unchanged.

### Visual smoke

Inventory Test button → Hub → click "TextField" entry → standalone `TextFieldSmokeScreen` opens. Verified:

- Click into field → IBEAM cursor, focus, blink animation, hint text disappears.
- Type → text renders (panel background no longer covers via fold-inline #2). `onChange` fires per-keystroke; updates `textFieldValue`.
- Press Enter → `onSubmit` fires; chats current value; updates `textFieldLastSubmitted`. Supplier-driven "Last submitted" label below the field updates live.
- Click Clear → `setValue("")` empties field; `onChange` fires; submitted-label unchanged (Clear doesn't fire submit).
- Close + reopen via Back-to-Hub → field empty (fresh EditBox); submitted-label persists with last submitted value (static state survives).
- IME would route via vanilla pipeline (not exercised in test environment but transparent by design).

### Build

Full monorepo (menukit + 6 consumer mods + dev) builds clean.

---

## Phase 14d-4 entry conditions

Per `PHASES.md` §14d sequencing — remaining 14d palette:
- Slider — continuous-value control with draggable handle
- Dropdown — single-selection opener list

Both designs against the locked TextField + opacity + testing convention from day one. Slider likely lighter (less vanilla mechanism to wrap; selection model is just "drag handle to set float"). Dropdown likely involves opening a popover-style sub-panel (compose with M9 opacity flags — opaque popup that doesn't dim or track-as-modal).

---

## Diff summary

3 new files (TextField.java, ScreenAccessor.java, TextFieldSmokeScreen.java + REPORT folder), 9 modified, 0 deleted. Approximate net: +800 / -50 LOC.

**Phase 14d-3 closed.**
