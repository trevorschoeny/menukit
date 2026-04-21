# CONTEXTS

The four rendering contexts MenuKit targets. This document names each context, specifies its composition root, enumerates the context-specific machinery that lives beneath the shared element abstraction, states the concrete guarantees that hold within it, identifies the consumer entry points for building UI in it, and documents the injection pattern consumers use to decorate vanilla UI in that context. It also describes how the four contexts share composition through Panel, names the contexts MenuKit deliberately does not target, and consolidates the failure modes consumer mixins hit when injecting into vanilla.

This document is the canonical reference for which context is which. Subsequent work lifting Panel across contexts, building context-specific containers, deciding whether a new element belongs in one or all contexts, or guiding consumer mods writing injection mixins checks against this document.

---

## What a "context" is

A rendering context, in MenuKit's sense, is a situation in which a consumer wants a panel of elements to render, receive input, and update over time — *and* has enough distinct surrounding machinery that the library treats it as a separate integration point.

Three properties distinguish one context from another:

- **What anchor the panel binds to.** MenuContext binds to the frame of a vanilla `AbstractContainerScreen` (or MenuKit-native equivalent). SlotGroupContext binds to the bounding box of a named slot group, wherever that group renders. HudContext binds to screen edges and the crosshair. StandaloneContext binds to a MenuKit-native screen's main panel (or a vanilla standalone screen for decoration).
- **What update loop drives the container.** MenuContext and SlotGroupContext panels render through the `AbstractContainerScreen` render path (the same library pipeline; different anchor source). HudContext updates through per-frame supplier evaluation in the HUD render callback. StandaloneContext updates through the vanilla `Screen` lifecycle (init / render / tick / removed).
- **What context-specific elements the container requires.** MenuContext (MenuKit-native variant) and SlotGroupContext both involve slot infrastructure. HudContext and StandaloneContext have no slot system.

Each of MenuKit's four contexts has distinct answers to all three. Any candidate context where the answers collapse into an existing context is not a new context — it is a use case *within* an existing context. Anything that cannot answer these three questions (chat messages, F3 debug overlay, world-selection list) is not a context MenuKit targets.

The four-context model is post-Phase-12.5 (M6 reframe). Prior phases used a three-context framing (inventory / HUD / standalone); slot-group anchoring was implicit inside the inventory context. M6 separated it out because "decorate this slot group wherever it appears" is genuinely distinct from "decorate this screen's frame." See `Mechanisms/M6_FOUR_CONTEXT_MODEL.md` for the reframe rationale and `Mechanisms/M4_REGION_SYSTEM.md` for the region machinery beneath both.

---

## MenuContext

The context where a consumer wants UI anchored to the frame of a vanilla container screen — chest, furnace, custom machine — or to a MenuKit-native menu screen.

**Composition root.** Panel. In MenuKit-native menus, a panel holds slot groups *and* elements; in decorations of vanilla menus, a panel holds elements only. The panel's position binds to the screen's `leftPos / topPos / imageWidth / imageHeight` (with chrome adjustments via M5 where applicable).

### Context-specific machinery

- **Server-side handler** (`AbstractContainerMenu` subclass) — for MenuKit-native menus, owns the panel tree, the flat slot list, and the bidirectional coordinate mapping between panel/group/local and flat slot index.
- **Client-side screen** (`AbstractContainerScreen` subclass) — owns per-frame layout, panel background rendering, slot positioning, hover detection, and element click dispatch. MenuKit's library mixins (`MenuKitPanelRenderMixin` + `MenuKitRecipeBookPanelRenderMixin`) inject into the render pipeline so consumer-registered MenuContext + SlotGroupContext adapters dispatch automatically.
- **Slot subclass** (`MenuKitSlot`) — for MenuKit-native menus, carries group membership and coordinates; is a proper `net.minecraft.world.inventory.Slot` to the outside world.
- **Storage abstraction** — block entity, player inventory, ephemeral, item-stack-backed, read-only, virtual. The storage layer is how consumers plug any item container into the slot system.
- **Interaction policy and quick-move participation** attached per slot group.
- **Three-layer shift-click routing** — directional pairings first, then source-aware baseline (player↔container preference), then declared priority.
- **Sync protocol for visibility** — visibility is mutable, changes trigger `broadcastChanges()`; client-initiated toggles go through vanilla's `clickMenuButton` C2S packet. Structure is frozen at handler construction.
- **Handler recognizer registry** — allows consumers to observe non-MenuKit container menus as `SlotGroupLike` views.
- **Region system** (M4) — `MenuRegion` enum (8 values: `LEFT_ALIGN_TOP`, `RIGHT_ALIGN_TOP`, `TOP_ALIGN_LEFT`, etc.) for declarative placement at the screen frame's edges. Auto-stacking for multiple panels in the same region. See `Mechanisms/M4_REGION_SYSTEM.md`.
- **Chrome-aware regions** (M5) — `MenuChrome` registry encodes vanilla chrome drawn outside the declared frame (creative tabs add ~25/26 px above/below; recipe-book widget consumes ~178 px to the left of the survival inventory). Region resolution consults chrome per-frame so `TOP_ALIGN_RIGHT` lands above the visible top edge regardless of which screen subclass hosts it. See `Mechanisms/M5_CHROME_AWARE_REGIONS.md`.
- **Adapter targeting** — `ScreenPanelAdapter.on(Class...)` declares which screen classes the adapter applies to (class-ancestry resolution); `.onAny()` is the explicit "every screen" form. Library-owned `ScreenPanelRegistry` matches per opened screen.

