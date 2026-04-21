# Toggle — design doc

**Element purpose.** A two-state on/off control with visual differentiation between states and a callback on state change. The general primitive for "boolean setting" interactions.

**First Phase 8 element designed from scratch.** Previous four (Icon, Divider, ItemDisplay, ProgressBar) were either fresh additions or HUD generalizations. Toggle is fresh design against the established conventions.

**First element with mutable internal state.** The palette explicitly names this as the one declared-structure exception: *"Internal state is mutable (one of the narrow exceptions to the declared-structure discipline — state changes are the element's reason for existing, and they do not affect structural shape)."* This design addresses the exception head-on.

---

## The mutable-state exception, explicitly

The thesis's declared-structure discipline says: "structure is decided once and frozen; visibility is the mutable dimension." Toggle's internal boolean state is a second mutable dimension for this one element type.

**Why the exception is legitimate:** state changes don't affect structural shape. A toggle flipping on/off doesn't add or remove elements, doesn't change layout, doesn't alter the panel's element list. The only thing that changes is the internal boolean and the subsequent render pass. No downstream structural consequence.

**Scope of the exception.** Applies to Toggle and its specializations (Checkbox, Radio — all three have mutable boolean-ish state). Does not apply to other elements; structure remains frozen everywhere else.

**Alternative considered and rejected: state-supplier variant only.** The Phase 9 specialization (`Toggle.linked(...)`) uses a `BooleanSupplier` for consumer-owned state. For Phase 8, could we *only* ship that form, avoiding the element-owned mutable state entirely? Rejected because:
- The common case (setting that lives on the element for one-session toggles, or for UIs without external state stores) would require every consumer to build a boilerplate state holder.
- Checkbox and Radio inherit the same pattern — all three elements having element-owned state keeps them parallel.
- The exception is narrow (one boolean per toggle instance, no structural consequence). Compliance with declared-structure for everything else is not compromised.

**Class-level javadoc commitment.** The exception must be documented on the Toggle class's javadoc itself, not just in this design doc. A future reader seeing "Toggle has mutable state" should find the architectural justification immediately in the code. One paragraph explaining: what the exception is, why it's legitimate, and its scope.

---

## Conventions pressure-test

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

Applies. Toggle has:
- `childX`, `childY` — position
- `width`, `height` — dimensions (not content-derived; consumer chooses size)
- `initialState` — "content" is the toggle's initial boolean value
- `onToggle` — the callback, `Consumer<Boolean>` receiving the new state

```java
new Toggle(x, y, width, height, boolean initialState, Consumer<Boolean> onToggle);
new Toggle(x, y, width, height, boolean initialState, Consumer<Boolean> onToggle,
           BooleanSupplier disabledWhen);  // with optional disabled predicate
```

The `disabledWhen` optional form mirrors Button's pattern — two constructors, plain and with-disabled.

### Convention 2 — Supplier variants for variable content (data-vs-configuration)

**Doesn't apply in Phase 8.** Toggle's current state is *mutable internal state*, not variable content in the data-vs-configuration sense. No `Supplier<Boolean>` variant in Phase 8 because the state isn't supplied — it's owned by the element.

Phase 9 adds `Toggle.linked(x, y, w, h, BooleanSupplier state, Runnable onToggle)` — the specialized variant where the consumer owns the state. That's a semantic shift (who owns the state), not a routine supplier variant of variable content.

Convention 2 still holds for elements with variable content; it simply has nothing to do here.

`initialState` is configuration — fixed at construction, sets the starting boolean. `disabledWhen` is a `BooleanSupplier` but that's not a Convention 2 content supplier; it's a gating predicate pattern shared with Button.

### Convention 3 — Render-only inherits defaults

**Partially applies — Toggle is interactive.** Toggle overrides `mouseClicked` (standard Button-like click behavior). Inherits `isVisible()` (default true) and `isHovered(ctx)` (default using own bounds).

### Convention 4 — One builder method per element

Applies. `.toggle(...)` on PanelBuilder. One overload (the plain form); disabled variant via `.element(new Toggle(..., disabledWhen))`, matching Button's approach (Button has no `.button()` builder overload for `disabledWhen` either — the full-form constructor goes through `.element(...)`).

```java
panelBuilder.toggle(x, y, w, h, boolean initial, Consumer<Boolean> onToggle);
```

