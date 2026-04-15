# Standalone-Screen Injection Pattern

**Phase 10 deliverable.** How consumer mods put MenuKit elements into vanilla standalone screens — pause menu, options menus, title screen, world-selection — without the library injecting on their behalf.

---

## Scope

Phase 10's audit surfaced this as Pattern 4 of the five-pattern decomposition. It covers the case where a consumer wants to add a button or small UI to a vanilla standalone screen (no container menu, no slot machinery) — *e.g.*, sandboxes adds an "enter sandbox" button to the pause menu.

Out of scope:

- **Consumer-owned MenuKit-native standalone screens.** A consumer subclassing `MenuKitScreen` to build their own full screen (a custom config screen, a sandbox-management screen) is using the **construction** path served by `MenuKitScreen` directly. It is not injection. See `CONTEXTS.md` for the construction path.
- **Replacing vanilla standalone screens entirely.** Out of scope per the THESIS scope ceiling — MenuKit does not ship a "replace the pause menu" framework.

---

## Principle

Same as inventory-menu injection: **library, not platform.** Consumer mods write their own mixins into the specific vanilla `Screen` subclass they want to decorate, and compose either vanilla widgets or MenuKit elements inside their mixin. MenuKit ships no ambient mixins into `Screen`, no registration API for "decorate this screen class", no global event bus for screen-open events.

The full discussion of the principle, the test (*could a second mod doing something similar still coexist?*), and the cross-mod composition boundary lives in `INVENTORY_INJECTION_PATTERN.md`. The same answers apply here.

---

## Two consumer approaches

Standalone-screen decoration has two viable shapes. Consumers pick based on how rich the decoration needs to be.

### Approach A — vanilla widgets via `Screen.addRenderableWidget`

The lightweight option for a button or two. Vanilla `Screen` exposes `addRenderableWidget(...)` as the canonical way to add widgets that participate in vanilla's render + input dispatch. Consumer mixin into the target screen, hook `init()` (where vanilla widgets are registered), call `addRenderableWidget(new Button.Builder(...).build())`.

**Audit anchor:** sandboxes adds three 11×11 buttons to the inventory-screen-bordering area of the pause menu (enter / back / mode-label). Each is a vanilla `Button` with an icon sprite. No MenuKit involvement on the widget side; sandboxes uses MenuKit elsewhere for its own MenuKitScreen-based sandbox-list screen.

**Why this is the default suggestion:** vanilla's widget machinery is robust, well-integrated with focus + accessibility + input cancellation, and requires no additional library at all. Consumers needing a single button or two should reach for this first.

```java
@Mixin(PauseScreen.class)
public abstract class MyPauseMixin extends Screen {
    protected MyPauseMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void myMod$addEnterButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(
                Component.literal("Enter Sandbox"),
                btn -> /* sandbox enter logic */ {})
            .bounds(this.width - 100 - 4, 4, 100, 20)
            .build());
    }
}
```

### Approach B — MenuKit Panel via `ScreenPanelAdapter`

The richer option when the decoration is more than a button — a panel of multiple elements, supplier-driven dynamic content, custom rendering, hover-triggered tooltips, etc. The same `ScreenPanelAdapter` from `INVENTORY_INJECTION_PATTERN.md` works here unchanged: the adapter is `Screen`-class-agnostic.

The mixin shape mirrors the inventory-menu Pattern 3 example — render TAIL + mouseClicked HEAD on the target standalone screen, with the adapter holding a Panel built at consumer init. The differences:

- **No `leftPos` / `topPos` / `imageWidth` / `imageHeight` to read** — vanilla standalone screens cover the full window. The consumer constructs `ScreenBounds` from `(0, 0, this.width, this.height)` or whatever full-window proxy fits. `ScreenOriginFn` then computes panel origin against window dimensions.
- **No `instanceof` gating typically needed** — the consumer's mixin targets a single specific screen class, not a parent that has many subclasses.
- **`mouseReleased` / `mouseDragged` / `keyPressed` are accessible** — vanilla standalone screens dispatch these the same way as inventory screens. The adapter only handles `render` and `mouseClicked`; consumers needing other input events write the mixin code directly alongside the adapter call.