### Guarantees

Five, all empirically verified:

1. **Composability.** MenuContext panels coexist with other mods that mixin into vanilla screen / container / slot classes. MenuKit does not take ownership of those types; it subclasses cleanly.
2. **Vanilla-slot substitutability.** A MenuKit slot is a vanilla slot. Ecosystem mixins into `Slot` affect MenuKit slots identically.
3. **Sync-safety.** Structure is decided once at handler construction and frozen. Visibility syncs through vanilla's protocol via C2S `clickMenuButton`; rendering is client-local. The protocol cannot desync mid-session.
4. **Uniform abstraction.** `SlotGroupLike` is the type consumers program against. Native MenuKit slot groups and observed VirtualSlotGroups both implement it.
5. **Inertness.** Hidden is invisible to the world. Hidden slots return EMPTY, refuse insertion, skip quick-move, render off-screen so vanilla's hover pipeline cannot find them. Phantom items do not exist.

### Consumer entry points

- `MenuKitScreenHandler.builder(menuType)` — declarative MenuKit-native handler construction: panels, groups, elements, pairings, toggle keys.
- `MenuKitHandledScreen` — client-side base for MenuKit-owned menu screens.
- `HandlerRecognizerRegistry.register(Recognizer)` — extending the uniform abstraction to new vanilla or third-party container menus.
- **For decorating vanilla menus**: consumer mixins composing MenuKit elements via `ScreenPanelAdapter` — see the injection pattern below.

### Injection pattern

The pattern for adding panels to vanilla container screens. Use this to drop a sort button on every chest, a settings gear in the inventory, an info panel in a custom modded menu, etc.

**Two construction paths:**

```java
// Region-based — library dispatches via ScreenPanelRegistry.
new ScreenPanelAdapter(panel, MenuRegion.TOP_ALIGN_RIGHT)
    .on(InventoryScreen.class, CreativeModeInventoryScreen.class);
// or .onAny() for every AbstractContainerScreen.

// Lambda-based — consumer's own mixin handles dispatch (escape hatch).
new ScreenPanelAdapter(panel, bounds ->
    new ScreenOrigin(bounds.leftPos() + bounds.imageWidth() - 100, bounds.topPos() - 16));
```

**Region-based** is the canonical path. The adapter declares its target screen classes via `.on(...)` / `.onAny()`; `ScreenPanelRegistry` (a library-owned `ScreenEvents.AFTER_INIT` listener) matches and dispatches render + click per-frame. No consumer mixin needed.

**Lambda-based** is the escape hatch for panels whose anchor doesn't fit any region — most commonly grafted-slot backdrops whose coordinates trace M3 slot positions (see `Mechanisms/M3_VANILLA_SLOT_INJECTION.md` §5.6 shared-constants pattern). Consumer writes their own mixin and calls `adapter.render(...)` / `adapter.mouseClicked(...)` directly. Lambda adapters are exempt from `ScreenPanelRegistry`'s targeting requirement.

**Visibility — supplier-driven canonical, imperative escape hatch.**

```java
private static volatile boolean panelVisible = false;

Panel myPanel = new Panel("my_panel", elements, /*initialVisible*/ true)
    .showWhen(() -> panelVisible);  // canonical: state lives in consumer code
```

Matches the `Toggle.linked` pattern from Phase 9 — state lives in consumer code; library reads via `BooleanSupplier`. `Panel.setVisible(boolean)` remains available as imperative escape hatch but is silently ignored while a `showWhen` supplier is active. Call `showWhen(null)` to revert to imperative mode.

**Click cancellation.** `adapter.mouseClicked(...)` returns whether the click hit an interactive element. Consumer's mixin (lambda path) inspects the return value and decides whether to cancel vanilla's handling via `cir.setReturnValue(true)`. Region-based path defaults to `return true` from `allowMouseClick` (vanilla still sees the click). Future extension `.cancelsUnhandledClicks(boolean)` flagged but not shipped.

### Pattern variants

