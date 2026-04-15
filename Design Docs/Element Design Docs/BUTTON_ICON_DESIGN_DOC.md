# Button.icon — design doc

**Specialization purpose.** A square Button whose content is a centered sprite instead of a centered text label. Ships alongside the base Button as a factory variant (`Button.icon(...)`).

**First Phase 9 specialization.** Follows the Phase 9 principle: specializations are factory variants on existing element types, accessed via `.element(...)` — no new PanelBuilder methods.

**Audit-sourced.** Two consumer mods need this pattern: shulker-palette's 9×9 mode buttons and sandboxes's 11×11 action icons. Both are "small square button with an icon" and today the base Button only renders text.

---

## What this phase actually ships

Two pieces of work, done in order as two commits:

1. **Factor `Button.render()` into two protected hooks** — `renderBackground(ctx, sx, sy)` and `renderContent(ctx, sx, sy)`. Pure refactor; no behavioral change. This is the enabling pre-work.
2. **Add `Button.icon(...)` factory + package-private `IconButton` subclass** — the specialization that overrides `renderContent` to paint a centered sprite.

Each commit independently compilable. Each visually verifiable.

---

## Factoring: the protected hooks

### Current `Button.render()` structure

1. Compute absolute `sx`, `sy` from `ctx.originX() + childX` etc.
2. Update hover state (`hovered = isHovered(ctx)`).
3. Paint panel background — `DARK` when disabled; `RAISED` otherwise, with a translucent white highlight overlay when hovered.
4. Paint centered text — ARGB white (or gray when disabled) with drop shadow.
5. Register tooltip for next frame if hovered and a tooltip supplier is set.

### Factored structure

Framework concerns stay in `render()`: coordinate compute, hover update, tooltip dispatch. Visual responsibilities move out.

```java
@Override
public final void render(RenderContext ctx) {          // final — extension surface is the hooks, not render() itself
    int sx = ctx.originX() + childX;
    int sy = ctx.originY() + childY;
    hovered = isHovered(ctx);

    renderBackground(ctx, sx, sy);
    renderContent(ctx, sx, sy);

    // Tooltip dispatch stays here (orthogonal to content; frame-scoped).
    if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
        Component ttText = tooltipSupplier.get();
        if (ttText != null) {
            ctx.graphics().setTooltipForNextFrame(
                    Minecraft.getInstance().font, ttText, ctx.mouseX(), ctx.mouseY());
        }
    }
}

/** Paint the panel background + hover overlay. Extension point — see contract below. */
protected void renderBackground(RenderContext ctx, int sx, int sy) {
    boolean disabled = isDisabled();
    if (disabled) {
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.DARK);
    } else {
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.RAISED);
        if (hovered) {
            ctx.graphics().fill(sx + 1, sy + 1, sx + width - 1, sy + height - 1, 0x30FFFFFF);
        }
    }
}

/** Paint the content (default: centered text label). Extension point — see contract below. */
protected void renderContent(RenderContext ctx, int sx, int sy) {
    var font = Minecraft.getInstance().font;
    int textWidth = font.width(text);
    int textX = sx + (width - textWidth) / 2;
    int textY = sy + (height - font.lineHeight) / 2;
    int textColor = isDisabled() ? 0xFF808080 : 0xFFFFFFFF;
    ctx.graphics().drawString(font, text, textX, textY, textColor, true);
}
```

**Note:** `render(RenderContext)` becomes `final`. The extension surface is `renderBackground` / `renderContent`, not the orchestration method. Subclasses that want to completely replace rendering still have that option by overriding the hooks fully; making `render()` final prevents accidental breakage of the framework-level ordering (hover update before draw, tooltip dispatch after content).

### Hooks are stable consumer-facing extension points

Both hooks carry this contract in their javadoc:

> *Stable extension point for consumer Button subclasses. The signature `(RenderContext ctx, int sx, int sy)` and the semantic contract — `sx`/`sy` are the absolute top-left of the button, `renderBackground` runs before `renderContent`, neither hook mutates Button state — are maintained across MenuKit versions. Consumer subclasses can rely on these hooks.*

This elevates the factoring from "internal implementation detail" to "documented public API" for consumer customization. Phase 8's customization verification found that Button was extensible via non-final class and non-final methods, but that full render override was the only clean path — partial customization required duplication. This factoring resolves that. Consumers subclassing Button now can:

- Override `renderBackground` alone for a custom background style while keeping default label rendering.
- Override `renderContent` alone for custom content while keeping default panel-style background.
- Override both for fully custom rendering.
- Call `super.renderBackground(...)` / `super.renderContent(...)` to layer custom painting over the defaults.

No `AbstractPanelElement` helper shipped. Two hooks, tight responsibilities.

---

## `Button.icon` API

Two static factories, mirroring Convention 2 (both fixed and supplier forms ship uniformly — sprite identity is data):

