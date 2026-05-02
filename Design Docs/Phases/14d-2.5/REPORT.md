# Phase 14d-2.5 — Close-out REPORT (M9 Panel Opacity)

**Status: complete.** Click-through prohibition shipped as a library-wide mechanism: `Panel.opaque(boolean)` flag with default `true`, modal reframed as composition over three independent flags (`opaque + dimsBehind + tracksAsModal`), input-handler mixins universalized to consult the unified opacity registry. Lambda-path adapters get `.activeOn` / `.deactivate` lifecycle for opacity participation. One round + three smoke fold-inline findings; no round 2 needed.

---

## Executive summary

Phase 14d-2.5's deliverable was the click-through prohibition principle Trevor surfaced during 14d-2 smoke: *"You should NEVER be able to click through behind a panel to something behind. No click through, no tooltips showing behind, no mouse icon change to clickable, nothing. Everything behind the panel is completely inert."*

This is a library-wide invariant: visible panels are interaction-opaque over their bounds. M9 generalizes 14d-1's modal mechanism (which delivered opacity globally for modal panels only) to apply to every visible panel by default. Modal becomes a composition over three independent flags rather than a bundled single primitive.

**Architectural progression:**

- **Round 1 (single round)** — design-doc-first per established pattern. Three load-bearing decisions confirmed in entry-brief exchange: (a) modal reframe via composition with `modal()` sugar over three independent flags; (b) lambda-path registration via `.activeOn` / `.deactivate` two-call lifecycle; (c) suppression scope asymmetry (pointer-driven local for non-modal; cursor lock + keyboard global-modal-only). One secondary load-bearing decision verdicted at round 1 (z-order: registration order = z; defer explicit z API on evidence). Five additional verdicts (lambda failure-mode, undefined `opaque(false)+tracksAsModal(true)`, V14 expanded scope, V5 deferred audit, per-element opacity primitive-gap filing) all signed off. One nit (V9 row in §6 audit table) folded inline.

