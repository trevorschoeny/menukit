# Tooltip — design doc

**Element purpose.** Contextual information displayed to the user. Two forms per the palette:
- **Form A (builder property)**: `.tooltip(...)` method on interactive elements (Button, Toggle, Checkbox, Radio, Icon). On hover, a vanilla-styled tooltip renders at the mouse position.
- **Form B (standalone element)**: `new Tooltip(x, y, text)` — a persistent info box rendered at a declared panel position. Auto-sizes to content.

**Works in input-dispatch contexts.** Form A requires hover detection (inventory menus and standalone screens). Form B renders in all three contexts but is uncommon on HUDs.

---

## Scope commitments

**Ships in Phase 8:**
- Form A on: **Button, Toggle, Checkbox, Radio, Icon** — five elements gain `.tooltip(...)` setter methods.
- Form B: standalone `Tooltip` element with fixed and supplier constructors.
- Builder method for standalone Tooltip.
- Vanilla-tooltip-style rendering for Form A (via `graphics.renderTooltip` which defers to end-of-frame).
- Simple RAISED panel background for Form B (MenuKit visual vocabulary).

**Defers to Phase 9 or later:**
- Form A on TextLabel, Divider, ItemDisplay, ProgressBar (no consumer pressure; render-only elements).
- ItemDisplay showing vanilla item tooltip on hover (different mechanism).
- Tooltip positioning customization beyond "at mouse for Form A, at declared position for Form B."
- Multi-line rich tooltip support beyond what vanilla's `renderTooltip` handles natively.

---

## Conventions pressure-test

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

**Form B (standalone element)** uses Convention 1's auto-sizing form (like Checkbox):

```java
new Tooltip(x, y, Component text);
new Tooltip(x, y, Supplier<Component> text);
```

Auto-sizes from the text content. Width = `fontWidth(text) + 2 * padding`; height = `fontLineHeight + 2 * padding`.

**Form A (builder property)** isn't a constructor call — it's a setter method on existing elements. Convention 1 doesn't directly apply.

### Convention 2 — Supplier variants for variable content

Applies to both forms. **Both fixed and supplier forms ship** across both forms — no supplier-only variant blessed here. Keeping Convention 2 uniformly applied across all elements; future designers don't cite Tooltip as precedent for skipping the fixed form.

**Form A setters (on each of Button, Toggle, Checkbox, Radio, Icon):**
```java
public SELF tooltip(Component text);             // fixed
public SELF tooltip(Supplier<Component> text);   // supplier
```

Many tooltips are static ("Enable auto-sort," "Toggle debug mode," "Refresh list"). Forcing consumers to wrap literals in lambdas would be a small but real ergonomic cost. Java's overload resolution distinguishes `Component` from `Supplier<Component>` unambiguously.

**Form B (standalone element):**
```java
new Tooltip(x, y, Component text);             // fixed
new Tooltip(x, y, Supplier<Component> text);   // supplier
```

Standard two-form ship.

### Convention 3 — Render-only inherits defaults

Form B (standalone Tooltip) is render-only. Inherits all defaults.

Form A doesn't add new PanelElement methods — it adds setters on interactive elements. No impact on Convention 3.

### Convention 4 — One builder method per element

Form B: one builder method, `.tooltip(x, y, Component text)`. Supplier variant via `.element(new Tooltip(x, y, supplier))`.

Form A: no new builder method. The `.tooltip(...)` setter is on the individual element, reached via `.element(new Button(...).tooltip(...))`.

One builder method added. After Tooltip: 18 + 1 = 19 methods on PanelBuilder. Final Phase 8 count. Four past the original comfortable threshold; pocketed for Phase 12 evaluation.

### Convention 5 — No factory methods except direction

Applies. No factories.

### Convention 6 — Vanilla textures for MenuKit defaults

**Form A: uses vanilla tooltip rendering directly.** `graphics.renderTooltip(...)` internally uses vanilla's tooltip sprite assembly via `TooltipRenderUtil` or equivalent. Resource packs retexturing vanilla tooltips automatically retexture Form A tooltips. Convention 6 satisfied by reuse at the API level.