```java
@Mixin(OptionsScreen.class)
public abstract class MyOptionsMixin extends Screen {
    protected MyOptionsMixin(Component title) { super(title); }

    @Unique
    private final ScreenPanelAdapter myMod$adapter = new ScreenPanelAdapter(
        MyMod.OPTIONS_DECORATION_PANEL,
        bounds -> new ScreenOrigin(bounds.imageWidth() - 100 - 4, 4)  // top-right corner
    );

    @Inject(method = "render", at = @At("TAIL"))
    private void myMod$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        myMod$adapter.render(g, new ScreenBounds(0, 0, this.width, this.height), mx, my);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void myMod$click(MouseButtonEvent event, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
        if (myMod$adapter.mouseClicked(
                new ScreenBounds(0, 0, this.width, this.height),
                event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
```

---

## Failure modes

The four consumer mixin failure modes documented in `INVENTORY_INJECTION_PATTERN.md` apply here unchanged:

1. **Silent-inert dispatch.** A subclass of the targeted standalone screen may override `render` or `keyPressed` without super-calling, hiding the mixin from that subclass. Less common than in the inventory-menu hierarchy because most standalone screens don't have deep subclass chains, but possible — the title screen and pause menu have inherited class trees worth checking before assuming the broad-target works.
2. **Render z-order occlusion.** `@Inject(method = "render", at = @At("TAIL"))` on a parent screen class fires before any subclass continues rendering. For deeper hierarchies, supplementary mixins at the actual subclass declaration point fix this — same shape as the inventory-menu Pattern 3 corner-button supplementary.
3. **`IllegalClassLoadError` on non-mixin classes in mixin package.** Same Fabric class-load rule. Keep non-mixin helpers (the Panel + adapter state) in a sibling package outside the mixins.json's `package` entry.
4. **`@Shadow` on inherited fields in multi-target mixins.** Same Fabric refmap limitation. Single-target mixins or extends-target pattern.

See `INVENTORY_INJECTION_PATTERN.md` § "Consumer mixin failure modes" for the full symptom/cause/fix/example for each.

---

## What ships

Same as inventory-menu injection — `ScreenPanelAdapter` + `ScreenBounds` + `ScreenOrigin` + `ScreenOriginFn` + `ScreenOriginFns`. No additional standalone-specific primitives are needed; the existing `inject/` package is class-agnostic.

`Panel.showWhen(Supplier<Boolean>)` is also useful here for the input-intercept shape (consumer's mixin toggles a boolean in response to a keybind; panel reads via supplier). Same precedence semantics as documented in the inventory-menu doc.

---

## What does NOT ship

Consistent with the inventory-menu doc, by design:

- **No registration API** for "decorate this standalone screen". No `MenuKit.decorate(PauseScreen.class, panel)`.
- **No ambient mixins** into `Screen` or any specific standalone screen.
- **No screen-replacement framework.** Consumers wanting to replace the pause menu or main menu build their own MenuKitScreen subclass and override Minecraft's screen hooks themselves (Fabric provides the necessary entrypoints for that — not MenuKit's job).
- **No defaults.** MenuKit ships zero out-of-box pause-menu buttons, options-screen widgets, or other standalone-screen UI.

Each of these would require MenuKit to take ownership of vanilla code paths it does not need — exactly the platform behavior Phase 5 rewrote out.

---

## Status

Already works as of Phase 10. `ScreenPanelAdapter` is class-agnostic; no standalone-specific code needed. Consumer-side patterns documented above; no library-side action item from this pattern. Phase 11 consumer refactors will validate by porting sandboxes's pause-menu decoration (currently vanilla-Button-based) to either approach.
