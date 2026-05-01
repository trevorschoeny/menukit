# Phase 14d-1 — Close-out REPORT (ConfirmDialog + AlertDialog + modal primitive)

**Status: complete.** Modal dialog primitive shipped (ConfirmDialog + AlertDialog as builders producing composed Panels), built on a library-wide modal mechanism that lives at the input-handler dispatch root. Three rounds of design + implementation + smoke; each round surfaced a deeper architectural finding the previous round hadn't anticipated. Final architecture is structurally clean — single mental model ("modal is a per-Panel flag; library pre-empts at the input dispatch root") with the dispatch mechanism bolted on at the right architectural layer. All 11 `/mkverify` contracts pass; no regressions across V2-V7 validator scenarios.

---

## Executive summary

Phase 14d-1's deliverable was the dialog primitive (ConfirmDialog + AlertDialog) per `PHASES.md` §14d. The hypothesis going in: dialogs are compositional Panels (title + body + button row via M8) plus a modality affordance. The modality affordance was the architectural surface — *what does "modal" mean in MenuKit, structurally?*

Three rounds of architectural evolution as smoke surfaced gaps:

- **Round 1**: ship the dialog primitive + modal flag at the adapter layer (`ScreenPanelAdapter.cancelsUnhandledClicks(boolean)`) consulted from Fabric's `ScreenMouseEvents.allowMouseClick` hook. Approved as shape (i) "modality emerges from existing primitives + flag." Implementation prep refined the flag location to `Panel` rather than the adapter (aligns with Principle 8 "elements are lenses, not stores"). All 4 advisor verdicts approved + 3 implementer pulls signed off; one architectural nit (StandaloneContext native dispatch) and two doc-framing nits folded inline.

- **Round 2**: smoke through `CreativeModeInventoryScreen` surfaced that adapter-level click-eat is structurally insufficient. Subclass overrides that pre-empt `super.mouseClicked(...)` (creative tabs) bypass any Fabric hook on the parent class — the silent-inert dispatch failure mode CONTEXTS.md documents. Tooltip rendering is a separate pipeline; render-path queue-clear couldn't catch tab tooltips queued post-super. Visual distinction via dim was needed (not deferred). Round-2 advisor verdict: ship (I) full modal primitive — multi-target HEAD-cancellable mixin on every vanilla `mouseClicked` override class + tooltip suppress mixin + dim overlay + Panel-level flag staying as consumer surface. Library plumbing fuller; consumer API unchanged.

- **Round 3**: Trevor's smoke pushback: *"Is there some bigger architectural change we need to look at, where it just cancels out everything in the screen all at once? Or do we absolutely have to make the cancellations one by one? That seems like the wrong way to do it..."* — exactly the question round 2 should have surfaced and didn't. The piecemeal-suppression approach implicit in shape (i) was the structural problem. Round-3 advisor verdict: ship (B) input-handler-level pre-emption — mixin into `MouseHandler.onButton` / `onScroll` and `KeyboardHandler.keyPress` at HEAD, fires BEFORE any per-Screen routing, subclass overrides become irrelevant. Drop round-2's per-Screen mixins; replace with input-handler layer. Single hook per input device covers every screen (vanilla and modded) uniformly.

Round-3 smoke surfaced four further architectural-shape questions that fold-on-evidence + Trevor's repeated "is there an architectural way?" pushback resolved cleanly:

1. **Cursor change on hover**: vanilla has `Window.setAllowCursorChanges(boolean)` — single global flag that coerces all `selectCursor` calls to DEFAULT. Per-tick callback syncs flag to `!hasAnyVisibleModal()`. Replaced an attempted per-class `MenuKitModalCreativeTabHoverMixin` patch.

2. **Dim coverage of non-modal MK panels**: per-adapter dim was order-fragile (only worked when modal iterated last in `menuMatches`). Two-pass render order at the dispatcher (non-modal → dim → modal) enforces visual order architecturally regardless of registration order.

3. **Hover-feedback suppression for MenuKit panel elements**: tried lying about mouse coords with `Integer.MIN_VALUE / 2`. Trevor's pushback ("kinda hacky"). Investigation surfaced that `RenderContext` already had the right primitive — the `-1` "no input dispatch" sentinel HUDs use. `RenderContext.hasMouseInput()` returns false; `isHovered(...)` short-circuits. All `PanelElement` kinds inherit inert behavior automatically through the existing context API — no per-element mixins.

4. **Coordinate conversion bug + Escape key behavior**: `Window.getWidth()` returns framebuffer pixels (HiDPI-doubled); should be `getScreenWidth()` per vanilla's own `MouseHandler.onButton` formula. Escape was eaten contradicting round-2's "Escape closes screen as normal v1 behavior" verdict; one-line allowlist for GLFW key 256.