**Form B: uses `PanelStyle.RAISED` background** — same reuse pattern as Toggle and other elements. No custom texture; MenuKit's visual vocabulary via existing panel styles. Distinct visual from Form A — Form B is a fixed-position info box, not a mouse-follower.

---

## Form A: hover-triggered tooltips on interactive elements

### Pattern

Each tooltip-supporting element gains:
- A private `@Nullable Supplier<Component> tooltipSupplier` field
- Two public setters for method chaining: `.tooltip(Component)` and `.tooltip(Supplier<Component>)`, each returning the element's own type
- At render time, if hovered and tooltip is set and input dispatch is present: call `graphics.renderTooltip(font, comp, mouseX, mouseY)`

Five elements affected: Button, Toggle, Checkbox, Radio, Icon.

The fixed-form setter wraps in `() -> text` and delegates to the supplier form. Same internal representation as other elements' supplier handling.

### Z-ordering

Vanilla 1.21.11's `GuiGraphics.renderTooltip` defers the actual tooltip render to end-of-frame via a deferred-tooltip mechanism. The element calls renderTooltip inside its own `render()`; vanilla executes the tooltip draw after all other rendering in the frame, including vanilla items. Correct z-ordering handled automatically.

### Icon — the one architectural note

Icon is render-only in Phase 8 (no `mouseClicked`). Adding tooltip support requires hover detection, which Icon didn't previously track.

**Extension:** Icon gains a transient `hovered` field updated each frame in `render()`. No `mouseClicked` override added — Icon remains non-interactive.

**Documented in Icon's class javadoc:** *"Icon tracks hover state internally to support hover-triggered tooltips (via `.tooltip(...)`). Hover state is transient (recomputed each frame) and does not affect Icon's structural contract as a render-only element."*

Makes the architectural shift explicit; future readers see hover tracking on Icon without concluding it's interactive.

### Disabled elements

A disabled Button (or Toggle, Checkbox, Radio) still shows its tooltip on hover. Tooltips are informational; showing them on disabled elements can explain why the element is disabled. Phase 8 default; Phase 9 can revisit if in-game testing reveals this is wrong.

### Configuration setter, not state mutator

The `.tooltip(...)` setter sets a field post-construction. This is configuration, not state. Documented in the convention refinement below.

---

## Form B: standalone Tooltip element

### Visual

RAISED panel background sized to text + padding. Text rendered left-aligned inside. Distinct from Form A's vanilla-tooltip-style — Form B is a MenuKit info box, not a mouse-follower tooltip.

