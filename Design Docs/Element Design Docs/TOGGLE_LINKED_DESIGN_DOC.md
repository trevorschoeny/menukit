# Toggle.linked — design doc

**Specialization purpose.** A Toggle variant where the boolean state lives in consumer code instead of the element. The consumer provides a `BooleanSupplier` that drives rendering and a `Runnable` that fires when the user clicks to toggle. MenuKit does not ship a persistence abstraction — `Toggle.linked` is how persistence happens: the consumer decides where state lives (block entity, player attachment, config file, in-memory field) and Toggle.linked bridges the UI to that state.

**Phase 9's second and final specialization.** Same factor-then-specialize cadence as Button.icon. Pre-committed in the Phase 8 Toggle design doc — this design doc formalizes the pre-commitment and locks the open questions.

**Audit-sourced.** Implicit across all three small consumer mods: shulker-palette's mode toggles, agreeable-allays's follow toggle, sandboxes's ambient-setting toggles. All three want a toggle whose state persists across sessions, and none of them want MenuKit's internal boolean as source-of-truth — they already have their own state stores.

---

## What this phase actually ships

Two commits, in order:

1. **Factor Toggle's state handling into two protected hooks** — `currentState()` and `applyState(boolean)`. Pure refactor; no behavioral change for existing Toggle users.
2. **Add `Toggle.linked(...)` factory + `LinkedToggle` subclass** — the specialization that overrides both hooks to read from a supplier and fire a Runnable without internal mutation.

---

## Factoring: the protected hooks

Current Toggle touches state in three places (render reads for RAISED/INSET, mouseClicked inverts+fires, setOn sets+fires). The factoring routes all reads through `currentState()` and all commits through `applyState(boolean)`, with `applyState` handling *both* the internal-state commit and the callback-fire as a single atomic operation.

```java
// Base Toggle (after factoring)
private boolean state;
private final Consumer<Boolean> onToggle;

/**
 * Returns the Toggle's current boolean state.
 *
 * <p>Stable extension point for consumer Toggle subclasses. Override to
 * read state from external storage (supplier, block entity, config, etc.).
 *
 * <p>Base Toggle's render and click handling call {@code currentState()}
 * exactly once per frame. Subclasses overriding {@code currentState()}
 * may rely on this: their supplier is invoked once per frame for base
 * Toggle's rendering purposes. If the supplier returns different values
 * across rapid successive calls, only the first call per frame affects
 * the rendered output.
 */
protected boolean currentState() {
    return state;
}

/**
 * Commits a state transition. Subclasses define what "commit" means for
 * their state-ownership model:
 *
 * <ul>
 *   <li>Base Toggle (element-owned state): writes the new state to internal
 *       storage and fires the {@code onToggle} callback with the new state.</li>
 *   <li>Toggle.linked (consumer-owned state): fires the consumer's callback;
 *       consumer is responsible for updating their own state. No internal
 *       storage commit happens.</li>
 * </ul>
 *
 * <p>Called from {@code toggleTo()} after the short-circuit no-op check
 * passes. Implementations should be atomic — the state transition and the
 * callback notification are conceptually a single event.
 *
 * <p>Stable extension point. Signature and semantic contract maintained
 * across MenuKit versions.
 */
protected void applyState(boolean newState) {
    this.state = newState;
    onToggle.accept(newState);
}

/** Short-circuit, commit, notify. Used by both mouseClicked and setOn. */
private void toggleTo(boolean newState) {
    if (currentState() == newState) return;
    applyState(newState);  // commits AND fires callback atomically
}

public boolean isOn()                { return currentState(); }
public void setOn(boolean newState)  { toggleTo(newState); }

@Override
public boolean mouseClicked(double mx, double my, int button) {
    if (button != 0 || isDisabled() || !hovered) return false;
    toggleTo(!currentState());
    return true;
}
```

### `render()` caches `currentState()` once per frame

The top of `render()` reads `currentState()` into a local and passes/uses that local for background-style decision, hover highlight gating, indicator painting. No subsequent `currentState()` calls in the same frame.

```java
@Override
public void render(RenderContext ctx) {
    int sx = ctx.originX() + childX;
    int sy = ctx.originY() + childY;
    hovered = isHovered(ctx);

    boolean disabled = isDisabled();
    boolean on = currentState();   // single read per frame
    PanelStyle bg = disabled ? PanelStyle.DARK
                  : on       ? PanelStyle.INSET
                             : PanelStyle.RAISED;
    PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, bg);

    if (!disabled && hovered) {
        ctx.graphics().fill(sx + 1, sy + 1, sx + width - 1, sy + height - 1, 0x30FFFFFF);
    }

    // tooltip dispatch unchanged
}
```