What shipped:

- **`core/dialog/` package** — ConfirmDialog + AlertDialog builders, returning configured Panels with `cancelsUnhandledClicks(true)` baked in. Composition over Panel + TextLabel + Button + M8 layout.
- **`Panel.cancelsUnhandledClicks(boolean)` flag** + getter — modality is a per-Panel property (consumer-facing surface, unchanged across rounds).
- **`MenuRegion.CENTER` + `StandaloneRegion.CENTER`** — centered-in-frame anchor for modal dialogs (StandaloneRegion.CENTER is reserved per its existing javadoc; resolver lands when StandaloneRegion gets resolvers in a follow-on phase).
- **Four library mixins** — modal pre-emption: `MenuKitModalMouseHandlerMixin` (button + scroll), `MenuKitModalKeyboardHandlerMixin` (key + Escape allowlist), `MenuKitModalHoverMixin` (slot hover), `MenuKitTooltipSuppressMixin` (tooltip queue).
- **Cursor flag sync** in `MenuKitClient.onInitializeClient` — per-tick `ClientTickEvents.END_CLIENT_TICK` callback that toggles `Window.setAllowCursorChanges(...)`. Vanilla flag, single observational toggle.
- **Two-pass render order** in `ScreenPanelRegistry.renderMatchingPanels` — non-modal → dim → modal.
- **Inert-RenderContext sentinel** (existing `-1` convention) propagated to non-modal MK panels in `ScreenPanelAdapter.render` when modal visible on same screen.
- **V10 modal click-eat probe + V11 dialog builder validation probe** in `ContractVerification`; aggregator now reports 11/11 contracts.
- **Design doc** at `Design Docs/Elements/DIALOGS.md` — full round-1/2/3 architectural record (~750 lines).
- **Six DEFERRED.md follow-ons** filed: multi-line TextLabel, MenuKit-native screen dispatch, dim-behind (now resolved as shipped), keyboard suppression beyond Escape, modals-with-slots, lambda-adapter modal-aware extension.

---

## Architecture decisions

### Modal as per-Panel flag with library-wide pre-emption

Final architecture: `Panel.cancelsUnhandledClicks(boolean)` on the consumer side; library-wide HEAD-cancellable mixins at the input-handler dispatch root on the implementation side. The split is intentional:

- **Consumer surface**: per-Panel flag means modality is a property of the visual element (Principle 8 "elements are lenses, not stores"). Consumer reads as: "this Panel is modal." No library-managed dialog stack, no global modal API.
- **Library mechanism**: input-handler-level pre-emption means modality gets delivered uniformly across every screen (vanilla + modded) without per-Screen-method patches. Single dispatch decision in one place.

The structural test for "is the dispatch layer right?" is: *can a single hook intercept the input before per-Screen-method overrides get the chance to pre-empt their super-call?* For mouse/keyboard, yes — `MouseHandler.onButton` / `KeyboardHandler.keyPress` are the dispatch root. For render-time hover, the test changes — hover state is computed during render, not from input events; the right level there is per-screen-class hover-detection (`AbstractContainerScreen.getHoveredSlot`) plus the cursor flag for OS-level cursor.

### Architectural progression across rounds

Round 1's "flag at adapter level + Fabric hook" was structurally insufficient for the silent-inert dispatch failure mode — the question was "what's the right dispatch layer?" not "what's the flag location?" Round 2 (multi-target Screen mixins) hit the right kind of layer (per-Screen-method) but the WRONG layer — too low; subclasses with overrides not in the multi-target list aren't covered. Round 3 (input-handler) hit the actually-right layer — fires before any per-Screen routing.

Calibration heuristic surfaced (saved to memory): *if delivering a primitive X requires multiple compounding mixins at the same architectural layer, that's a signal X needs a different layer. Ask "what's the smallest dispatch level above which X's mechanism can sit?"*

A second heuristic: *when an apparent "single-instance patch" is needed (per-widget hover suppression), look for a vanilla flag or existing primitive that already centralizes the behavior.* Trevor's repeated "architectural?" pushback found `Window.setAllowCursorChanges` and the `-1` `RenderContext` sentinel both via this question — the right primitives already existed; we just hadn't recognized them.

### Cross-context applicability

Dialogs target MenuContext + StandaloneContext (vanilla standalone screens via mixin). MenuKit-native screen dispatch is deferred (Finding B; trigger: first concrete consumer wanting modal dialog on a `MenuKitScreen` subclass). HudContext doesn't fit (render-only); SlotGroupContext doesn't fit (anchor mismatch).