- **Region anchor at frame corner** — sandboxes' enter/back/mode buttons in `TOP_ALIGN_RIGHT`; IP's settings gear in same. Stacks automatically by registration order.
- **Region anchor at slot-grid edge** — IP's lock overlay sits adjacent to slot grid via `TOP_ALIGN_LEFT` or similar.
- **Lambda-anchor at custom position** — shulker-palette's toggle above the shulker grid (uses `aboveSlotGrid(...)` shape positioning, not a region — menu-internal placement isn't modeled by regions).
- **Lambda-anchor traced to M3 graft coords** — F8 equipment panel backdrop, F15 peek panel backdrop. Shared constants drive both M3 `addSlot()` x/y AND the visual-layer Panel origin; one source of truth.

---

## SlotGroupContext

The context where a consumer wants UI anchored to a slot group's bounding box, wherever that group renders. The same panel appears anchored to `PLAYER_INVENTORY` whether the player is in an inventory screen, a chest, a furnace, or any other menu that exposes that category.

**Composition root.** Panel. In SlotGroupContext, a panel holds elements only; its position binds to the bounding rectangle enclosing all slots in a named category, recomputed per frame (so creative tab switches and similar slot-list mutations take effect on the next frame).

### Context-specific machinery

- **`SlotGroupCategory`** — a labeled identity tag (e.g., `PLAYER_INVENTORY`, `FURNACE_INPUT`, `CHEST_STORAGE`). Pure namespace + path; says nothing about rendering or stacking. 43 vanilla categories ship with the library.
- **`SlotGroupResolver`** — a per-menu-class resolver mapping `AbstractContainerMenu` → `Map<SlotGroupCategory, List<Slot>>`. Library ships 22 vanilla resolvers covering every vanilla 1.21.11 menu (player inventory, chest, shulker, hopper, dispenser, crafting family, furnace family, enchanting/anvil/grindstone/smithing/loom/stonecutter/cartography, brewing, trading, beacon, mounts).
- **`SlotGroupCategories.register(Class, SlotGroupResolver)`** — first-wins registration for consumer-owned menu classes.
- **`SlotGroupCategories.extend(Class, SlotGroupResolver)`** — additive extension for vanilla menu classes (β API from Phase 12.5; lets consumers add categories to library-resolved menus, e.g., for M3-grafted slot groups).
- **`SlotGroupRegion`** — parallel to `MenuRegion`, eight values, applied to the slot-group bounding box rather than the screen frame.
- **`SlotGroupBounds`** — record. Bounds are recomputed per frame against the currently-resolved slots; per-frame resolution lets the system react to mid-session slot-list changes (`CreativeModeInventoryScreen.ItemPickerMenu` is the canonical case).
- **Shared rendering pipeline with MenuContext** — both contexts dispatch through the same `AbstractContainerScreen.render` library mixin (`MenuKitPanelRenderMixin`, plus `MenuKitRecipeBookPanelRenderMixin` for recipe-book-hosted screens). The distinction is anchor source (frame vs. slot-group bounds), not pipeline.
- **No chrome.** Slot groups always render inside the screen frame, so chrome-aware adjustments don't apply.

### Guarantees

The SlotGroupContext analogues of the five disciplines.

1. **Composability.** Slot-group decorations coexist across mods registering different categories on the same menu. Consumer extends a vanilla resolver via `extend(...)` to add their own grafted-slot category; existing library categories are preserved.
2. **Vanilla-pipeline substitutability.** Same render pipeline as MenuContext; same library mixins; ecosystem observation of `AbstractContainerScreen` rendering sees these the same way.
3. **Tick-safety.** Structure declared at registration; runtime re-evaluation through resolvers and per-frame bounds computation only.
4. **Uniform abstraction.** Same Panel + PanelElement types as every other context.
5. **Inertness.** Hidden panels skip render and click dispatch.

### Consumer entry points

- `SlotGroupCategory.PLAYER_INVENTORY` (and 42 other vanilla constants) — pre-registered, ready to target.
- `SlotGroupCategories.register(MyModMenu.class, resolver)` — register a resolver for a consumer-owned menu class.
- `SlotGroupCategories.extend(VanillaMenuClass.class, resolver)` — add categories to a library-registered vanilla menu (e.g., to expose an M3-grafted slot group as a category).

### Injection pattern

```java
new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
    .on(SlotGroupCategory.PLAYER_INVENTORY);
// or .on(CATEGORY_A, CATEGORY_B) — panel renders once per resolved category in the current menu.
```