| Aspect | Value |
|---|---|
| Background | `PanelStyle.RAISED` |
| Padding | 4 px on all sides |
| Text color | `0xFF404040` (MenuKit's stabilizing default text-on-panel color) |
| Text shadow | none |
| Auto-size width | `fontWidth(text) + 2 * padding` |
| Auto-size height | `fontLineHeight + 2 * padding` |

### API

```java
public class Tooltip implements PanelElement {

    public static final int PADDING = 4;
    public static final int DEFAULT_TEXT_COLOR = 0xFF404040;

    public Tooltip(int childX, int childY, Component text);
    public Tooltip(int childX, int childY, Supplier<Component> text);

    // PanelElement — auto-sizes from text
    public int getChildX();
    public int getChildY();
    public int getWidth();   // fontWidth(text) + 2 * PADDING
    public int getHeight();  // fontLineHeight + 2 * PADDING
    public void render(RenderContext ctx);
}
```

Builder:
```java
public PanelBuilder tooltip(int childX, int childY, Component text);
```

Dynamic-width rule inherited per Checkbox — documented in Tooltip's class javadoc.

---

## Form A API additions (each tooltip-supporting element)

Each of Button, Toggle, Checkbox, Radio, Icon gains:

```java
// Private field
private @Nullable Supplier<Component> tooltipSupplier;

/**
 * Attaches a hover-triggered tooltip with fixed text. Returns this element
 * for method chaining. Tooltip renders at the mouse position using vanilla's
 * tooltip styling; resource packs restyling vanilla tooltips restyle this
 * tooltip automatically.
 */
public SELF tooltip(Component text) {
    return tooltip(() -> text);
}

/**
 * Attaches a hover-triggered tooltip with supplier-driven text. The supplier
 * is invoked each frame while hovered. Returns this element for method chaining.
 */
public SELF tooltip(Supplier<Component> supplier) {
    this.tooltipSupplier = supplier;
    return (SELF) this;
}
```

Render-body addition (in each element, after existing visual rendering):
```java
if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
    var comp = tooltipSupplier.get();
    if (comp != null) {
        ctx.graphics().renderTooltip(Minecraft.getInstance().font, comp,
                ctx.mouseX(), ctx.mouseY());
    }
}
```

The `ctx.hasMouseInput()` check prevents tooltip rendering on HUDs (where `mouseX = -1`).

---

## Scope boundary — what Tooltip does not do

- **Form A: no custom positioning.** Renders at current mouse position (vanilla behavior).
- **Form A: no style customization.** Uses vanilla tooltip styling. For custom styles, consumers implement PanelElement directly.
- **Form B: no multi-line beyond what vanilla `Component` rendering supports.**
- **No dismissal control.** Form A shows while hovered, disappears when not. No sticky variant.
- **No delay.** Shows immediately on hover, matching vanilla behavior.
- **No interactivity.** Display-only. Consumers cannot click through or interact with tooltip content.
- **No tooltip on non-interactive Phase 8 elements (TextLabel, Divider, ItemDisplay, ProgressBar).** Deferred to Phase 9 if consumer demand surfaces.

---

## Convention refinements (roll forward)

One new refinement worth capturing. The Form A supplier-only idea from the draft is explicitly rejected (see Convention 2 above); not blessed.

**Post-construction configuration setters:**

*"Elements may expose post-construction configuration setter methods for optional, orthogonal features (e.g., `.tooltip(...)`). Setters are intended to be called exactly once during the construction chain before the element is added to a panel. They do not fire callbacks or trigger re-renders; they are not runtime state mutators. Setters return `this` for method chaining. The type system does not enforce single-call intent; consumers respect it by convention. Features central to the element's behavior belong in the constructor, not as setters."*

The "optional, orthogonal features" qualifier prevents the setter pattern from being abused for primary features. Tooltip qualifies (optional, orthogonal to the element's primary purpose). Element-core configuration — Button's `onClick`, Toggle's `initialState` — does not qualify and must be in the constructor.

Only Tooltip's `.tooltip(...)` exercises this in Phase 8. Future optional-feature setters would cite this refinement.

---

## Pockets for Phase 12

- **`StyleDefaults` consolidation.** Default text color `0xFF404040` now appears in Divider, Checkbox, Radio, Tooltip Form B. A shared `StyleDefaults` class consolidating these into named constants is Phase 12 cleanup. Not Phase 8 scope.
- **Builder-method consolidation.** PanelBuilder ends Phase 8 at ~19 methods. Whether any consolidation is worth doing (sub-builders for related elements?) is Phase 12 evaluation. Current reading: 19 is acceptable; no hard need to consolidate.

---

## Summary

The largest Phase 8 element by scope:
- One new element class (`Tooltip` for Form B).
- Five elements gain `.tooltip(...)` setter methods — both fixed and supplier forms on each (Form A).
- One small hover-tracking extension to Icon with explicit javadoc noting the architectural shift.
- One new builder method.

Convention 6 satisfied through vanilla rendering reuse (Form A) and PanelStyle reuse (Form B). One narrow convention refinement (post-construction configuration setters for optional orthogonal features).

Form A both-forms per Convention 2; no supplier-only precedent. Consistency with every other element holds.

Files:
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Tooltip.java` (new)
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Button.java` (modified — tooltip setters)
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Toggle.java` (modified — tooltip setters)
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Checkbox.java` (modified — tooltip setters)
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Radio.java` (modified — tooltip setters)
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Icon.java` (modified — hover tracking + tooltip setters + javadoc)
- `menukit/src/main/java/com/trevorschoeny/menukit/screen/MenuKitScreenHandler.java` (modified — builder method)

**Approved 2026-04-14.** Implementation proceeds.
