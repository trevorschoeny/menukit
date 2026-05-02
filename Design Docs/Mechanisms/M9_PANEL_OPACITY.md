# M9 — Panel Opacity Mechanism

**Phase 14d-2.5 mechanism — universal click-through prohibition for visible panels.**

**Status: draft — round 1, awaiting advisor review.**

**Load-bearing framing (Trevor's principle, Phase 14d-2 smoke):**

> *You should NEVER be able to click through behind a panel to something behind. No click through, no tooltips showing behind, no mouse icon change to clickable, nothing. Everything behind the panel is completely inert.*

This is the architectural commitment. Every design decision below checks against it. If a proposed shape leaves any path by which input behind a visible panel reaches the underlying screen — clicks falling through, tooltips bleeding from below, slot hover firing, cursor changing to clickable — the principle is broken and the design needs to reshape.

The principle is a generalization of 14d-1's modal mechanism. Modal panels (`cancelsUnhandledClicks(true)`) deliver opacity globally — clicks outside the modal are eaten, dim layer covers everything behind, tooltips/cursor/hover suppressed for the whole screen. Non-modal panels currently allow click-through: an MK panel covering vanilla slots eats the click via the element layer, but Fabric's `allowMouseClick` returns `true` (allow vanilla) — and the slot underneath sees the click anyway. Hover, tooltips, cursor changes for vanilla widgets behind also fire.

M9 makes opacity universal: every visible panel is interaction-opaque over its bounds by default. Modal becomes a composition over three independent flags (`opaque` + `dimsBehind` + `tracksAsModal`). The mechanism that 14d-1 built for modals extends to all opaque panels, with one principled asymmetry: pointer-driven suppressions (hover, tooltip) localize to the panel under the cursor; the global cursor lock stays modal-only because cursor is a window-edge state, not a pointer-driven one.

---

## 1. Purpose

**The bug 14d-2 surfaced** (filed in `DEFERRED.md`):

A consumer mod opens an inventory screen with an MK panel registered at `MenuRegion.RIGHT_ALIGN_TOP`. The panel covers the top-right slot of the player's inventory. The user clicks on the MK panel's empty space (not on any element). Today:

1. `ScreenPanelAdapter.mouseClicked(...)` fires — no element consumed → returns `false`.
2. Fabric's `allowMouseClick` hook returns `true` → vanilla proceeds with normal dispatch.
3. Vanilla's `Screen.mouseClicked` reaches the slot underneath the panel → slot pickup fires.
4. Player loses the item they thought they were just clicking inertly on.

Same shape for tooltips (vanilla queues item tooltip on hover-through), cursor changes (`checkTabHovering` flips cursor to clickable for tabs behind a non-modal panel), and slot-hover (`getHoveredSlot` returns the slot underneath even though a panel sits on top).

**What M9 fixes:** all four input pathways (click, hover, tooltip, cursor) respect the panel as opaque over its bounds. Empty space within an opaque panel's bounds eats input; vanilla underneath never sees it.

**What M9 replaces:**

- `Panel.cancelsUnhandledClicks(boolean)` (14d-1 flag) — renamed to `Panel.opaque(boolean)` and default-flipped from `false` to `true`.
- The four 14d-1 modal mixins — kept, but consult a generalized "any visible opaque panel covers this point" condition instead of "any visible modal."
- Two-pass render order (non-modal → dim → modal) — kept; dim layer becomes a separate `dimsBehind` flag.
- `hasAnyVisibleModal()` / `hasVisibleModalOnScreen(...)` — kept for the modal-cursor-lock case; new `hasOpaquePanelAt(x, y)` for the bounds-driven cases.

The 14d-1 mechanism is structurally correct; M9 generalizes its gate condition without inventing new layers.

---

## 2. Consumer evidence

**Existing — modal dialogs (14d-1).** ConfirmDialog and AlertDialog. Already opaque (via `cancelsUnhandledClicks(true)`); the rename and default flip make their existing behavior the new default for everyone else, not a new capability for them. Mechanical migration.

**Existing — non-modal MK panels covering vanilla slots.** The bug case above. Consumer mods covered:

- IP equipment panel (Phase 11 era) — covers slot grid edges via lambda-anchor.
- Sandboxes pause-menu buttons — addRenderableWidget, vanilla widget; not affected (vanilla widgets already eat clicks correctly).
- Shulker-palette toggle above shulker grid — lambda-anchor, region close to slots.
- IP / shulker-palette future panels — any region-anchored decoration that lands over slots will need the same opacity.

**Future — half-opaque dropdowns / popovers (deferred but enabled).** A dropdown popup that opaque-blocks clicks behind it (so clicking outside the popup doesn't accidentally activate buttons behind) but doesn't dim the whole screen and doesn't track as modal (Escape doesn't close it; cursor doesn't lock). Composition: `opaque(true) + dimsBehind(false) + tracksAsModal(false)`. This is exactly the use case a single-flag `modal()` couldn't deliver.

**Future — hover cards / tooltip overlays (deferred but enabled).** A panel that displays on hover, blocks clicks while visible, doesn't dim. Same shape as dropdown.

**Future — fully transparent overlays (escape hatch).** Rare but real: a custom decoration that paints over an inventory but is visually transparent AND interaction-transparent — slots underneath should still receive clicks. `opaque(false)` is the explicit opt-out.

**Rule of Three check:**

- `opaque(true)` default: 3+ concrete cases (modal dialogs already; non-modal panels covering slots; future popups). ✓
- `opaque(false)` escape hatch: 1 concrete case (hypothetical transparent overlays). Deferred-on-evidence is the alternative — but the cost of including it is one method overload, and the cost of NOT including it is "no escape hatch ever," which violates the library-not-platform principle. Ship with the escape hatch; `Rule of Three` doesn't apply to escape hatches. ✓
- `dimsBehind` independent flag: 2 concrete cases (modal yes; popup no). Provisionally ship; if the popup case never materializes, this flag stays useful only inside `modal()`. ✓
- `tracksAsModal` independent flag: 2 concrete cases (modal yes; popup no). Same logic. ✓

---

## 3. Scope

### In scope

- New `Panel.opaque(boolean)` flag with default `true`. Replaces `cancelsUnhandledClicks` via rename + default flip.
- New `Panel.dimsBehind(boolean)` flag with default `false`. Today's dim layer behavior shifts from "if any modal visible" to "if any panel with `dimsBehind(true)` visible."
- New `Panel.tracksAsModal(boolean)` flag with default `false`. Today's modal-tracking behavior (Escape closes, global cursor lock, tooltip/hover suppression) shifts from "if any `cancelsUnhandledClicks` panel visible" to "if any `tracksAsModal` panel visible."
- `Panel.modal()` builder convenience: sugar for `opaque(true).dimsBehind(true).tracksAsModal(true)`. Documented as the canonical "real modal" form; the underlying composition is the architectural primitive.
- `ScreenPanelAdapter` lambda-path lifecycle: `.activeOn(Screen, Supplier<ScreenBounds>)` and `.deactivate(Screen)`. Lambda panels register their bounds-supplier so the unified registry can dispatch opacity decisions for them.
- `ScreenPanelRegistry` unification: tracks lambda-path active screens alongside region-based matches. New `hasOpaquePanelAt(Screen, double, double)` query consulted by the four mixins for bounds-driven suppressions.
- The four 14d-1 modal mixins retargeted onto generalized gate conditions:
  - `MenuKitModalMouseHandlerMixin` (button + scroll) — eats input outside opaque panels OR dispatches to opaque panels. Cursor inside opaque panel's bounds → dispatch to that panel; cursor outside any opaque panel + something visible needs eating only if `tracksAsModal` is set on a visible panel (preserves modal blocking; non-modal opaque just dispatches at its bounds without eating outside its bounds).
  - `MenuKitModalKeyboardHandlerMixin` — keyboard suppression stays gated on `tracksAsModal` (keyboard isn't pointer-driven; can't localize to bounds). Renamed to drop "Modal" — just generic input-routing concern under the new vocabulary; gate remains modal-tracking.
  - `MenuKitModalHoverMixin` (`AbstractContainerScreen.getHoveredSlot`) — returns null when cursor inside any visible opaque panel, regardless of modal-tracking. Localized.
  - `MenuKitTooltipSuppressMixin` (`GuiGraphics.setTooltipForNextFrameInternal`) — suppresses when cursor inside any visible opaque panel. Localized.
- Cursor lock (`Window.setAllowCursorChanges`) stays gated on `hasAnyVisibleModalTracking()` — preserves 14d-1 modal cursor behavior; non-modal panels do NOT lock cursor.
- Dim layer (the 14d-1 two-pass render order in `renderMatchingPanels`) — render passes generalize: pass 1 = non-dim panels; pass 2 = dim if any `dimsBehind(true)` panel visible; pass 3 = dim panels.
- V14 + V15 `/mkverify` probes: V14 opacity dispatch (`hasOpaquePanelAt` semantics); V15 lambda lifecycle (`.activeOn` / `.deactivate`).
- Cross-context applicability per CONTEXTS.md (§5).
- V0–V12 probe audit (§6).

### Out of scope (deferred, with discipline)

- **Per-panel z explicit API.** Z-order is determined by registration order for non-`tracksAsModal` panels and "all modal-tracking panels above all non-modal-tracking panels" otherwise. Same shape as today. If multiple non-modal panels overlap and a consumer needs explicit control, defer to evidence — not a v1 concern. (See §4.6.)
- **Per-element opacity.** Opacity is a Panel property; elements within an opaque panel can't independently make themselves transparent. If a consumer wants a "hole" in an otherwise-opaque panel for click-through to specific slots underneath, that's a primitive gap to surface, not a v1 capability.
- **Region-vs-bounds conflict resolution.** When two non-modal opaque panels overlap (e.g., one consumer's panel registered to LEFT_ALIGN_TOP, another to TOP_ALIGN_LEFT, with overlapping coords), the higher-z (later-registered) wins dispatch. No mediation; library doesn't pick winners across consumers. Same shape as today.
- **Animated opacity transitions.** THESIS scope ceiling (animation framework deferred). Opacity flips synchronously with visibility.
- **Half-opaque interaction (e.g., 50% probability of click pass-through).** Not a real use case; mentioned only to confirm we're shipping boolean opacity, not graded.

---

## 4. Design decisions

Each decision is a draft position. §4.3 (modal reframe), §4.4 (lambda registration), and §4.7 (suppression scope asymmetry) are load-bearing — these are the three the advisor flagged in the verdict. §4.6 (z-order) is a secondary load-bearing decision. The rest are mechanical or already signed off in the entry-brief exchange.

### 4.1 The principle — bounding-box opacity, not visual opacity

The interaction footprint of an opaque panel is its bounding box, regardless of `PanelStyle`. Empty space within bounds eats input; transparent visual styles (`PanelStyle.NONE`) don't open the panel up to click-through.

**Justification:**

1. Matches Trevor's literal framing — "everything behind the panel is completely inert" treats the panel as its bounds, not its rendered pixels.
2. Simpler implementation — single bounding-box test in the unified registry; no per-pixel render-state inspection.
3. Predictable for consumers — opacity is a property of the panel's footprint, knowable without tracking PanelStyle implications.
4. Composability — a consumer wanting visual-driven opacity (transparent style → click-through) opts out via `.opaque(false)`. The escape hatch exists; the default is the principled invariant.

**Style/opacity matrix:**

| `PanelStyle` | `opaque(?)` | Result |
|---|---|---|
| `RAISED` (or any visible variant) | `true` (default) | Visible AND opaque — canonical |
| `RAISED` | `false` | Visible but transparent to input — unusual; ships as escape hatch |
| `NONE` | `true` (default) | Invisible but opaque — "click blocker" pattern |
| `NONE` | `false` | Invisible AND transparent — purely-decorative overlay; rare |

The third row (invisible-but-opaque) is the "click blocker" pattern. The fourth row is the explicit transparent overlay. Both have legitimate use cases; both require explicit consumer choice.

### 4.2 The flag — `Panel.opaque(boolean)`, default true

```java
public Panel opaque(boolean isOpaque) {
    this.opaque = isOpaque;
    return this;
}

public boolean isOpaque() {
    return opaque;
}
```

Default is `true`. Constructed Panels are opaque-by-default; consumers explicitly opt out for transparent overlays.

**Naming.** `opaque` rather than `blocksInput` or `cancelsUnhandledClicks` because:

- `opaque` is a single word matching common UI vocabulary (CSS `opacity`, etc.).
- It's reusable as a Panel property with consistent meaning across uses (interaction opacity, can extend to visual if a future need arises).
- `cancelsUnhandledClicks` named the mechanism, not the property; the new vocabulary names the property and lets the mechanism follow.

**Builder placement.** Top-level Panel method (not buried in builder/style). Same level as `setVisible`, `showWhen`, `cancelsUnhandledClicks` (existed there). Consumers reading the Panel API see opacity as a first-class property.

### 4.3 Modal reframe — composition over three independent flags

**Decision: composition.** `Panel.modal()` becomes builder convenience setting:

```java
public Panel modal() {
    return opaque(true).dimsBehind(true).tracksAsModal(true);
}
```

Three independent flags exposed:

```java
public Panel opaque(boolean);          // interaction opacity (this section's main flag)
public Panel dimsBehind(boolean);      // visual dim layer behind panel when visible
public Panel tracksAsModal(boolean);   // Escape closes; global cursor lock; keyboard suppression
```

The `modal()` sugar ships alongside as the canonical form for real modals; consumers don't memorize the trio:

```java
// Canonical real-modal pattern — sugar
Panel dialogPanel = ConfirmDialog.builder(...).build()
    .modal();

// Equivalent expanded form (for future variants)
Panel dialogPanel = ConfirmDialog.builder(...).build()
    .opaque(true)
    .dimsBehind(true)
    .tracksAsModal(true);

// Half-opaque dropdown (future use case)
Panel dropdown = createDropdown()
    .opaque(true)
    .dimsBehind(false)
    .tracksAsModal(false);
```

**Justification:** each flag answers a separate question:

| Flag | Question |
|---|---|
| `opaque` | Does input behind this panel reach what's behind? |
| `dimsBehind` | Should the screen dim visually when this panel is visible? |
| `tracksAsModal` | Should keyboard be suppressed; Escape close; cursor lock globally? |

A real modal answers yes to all three. A dropdown/popup answers yes to opacity, no to dim, no to modal-tracking. A transparent overlay answers no to all. A click-blocker (invisible opaque panel) answers yes to opacity, no to dim, no to modal-tracking. Single-flag `modal()` couldn't capture these distinctions; composition does.

**Edge case — `opaque(false) + tracksAsModal(true)`:**

This combination is logically nonsensical: a transparent panel that closes on Escape and locks cursor globally. Consumers constructing this almost certainly have a bug.

**Decision: documented as undefined for v1.** Don't reject at builder time; consumer judgment. If the misuse surfaces in practice, fold-on-evidence to add `IllegalStateException` at construction. The doc addresses this explicitly:

> **Undefined combinations.** `opaque(false) + tracksAsModal(true)` is undefined for v1 — clicks pass through but Escape closes the panel. Consumers constructing this almost certainly have a bug; the library does not currently reject the combination but may in a future phase.

**Why composition is also worth it for the reframe:** the 14d-1 modal mechanism is a single primitive (`cancelsUnhandledClicks`) that bundled three concerns. Today's behavior — modal eats clicks AND dims AND tracks-as-modal — is correct for dialogs, but the *bundling* limited the primitive. Composition factors the bundle without changing dialog behavior; future primitives get the independent flags they need.

**`modal()` as the canonical surface.** Naming the sugar is a soft commitment to the bundled-as-default modal pattern. Consumers writing dialogs see `.modal()` and know it's the right call without thinking about the underlying composition. Only consumers building non-canonical panels (popovers, dropdowns) reach for the independent flags. This matches the library-not-platform discipline: defaults live with consumers (who pick `modal()` or build their own composition); library doesn't decide what "modal" means for every consumer.

### 4.4 Registry unification — lambda registration via `.activeOn` / `.deactivate`

**Decision: lambda-path adapters register their active-screen + bounds-supplier with the unified registry.**

```java
public ScreenPanelAdapter activeOn(Screen screen, Supplier<ScreenBounds> boundsSupplier) {
    // Adds (screen, this, boundsSupplier) to the unified opacity registry.
    // Called from consumer's mixin during init() (TAIL).
}

public ScreenPanelAdapter deactivate(Screen screen) {
    // Removes (screen, this) from the unified opacity registry.
    // Called from consumer's mixin during removed() (TAIL).
}
```

**Consumer side (mixin path):**

```java
@Mixin(SomeVanillaScreen.class)
public abstract class SomeMixin extends Screen {
    @Unique
    private final ScreenPanelAdapter myMod$adapter =
        new ScreenPanelAdapter(MyMod.PANEL,
                bounds -> new ScreenOrigin(bounds.imageWidth() - 100, 4));

    @Inject(method = "init", at = @At("TAIL"))
    private void myMod$register(CallbackInfo ci) {
        myMod$adapter.activeOn(this,
            () -> new ScreenBounds(0, 0, this.width, this.height));
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void myMod$unregister(CallbackInfo ci) {
        myMod$adapter.deactivate(this);
    }

    // Consumer's render + click mixins remain — lambda path's escape-hatch
    // property preserved. Library only learns about active-screen + bounds
    // supplier so the input-handler mixins know which point-in-bounds tests
    // to run on the consumer's panel.
}
```

**Justification:**

1. **Click-through prohibition is a library invariant** — making lambda exempt creates exactly the inconsistency the principle prohibits. Region-based panels eat correctly; lambda panels don't. Inconsistent default cancels the principle.
2. **Bounds-supplier is the minimum additional info needed.** The lambda path already takes a `ScreenOriginFn`; combined with `panel.getWidth/Height`, the registry can compute current bounds. The supplier provides the screen-relative anchor (`ScreenBounds`), the rest of the math runs against existing primitives.
3. **Two-call lifecycle is minimal API surface.** `activeOn` in init, `deactivate` in removed — same shape as `addRenderableWidget` + lifecycle removal in vanilla. No new mental model.
4. **Escape-hatch property preserved.** Consumer still owns rendering and dispatch in their mixin. Library only knows about bounds for opacity dispatch. The "consumer-owned full-control" property of the lambda path is intact for everything except the opacity invariant — which is library-wide and not negotiable.

**Failure mode: consumer forgets `deactivate`.** A consumer registers `activeOn` but never `deactivate`. The Screen instance references stay in the registry until the next register call replaces them. Since registrations are keyed on (screen, adapter), forgetting deactivate just leaves stale entries that refer to non-active screens — these never match `Minecraft.getInstance().screen`, so dispatch is harmless. Fold-on-evidence: if leak becomes a real concern, add WeakReference tracking on the screen key and a periodic cleanup pass. Not a v1 issue.

**Failure mode: consumer registers `activeOn` for a screen that's never opened.** No-op — registration entry sits in the map but is never consulted (the dispatch path queries by `Minecraft.getInstance().screen` and only matches the active screen). Harmless.

**Failure mode: consumer forgets `activeOn` entirely.** Lambda panel renders (the consumer's mixin handles render + click) but isn't in the opacity registry. Click-through bug for that panel — exactly the bug we're fixing. Library does NOT enforce `activeOn` registration (parallel to no-`onAny()` enforcement on lambda — lambda is the escape hatch). Doc note: "consumers using the lambda path must call `.activeOn(...)` from their mixin's `init()` to participate in opacity dispatch; without it, the panel will allow click-through (the M9 invariant doesn't apply)."

This is a deliberate choice: lambda is the escape hatch with the explicit warning. Forcing automatic enforcement (mixin into `Screen.init` to detect un-registered lambdas) violates library-not-platform.

**Per-Screen vs cross-Screen registration.** The registration is per-screen-instance: the same lambda adapter active on InventoryScreen has a different registration entry than the same adapter on a custom screen. Registration is screen-scoped, not adapter-scoped. This matches lifecycle (init fires per screen open).

### 4.5 Mechanism universalization — input-handler mixins consult unified registry

The 14d-1 modal mixins (mouse, keyboard, hover, tooltip) get their gate condition generalized. Same hook points, same HEAD-cancellable shape; different gate function.

**Mouse mixin (`MenuKitModalMouseHandlerMixin.onButton` + `onScroll`):**

```java
// 14d-1 (today):
//   if (hasAnyVisibleModal()) → eat or dispatchModalClick
//
// M9 (post-rename):
//   if (cursor inside any visible opaque panel):
//     dispatch click to that panel; eat (atomic)
//   else if (any visible tracksAsModal panel):
//     eat (modal-blocking outside its bounds)
//   else:
//     don't intervene; vanilla proceeds normally
```

The pure-decision helper splits into two:

```java
public static @Nullable ScreenPanelAdapter findOpaquePanelAt(Screen, double x, double y);
public static boolean hasAnyVisibleModalTracking(Screen);
```

A click inside any visible opaque panel dispatches to it (atomic dispatch + eat, same shape as 14d-1's `dispatchModalClick`). A click outside all opaque panels but with a `tracksAsModal` panel visible is eaten (preserves modal-blocking semantics). A click outside all opaque panels with no `tracksAsModal` is left alone (vanilla proceeds).

**Hover mixin (`MenuKitModalHoverMixin` on `AbstractContainerScreen.getHoveredSlot`):**

```java
// 14d-1: if (hasAnyVisibleModal()) → return null
// M9:    if (cursor inside any visible opaque panel) → return null
```

Bounds-localized — hovering over a slot underneath an opaque panel returns null. Hovering over a slot NOT under any opaque panel returns the slot normally. Even works with non-modal opaque panels covering only part of a slot grid.

**Tooltip mixin (`MenuKitTooltipSuppressMixin` on `GuiGraphics.setTooltipForNextFrameInternal`):**

```java
// 14d-1: if (hasAnyVisibleModal()) → cancel queueing
// M9:    if (cursor inside any visible opaque panel) → cancel queueing
```

Bounds-localized. Tooltips for items behind an opaque panel are suppressed; tooltips for items NOT under any opaque panel render normally.

**Keyboard mixin (`MenuKitModalKeyboardHandlerMixin.keyPress`):**

```java
// 14d-1: if (hasAnyVisibleModal() && key != GLFW_KEY_ESCAPE) → eat
// M9:    if (hasAnyVisibleModalTracking() && key != GLFW_KEY_ESCAPE) → eat
```

Stays gated on `tracksAsModal` — keyboard isn't pointer-driven, can't localize to bounds. Justified in §4.7.

**Cursor mixin (`Window.setAllowCursorChanges` per-tick sync):**

```java
// 14d-1: setAllowCursorChanges(!hasAnyVisibleModal())
// M9:    setAllowCursorChanges(!hasAnyVisibleModalTracking())
```

Stays gated on `tracksAsModal` for the global lock. Per-bounds cursor unlock isn't structurally available (cursor is a window-edge state, not pointer-driven). Justified in §4.7.

**Dim layer (`renderMatchingPanels` two-pass order):**

```java
// 14d-1: pass 2 dim if hasVisibleModalOnScreen(screen)
// M9:    pass 2 dim if any visible panel with dimsBehind(true) on screen
```

Dim is gated on the explicit `dimsBehind` flag. Modal panels (which set `modal()` → `dimsBehind(true)`) keep dimming. Non-modal opaque panels without `dimsBehind` don't dim. Future popups can opt in independently.

### 4.6 Z-order — registration order = z; modal-tracking panels above non-modal-tracking

**Today (14d-1):** Two-pass render order in `renderMatchingPanels`:

1. Pass 1: non-modal MenuContext adapters render in registration order.
2. Pass 2: dim if any modal visible.
3. Pass 3: modal MenuContext adapters render in registration order.

**M9 (no change in shape, generalize gate):**

1. Pass 1: panels without `dimsBehind`, in registration order.
2. Pass 2: dim if any panel with `dimsBehind(true)` visible.
3. Pass 3: panels with `dimsBehind(true)`, in registration order.

Combined with opacity dispatch: the opaque-panel-at-point query iterates in render order (or reverse: highest-z first wins) — same order, same answer. A click at coords inside both a non-dim panel AND a dim panel: the dim panel wins (higher z because it's drawn later).

**No new explicit z API in v1.** Two reasons:

1. The render-order = z mapping is sufficient for current use cases. Registration order is consumer-controlled (consumer constructs adapters in the order they want layered).
2. Adding explicit z would require resolving cross-consumer z conflicts (which mod's z-100 wins?). Library-not-platform: don't mediate. Consumers needing tight z control register with the library in their preferred order, accepting that other consumers can register later and end up "above."

**Defer-on-evidence:** if a real consumer surfaces a use case where registration-order isn't enough (e.g., a panel needs to render below another panel registered earlier), reopen the question. Until then, registration-order = z is the simplest workable model.

**Z within the same priority pass:** registration order. Two non-`dimsBehind` panels both visible at overlapping coords: the later-registered one wins click dispatch. Same as 14d-1's two-pass order; this is just naming the existing behavior explicitly.

### 4.7 Suppression scope — panel governs interaction within its scope

**Decision: each panel claims a scope; the kind of input determines whether the scope is global or bounds-local.**

The principle a panel honors is: *the panel governs interaction within its scope.* What "scope" means depends on the panel's flags:

- **Opaque panel scope** = the panel's bounding box (local).
- **`tracksAsModal` panel scope** = the whole screen (global). Modal claims the screen; everything behind it should feel inert per Trevor's principle.

Different input modalities suppress within these scopes differently:

| Suppression | Driver | When `tracksAsModal` visible | When opaque-only (no modal-tracking) | Gate (combined) |
|---|---|---|---|---|
| Slot hover (returns null) | Pointer position | Suppress GLOBALLY | Suppress LOCALLY (cursor inside opaque bounds) | `hasAnyVisibleModalTracking() \|\| hasAnyVisibleOpaquePanelAt(x, y)` |
| Tooltip suppression | Pointer position | Suppress GLOBALLY | Suppress LOCALLY (cursor inside opaque bounds) | `hasAnyVisibleModalTracking() \|\| hasAnyVisibleOpaquePanelAtCursor()` |
| Cursor change (`setAllowCursorChanges`) | Window state | Suppress (lock cursor) | Don't lock | `hasAnyVisibleModalTracking()` |
| Keyboard eating (non-Escape) | Keystroke event | Suppress (eat keys) | Don't eat | `hasAnyVisibleModalTracking()` |

**Pointer-driven suppressions OR both gates.** Pointer position determines whether bounds-local suppression applies; modal-tracking is a global predicate that overrides bounds. So the gate is "global modal-tracking up OR cursor inside an opaque bounds." Hover and tooltip both work this way.

**Window-state suppressions are modal-tracking-only.** Cursor lock and keyboard eating can't localize to bounds — cursor is one OS-level resource that's locked or unlocked for the whole window; keyboard isn't pointer-driven and has no bounds. So they gate on `tracksAsModal` only. Localizing cursor would flicker on every panel-edge crossing; localizing keyboard isn't even structurally meaningful.

**Why this is the right shape (and what the original §4.7 framing got wrong):**

The first draft of §4.7 said "pointer-driven localizes; window-state globalizes." That implied pointer-driven suppressions only operate at bounds, even for modal-tracking panels — which broke the 14d-1 modal contract (everything behind a modal feels inert, including outside the modal's bounds). Smoke surfaced the regression: with a modal up, slot hover and item tooltips outside the modal's bounds still rendered.

The corrected framing: the panel's *scope* is what matters; the suppression then operates *throughout that scope* for whichever input modality applies. Modal-tracking widens the scope to global; opaque widens it to bounds. Cursor + keyboard are window-state-only so they can only respect the wider modal-tracking scope.

> **Suppression scope follows panel scope.** Click-through prohibition is the principle: visible panels are interaction-opaque over their scope. An opaque panel's scope is its bounds; a `tracksAsModal` panel's scope is the whole screen. Pointer-driven suppressions (hover, tooltip) honor both — bounds-local for opaque, global for modal-tracking. Window-state suppressions (cursor lock, keyboard) honor only the global modal-tracking scope because they can't localize to bounds structurally. Both follow the same principle; the difference is which scope is reachable per input modality.

### 4.8 Migration — `cancelsUnhandledClicks` → `opaque` rename + default flip

**Mechanical:**

```java
// 14d-1 (today)
private boolean cancelsUnhandledClicks = false;
public Panel cancelsUnhandledClicks(boolean b);
public boolean cancelsUnhandledClicks();

// M9 (post-rename)
private boolean opaque = true;          // default flipped false → true
public Panel opaque(boolean b);
public boolean isOpaque();              // getter renamed from cancelsUnhandledClicks() → isOpaque()
```

Plus two new flags + sugar:

```java
private boolean dimsBehind = false;
private boolean tracksAsModal = false;

public Panel dimsBehind(boolean b);
public boolean dimsBehind();

public Panel tracksAsModal(boolean b);
public boolean tracksAsModal();

public Panel modal();   // sugar for opaque(true).dimsBehind(true).tracksAsModal(true)
```

**Dialog builders (mechanical):**

```java
// ConfirmDialog.build() / AlertDialog.build() today:
//   panel.cancelsUnhandledClicks(true);
// → renames to:
//   panel.modal();  // composition sugar; equivalent to the trio
```

Internal-rename only. Dialog behavior unchanged: `modal()` sets all three flags; opacity, dim, modal-tracking all preserved.

**ScreenPanelRegistry callers:**

All references to `panel.cancelsUnhandledClicks()` (getter) update:

- `renderMatchingPanels` two-pass: read `panel.dimsBehind()` instead — preserves dim behavior; new flag drives it.
- `dispatchModalClick`: read `panel.isOpaque()` for "modal panel" check — but rename to `dispatchOpaqueClick` since semantics generalize. Add `panel.tracksAsModal()` check separately for "blocks outside-bounds clicks" semantic.
- `hasVisibleModalOnScreen` / `hasAnyVisibleModal`: rename to `hasVisibleModalTrackingOnScreen` / `hasAnyVisibleModalTracking`. Read `panel.tracksAsModal()` not `panel.isOpaque()`. Preserves keyboard + cursor gate semantics.
- New: `findOpaquePanelAt(screen, x, y)` for pointer-driven local suppressions.
- `shouldEatUnhandledClick`: rename or repurpose.

Mixin call-sites update gate function:

- `MenuKitModalMouseHandlerMixin` → consults `findOpaquePanelAt` + `hasAnyVisibleModalTracking`.
- `MenuKitModalHoverMixin` → consults `findOpaquePanelAt`.
- `MenuKitTooltipSuppressMixin` → consults `findOpaquePanelAt`.
- `MenuKitModalKeyboardHandlerMixin` → consults `hasAnyVisibleModalTracking` (gate name change, semantics preserved).

**Default-flip behavioral change:**

This is the deliberate behavior change. Existing non-dialog consumer panels become opaque by default. Specifically:

- Consumer-mod region-based panels: now block click-through. The bug the principle prohibits is fixed automatically — no consumer-side change needed. Verified by smoke after migration.
- Consumer-mod lambda-based panels: still allow click-through unless they call `.activeOn(...)`. The lambda escape-hatch property is preserved (lambda = consumer-owned dispatch).

**Smoke wire-ups (in-tree):** RegionProbes, dialog smoke, scroll smoke — all already opaque-correct or dialog-uses-`modal()` (which keeps existing semantics). No smoke wire-up changes expected. Verified by re-running all `/mkverify` contracts after migration.

**Probes per §6.**

---

## 5. Cross-context applicability

Per CONTEXTS.md:

| Context | Opacity applies | Notes |
|---|---|---|
| **MenuContext** | Yes — region-based via existing registry; lambda-based via `.activeOn` | Primary case; modal dialogs already exist; bug fix here |
| **SlotGroupContext** | Yes — bounded by slot-group bounds; opacity is naturally scoped | Slot-group panels can't extend past slot-group bounds; opacity at panel.bounds level still applies |
| **HudContext** | Render-only context — moot | HUDs have no input dispatch; opacity flag has no observable effect; ignore for HUD panels |
| **StandaloneContext** | Yes — full input dispatch; same lambda or addRenderableWidget paths | MenuKit-native screens already dispatch through Panel; vanilla decoration via `ScreenPanelAdapter` lambda path with `.activeOn` |

**HudContext detail.** HUD panels are render-only — `MKHudPanel` doesn't expose `mouseClicked` or any input hook. The `opaque` flag is silently ignored for HUD panels (no behavior to gate). Doc note in HUD context: "HUD panels are render-only; opacity has no effect." A consumer setting `panel.opaque(false)` on a HUD panel sees no observable difference.

**SlotGroupContext detail.** SlotGroupContext panels anchor to slot-group bounds; their bounds are inherited from the slot group. Opacity applies at the panel's bounds — not extending past the slot group. Same dispatch path as MenuContext via the unified registry.

**StandaloneContext — MenuKit-native screens.** MenuKit-native standalone screens (subclasses of `MenuKitScreen`) dispatch through Panel directly in `mouseClicked`. The opacity flag affects the dispatch decision: if a Panel within the screen is opaque, clicks within its bounds dispatch to that Panel; clicks outside reach next-Panel-or-screen-default per existing logic. This already behaves correctly for the unified opacity model — Panel level dispatch IS bounds-localized.

**StandaloneContext — vanilla decoration via mixin.** Consumer mixin into a vanilla `Screen` subclass. Two paths:

1. `ScreenPanelAdapter` lambda — uses new `.activeOn` lifecycle; opacity coverage automatic.
2. `addRenderableWidget` — vanilla widgets handle their own click eating; M9 doesn't apply (vanilla widget framework owns this).

Most StandaloneContext decoration uses path 1 (rich panels) or path 2 (single buttons). Both have opacity correctness; M9 handles path 1 via lambda registration.

---

## 6. V0–V12 probe audit — per-probe verdict

In-tree probe surfaces, audited for opacity behavior under the new defaults:

| Scenario | File | Path | Verdict |
|---|---|---|---|
| **V0** Consumer mini-application | (in consumer mods) | n/a in-tree | Out-of-tree; consumer responsibility; doc note pointing at lambda registration |
| **V1** Element palette sweep | `ElementDemoHandler/Screen` | MenuKit-native standalone screen | **Exempt** — MenuKit-native dispatch; opacity already at Panel level; no behavior change |
| **V2** Regions × element palette | `RegionProbes.java` | Region-based, `.onAny()` | **Verify-under-new-defaults** — probes use `.onAny()` region; default-flip → opacity true; visual smoke confirms no regression. Likely passes without change since probes don't overlap slot regions in canonical placement |
| **V3** Visibility lifecycle and inertness | (element-level + slot-level) | Mixed | **Exempt** — element-level inertness already at element layer; slot-level inertness already at slot layer; opacity adds bounding-box layer above both. No regression expected |
| **V4** Native screen lifecycle | `TestContractScreen/Handler` | MenuKit-native standalone screen | **Exempt** — same as V1; native dispatch through Panel |
| **V4.2** Inventory-context cross-context reuse | (mixed) | `ScreenPanelAdapter` region-based | **Verify-under-new-defaults** — region-based; default-flip → opacity true; expected to pass |
| **V5** Slot interactions | (in consumer mods + smoke) | Mixed | **Verify-under-new-defaults** — V5 tests slot click + persist; if any V5 probe panel covers a slot, opacity-true could change behavior. Audit during implementation; expected exempt since slot-interaction tests are slot-layer focused |
| **V6** M1 persistence scenarios | (server-side automated) | Server thread | **Exempt** — server-side; no rendering or input dispatch |
| **V7** HUD behavior | (in consumer mods) | HudContext | **Exempt** — HUDs render-only; opacity flag silently ignored |
| **V8** MKFamily cross-mod | (in consumer mods) | Mixed | Out-of-tree; consumer responsibility |
| **V9** M8 layout math | `ContractVerification` V9 probe | Pure math | **Exempt** — geometry; not affected by opacity (same exemption reasoning as V12) |
| **V10** Modal click-eat (14d-1) | `ContractVerification` V10 probe | Pure decision logic | **Rewrite** — semantics change: probe tests `shouldEatUnhandledClick(anyModal, consumed)` which renames to `shouldEatOpaqueDispatch` semantics. Mechanical update to call new helper; same logic |
| **V11** Dialog builder validation | `ContractVerification` V11 probe | Pure builder validation | **Mechanical-rename** — probe asserts `panel.cancelsUnhandledClicks() == true` for dialogs; rename to `panel.isOpaque() == true && panel.tracksAsModal() == true`. Both should be true post-`modal()` sugar. Same shape |
| **V12** ScrollContainer math | `ContractVerification` V12 probe | Pure math | **Exempt** — geometry; not affected by opacity |
| **V13** Modal-scroll dispatch | `ContractVerification` V13 probe | Pure null-guard | **Rewrite** — probe tests `dispatchModalScroll(...)` semantics which generalize per §4.5. Update probe to test `dispatchOpaqueScroll` (new name) under same null-guard cases. Mechanical |
| **V14 (new)** Opacity dispatch | New probe in `ContractVerification` | Pure decision | **Add** — tests `findOpaquePanelAt`, `hasAnyVisibleModalTracking`, decision-helper unit tests. ~5–10 cases |
| **V15 (new)** Lambda lifecycle | New probe in `ContractVerification` | Pure registry test | **Add** — tests `.activeOn` / `.deactivate` registration semantics; null guards; idempotency. ~3–5 cases |

**Summary breakdown:**

- **Add new** — V14 (opacity dispatch), V15 (lambda lifecycle).
- **Rewrite (mechanical, semantic-rename)** — V10, V13.
- **Mechanical-rename only** — V11.
- **Verify-under-new-defaults** — V2, V4.2, V5.
- **Exempt** — V1, V3, V4, V6, V7, V9, V12.

No probe currently relies on click-through to vanilla underneath as the test mechanism. (V5 slot interactions are tested at the slot layer, not via panels covering slots.)

**Out-of-tree (consumer mod) probes** — V0, V8, and any consumer-side V2.x extensions live in consumer mods (inventory-plus, shulker-palette, agreeable-allays). M9 doc points consumers at:

- Region-based probes: pick up new opacity default automatically; verify post-migration.
- Lambda-based probes: must add `.activeOn(...)` calls to participate in opacity dispatch (or accept click-through for that probe).

Consumer-side audit is consumer responsibility per library-not-platform discipline. The library doc names what changed; consumers update at their pace.

---

## 7. Verification

### Automated (`/mkverify`)

15 library contracts post-M9. New entries:

| Contract | Cases | Description |
|---|---|---|
| V14 | ~10–15 | Opacity dispatch under realistic multi-panel state. Single-panel cases: `findOpaquePanelAt` returns panel for cursor inside; null for cursor outside; null for invisible panel; `hasAnyVisibleModalTracking` correct gate behavior; `shouldEatOpaqueDispatch` decision logic. Multi-panel cases: (a) two opaque panels, no overlap → each dispatches at its bounds; (b) two opaque panels overlapping → higher-z (later-registered) wins click dispatch; (c) modal-tracking + non-modal-tracking visible simultaneously → `hasAnyVisibleModalTracking` returns true (gates global suppressions); `findOpaquePanelAt` outside both returns null but `hasAnyVisibleModalTracking` returns true → eat at modal boundary; (d) `findOpaquePanelAt` with multiple visible panels at same point → returns topmost (last-registered). Round-1 verdict expanded scope to ensure unified registry dispatch is correct under realistic multi-panel state, not just single-panel. |
| V15 | ~3–5 | Lambda lifecycle — `.activeOn` adds entry; `.deactivate` removes; double-register replaces (or is idempotent); null guards |

Existing contracts updated per §6 audit table:

- V10 — rename helper from `shouldEatUnhandledClick` to `shouldEatOpaqueDispatch`; same 12 cases.
- V11 — assertion rename: `cancelsUnhandledClicks()` → `isOpaque() && tracksAsModal()`. Same 11 cases.
- V13 — helper rename: `dispatchModalScroll` → `dispatchOpaqueScroll`. Same 3 cases.

Total contracts post-M9: 15 (was 13).

### Visual smoke

Smoke scenarios:

1. **`/mkverify regions`** (V2 RegionProbes) — region-based probes still render at all 8 MenuRegion + 9 HudRegion positions; clicks on probe surface eat (don't pass through to slot underneath if probe lands over slots).
2. **`/mkverify dialog`** (V11 dialog smoke) — modal dialog still eats clicks outside bounds; tooltips/cursor/hover behind the dialog still suppressed; dim layer still renders.
3. **`/mkverify scroll`** (V12 scroll smoke) — ScrollContainer panel: clicks on scrollbar handle eat; clicks in viewport empty space eat (don't pass through); scroll inside dispatches to ScrollContainer.
4. **New scenario: non-modal panel covers vanilla slot** — register a region-based panel at `MenuRegion.RIGHT_ALIGN_TOP` that covers a player-inventory slot; click inside the panel's empty space; verify slot does NOT receive click (the bug we're fixing). Wire into `wireOpacitySmoke()` toggled via `/mkverify opacity`.

### Cross-mod regression

After single-commit migration, smoke through all consumer mods that ship MK panels:

- inventory-plus — verify equipment panel, peek panel, F8/F15 backdrops; verify lambda-based panels still work (consumer mixin needs `.activeOn` post-M9; if not, click-through bug for those panels — explicit doc note).
- shulker-palette — verify toggle panel above shulker grid; lambda-anchor; consumer mixin needs `.activeOn`.
- agreeable-allays — verify HUD panels (exempt); any inventory decorations (verify-under-new-defaults).
- sandboxes — verify pause-menu buttons (vanilla addRenderableWidget; exempt); enter/back/mode panels.

Consumer mods NOT updated to use `.activeOn` for their lambda-path panels will see click-through for those panels — explicit doc note + DEFERRED.md filing.

---

## 8. What this licenses / does NOT license

**Licensed:**

- Half-opaque dropdowns / popovers via `opaque(true) + dimsBehind(false) + tracksAsModal(false)`.
- Click-blocking invisible panels via `PanelStyle.NONE + opaque(true)`.
- Future independent-flag combinations as primitives surface.

**NOT licensed:**

- Per-element opacity. Opacity is a Panel property; "hole" elements that pass through to slots underneath aren't a v1 capability. Surface as primitive gap if needed.
- Animated opacity transitions. THESIS scope ceiling — animation framework deferred.
- Per-region z-order explicit API. Registration order is the model; defer to evidence.
- Cross-mod opacity mediation. Library doesn't pick winners across consumer mods; registration order = z within a screen; cross-mod conflicts are consumer-coordinated.

---

## 9. Implementation outline

Single commit. Approximate LOC:

| Component | Files | LOC delta |
|---|---|---|
| `Panel.java` — flag rename + 2 new flags + `modal()` sugar | 1 | +30 / -5 |
| `ScreenPanelAdapter.java` — `.activeOn` / `.deactivate` lifecycle | 1 | +50 |
| `ScreenPanelRegistry.java` — unified opacity registry; bounds-supplier tracking; `findOpaquePanelAt`, `hasAnyVisibleModalTracking` helpers | 1 | +100 / -30 |
| `MenuKitModalMouseHandlerMixin.java` — gate condition update | 1 | +20 / -10 |
| `MenuKitModalKeyboardHandlerMixin.java` — gate condition rename | 1 | +5 / -5 |
| `MenuKitModalHoverMixin.java` — gate condition update | 1 | +10 / -5 |
| `MenuKitTooltipSuppressMixin.java` — gate condition update | 1 | +10 / -5 |
| `ConfirmDialog.java` / `AlertDialog.java` — internal flag rename via `modal()` sugar | 2 | +5 / -5 |
| `ContractVerification.java` — V10/V11/V13 mechanical updates + new V14/V15 probes + opacity smoke wire-up | 1 | +200 / -30 |
| `MenuKitClient.java` — cursor lock callback gate update (rename) | 1 | +2 / -2 |
| `Design Docs/Mechanisms/M9_PANEL_OPACITY.md` | 1 (new) | +600 |
| `Design Docs/PHASES.md` — current marker advance | 1 | +5 / -1 |
| `Design Docs/DEFERRED.md` — close click-through entry; flag consumer-mod lambda updates as follow-on | 1 | +10 / -20 |

**Total (rough):** ~12 files modified, ~1 new (M9 doc). +1050 / -120 LOC.

**File ordering for the implementer:**

1. Panel.java — rename + new flags + sugar.
2. Internal callers — registry helpers, mixin gates, dialog builders.
3. ContractVerification.java — V10/V11/V13 updates + V14/V15 new + smoke wire-up.
4. ScreenPanelAdapter — `.activeOn` / `.deactivate` lifecycle.
5. ScreenPanelRegistry — unified registry; bounds-supplier; `findOpaquePanelAt`.
6. Doc updates — PHASES.md marker; DEFERRED.md cleanup.

Build + `/mkverify all` between layers; visual smoke at completion. Single commit.

---

## 10. Open questions for advisor verdict

Three load-bearing positions confirmed in entry-brief exchange (signed off):

- §4.3 Modal reframe via composition — `modal()` sugar over three independent flags.
- §4.4 Lambda-path registration via `.activeOn` / `.deactivate`.
- §4.7 Suppression scope asymmetry — pointer-driven local; cursor lock global-modal.

Two procedural positions confirmed (signed off):

- Straight to advisor (skip Trevor pre-review on this doc; same path as 14d-1).
- Single-commit at implementation time.

**Round 1 verdict targets these confirmations + the secondary load-bearing decision:**

**Q1 (§4.6 z-order).** Registration-order = z; modal-tracking panels above non-modal-tracking. No explicit z API in v1. Defer-on-evidence. **Advisor verdict requested:** is this the right deferral, or does the principle motivate explicit z control upfront?

**Q2 (§4.4 failure-mode handling).** Lambda consumer forgets `.activeOn(...)`: click-through bug for that panel. Library does NOT enforce; doc warning only. **Advisor verdict requested:** is the explicit-warning-no-enforcement choice consistent with library-not-platform discipline? Alternative: detect un-registered lambda adapters during render and log warning at the call-site.

**Q3 (§4.3 edge case).** `opaque(false) + tracksAsModal(true)`: undefined for v1; consumer responsibility. No `IllegalStateException`. **Advisor verdict requested:** documented-undefined or builder-rejected? My pull: documented-undefined for v1; fold-on-evidence to reject if misuse surfaces.

**Q4 (V14/V15 scope).** New probe coverage: V14 opacity dispatch (~5–10 cases) + V15 lambda lifecycle (~3–5 cases). **Advisor verdict requested:** sufficient automated coverage, or should V14/V15 expand to include cross-screen state (multiple registered adapters with overlapping coords; modal-tracking + non-modal interaction)?

**Q5 (V5 slot-interactions audit).** §6 marks V5 as "verify-under-new-defaults; expected exempt since slot-interaction tests are slot-layer focused." This is a concrete audit-during-implementation item, not a load-bearing pre-decision. **Advisor verdict requested:** acceptable to defer the V5 verdict to implementation, or should the doc commit to a specific re-audit checklist before code lands?

**Q6 (Per-element opacity capability).** §3 lists per-element opacity as out-of-scope. **Advisor verdict requested:** should §3 also explicitly file this as a primitive-gap candidate for surface to advisor on first concrete consumer (e.g., a future "highlight reticle" element that wants click-through on its non-highlighted regions)? My pull: yes; add to DEFERRED.md as a primitive-gap follow-on.

---

## 11. Implementation findings (smoke fold-inline)

Three findings surfaced during smoke verification of the implementation. All resolved inline; no round 2 needed. Each refined the design without contradicting the round-1 verdicts.

### 11.1 Scroll release passthrough — initial cut broke ScrollContainer drag-end

**Symptom.** Click-and-drag on the scrollbar handle started a drag, but the drag never ended on release. User had to move the cursor outside the scroll panel and release before the drag stopped.

**Cause.** Initial M9 implementation ate every press-and-release event when cursor was inside any opaque panel. Eating the release at `MouseHandler.onButton` HEAD canceled the entire input chain — including Fabric's `allowMouseRelease` hook, which is the path to `adapter.mouseReleased` for drag-end detection (per 14d-2 plumbing). Pre-M9, the eat-everything-when-modal approach didn't hit this case because non-modal panels didn't eat at the input-handler level. M9's universal opacity extended the eat to releases inside non-modal opaque panels — breaking ScrollContainer drag-end.

**Initial fix (incorrect).** Early-return in the mixin for `action == 0` (GLFW_RELEASE) — passes releases through to vanilla → Fabric's hook → `adapter.mouseReleased`. ScrollContainer drag-end works again, but introduced a downstream regression (see §11.3).

### 11.2 Hover/tooltip global-modal scope — §4.7 framing was incomplete

**Symptom.** Modal dialog up; cursor over a creative tab outside the dialog's bounds → tab still showed hover highlight + hover tooltip. Slot hover (white rectangle) and item tooltips outside the dialog also leaked through. (Click-blocking and cursor lock worked correctly.)

**Cause.** §4.7's first-cut framing said "pointer-driven suppressions localize to bounds; window-state suppressions globalize for modal-tracking." That left modal-tracking with no global hover/tooltip suppression — pointer-driven mixins gated only on `hasAnyVisibleOpaquePanelAt(x, y)`, which returns false when cursor is outside the modal's bounds. The 14d-1 contract ("everything behind the panel is completely inert") was structurally weakened.

**Fix.** Pointer-driven suppressions OR both gates: global modal-tracking visible OR cursor-inside-opaque-bounds. Hover and tooltip mixins now check `hasAnyVisibleModalTracking() || hasAnyVisibleOpaquePanelAt(x, y)`. §4.7 reframed: each panel claims a *scope* (opaque = bounds; tracksAsModal = whole screen); pointer-driven suppressions honor either scope; window-state suppressions can only honor the global scope (cursor lock and keyboard can't localize to bounds structurally). The asymmetry remains principled but the framing is corrected.

### 11.3 Symmetric press/release at mixin level — modal tab-blocking required release-eating

**Symptom.** Modal dialog up; clicking a creative tab outside the dialog's bounds switched the tab. (Press was correctly eaten; release was passing through per §11.1's fix; vanilla `CreativeModeInventoryScreen.mouseReleased` is what selects tabs — not `mouseClicked`.)

**Cause.** Vanilla's tab selection happens on release, not press. The §11.1 fix passed all releases through, including releases on tabs while modal-tracking was up — letting vanilla process tab selection.

**Fix.** Symmetric press/release handling at the mixin level. When the press would be eaten (opaque-at-cursor OR modal-tracking visible), the release is also eaten. Since canceling `onButton` prevents Fabric's `allowMouseRelease` from firing, the registry now manually dispatches `mouseReleased` to all visible opaque adapters' elements at mixin level — preserving drag-end semantics for ScrollContainer (and future draggables).

The new helper `dispatchOpaqueRelease` mirrors `dispatchOpaqueClick`: cursor inside opaque OR modal-tracking visible → eat + dispatch release to opaque adapters; otherwise pass through to Fabric's existing path. Both fixes (§11.1 ScrollContainer drag-end + §11.3 modal tab-blocking) hold simultaneously because the gate condition is symmetric with the press path.

### Calibration heuristic surfaced

*"When the input-handler mixin pre-empts vanilla's chain, the mixin owns the dispatch responsibility for whatever vanilla would have routed."* §11.1's initial fix passed responsibility back to Fabric's hook, which couldn't fire because the mixin canceled. §11.3's final shape keeps responsibility at the mixin — symmetric with the press path. Same shape as 14d-1's round-3 finding: when you eat at a layer, you also dispatch at that layer; partial pre-emption (eat input but rely on downstream for dispatch) is structurally fragile.

---

## Appendix A — Builder API reference (post-M9)

**Panel builder:**

```java
public Panel opaque(boolean isOpaque);          // default true
public Panel dimsBehind(boolean dims);           // default false
public Panel tracksAsModal(boolean tracks);      // default false
public Panel modal();                            // sugar: opaque(true).dimsBehind(true).tracksAsModal(true)

public boolean isOpaque();
public boolean dimsBehind();
public boolean tracksAsModal();
```

**ScreenPanelAdapter (lambda path):**

```java
public ScreenPanelAdapter activeOn(Screen screen, Supplier<ScreenBounds> boundsSupplier);
public ScreenPanelAdapter deactivate(Screen screen);
```

**ScreenPanelRegistry (post-rename helpers):**

```java
public static @Nullable ScreenPanelAdapter findOpaquePanelAt(Screen, double x, double y);
public static boolean hasAnyVisibleModalTracking();
public static boolean hasVisibleModalTrackingOnScreen(AbstractContainerScreen<?>);
public static boolean shouldEatOpaqueDispatch(boolean opaqueAtCursor, boolean dispatched);
public static boolean dispatchOpaqueClick(Screen, double x, double y, int button);
public static boolean dispatchOpaqueRelease(Screen, double x, double y, int button);
public static boolean dispatchOpaqueScroll(Screen, double x, double y, double sx, double sy);
```

---

## Appendix B — Migration cheatsheet (for implementer)

**Find/replace pattern** (review each call site; not all are mechanical):

| Old | New |
|---|---|
| `panel.cancelsUnhandledClicks(true)` | `panel.modal()` (in dialog builders) OR `panel.opaque(true)` (everywhere else) |
| `panel.cancelsUnhandledClicks(false)` | `panel.opaque(false)` |
| `panel.cancelsUnhandledClicks()` (getter) | `panel.isOpaque()` (most cases) OR `panel.tracksAsModal()` (modal-tracking semantic) |
| `hasAnyVisibleModal()` | `hasAnyVisibleModalTracking()` |
| `hasVisibleModalOnScreen(...)` | `hasVisibleModalTrackingOnScreen(...)` |
| `dispatchModalClick(...)` | `dispatchOpaqueClick(...)` |
| `dispatchModalScroll(...)` | `dispatchOpaqueScroll(...)` |
| `shouldEatUnhandledClick(anyModal, consumed)` | `shouldEatOpaqueDispatch(opaqueAtCursor, dispatched)` |

**Audit checklist for each call site:**

1. Is the call gate-checking "any modal panel visible" (global)? → `hasAnyVisibleModalTracking()`.
2. Is the call gate-checking "click bounds inside opaque panel" (local)? → `findOpaquePanelAt(...) != null`.
3. Is the call testing the per-Panel flag for opacity (interaction)? → `panel.isOpaque()`.
4. Is the call testing the per-Panel flag for dim (visual)? → `panel.dimsBehind()`.
5. Is the call testing the per-Panel flag for modal-tracking (Escape, cursor, keyboard)? → `panel.tracksAsModal()`.

The five questions partition the rename. Any call site that doesn't fit one of the five is a refactor signal — re-examine.
