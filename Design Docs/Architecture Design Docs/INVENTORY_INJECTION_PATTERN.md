# Inventory-Menu Injection Pattern

**Phase 10 deliverable.** How consumer mods put MenuKit elements into vanilla inventory screens — chests, furnaces, shulker boxes, the player inventory, and any other `AbstractContainerScreen` subclass — without the library injecting on their behalf.

---

## Scope

Phase 10's audit of the four consumer mods surfaced **five distinct injection patterns** by target. This document covers three of them, all targeting the inventory-menu context.

| # | Pattern | Target | This doc |
|---|---|---|---|
| 1 | Input-intercept mixin + pre-declared panel | vanilla inventory-menu screen | ✓ |
| 2 | Button / small panel at a slot-grid region | vanilla inventory-menu screen | ✓ |
| 3 | Panel at vanilla-screen-bounds corner | vanilla inventory-menu screen | ✓ |
| 4 | Widget/overlay insertion into vanilla standalone screen | vanilla standalone screen | `STANDALONE_INJECTION_PATTERN.md` |
| 5 | HUD panel with visibility predicate | HUD | `HUD_INJECTION_PATTERN.md` |

Explicitly out of scope for this document:

- **Consumer-owned MenuKit-native screens** (sandboxes's `SandboxScreen`; an inventory-plus full inventory replacement). These are served by `MenuKitScreen` and `MenuKitScreenHandler.builder()` already. They are construction, not injection, and are not what Phase 10 addresses.
- **Cross-mod composition mechanisms.** Discussed at the end as a boundary note, not as a MenuKit feature.

---

## Principle

**Library, not platform.** `THESIS.md` principle #1. The test:

> *If MenuKit took ownership of this code path, could a second mod doing something similar still coexist?*

For vanilla-screen decoration, the code path is the vanilla screen class itself. If MenuKit mixed into `AbstractContainerScreen` to dispatch consumer-registered decorators, MenuKit would become the arbiter of conflicts between consumers and vanilla — and between multiple consumers decorating the same screen. The Fabric ecosystem already has a mechanism that handles this: mixin composition. MenuKit does not duplicate it.

**Consumer mods write their own mixins.** Each consumer targets exactly the screen classes they want to decorate, at the hook points they need. MenuKit ships primitives the consumer composes inside those mixins. That's the entire shape.

This document specifies:
- What primitives ship (one helper class and a small family of positioning conveniences).
- How the three patterns above compose those primitives.
- What explicitly does *not* ship, and why.

---

## What ships

### `ScreenPanelAdapter` (core helper)

An adapter that bundles the mechanical parts of rendering a `Panel` inside a vanilla screen and dispatching input to it. The consumer holds the adapter as a `@Unique` field on their mixin, and calls its methods from inside the mixin's own render and input methods.

**The adapter bundles the mechanical:**

- **Coordinate translation.** The consumer supplies a `ScreenOriginFn` — a pure function from the vanilla screen's bounds to the panel's top-left origin. The adapter computes the origin each render (handling screen resizes).
- **Render dispatch.** The adapter constructs the `RenderContext` (absolute screen coords + mouse coords + `GuiGraphics`) and dispatches to each visible element. Short-circuits early if `panel.isVisible()` returns false — invisible panels do not render, per the inertness discipline.
- **Input dispatch.** The adapter hit-tests each visible element against screen-space mouse coords and calls `element.mouseClicked(...)` on any hit. Returns a boolean indicating whether the click landed on an interactive element. Also short-circuits on `!panel.isVisible()`. Mouse coordinates are screen-space throughout — see `PanelElement`'s class-level "Coordinate contract" javadoc for the canonical rule.

**The adapter explicitly does NOT bundle the policy:**

- **Visibility composition.** The consumer decides when to call `adapter.render()` and `adapter.mouseClicked()` at all. Visibility in the new architecture is owned by the consumer — either via `Panel.setVisible(boolean)` the consumer flips, or (open question #1 below) via `Panel.showWhen(Supplier<Boolean>)` the consumer-supplied predicate drives. Forcing the adapter to own visibility would take a decision away from the consumer and calcify a choice (supplier vs. imperative vs. compound predicate) that differs across use cases.
- **Cancellation policy.** `mouseClicked` returns whether the click hit the panel's interactive surface; the consumer's mixin inspects the return value and decides whether to cancel vanilla's handling (e.g., `cir.setReturnValue(true)`). Some consumers want to swallow clicks the panel consumes; some want vanilla to also see them. Not the library's call.

**Sketch** (final API decided at implementation time):

```java
public final class ScreenPanelAdapter {

    public ScreenPanelAdapter(Panel panel, ScreenOriginFn originFn) { ... }

    /** Render the panel. No-op if !panel.isVisible(). */
    public void render(GuiGraphics graphics, ScreenBounds screenBounds,
                       int mouseX, int mouseY) { ... }

    /**
     * Dispatch a click. No-op if !panel.isVisible().
     * @return true if the click landed on an interactive element.
     *         The consumer decides whether to cancel vanilla based on this return value.
     */
    public boolean mouseClicked(ScreenBounds screenBounds,
                                double mouseX, double mouseY, int button) { ... }
}

public record ScreenBounds(int leftPos, int topPos, int imageWidth, int imageHeight) {}
public record ScreenOrigin(int x, int y) {}

@FunctionalInterface
public interface ScreenOriginFn {
    ScreenOrigin compute(ScreenBounds bounds);
}
```

### Positioning conveniences

A small set of `ScreenOriginFn` constructors for the cases the audit actually surfaced. Consumers write their own lambda if these don't fit.

```java
public final class ScreenOriginFns {
    /** Offset from the vanilla screen's top-left corner (leftPos+dx, topPos+dy). */
    public static ScreenOriginFn fromScreenTopLeft(int dx, int dy);

    /** Offset from the vanilla screen's top-right corner. dx/dy are panel-top-left relative to top-right. */
    public static ScreenOriginFn fromScreenTopRight(int panelWidth, int dx, int dy);

    /** Above a slot grid whose top-left sits at (gridX, gridY) in panel-local coords. */
    public static ScreenOriginFn aboveSlotGrid(int gridX, int gridY, int panelHeight, int gap);

    /** Below a slot grid whose top-left sits at (gridX, gridY) and dimensions (gridW, gridH). */
    public static ScreenOriginFn belowSlotGrid(int gridX, int gridY, int gridHeight, int gap);
}
```

Only four constructors ship. The bar for adding more: a concrete consumer case that cannot be expressed cleanly with a custom lambda. Hypothetical future demand does not qualify.

---

## Pattern 1 — Input-intercept mixin + pre-declared panel

**Use when** the panel should appear or disappear in response to an input event (keybind press, click on another widget, S2C packet), not from a continuous predicate over game state.

**Canonical example:** inventory-plus's container peek. Three peek panels (shulker / ender / bundle) are declared at mod init, hidden. A mixin into `AbstractContainerScreen` intercepts the peek keybind while hovering a peekable slot, sends a C2S packet, and the S2C response toggles the consumer-held visibility state.

### Visibility mechanism

The canonical approach follows the **Phase 8/9 state-ownership pattern** established by `Toggle.linked`:

> *Consumer holds the state. Library reads it via a `BooleanSupplier`. Consumer mutates its own state in response to events. Library re-reads on next frame.*

```java
// Consumer's state holder.
private static boolean peekVisible = false;

// Consumer declares the panel at init. Panel is constructed via the core
// constructor; showWhen is chained on the returned Panel.
Panel peekPanel = new Panel(
    "peek",
    List.of(/* elements */),
    /* initialVisible */ true
).showWhen(() -> peekVisible);

// Consumer's mixin flips the state.
@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
private void onKeyPress(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    if (isPeekKeybind(keyCode, modifiers) && isHoveringPeekable()) {
        peekVisible = !peekVisible;
        cir.setReturnValue(true);
    }
}
```

This matches `Toggle.linked` exactly: state lives in consumer code, the library reads via supplier, there is no library-owned visibility state that could desync from the consumer's.

### Escape hatch

`Panel.setVisible(boolean)` on the Panel reference remains available. Use it when the supplier indirection is genuinely awkward — typically event-driven one-shot toggles where the state *is* the visibility and wrapping it in a separate boolean field adds no clarity. Still library-not-platform compliant: the Panel reference is consumer-held, `setVisible` is scoped to it, no global registry.

The design doc's recommendation is **supplier-driven is canonical, imperative is escape hatch**. Consistency with Phase 8/9 patterns; keeps state ownership on the consumer.

### Composition with Patterns 2/3

The peek case is Pattern 1 *plus* Pattern 2/3: input interception is Pattern 1; actually rendering the panel inside the vanilla container screen uses the `ScreenPanelAdapter` machinery described below. Pattern 1 is a standalone pattern when the panel lives on a MenuKit-owned screen and just needs visibility triggered from outside; it composes with Patterns 2/3 when the panel is being injected into a vanilla screen.

---

## Pattern 2 — Button / small panel at a slot-grid region

**Use when** the consumer wants to render an interactive element anchored to a specific slot grid inside a specific vanilla inventory screen.

**Canonical examples:**
- Shulker-palette's 9×9 palette toggle above the shulker's slot grid in `ShulkerBoxScreen`.
- Inventory-plus's sort and move-matching buttons above the slot grid of any container screen.

### Shape

1. Consumer declares the small Panel at init (typically one `Button.icon(...)` with a tooltip — minimal structure).
2. Consumer writes a mixin into the target vanilla screen class.
3. The mixin holds a `ScreenPanelAdapter` as a `@Unique` field.
4. The mixin `@Inject`s into `render` (TAIL) to call `adapter.render(...)` and `mouseClicked` (HEAD, cancellable) to call `adapter.mouseClicked(...)` and cancel vanilla if the click was consumed.

### Example

```java
@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerPaletteScreenMixin extends AbstractContainerScreen<ShulkerBoxMenu> {

    protected ShulkerPaletteScreenMixin(ShulkerBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Unique
    private final ScreenPanelAdapter shulkerPalette$adapter = new ScreenPanelAdapter(
        ShulkerPaletteMod.TOGGLE_PANEL,
        ScreenOriginFns.aboveSlotGrid(
            /* gridX       */ 8,
            /* gridY       */ 18,
            /* panelHeight */ 9,
            /* gap         */ 2
        )
    );

    @Inject(method = "render", at = @At("TAIL"))
    private void shulkerPalette$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        shulkerPalette$adapter.render(g, shulkerPalette$bounds(), mx, my);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void shulkerPalette$click(MouseButtonEvent event, boolean doubleClick,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (shulkerPalette$adapter.mouseClicked(shulkerPalette$bounds(),
                event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);  // consumer chose to swallow; vanilla does not see
        }
    }

    @Unique
    private ScreenBounds shulkerPalette$bounds() {
        return new ScreenBounds(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }
}
```

### Why Pattern 2 exists as a distinct pattern

Pattern 2 and Pattern 3 share all the machinery — they differ only in the positioning conveniences they tend to use (`aboveSlotGrid` / `belowSlotGrid` for Pattern 2; `fromScreenTopRight` etc. for Pattern 3). Calling them out as separate patterns in this document is for consumer clarity when scanning for "which one is my use case" — the implementation is unified.

---

## Pattern 3 — Panel at vanilla-screen-bounds corner

**Use when** the consumer wants a panel at a corner or edge of the vanilla screen's visual frame, not relative to a specific slot grid.

**Canonical examples:**
- Sandboxes's three 11×11 icons in the above-right corner of the inventory screen.
- Inventory-plus's settings gear button in the above-right corner of any inventory screen.
- Inventory-plus's pockets panels below the hotbar row.

### Shape

Identical to Pattern 2. The only difference is the origin function.

```java
@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})
public abstract class SandboxesInventoryMixin {

    @Unique
    private final ScreenPanelAdapter sandboxes$enterAdapter = new ScreenPanelAdapter(
        SandboxMod.ENTER_BUTTON_PANEL,
        ScreenOriginFns.fromScreenTopRight(
            /* panelWidth */ 11,
            /* dx */ -4, /* dy */ -16
        )
    );

    @Inject(method = "render", at = @At("TAIL"))
    private void sandboxes$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        sandboxes$enterAdapter.render(g, sandboxes$bounds(), mx, my, null);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void sandboxes$click(double mx, double my, int btn, CallbackInfoReturnable<Boolean> cir) {
        if (sandboxes$enterAdapter.mouseClicked(sandboxes$bounds(), mx, my, btn)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private ScreenBounds sandboxes$bounds() { /* read this.leftPos etc. */ }
}
```

### Multiple panels with mutually exclusive visibility

Sandboxes declares three panels at this anchor (enter button, back button, mode label) with mutually exclusive visibility predicates (enter in main world; back + label in sandbox). The mixin holds three adapters. Per render tick, the mixin calls `render` on all three; each panel's own visibility — supplier-driven via `.showWhen(...)` or imperative via `setVisible(...)` — gates whether it actually paints. Inert panels render nothing (per the inertness discipline).

The adapter does not know or care about "mutual exclusivity." It renders what it's called on; invisible panels are silent. Consumers who want panels to coordinate visibility do so through their own state and supplier predicates.

---

## Targeting multiple screen classes

Some vanilla screens come in variants that share conceptual purpose but differ in implementation. The canonical example: `InventoryScreen` and `CreativeModeInventoryScreen`. The creative inventory handles hotbar clicks differently (vanilla dispatches through `mouseClicked` without always going through `slotClicked`), so a mixin targeting only `InventoryScreen` misses creative-mode interactions.

There is a **targeting pitfall** here that consumers will hit on first try, because mixin injection cannot find methods that are only inherited — only methods that are declared (or overridden) on the targeted class are injectable.

### The pitfall

Consumers typically reach for multi-class targets first:

```java
// Looks right, but breaks if mouseClicked isn't overridden on InventoryScreen:
@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})
public abstract class MyInventoryMixin { ... }
```

If `InventoryScreen` inherits `mouseClicked(MouseButtonEvent, boolean)` from `AbstractContainerScreen` without overriding it, then `@Inject(method = "mouseClicked(...)")` fails with *"could not find any targets matching ... in InventoryScreen"*. The vanilla hierarchy for container screens is inconsistent: `CreativeModeInventoryScreen` overrides `mouseClicked`, but `InventoryScreen` and `ContainerScreen` do not.

### The recommended pattern — broad target, narrow runtime gate

Target the abstract parent where the methods are declared, then use a runtime `instanceof` check to narrow behavior to the intended subclasses:

```java
@Mixin(AbstractContainerScreen.class)
public abstract class MyInventoryMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    @Unique
    private boolean myMod$appliesToThisScreen() {
        Object self = this;  // Cast through Object to bypass generic self-reference.
        return self instanceof InventoryScreen
                || self instanceof CreativeModeInventoryScreen;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void myMod$render(...) {
        if (!myMod$appliesToThisScreen()) return;
        adapter.render(...);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void myMod$click(...) {
        if (!myMod$appliesToThisScreen()) return;
        if (adapter.mouseClicked(...)) cir.setReturnValue(true);
    }
}
```

The mixin injects into `AbstractContainerScreen`'s declared methods — which every container screen inherits — and the `instanceof` gate narrows runtime behavior to the two inventory variants. This pattern works uniformly whether the subclasses override the injected method or not.

The `ScreenPanelAdapter` is class-agnostic. It renders and dispatches against whatever `Screen` subclass invokes it. The mixin does the targeting; the adapter does the mechanics.

### When concrete-subclass targets work

Multi-class or concrete-subclass targeting is safe when the targeted classes actually declare the injected methods. `CreativeModeInventoryScreen.mouseClicked` and `MenuKitHandledScreen.mouseClicked` are declared overrides, so `@Mixin(CreativeModeInventoryScreen.class)` with `@Inject(method = "mouseClicked...")` works. Check the vanilla (or dependency) source before committing to a concrete-subclass target. When in doubt, default to the broad-target + runtime-gate pattern above.

The Phase 10 example mixins (`examples/injection/`) use this pattern for their `mouseClicked` injections — a concrete reference for the recipe.

### Split mixins are the default, not the exception

In practice, decorations targeting multiple inventory variants almost always require **multiple mixin classes**, not one. Two forces conspire:

1. **Per-method declaration points.** Each hook a decoration uses (render, mouseClicked, keyPressed) may be declared at a different level of the hierarchy. `InventoryScreen` inherits `render` and `mouseClicked` through its chain but `AbstractRecipeBookScreen` declares its own `keyPressed`; `CreativeModeInventoryScreen` declares all three itself. A single mixin cannot reliably reach every hook through the same target class.

2. **Per-variant super-call asymmetry.** A subclass may override one method with `super.foo(...)` and another without. This means a broad-target mixin fires for one hook but silently skips the next in the same variant — see the "Silent-inert dispatch" failure mode in the next section. Each hook must be treated as an independent question: *which class actually reaches my mixin for this subclass, for this method?*

The realistic shape: **one mixin per (hook, actual declaration point)**, sharing a non-mixin state holder and joined by runtime `instanceof` gates to the intended subclass set. The Phase 10 example mixins demonstrate the floor:

- **Corner button (Pattern 3)**: `ExampleInventoryCornerButtonMixin` targets `AbstractContainerScreen` for both render and click (broad-target + instanceof gate for the two inventory variants); `ExampleInventoryCornerButtonRecipeBookRenderMixin` is a supplementary render-only hook on `AbstractRecipeBookScreen` that paints on top of the recipe-book widget for survival inventory (z-order fix). Two mixin classes plus `ExampleInventoryCornerButton` (a non-mixin state holder in `examples.shared`).
- **Keybind panel (Pattern 1)**: `ExampleKeybindTriggeredPanelMixin` is the primary on `AbstractContainerScreen` (render TAIL + keyPressed HEAD); `ExampleKeybindTriggeredPanelRecipeBookMixin` is supplementary on `AbstractRecipeBookScreen.keyPressed` with `instanceof InventoryScreen` gate, for survival's non-super-calling override. Two mixin classes plus `ExampleKeybindTriggeredPanel`.

Three mixin classes plus a shared non-mixin state holder is the realistic floor for a decoration spanning both survival and creative inventory, not a ceiling. Plan for supplementary mixins at the declaration points where broad-target + instanceof-gate falls short — see the next section for the failure modes that force this.

---

## Consumer mixin failure modes

Four failure modes consumers hit repeatedly when decorating vanilla screens. All are Fabric/vanilla constraints rather than MenuKit concerns; the library cannot shield consumers from them but documents them here so consumers recognize the symptoms when they hit.

### 1. Silent-inert dispatch (no-super-call override)

**Symptom.** A mixin at `@Inject(method = "render"|"mouseClicked"|"keyPressed", at = @At("HEAD"))` on a parent class installs cleanly — no mixin error at startup — but never fires when the player is inside a specific subclass. The decoration is invisible or unresponsive on that screen only.

**Cause.** An intermediate class in the subclass's hierarchy overrides the method without calling `super.foo(...)`. The dispatch chain stops at the override; the parent method — and your mixin on it — never runs.

**Concrete example from Phase 10.** `InventoryScreen` extends `AbstractRecipeBookScreen` extends `AbstractContainerScreen`. `AbstractRecipeBookScreen.keyPressed` overrides the method to handle the recipe-book toggle and does not super-call. A mixin on `AbstractContainerScreen.keyPressed` therefore never fires in survival inventory — the dispatch stops at `AbstractRecipeBookScreen`.

**Fix pattern.** Supplementary `@Mixin(<parent-at-actual-declaration>.class)` plus runtime `instanceof <intended-subclass>` gate, with HEAD cancellation via `cir.setReturnValue(true)` to prevent double-toggle if vanilla ever starts super-calling. The `ExampleKeybindTriggeredPanelRecipeBookMixin` is the concrete shape.

Per-method and per-subclass — and sometimes per-runtime-state. The same hierarchy can super-call for one method and not for another, or super-call conditionally depending on widget state (recipe book open/closed, focused text field, etc.). In 1.21.11, `AbstractRecipeBookScreen.mouseClicked` super-calls reliably; `AbstractRecipeBookScreen.keyPressed` and `AbstractRecipeBookScreen.render` do not — consumers decorating `InventoryScreen` need supplementary mixins on `AbstractRecipeBookScreen` for both input and render. Each hook must be tested independently and treated as an empirical observation, not a design contract from vanilla's side. Expect the per-hook answer to change across vanilla versions.

### 2. Render z-order occlusion

**Symptom.** A decoration renders but is covered by vanilla UI drawn after it — a tab icon, a recipe-book widget, a side panel. The decoration exists; you just can't see it.

**Cause.** `@Inject(method = "render", at = @At("TAIL"))` on a parent class fires when the parent's render method returns — but the subclass's render may call `super.render(...)` from the middle of its body and continue drawing afterward. TAIL on the parent is not TAIL on the whole render pass.

**Concrete example from Phase 10.** The keybind-triggered panel's text label renders at TAIL of `AbstractContainerScreen.render`. In survival inventory, `AbstractRecipeBookScreen.render` calls super then continues to draw the recipe-book widget, overlaying the text. In creative inventory, `CreativeModeInventoryScreen.render` calls super then continues to draw tab icons on top.

**Fix pattern.** Supplementary `@Mixin(<subclass-that-overrides-render>.class)` at `@Inject(method = "render", at = @At("TAIL"))`. The subclass's render TAIL is the true end of its draw pass; injecting there guarantees on-top rendering. The Phase 10 corner-button example does this for survival via `ExampleInventoryCornerButtonRecipeBookRenderMixin`; the keybind-panel example does the same via `ExampleKeybindTriggeredPanelRecipeBookRenderMixin`.

**Connection to failure mode #1.** For render hooks, "silent-inert dispatch" and "z-order occlusion" often converge — the supplementary-render-at-the-override-class fix resolves both. Consumers planning any render decoration in a subclass that overrides render should add the supplementary proactively, without distinguishing the two modes. The convergence means: any decoration rendering inside `InventoryScreen` needs a supplementary render mixin on `AbstractRecipeBookScreen`, regardless of whether the primary would have silently failed or merely been occluded. Both Phase 10 example decorations spanning survival inventory need one.

### 3. `IllegalClassLoadError` on non-mixin classes in the mixin package

**Symptom.** `Mixin transformation of <YourHelperClass> failed ... IllegalClassLoadError: <class> is in a defined mixin package ... and cannot be referenced directly`. Crashes occur not at mixin load but at first reference from transformed code — often when the player opens the target screen for the first time.

**Cause.** The `package` entry in your `*.mixins.json` declares a mixin-only package. Fabric's class loader refuses to load non-mixin classes from that package when referenced by transformed target code.

**Concrete example from Phase 10.** An earlier iteration placed `ExampleInventoryCornerButton` (a non-mixin helper holding the Panel + adapter as static fields) in `com.trevorschoeny.menukit.examples.injection`, the same package as the example mixins. Both the render and click mixins referenced it. When the mixin-transformed `AbstractContainerScreen.render` tried to load the helper class → `IllegalClassLoadError` → game crash on screen open.

**Fix pattern.** Keep non-mixin helpers in a sibling package outside the mixins.json's `package` entry. The Phase 10 examples use `examples.shared` for helpers and `examples.injection` for mixins.

### 4. `@Shadow` on inherited fields in multi-target mixins

**Symptom.** `@Shadow field <fieldName> was not located in the target class ... No refMap loaded.` Multi-target mixin fails to apply (or applies with broken field references), and the decoration misfires where the target is a subclass inheriting the field.

**Cause.** Fabric's refmap cannot remap a `@Shadow` to an inherited field across multiple target classes simultaneously. `@Shadow` on a field declared directly on the single target works; `@Shadow` on a field inherited from a parent, in a multi-target mixin, does not.

**Concrete example from Phase 10.** An earlier corner-button implementation used `@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})` with `@Shadow protected int leftPos`. Both targets inherit `leftPos` from `AbstractContainerScreen`. Fabric logged `Found a remappable @Shadow annotation on leftPos` as an ERROR and the mixin's field access resolved to garbage.

**Fix patterns (two options).**
- **Single-target broad-parent mixin.** `@Mixin(AbstractContainerScreen.class)` plus `instanceof` runtime gate for scoping. `@Shadow` works trivially because the field is declared on the target. This is what the primary `ExampleInventoryCornerButtonMixin` does.
- **Extends-target pattern when you must target the subclass.** `@Mixin(SubClass.class) public abstract class MyMixin extends ParentWhereFieldIsDeclared<...>`. A dummy constructor satisfies the compiler; the mixin class is never instantiated, so the constructor body doesn't matter. The inherited field becomes visible through Java's inheritance rather than through mixin's field resolution. The Phase 10 supplementary render mixin `ExampleInventoryCornerButtonRecipeBookRenderMixin` uses this shape for `AbstractRecipeBookScreen`.

Each of these is a vanilla/Fabric constraint, not a MenuKit choice. The library cannot shield consumers from them but — by naming them — gives consumers the vocabulary to recognize the symptoms when they hit.

---

## Cross-mod composition

Shulker-palette wants its palette toggle to also appear on inventory-plus's peek panel when the player is peeking a shulker-box item. This is a genuine inter-mod use case the audit surfaced.

**This is not MenuKit's to mediate.** The shape under library-not-platform:

- Inventory-plus, as the consumer mod owning the peek panel, may (at its own discretion) expose a public Java API for third-party mods to decorate it — e.g., `InventoryPlus.addPeekDecorator(Predicate<ItemStack>, PanelElement)`.
- Shulker-palette imports inventory-plus's public API and calls it directly from its own init code. No MenuKit involvement; direct Java linkage.
- Alternative: shulker-palette writes its own mixin into inventory-plus's peek class. Mixin composition handles the coexistence.
- Alternative: the two mods agree on a third-party contract (a service loader, a Fabric entrypoint, an event-bus library neither of them is MenuKit). MenuKit doesn't care.

**What MenuKit does not ship:** a decorator registry, a region-match injection system, a cross-mod event bus, or any library-mediated composition mechanism for consumer mods decorating each other's UI. The old architecture had such machinery (`MenuKit.buttonAttachment(...)` with region-name matching across consumer mods). It was platform behavior. It is gone.

This document documents the boundary and stops there.

---

## What does NOT ship

Explicitly, by design:

- **No registration API.** No `MenuKit.register(ChestScreen.class, panel, position)`. No `@DecoratesScreen(...)` annotation. No registry of any kind.
- **No ambient mixins.** MenuKit does not mixin into `AbstractContainerScreen`, `Gui`, or any vanilla screen class.
- **No `.showIn(ContextEnum)` API.** The old `MKContext` enum mapping panels to contexts-where-they-appear is ambient injection. Gone.
- **No region-based button attachments.** The old `MenuKit.buttonAttachment("id").forContainerType(X).above().buttons(regionName -> ...)` is ambient registration. Gone.
- **No defaults.** MenuKit does not ship a sort button on chests, a settings button on the inventory, or any other consumer-targeted UI default. All such UI is consumer work, composed from MenuKit primitives.
- **No cross-mod composition registry.** Consumer mods expose their own public Java APIs; other consumers call those APIs directly.
- **No config-driven injection.** No "set flag X in MenuKit config to show panel Y on screen Z."

Each of these, if shipped, would require MenuKit to take ownership of a code path it does not need. Each would make the library brittle, harder to coexist with, and architecturally tangled — the exact state Phases 1-5 rewrote out.

---

## Resolved design questions (post advisor review)

### 1. `Panel.showWhen(Supplier<Boolean>)` ships as first-class surface

**Decision: yes.** Ships on the core Panel builder, unifying visibility semantics with `MKHudPanel.builder().showWhen(...)`. Matches the Phase 8/9 state-ownership pattern (Toggle.linked precedent): consumer holds the state; the library reads via supplier.

**Precedence semantics.** `showWhen(...)` is the single source of truth for visibility while set. The behavior:

- Calling `showWhen(supplier)` replaces any prior `setVisible(...)` state. From that point forward, `isVisible()` evaluates the supplier.
- Subsequent `setVisible(...)` calls are silently ignored (no-op) while a supplier is active. No exception — consumers who have committed to supplier-driven visibility should not get spurious partial overrides from unrelated code paths.
- To revert to imperative-only visibility, call `showWhen(null)`. The prior `setVisible(...)` state is not restored; visibility defaults to true until the consumer calls `setVisible(...)` again.

This matches `Toggle.linked`'s state-ownership story: the supplier *is* the state. There is no parallel imperative field to desync. Document the nullability of the supplier parameter explicitly at the API boundary.

### 2. `ScreenBounds` shape: consumer constructs, passes per-call

**Decision: confirmed.** `ScreenBounds` is a record the consumer's mixin constructs from `this.leftPos`, `this.topPos`, `this.imageWidth`, `this.imageHeight` inside its own helper method, passed per render/click call. The adapter does not hold a `Supplier<ScreenBounds>`; keeping the adapter decoupled from `Screen` state is worth one trivial line of mixin glue.

---

## Clarifications

Design choices that are not open questions but worth stating so reviewers know they were considered:

- **Nested adapters / nested panels.** Not supported. Panel is the ceiling of composition per THESIS. Consumers wanting multiple visual groups declare multiple Panels, each with its own adapter.
- **Non-left-click buttons.** `Panel.mouseClicked` already handles the button-index parameter; the adapter passes it through unchanged.
- **`mouseReleased` / `mouseDragged` / scroll events.** Not shipped in Phase 10. `PanelElement` does not currently expose `mouseReleased` or `mouseDragged` (noted in DEFERRED). Consumers needing drag semantics write raw mixin code alongside the adapter for those events. Reconsideration trigger: Phase 11 consumer refactors reveal multiple mods building the same workaround.
- **Screen resize handling.** `ScreenOriginFn` is a pure function of `ScreenBounds`, so re-computing per-render handles resizes correctly. The consumer's mixin calls `adapter.render(...)` with fresh bounds each frame.
- **Inertness.** The adapter short-circuits `render` and `mouseClicked` when `!panel.isVisible()`. Hidden panels consume zero work — no supplier ticks, no render calls, no input. Consistent with the inertness discipline across all three contexts.

---

## Example mixins

Ship under `menukit/src/main/java/com/trevorschoeny/menukit/examples/`. Library-shipped illustrations, not consumer mod code. Phase 11 consumer refactors may use them as reference but do not import them as dependencies.

Two-package split reflecting Fabric's class-load rule (failure mode #3 in "Consumer mixin failure modes" above):

- `examples/injection/` — mixin classes. Claimed by `menukit-examples.mixins.json` as its `package`, so Fabric's loader won't permit non-mixin classes here.
- `examples/shared/` — non-mixin helpers (Panel + adapter + state holders) referenced by the mixins. Sibling package so the class-load rule doesn't fire.

### Pattern 3 — `ExampleInventoryCornerButton` (survival + creative inventory)

- `examples/shared/ExampleInventoryCornerButton` — the shared Panel + adapter + bounds helper. Single 11×11 button anchored `fromScreenTopRight(11, -4, -16)`.
- `examples/injection/ExampleInventoryCornerButtonMixin` — primary `@Mixin(AbstractContainerScreen.class)`. Render TAIL + mouseClicked HEAD, both gated by `instanceof InventoryScreen || instanceof CreativeModeInventoryScreen`.
- `examples/injection/ExampleInventoryCornerButtonRecipeBookRenderMixin` — supplementary `@Mixin(AbstractRecipeBookScreen.class) extends AbstractContainerScreen<AbstractContainerMenu>`. Render TAIL only, gated by `instanceof InventoryScreen`. Paints the button **on top of** the recipe-book widget, which the primary's TAIL alone cannot (failure mode #2, z-order). The extends-target pattern (failure mode #4 fix) makes `@Shadow leftPos` unnecessary — inherited fields are visible through Java.

Demonstrates: `fromScreenTopRight`, the broad-target + instanceof gate for two inventory variants, the supplementary-mixin pattern for z-ordering, and the extends-target shape for inherited-field access.

### Pattern 2 — `ExampleChestToolbarMixin` (chests)

Single-file `@Mixin(AbstractContainerScreen.class)` with `instanceof ContainerScreen` gate. Two 9×9 buttons above the slot grid via `aboveSlotGrid(8, 18, 9, 14)`. Panel + adapter held as `@Unique` fields directly on the mixin (mixin static + instance fields work fine for a single-target mixin that doesn't need to share state with supplementary mixins).

Demonstrates: `aboveSlotGrid` positioning, multi-button panel composition, and the single-mixin shape for decorations that don't need to span multiple variants.

### Pattern 1 — `ExampleKeybindTriggeredPanel` (any container screen, including survival inventory)

- `examples/shared/ExampleKeybindTriggeredPanel` — shared Panel (with `showWhen(() -> visible)`) + adapter + the `volatile boolean visible` state. Visibility lives on a plain class rather than on a mixin because mixin static fields don't reliably share state across mixin classes.
- `examples/injection/ExampleKeybindTriggeredPanelMixin` — primary `@Mixin(AbstractContainerScreen.class)`. Render TAIL + keyPressed HEAD. Covers chests + creative inventory via the parent's super-call chain.
- `examples/injection/ExampleKeybindTriggeredPanelRecipeBookMixin` — supplementary `@Mixin(AbstractRecipeBookScreen.class)`. keyPressed HEAD only, gated by `instanceof InventoryScreen`. Fixes failure mode #1 (silent-inert dispatch) for survival, since `AbstractRecipeBookScreen.keyPressed` doesn't super-call. Cancels at HEAD to prevent double-toggle.
- `examples/injection/ExampleKeybindTriggeredPanelRecipeBookRenderMixin` — supplementary `@Mixin(AbstractRecipeBookScreen.class) extends AbstractContainerScreen<AbstractContainerMenu>`. Render TAIL only, gated by `instanceof InventoryScreen`. Fixes the render side of the same problem — the primary's TAIL on `AbstractContainerScreen.render` also doesn't reliably fire for survival, so without this the text would never paint there. Extends-target pattern for inherited-field access (failure mode #4 fix).

Demonstrates: supplier-driven Panel visibility (Toggle.linked precedent), input interception, the silent-inert-dispatch fix pattern applied to BOTH render and keyPressed, and state sharing across three mixin classes via a plain helper class. One primary + two supplementaries (one per silent-inert method) is the realistic shape when a vanilla hierarchy has multiple no-super-call overrides across different methods.

### Dev-only gating

`menukit-examples.mixins.json` uses `IMixinConfigPlugin` (`DevOnlyExampleMixinsPlugin`) whose `shouldApplyMixin` returns `FabricLoader.isDevelopmentEnvironment()`. Examples compile and ship in `menukit.jar` as dormant bytecode; they only apply in the dev client. Preserves the "no defaults" rule from "What does NOT ship" below — production consumers of MenuKit are not decorated by these examples.

A fourth example may surface in Phase 11 review: cross-mod composition (pattern 2 + direct API call to another consumer mod's public surface). Probably deferred — cross-mod composition is a consumer concern, not a library-primitive demonstration.

---

## Verification

Phase 5 contracts are the regression gate. Phase 10's injection work touches:

- Screen rendering (new code path via `ScreenPanelAdapter.render` — runs outside `MenuKitHandledScreen`'s existing render).
- Panel visibility (potentially new `Panel.showWhen(Supplier<Boolean>)` surface).

Neither touches slot infrastructure or the sync protocol. Expected impact on the five canonical guarantees:

- **Composability.** Should strengthen. MenuKit remains uninvolved in vanilla screen mixins; consumers coexist via Fabric's mixin composition.
- **Vanilla-slot substitutability.** Unaffected. Injection patterns do not touch slots.
- **Sync-safety.** Unaffected. Injection is client-side render / input only.
- **Uniform abstraction.** Potentially improved if `Panel.showWhen` ships and unifies visibility across contexts.
- **Inertness.** Unaffected. The adapter already respects `panel.isVisible()`; hidden panels consume zero work per render.

All five contracts run at Phase 10 completion.

---

## Status

Implementation landed (Phase 10 work).

- ✓ `ScreenPanelAdapter`, `ScreenBounds`, `ScreenOrigin`, `ScreenOriginFn`, `ScreenOriginFns` in `menukit/inject/`.
- ✓ `Panel.showWhen(Supplier<Boolean>)` shipped with precedence semantics.
- ✓ Example mixins in `examples/injection/` (mixin classes) + `examples/shared/` (non-mixin helpers). Dev-only gated via `DevOnlyExampleMixinsPlugin`.
- ✓ Visual verification of all three patterns in-dev.
- ✓ Phase 5 contract verifications — all five pass.
- ✓ `PanelElement`'s coord-space contract lifted to class-level javadoc (canonical home).

Remaining for Phase 10:

1. Draft `STANDALONE_INJECTION_PATTERN.md` (Pattern 4). Smaller doc — documents sandboxes's pure-vanilla pause-menu pattern plus the MenuKit-Panel-inside-standalone-screen variant.
2. Draft `HUD_INJECTION_PATTERN.md` (Pattern 5). Smallest doc — mostly documents what `MKHudPanel.builder().showWhen(...)` already handles, plus positioning-relative-to-vanilla-HUD-elements guidance and the render-only cautionary note.
3. Cross-pattern review. Consolidate shared abstractions if any emerge.
4. Phase 10 report + DEFERRED updates.