One builder method. After Toggle: 15 + 1 = 16 methods on PanelBuilder. One over the 15 "comfortable" threshold; within the broader tolerance.

### Convention 5 — No factory methods except direction

Applies. No factories.

### Convention 6 — Vanilla textures for MenuKit defaults

**Applies — first case of pre-identified custom-texture consideration.** Convention 6 noted at its introduction: "Toggle switches are probably custom — there's no vanilla equivalent."

**Design decision: don't ship a custom texture. Render via MenuKit's existing PanelStyle vocabulary:**
- **Off state:** `PanelStyle.RAISED` background (same as Button's default)
- **On state:** `PanelStyle.INSET` background (visually "pressed in" / "sunken")
- **Hover:** translucent highlight overlay (same as Button hover)
- **Disabled:** `PanelStyle.DARK` background with muted visuals

This reuses MenuKit's vanilla-derived panel backgrounds. No custom texture shipped. Convention 6 satisfied through reuse rather than through new assets — resource packs that re-texture vanilla panel sprites automatically re-texture toggles.

---

## Visual legibility — explicit trade-off

RAISED/INSET differentiation is elegant and composable, but it carries a real legibility trade-off. A bare unlabeled Toggle with RAISED styling reads as "button." The same toggle with INSET styling reads as "pre-pressed button." Neither screams "toggle switch" at first glance.

**Phase 8 decision: ship pure RAISED/INSET without an internal indicator.** No dot, no shifting thumb, no colored tint. The toggle is a rectangle whose panel-style differentiates state.

**Reasoning:**

1. **The composition pattern handles legibility.** Consumers almost always want labels next to toggles — `[Toggle] Auto-sort inventory`, `[Toggle] Show tooltips`. Context clarifies the toggle's purpose.

2. **Checkbox is the settings-ready alternative.** For UIs where a conventional "on/off" visual is needed, Checkbox (next element) ships with a built-in label and conventional check-mark visual. Toggle is the primitive; Checkbox is the UX-ready variant. Consumers choose based on their use case.

3. **Decorative additions commit to unproven visual style.** Shipping a small indicator now commits to iOS-switch-like dot, vanilla-style highlight, colored active-tint, or something else — without empirical data on which reads best. Minimalism wins until proven otherwise.

4. **Fallback path is clean.** If in-game testing reveals the visual is too ambiguous, the cheapest response is a subtle refinement: a 2px internal mark, a colored on-state tint, or a small custom sprite. Non-breaking. Can land as a Phase 8 visual-polish patch or as Phase 9 refinement. The commitment cost of shipping minimal first is low.

**Reconsideration trigger.** If in-game testing of Toggle — either by me during Phase 8 or by consumer-mod developers during Phase 11 — reveals that isolated unlabeled toggles are confusingly button-like, add a minimal visual indicator. No hard commitment on which style; whatever reads best empirically.

---

## Visual behavior

| State | Rendering |
|---|---|
| Off | RAISED panel background |
| On | INSET panel background (visually sunken) |
| Hover, off, enabled | RAISED + translucent white highlight overlay |
| Hover, on, enabled | INSET + translucent white highlight overlay |
| Disabled (either state) | DARK panel background; no hover highlight |

No text or icon by default. Toggle is the bare switch. Consumers wanting a labeled toggle either:
- Place a `TextLabel` adjacent to the Toggle (composition)
- Use `Checkbox` instead (shipped pre-labeled)

---

## API

```java
public class Toggle implements PanelElement {

    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle);

    public Toggle(int childX, int childY, int width, int height,
                  boolean initialState,
                  Consumer<Boolean> onToggle,
                  @Nullable BooleanSupplier disabledWhen);

    // PanelElement
    public int getChildX();
    public int getChildY();
    public int getWidth();
    public int getHeight();
    public void render(RenderContext ctx);
    public boolean mouseClicked(double mouseX, double mouseY, int button);

    // Element-specific
    public boolean isOn();         // current state
    public void setOn(boolean);    // programmatic state change (fires onToggle)
    public boolean isDisabled();
    public boolean isHovered();    // last-render hover state
}
```

Internal fields: `childX`, `childY`, `width`, `height`, `state` (mutable), `Consumer<Boolean> onToggle`, `@Nullable BooleanSupplier disabledWhen`, `hovered` (render-frame state, like Button).

### The `setOn(boolean)` programmatic setter

Toggles need programmatic state mutation — a chat command or keybind might want to flip a toggle without clicking it. `setOn(boolean)` sets state and fires `onToggle` if state actually changed (no-op for same-state sets).

This is a second mutable entry point alongside clicks. The callback still fires on programmatic changes, keeping consumer-observed behavior consistent.

### Builder integration

```java
public PanelBuilder toggle(int childX, int childY, int width, int height,
                           boolean initialState,
                           Consumer<Boolean> onToggle);
```

One builder method. Disabled form via `.element(new Toggle(..., disabledWhen))`.

---

## Callback type — `Consumer<Boolean>` not custom `BooleanConsumer`

Java doesn't ship a `BooleanConsumer` in `java.util.function` (it ships `IntConsumer`, `LongConsumer`, `DoubleConsumer` but not boolean). Use `Consumer<Boolean>`.

Click events are rare (per user action), autoboxing overhead is imperceptible, and shipping a new functional interface adds API surface for negligible benefit. Consistent with Button's `Consumer<Button>`.

The palette sketch said `BooleanConsumer` as shorthand; it's not a commitment to ship the functional interface. Checkbox and Radio will use the same `Consumer<Boolean>` pattern for symmetry.

---

## Scope boundary — what Toggle does not do

- **No built-in label.** Composition via adjacent `TextLabel` or use `Checkbox` for a labeled variant.
- **No icon swapping for on/off state.** Phase 9 will ship that as a state-linked-toggle-with-icons variant if consumer demand surfaces.
- **No tri-state.** Boolean only. Three-state controls (on/off/indeterminate) are not supported; consumers needing indeterminate states compose multiple elements or implement `PanelElement` directly.
- **No animation between states.** State changes render immediately. A future "animated toggle" with a sliding thumb would be a Phase 9 or later addition.

---

## Phase 9 composability notes

Phase 9 adds `Toggle.linked(x, y, w, h, BooleanSupplier state, Runnable onToggle)`. This requires factoring Toggle's state-holding and state-rendering logic so that the linked variant can substitute external state for internal state.

**Implementation strategy (pre-committed):**
- Keep `Toggle`'s class non-final and methods non-final (Button precedent — verified in the customization check: Button's methods are public and non-final).
- Phase 9 factors `protected boolean currentState()` that the base class calls from `render` and `mouseClicked`. Default implementation returns the internal field; `Toggle.linked(...)` returns a subclass that overrides `currentState()` to read the supplier.
- `mouseClicked` likewise calls `protected void setState(boolean)` — internal version updates the field, linked version just fires the callback (consumer mutates external state).

