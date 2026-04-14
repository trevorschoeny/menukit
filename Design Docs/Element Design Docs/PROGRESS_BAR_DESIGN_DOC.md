# ProgressBar — design doc

**Element purpose.** Renders a filled bar driven by a 0-to-1 progress value. Configurable fill color, background color, direction, and optional label. The "bounded-progress indicator" primitive.

**Generalization from existing code.** MKHudBar exists as the HUD-specific draft. Phase 8 promotes it to core/ and deletes the HUD-specific class. The HUD builder's `.bar(...)` method and `BarBuilder` sub-builder retarget to produce core ProgressBar.

**Works in all three contexts.** Render-only.

---

## Conventions pressure-test

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

Applies. ProgressBar is rectangular — width and height are independent (horizontal bar is wide-short, vertical bar is narrow-tall). Standard shape:

```java
new ProgressBar(x, y, width, height, value);
new ProgressBar(x, y, width, height, value, direction, fillColor, bgColor, label);
```

### Convention 2 — Supplier variants for variable content (data-vs-configuration)

Applies. `value` is data — the bar's current state. Ship both fixed and supplier forms, consistent with Icon and ItemDisplay:

```java
new ProgressBar(x, y, w, h, 0.75f);               // fixed value
new ProgressBar(x, y, w, h, () -> gameProgress);  // supplier
```

`fillColor`, `bgColor`, `direction` are configuration — fixed at construction. The `label` is the interesting case: whether to show a label is configuration, but the label's content is often variable (`"50%"`, `"3/10 mobs"`). Label ships as a nullable `Supplier<Component>` — null means no label; non-null means label-with-dynamic-content.

Mirrors the ItemDisplay pattern where item count overlay is split: whether to show is config, the count value is data.

### Convention 3 — Render-only inherits defaults

Applies. Standard overrides only.

### Convention 4 — One builder method per element (refined)

**Partial application.** ProgressBar has complex full-form configuration (9 constructor parameters for the full form). Shipping four builder overloads (supplier + fixed × common + full) would add 4 methods to PanelBuilder. After ProgressBar, PanelBuilder would be at 17 methods — over the comfortable threshold.

**Refinement to Convention 4 (with trigger):**

*Elements with four or more configuration parameters beyond position, dimensions, and primary content ship common-case builder overloads only. Full configuration via `.element(new Element(...))`. The threshold is soft — elements close to the line can go either way based on how discoverable the configuration needs to be.*

This prevents the refinement from being applied capriciously to elements with two or three configuration parameters, where the full overload is still reasonable. Only elements with substantial configuration clusters route full-form construction through `.element(...)`.

ProgressBar has four configuration parameters beyond position/dimensions/value (direction, fillColor, bgColor, label) — the threshold applies. Ship two builder methods:

```java
panelBuilder.progressBar(x, y, w, h, float value);             // common fixed
panelBuilder.progressBar(x, y, w, h, Supplier<Float> value);   // common supplier
```

Consumers wanting direction/colors/label on PanelBuilder use `.element(new ProgressBar(x, y, w, h, value, direction, fillColor, bgColor, label))`. Two builder methods total (15 on PanelBuilder after ProgressBar; at threshold).

HUD builder retains its existing `BarBuilder` sub-builder for chained configuration — HUDs get the rich API; inventory-menu panels use the common-case shortcut or `.element(...)`.

Only ProgressBar exercises this refinement in Phase 8. Toggle, Checkbox, Radio, and Tooltip ship standard 2-method builder overloads since they're below the four-parameter threshold.

### Convention 5 — No factory methods except direction

Direction enum stays as a constructor parameter, not a factory. Unlike Divider where horizontal vs vertical is a single-axis choice, ProgressBar has four directions (LTR/RTL/TTB/BTT). Four factory methods would expand Convention 5's "direction factories" exception from a single pair to a quartet per direction-enum-bearing element. Better to keep Divider as the one exception.

Direction is an enum passed to the constructor. Default is `LEFT_TO_RIGHT` on the common constructor.

### Convention 6 — Vanilla textures for MenuKit defaults

