# Checkbox — design doc

**Element purpose.** A two-state boolean control rendered as a small square with a check mark indicator and an adjacent label. The settings-ready convention for boolean interactions — pre-composed with a label, conventional visual.

**Related to Toggle.** Checkbox shares Toggle's mutable-state exception (element owns a boolean, clicks flip it, callback fires on change). It's a separate element class, not a Toggle subclass — the render and the auto-sizing behavior diverge enough that sibling classes read clearer than inheritance.

**Works in input-dispatch contexts.** Inventory menus and standalone screens. Renders on HUDs but has no click behavior there per the HUD render-only doctrine.

---

## Conventions pressure-test

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

**Content-sizing case — first material application.** Checkbox doesn't take width/height at construction; the element sizes from its label. The checkbox square is fixed at a constant size (10×10 px); the label extends to the right with 4px padding; total width = `10 + 4 + fontWidth(label)`; total height = `max(10, fontLineHeight)` = 10.

```java
new Checkbox(x, y, boolean initialState, Component label, Consumer<Boolean> onToggle);
```

Confirms Convention 1's auto-sizing form works. `TextLabel`, `Tooltip` will follow the same pattern.

### Convention 2 — Supplier variants for variable content (data-vs-configuration)

Applies. Label content is variable content; ship both fixed and supplier forms as overloaded constructors.

```java
new Checkbox(x, y, initial, Component label, onToggle);
new Checkbox(x, y, initial, Supplier<Component> label, onToggle);
```

`initialState` and `onToggle` are not subject to supplier variants — initial state is configuration (fixed at construction), callback is not content.

### Convention 3 — Render-only inherits defaults

**Partially applies — Checkbox is interactive.** Overrides `mouseClicked` with Toggle-like behavior. Inherits `isVisible` and `isHovered` defaults.

### Convention 4 — One builder method per element

Applies. `.checkbox(x, y, initialState, Component label, Consumer<Boolean> onToggle)` — the common fixed-label, no-disabled form. Supplier-label and disabled variants via `.element(new Checkbox(...))`.

One builder method. After Checkbox: 16 + 1 = 17 methods on PanelBuilder. Two over the comfortable threshold. Continuing to accept the drift; Checkbox, Radio, Tooltip each add one builder method; final count ~20.

### Convention 5 — No factory methods except direction

Applies. No factories.

### Convention 6 — Vanilla textures for MenuKit defaults

**Applies. Vanilla sprite verified and used: `icon/checkmark`.**

Before committing to drawn-pixel rendering, verified vanilla's texture atlas for a usable check-mark sprite. Found `assets/minecraft/textures/gui/sprites/icon/checkmark.png` — a 9×8 RGBA check-mark sprite used by vanilla's advancement and recipe-book interfaces. Perfect fit for the 10×10 checkbox interior (center-aligned with 0.5px horizontal and 1px vertical padding).

**Decision: use `Identifier.withDefaultNamespace("icon/checkmark")` via `blitSprite`.** Rendered as 9×8 inside the 10×10 INSET background, positioned at `(boxX, boxY + 1)` — horizontally flush-left within the 1px right margin, vertically centered.

**Benefits over drawn pixels:**
- Resource packs re-texture MenuKit checkboxes automatically (Faithful, Bare Bones, etc.)
- Matches vanilla's own check-mark aesthetic exactly — users get the same visual across advancement screens, recipe book, and MenuKit UIs
- No custom pixel pattern to maintain or generalize for different sizes
- Zero shipped assets — uses vanilla's atlas

Convention 6 properly satisfied. The earlier "drawing 8 pixels is overkill for a sprite" intuition was wrong; what was actually overkill was skipping the sprite-verification step.

---

## Sharing Toggle's mutable-state exception

