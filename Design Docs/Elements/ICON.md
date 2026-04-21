# Icon — design doc

**Element purpose.** Renders a sprite (texture atlas identifier) at a fixed position and size. No interaction. Composable as the render portion of icon-only Buttons (Phase 9) and state-indicating Toggles (Phase 9). The foundational "show a picture" primitive.

**Works in all three contexts** (inventory menus, HUDs, standalone screens). Per the thesis: context-agnostic element.

---

## Conventions established by this element

These are the conventions subsequent elements follow. Named explicitly because Icon is first.

### Convention 1 — Constructor shape

Positional arguments in the order: `(childX, childY, [width, height,] content, [callback])`. Dimensions are present for elements whose size isn't derivable from content and absent for elements that size themselves from content (Checkbox, Tooltip).

Reading order: where, how big (if variable), what, and what-happens-on-interaction.

### Convention 2 — Supplier variants via constructor overloads (data vs. configuration)

Every element with variable content (the element's current state — text, sprite, item stack, progress value) exposes a fixed form and a supplier form as overloaded constructors:

```java
new Icon(x, y, 16, 16, MY_SPRITE);              // fixed
new Icon(x, y, 16, 16, this::currentSprite);    // supplier (state-driven)
```

Java resolves the overload by type — `Identifier` vs `Supplier<Identifier>` are unambiguous. Internal representation wraps the fixed form in a `() -> identifier` closure — one allocation at construction time, not per frame — so the render path is uniform.

**Data vs. configuration:** Supplier variants apply to *variable content* (the element's current state). *Configuration of the element's shape* (e.g., ProgressBar's direction and colors, Toggle's initial state on the Toggle-owns-state path) is fixed at construction and does not receive supplier variants.

### Convention 3 — Render-only elements inherit defaults

Icon implements only `getChildX`, `getChildY`, `getWidth`, `getHeight`, and `render(RenderContext)`. It does not override `isVisible()` (default true), `mouseClicked(...)` (default false), or `isHovered(ctx)` (default using own bounds).

This applies to all render-only elements. Interactive elements override `mouseClicked`.

### Convention 4 — Builder integration as one method per element

`PanelBuilder` gets a `.icon(...)` method that mirrors the Icon constructors. One method name per element type. Overloads for fixed vs. supplier variants.

```java
panelBuilder.icon(x, y, w, h, MY_SPRITE);                  // fixed
panelBuilder.icon(x, y, w, h, this::currentSprite);        // supplier
```

### Convention 5 — No factory methods except direction-choice cases

Constructors are the primary API. Factory methods are reserved for cases where the constructor would require a meaningless enum parameter — Divider's `horizontal(...)` / `vertical(...)` is the one planned case. Icon has no direction, no factory method.

### Convention 6 — Vanilla textures for MenuKit defaults

Whenever MenuKit needs a sprite for its own internal rendering (default visuals shipped by the library — divider lines, checkbox marks, radio selectors, progress-bar fills, notification edges), prefer vanilla texture atlas identifiers. Be clever about compositing; ship custom textures only when vanilla doesn't provide something reusable (e.g., Toggle switches likely require custom).

Icon itself takes whatever identifier the consumer passes — the convention governs MenuKit's own default visuals, not what consumers render through Icon.

---

## API

```java
public class Icon implements PanelElement {
    public Icon(int childX, int childY, int width, int height, Identifier sprite);
    public Icon(int childX, int childY, int width, int height, Supplier<Identifier> sprite);

    // PanelElement
    public int getChildX();
    public int getChildY();
    public int getWidth();
    public int getHeight();
    public void render(RenderContext ctx);

    // Element-specific
    public Identifier getCurrentSprite();  // resolves supplier if present
}
```

Render uses `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, originX+childX, originY+childY, width, height)`.

Builder additions:
```java
public PanelBuilder icon(int childX, int childY, int width, int height, Identifier sprite);
public PanelBuilder icon(int childX, int childY, int width, int height, Supplier<Identifier> sprite);
```

---

## Scope boundary — what Icon does not do

- **No automatic sizing.** Sprite dimensions are explicit constructor parameters.
- **No tint, alpha, or color modulation.** Consumers who need tinted sprites implement `PanelElement` directly.
- **No hover/active state rendering.** Icon is render-only.
- **No animation.** A `Supplier<Identifier>` can return different sprites per frame for flip-book animation, but Icon itself doesn't own any timing — consumers drive timing.

---

## Phase 9 composability notes

Icon is the render portion of the Phase 9 icon-only Button variant and the Phase 9 state-linked Toggle icon swap. Its API is self-contained and supports either composition path — the `Supplier<Identifier>` variant directly covers state-driven icon swap.

Phase 9 work: factor Button's render into protected `renderBackground()` / `renderContent()` hooks; `Button.icon(...)` returns a subclass that overrides `renderContent` to paint a centered Icon instead of centered text. Consumer-side subclasses of Button get the same hooks for free as a side effect.

---

## Summary of convention claims

After reviewing the Icon design, these are the conventions locked for Phase 8:

1. **Constructor shape:** `(childX, childY, [width, height,] content, [callback])` — dimensions optional when content-sizing.
2. **Supplier variants:** overloaded constructors, fixed and `Supplier<T>` forms for *variable content*; configuration stays fixed.
3. **Render-only defaults:** inherit `isVisible`, `mouseClicked`, `isHovered` defaults.
4. **Builder integration:** one method name per element, with overloads for fixed/supplier.
5. **No factory methods** except where constructor-selection would require a meaningless enum (Divider only).
6. **Vanilla textures** for MenuKit's own default visuals; consumer-supplied identifiers unconstrained.

Every subsequent element's design doc references these and either confirms they hold or explicitly justifies deviation.

**Approved 2026-04-14.** Implementation proceeded.