**N/A for rendering, like Divider.** ProgressBar renders via `graphics.fill()` — no textures. Solid-color fills auto-adapt to nothing (they're not textured) but that's correct: they're consistent across resource packs without any special handling.

Vanilla's experience/boss bars are sprite-based but use colors specific to their context. Using them as ProgressBar's default would bake the experience-bar aesthetic into every MenuKit progress bar, which is wrong for the general primitive. Fill-based rendering is the right default.

A sprite-backed variant could ship in Phase 9 or later if consumer demand surfaces; Phase 8's ProgressBar is color-configurable fills.

---

## Existing MKHudBar features audit — keep vs. drop

| Feature | Decision | Reasoning |
|---|---|---|
| `Supplier<Float>` value | Keep (supplier + fixed forms) | Per Convention 2 |
| `barWidth`, `barHeight` | Keep | Rectangular element; Convention 1 fits |
| `fillColor`, `bgColor` | Keep | Configuration |
| `Direction` enum | Keep | Four directions; constructor parameter |
| Nullable `Supplier<Component>` label | Keep | Optional feature; config-vs-data split |
| `onRender` callback | **Drop** | No verified consumer need; same reasoning as ItemDisplay |

Same `onRender` drop as ItemDisplay. Library-minimalism discipline.

---

## Default colors

MKHudBar's existing defaults: `fillColor = 0xFFFFFFFF` (white), `bgColor = 0xFF333333` (dark gray). Retained — functional for most use cases and consumer-overridable.

---

## API

```java
public class ProgressBar implements PanelElement {

    /** Fill direction for the progress bar. */
    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM
    }

    /** Default fill color (white). */
    public static final int DEFAULT_FILL_COLOR = 0xFFFFFFFF;

    /** Default background color (dark gray). */
    public static final int DEFAULT_BG_COLOR = 0xFF333333;

    /** Default direction (left-to-right). */
    public static final Direction DEFAULT_DIRECTION = Direction.LEFT_TO_RIGHT;

    // ── Common constructors ───────────────────────────────────────────
    public ProgressBar(int childX, int childY, int width, int height, float value);
    public ProgressBar(int childX, int childY, int width, int height, Supplier<Float> value);

    // ── Full-control constructors ─────────────────────────────────────
    public ProgressBar(int childX, int childY, int width, int height,
                       float value,
                       Direction direction, int fillColor, int bgColor,
                       @Nullable Supplier<Component> label);
    public ProgressBar(int childX, int childY, int width, int height,
                       Supplier<Float> value,
                       Direction direction, int fillColor, int bgColor,
                       @Nullable Supplier<Component> label);

    // ── PanelElement ────────────────────────────────────────────────
    public int getChildX();
    public int getChildY();
    public int getWidth();
    public int getHeight();
    public void render(RenderContext ctx);

    // ── Queries ──────────────────────────────────────────────────────
    public float getCurrentValue();  // resolves supplier, clamped [0,1]
    public Direction getDirection();
    public int getFillColor();
    public int getBgColor();
}
```

Builder additions:
```java
public PanelBuilder progressBar(int childX, int childY, int width, int height, float value);
public PanelBuilder progressBar(int childX, int childY, int width, int height, Supplier<Float> value);
```

Two methods. Full configuration via `.element(new ProgressBar(...))`.

---

## Implementation notes

Two small things to preserve explicitly during implementation.

**Label positioning.** MKHudBar's current render centers the label on the bar — horizontal-center, vertical-center of the bar's bounds. Preserve this exactly; document it in the javadoc. For vertical bars the label is still rendered centered on the bar's 2D bounds (not rotated). Consumers who want a label above/below a vertical bar position a separate `TextLabel` alongside the bar.

**Silent clamping.** Values outside [0, 1] clamp to the boundary. No exception, no warning. This is the right behavior — exceptions on progress values would be noisy and progress computations sometimes legitimately overshoot (e.g., timer ticks that briefly exceed duration before reset). Document explicitly in the javadoc: *"Values outside [0, 1] are clamped silently. A value of 1.5f renders as a full bar; a value of -0.5f renders as empty."* Consumers debugging unexpected display behavior benefit from the explicit documentation.

---

## HUD builder migration

`MKHudPanel.Builder.bar(x, y, w, h)` returns `BarBuilder` (sub-builder for chained config) — retargets to build a `ProgressBar` instead of `MKHudBar`. Consumer-facing chained API unchanged.

After this lands, `MKHudBar.java` is deleted along with its nested `Direction` enum (the new canonical location is `ProgressBar.Direction`).

Note: Direction is moving from `MKHudBar.Direction` to `ProgressBar.Direction`. Any consumer importing `MKHudBar.Direction` would break — but zero consumers use `.bar()` builder at all, and zero consumers reference `MKHudBar.Direction` directly. Safe rename.

---

## Scope boundary — what ProgressBar does not do

- **No animation.** Value changes render immediately per frame. Consumers wanting animated fills (ease-in/out when value changes) wrap the `Supplier<Float>` with their own interpolation logic.
- **No sprite-backed rendering.** Phase 8 ships fills only. A sprite-backed variant would be a Phase 9 specialization if consumer demand surfaces.
- **No multi-segment bars** (e.g., a health bar split into 10 hearts). Consumers wanting segmented displays compose multiple ProgressBars with different color fills or implement a custom `PanelElement`.
- **No percentage formatting for the label.** The label supplier returns whatever text the consumer provides. "50%" vs "5/10" vs "HALF" is all consumer formatting.

---

## Convention refinements (roll forward)

**Convention 4 refinement with trigger:** elements with four or more configuration parameters beyond position, dimensions, and primary content ship common-case builder overloads only; full configuration via `.element(new Element(...))`. Soft threshold. Only ProgressBar exercises this in Phase 8.

**Convention 6 reconfirmation:** pure-fill rendering is N/A to texture-choice preference (same pattern as Divider).

Subsequent elements (Toggle, Checkbox, Radio, Tooltip) are below the four-parameter configuration threshold and will ship standard 2-method builder overloads.

---

## Phase 9 / later notes

- **Sprite-backed variant.** If a consumer mod needs an experience-bar-styled progress bar, Phase 9 could add `ProgressBar.sprite(...)` factory or a subclass. Reconsider when real demand surfaces.
- **Animated variant.** Same — if consumers hand-roll smooth-fill interpolation repeatedly, Phase 9 could ship a "smooth" variant.

Neither is Phase 8 scope. Both are reopen-triggers.

---

## Summary

Three convention confirmations: 1, 2, 3 apply cleanly.

Two convention refinements:
- **Convention 4**: complex-configuration elements (≥4 config params) ship builder common forms only; full config via `.element(...)`. Keeps builder count manageable.
- **Convention 6**: reconfirms that pure-fill rendering is N/A to texture-choice preference.

One feature drop: `onRender` callback (no verified consumer).

File: `menukit/src/main/java/com/trevorschoeny/menukit/core/ProgressBar.java`, ~130 lines. Plus deletion of `MKHudBar.java` and HUD builder retargeting.

**Approved 2026-04-14.** Implementation proceeds.