- **Implementation + smoke** — single commit per established pattern. Three smoke findings surfaced and resolved inline, each refining the design without contradicting round-1 verdicts:

  1. **Scroll release passthrough** (smoke #1) — initial M9 ate every press-and-release inside opaque panels, breaking ScrollContainer drag-end. First fix: pass releases through to Fabric's `allowMouseRelease` hook. Drag-end worked again, but introduced #3.

  2. **Hover/tooltip global-modal scope** (smoke #2) — §4.7's first framing was incomplete. Pointer-driven suppressions only checked bounds-local opacity; for modal-tracking, hover/tooltip on items outside the modal's bounds leaked. Fix: pointer-driven suppressions OR both gates — `hasAnyVisibleModalTracking() || hasAnyVisibleOpaquePanelAt(...)`. §4.7 reframed: each panel claims a *scope* (opaque = bounds; tracksAsModal = whole screen); pointer-driven suppressions honor either scope; window-state suppressions can only honor the global scope.

  3. **Symmetric press/release at mixin level** (smoke #3) — vanilla `CreativeModeInventoryScreen.mouseReleased` selects tabs (not `mouseClicked`). The #1 fix passed releases through, letting vanilla process tab selection while modal up. Final fix: symmetric press/release handling at the mixin level. New helper `dispatchOpaqueRelease` parallels `dispatchOpaqueClick`: when press would be eaten (opaque-at-cursor OR modal-tracking visible), eat the release too AND manually dispatch `mouseReleased` to opaque adapters' elements (since canceling `onButton` prevents Fabric's hook from firing). Both #1 (drag-end) and modal tab-blocking hold simultaneously.

**Calibration heuristic surfaced:** *"When the input-handler mixin pre-empts vanilla's chain, the mixin owns the dispatch responsibility for whatever vanilla would have routed."* Same shape as 14d-1's round-3 finding — partial pre-emption (eat input but rely on downstream for dispatch) is structurally fragile.

What shipped:

- **`Panel.opaque(boolean)`** + getter `isOpaque()` — interaction opacity, default `true`. Renames + default-flips 14d-1's `cancelsUnhandledClicks(boolean)`.
- **`Panel.dimsBehind(boolean)`** + getter — visual dim layer when panel visible, default `false`.
- **`Panel.tracksAsModal(boolean)`** + getter — global modal-tracking (cursor lock, keyboard suppression, outside-bounds click eating), default `false`.
- **`Panel.modal()`** — builder convenience setting all three flags to `true`. Canonical real-modal pattern.
- **Lambda-path lifecycle**: `ScreenPanelAdapter.activeOn(Screen, Supplier<ScreenBounds>)` + `deactivate(Screen)`. Lambda panels register their bounds-supplier so the unified registry can dispatch opacity decisions for them.
- **Unified opacity registry** — `ScreenPanelRegistry` tracks lambda-active entries alongside region-based matches. `findOpaquePanelAt`, `hasAnyVisibleOpaquePanelAt`, `hasAnyVisibleOpaquePanelAtCursor`, `hasAnyVisibleModalTracking`, `hasVisibleDimsBehindOnScreen` helpers consulted by the four mixins.
- **Mixin generalization** — `MenuKitModalMouseHandlerMixin` (button + scroll) consults `dispatchOpaqueClick` / `dispatchOpaqueRelease` / `dispatchOpaqueScroll`. `MenuKitModalKeyboardHandlerMixin` gates on `hasAnyVisibleModalTracking()`. `MenuKitModalHoverMixin` ORs both gates (modal-tracking global + opaque-at-cursor local). `MenuKitTooltipSuppressMixin` same OR-shape.
- **Cursor lock** — `Window.setAllowCursorChanges(false)` per-tick, gated on `hasAnyVisibleModalTracking()`. Preserved from 14d-1.
- **Two-pass dim render order** — pass 1 = non-dim panels, pass 2 = dim if any `dimsBehind(true)` visible, pass 3 = dim panels. Generalized from 14d-1's modal-only dim.
- **V14 + V15 `/mkverify` probes** — V14 opacity dispatch under multi-panel state (~10–15 cases); V15 lambda lifecycle (~3–5 cases). Aggregator now reports 15 contracts.
- **V10 + V13 mechanical updates** — semantic renames; same coverage. V11 untouched (server-thread scope; doesn't reference flag names).
- **`/mkverify opacity`** smoke command — region-based panel at LEFT_ALIGN_TOP; verifies clicks within bounds don't pick up slots underneath.
- **Design doc** at `Design Docs/Mechanisms/M9_PANEL_OPACITY.md` — ~700 lines; full round-1 design + smoke fold-inline findings + appendices.
- **Two `DEFERRED.md` resolutions** + one new entry — click-through prohibition resolved (was top architectural finding); lambda-adapter modal-aware extension resolved; per-element opacity filed as primitive-gap candidate per Q6 verdict.
- **Documentation updates** — `PHASES.md` current marker advanced 14d-2 → 14d-2.5.

---

## Architecture decisions

### Modal as composition (not single bundled flag)

`Panel.modal()` is syntactic sugar over `opaque(true).dimsBehind(true).tracksAsModal(true)`. Three independent flags exposed because each answers a separate question:

| Flag | Question |
|---|---|
| `opaque` | Does input behind this panel reach what's behind? |
| `dimsBehind` | Should the screen dim visually when this panel is visible? |
| `tracksAsModal` | Should keyboard be suppressed; cursor lock globally? |

A real modal answers yes to all three. A future popup answers yes to opacity, no to dim, no to modal-tracking. Composition is honest about what "modal" means structurally; future primitives (popovers, dropdowns, hover cards) get the independent flag control they need.

`opaque(false) + tracksAsModal(true)` is documented as undefined for v1 (logically nonsensical — clicks pass through but Escape closes + cursor locks). Not rejected at builder time; consumer judgment. Fold-on-evidence to reject if misuse surfaces.

### Lambda-path registration via `.activeOn` / `.deactivate`

Lambda-based adapters register bounds-suppliers with the unified registry so they participate in opacity dispatch automatically. Two-call lifecycle (consumer's mixin calls `.activeOn(this, () -> ScreenBounds(...))` in `init()`; `.deactivate(this)` in `removed()`).

The escape-hatch property of the lambda path — consumer owns rendering and dispatch in their own mixin — is preserved. Library only learns about bounds for opacity purposes. The `cancelsUnhandledClicks → opaque` default-flip means lambda consumers who don't update their mixins to call `.activeOn(...)` will see click-through bugs for their panels (the M9 invariant doesn't apply to un-registered lambda adapters). Library does NOT enforce; doc warning + DEFERRED.md filing only. Library-not-platform.

In-tree probes are all region-based — zero affected. Out-of-tree consumer mods (inventory-plus, shulker-palette, etc.) need migration; consumer pace per library-not-platform.

### Suppression scope follows panel scope

§4.7's reframed framing (corrected post-smoke): each panel claims a *scope*; the kind of input determines whether the scope is global or bounds-local.

- **Opaque panel scope** = the panel's bounding box (local).
- **`tracksAsModal` panel scope** = the whole screen (global).

Different input modalities suppress within these scopes differently:

| Suppression | Driver | When `tracksAsModal` visible | When opaque-only |
|---|---|---|---|
| Slot hover | Pointer position | Suppress GLOBALLY | Suppress LOCALLY (cursor inside bounds) |
| Tooltip suppression | Pointer position | Suppress GLOBALLY | Suppress LOCALLY (cursor inside bounds) |
| Cursor change | Window state | Suppress (lock cursor) | Don't lock |
| Keyboard eating | Keystroke event | Suppress (eat keys) | Don't eat |

Pointer-driven suppressions OR both gates. Window-state suppressions can only honor the global scope (cursor lock and keyboard can't localize to bounds structurally). The asymmetry is principled; the original framing was incomplete.

### Symmetric press/release at the input-handler level (smoke #3 fold-inline)

When the press would be eaten by M9's mechanism, the release is also eaten — by the same opacity gate. The new `dispatchOpaqueRelease` helper mirrors `dispatchOpaqueClick` and additionally dispatches `mouseReleased` to all visible opaque adapters' elements (since canceling `onButton` at the mixin level prevents Fabric's `allowMouseRelease` hook from firing).

This preserves both:
- ScrollContainer drag-end inside an opaque panel (release dispatches to scroll panel's adapter at mixin level).
- Modal tab-blocking (release eaten before vanilla `CreativeModeInventoryScreen.mouseReleased` can run; tabs don't switch while modal up).

Pure non-modal non-opaque releases still pass through to vanilla → Fabric's existing path.

### Vanilla primitives drive implementation

Per the calibration heuristic from 14d-1 (*find vanilla's existing primitive before inventing one*):

- **Cursor lock**: `Window.setAllowCursorChanges(boolean)` — vanilla's existing global flag.
- **Keystroke handling**: GLFW key 256 (Escape) allowlist — preserved from 14d-1.
- **Dim layer**: `GuiGraphics.fill(0, 0, w, h, 0xC0000000)` — same primitive 14d-1 used.
- **Mixin layer**: `MouseHandler.onButton` HEAD-cancellable — same dispatch root 14d-1's round 3 settled on.

---

## What shipped

### Library — modified

| File | Change |
|---|---|
| `core/Panel.java` | Renamed `cancelsUnhandledClicks` → `opaque`; default flipped `false` → `true`; added `dimsBehind` + `tracksAsModal` flags + `modal()` sugar |
| `core/dialog/ConfirmDialog.java` | `.cancelsUnhandledClicks(true)` → `.modal()` (mechanical) |
| `core/dialog/AlertDialog.java` | Same mechanical rename |
| `inject/ScreenPanelAdapter.java` | Added `.activeOn(Screen, Supplier<ScreenBounds>)` + `.deactivate(Screen)` for lambda-path opacity registration; added `getOriginForScreen(...)` for any-Screen origin query |
| `inject/ScreenPanelRegistry.java` | Added `LAMBDA_ACTIVE` per-screen registry; added `findOpaquePanelAt`, `hasAnyVisibleOpaquePanelAt(coords)`, `hasAnyVisibleOpaquePanelAtCursor()`, `hasAnyVisibleModalTracking()`, `hasVisibleModalTrackingOnScreen`, `hasVisibleDimsBehindOnScreen`, `dispatchOpaqueClick`, `dispatchOpaqueRelease`, `dispatchOpaqueScroll`, `shouldEatOpaqueDispatch`. Two-pass render order generalized from modal-only to dim-only gate. |
| `mixin/MenuKitModalMouseHandlerMixin.java` | `onButton` HEAD: symmetric press/release handling via `dispatchOpaqueClick` / `dispatchOpaqueRelease`. `onScroll` HEAD: `dispatchOpaqueScroll` |
| `mixin/MenuKitModalKeyboardHandlerMixin.java` | Gate renamed `hasAnyVisibleModal` → `hasAnyVisibleModalTracking` |
| `mixin/MenuKitModalHoverMixin.java` | Gate ORs `hasAnyVisibleModalTracking() \|\| hasAnyVisibleOpaquePanelAt(x, y)` |
| `mixin/MenuKitTooltipSuppressMixin.java` | Gate ORs `hasAnyVisibleModalTracking() \|\| hasAnyVisibleOpaquePanelAtCursor()` |
| `MenuKitClient.java` | Cursor lock callback gate renamed; added `wireOpacitySmoke()` registration |
| `verification/ContractVerification.java` | V10 + V13 mechanical updates; new V14 (opacity dispatch, multi-panel state) + V15 (lambda lifecycle) probes; `/mkverify opacity` smoke command + `wireOpacitySmoke()` panel registration |

### Library — new

| File | Role |
|---|---|
| `Design Docs/Mechanisms/M9_PANEL_OPACITY.md` | ~700 lines — full round-1 design + smoke fold-inline findings + appendices |
| `Design Docs/Phases/14d-2.5/REPORT.md` | this file |

### V14 + V15 verification

| Probe | Cases | Result |
|---|---|---|
| **14 M14 opacity dispatch** | 14 | Multi-panel state coverage: default flag values, modal() sugar, undefined-combo, shouldEatOpaqueDispatch truth table, null guards |
| **15 M15 lambda lifecycle** | 5 | Lambda construction, activeOn null guards, deactivate idempotent, region-based rejection of activeOn |

`/mkverify` aggregator now reports 15 contracts.

### DEFERRED.md updates

- ~~Click-through prohibition~~ → RESOLVED in 14d-2.5 (shipped as M9).
- ~~Lambda-adapter modal-aware extension~~ → RESOLVED in 14d-2.5 (`.activeOn` / `.deactivate` lifecycle).
- **Per-element opacity** (new entry per Q6 verdict) — primitive-gap candidate for first concrete consumer (e.g., highlight reticle with click-through holes). Architectural shape questions filed for trigger time.
- **Lambda consumer mods need `.activeOn` migration** (new entry) — explicit notice that consumer mods using lambda-path `ScreenPanelAdapter` must update mixins to participate in opacity dispatch. In-tree zero affected; out-of-tree consumer pace.

---

## What didn't ship / deferred

- **Per-element opacity** — out of scope for v1; surface as primitive gap on first concrete consumer. See DEFERRED.md.
- **Per-region z explicit API** — registration-order = z is sufficient for v1 use cases. Defer-on-evidence per Q1 verdict.
- **`opaque(false) + tracksAsModal(true)` rejection at builder time** — documented undefined for v1; fold-on-evidence to reject if misuse surfaces.
- **MenuKit-native screen dispatch** — same as 14d-1 (deferred until first concrete consumer in Phase 15).
- **Lambda registration enforcement** — no library-side detection of un-registered lambda adapters; explicit-warning-no-enforcement per Q2 verdict (library-not-platform).

---

## Process notes

**One round + three smoke fold-inline findings.** Round 1 closed cleanly with six verdicts + one nit (V9 row in audit table). Implementation surfaced three findings during smoke, all resolved inline:

1. Scroll release passthrough (initial fix broke modal tab-blocking later).
2. Hover/tooltip global-modal scope (§4.7 framing was incomplete).
3. Symmetric press/release at mixin level (final shape; both #1 and modal tab-blocking hold simultaneously).

The third finding is the most architecturally interesting — it's the same shape as 14d-1's round-3 calibration heuristic ("compounding mixins at the same layer signals wrong layer"). M9's variant: when you eat input at a layer, you also dispatch at that layer; partial pre-emption is structurally fragile. Saved as a calibration heuristic.

**Calibration heuristics applied successfully:**

- *"Find the vanilla primitive that already centralizes the behavior"* — applied for cursor lock (`setAllowCursorChanges`), dim layer (`graphics.fill`), input-handler layer (`MouseHandler.onButton`).
- *"When the input-handler mixin pre-empts vanilla's chain, the mixin owns the dispatch responsibility for whatever vanilla would have routed"* — surfaced from smoke #3; saved.

**Round count discipline:** the round-1 advisor verdict said "1 round + inline target." Three fold-inline findings honored that target — no findings warranted a new architectural verdict; each was a refinement within the round-1 design space. Smoke verification was load-bearing, design discipline held.

---

## Verification

### Automated (`/mkverify`)

15 library contracts. All PASS expected:

| Contract | Result |
|---|---|
| 1-9 | (unchanged from 14d-2 close) |
| **10 M10 opaque-dispatch decision** | 14/14 ✓ (M9 generalization of 14d-1 modal click-eat) |
| **11 M11 dialog builder** | 11/11 ✓ |
| **12 M12 ScrollContainer math** | 11/11 ✓ |
| **13 M13 opaque-scroll dispatch** | 3/3 ✓ (M9 generalization of 14d-2 modal-scroll) |
| **14 M14 opacity dispatch** (new) | 14/14 ✓ |
| **15 M15 lambda lifecycle** (new) | 5/5 ✓ |

### Visual smoke (dev-client)

Comprehensive smoke through the three smoke commands + creative inventory:

- `/mkverify dialog`: modal dialog correctly blocks all underlying interaction (clicks, hover, tooltips, cursor, tab clicks/release, slot hover, item tooltips). Behind-dialog inert.
- `/mkverify scroll`: ScrollContainer scroll + drag-end work correctly. Drag-end inside panel: dispatched at mixin level. Drag-end outside panel: dispatched via Fabric's existing path.
- `/mkverify opacity`: region-based panel at LEFT_ALIGN_TOP. Click within panel bounds doesn't pick up slots underneath. Verifies the click-through prohibition principle at the smoke layer.

Cross-mod regression: full monorepo build succeeds (menukit + 6 consumer modules). Existing region-based panels in consumer mods automatically pick up new opacity default. Lambda-based panels in consumer mods continue to work as before; opacity participation requires migration per DEFERRED.md.

---

## Phase 14d-3 entry conditions

- M9 panel-opacity mechanism shipped and smoke-verified.
- Click-through prohibition principle delivered library-wide.
- Modal mechanism reframed without losing 14d-1 capabilities.
- Lambda-path opacity participation enabled via `.activeOn` / `.deactivate`.
- All 15 `/mkverify` contracts passing.
- No regressions across V2–V7 validator scenarios or 13 prior library contracts.
- Design doc + phase report committed.
- Per-element opacity primitive-gap filed in DEFERRED.md for trigger-time evidence.

Phase 14d-3 (per `PHASES.md` §14d sequencing): text input. Designs against the locked opacity model; opacity-aware concerns (active text field claiming click region, focus indicators, etc.) get a clean foundation rather than retrofitting.

---

## Diff summary

~12 files modified, 2 new (M9 doc + REPORT). Approximate net: ~+1100 / ~-100 LOC. New library surface: ~600 LOC across Panel.java rename + new flags + sugar (~50), ScreenPanelAdapter.java lambda lifecycle (~60), ScreenPanelRegistry.java unified registry + helpers (~250), mixin updates (~50), ContractVerification.java V14+V15 + smoke wire-up (~180). Plus ~700 LOC design doc + ~250 LOC report.

**Phase 14d-2.5 closed.**