---

## What shipped

### Library — new

| File | Role |
|---|---|
| `core/dialog/ConfirmDialog.java` | Builder for confirm/cancel dialog; returns Panel |
| `core/dialog/AlertDialog.java` | Builder for single-button acknowledge dialog; returns Panel |
| `mixin/MenuKitModalMouseHandlerMixin.java` | HEAD-cancellable on `MouseHandler.onButton` + `onScroll` |
| `mixin/MenuKitModalKeyboardHandlerMixin.java` | HEAD-cancellable on `KeyboardHandler.keyPress` (Escape allowlist) |
| `mixin/MenuKitModalHoverMixin.java` | HEAD-cancellable on `AbstractContainerScreen.getHoveredSlot` |
| `mixin/MenuKitTooltipSuppressMixin.java` | HEAD-cancellable on `GuiGraphics.setTooltipForNextFrameInternal` |
| `Design Docs/Elements/DIALOGS.md` | ~750 lines — full round-1/2/3 design record |
| `Design Docs/Phases/14d-1/REPORT.md` | this file |

### Library — modified

| File | Change |
|---|---|
| `core/Panel.java` | + `cancelsUnhandledClicks(boolean)` chainable setter + getter |
| `core/MenuRegion.java` | + `CENTER` enum value |
| `core/StandaloneRegion.java` | + `CENTER` reserved value (resolver deferred) |
| `core/RegionMath.java` | + `CENTER` resolver case for MenuRegion |
| `inject/ScreenPanelAdapter.java` | non-modal panels get `RenderContext` with `-1` mouse sentinel when modal visible on same screen |
| `inject/ScreenPanelRegistry.java` | + `dispatchModalClick`, `hasAnyVisibleModal`, `hasVisibleModalOnScreen`; two-pass render order in `renderMatchingPanels` |
| `MenuKitClient.java` | + per-tick cursor-flag sync via `ClientTickEvents` |
| `verification/ContractVerification.java` | + V10 modal click-eat probe + V11 dialog builder probe + `/mkverify dialog` smoke command + smoke ConfirmDialog wire-up + LEFT_ALIGN_TOP test panel |
| `verification/RegionProbes.java` | + color for `MenuRegion.CENTER` (exhaustive switch coverage) |
| `resources/menukit.mixins.json` | registers 4 new client-side mixins |
| `Design Docs/DEFERRED.md` | + 6 14d-1 follow-on items |

### V10 + V11 verification

| Probe | Cases | Result |
|---|---|---|
| **10 M10 modal click-eat** | 12 | PASS — Panel flag mechanics; dispatcher decision truth table; `MenuRegion.CENTER` resolver math; CENTER overflow check |
| **11 M11 dialog builder** | 11 | PASS — required-field validation for ConfirmDialog + AlertDialog; builder fluency; null-guards on setters |

V10 + V11 are pure-logic probes (no live screen needed). Visual smoke covers the integration: dialog renders centered with dim, all input modifiers blocked, cursor doesn't change, hover feedback suppressed for vanilla widgets + MK panels, buttons dispatch correctly, Escape closes underlying screen.

`/mkverify` aggregator reports 11/11 library contracts PASS + 5/5 validator scenarios PASS. No regressions.

---

## What didn't ship / deferred

Six items filed in `DEFERRED.md` Phase 14d-1 follow-on section:

- **Multi-line / wrapped `TextLabel` variant** — dialog body is single-line in v1; multi-line consumers compose `Column.of(TextLabel.spec(...) × N)` manually. Trigger: 3+ concrete consumer cases.
- **MenuKit-native screen dispatch for dialogs** (Finding B) — 14d-1 ships MenuContext-only. Architectural shapes (α / β) verdicted at trigger time when first concrete `MenuKitScreen`-modal consumer surfaces.
- **Dim-behind overlay** — *resolved as shipped*. Round-2 deferred-pending-smoke; smoke said yes.
- **Keyboard suppression beyond Escape** — v1 = "all keys eaten except Escape." Fold-on-evidence if smoke surfaces specific keys that should pass through.
- **Modals with slot groups** — decoration-only design ships in v1. Slot-input dialogs are a separate architectural shape (modal-as-its-own-menu vs modal-as-decoration). Trigger: ASK TREVOR EXPLICITLY before implementing — he wants to drive the design conversation.
- **Lambda-adapter modal-aware extension** — region-based adapters get dim coverage + inert RenderContext propagation; lambda-path adapters don't. Click eating still works (input-handler mixins are universal); the leak is visual only. Trigger: first concrete consumer wanting modal-aware behavior in a lambda adapter.