```java
public static Button icon(int childX, int childY, int size,
                          Identifier sprite, Consumer<Button> onClick);

public static Button icon(int childX, int childY, int size,
                          Supplier<Identifier> sprite, Consumer<Button> onClick);
```

- `size` — single int, square only (Convention 1 narrowing for square elements, matches Icon's own API).
- `sprite` / `Supplier<Identifier>` — the icon. Supplier form enables state-swap-by-icon for free: `Button.icon(x, y, 16, () -> isActive ? iconOn : iconOff, onClick)`.
- `onClick` — trailing callback per Convention 1.
- Returns `Button` (public type); the concrete subclass (`IconButton`) is package-private.

Two factories, not three. No `disabledWhen` overload; consumers wanting a disabled-predicate on an icon button can either wrap the fixed form with `.element(new IconButton-equivalent)` or subclass Button directly. This matches base Button's builder posture — `.button(...)` ships without a `disabledWhen` overload; full form via `.element(new Button(..., disabledWhen))`.

**Tooltip setters inherit for free.** `Button.icon(...).tooltip(...)` works without additional code because `.tooltip(Component)` and `.tooltip(Supplier<Component>)` live on the parent Button class. Good — the accessibility recommendation (below) leans on this.

**No new PanelBuilder method.** Accessed via `.element(Button.icon(x, y, size, sprite, onClick))`. PanelBuilder stays at ~20 methods per the Phase 9 builder-method principle.

---

## `IconButton` internals

Package-private subclass, one field (`Supplier<Identifier>`), one override:

```java
final class IconButton extends Button {
    private final Supplier<Identifier> spriteSupplier;

    IconButton(int childX, int childY, int size,
               Supplier<Identifier> sprite, Consumer<Button> onClick) {
        super(childX, childY, size, size, Component.empty(), onClick);
        this.spriteSupplier = sprite;
    }

    @Override
    protected void renderContent(RenderContext ctx, int sx, int sy) {
        Identifier id = spriteSupplier.get();
        if (id == null) return;

        int inset = 2;
        int iconSize = getWidth() - inset * 2;
        int tint = isDisabled() ? 0x66FFFFFF : 0xFFFFFFFF;
        ctx.graphics().blitSprite(
                RenderPipelines.GUI_TEXTURED, id,
                sx + inset, sy + inset, iconSize, iconSize,
                tint);
    }
}
```

The parent is constructed with `Component.empty()` so the inherited `renderContent` has nothing to paint — but we override it anyway, so the parent's text-drawing code never runs.

### Direct `blitSprite`, not Icon composition

Icon is a PanelElement with its own `childX`/`childY` semantics. Composing one inside a Button means the inner Icon is rendered at panel-relative coords, and offsetting cleanly inside an arbitrary parent requires synthesizing a `RenderContext` with a different origin — awkward and a layering inversion.

Direct `blitSprite` on `RenderPipelines.GUI_TEXTURED` is the same underlying primitive Icon uses. Same visual outcome, cleaner code, no abstraction inversion. If Icon later grows stateful features (tint modes, flip, rotation) that Button.icon should mirror, revisit at that point — not speculatively now.

### 2px sprite inset

The inset preserves the panel-style border (RAISED's bevel, INSET's sunken edge, DARK's muted border) around the icon. A flush sprite covers the border and the button loses its "button-ness" — it reads as a sprite rather than a tappable control. 2px keeps the border visible; the icon renders in the interior.

Not configurable. If Phase 11 consumer refactors surface a real demand for inset control, it's a one-field addition.

### Dim-when-disabled only

Icons render with a ~40% alpha tint (ARGB `0x66FFFFFF`) when the button is disabled. Full opacity (`0xFFFFFFFF`) otherwise, including hovered and pressed states.

**Hovered/pressed states are already communicated** by the panel-style background shift (RAISED → INSET overlay, translucent highlight). Adding sprite tint there would double-communicate and potentially muddy the visual.

**Disabled state has an accessibility concern** — users need to perceive that the button isn't interactable. Dimming the sprite reinforces the DARK background's disabled signal. ~40% alpha matches vanilla's convention.

Consumers wanting exact-sprite rendering (no dim) subclass Button directly and override `renderContent` to skip the tint. The factoring makes this cheap.

---

## Accessibility: recommend pairing with a tooltip

Class-level javadoc on the `Button.icon` factories:

> *Icon-only buttons convey meaning through sprite alone, which can be less discoverable than text labels. Pairing `Button.icon` with a tooltip — `.tooltip(Component)` or `.tooltip(Supplier<Component>)` — is strongly recommended for accessibility and discoverability. Users hovering over an icon button should learn its purpose from the tooltip.*

Recommendation, not enforcement. Enforcement would mean constructor-requiring a tooltip, which is heavy-handed — some icons are universally understood (a button with an X for close, for example) and the library shouldn't dictate. Documentation at the factory javadoc is the right weight.

---

## Conventions check

1. **Constructor shape** — `(childX, childY, size, sprite, onClick)`. Single-int size for square element ✓, trailing callback ✓.
2. **Supplier variants** — both `Identifier` and `Supplier<Identifier>` forms ship ✓. Uniform with Icon's API.
3. **Render-only inherits defaults** — N/A (Button is interactive).
4. **One builder method per element** — **no new builder method.** Specializations accessed via `.element(Button.icon(...))` per Phase 9 principle ✓.
5. **Factory methods** — **refinement proposed.** See below.
6. **Vanilla textures for defaults** — N/A (sprite is consumer-provided).

### Convention 5 refinement

Phase 8's Convention 5 said: *"No factory methods except for direction-choice cases (Divider is the sole exercise)."*

Phase 9 specializations (`Button.icon`, `Toggle.linked`) are factory methods. They are structurally different from preset-value shortcuts and warrant a refined convention.

**Proposed refined wording (blessed by advisor):**

> *Factory methods are permitted when they return a specialization subclass with structurally different construction — different fields, different render behavior, different rendering semantics. Factory methods are NOT permitted as preset-value shortcuts over the primary constructor. The distinguishing test: does the factory return a different concrete type, or the same type with different default values? Different type = factory permitted; same type = use the constructor.*

Application:
- `Button.icon(...)` — returns `IconButton` subclass with different `renderContent`. **Factory permitted.** ✓
- `Toggle.linked(...)` — returns a linked subclass with overridden state hooks. **Factory permitted** (Phase 9 work, next design doc).
- `Divider.horizontal(...)` / `Divider.vertical(...)` — direction-choice case; already blessed in Phase 8. ✓
- Hypothetical `Button.primary(...)` (same class, preset style) — **factory not permitted**; use the constructor.

The refinement preserves Convention 5's original intent (prevent factory proliferation for ergonomic preset shortcuts) while acknowledging specialization subclasses as a legitimate use case.

**This refinement is locked in Phase 9** and carried into the Phase 9 report and the MenuKit migration memory.

---

## Implementation order

Two commits.

### Commit 1 — factor `Button.render()` into hooks

- Split `render()` into `renderBackground(ctx, sx, sy)`, `renderContent(ctx, sx, sy)`, with orchestration staying in `render()`.
- Mark `render()` final.
- Add stability-contract javadoc to both hooks and a class-level pointer.
- No change in behavior. Build, relaunch, visually verify (via temporary test Button added to `TestContractHandler` for this phase's work; reverted before commit).

### Commit 2 — add `Button.icon` + `IconButton`

- `IconButton` package-private final class in the same file (or `IconButton.java` sibling — decide at implementation time).
- Two static factories on Button: `icon(..., Identifier, ...)` and `icon(..., Supplier<Identifier>, ...)`.
- Accessibility-recommendation javadoc on the factories.
- Build, relaunch, visually verify: normal state, hovered, disabled (with ~40% alpha dim), tooltip when attached, state-swap via supplier form.

---

## Scope boundaries — what Button.icon does not do

- **No icon-and-label combined variant.** Consumers wanting an icon-plus-text button subclass Button directly using the new hooks; no factory for it.
- **No animated state transitions.** Sprite supplier handles state-based swaps; any animation is consumer scope.
- **No per-state sprite parameters on the factory.** `Button.icon(x, y, size, onSprite, offSprite, stateSupplier, onClick)` is rejected — the supplier form already handles this: `Button.icon(x, y, size, () -> state.get() ? onSprite : offSprite, onClick)`.
- **No disabled-predicate overload.** Consumers needing `disabledWhen` on an icon button subclass Button directly, matching base Button's own posture.
- **No change to Button.java's existing API.** All existing constructors, builder methods, getters, tooltip setters preserved. Subclass-ability via the new hooks is purely additive.

---

## Risks & mitigations

- **Factoring regresses visual output.** Mitigation: pre/post visual verification with a temporary test Button added to `TestContractHandler` and reverted. Factoring is mechanical; behavior-preservation is verifiable.
- **`blitSprite` API signature mismatch in 1.21.11.** Mitigation: verify the color-tint overload exists at implementation time. Fallback: pose-stack + setShaderColor if the tinted overload is unavailable.
- **Making `render()` final breaks an existing consumer subclass.** Mitigation: no consumer mod currently extends Button (consumer mods are disabled; audit shows no in-tree subclass). Risk is zero for Phase 9; if Phase 11 refactors surface a Button subclass that overrode `render()` directly rather than the hooks, it becomes a documented migration — the new factoring is the blessed path.

---

## Approved 2026-04-14. Implementation proceeds.

File: `menukit/src/main/java/com/trevorschoeny/menukit/core/Button.java` (factoring + factory + `IconButton` internal class).
