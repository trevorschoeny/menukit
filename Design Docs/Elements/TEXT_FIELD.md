# TextField — Phase 14d-3 design

**Status: round 1 draft, pre-advisor review.**

Single-line editable text field for MenuContext + StandaloneContext panels. Wraps vanilla `EditBox` rather than reimplementing the input mechanism.

---

## 1. Intent

Phase 14d-3 ships text input — single-line editable field. Heaviest design surface in 14d (selection model, IME, validation, focus, submission, read-only mode). The advisor's entry brief mandates: investigate vanilla precedent first, apply *"find the vanilla flag/primitive that already centralizes the behavior"* aggressively.

EditBox investigation conclusion: **vanilla EditBox is comprehensive and the right thing to wrap.** ~600 LOC of vanilla-tested mechanism covering selection model (cursorPos + highlightPos), IME (transparent via charTyped pipeline), validation (Predicate<String> filter), responder callback, copy/paste/cut/select-all (platform-correct via KeyEvent.isCopy/etc), word navigation, scroll-to-cursor, 300ms cursor blink, IBEAM cursor request, hint text, suggestion text, bordered/unbordered modes, focused/unfocused border styling.

Reimplementing risks bugs around IME edge cases vanilla already solved. Library-not-platform clean: vanilla owns the input mechanism; MenuKit owns layout integration + lifecycle. Honest dogfooding angle: MenuKit's role for text input is to MAKE EditBox composable into Panel + M8 layout + M9 opacity — not to compete with EditBox.

---

## 2. Core architectural decision — wrap vanilla EditBox

`TextField` is a `PanelElement` that holds an internal `EditBox` (composition, not subclassing). Lifecycle:

- **Construction** — TextField builds its EditBox at construction time with the consumer's settings (width, height, hint, max length, validator, responder, editable mode, bordered).
- **Attach** — when the screen opens with the panel visible (or when the panel goes visible), TextField registers its EditBox via `screen.addRenderableWidget(editBox)` so vanilla's screen widget pipeline routes charTyped/keyPressed to it when focused.
- **Render** — per frame, TextField updates EditBox's screen coords (`editBox.setX/setY`) to track the panel's current content origin. EditBox renders itself via vanilla's widget render pipeline.
- **Detach** — when the screen closes (or panel hides), TextField removes its EditBox from the screen so vanilla's pipeline cleans up.

**Why wrapping over reimplementing:**

| Concern | Wrap | Reimplement |
|---|---|---|
| Selection model | Free (cursorPos + highlightPos) | Reimplement int-offset selection arithmetic |
| IME | Free (vanilla pipeline routes CharacterEvent → focused widget) | Plumb CharacterEvent into MenuKit input dispatch |
| Copy/paste/cut | Free (KeyEvent.isCopy/Paste/Cut platform-correct) | Reimplement clipboard interaction + platform shortcut detection |
| Word navigation | Free (Ctrl+arrow/delete) | Reimplement getWordPosition + ctrl modifier handling |
| Cursor blink | Free (300ms phase from focusedTime) | Reimplement timing |
| Future vanilla improvements | Inherited automatically | Manual port |
| Architectural risk | Lifecycle bridge complexity | IME + edge-case bug risk |

The lifecycle bridge is the only non-trivial cost. It surfaces a real primitive-gap (PanelElement attach/detach hooks) — see §3.

---

## 3. New primitive — PanelElement lifecycle hooks

PanelElement currently has render + click + scroll + release dispatch. No attach/detach lifecycle. Most elements don't need it (rendering is stateless against screen lifecycle). TextField is the first element that DOES need it because:
- The wrapped EditBox must register with the screen via `addRenderableWidget` for vanilla's input pipeline to route to it.
- The EditBox must unregister when the screen closes / panel hides so vanilla's pipeline cleans up references.

Proposed addition to `PanelElement`:

```java
default void onAttach(Screen screen) {}
default void onDetach(Screen screen) {}
```

Default no-op. TextField overrides to call screen.addRenderableWidget / screen.removeWidget on its EditBox.

**Who calls these:**

- **MenuKitScreen** (StandaloneContext) — iterates panels' elements during `init()` after super.init(), calls onAttach(this) on each. Iterates again during `removed()`, calls onDetach(this).
- **MenuKitHandledScreen** (MenuContext-native) — same shape.
- **ScreenPanelAdapter** (vanilla-screen decoration) — region-based: hooks ScreenEvents.AFTER_INIT to call onAttach for each element in the adapter's panel; hooks ScreenEvents.AFTER_REMOVE for onDetach. Lambda-path: `.activeOn(screen, supplier)` triggers onAttach; `.deactivate(screen)` triggers onDetach.