Checkbox owns a mutable boolean like Toggle. Same architectural justification (state changes don't affect structural shape) applies. The class javadoc references the Toggle class doc for the full rationale rather than duplicating it — "See `Toggle` for the mutable-state exception to the declared-structure discipline."

---

## Visual behavior

| State | Rendering |
|---|---|
| Unchecked | INSET square (10×10) + label text |
| Checked | INSET square + `icon/checkmark` sprite (9×8, centered) + label text |
| Hover (either state), enabled | INSET square + translucent white highlight overlay on the square + label |
| Disabled (either state) | DARK square background; label text in muted color |

Label text color: dark gray (`0xFF404040`) by default — matches vanilla inventory-label color, reads against light panel backgrounds.

Clicking anywhere within the element bounds (square OR label area) toggles the state — matches HTML/native checkbox convention.

---

## Dynamic-width edge case (general rule)

When label is supplied via `Supplier<Component>` and the supplier returns different text each frame (different word lengths), the element's width changes per frame. Panel layout isn't re-resolved per frame — panel size was computed at layout time from that frame's element widths.

**General rule for auto-sizing elements with supplier-based variable content:**

*Auto-sizing elements with supplier-based variable content cannot guarantee layout stability. Consumers needing stable layout should use fixed-content variants or ensure supplier returns same-width content.*

This rule propagates to TextLabel (when its Phase 9 supplier variant lands) and Tooltip. Each auto-sizing element with a supplier variant documents the rule in its own javadoc.

**Practical consequence for consumers.** In inventory-menu panels, dynamic-label checkboxes may overlap with adjacent elements if the label grows beyond its initial width. Two mitigations: reserve enough width in layout for the longest expected label, or use fixed-label variants whose text doesn't vary.

**Where the rule lives:** in Checkbox's class javadoc (not just this design doc). A consumer reaching for the supplier variant should find the limitation in the class documentation, not discover it in production.

---

## API

```java
public class Checkbox implements PanelElement {

    /** Size of the checkbox square, in pixels. */
    public static final int BOX_SIZE = 10;

    /** Horizontal gap between the checkbox square and the label text. */
    public static final int LABEL_GAP = 4;

    /** Vanilla check-mark sprite used for the checked state. */
    public static final Identifier CHECKMARK_SPRITE =
            Identifier.withDefaultNamespace("icon/checkmark");

    // ── Constructors ─────────────────────────────────────────────────
    public Checkbox(int childX, int childY, boolean initialState,
                    Component label, Consumer<Boolean> onToggle);

    public Checkbox(int childX, int childY, boolean initialState,
                    Supplier<Component> label, Consumer<Boolean> onToggle);

    public Checkbox(int childX, int childY, boolean initialState,
                    Component label, Consumer<Boolean> onToggle,
                    @Nullable BooleanSupplier disabledWhen);

    public Checkbox(int childX, int childY, boolean initialState,
                    Supplier<Component> label, Consumer<Boolean> onToggle,
                    @Nullable BooleanSupplier disabledWhen);

    // ── PanelElement ────────────────────────────────────────────────
    public int getChildX();
    public int getChildY();
    public int getWidth();   // BOX_SIZE + LABEL_GAP + fontWidth(label)
    public int getHeight();  // BOX_SIZE

    public void render(RenderContext ctx);
    public boolean mouseClicked(double mouseX, double mouseY, int button);

    // ── State ───────────────────────────────────────────────────────
    public boolean isChecked();
    public void setChecked(boolean checked);  // fires onToggle on change
    public boolean isDisabled();
    public boolean isHovered();
}
```

**No pre-committed Phase 9 factoring hooks.** Phase 9 will add `Checkbox.linked(...)` at the time it's built, factoring protected `currentState()`/`applyState()` methods then. Phase 8 ships only what Phase 8 uses; speculative hooks don't ship. Class is non-final and methods are non-final (same as Button/Toggle), so Phase 9's refactoring path stays open.

Builder additions:
```java
public PanelBuilder checkbox(int childX, int childY, boolean initialState,
                             Component label,
                             Consumer<Boolean> onToggle);
```

---

## Scope boundary — what Checkbox does not do

- **No tri-state (indeterminate)** — boolean only.
- **No configurable check-mark sprite** — uses vanilla's `icon/checkmark`. Consumers wanting a different mark implement PanelElement directly.
- **No animation on state change** — instant toggle rendering.
- **No label formatting beyond `Component`** — labels are rendered with vanilla font, dark-gray color, no shadow.
- **No configurable checkbox size in Phase 8** — fixed at 10×10, sized to match the 9×8 vanilla check-mark sprite. If a consumer needs a larger checkbox, they use `.element(...)` with their own PanelElement.

---

## Implementation notes

- `CHECKMARK_SPRITE` as a named constant (rather than inlined in render code) — matches `Divider.DEFAULT_COLOR` pattern.
- Label default color (`0xFF404040`) matches Divider's default — pocketed for Phase 12 as a potential shared `ColorDefaults` or `PanelStyle`-level constant. Not Phase 8 cleanup scope.
- Sprite positioning: `(boxX + 0, boxY + 1)` — flush-left with 1px right margin, 1px top and bottom margins. Vanilla's `icon/checkmark` is 9×8, so this centers acceptably in a 10×10 box.

---

## Convention refinements (roll forward)

No new refinements. Checkbox exercises:

- Convention 1 auto-sizing form (first material application — content derives width).
- Convention 2 both-forms for label (variable content) with documented dynamic-width rule.
- Convention 3 partial application (interactive).
- Convention 4 single builder method.
- Convention 5 no factories.
- Convention 6 — *vanilla sprite verified and used*. Correct application of the preference after empirical check.

The Toggle mutable-state exception extends cleanly to Checkbox.

One rule makes it into general doctrine via Checkbox's javadoc: *auto-sizing elements with supplier-based variable content cannot guarantee layout stability.* Future elements inherit it.

---

## Summary

Roughly 120 lines of implementation. Fixed 10×10 checkbox square with vanilla `icon/checkmark` sprite; auto-sizing total width from label; four constructor overloads (fixed label × optional disabled, supplier label × optional disabled); one builder method.

First material application of Convention 1's auto-sizing form. First verified vanilla-sprite use for Convention 6. Second application of the mutable-state exception.

File: `menukit/src/main/java/com/trevorschoeny/menukit/core/Checkbox.java`.

**Approved 2026-04-14.** Implementation proceeds.