This way, the linked variant is a tiny subclass with two overrides. The base Toggle ships clean in Phase 8; Phase 9's factoring is minimal.

Also per the Phase 9 builder-principle: `.linked()` is a factory on Toggle, not a new PanelBuilder method. Consumers use `.element(Toggle.linked(...))`.

---

## Persistence (Phase 9+ concern)

Captured in DEFERRED.md. When `Toggle.linked(...)` lands, documentation addresses persistence head-on — MenuKit does not ship a persistence abstraction; the state-linked pattern is the answer.

---

## Convention refinements (roll forward)

No new refinements. Toggle confirms the conventions hold for the first from-scratch interactive element with the first application of the mutable-state exception:

- Convention 1 applies cleanly.
- Convention 2 correctly doesn't apply — state is not variable content in the data-vs-configuration sense.
- Convention 3 applies partially (interactive; overrides `mouseClicked`; still inherits `isVisible` and `isHovered`).
- Convention 4 applies with one builder method.
- Convention 5 applies (no factories).
- Convention 6 applies — chose reuse-existing-vocabulary path over shipping custom texture. Visual legibility explicitly named as a trade-off with a reconsideration trigger.

The mutable-state exception is scoped, documented, and will be restated in the class javadoc.

---

## Summary

One element. Two constructors. One builder method. ~100 lines of implementation. First exercise of the mutable-state exception. First Phase 8 element designed without a prior draft.

Convention 6's "likely custom" prediction for toggles is revisited and resolved: ship with RAISED/INSET panel styles (reusing existing MenuKit visuals) rather than a custom texture. Visual legibility for isolated unlabeled toggles is an explicit Phase 8 trade-off with a clean reconsideration path.

File: `menukit/src/main/java/com/trevorschoeny/menukit/core/Toggle.java`.

**Approved 2026-04-14.** Implementation proceeds.