`.on(SlotGroupCategory...)` declares targeting; **no `.onAny()`** for SlotGroupContext (categories are the point — "any category" isn't a meaningful mental model).

**Multi-category targeting** renders the panel once per resolved category per frame. In a furnace screen, targeting `PLAYER_INVENTORY` and `FURNACE_INPUT` paints the panel twice — once anchored at the player-inventory bounds, once at the furnace input. This is the consumer's declaration honored literally; the library does not silently pick a winner.

**Custom category for grafted slots:**

```java
SlotGroupCategory GRAFTED = new SlotGroupCategory("mymod", "v5_7_grafted");

SlotGroupCategories.extend(FurnaceMenu.class, menu ->
    Map.of(GRAFTED, menu.slots.stream()
        .filter(s -> s.container == MyMod.GRAFTED_STORAGE)
        .toList()));

new SlotGroupPanelAdapter(backdropPanel, SlotGroupRegion.TOP_ALIGN_RIGHT)
    .on(GRAFTED);
```

Use container-reference matching (not last-slot indexing) for grafted-slot identification — works correctly even if other mods graft additional slots after yours. See `Mechanisms/M6_FOUR_CONTEXT_MODEL.md` and the V5_7 design notes in `Phases/12.5/V5_7_EXTEND_RESOLVER_FIX.md` for the design rationale.

**Visibility, click cancellation, lambda-path escape hatch** — same mechanics as MenuContext above.

---

## HudContext

The context where a consumer wants something to render on top of the game while the player is actively playing, without opening a screen.

**Composition root.** Panel. A HUD panel holds elements only (no slot groups). It carries a screen-edge anchor and offset (or a `HudRegion` declarative placement) rather than a constraint against another panel — HUDs render against the game window, not a centered container.

### Context-specific machinery

- **Per-frame render dispatch** via the vanilla HUD rendering pipeline (`HudRenderCallback` or equivalent). The panel renders each frame without ever entering a screen lifecycle.
- **Screen-edge anchoring** via `HudAnchor` (nine-position enum) or `HudRegion` (declarative regions: 9 values including `TOP_LEFT`, `BOTTOM_CENTER`, `CENTER` below crosshair, etc.).
- **Auto-sizing from content.** Panels can declare an explicit size or grow to fit children plus padding.
- **Supplier-driven dynamic content.** All runtime data — text strings, item stacks, progress values, icon identities — comes from `Supplier<T>` fields evaluated at render time. Elements are otherwise stateless definitions.
- **Screen-open visibility policy.** A HUD panel declares whether it stays visible when a screen is open or hides. Default: hide (matches vanilla HUD behavior).
- **Notification subsystem.** A time-bounded element type with slide-in and fade-out animation, triggered at runtime by key and optional text/item data. The only stateful HUD element; animation state held centrally rather than on the element itself.
- **No input dispatch.** HUDs are render-only. MenuKit does not route clicks, keys, or any input to HUD elements. This is a deliberate scope boundary — interactive HUD elements during gameplay would require input dispatch machinery that competes with vanilla input handling and breaks tick-safety. **Consumers wanting interactive overlays during gameplay build a standalone screen** opened by keybind and rendered on top of the game. The HUD is for display; the standalone screen is for interaction.

### Guarantees

1. **Composability.** HUD panels coexist with vanilla HUD rendering and other mods' HUD overlays. MenuKit does not replace vanilla's HUD renderer, does not own the HUD render pipeline beyond registering its own callbacks.
2. **Vanilla-pipeline substitutability.** HUD elements render through the same `GuiGraphics` API and HUD render hooks vanilla exposes.
3. **Tick-safety.** Structure declared once at registration. Runtime re-evaluation through suppliers only; no structural mutation per frame.
4. **Uniform abstraction.** Same Panel + PanelElement types as the other three contexts.
5. **Inertness.** Hidden HUD panels do not evaluate suppliers, do not reserve screen space, do not emit render calls. A panel whose `showWhen` is false costs nothing per frame beyond the predicate evaluation.

### Consumer entry points

- `MKHudPanel.builder(id)` — declarative construction at mod init: anchor (or region), offset, padding, style, elements, visibility predicate.
- Notification builder + runtime trigger — `build()` at init, `notify(key, ...)` at runtime.
- For decorating vanilla HUD elements specifically (drawing on top of the hotbar, replacing the experience bar), consumer mixins into `Gui` compose MenuKit elements directly. MenuKit ships no `Gui` mixins.

### Injection pattern

Already shipped via `MKHudPanel.builder().showWhen(...)`. The pattern: consumer-owned predicate gates visibility; supplier-driven content updates per frame.

```java
private static volatile boolean hintActive = false;

@Override
public void onInitializeClient() {
    MKHudPanel.builder("my_action_hint")
        .anchor(HudAnchor.BOTTOM_CENTER)
        .offset(0, -32)
        .padding(4)
        .style(PanelStyle.DARK)
        .showWhen(() -> hintActive)
        .text(() -> Component.literal("[Right-click] commit"))
        .build();

    // Consumer flips `hintActive` from wherever the trigger comes from —
    // ItemUseCallback, KeyBindingHelper, ClientTickEvents, S2C packet, etc.
}
```

Or with declarative region placement:

```java
MKHudPanel.builder("pocket_hud")
    .region(HudRegion.BOTTOM_CENTER)
    .style(PanelStyle.NONE)
    .showWhen(() -> isPocketOpen())
    .build();
```

### Positioning relative to vanilla HUD elements

Consumers anchoring HUD panels near vanilla HUD elements (hotbar, experience bar, food bar, chat) pick anchors and offsets that don't overlap. The library does not lay out HUD panels around vanilla elements automatically — the consumer chooses the anchor/offset and accepts responsibility for the overlap question.

A few practical anchors:

- `BOTTOM_CENTER` with negative Y offset — sits above the hotbar (clear of vanilla's item-name and recipe-book hint area). Used by agreeable-allays for action hints. Choose offsets large enough to clear vanilla's hotbar overlay region (~40 px above hotbar).
- `TOP_RIGHT` with small offsets — sits in the area vanilla uses for boss bars and overlay messages. Less reliably clear; consumers using this should accept potential vanilla overlap.
- `MIDDLE_LEFT` / `MIDDLE_RIGHT` — typically clear of vanilla HUD content; good for status indicators.

For precise per-vanilla-element positioning ("above the experience bar specifically"), consumers either hardcode known offsets per Minecraft version or mixin into `Gui` to read live values — vanilla doesn't expose its HUD layout as a public coordinate API.

### Predicate cost note

Predicate-driven visibility means the supplier runs every frame the panel is potentially visible. Heavy work in the predicate (item lookups, network calls, complex state checks) compounds across frames. Keep the predicate cheap; if it depends on expensive state, cache the result on a tick callback and read the cache in the supplier.

---

## StandaloneContext

The context where a consumer wants a full-screen, client-local, interactive UI without a container menu — a custom main-menu replacement, a settings-like screen, an in-game UI opened by keybind that isn't tied to a block or entity. Or for decorating vanilla standalone screens (pause menu, options menus).

**Composition root.** Panel. A standalone-screen panel holds elements only (no slot groups). For MenuKit-native standalone screens, panels use the same constraint-based `PanelPosition` layout as menu panels so multi-panel screens compose the same way.

### Context-specific machinery

- **Screen subclass** (`net.minecraft.client.gui.screens.Screen`, not `AbstractContainerScreen`) that owns the panel tree, per-frame layout, and full input dispatch.
- **Screen lifecycle integration**: `init()` builds layout state against the current screen size; resize re-invokes `init()`; `removed()` clears listeners. The screen lifecycle is vanilla's; MenuKit does not replace it.
- **Full input dispatch**: `mouseClicked`, `mouseReleased`, `mouseDragged`, `keyPressed`, `charTyped`, `mouseScrolled` all route through the panel tree before falling through to vanilla defaults.
- **No slot system.** No sync protocol, no container menu, no server-side counterpart. Standalone screens are entirely client-local unless the consumer opens their own networking.
- **`StandaloneRegion`** enum + reserved API. Solver deferred until a concrete consumer surfaces (Phase 13/14 candidate). MenuKit-native standalone screens currently use `PanelPosition` constraints (BODY / RIGHT_OF / LEFT_OF / ABOVE / BELOW) directly.
- **Optional pause-the-game behavior.** Standard vanilla `Screen` decision — consumers override `isPauseScreen()` per their use case.
- **Escape closes the screen.** Standard vanilla behavior; consumers override `shouldCloseOnEsc()` if needed.

### Guarantees

1. **Composability.** MenuKit standalone screens coexist with other screens, other mods' screens, and vanilla's screen management. MenuKit does not take ownership of the current-screen slot or the screen stack.
2. **Vanilla-screen substitutability.** A MenuKit standalone screen *is* a `net.minecraft.client.gui.screens.Screen`. Ecosystem mixins into `Screen` affect MenuKit standalone screens identically.
3. **Lifecycle-safety.** Structure declared at screen construction and frozen. `init()` and resize recompute layout only; `removed()` does not leave listeners attached. Open / resize / close cycles run without structural drift.
4. **Uniform abstraction.** Same Panel + PanelElement.
5. **Inertness.** Hidden elements do not render, do not receive input, do not contribute to layout. Closed screens have no residual presence.

### Consumer entry points

- `MenuKitScreen` base class — subclass with panels declared in construction, optional title and pause-behavior overrides.
- For decorating vanilla standalone screens (pause menu, options screens), consumer mixins compose MenuKit elements via `ScreenPanelAdapter` (same class as MenuContext; class-agnostic) OR via vanilla's `Screen.addRenderableWidget` for simpler cases.

### Injection pattern

Two viable shapes — pick by decoration richness.

#### Approach A — vanilla widgets via `Screen.addRenderableWidget`

The lightweight option for a button or two. Vanilla `Screen` exposes `addRenderableWidget(...)` as the canonical way to add widgets that participate in vanilla's render + input dispatch. Consumer mixin into the target screen, hook `init()` (where vanilla widgets are registered), call `addRenderableWidget(new Button.Builder(...).build())`.

Sandboxes uses this for its three pause-menu buttons (enter / back / mode-label).

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

**Default suggestion** for single-button decorations. Vanilla's widget machinery is robust, well-integrated with focus + accessibility + input cancellation, requires no additional library.

#### Approach B — MenuKit Panel via `ScreenPanelAdapter`

The richer option when the decoration is more than a button — a panel of multiple elements, supplier-driven dynamic content, custom rendering, hover-triggered tooltips. Same `ScreenPanelAdapter` from MenuContext works here unchanged: the adapter is `Screen`-class-agnostic.

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

Differences from MenuContext injection:

- **No `leftPos / topPos / imageWidth / imageHeight` to read** — vanilla standalone screens cover the full window. Consumer constructs `ScreenBounds(0, 0, this.width, this.height)`. `ScreenOriginFn` then computes panel origin against window dimensions.
- **No `instanceof` gating typically needed** — consumer's mixin targets a single specific screen class, not a parent with many subclasses.
- **`mouseReleased` / `mouseDragged` / `keyPressed` accessible** — vanilla standalone screens dispatch these the same way as inventory screens. The adapter handles `render` and `mouseClicked`; consumers needing other input events write the mixin code directly alongside.

---

## Cross-context composition

Panel is the composition root in all four contexts. PanelElement is the element interface in all four contexts. A Button written once works in any context it is placed in. This is the center of MenuKit's identity as a component library — the composition unit and the element vocabulary are shared, and the context-specific machinery lives beneath.

Three practical consequences:

**What panels hold differs by context.** A MenuKit-native MenuContext panel holds slot groups *and* elements. Decoration panels (in any context) hold elements only. SlotGroupContext panels hold elements only (the slot group is the anchor, not the panel content). HUD and standalone panels hold elements only.

**What panels know differs by context.** A panel does not know which context it is in. Position metadata (region, lambda-anchor, screen-edge anchor, body-stacked, relative-to-anchor) is carried on the panel, but the panel does not reach for context-specific machinery to interpret it. The container holding the panel — the screen, the HUD dispatch, the standalone screen — interprets position metadata according to its own layout rules.

**What containers provide differs by context.** MenuContext containers provide slot sync, shift-click routing, cursor handling, full input dispatch (for MenuKit-native menus). SlotGroupContext shares the MenuContext rendering pipeline but reroutes anchor computation. HudContext provides per-frame iteration and auto-sizing — and nothing else (HUDs are render-only). StandaloneContext provides screen lifecycle and full input dispatch.

A corollary: interactive elements have their interactive behavior only in contexts with input dispatch. Consumers who want click-driven behavior during gameplay build a standalone screen, not a HUD. The palette document specifies per element whether it is meaningful on HUDs at all.

The target state of the library is that a consumer writing an element (Button, Toggle, ProgressBar, Icon, Tooltip) can do so without caring which context it will end up in. Context-specific elements — SlotGroup is the canonical case — declare their scope explicitly in the palette and do not transplant.

---

## What is not a context

Several rendering situations in Minecraft are not MenuKit contexts.

**Config UIs** — Cloth Config owns this space. MenuKit's StandaloneContext is the appropriate home for any consumer wanting a config-like screen built from primitives, but MenuKit ships no config-as-a-context with field widgets and save pipelines.

**Chat** — heavy vanilla ownership (message pipeline, command suggestions, auto-complete), narrow cross-consumer demand, no compositional value from sharing vocabulary. Consumers who want to decorate the chat HUD write their own mixins; MenuKit does not recognize chat as a target.

**F3 debug overlay** — narrow vanilla feature with narrow ownership. Consumers wanting custom debug text use a HUD panel; consumers wanting to modify F3 itself do so outside MenuKit.

**World-selection / server-selection / realms** — narrow vanilla screens with specific workflows. Consumers wanting to decorate them write their own mixins and use MenuKit elements inside (the same StandaloneContext injection pattern).

**Main menu and pause menu, specifically** — not MenuKit contexts in the sense that MenuKit does not target them as a category. They are instances of vanilla standalone screens; consumers use the StandaloneContext consumer-mixin pattern.

**Game-world rendering** — rendering things in the 3D world rather than on the 2D screen — is not a MenuKit context and not a rendering context in any meaningful sense here. MenuKit's scope is the 2D GUI layer.

The four contexts — MenuContext, SlotGroupContext, HudContext, StandaloneContext — are exhaustive for MenuKit. A candidate context outside that set is either a use case within an existing context or out of scope.

---

## Common injection failure modes

Four failure modes consumers hit repeatedly when decorating vanilla screens via MenuContext or StandaloneContext injection. All are Fabric/vanilla constraints rather than MenuKit concerns; the library cannot shield consumers from them but documents them so consumers recognize the symptoms when they hit. (HudContext injection sidesteps most of these — there's no consumer mixin into a vanilla screen, no `@Shadow` of inherited fields, no class-load rule.)

### 1. Silent-inert dispatch (no-super-call override)

**Symptom.** A mixin at `@Inject(method = "render"|"mouseClicked"|"keyPressed", at = @At("HEAD"))` on a parent class installs cleanly — no mixin error at startup — but never fires when the player is inside a specific subclass. The decoration is invisible or unresponsive on that screen only.

**Cause.** An intermediate class in the subclass's hierarchy overrides the method without calling `super.foo(...)`. The dispatch chain stops at the override; the parent method — and your mixin on it — never runs.

**Concrete example.** `InventoryScreen` extends `AbstractRecipeBookScreen` extends `AbstractContainerScreen`. `AbstractRecipeBookScreen.keyPressed` overrides the method to handle the recipe-book toggle and does not super-call. A mixin on `AbstractContainerScreen.keyPressed` therefore never fires in survival inventory — the dispatch stops at `AbstractRecipeBookScreen`.

**Fix pattern.** Supplementary `@Mixin(<parent-at-actual-declaration>.class)` plus runtime `instanceof <intended-subclass>` gate, with HEAD cancellation via `cir.setReturnValue(true)` to prevent double-toggle if vanilla ever starts super-calling. Per-method and per-subclass — and sometimes per-runtime-state. The same hierarchy can super-call for one method and not for another, or super-call conditionally depending on widget state. Each hook must be tested independently and treated as an empirical observation, not a design contract.

### 2. Render z-order occlusion

**Symptom.** A decoration renders but is covered by vanilla UI drawn after it — a tab icon, a recipe-book widget, a side panel. The decoration exists; you just can't see it.

**Cause.** `@Inject(method = "render", at = @At("TAIL"))` on a parent class fires when the parent's render method returns — but the subclass's render may call `super.render(...)` from the middle of its body and continue drawing afterward. TAIL on the parent is not TAIL on the whole render pass.

**Concrete example.** A consumer's text label renders at TAIL of `AbstractContainerScreen.render`. In survival inventory, `AbstractRecipeBookScreen.render` calls super then continues to draw the recipe-book widget, overlaying the text. In creative inventory, `CreativeModeInventoryScreen.render` calls super then continues to draw tab icons on top.

**Fix pattern.** Supplementary `@Mixin(<subclass-that-overrides-render>.class)` at `@Inject(method = "render", at = @At("TAIL"))`. The subclass's render TAIL is the true end of its draw pass; injecting there guarantees on-top rendering.

**Convergence note.** For render hooks, "silent-inert dispatch" and "z-order occlusion" often converge — the supplementary-render-at-the-override-class fix resolves both. Consumers planning any render decoration in a subclass that overrides render should add the supplementary proactively.

**Library-level fix already shipped:** `MenuKitPanelRenderMixin` + `MenuKitRecipeBookPanelRenderMixin` together cover the render-pipeline coverage gap for region-based adapters dispatching through `ScreenPanelRegistry`. Consumers using the lambda path (or hitting other vanilla screens with no-super-call overrides) still write their own supplementary mixins.

### 3. `IllegalClassLoadError` on non-mixin classes in the mixin package

**Symptom.** `Mixin transformation of <YourHelperClass> failed ... IllegalClassLoadError: <class> is in a defined mixin package ... and cannot be referenced directly`. Crashes occur not at mixin load but at first reference from transformed code — often when the player opens the target screen for the first time.

**Cause.** The `package` entry in your `*.mixins.json` declares a mixin-only package. Fabric's class loader refuses to load non-mixin classes from that package when referenced by transformed target code.

**Fix pattern.** Keep non-mixin helpers in a sibling package outside the mixins.json's `package` entry. Common convention: `mymod.mixin.X` for mixins, `mymod.shared.X` (or similar) for non-mixin helpers referenced by them.

### 4. `@Shadow` on inherited fields in multi-target mixins

**Symptom.** `@Shadow field <fieldName> was not located in the target class ... No refMap loaded.` Multi-target mixin fails to apply (or applies with broken field references), and the decoration misfires where the target is a subclass inheriting the field.

**Cause.** Fabric's refmap cannot remap a `@Shadow` to an inherited field across multiple target classes simultaneously. `@Shadow` on a field declared directly on the single target works; `@Shadow` on a field inherited from a parent, in a multi-target mixin, does not.

**Fix patterns (two options).**
- **Single-target broad-parent mixin.** `@Mixin(AbstractContainerScreen.class)` plus `instanceof` runtime gate for scoping. `@Shadow` works trivially because the field is declared on the target.
- **Extends-target pattern when you must target the subclass.** `@Mixin(SubClass.class) public abstract class MyMixin extends ParentWhereFieldIsDeclared<...>`. A dummy constructor satisfies the compiler; the mixin class is never instantiated, so the constructor body doesn't matter. The inherited field becomes visible through Java's inheritance rather than through mixin's field resolution.

### Targeting multiple screen classes — the broad-target + runtime-gate pattern

When decorating multiple screen variants (e.g., `InventoryScreen` and `CreativeModeInventoryScreen`), target the abstract parent where the methods are declared, then use a runtime `instanceof` check to narrow behavior:

```java
@Mixin(AbstractContainerScreen.class)
public abstract class MyInventoryMixin extends AbstractContainerScreen<AbstractContainerMenu> {

    @Unique
    private boolean myMod$appliesToThisScreen() {
        Object self = this;
        return self instanceof InventoryScreen
                || self instanceof CreativeModeInventoryScreen;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void myMod$render(...) {
        if (!myMod$appliesToThisScreen()) return;
        adapter.render(...);
    }
}
```

The mixin injects into `AbstractContainerScreen`'s declared methods (which every container screen inherits), and the `instanceof` gate narrows runtime behavior. Works uniformly whether the subclasses override the injected method or not.

### Split mixins are the default

Decorations targeting multiple inventory variants almost always require **multiple mixin classes**, not one. Each hook a decoration uses (render, mouseClicked, keyPressed) may be declared at a different level of the hierarchy. The realistic shape: **one mixin per (hook, actual declaration point)**, sharing a non-mixin state holder and joined by runtime `instanceof` gates.

A typical decoration spanning survival + creative inventory looks like: one primary mixin on `AbstractContainerScreen` (broad-target + instanceof gate) + one or two supplementary mixins on `AbstractRecipeBookScreen` (render-only and/or keyPressed-only, for the silent-inert / z-order fixes). Three mixin classes plus a shared non-mixin state holder is the realistic floor.

---

## Cross-mod composition

Multiple consumer mods may want to decorate each other's UI (e.g., shulker-palette adding a toggle to inventory-plus's peek panel). **This is not MenuKit's to mediate.**

Under library-not-platform discipline:

- The owning consumer mod (e.g., inventory-plus) may expose a public Java API for third-party mods to decorate its UI — e.g., `InventoryPlus.addPeekDecorator(Predicate<ItemStack>, PanelElement)`.
- The decorating mod imports the owning mod's public API and calls it directly. No MenuKit involvement.
- Alternative: the decorating mod writes its own mixin into the owning mod's class. Mixin composition handles coexistence.
- Alternative: the two mods agree on a third-party contract (a service loader, a Fabric entrypoint, an event-bus library neither of them is MenuKit). MenuKit doesn't care.

**What MenuKit does not ship:** a decorator registry, a region-match injection system, a cross-mod event bus, or any library-mediated composition mechanism for consumer mods decorating each other's UI. The old architecture had such machinery (`MenuKit.buttonAttachment(...)` with region-name matching across consumer mods). It was platform behavior. It is gone.

---

## What does NOT ship for any context

Explicitly, by design:

- **No registration API** for "decorate this screen / HUD / standalone." No `MenuKit.register(...)`. No `@DecoratesScreen(...)` annotation.
- **No ambient mixins** into vanilla screen classes, `Gui`, or any vanilla rendering pipeline.
- **No defaults.** MenuKit does not ship default sort buttons on chests, settings buttons on the inventory, hotkey hints on the HUD, or any consumer-targeted UI defaults. All such UI is consumer work, composed from MenuKit primitives.
- **No cross-mod composition registry** (see above).
- **No config-driven injection.** No "set flag X in MenuKit config to show panel Y on screen Z."

Each of these, if shipped, would require MenuKit to take ownership of code paths it does not need.

---

## Summary

MenuKit targets four contexts. Each has its own anchor source, container, and context-specific machinery. All four share Panel as the composition root, PanelElement as the element interface, and the five disciplines from the thesis as guarantees (instantiated as five context-specific guarantees per context). Elements written against the shared abstraction work uniformly across the four; context-specific elements (SlotGroup) declare their scope explicitly.

| Context | Anchor | Composition pipeline | Input | Region enum |
|---|---|---|---|---|
| MenuContext | Vanilla screen frame (chrome-aware) | `AbstractContainerScreen` render via library mixin | Full | `MenuRegion` (8) |
| SlotGroupContext | Slot-group bounding box (per-frame) | Same as MenuContext | Full | `SlotGroupRegion` (8) |
| HudContext | Screen edges + crosshair | HUD render callback | Render-only | `HudRegion` (9) |
| StandaloneContext | MenuKit-native main panel OR vanilla `Screen` | Vanilla `Screen` lifecycle | Full | `StandaloneRegion` (8, solver deferred) |

For each context, the consumer's question is *"what am I anchoring to?"* — the answer picks the context, and the shared element vocabulary slots in. Injection patterns documented per context above; failure modes consolidated in the appendix above. Any future work introducing a new element, generalizing an abstraction across contexts, or deciding whether a use case belongs to MenuKit at all checks against this document first.