Visibility-driven attach/detach (panel hides mid-screen-life) is more involved. For v1, attach happens when the screen opens regardless of panel.isVisible() — vanilla's widget framework handles invisible widgets correctly (renderWidget gates on isVisible internally). Detach only on screen close.

---

## 4. API shape

### Builder pattern (matches ConfirmDialog/AlertDialog/ScrollContainer style)

```java
TextField field = TextField.builder()
    .at(0, 0)
    .size(120, 20)
    .initialValue("hello")               // optional; default ""
    .maxLength(64)                       // optional; default 256
    .hint(Component.literal("type..."))  // optional; placeholder shown when empty + unfocused
    .filter(s -> s.length() <= 64)       // optional; Predicate<String>; default pass-all
    .onChange(value -> { ... })          // optional; Consumer<String>; fires on every value mutation
    .onSubmit(value -> { ... })          // optional; fires on Enter while focused
    .editable(true)                      // optional; default true
    .bordered(true)                      // optional; default true
    .build();
```

### Lens pattern (Principle 8)

`onChange` is the lens write — consumer holds the value, reads it whenever they want. Library calls `onChange(newValue)` after every mutation (typing, paste, delete, etc.). Consumer's onChange typically does `myValue = newValue` (or pushes to a server, validates further, etc.).

For initial value, `initialValue("hello")` sets the EditBox's value at construction. This is the only time the library writes to consumer state — after that, the consumer drives.

### Imperative escape hatch — `setValue(String)`

Per Q5 advisor verdict (round-1 push): the lens-only API has a real gap — no programmatic write path. Use case: consumer ships a "Clear" button adjacent to the field; clicking it should empty the input. Lens-only design has no way for consumer to push state INTO the field.

Resolution: `TextField.setValue(String)` imperative method delegates to the wrapped EditBox.setValue. Same precedent as Panel (`showWhen(supplier)` lens + `setVisible(boolean)` imperative) and Toggle. Two-API shape:

- **Lens** (canonical) — `onChange` write trigger; consumer state is the source of truth.
- **Imperative escape** — `setValue(String)` for programmatic mutation (clear button, reset to default, etc.).

Document explicitly: *"Imperative escape hatch. Canonical pattern is to keep consumer state authoritative via the `onChange` callback; use `setValue` only when programmatic mutation is the source of truth (e.g., a Clear button, undo/reset, server-pushed update)."*

### Submission

`onSubmit` fires when the player presses Enter while the TextField is focused. Implemented by overriding the wrapped EditBox's keyPressed (or via TextField's own keyPressed dispatch — see §5.4).

### Read-only

`editable(false)` passes through to EditBox.setEditable. Visual: text renders in greyed color; cursor still movable but typing is suppressed. Same pattern as vanilla.

---

## 5. Answers to the 10 load-bearing questions

### 5.1 Selection model

**Full selection (cursor + range), via vanilla EditBox.** EditBox tracks `cursorPos` (active caret) + `highlightPos` (selection anchor). When they differ, a selection range exists. Operations: `moveCursorTo(pos, withHighlight)` — `withHighlight=true` extends selection (anchor stays, caret moves); `withHighlight=false` collapses selection to caret. Inherited cleanly.

### 5.2 IME / international input

**Vanilla EditBox handles transparently via charTyped pipeline.** Vanilla's KeyboardHandler delivers IME-composed character events to the focused widget's `charTyped(CharacterEvent)`. EditBox.charTyped calls insertText with the character. For multi-codepoint composition (e.g., Japanese kanji), the OS's IME composes via separate channels; Minecraft only sees the final character(s).

By wrapping EditBox AND registering it via addRenderableWidget, IME works automatically — vanilla's screen widget pipeline routes charTyped to the focused EditBox without any MenuKit-side IME handling.

If we reimplemented, we'd need to plumb CharacterEvent into MenuKit's input dispatch — which doesn't currently route charTyped at all. That would be a substantial primitive gap.

### 5.3 Validation hooks

**Predicate<String> filter, passed to EditBox.setFilter.** Inherited shape. Filter is called BEFORE any value mutation; if filter rejects the candidate value, the mutation is skipped. Cleanest API:

```java
.filter(s -> s.matches("\\d+"))   // numeric only
.filter(s -> s.length() <= 8)     // bounded length (in addition to maxLength which is character count)
```

Rich validation (filter + error display) is deferred — MenuKit's TextField v1 just gates input. Consumer can render their own error label adjacent to the field.

### 5.4 Submission semantics

**`onSubmit` callback fires on Enter while focused.** Both Enter (key 257) and KP_Enter (key 335) — match vanilla's `KeyEvent.isConfirmation()` helper.

EditBox doesn't catch Enter; ChatScreen catches it at the screen level. For TextField (a PanelElement, not a Screen), we need to capture Enter while the EditBox is focused. Two options:

**(α) Mixin into EditBox.keyPressed** — HEAD-cancellable mixin on EditBox.keyPressed; if the EditBox is one of MenuKit's wrapped EditBoxes AND the key is Enter, fire the registered onSubmit and consume.

**(β) Subclass EditBox** — `MenuKitEditBox extends EditBox` overrides keyPressed to handle Enter before delegating.

Implementer pull: **(β)** subclass. No mixin needed; cleaner ownership. MenuKitEditBox is private nested class inside TextField (similar pattern to HubHandler's ToggleButton).

`onChange` (Consumer<String> responder) fires on every value mutation (typing, paste, delete, programmatic setValue). Wired via EditBox.setResponder. Consumer reads live value via `field.getValue()` any time, or via the onChange callback.

### 5.5 Focus model

**Click-to-focus inherited from EditBox.** Vanilla EditBox.onClick moves cursor to click position; vanilla widget framework handles focus on click via screen.setFocused.

**Tab navigation inherited from screen widget cycle** (after addRenderableWidget). Player can Tab between MenuKit TextFields and other vanilla widgets registered on the screen.

**Modal interaction (M9):** when a `tracksAsModal` panel is up, M9's keyboard mixin currently eats all keys except Escape. This breaks text input INSIDE a modal because the mixin consumes keystrokes before vanilla's pipeline routes them to the focused widget.

**Decision: defer modals-with-text-input to evidence.** v1 ships text fields on non-modal panels. If a future use case wants a text-input modal (e.g., "name your sandbox" prompt), refine the keyboard mixin to dispatch to focused element before eating — same shape as 14d-2.5's symmetric press/release fold-inline. File as DEFERRED.md entry with the trigger condition + architectural shape.

For non-modal cases (text field on inventory decoration, text field on standalone screen), keyboard mixin doesn't apply (it only fires when tracksAsModal is up). No regression.

### 5.6 Read-only mode

**`editable(boolean)` flag, passed to EditBox.setEditable.** Inherited. When `editable(false)`:
- Text renders in `textColorUneditable` (grey)
- Cursor stays movable; typing/paste/cut suppressed
- Hover cursor switches to NOT_ALLOWED instead of IBEAM

### 5.7 Visual feedback

**All inherited from EditBox:**
- 300ms cursor blink (vanilla's `(getMillis() - focusedTime) / 300L % 2L`)
- Hint text shown when empty + unfocused (DEFAULT_HINT_STYLE = dark grey)
- Suggestion text (greyed) shown after cursor when applicable
- Bordered mode: text_field sprite (focused vs unfocused variants)
- Selection highlight via `guiGraphics.textHighlight`
- IBEAM hover cursor (NOT_ALLOWED when not editable)

MenuKit's TextField builder exposes `.bordered(boolean)` (default true). Hint text via `.hint(Component)`.

### 5.8 Cross-context applicability

| Context | Applies | Reasoning |
|---|---|---|
| **MenuContext** | Yes | Text inputs on inventory-attached panels are a real use case (filter input, search, name a thing). Region-based ScreenPanelAdapter or MenuKitScreenHandler. |
| **StandaloneContext** | Yes | MenuKitScreen-based screens with text input (e.g., create-named-sandbox prompt). |
| **SlotGroupContext** | No | Slot-group anchors are for slot-related decorations. Text input on a slot-group anchor is shape-mismatched. |
| **HudContext** | No | HUDs are render-only (no input dispatch). Text input fundamentally requires input. |

Cross-context check: TextField doesn't depend on slot mechanics or HUD coordinate space — context-agnostic at the element level. The container determines applicability per Principle 5.

### 5.9 Lens pattern (Principle 8)

**`Consumer<String>` onChange + initial value via builder.** Consumer holds the value field; library calls onChange after every mutation. Consumer reads via direct field access (their own state) or via `field.getValue()` when they need the current snapshot.

No `Supplier<String>` for live reads — the lens write (onChange) is sufficient because the consumer holds the source of truth and updates it on each callback. Forcing the consumer to provide a Supplier creates a pull-based read pattern that doesn't help anything (the consumer already has the value via the most recent onChange).

This is a tighter lens than 14d-2 ScrollContainer's `Supplier<Double> + DoubleConsumer` pair. Justification: ScrollContainer's offset can change WITHOUT a write trigger (auto-scroll-to-cursor, scroll-to-handle-position) so the supplier pull is needed. TextField value only changes through user input → consumer is notified through onChange; no out-of-band mutations.

### 5.10 Composition with M9 / M8

**M9 opacity:** TextField inside an opaque panel — clicks inside the field's bounds focus the field (via EditBox.onClick), clicks outside the field but inside the opaque panel are eaten by the panel's bounds (don't blur the field). Clicks outside the panel — different story:

- Outside opaque panel + non-modal opaque cover: M9 dispatches the click to whatever opaque panel covers the cursor. If that panel doesn't have the TextField, the field stays focused but vanilla's screen-level `setFocused(null)` from the click fires anyway via the addRenderableWidget pipeline... need to verify behavior.
- Outside any opaque panel: vanilla click reaches the screen normally; screen.setFocused(null) is the typical blur behavior.

**Verification:** the M9 click dispatch goes through `MouseHandler.onButton` HEAD mixin → `dispatchOpaqueClick(...)`. Inside-opaque clicks dispatch to the opaque panel's adapter's elements via `adapter.mouseClicked(...)` THEN cancel. Vanilla's screen.setFocused doesn't fire because the screen.mouseClicked never runs (canceled at MouseHandler.onButton). So focused TextField stays focused unless the player clicks the same TextField (which routes to its own mouseClicked → which calls EditBox.onClick → cursor-position update).

This is actually the right behavior: clicks inside the modal shouldn't blur a focused field. Clicks outside the modal are eaten entirely; nothing happens to focus.

For non-modal cases (no opacity dispatch), vanilla's click pipeline runs normally and screen-level focus management applies.

**M8 layout:** TextField is a PanelElement with fixed width/height (consumer-declared via `.size(w, h)`). Composes naturally with Column/Row/Grid. Auto-sizing the wrapping Panel works (per the panel-auto-size convention) — the panel sizes to fit the field plus other elements.

---

## 6. Composition with the testing convention (14d-2.7)

New TextField smoke registers as ONE Hub entry in validator (no new chat command, no library-side scaffolding):

- Validator: `MenuKitSmokeState.textFieldVisible` flag. `MenuKitSmokeWireup.wireTextFieldSmoke()` registers a panel containing a TextField + a label showing the current value. Hub entry "TextField (text input)" toggles visibility.

Pure-logic auto-tests added to `ContractVerification.runAll`:
- M16 TextField builder validation (required fields, null guards, chainable returns)
- M17 TextField value lifecycle (initial value applied; filter rejects bad values; onChange callback fires)

Convention's structural test sentence holds: one wireup method (validator), one Hub entry (validator), two auto-check probes (library) — no new chat command, no library-side test scaffolding.

---

## 7. Implementation outline

| File | Role | LOC |
|---|---|---|
| `core/TextField.java` (new) | PanelElement subclass; builder; wraps internal MenuKitEditBox; `onAttach`/`onDetach` lifecycle | ~200 |
| `core/PanelElement.java` (modify) | Add `onAttach(Screen)` + `onDetach(Screen)` defaults | +5 |
| `screen/MenuKitScreen.java` (modify) | Iterate panel elements at init/removed → call onAttach/onDetach | +20 |
| `screen/MenuKitHandledScreen.java` (modify) | Same | +20 |
| `inject/ScreenPanelAdapter.java` (modify) | Iterate adapter's panel elements at AFTER_INIT/AFTER_REMOVE → onAttach/onDetach | +30 |
| `inject/ScreenPanelRegistry.java` (modify) | Wire adapter lifecycle hooks into Fabric ScreenEvents | +20 |
| `verification/ContractVerification.java` (modify) | M16 + M17 probes | +100 |
| `validator/.../scenarios/smoke/MenuKitSmokeState.java` (modify) | textFieldVisible flag | +1 |
| `validator/.../scenarios/smoke/MenuKitSmokeWireup.java` (modify) | wireTextFieldSmoke method | +50 |
| `validator/.../scenarios/hub/HubHandler.java` (modify) | Add Hub entry at top of list (newest-first) | +5 |
| `Design Docs/Elements/TEXT_FIELD.md` | This file | (new) |
| `Design Docs/Phases/14d-3/REPORT.md` | Phase report on close | (new) |

Total approximate: ~+450 LOC, ~12 files touched. Modest scope.

---

## 8. Open questions for advisor verdict

**Q1 (§3 PanelElement lifecycle hooks).** `onAttach(Screen)` + `onDetach(Screen)` on PanelElement. Default no-op. Called by MenuKit screens + ScreenPanelAdapter at init/removed boundaries. Verdict: shape correct? Should the parameter be `Screen` (vanilla type) or a MenuKit-specific `ScreenContext` that abstracts attach/detach without exposing vanilla Screen?

Implementer pull: **`Screen`** — TextField needs to call `screen.addRenderableWidget`, which is on vanilla Screen. Abstracting would require MenuKit to wrap that API too, for no clear win.

**Q2 (§2 wrap vs reimplement).** Wrapping vanilla EditBox vs reimplementing. Implementer strongly prefers wrap.

Implementer pull: **wrap**. Confirmation requested; alternatives are higher-risk + reinvent vanilla mechanism.

**Q3 (§5.4 submission via subclass vs mixin).** MenuKitEditBox subclass overriding keyPressed to capture Enter, vs HEAD-cancellable mixin on EditBox.keyPressed.

Implementer pull: **(β) subclass**. Cleaner ownership; no mixin needed for what's a per-element concern.

**Q4 (§5.5 modals-with-text-input).** Defer modals-with-text-input to evidence. v1 ships non-modal text inputs only. If used inside a modal, M9 keyboard mixin eats keystrokes (text input doesn't work). Acceptable v1 deferral?

Implementer pull: **defer**. The modal-keyboard refinement is a separate primitive-gap fold-inline trigger, not a v1 blocker.

**Q5 (§5.9 lens shape — onChange-only vs supplier+consumer).** Use Consumer<String> only (push-based; consumer holds value), not Supplier<String> + Consumer<String> (pull+push). Justification: TextField value mutates only via user input which fires the consumer; no out-of-band changes that would require supplier pull.

Implementer pull: **Consumer-only**. Tighter lens; matches the reality that text inputs are write-trigger-only.

**Q6 (§5.10 focus + M9 interaction verification).** Need to confirm via smoke that focus survives clicks inside a modal opaque panel (clicks dispatched to elements; vanilla screen.setFocused(null) doesn't fire because screen.mouseClicked never runs). Concern: does EditBox lose focus through other paths I haven't considered?

Implementer pull: **verify in smoke**. Probably correct based on M9's MouseHandler-level cancellation semantics, but smoke is the proof.

**Q7 (PanelElement lifecycle — visibility-driven detach).** v1 attaches at screen init (regardless of panel.isVisible()), detaches at screen close. Visibility-driven attach/detach (panel hides mid-screen-life → detach EditBox; panel re-shows → re-attach) deferred.

Per Q7 advisor verdict (round-1 fold): document the gotcha in TextField's javadoc:

> *"Visibility-driven lifecycle gotcha: if a panel containing a focused TextField is hidden mid-screen-life, keystrokes still route to the (invisible) field via vanilla's widget pipeline. Recommended pattern: blur the field (e.g., `screen.setFocused(null)`) before hiding the panel."*

---

## 9. What this design does NOT do

- **Multi-line / wrapped text input** — separate primitive (TextArea); folded-on-evidence per existing DEFERRED entry.
- **Rich text formatting beyond TextFormatter passthrough** — the consumer can register an EditBox.TextFormatter for syntax highlighting; MenuKit doesn't add a richer model on top.
- **Validation + error display** — filter only gates input; consumer renders their own error label adjacent.
- **Auto-complete / dropdown suggestions** — vanilla CommandSuggestions exists for command-style autocomplete; MenuKit doesn't ship its own. Future TextArea/AutocompleteField can be a separate primitive.
- **Modals containing text input** — deferred per Q4.
- **Visibility-driven lifecycle** — deferred per Q7.

---

## 10. Standing by

Round-1 design ready for advisor verdict on Q1–Q7. Implementer pulls per above; advisor confirms or pushes back.

Aim: 1 round + inline. Vanilla precedent investigated thoroughly; design follows the centralizes-the-behavior heuristic; primitive-gap (PanelElement lifecycle) surfaced explicitly.
