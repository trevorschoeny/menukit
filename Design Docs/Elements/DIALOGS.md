# Dialogs — design doc (ConfirmDialog + AlertDialog batched)

**Phase 14d-1 element pair — palette additions** (per `PHASES.md` §14d).

**Status: round 3 closed; smoke green. Modal mechanism architecturally complete. Implementation done; phase ready for commit + close-out.**

**Load-bearing framing (PHASES.md §14d verbatim):**

> **ConfirmDialog** — modal confirm/cancel
> **AlertDialog** — modal acknowledge

The word that does the architectural work: **modal**. Everything else (composition, API ergonomics, visibility model) follows from existing principles + M8 + Principle 8. The genuine design surface is *what does "modal" mean in MenuKit*, and the cascade of decisions that flow from the answer.

This doc batches Confirm + Alert per advisor framing. They share ~85% of architecture (modal Panel + Column body + Row button bar via M8); Alert is structurally a subset of Confirm with one button instead of two. Splitting into two docs would duplicate every architectural decision.

---

## 1. Purpose

Dialogs are the primitive for "interrupt the user with a confirmation or notification before they continue." They are not interactive widgets in the sense of Toggle or Slider — their lifecycle is open → user-acks → dismissed. The library does not ship a dialog primitive today; consumers building "Are you sure?" UX hand-roll a Panel, manage their own visibility flag, and write their own click-eating logic for everywhere outside the dialog so the underlying screen doesn't accept clicks while the dialog is up.

That hand-rolled work has a load-bearing primitive gap inside it: **modal click-eating**. CONTEXTS.md flags `ScreenPanelAdapter.cancelsUnhandledClicks(boolean)` as the planned cancellation-aware extension; ScreenPanelRegistry.java has the `// Per M8 §8.3, future cancellation-aware behavior...` placeholder pointing at the same. Dialog primitives turn that planned flag from speculative to load-bearing.

**M8 is the second integration point.** Dialogs are M8's first real consumer — Column for the title-body-buttons stack, Row for the button bar. If M8 doesn't compose into dialogs cleanly, the M8 design has gaps. This is exactly the kind of "validate the product, not just the primitives" check Principle 7 names.

---

## 2. Consumer evidence

### ConfirmDialog (modal confirm/cancel)

Three concrete consumer cases:

- **Sandboxes "delete sandbox?"** — destroy-sandbox is irreversible; confirm before action. Currently absent — sandboxes ships without the safety prompt; users have lost work.
- **InventoryPlus "clear inventory?" / "drop all?"** — destructive bulk actions; confirm protects against accidental clicks. Phase 15a likely surfaces this.
- **Shulker-palette "reset palette?"** — wipes palette state; confirm before action. Same shape as the previous two.

Pattern across the three: a destructive action that needs a "yes I meant it" gate.

### AlertDialog (modal acknowledge)

Three concrete consumer cases:

- **Operation-failed surfaces** — agreeable-allays "no allays available" when the player tries to issue a follow command with no allays in range; sandboxes "world failed to load" with a return-to-menu acknowledge.
- **Single-button confirmations** of completed irreversible actions ("sandbox deleted") where a non-modal toast would feel too transient for the user to confirm they noticed.
- **First-launch / version-change notices** — IP "config schema changed; review and re-save" on first launch after an update, requiring acknowledgment before the player proceeds.

Pattern: an information surface the user must explicitly acknowledge before continuing.

**Rule of Three check:**
- ConfirmDialog: 3 concrete cases ✓
- AlertDialog: 3 concrete cases ✓

Both ship.

---

## 3. Scope

- **Two element constructs:** `ConfirmDialog` and `AlertDialog`. Each is a builder that produces a configured Panel ready to drop into the consumer's UI.
- **Full modal primitive** via `Panel.cancelsUnhandledClicks(boolean)` flag (§4.1). Round 1's "modality emerges from existing primitives + flag" hypothesis evolved across three rounds as smoke surfaced structural gaps. Final architecture (round 3): per-Panel flag (consumer-facing surface, unchanged from round 1) + library-wide pre-emption at the input-handler dispatch root (MouseHandler / KeyboardHandler mixins fire BEFORE per-screen routing, catching subclass override pre-empts that round-2's per-Screen mixins couldn't reach) + tooltip suppression at the queueing site + cursor suppression via vanilla's `Window.allowCursorChanges` flag + hover suppression at `AbstractContainerScreen.getHoveredSlot` + render-pass-order dim overlay covering vanilla AND non-modal MK panels + RenderContext `-1` sentinel reuse for inert non-modal panels. See §4.1 for the full mechanism.
- **Composition over existing primitives** — dialogs are Panels composed of `TextLabel` + `Row(Button.spec(...) × N)` via M8. No new render pipeline; no new container concept.
- **Cross-context: MenuContext + StandaloneContext only** (§4.5). HudContext (no input dispatch) and SlotGroupContext (anchor mismatch) don't fit.
- **One additive region: `MenuRegion.CENTER` + `StandaloneRegion.CENTER`** (§4.6). Centering a modal dialog over the screen is the canonical positioning, and the existing 8-corner regions don't express it. `MenuRegion.CENTER` ships with a working resolver in 14d-1; `StandaloneRegion.CENTER` ships as a reserved enum value (the StandaloneRegion resolver is generally deferred per its existing javadoc — CENTER is the first reserved value pending the broader resolver work).
- **MenuKit-native screen dispatch deferred** (§4.5 finding). The advisor's "decorator pattern uniformly across both kinds" pull surfaced an implementation gap during prep: `ScreenPanelRegistry.java:212` hardcodes `AbstractContainerScreen` and `ScreenPanelAdapter.render(...)` requires it as a parameter type. Extending the dispatch path to support `MenuKitScreen` is real new library surface, not a fold-inline. **14d-1 ships dialog support for MenuContext path only**; MenuKit-native screen dispatch is filed as a follow-on advisor question (see §10).

**Deferred:**
- **Custom-shape dialogs** (single-text-input prompt, three-button "Save / Don't Save / Cancel," icon-decorated dialogs). No concrete consumer today; v1 ships the canonical shapes.
- **Toast / non-modal notifications.** Already served by the HUD `Notification` element for transient surfaces. AlertDialog is the modal variant.
- **Rich body content** (multi-line wrapping, embedded buttons inside body, links). Body is a single `Component` in v1 (§4.9); multi-line consumers compose `Column` of `TextLabel`s manually. Surface as primitive gap if Phase 15 consumers hit it.
- ~~**Dim-behind overlay**~~ **shipping in v1** per round-2 smoke verdict (§4.10) — Trevor's smoke found dialogs without dim feel "not visually distinct enough."

---

## 4. Architectural decisions

Each decision presents the resolution shapes considered, the implementer pull, and the round 1 advisor verdict (now resolved — see §10). §4.1 (modal) and §4.2 (lifecycle) are load-bearing.

### 4.1 What "modal" means — three resolution shapes

**The question.** "Modal" in dialog UX means: while visible, the dialog blocks user input to the underlying UI; clicks outside the dialog's bounds do not reach the screen below it. Implementing this in MenuKit needs a way for the dialog Panel to eat clicks that don't hit its own elements.

**Three resolution shapes:**

**(i) Modality emerges from existing primitives + library-wide pre-emption at the input dispatch root.** This was round 1's hypothesis ("flag + library-wide dispatch") — but the dispatch layer evolved across three rounds as each round's smoke surfaced a deeper structural gap. The final architecture lives at the input-handler dispatch root, well above any per-Screen entry point.

**Shipped mechanism (final, post-round-3):**

- **Consumer-facing surface unchanged across all rounds.** `Panel.cancelsUnhandledClicks(boolean)` flag — modality is a per-Panel property; the dialog builder sets it true by default. Consumer code is identical to round 1's design. What grew across rounds was the library plumbing that delivers on the contract.

- **Click + scroll suppression: input-handler-level pre-emption.** `MenuKitModalMouseHandlerMixin` HEAD-cancellable on `MouseHandler.onButton` and `MouseHandler.onScroll`. Fires at the input dispatch root, BEFORE any per-Screen routing. Subclass override pre-empts (the silent-inert dispatch failure mode CONTEXTS.md documents — e.g., `CreativeModeInventoryScreen.mouseClicked` processing tab clicks before super) become irrelevant: the mixin runs first, eats the click before vanilla's screen-specific code ever sees it.
  - Click inside any visible modal's bounds: dispatches via `ScreenPanelRegistry.dispatchModalClick(...)` to the modal's adapter (so its buttons get the click), then cancels — vanilla's screen.mouseClicked never sees it. **Atomic dispatch + eat in one operation**, fixes round-2's "Confirm picks up item behind it" bug.
  - Click outside all visible modals: cancels — modal blocks underlying interaction.
  - No modal: passes through, vanilla dispatches normally and existing non-modal adapters fire via Fabric's `allowMouseClick` hook.
  - Scroll wheel events eaten symmetrically.