---

## Process notes

**Three rounds.** Round 1 closed cleanly (4 advisor verdicts approved + 3 implementer pulls signed off; minor inline folds). Round 2 surfaced the structural-modal finding post-smoke and re-verdicted shape (I) full modal primitive. Round 3 surfaced the deeper architectural question (input-handler-level pre-emption) and re-verdicted shape (B). Each round was warranted — empirical learning about the right dispatch layer; not over-ceremonialization.

Round-3 had four post-verdict implementation findings folded inline (not new rounds): HiDPI coord bug, Escape eat, cursor flag (architectural), two-pass dim render order, off-screen-coords→`-1` sentinel reuse. All surfaced via Trevor's repeated "architectural?" pushback during smoke; each yielded an architecturally cleaner solution than the patch-form attempt.

Calibration discipline held: rounds for architectural decisions, fold-inline for implementation surprises, surface-don't-workaround for primitive gaps. The "validate the product, not just the primitives" Principle 7 earned again — programmatic V10/V11 probes pass cleanly, but the consumer-shaped smoke discovered every architectural gap.

**Two calibration heuristics saved to memory:**

1. *If delivering a primitive X requires multiple compounding mixins at the same architectural layer, that's a signal X needs a different layer. Ask "what's the smallest dispatch level above which X's mechanism can sit?"*
2. *When an apparent "single-instance patch" is needed (per-widget hover suppression, etc.), look for a vanilla flag or existing primitive that already centralizes the behavior.*

---

## Verification

### Automated (`/mkverify`)

11 library contracts + 5 validator scenarios. All PASS in dev-client smoke:

| Contract | Result |
|---|---|
| 1 Composability | mixin fired on both vanilla and MK slot types ✓ |
| 2 Substitutability | 46/46 MK slots pass `instanceof Slot` ✓ |
| 3 SyncSafety | 10 toggles, 0 inconsistencies ✓ |
| 4 Uniform | findGroup() uniform across vanilla + MK handlers ✓ |
| 5 Inertness | 4/4 hidden inert, 4/4 restored on visible ✓ |
| 6 RegionMath | 27/27 cases ✓ |
| 7 SlotState | 7/7 cases ✓ |
| 8 M7 storage | 6/6 cases ✓ |
| 9 M8 layout | 16/16 cases ✓ |
| **10 M10 modal click-eat** | **12/12 cases ✓** |
| **11 M11 dialog builder** | **11/11 cases ✓** |

Validator scenarios V2–V7 all green; no regressions from the modal mechanism.

### Visual smoke

Comprehensive smoke through creative inventory + survival inventory:

- Modal dialog renders centered with ~75% black dim overlay
- Click on tabs, slots, scroll bar — eaten (no tab switch, no slot pickup, no scroll)
- Shift-click, right-click, double-click on slots — all eaten via input-handler mixin
- Tooltips for slots underneath dialog — suppressed
- OS cursor stays as default arrow regardless of hover position
- Hover feedback on vanilla widgets (slot highlights) — suppressed
- Hover feedback on MenuKit panel elements (buttons, toggles in non-modal MK panels) — suppressed via `-1` RenderContext sentinel
- Dim covers vanilla content AND non-modal MK panels (two-pass render order)
- Modal Panel buttons (Cancel, Confirm) detect hover and dispatch clicks normally
- Confirm/Cancel callbacks fire; consumer state mutates; dialog dismisses
- Escape closes underlying screen normally (v1 acceptable)

Test panel registered at `LEFT_ALIGN_TOP` to verify dim coverage of non-modal MK panels. Confirmed dimmed when modal up.

---

## Phase 14d-2 entry conditions

- Modal mechanism shipped + smoke-verified
- Dialog builders shipped (ConfirmDialog + AlertDialog)
- All existing element types untouched
- No regressions across V2–V7 scenarios or 9 other canonical contracts
- Design doc reflects shipped reality
- `/mkverify dialog` smoke command + test panel kept as dev tooling for regression-checks

Phase 14d-2 (per `PHASES.md` §14d sequencing — scroll container next per implementer brief): scroll container is the rendering primitive that introduces clipping. Likely unlocks Dropdown internals.

---

## Diff summary

~17 files modified, 8 new (4 mixins + 2 dialog builders + DIALOGS.md + 14d-1 REPORT.md), 0 deleted. New library surface ~950 LOC (substantial javadoc covering the architectural progression for future readers; ~400 LOC of actual logic).

**Phase 14d-1 closed.**