The contract-documented "exactly once per frame" promise is enforced by this structure.

### Invariant established by the factoring

Base Toggle code reads state *only* through `currentState()` — never `this.state` directly except in `applyState` itself. Subclasses override `currentState()` (and optionally `applyState`) and the rest of the render/click machinery Just Works.

### Edge cases

- **`setOn(currentState())` is still a no-op.** Short-circuit in `toggleTo` preserves Phase 8 contract.
- **Disabled Toggle ignores clicks.** mouseClicked returns false before touching state.
- **`this.state` field on LinkedToggle is dead storage.** Super's constructor initializes it; LinkedToggle's `currentState()` override means it's never read again. Minor memory cost; irrelevant.

---

## Toggle.linked API

```java
public static Toggle linked(int childX, int childY, int width, int height,
                            BooleanSupplier state,
                            Runnable onToggle);
```

- `state` — the supplier invoked each frame to drive rendering.
- `onToggle` — fired on user-initiated state changes; the consumer decides what to do (typically: update their own state store).
- Returns `Toggle` (public type); concrete `LinkedToggle` is package-private.

**Callback type is `Runnable`, matching the palette sketch.** Reasoning: Toggle.linked doesn't own the state; the consumer does. The consumer already knows what they want to flip — passing `newState` through the callback is redundant in the common case and doesn't generalize well when the supplier returns something computed rather than a raw field. Runnable is the cleaner primitive for "user clicked; do your thing."

If Phase 11 consumer refactors reveal genuine demand for passing the new state through the callback, `Runnable → Consumer<Boolean>` is a one-field migration and a cheap move at that point.

### Usage

```java
Toggle.linked(x, y, w, h,
    () -> config.autoSort,
    () -> config.autoSort = !config.autoSort);
```

**Tooltip setters inherit for free** from parent Toggle. `Toggle.linked(...).tooltip(...)` works with no additional code.

**No new PanelBuilder method.** Accessed via `.element(Toggle.linked(...))` per the Phase 9 no-new-builder-method principle.

---

## `LinkedToggle` internals

Package-private subclass of Toggle. Overrides both hooks:

```java
static final class LinkedToggle extends Toggle {
    private final BooleanSupplier stateSupplier;
    private final Runnable onToggleRunnable;

    LinkedToggle(int childX, int childY, int width, int height,
                 BooleanSupplier state, Runnable onToggle) {
        // Super needs Consumer<Boolean>; pass a no-op since applyState is
        // fully overridden below and never delegates to super's callback.
        super(childX, childY, width, height, state.getAsBoolean(), b -> {});
        this.stateSupplier = state;
        this.onToggleRunnable = onToggle;
    }

    @Override
    protected boolean currentState() {
        return stateSupplier.getAsBoolean();
    }

    @Override
    protected void applyState(boolean newState) {
        // Consumer-owned state — no internal commit to do.
        // Fire the Runnable; consumer mutates their state; supplier returns
        // the new value on next frame.
        onToggleRunnable.run();
    }
}
```

Key properties:
- Super's `state` field is dead storage after construction — `currentState()` override reads the supplier instead.
- Super's `onToggle` (Consumer<Boolean>) is a `b -> {}` no-op lambda and is never fired — `applyState` override doesn't delegate to super.
- Single source of truth for state lookup: the supplier.
- Single source of notification: the Runnable.

---

## Self-healing behavior (factory-javadoc note)

Documented on the `Toggle.linked` factory method's javadoc specifically — it's a linked-variant property, not a base Toggle property. Base Toggle owns its state internally; there's nothing external that could diverge, so "self-healing" isn't a concept there.

Proposed wording:

> *If the onToggle callback fails to update consumer state (bug, exception, swallowed error), the next frame's render reads the supplier and shows the unchanged state. The toggle visually snaps back to its pre-click appearance. State displayed is always state reported by the supplier — there is no internal state that could diverge from consumer state.*

Consumers reading base Toggle's docs don't need to see this. Consumers reaching for `Toggle.linked` see it right where they make the decision to use the variant.

---

## Persistence framing

`Toggle.linked`'s factory javadoc gets the persistence pitch directly:

> *State persistence is a consumer concern. MenuKit does not ship a persistence abstraction — no {@code PersistentValue<T>}, no {@code BooleanFlag}, no config-backed state helpers. If you need a toggle whose state persists, use {@code Toggle.linked} and back the supplier/callback with wherever your state actually lives: a block entity, player attachment, config file, static field on a singleton, or anywhere else. The supplier reads; the callback signals. The library gives you the visual element and user input handling; the storage is yours to define.*

This framing was drafted in DEFERRED.md during Phase 8; Phase 9 makes it real documentation. It says "no" to a built-in persistence layer explicitly and names the state-linked pattern as the library's answer.

---

## Conventions check

1. **Constructor shape** — `(childX, childY, width, height, state, onToggle)`. Matches base Toggle's constructor shape (supplier in place of initialState; callback in same trailing position). ✓
2. **Supplier variants** — `BooleanSupplier` is the primary form, not a secondary variant. This is a semantic shift (state ownership), not a Convention 2 "variable content" supplier variant. Treat as separate construction pattern, aligned with base but distinct. ✓
3. **Render-only defaults** — N/A (interactive).
4. **One builder method per element** — no new builder method; access via `.element(Toggle.linked(...))`. ✓
5. **Factory methods** — permitted under the Phase 9 refinement. `Toggle.linked(...)` returns a `LinkedToggle` subclass with structurally different construction (external-state supplier + Runnable callback instead of initialState + Consumer<Boolean>). Returns a different concrete type → factory permitted. ✓
6. **Vanilla textures** — N/A (inherits base Toggle's RAISED/INSET rendering).

No new convention refinements. Factoring and factory pattern mirror Button.icon's shape.

---

## Implementation order

Two commits.

**Commit 1 — factor Toggle state handling into hooks.**
- Introduce `protected boolean currentState()` and `protected void applyState(boolean)`.
- Move callback-fire into `applyState` (both internal-state commit and Consumer<Boolean>.accept happen atomically inside applyState).
- Extract private `toggleTo(boolean)` orchestration helper.
- Route `render()`, `mouseClicked`, `setOn`, `isOn` through the hooks.
- Cache `currentState()` once at top of `render()` as a local.
- Add stability-contract javadocs on both hooks with advisor's exact wording.
- Build, relaunch, visually verify existing Toggle behavior unchanged.

**Commit 2 — add `Toggle.linked(...)` + `LinkedToggle`.**
- Static factory on Toggle.
- Package-private `LinkedToggle final class`.
- Factory-javadoc additions: persistence framing + self-healing note.
- Build, relaunch, visually verify: consumer-state-backed toggle flips correctly, visual matches supplier on every frame, callback fires with no args, self-healing (if Runnable no-ops, toggle bounces back).

---

## Scope boundaries — what Toggle.linked does not do

- **No persistence layer.** See framing above. Consumers build their own.
- **No debouncing or animation delay.** Click → applyState → Runnable → next frame renders new state.
- **No partial-animation states.** State is strictly boolean at all times.
- **No state-change broadcasting beyond the single Runnable.** No event bus integration.
- **No `Toggle.linked` with `disabledWhen`.** Same posture as base Toggle — consumers wanting disabled-predicate subclass Toggle directly. Cheap now that the hooks are factored.
- **No new-state value in the callback.** Deliberate (see Runnable decision above). Revisit in Phase 11 if consumer demand surfaces.

---

## Architectural observations

**Factor-then-specialize is a proven template now.** Both Phase 9 specializations (Button.icon and Toggle.linked) followed the same structural pattern: factor the parent element into protected hooks, then add a subclass factory that overrides those hooks. This is a repeatable template for future element specializations.

**If Phase 11 or later reveals demand for Checkbox.linked, Radio.linked, or other specializations, the template is proven and the work is mechanical.** The factor-then-specialize cadence is worth capturing in Phase 12's documentation as "the shape of how MenuKit elements grow variants without API bloat." Carried into the Phase 9 report and DEFERRED.md handoff for Phase 12.

**Interaction decisions (like state-commit-and-callback-fire coupling) surface during cross-question pressure-testing.** The draft originally had `toggleTo` fire the callback after `applyState` committed — fine when callback types matched. The switch to Runnable for linked revealed that commit and notify are architecturally coupled; they happen at the same "transition is real now" moment. Folding them into `applyState` reflects the true coupling rather than implying independence that doesn't exist. This is an architectural truth the factoring now honors.

---

## Approved 2026-04-14. Implementation proceeds.

Files:
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Toggle.java` (factoring + factory + LinkedToggle nested class).