- **Keyboard suppression: input-handler-level pre-emption.** `MenuKitModalKeyboardHandlerMixin` HEAD-cancellable on `KeyboardHandler.keyPress`. Eats key events while modal is up — except Escape (GLFW key 256), which passes through per round-2 verdict ("Escape closing the underlying screen is acceptable v1 behavior"). Consumer's button callbacks are the explicit dismissal path; Escape is a user-initiated screen close.

- **Cursor suppression: vanilla's own toggle.** `Window.setAllowCursorChanges(boolean)` is vanilla's existing global flag — when false, `Window.selectCursor(...)` coerces to `CursorType.DEFAULT` regardless of caller (verified in bytecode: `selectCursor` early-returns DEFAULT when the flag is false). Per-tick callback in `MenuKitClient.onInitializeClient` syncs the flag to `!hasAnyVisibleModal()`. **Single vanilla flag, no widget-by-widget patches.** Replaced round-3-attempt's `MenuKitModalCreativeTabHoverMixin` per Trevor's "is there an architectural way?" pushback — vanilla's existing toggle was the right lever all along.

- **Hover suppression for vanilla widgets: `getHoveredSlot` mixin.** `MenuKitModalHoverMixin` HEAD-cancellable on `AbstractContainerScreen.getHoveredSlot` returning null when modal up. No slot is "hovered" → no slot highlights, no slot-driven tooltip queueing. Single mixin covers all `AbstractContainerScreen` subclasses uniformly via standard Java dispatch (private method on the parent class with no subclass overrides).

- **Hover suppression for MenuKit panel elements: `RenderContext` inert sentinel.** When a non-modal MK panel renders alongside a visible modal, `ScreenPanelAdapter.render` constructs its `RenderContext` with `mouseX = -1`, `mouseY = -1` — the same "no input dispatch" sentinel HUDs already use. `RenderContext.hasMouseInput()` returns false; `RenderContext.isHovered(...)` short-circuits to false. **All `PanelElement` kinds inherit the inert behavior automatically through the existing context API** — no per-element mixins. Modal panels themselves keep real coords so their own buttons detect hover and dispatch normally.

- **Tooltip suppression: HEAD-cancellable mixin.** `MenuKitTooltipSuppressMixin` on `GuiGraphics.setTooltipForNextFrameInternal` (the private method all public `setTooltipForNextFrame` overloads delegate to). Cancels the call when modal is up — tooltip never queues, never renders. Round-2's queue-clearing approach was insufficient because `CreativeModeInventoryScreen.render` queues tab-hover tooltips AFTER `super.render()` returns (post our render-path clear); suppression at the queueing site is robust.

- **Dim overlay: two-pass render order at the dispatcher.** `ScreenPanelRegistry.renderMatchingPanels` runs three passes: (1) non-modal MK adapters render first, (2) dim overlay (`0xC0000000` ~75% black, full-screen-window) renders if any modal visible — covers vanilla content AND step-(1) panels, (3) modal MK adapters render last on top of dim. Single render-order pattern enforced architecturally; no per-adapter dim that depends on iteration order.

**Library-not-platform audit (Principle 1).** The mixins into `MouseHandler` / `KeyboardHandler` / `AbstractContainerScreen` / `GuiGraphics` are library-wide dispatch surface; the flag is per-Panel. Structural test: *if MenuKit took ownership of these code paths, could a second mod doing something similar still coexist?* Yes — two mods both shipping modal panels each have their own `Panel.cancelsUnhandledClicks(true)`; each library-wide mixin checks "any visible modal panel" and consults the flag. Each consumer's modal works independently. Cross-mod overlap is consumer ergonomics, not library mediation. The mixins are observational/dispatch-policy at single hook points, not ownership of behavior. Cursor flag is vanilla's own — we observe modal state and toggle a flag vanilla already exposes. Round-3 advisor verified.

**(ii) New library primitive — `Panel.modal(true)`.** A Panel-level flag that means "when visible, the rendering pipeline should suppress click dispatch to all other panels and to vanilla." More expressive than (i) but pushes complexity into the rendering pipeline — the dispatcher now has to know about cross-panel priority, which Panel is currently top-modal, etc. Real expansion of library surface; Principle 11 evidence required (is dialog the only consumer? probably no — error toasts, popovers, in-screen confirmations all want modal-like behavior, but most could ride the same `cancelsUnhandledClicks` mechanism from (i)).

**(iii) Punt on modality — ship as overlay.** Dialog renders on top, doesn't eat clicks. Consumer manages click gating themselves (mixin-cancels clicks while dialog visible, etc.). Smallest library; biggest consumer burden; defeats the dialog-is-a-primitive thesis.

**My pull: (i).** Three reasons:
1. The mechanism is already designed and flagged in the code (`cancelsUnhandledClicks(boolean)` in CONTEXTS.md + ScreenPanelRegistry.java line 244 comment). 14d-1 is the right phase to land it because dialogs are the first real consumer.
2. Library-not-platform clean. Each mod's modal panels are their own. Cross-mod scenarios — two mods with concurrent modal dialogs in the same screen — are independent: each adapter eats its own clicks; they don't compete. (Edge case: if two dialogs overlap visually, the consumer who positioned them is responsible. The library doesn't mediate cross-mod dialog stacking; per Principle 1, that's not its job.)
3. The structural test ("if MenuKit took ownership of this code path, could a second mod doing something similar still coexist?") passes — the flag is per-adapter; nothing global; nothing platform-y.

**Cost of (i):** ship `Panel.cancelsUnhandledClicks(boolean)` flag (~10 lines on Panel + ~5 lines plumbing through ScreenPanelRegistry's `allowMouseClick` hook). For MenuKit-native screen dispatch, see §4.5 finding — that path's not covered in 14d-1.

**Round 1 verdict: (i) approved.** Per-Panel flag, library-not-platform clean; (ii) rejected as platform behavior. **Round 2 verdict (post-smoke):** (i) ships with expanded dispatch mechanism — multi-target mixin + tooltip queue-clear + dim-behind. Consumer surface unchanged; library plumbing fuller. See §10 round 2 record.

### 4.2 Lifecycle — where the dialog lives

**The question.** When a dialog is shown, does it live as its own Screen pushed on top, a Panel layered on the current Screen, or a render-callback the Screen invokes?

**Three resolution shapes:**

**(a) Dialog is its own Screen pushed on top.** `Minecraft.getInstance().setScreen(myDialogScreen)`. Vanilla pattern (PauseScreen overlays). Clean for StandaloneContext (`MenuKitScreen` pushed over current screen). Breaks for MenuContext: pushing a Screen onto an `AbstractContainerScreen` would close the underlying menu (server thinks screen closed → slot sync stops → quick-move routing breaks → reopening rebuilds the handler from scratch). The vanilla-substitutability discipline (Principle 2) takes a hit too — dialogs would be "a thing on top of menus" but menus aren't "screens with a thing on top," and other mods' mixins on `AbstractContainerScreen` don't see the dialog as part of the screen they're decorating.

**(b) Dialog is a Panel layered on the current Screen.** Dialog is an ordinary Panel that the consumer adds to their Panel list (alongside their main panels), with visibility driven by consumer state. When the dialog should appear, consumer flips visibility to true; when dismissed, false. The underlying Screen is unchanged; vanilla menu state is preserved; sync protocol untouched. Principle 4 (declared structure, mutable visibility) holds — dialog Panel is declared at construction; visibility is the one mutable dimension.

**(c) Dialog is a render-callback.** Screen invokes a callback that paints the dialog. Too consumer-facing; bypasses Panel + PanelElement composition; doesn't compose with M8.

**My pull: (b).** It's the only shape that holds Principle 4 across MenuContext + StandaloneContext uniformly. Sub-question of "who manages the dialog's existence" handled in §4.3.

### 4.3 Library-managed vs consumer-managed — within shape (b)

**Within (b) — a Panel-layered-on-current-Screen — who manages the dialog's existence?**

**Library-managed:** `MenuKit.showDialog(dialog)` queues + dispatches dialogs. Library owns a global dialog stack across all MenuKit-aware screens. When the consumer wants a dialog, they call `MenuKit.showDialog(...)`; the library handles visibility, click-eating, and dismissal. North Star "it just works" for the common case.

**Consumer-managed:** consumer adds the dialog Panel to their Panel list directly, with visibility driven by their own state. Library provides the dialog's composition (the configured Panel returned from the builder) but no orchestration. Principle 1 clean.

**Trade-off.** Library-managed is more ergonomic for the simple case, but raises a Principle 1 alarm: when two consumer mods both call `MenuKit.showDialog(...)` simultaneously, the library has to mediate. Stack? Queue? First-wins? Each policy forces one set of answers on consumers, exactly the library-not-platform failure mode. Consumer-managed is more boilerplate (consumer maintains their own `dialogOpen` state, wires `showWhen` + button callbacks to it) but each consumer's dialog is theirs alone; coexistence is automatic.

**My pull: consumer-managed.** Library ships:
- `ConfirmDialog.builder().title(...).body(...).onConfirm(...).onCancel(...).build()` — returns a configured Panel
- `AlertDialog.builder().title(...).body(...).onAcknowledge(...).build()` — returns a configured Panel

Consumer wires it into their UI like any other Panel:

```java
private boolean confirmDeleteOpen = false;

Panel deleteDialog = ConfirmDialog.builder()
    .title(Component.literal("Delete sandbox?"))
    .body(Component.literal("This cannot be undone."))
    .onConfirm(() -> { confirmDeleteOpen = false; deleteSandbox(); })
    .onCancel(() -> confirmDeleteOpen = false)
    .build()
    .showWhen(() -> confirmDeleteOpen);

panels.add(deleteDialog);

// Triggered by another button in the UI
deleteButton.onClick(() -> confirmDeleteOpen = true);
```

This matches the `Toggle.linked` consumer-state-source-of-truth pattern from Phase 9; visibility lives in consumer code; library provides the visual element. Principle 8 ("elements are lenses, not stores") extends naturally.

**Convenience question.** A common pattern is "open dialog → click a button → dismiss + run action." The above example shows the consumer manually flipping `confirmDeleteOpen = false` in both callbacks. Is that boilerplate worth library help?

Possible shape: `.dismissOn(BooleanConsumer setOpen)` builder method that wires a passed-in setter into both Confirm-and-Cancel callbacks before they fire the consumer's logic. Or just document the pattern and let consumers wrap their own helper. **My pull:** document the pattern; defer the helper. Consumers writing this once per dialog is fine; the boilerplate isn't load-bearing.

**Round 1 verdict: consumer-managed approved.** Principle 1 clean; cross-mod scenarios handled by structural independence. See §10.

### 4.4 Composition pattern — Panel + Column + Row

A ConfirmDialog is structurally:

```
Panel (RAISED, content area)
  Column (M8, vertical stack)
    TextLabel (title)
    TextLabel (body)
    Row (M8, button bar)
      Button.spec(.., "Cancel", onCancel)
      Button.spec(.., "Confirm", onConfirm)
```

AlertDialog is the same with one button instead of two:

```
Panel (RAISED, content area)
  Column (M8)
    TextLabel (title)
    TextLabel (body)
    Row (M8, button bar)
      Button.spec(.., "OK", onAcknowledge)
```

The dialog builder constructs this internal structure and returns the composed Panel. The user sees:

```java
Panel dialog = ConfirmDialog.builder().title(...).body(...).onConfirm(...).build();
// dialog is just a Panel — add it to the panel list, configure visibility, done
```

Internally, `build()` does:

```java
List<PanelElement> elements = Column.at(PADDING, PADDING).spacing(SECTION_GAP)
    .add(TextLabel.spec(title))
    .add(TextLabel.spec(body))
    .addRow(r -> r.spacing(BUTTON_GAP).crossAlign(CrossAlign.CENTER)
        .add(Button.spec(BUTTON_W, BUTTON_H, cancelLabel, cancelHandler))
        .add(Button.spec(BUTTON_W, BUTTON_H, confirmLabel, confirmHandler)))
    .build();
return new Panel(id, elements, /*initialVisible*/ false, PanelStyle.RAISED, ...);
```

M8 does the layout math; PanelStyle handles the chrome; existing primitives compose. The dialog builder is just an opinionated factory over the shape.

**Layout decisions baked into the builder:**

- **Padding:** `PADDING = 8` between the panel chrome and content (consistent with vanilla container padding).
- **Section gap:** `SECTION_GAP = 6` between title, body, and button row.
- **Button gap:** `BUTTON_GAP = 4` between buttons in the bar (matches M8 default-spacing convention).
- **Default button width:** `BUTTON_W = 60` and `BUTTON_H = 20` (matches vanilla's confirm-screen button proportions).
- **Button bar alignment:** centered. ConfirmDialog uses Cancel-on-left, Confirm-on-right (matches vanilla `ConfirmScreen`'s convention).
- **Auto-sized panel.** The Panel sizes from its content via existing auto-size semantics — consumer doesn't declare width/height; the dialog grows to fit the longest of (title, body, button row).

**Customization escape hatch.** Consumers who want a non-standard layout (a third "neither" button, an icon next to the body, a custom button width) compose the Panel manually using the same M8 patterns the builder uses internally. The builder is for the canonical shape; the primitives below are for custom shapes. This matches the Phase 9 specialization-not-builder-method discipline.

### 4.5 Cross-context applicability — MenuContext + StandaloneContext only

Per Principle 5, the test is: *could this element render correctly in any context if the container did its part?*

| Context | Applies? | Reason |
|---|---|---|
| **MenuContext** | Yes | Vanilla container screens host dialogs naturally — "Confirm clear inventory?" over the inventory. Anchored to screen frame via `ScreenPanelAdapter`. |
| **StandaloneContext** | Yes | Canonical case. Dialogs over MenuKit-native screens (sandboxes selector, etc.) and vanilla standalone screens (pause menu modal). |
| **SlotGroupContext** | **No** | Anchor mismatch. SlotGroupContext panels anchor to slot-group bounds; modal dialogs need to overlay the whole screen, not the bounds of one slot category. Putting a dialog at the slot-group anchor is semantically wrong. |
| **HudContext** | **No** | HUDs are render-only (no input dispatch per CONTEXTS.md). Modal dialogs need clicks. Use `Notification` for transient HUD-context surfaces; use AlertDialog when input is required. |

Document explicitly: dialogs target MenuContext and StandaloneContext. The other two are out of scope, with named reasons. This isn't a Principle 5 violation — it's a recognition that some primitives are meaningful only in input-dispatch contexts. Button is the same shape (renders on HUDs but inert per the HUD render-only doctrine).

**Dispatch path on MenuKit-native screens — implementation finding.** Round 1's "decorator pattern uniformly across both kinds" pull for StandaloneContext (covering both vanilla standalone screens AND MenuKit-native `MenuKitScreen` subclasses through the same `ScreenPanelAdapter`) surfaced an implementation gap during prep:

- `ScreenPanelRegistry.java:212` early-returns for non-`AbstractContainerScreen` instances:
  ```java
  if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
  ```
- `ScreenPanelAdapter.render(...)` and `mouseClicked(...)` both require `AbstractContainerScreen<?> screen` as a parameter.
- `ScreenPanelAdapter.matches(Class<? extends AbstractContainerScreen<?>>)` is hardcoded to the type.

The adapter mechanism originated for vanilla container-screen decoration (Phase 10). Extending it to dispatch for `MenuKitScreen` subclasses is **real new library surface**, not a fold-inline:

- `ScreenPanelAdapter` API changes (relax `AbstractContainerScreen` parameter type to `Screen`, or add a parallel adapter for non-AbstractContainerScreen)
- `ScreenPanelRegistry` extends its dispatch to handle `MenuKitScreen` instances (likely needs a render-side hook analogous to `MenuKitPanelRenderMixin` for vanilla container screens)
- Layout question: dialogs over `MenuKitScreen` should center on screen window (consistent with modal dialog visual convention), but `MenuKitScreen` uses constraint-based `PanelPosition` layout for its native panels. Modal dialogs need to bypass that layout flow.

**Resolution for 14d-1:** ship dialog support for **MenuContext path only**. The MenuContext path covers vanilla container-screen modal dialogs (consumer mod's "Confirm clear inventory?" flow over a chest, inventory, etc.) — the most evidence-rich case. MenuKit-native screen dispatch is filed as a follow-on for advisor verdict (see §10 implementation findings).

**Smoke implications:** 14d-1 smoke uses MenuContext. A confirm dialog appearing over the player inventory or a chest screen validates the dialog primitive end-to-end. Sandboxes "delete sandbox?" (originally targeted as the canonical smoke case) requires MenuKit-native screen dispatch and shifts to follow-on phase.

**Per Principle 1 framing:** the existing two-path situation (adapter for vanilla; native for `MenuKitScreen`) is fine — different dispatch paths are not a violation. The question is whether `ScreenPanelAdapter` should grow to be the single uniform dispatch primitive, or whether `MenuKitScreen` should grow native dialog/modal-panel awareness. Both are real architectural choices.

### 4.6 Positioning — CENTER as additive primitive

**The question.** Modal dialogs are typically centered on the screen (vanilla convention; user perception convention; the dialog is the focus). MenuKit's existing `MenuRegion` (8 values, all corner/edge anchored) and `StandaloneRegion` (same shape, solver deferred) don't express "center."

**Three resolution shapes:**

**(α) Lambda-anchor escape hatch.** Consumers position dialogs using `ScreenPanelAdapter`'s lambda form: `bounds -> new ScreenOrigin(bounds.leftPos() + (bounds.imageWidth() - dialogW) / 2, bounds.topPos() + (bounds.imageHeight() - dialogH) / 2)`. Works today; per-consumer boilerplate per dialog; requires the consumer to know the dialog's W/H, which the auto-sized Panel doesn't expose at construction time (Panel auto-sizes from its visible elements at render time).

**(β) Add `MenuRegion.CENTER` + `StandaloneRegion.CENTER` as additive primitives.** Low-cost evidence-driven additive primitive within Rule-of-Three relaxation. Per-entry cost is one enum constant + one resolver case = trivial. Migration cost if shipped later is real: dialogs would hand-roll lambda-anchor centering until CENTER lands, and each consumer reinvents the same arithmetic. Dialogs are concrete consumer evidence ONE; future popovers, splash UIs, and toast variants are speculative — but the marginal cost of shipping CENTER now is so low that one concrete consumer is enough to clear the bar. (This is not the full Principle 11 exhaustive-coverage exception — that's reserved for shipping a complete catalog at v1, which this isn't. It's the single-additive-primitive relaxation: low-cost additive primitives can ship on first concrete consumer when the alternative is per-consumer reinvention.)

**(γ) Dialog-specific positioning.** Dialog builder takes positioning as a parameter (e.g., `.centered()` on the builder returns a Panel-with-lambda-adapter pre-wired). Hides the primitive gap inside the builder; doesn't help non-dialog consumers who want centering. Workaround inside one element rather than a primitive fix.

**Resolution: (β).** Ship CENTER additively in 14d-1's commit set. Centering is the right additive primitive; the Region enums are catalogs of named anchor positions, and CENTER is the missing canonical anchor. Dialogs trigger the addition; future consumers (popovers, splash UIs, future toast variants) inherit it.

**Cost.** ~10 lines in `MenuRegion.java` (+ value), `StandaloneRegion.java` (+ value), `RegionMath.java` (+ resolution case for CENTER, computing `(frame.imageW - panel.w) / 2` per-frame).

### 4.7 API shape — builder returning Panel

**Three options reconsidered:**

- **(a) Builder:** `ConfirmDialog.builder().title(...).body(...).onConfirm(...).onCancel(...).build()` returns Panel. Most ergonomic; chains naturally; supports optional configuration (custom button labels, custom button widths).
- **(b) Static factory:** `ConfirmDialog.of(title, body, onConfirm, onCancel)` returns Panel. Thinner; less extensible (no clean way to override defaults).
- **(c) Direct compose:** consumer writes the Column-of-(TextLabel, TextLabel, Row) manually. Defeats the purpose of shipping ConfirmDialog as a primitive.

**My pull: (a) builder.** Standard ergonomic shape. Builder is fluent; returns a Panel ready to add to the consumer's UI.

**Builder methods (proposed):**

```java
ConfirmDialog.builder()
    .title(Component title)              // required; title text shown bold-or-prominent
    .body(Component body)                // required; body text
    .onConfirm(Runnable handler)         // required; fired on Confirm click
    .onCancel(Runnable handler)          // required; fired on Cancel click
    .confirmLabel(Component label)       // optional; default "Confirm"
    .cancelLabel(Component label)        // optional; default "Cancel"
    .id(String id)                       // optional; defaults to library-generated
    .build()                             // returns Panel
```

```java
AlertDialog.builder()
    .title(Component title)              // required
    .body(Component body)                // required
    .onAcknowledge(Runnable handler)     // required; fired on OK click
    .acknowledgeLabel(Component label)   // optional; default "OK"
    .id(String id)                       // optional
    .build()                             // returns Panel
```

**Callbacks are `Runnable`, not `Consumer<Boolean>` or `Consumer<DismissHandle>`.** Matches Toggle.linked precedent — consumer-owned state means the callback fires "user did the thing"; the consumer mutates whatever they want. No library-injected dismiss handle. (See §4.3 — convenience helper deferred.)

**Returns Panel, not a typed `Dialog` wrapper.** The dialog *is* a Panel; nothing distinguishes it from any other Panel at the framework level. A typed wrapper would add API surface for no compositional benefit (you can't `.dialog(...)` it onto something different than you can `.add(panel)` it).

### 4.8 Visibility per Principle 8

Per Principle 8 ("elements are lenses, not stores"), dialog visibility lives in consumer state. The consumer flips `dialogOpen = true/false`; the Panel's `showWhen` supplier reads that boolean.

```java
boolean confirmDeleteOpen = false;

Panel dialog = ConfirmDialog.builder()
    .title(...).body(...)
    .onConfirm(() -> { confirmDeleteOpen = false; deleteSandbox(); })
    .onCancel(() -> confirmDeleteOpen = false)
    .build()
    .showWhen(() -> confirmDeleteOpen);
```

The `showWhen(...)` chain comes from existing Panel API (Phase 9 showWhen/`linked` pattern). No new visibility primitive; reuses what already ships.

**State self-healing.** If `onConfirm` throws (consumer's confirm logic fails), the dialog stays visible (next frame's supplier still returns `confirmDeleteOpen == true`). The consumer either sets `confirmDeleteOpen = false` in a finally block, or surfaces the failure via another dialog/notification. Either way, library state and consumer state never diverge — supplier is the single source of truth.

### 4.9 Body text — single Component, not multi-line

**The question.** A canonical confirm dialog body is multi-line ("This action will permanently delete the sandbox. This cannot be undone."). MenuKit's `TextLabel` is single-line; long body text would either truncate or render past panel bounds.

**Three resolution shapes:**

**(I) Body is a single `Component`; multi-line consumers compose Column-of-TextLabels manually.** Consumer who wants a multi-line body steps outside the dialog builder and composes the Panel using the same M8 pattern the builder uses internally. The builder ships the canonical shape; richer shapes are consumer composition.

**(II) Body is a `List<Component>` or `Component...` varargs.** Builder accepts multiple lines; internal composition stacks them in the Column. Cheap to implement; mild API growth.

**(III) Ship a multi-line `TextLabel` variant** (`MultiLineTextLabel` or `TextLabel.wrapped(text, maxWidth)`). Real new primitive. Substantial design surface (wrapping algorithm, baseline alignment, auto-sizing semantics). Probably worth its own design doc later.

**My pull: (I) for v1.** Defer (III) — it's a primitive gap worth surfacing but not blocking 14d-1 on. (II) is tempting but creates an API drift: the body parameter accepts multiple values for one element type, where every other content parameter in MenuKit takes a single Component. Consumers wanting multi-line do the Column-of-TextLabels composition manually until (III) lands.

**Document the workaround.** Builder javadoc: *"Body renders as a single line. For multi-line dialog bodies, compose the Panel manually using `Column.at(...).add(TextLabel.spec(line1)).add(TextLabel.spec(line2))...build()` plus the same M8 layout the builder uses internally. A multi-line TextLabel variant is filed as a deferred primitive (see DEFERRED.md)."*

**Surface the gap.** The deferred primitive (multi-line TextLabel) gets a DEFERRED.md entry naming the use case (dialog bodies, V7-style hint multi-line text, future text-display-with-wrapping consumers) and the trigger conditions for shipping (3+ concrete consumer cases needing wrapped text per Principle 11 Rule of Three).

**Round 1 verdict: (I) approved.** Single-line body in v1; multi-line `TextLabel` filed as deferred primitive; escape-hatch pattern made prominent in §5. See §10.

### 4.10 Default styling — no dim-behind in v1

**The question.** Modal dialogs in vanilla and most UI libraries dim the underlying content while visible — a translucent black overlay that draws focus to the dialog. Should MenuKit dialogs ship with a dim-behind layer?

**Evidence.** No concrete consumer evidence today asks for dim-behind. The modal click-eating from §4.1 already captures the "dialog blocks input" affordance. Visual dimming is polish, not function.

**Three shapes:**

**(I) No dim in v1.** Dialogs render via standard PanelStyle.RAISED; underlying content stays at full brightness; modality is communicated via click-eating (clicks outside dialog do nothing visibly).

**(II) Optional dim via builder method.** `.dimsBehind(boolean)` toggles a translucent black overlay rendered between underlying panels and the dialog. Default off; opt-in.

**(III) Always-on dim.** Shipped by default; consumer opts out.

**Round 2 resolution: ship dim-behind in v1.** Trevor's smoke verdict: dialogs without dim feel "not visually distinct enough." The deferred-pending-smoke discipline did its job — smoke said yes. Implemented in `ScreenPanelAdapter.render` as a translucent black quad (`0xC0000000` ~75% black) covering the full screen window, rendered after the panel's existing background but before its elements. The dim is gated on `panel.cancelsUnhandledClicks()` — non-modal panels render unchanged.

This matches the Phase 14b drop-on-break shape — ship core architecture first, validate end-to-end, fold in the missing thing post-smoke. Post-smoke folds are inline verdicts, not new rounds — calibration discipline holds.

---

## 5. Consumer API — before / after

### Before (no dialog primitive)

```java
private boolean confirmDeleteOpen = false;

// Consumer hand-rolls the panel
List<PanelElement> dialogElements = new ArrayList<>();
dialogElements.add(new TextLabel(8, 8, Component.literal("Delete sandbox?")));
dialogElements.add(new TextLabel(8, 26, Component.literal("This cannot be undone.")));
dialogElements.add(new Button(8, 50, 60, 20, Component.literal("Cancel"),
        b -> confirmDeleteOpen = false));
dialogElements.add(new Button(76, 50, 60, 20, Component.literal("Confirm"),
        b -> { confirmDeleteOpen = false; deleteSandbox(); }));

Panel dialogPanel = new Panel("delete-confirm", dialogElements, false, PanelStyle.RAISED, ...);
dialogPanel.showWhen(() -> confirmDeleteOpen);

// Consumer wires their own click-eat mixin to suppress underlying clicks while visible:
@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
private void cancelClicksWhileDialogOpen(...) {
    if (confirmDeleteOpen) cir.setReturnValue(true);
}

// Consumer manages their own positioning:
ScreenPanelAdapter dialogAdapter = new ScreenPanelAdapter(dialogPanel,
    bounds -> new ScreenOrigin(
        bounds.leftPos() + (bounds.imageWidth() - 152) / 2,
        bounds.topPos() + (bounds.imageHeight() - 78) / 2));
```

Consumer hand-rolls: composition (Column + Row arithmetic), click-eating mixin, positioning lambda. ~30 lines per dialog, all reinventing the same pattern.

### After (M8 + ConfirmDialog + CENTER region + cancelsUnhandledClicks flag)

```java
private boolean confirmDeleteOpen = false;

Panel dialog = ConfirmDialog.builder()
    .title(Component.literal("Delete sandbox?"))
    .body(Component.literal("This cannot be undone."))
    .onConfirm(() -> { confirmDeleteOpen = false; deleteSandbox(); })
    .onCancel(() -> confirmDeleteOpen = false)
    .build()
    .showWhen(() -> confirmDeleteOpen);

new ScreenPanelAdapter(dialog, MenuRegion.CENTER)
    .cancelsUnhandledClicks(true)
    .on(MyMenuScreen.class);
```

Composition + click-eating + positioning all primitive-supplied. Consumer's code is the consumer's logic only.

### Multi-line body — escape hatch (canonical workaround)

For dialog bodies that need more than one line, consumers compose the Panel manually using the same M8 patterns the builder uses internally. This is the **documented path** until a multi-line `TextLabel` variant ships (filed as deferred primitive — see §4.9).

```java
private boolean confirmDeleteOpen = false;

// Manual composition for multi-line body
List<PanelElement> elements = Column.at(8, 8).spacing(6)
    .add(TextLabel.spec(Component.literal("Delete sandbox?")))
    .addColumn(c -> c.spacing(2)  // tight stacking for body lines
        .add(TextLabel.spec(Component.literal("This will permanently delete the sandbox")))
        .add(TextLabel.spec(Component.literal("and all of its contents.")))
        .add(TextLabel.spec(Component.literal("This cannot be undone."))))
    .addRow(r -> r.spacing(4)
        .add(Button.spec(60, 20, Component.literal("Cancel"),
                b -> confirmDeleteOpen = false))
        .add(Button.spec(60, 20, Component.literal("Confirm"),
                b -> { confirmDeleteOpen = false; deleteSandbox(); })))
    .build();

Panel dialog = new Panel("delete-confirm", elements, false, PanelStyle.RAISED, ...)
    .showWhen(() -> confirmDeleteOpen);

new ScreenPanelAdapter(dialog, MenuRegion.CENTER)
    .cancelsUnhandledClicks(true)
    .on(MyMenuScreen.class);
```

Escape-hatch composition is ~25 lines vs the builder's 8 — the builder still wins on the common (single-line) case. When multi-line `TextLabel` ships, consumers using this workaround migrate to a single-line `body(...)` call with the wrapped variant.

The 14d-1 sandboxes "delete sandbox?" smoke may itself exercise this workaround if the body needs more than one line — useful M8-integration evidence either way (proves the escape hatch is ergonomic-enough, or surfaces friction that promotes multi-line `TextLabel` from deferred to next-up).

---

## 6. Library surface

### New files

- `core/dialog/ConfirmDialog.java` — public final class with nested `Builder`. `builder()` entry point; chainable `.title(...)`, `.body(...)`, `.onConfirm(...)`, `.onCancel(...)`, `.confirmLabel(...)`, `.cancelLabel(...)`, `.id(...)`, `.build() → Panel`.
- `core/dialog/AlertDialog.java` — parallel to ConfirmDialog with one button.

### New files

- `core/dialog/ConfirmDialog.java` — public final builder; `builder()` entry; chainable `.title/.body/.onConfirm/.onCancel/.confirmLabel/.cancelLabel/.id` then `.build() → Panel`.
- `core/dialog/AlertDialog.java` — parallel to ConfirmDialog with one button.
- `mixin/MenuKitModalMouseHandlerMixin.java` — HEAD-cancellable on `MouseHandler.onButton` + `MouseHandler.onScroll`. Click + scroll suppression at the input dispatch root, before per-Screen routing.
- `mixin/MenuKitModalKeyboardHandlerMixin.java` — HEAD-cancellable on `KeyboardHandler.keyPress`. Key suppression with Escape allowlist.
- `mixin/MenuKitModalHoverMixin.java` — HEAD-cancellable on `AbstractContainerScreen.getHoveredSlot`. Suppresses slot-hover-driven feedback (highlights + tooltips).
- `mixin/MenuKitTooltipSuppressMixin.java` — HEAD-cancellable on `GuiGraphics.setTooltipForNextFrameInternal`. Suppresses tooltip queueing for ALL screen-specific tooltip paths.

### Modified files

- `core/Panel.java` — adds `cancelsUnhandledClicks(boolean)` chainable setter + `cancelsUnhandledClicks()` getter. Modality is a property of the visual element.
- `core/MenuRegion.java` — adds `CENTER` enum value.
- `core/StandaloneRegion.java` — adds `CENTER` enum value as reserved API (StandaloneRegion's resolver is generally deferred per its existing javadoc; CENTER joins the reserved list pending the broader resolver work).
- `core/RegionMath.java` — adds `resolveMenu` case for `MenuRegion.CENTER` (centers panel within the menu's container frame, honoring chrome adjustments per M5). No `resolveSlotGroup` case (SlotGroupContext doesn't apply per §4.5). No `resolveStandalone` (resolver deferred).
- `inject/ScreenPanelAdapter.java` — non-modal render passes use `RenderContext` with `mouseX = -1` (HUD inert sentinel) when a modal is visible on the same screen, suppressing all element hover state via the existing `hasMouseInput()` / `isHovered(...)` API.
- `inject/ScreenPanelRegistry.java` — adds `dispatchModalClick(Screen, double, double, int)` (atomic dispatch+eat for clicks at modal coords), `hasAnyVisibleModal()` (modal-on-current-screen query for input-handler mixins and cursor sync), `hasVisibleModalOnScreen(AbstractContainerScreen)` (per-screen query). `renderMatchingPanels` runs three passes: non-modal → dim → modal.
- `MenuKitClient.java` — registers per-tick `ClientTickEvents.END_CLIENT_TICK` callback that syncs `Window.setAllowCursorChanges(!hasAnyVisibleModal())`. Single vanilla-flag toggle replaces per-widget cursor patches.
- `resources/menukit.mixins.json` — registers the new client-side mixins.

**No changes to PanelElement, M8 helpers, or existing element types.** Dialogs are composition over existing elements + the modal primitive.

### Lines of code estimate (round 3 actuals)

- ConfirmDialog.java: ~210 lines (builder + Panel composition + javadoc)
- AlertDialog.java: ~165 lines (one fewer button than Confirm)
- Panel.java: ~50 lines (field + chainable setter + getter + extensive javadoc covering modal semantics)
- MenuRegion + StandaloneRegion: +1 enum value each + javadoc
- RegionMath: +CENTER case + restructured overflow check (~30 lines)
- ScreenPanelAdapter: +inert RenderContext sentinel block (~20 lines)
- ScreenPanelRegistry: dispatchModalClick + hasAnyVisibleModal + hasVisibleModalOnScreen + two-pass render (~150 lines)
- MenuKitModalMouseHandlerMixin: ~120 lines (button + scroll mixins + javadoc)
- MenuKitModalKeyboardHandlerMixin: ~50 lines (key + Escape allowlist + javadoc)
- MenuKitModalHoverMixin: ~70 lines (getHoveredSlot mixin + javadoc)
- MenuKitTooltipSuppressMixin: ~75 lines (setTooltipForNextFrameInternal mixin + javadoc)
- MenuKitClient.java: +15 lines (per-tick cursor flag sync)

Total new+modified: ~950 LOC. Round-1 estimate was 250; round-3 architecture expanded the dispatch mechanism to the input-handler layer with substantial javadoc covering the architectural progression. The grown LOC reflects the architectural completeness (single dispatch decision at the right layer), not implementation bloat — most of the additions are javadoc that records what we learned across rounds for future readers.

---

## 7. Migration plan — Phase 14d-1

14d-1 ships:
- ConfirmDialog + AlertDialog builders
- `MenuRegion.CENTER` (with resolver) + `StandaloneRegion.CENTER` (reserved enum value, resolver deferred with the rest of StandaloneRegion)
- `Panel.cancelsUnhandledClicks(boolean)` flag (refined location from round 1 — see §10)
- ScreenPanelRegistry MenuContext path consults the flag

Validation: dev-client smoke with a confirm dialog over the player inventory or a chest screen (MenuContext path). Sandboxes "delete sandbox?" originally targeted as canonical smoke; pending MenuKit-native screen dispatch resolution (§4.5 finding) it shifts to a follow-on smoke after the dispatch question is verdicted.

No consumer migration in 14d-1. Phase 15a (IP) is the first consumer that may adopt dialogs in its migration; Phase 15e (sandboxes) is a strong candidate once MenuKit-native screen dispatch is resolved.

---

## 8. Verification plan

### 8.1 `/mkverify` aggregator probe — V10 dialog composition

New validator scenario: **V10 — dialog composition probe.** Programmatic, no live screen:

- Build a ConfirmDialog programmatically; assert the returned Panel contains expected elements (title TextLabel, body TextLabel, two Buttons in the right positions per M8 layout math).
- Build an AlertDialog; assert single Button.
- Verify Panel's auto-size width/height reflect the longest line of (title, body, button row).
- Test edge cases: empty title (allowed?), null callbacks (rejected?), long body text (overflows panel — documented behavior).

Same shape as M8's V9 probe.

### 8.2 Click-eat probe — V11 modal click cancellation

- Construct a Panel with `cancelsUnhandledClicks(true)`, frame bounds (10, 10, 100, 50), no interactive elements.
- Simulate a click at (50, 30) — inside frame bounds, no element hit; assert `allowMouseClick` returns `false` (eaten).
- Simulate a click at (200, 200) — outside frame bounds; assert `allowMouseClick` returns `true` (passes through).
- Repeat with the adapter for a non-modal panel (default flag); assert all clicks return `true`.

### 8.3 Integration smoke (dev-client)

- Open sandboxes UI; trigger ConfirmDialog for delete-sandbox flow; verify:
  - Dialog renders centered on screen
  - Click outside dialog does not interact with sandboxes UI
  - Click inside dialog buttons fires the right callback
  - Dismissal closes dialog and returns input to underlying UI
- Open AlertDialog for an operation-failure case; verify single-button acknowledge dismisses correctly.

If 14d-1 smoke surfaces visual unmoored-ness (per §4.10), fold dim-behind in via a post-smoke fold-in commit; otherwise dim becomes a deferred primitive.

---

## 9. Library vs consumer boundary

**Library provides:**
- ConfirmDialog + AlertDialog builders (composition over existing primitives + M8)
- `MenuRegion.CENTER` + `StandaloneRegion.CENTER` for centered modal positioning
- `ScreenPanelAdapter.cancelsUnhandledClicks(boolean)` flag for modal click-eating
- Default labels ("Confirm", "Cancel", "OK") with override hooks
- Default sizing (button widths, padding, gaps) with no override hooks (consumer composes manually for non-standard layouts)

**Consumers provide:**
- `BooleanSupplier` for visibility (Principle 8 lensing)
- Callback `Runnable`s for Confirm / Cancel / Acknowledge actions
- Title and body `Component`s
- `ScreenPanelAdapter` registration with `.on(...)` targeting (which screens host the dialog)
- Multi-line body composition if needed (manually, until multi-line TextLabel ships)

**Library does NOT provide:**
- Library-managed dialog stack (`MenuKit.showDialog(...)`) — Principle 1
- Cross-mod dialog mediation
- Auto-dismissal logic — consumer mutates state in callbacks (Principle 8)
- Multi-line / wrapped body content in v1 — deferred primitive
- Dim-behind overlay in v1 — deferred unless smoke demands
- Custom button counts beyond 1 (Alert) and 2 (Confirm) — consumer composes manually for 3+
- Icon-decorated dialogs — consumer composes manually
- Text-input prompt dialogs — separate primitive (text input is its own 14d sub-phase)

---

## 10. Round 1 verdicts

Round 1 closed with four advisor verdicts, three implementer pulls signed off, one architectural nit folded into §4.5, and two doc-framing nits folded into §4.6 + §4.10.

### Advisor verdicts (all four match implementer pulls)

1. **Modal mechanism (§4.1).** Ship shape (i) — `cancelsUnhandledClicks(boolean)` flag on existing `ScreenPanelAdapter`. Per-adapter, nothing global, library-not-platform clean. Mechanism already half-designed in the codebase (`ScreenPanelRegistry.java:244` placeholder + CONTEXTS.md flagged extension). Shape (ii) `Panel.modal(true)` rejected — would push cross-panel priority into the dispatcher (platform behavior).

2. **CENTER region scope (§4.6).** Ship `MenuRegion.CENTER` + `StandaloneRegion.CENTER` additively in 14d-1's commit set. Per-entry cost trivial; migration cost real if shipped later (consumers reinvent lambda-anchor centering arithmetic per dialog). One concrete consumer (dialogs) is enough under the low-cost-additive-primitive relaxation.

3. **Multi-line body in v1 (§4.9).** Single-line body in v1 with deferred multi-line `TextLabel` filing. Shape (II) varargs creates API drift; shape (III) is real design surface deserving its own evidence-driven phase. Escape-hatch pattern (Column-of-TextLabels) made prominent in §5 per advisor's ask.

4. **Dim-behind (§4.10).** Ship without dim in v1; validate during 14d-1 smoke; fold via post-smoke fold-in commit if smoke demands. Matches 14b drop-on-break shape.

### Implementer pulls signed off (no objection)

- **Lifecycle (§4.2)**: shape (b) Panel-layered-on-current-Screen — only shape holding Principle 4 across MenuContext + StandaloneContext uniformly.
- **Library-managed vs consumer-managed (§4.3)**: consumer-managed. Principle 1 clean; cross-mod scenarios handled by structural independence.
- **API shape (§4.7)**: builder returning Panel; `Runnable` callbacks. Matches Phase 9 specialization-not-builder-method discipline + Toggle.linked precedent.

### Architectural nit folded inline (§4.5)

StandaloneContext native-screen dispatch path was underspecified. Resolution: dialogs use the **decorator pattern uniformly across all host kinds** — even on MenuKit-native screens, consumers register a `ScreenPanelAdapter` for the dialog Panel. Single mechanism; `cancelsUnhandledClicks` works through the same code path on every host. Folded into §4.5.

### Doc-framing nits folded inline

- **§4.6 framing.** "Exhaustive coverage exception" framing was wrong (CENTER isn't a complete catalog). Re-framed as "low-cost evidence-driven additive primitive within Rule-of-Three relaxation" — different Principle 11 mechanism, same outcome.
- **§4.10 round nomenclature.** "Round 1.5" replaced with "post-smoke fold-in commit" throughout. Post-smoke folds are inline verdicts, not new rounds — calibration discipline preserved.

### Implementation findings (post-verdict, surfaced during prep)

Two findings surfaced during Commit 1 prep that warrant explicit advisor attention. Folded into the doc inline; surfaced in the close-out brief:

**Finding A: Modal flag location refined from `ScreenPanelAdapter` to `Panel`.** Round 1 framing put `cancelsUnhandledClicks(boolean)` on the adapter (matching the existing CONTEXTS.md hint and `ScreenPanelRegistry.java:244` placeholder comment). Implementation prep surfaced an architectural improvement: modality is a property of the visual element, not the dispatch transport. Putting the flag on Panel:
- Aligns with Principle 8 ("elements are lenses, not stores") — visual properties live on the visual primitive
- Generalizes cleanly across dispatch paths (MenuContext via adapter; future MenuKit-native via native dispatch)
- Same per-adapter / per-screen reasoning as round 1's verdict — nothing global, nothing platform-y; structural test still passes

**Architectural intent unchanged from round 1's shape (i) verdict.** The flag's location moved one level (adapter → panel) for cleanness across dispatch paths. Folded inline; close-out brief asks advisor whether this should have been a round-2 conversation or qualifies as an implementation refinement under round-1's approval umbrella.

**Finding B: MenuKit-native screen dispatch is real new library surface, not a fold-inline.** Round 1's "decorator pattern uniformly across both kinds" pull (§4.5) assumed `ScreenPanelAdapter` could dispatch on both vanilla container screens AND `MenuKitScreen` subclasses with light extension. Implementation prep traced the dispatch path end-to-end and found:
- `ScreenPanelRegistry.java:212` early-returns for non-`AbstractContainerScreen` instances
- `ScreenPanelAdapter.render(...)` and `mouseClicked(...)` require `AbstractContainerScreen<?>` parameters
- Modal dialogs over `MenuKitScreen` need to bypass the screen's `PanelLayout` constraint flow (modal panels float on top, not in layout)

Extending the dispatch primitive to support `MenuKitScreen` is a real library expansion — not a fold-inline. Three architectural shapes worth surfacing:

- **(α) Generalize ScreenPanelAdapter to dispatch on any Screen subclass.** Relax `AbstractContainerScreen` parameter type to `Screen`; extend ScreenPanelRegistry's `onScreenInit` to handle `MenuKitScreen` as a separate dispatch case (likely needs a render hook; click hook via `ScreenMouseEvents` is already universal). Real new surface but uniform decorator pattern across all hosts.
- **(β) Native dispatch on MenuKitScreen.** Dialogs added to `MenuKitScreen.panels` directly; `MenuKitScreen.dispatchElementClick` consults `panel.cancelsUnhandledClicks()`. Requires solving the modal-panel-bypasses-PanelLayout question (new `PanelPosition.OVERLAY`? new `screen.overlayPanels` list?). Different dispatch paths but no adapter for native screens.
- **(γ) Defer MenuKit-native screen support to a later phase.** 14d-1 ships MenuContext-only; sandboxes and other MenuKitScreen consumers wait for the architectural call.

**14d-1 resolution: ship MenuContext path; defer native dispatch.** Real architectural surface deserves an explicit advisor call, not a unilateral implementer decision under "fold-inline" framing.

**Round-2 verdict on Finding B: defer (γ).** Phase 14d-1 is already expanding scope to ship full modality (Finding C). Adding MenuKit-native dispatch on top is too much for one phase. Filed as DEFERRED.md item with trigger: first concrete consumer in Phase 15 (sandboxes selector wanting modal dialog, or IP equipment screen wanting one) drives the (α) vs (β) choice at that point.

### Round 2 record — Finding C (post-smoke architectural finding)

Round 1's verdict on shape (i) — *"modality emerges from existing primitives + the cancelsUnhandledClicks flag"* — was based on the hypothesis that adapter-level click-eat would deliver modality. Smoke through `CreativeModeInventoryScreen` disproved the hypothesis:

- **Symptom 1 — tabs interactable through dialog:** clicking creative tabs while the dialog was visible **switched tabs**. `CreativeModeInventoryScreen.mouseClicked` processes tab clicks BEFORE super-calling, so Fabric's `ScreenMouseEvents.allowMouseClick` (which mixins into `Screen.mouseClicked` HEAD) never fired for those clicks. Silent-inert dispatch failure mode CONTEXTS.md warns about.
- **Symptom 2 — hover tooltips leak through dialog:** tooltips for slots underneath the dialog still rendered. Tooltip dispatch is a separate pipeline (`renderDeferredElements` after `render` returns); the click-eat flag had no effect on it.
- **Symptom 3 — visual distinction insufficient:** dialogs without dim-behind read as "another panel layered on" rather than "modal interrupt." §4.10 dim verdict: needed, not deferred.

Round 2 advisor verdict: **ship (I) — full modal primitive.** Three reasons:
- (II) "non-modal dialogs as composition primitives" reframes dialogs to a value that doesn't earn the primitive — `Panel.of(title, body, button-row)` is already trivial via M8.
- (III) "defer dialogs entirely" leaves orphan primitives (Panel.cancelsUnhandledClicks + CENTER region with no consumer).
- (I) is bounded and well-shaped. Consumer surface unchanged; library plumbing fuller.

**Implementation shape:**
- Multi-target HEAD-cancellable mixin on every vanilla mouseClicked override class (`Screen` + 3 subclasses). Catches subclass tab pre-empts.
- Queue-clearing accessor for tooltip suppression (preferred over mixing into `setTooltipForNextFrame` — render path is library-owned, mixin would be vanilla-code-path-flavored).
- Dim-behind overlay in adapter render path before panel background.
- Keyboard suppression deferred — fold-on-evidence post-smoke (Escape closing underlying screen is acceptable v1).

**Principle 1 audit (passed):** the mixin into `Screen.mouseClicked` HEAD is library-wide dispatch surface; the flag is per-Panel. Two mods both shipping modal panels coexist independently — each consumer's modal works on its own; library mediates "any visible modal" without taking ownership across mods. Multi-modal mid-overlap documented as consumer-side concern (out of library scope).

**Future calibration heuristic (advisor):** location moves between architectural layers (element vs dispatch vs screen) are architectural changes worth rounds. Refinements within the same layer are implementation. Findings about dispatch path completeness (Finding C) are architectural — round was the right call.

### Round 2 implementation findings

Round 3 only if **another architectural gap** surfaces at the dispatch-completeness level. Implementation-level surprises (mixin pattern doesn't work, vanilla API quirk in the inject point) fold inline.

### Round 3 record — input-handler-level pre-emption (the architecture round-2 should have surfaced)

Round 2's "ship full modal primitive" verdict implicitly accepted **piecemeal-suppression** as the implementation: per-Screen mouseClicked mixins on every class that overrides without super-calling, a tooltip-suppress mixin on its own pipeline, a dim per-adapter, etc. Round-2 smoke (post-implementation) surfaced symptoms of this approach being structurally fragile — tabs still switched (because subclass overrides pre-empt parent mixins), tooltips leaked through (queue-clear approach insufficient because tab tooltips queue after super.render returns).

Trevor's framing during smoke session: *"Is there some bigger architectural change we need to look at, where it just cancels out everything in the screen all at once? Or do we absolutely have to make the cancellations one by one? That seems like the wrong way to do it..."* — exactly the question round 2 should have surfaced and didn't.

**Round-3 advisor verdict: ship (B) input-handler-level pre-emption.** Mixing into `MouseHandler.onButton` / `onScroll` and `KeyboardHandler.keyPress` at HEAD intercepts BEFORE any per-Screen routing. Subclass overrides become irrelevant — the library's mixin runs first regardless of which Screen subclass is active. Single hook per input device. Drop all per-Screen mixins (round-2's `MenuKitModalClickEatMixin`); they're superseded by the input-handler layer.

**Round-3 implementation findings (post-verdict, surfaced during smoke):**

- **HiDPI coord conversion bug.** Initial round-3 implementation used `Window.getWidth()` / `getHeight()` for scaling — these return framebuffer pixels (HiDPI-doubled on Retina displays). Vanilla `MouseHandler.onButton` uses `getScreenWidth()` / `getScreenHeight()` (logical window pixels). Smoke surfaced "buttons don't work" because click coords were wrong by a factor of 2 on Retina. Fix: one-line method swap. Pure implementation bug; architecture intact.

- **Escape eaten contradicted round-2 verdict.** Round-3 implementation eat-all-keys including Escape; smoke surfaced "can't exit anything" frustration (compounded with the coord bug). Round-2 verdict had been explicit: *"Escape closing the underlying screen is acceptable v1 behavior."* Forgot when implementing round-3. Fix: GLFW key 256 allowlist in keyboard mixin.

- **Cursor change wasn't suppressed by per-Screen approach.** Cursor changes when hovering over creative tabs were visible even after round-3's input-handler mixins eliminated tab clicks. Round-3 attempt added a per-class `MenuKitModalCreativeTabHoverMixin` patching `checkTabHovering`. Trevor's pushback: *"sounds like checktabhovering is yet another single-instance patch, not an architectural fix. It should be cancelling ANY sort of hover behavior."* — pointing at the same architectural concern again. Investigation surfaced vanilla's `Window.setAllowCursorChanges(boolean)` — a global flag that coerces `selectCursor` to DEFAULT (verified in bytecode). Single vanilla-flag toggle synced per-tick to `!hasAnyVisibleModal()` replaces all per-widget cursor patches. Architectural answer all along; we just hadn't found the lever.

- **Dim coverage didn't reach non-modal MK panels.** Round-3 dim was rendered per-adapter inside `ScreenPanelAdapter.render` — only fired when modal panel rendered. If non-modal MK panels iterated AFTER the modal in `menuMatches`, they covered the dim. Order-fragile. Fix: two-pass render order at the dispatcher (`ScreenPanelRegistry.renderMatchingPanels`) — non-modal pass → dim pass → modal pass. Single render-order pattern enforced architecturally. Same shape as Trevor's repeated architectural framing: solve at the dispatch boundary, not per-widget.

- **Off-screen mouse coords for inert panels was hacky.** Round-3 attempt initially used `Integer.MIN_VALUE / 2` as "fake mouse position" to make non-modal MK panels report no hover. Trevor's pushback: *"the whole off-screen coords seems kinda hacky."* Investigation surfaced that `RenderContext` already had the right primitive — the `-1` "no input dispatch" sentinel HUDs already use. `RenderContext.hasMouseInput()` returns false; `isHovered(...)` short-circuits. Reusing the existing convention rather than inventing a new one. Architectural primitive was right there; we just hadn't recognized it.

**Library-not-platform check (round-3).** Mixins into `MouseHandler` / `KeyboardHandler` are library-wide dispatch surface. Structural test still passes — per-Panel flag is the consumer surface; library-wide dispatch checks "any visible modal panel" via `hasAnyVisibleModal()`. Two mods both shipping modal panels coexist via structural independence; library doesn't mediate. Cursor flag is vanilla's own — observational, not ownership.

**Lambda-adapter limitation (filed for follow-on).** `ScreenPanelAdapter`'s lambda-based form (constructed with `ScreenOriginFn` instead of `MenuRegion`) is the escape hatch for custom positioning — these adapters bypass `ScreenPanelRegistry` and render via consumer-side mixins. The two-pass dim and inert-RenderContext sentinel only apply to region-based adapters in the registry. Lambda-path consumers (validator probes, M3 grafted-slot backdrops, etc.) need their own dim/inert wiring if they want modal-aware behavior. Filed in DEFERRED.md as "lambda-adapter modal-aware extension" with trigger: first concrete consumer wanting modal-aware behavior in a lambda adapter.

**Future calibration heuristic (saved to memory):** if delivering a primitive X requires multiple compounding mixins at the same architectural layer, that's a signal X needs a different layer. Ask "what's the smallest dispatch level above which X's mechanism can sit?" — for modality, that's the input-handler dispatch root, not per-Screen-method. And: when an apparent "single-instance patch" is needed (e.g., per-widget hover suppression), look for a vanilla flag or existing primitive that already centralizes the behavior — Trevor's repeated "architectural?" pushback found `Window.setAllowCursorChanges` and the `-1` RenderContext sentinel both via this question.

### Round 3 closes

Smoke green for: modal click-eat across all input modifiers (shift, right, double, drag), tooltip suppression, cursor suppression, tab interaction blocking, slot pickup blocking, hover-feedback suppression for all PanelElement kinds, escape passthrough, dim covering both vanilla AND non-modal MK panels (via two-pass render), modal Panel buttons dispatching correctly.

Documented limitations:
- Lambda-adapter consumer panels not dim/inert (filed for follow-on, see DEFERRED.md)
- Modals-with-slots not supported (decoration-only design, filed for follow-on per Trevor's request)
- Keyboard suppression v1 = "all keys eaten except Escape" (fold-on-evidence per round-2 verdict)
- MenuKit-native screen dispatch deferred (Finding B, filed for follow-on)

Phase ready to commit + close.

---

## 11. Non-goals / out of scope

- **Library-managed dialog stack.** Principle 1 — cross-mod stack mediation isn't MenuKit's job.
- **Auto-dismissal in callbacks.** Consumer mutates their visibility state explicitly per Principle 8.
- **Multi-line wrapped body in v1.** Deferred primitive (see DEFERRED.md filing).
- **Dim-behind overlay in v1.** Deferred unless smoke demands.
- **Three-or-more-button dialogs** (e.g., "Save / Don't Save / Cancel"). Consumer composes manually using the same M8 pattern; document the path.
- **Text-input dialogs.** Separate 14d sub-phase (text input element first, then a TextInputDialog primitive may emerge).
- **Icon-decorated dialogs.** Consumer composes Icon + dialog body manually.
- **Async / promise-based dialog APIs.** No `CompletableFuture<DialogResult>`. Callbacks are synchronous; consumer wires async logic in their handler if needed.
- **Animation on open / dismiss.** Per THESIS scope ceiling — no animation framework beyond notifications.
- **Stacked dialogs visible simultaneously.** Library doesn't enforce one-modal-at-a-time, but advises consumers to gate visibility so only one is up at once. Two visible modal panels in the same screen would each eat clicks within their own bounds; clicks outside both fall through. Consumer-owned constraint.

---

## 12. Summary

ConfirmDialog and AlertDialog ship as **builders that produce composed Panels** — no new container concept, no new render pipeline, no library-managed dialog stack. The primitives below are existing (Panel, TextLabel, Button, M8 Row/Column, ScreenPanelAdapter) plus three small additives:

1. **`MenuRegion.CENTER` + `StandaloneRegion.CENTER`** — centered positioning for modal dialogs. Low-cost additive primitive shipping on first concrete consumer (dialogs) under the Rule-of-Three relaxation for trivially-priced primitives where the alternative is per-consumer reinvention.
2. **`ScreenPanelAdapter.cancelsUnhandledClicks(boolean)`** — modal click-eating, already roadmapped in CONTEXTS.md.
3. **`ConfirmDialog.builder()` + `AlertDialog.builder()`** — opinionated factories over the canonical dialog shape.

Cross-context: MenuContext + StandaloneContext only (HudContext is render-only; SlotGroupContext anchors mismatch). Visibility lives in consumer state per Principle 8. Library does not manage dialog lifecycle; consumer adds the dialog Panel to their UI list with `showWhen(...)` and mutates visibility from button callbacks.

Two deferred primitives surfaced and filed: **multi-line TextLabel** (consumers compose Column-of-TextLabels in v1; escape hatch documented in §5) and **dim-behind overlay** (validated during 14d-1 smoke; fold in via post-smoke commit if real, otherwise deferred).

**Status: round 1 closed; advisor-approved.** All four verdicts match implementer pulls; three implementer pulls signed off; one architectural nit (StandaloneContext dispatch) and two doc-framing nits (§4.6, §4.10) folded inline. Implementation can begin: ~250 LOC across three additive primitives + two dialog builders. V10 + V11 verification probes alongside; sandboxes "delete sandbox?" smoke validates M8 + dialogs end-to-end.
