# M7 — Chrome-aware MenuContext regions

*(Originally titled "Chrome-aware inventory regions"; renamed during the M8 four-context reframe — see `M8_FOUR_CONTEXT_MODEL.md`. The chrome registry's job is unchanged; what's now called MenuContext is what this doc originally called inventory-context. `InventoryChrome` → `MenuChrome`, `InventoryRegion` → `MenuRegion` throughout.)*

**Status: shipped. M8 renamed `InventoryChrome` → `MenuChrome`; otherwise unchanged.**

Phase 12.5 V2 validation surfaced that regions anchor to the **declared inventory frame** (`imageWidth × imageHeight`) but not to the **effective visible boundary** of the screen — which includes vanilla chrome each `AbstractContainerScreen` subclass hand-draws outside its declared frame. Concrete symptoms:

- `MenuRegion.TOP_ALIGN_RIGHT` in `CreativeModeInventoryScreen` anchors above the declared 195×136 frame but below the creative tab row (TAB_HEIGHT=32 above the frame). Panels overlap tabs.
- `MenuRegion.LEFT_ALIGN_TOP` in `InventoryScreen` with the recipe book open anchors to the left of the declared frame but on top of the recipe book widget, which renders in that space.
- Similar overlap cases in future modded screens with chrome outside their frame.

The frame-edge dynamics already work — `AbstractContainerScreen` mutates `leftPos` / `topPos` on `init()` and on recipe-book toggle, and MenuKit reads those fields per-frame via `AbstractContainerScreenAccessor`. What's missing is the library-owned encoding of "what chrome each screen subclass draws outside its frame."

M7 introduces this encoding as a new library primitive.

---

## 1. Problem — the effective-boundary gap

Vanilla's `AbstractContainerScreen` exposes `leftPos`, `topPos`, `imageWidth`, `imageHeight` — the declared inventory frame rectangle. That's what MenuKit's M5 region system uses today. It's also what `hasClickedOutside` uses, what slot-position math uses, what most of vanilla's own layout math uses.

But many screen subclasses draw additional chrome **outside** that rectangle:

| Screen subclass | Declared frame | Chrome outside frame |
|---|---|---|
| `CreativeModeInventoryScreen` | 195×136 | Top row: tabs (TAB_HEIGHT=32). Bottom row: tabs (TAB_HEIGHT=32). |
| `InventoryScreen` (recipe book closed) | 176×166 | None. |
| `InventoryScreen` (recipe book open) | 176×166 | Left: recipe book widget (~147 px wide). |
| Modded screens | varies | Consumer-specific; library doesn't know. |

Vanilla doesn't expose this chrome via any universal property. Each subclass draws its chrome in `render()` at positions the subclass computes from `leftPos`/`topPos`. There's no `getEffectiveBounds()` method, no `ChromeExtents` field, no pattern the library could pick up automatically.

The consequence for MenuKit's region system: **`MenuRegion.TOP_ALIGN_RIGHT` means "above the declared frame," not "above the visible edge."** In practice, these diverge whenever a subclass has top-row chrome.

## 2. Why this is a library primitive, not a consumer concern

The existing M5 §11 non-goal language reads:
> Vanilla-HUD-element awareness. Regions do not know about vanilla hotbar / XP bar / boss bar / chat. Consumers that need clearance from vanilla HUD use the `.anchor(...)` path with manual offset.

This has been implicitly read as also covering inventory chrome. M7 takes the position that inventory chrome is categorically different from HUD chrome:

- **HUD chrome** (hotbar, XP bar, boss bar, chat) is drawn in the HUD overlay at screen-edge-relative positions. It doesn't move when a container screen opens. A HUD panel that clears the hotbar does so at screen coordinates. Consumers who need hotbar clearance know their HUD panel's geometry and solve it locally via `.anchor(..., offsetY)`.
- **Inventory chrome** (creative tabs, recipe book widget) is drawn by specific `AbstractContainerScreen` subclasses at positions computed from their `leftPos` / `topPos`. It's the same vanilla-screen-specific knowledge the library already encodes elsewhere (M4 vanilla-slot-injection, `MKHasClickedOutsideMixin`, `StorageContainerAdapter`). Each consumer that builds an inventory-context decoration with chrome-aware placement would have to rediscover the same chrome extents.

The library-not-platform principle (THESIS §1) says the library provides mechanisms; consumers provide policy. It does not say the library is ignorant of vanilla. MenuKit already encodes extensive vanilla knowledge — mixins into vanilla classes, subclassing vanilla types, M4's slot-injection primitive. That encoding is the library doing its job: knowing vanilla once so consumers don't each have to.

Leaving inventory-chrome knowledge scattered across consumers fails two tests:
1. **Rule of Three.** Current consumers targeting creative or recipe-book contexts: V2 probes, IP settings gear (already hits creative tab row), sandboxes, any future mod. Chrome-awareness is already Rule-of-Three satisfied.
2. **"Just works" north star.** `TOP_ALIGN_LEFT` should mean the same thing to a consumer in survival as in creative. Without M7, it means "above the frame" in survival and "under the tabs" in creative — two behaviors for one API.

M7 closes this. The library owns inventory-chrome knowledge; consumers read via the existing region system; modded screens extend the registry for their own chrome.

## 2.5 M7 as a Principle 9 instance

THESIS Principle 9 — *"Rendering pipelines are uniform across contexts; embedding is context-specific"* — landed with Phase 12.5's V4 work. Principle 9's test sentence: *"when a rendering behavior varies between contexts, does the variation have a named reason rooted in the screen's relationship to gameplay, not in the container's implementation?"*

M7 is the second concrete instance of Principle 9 forcing a gap closure (after `ScreenPanelAdapter` completeness). Apply the test to the chrome-overlap observation: `MenuRegion.TOP_ALIGN_RIGHT` produces different visible behavior in survival vs. creative. Is there a gameplay-rooted reason? No. The variation is in the container's implementation — each subclass draws chrome in its own render method at subclass-computed positions. That's an implementation detail of the embedding, but the region's *semantic meaning* ("top edge of the visible inventory screen") should be invariant across subclasses.

When a region declaration produces different visible behavior across screen subclasses without a gameplay-rooted reason, that's a Principle 9 violation and the library owns closing it. M7 is this specific closure: a library-owned chrome registry that makes `TOP_ALIGN_RIGHT` mean the same thing everywhere.

This pattern — "region semantics diverge across subclasses without named reason → library closes" — is worth citing in future chrome-shaped discussions.

## 3. API shape

### 3.1 `MenuChrome` registry

New class `com.trevorschoeny.menukit.inject.MenuChrome` with static registry state:

```java
public final class MenuChrome {

    /**
     * Per-side chrome extents outside the declared inventory frame.
     * top/left/right/bottom are non-negative pixel amounts added to
     * the frame's bounds to produce the effective visible boundary.
     */
    public record ChromeExtents(int top, int left, int right, int bottom) {
        public static final ChromeExtents NONE = new ChromeExtents(0, 0, 0, 0);
    }

    /**
     * Computes chrome extents from a live screen instance. Providers take
     * the screen even when static (they just ignore it) so the interface
     * is uniform across dynamic (recipe-book-state-dependent) and static
     * (creative-tabs-constant) cases.
     */
    @FunctionalInterface
    public interface ChromeProvider {
        ChromeExtents compute(AbstractContainerScreen<?> screen);
    }

    /**
     * Registers a chrome provider for a screen class. Exact-class match
     * only — no inheritance walk. Registration happens at mod init; first
     * registration wins (second attempts on the same class are no-op
     * warnings, same pattern as MKSlotState.register).
     */
    public static <T extends AbstractContainerScreen<?>> void register(
            Class<T> screenClass, ChromeProvider provider) { ... }

    /**
     * Returns the chrome extents for the given screen instance. Looks up
     * by screen.getClass() — exact-class only. Returns ChromeExtents.NONE
     * for screens without a registered provider.
     */
    public static ChromeExtents of(AbstractContainerScreen<?> screen) { ... }
}
```

**Exact-class resolution.** A screen's chrome is whatever provider was registered for its concrete class, or `NONE` if no registration. No inheritance walk. Rationale:

- A consumer registering for `AbstractContainerScreen` thinking "I'll catch everything" would accidentally apply that chrome to every screen in existence. Ancestry-walked resolution makes the wrong accident possible.
- Vanilla subclass relationships don't map to chrome relationships. `CreativeModeInventoryScreen extends EffectRenderingInventoryScreen extends AbstractContainerScreen` — inheriting tab-chrome down that hierarchy would mean `EffectRenderingInventoryScreen` gets creative's chrome, which is meaningless.
- Modded "my custom chest extends ChestScreen" cases: if the modded class draws different chrome than its parent, ancestry resolution would pick up the parent's chrome incorrectly. The consumer would have to override to opt out. That's the wrong default.

Exact-class is predictable: every screen gets exactly the chrome registered for its concrete class. Consumers of modded screens register for their own concrete classes. Zero inheritance surprise.

**Transition semantics.** Chrome extents are recomputed per frame (the provider is called each time `RegionRegistry.menuOriginFn` resolves an origin). When a screen's chrome changes mid-session (recipe book toggle), there may be a one-frame visual transition as the provider picks up the new state — vanilla mutates `this.leftPos` in the same event that opens the recipe book, and the provider reads `recipeBookComponent.isVisible()` on the next frame. In practice the one-frame lag is imperceptible. Acceptable for current cases; revisit if longer transitions surface as a consumer concern.

**Dynamic providers are standard.** Providers that need no screen state (creative tabs, constant extents) simply ignore the `screen` parameter. Providers that need state (recipe book toggle) read the screen and return appropriate extents. Unified interface; no static/dynamic API split.

### 3.2 Integration with `RegionMath`

`RegionMath.resolveMenu(region, bounds, pw, ph, prefix)` currently uses `bounds.leftPos` / `topPos` / `imageWidth` / `imageHeight` directly. After M7, the origin functions in `RegionRegistry.menuOriginFn` produce chrome-extended bounds before calling `RegionMath`:

```java
public static ScreenOriginFn menuOriginFn(Panel panel, MenuRegion region) {
    return (bounds, screen) -> {
        ChromeExtents chrome = MenuChrome.of(screen);
        ScreenBounds effective = new ScreenBounds(
                bounds.leftPos() - chrome.left(),
                bounds.topPos() - chrome.top(),
                bounds.imageWidth() + chrome.left() + chrome.right(),
                bounds.imageHeight() + chrome.top() + chrome.bottom());
        // ... existing RegionMath.resolveMenu call using `effective`
    };
}
```

`RegionMath` stays pure. M5 §6.1 invariant preserved: registry state stays out of the math. Chrome logic lives in `MenuChrome` + `RegionRegistry`; `RegionMath` still receives explicit `(bounds, pw, ph, prefix)`.

### 3.3 v1 scope — what the library ships

Library-shipped providers at class-load time (registered in `MenuKitClient.onInitializeClient`):

| Screen class | Chrome extents (shipped values) |
|---|---|
| `CreativeModeInventoryScreen` | `ChromeExtents(25, 0, 0, 26)` — visible-tab edges |
| `InventoryScreen` | dynamic: when recipe book visible and screen ≥ 379 wide, `chrome.left = currentLeftPos − ((screen.width − 147) / 2 − 116)` ≈ 178px for a 427-wide screen (covers book body + filter-tab column) |

**Creative chrome values** are not `TAB_HEIGHT=32`. `TAB_HEIGHT` includes 3-4px of transparent sprite padding around each tab's visible shape; anchoring probes to 32 floats them too far from the visible tab edge. The shipped 25/26 values come from vanilla's own hit-test geometry in `CreativeModeInventoryScreen.checkTabHovering` (21×27 sensitive area at sprite offset +3, +3):
- Top visible tab: `topPos − 25` to `topPos + 2` → 25 px above frame
- Bottom visible tab: `topPos + iH − 1` to `topPos + iH + 26` → 26 px below frame

The 1 px asymmetry matches vanilla's 1 px deeper bottom-tab bias. Probes then land `STACK_GAP=2` past the visible edge — clean 2 px gap either side of the visible tab row.

**Recipe-book chrome** computes chrome.left dynamically from vanilla's `RecipeBookComponent.updateTabs` formula (`tab_left = (screen.width − 147) / 2 − xOffset − 30`, with `xOffset = 86` when the book is visible). That captures the 147 px book body plus the ~31 px filter-tab column on its left. When the book is visible but `screen.width < 379` (vanilla's widthTooNarrow mode overlays the book instead of shifting the frame), the provider returns `NONE` — there's no clean "left of the book" space to anchor to.

That's it for v1. Scope is evidence-driven per Rule of Three: V2 exercises `InventoryScreen` (survival inventory, with and without recipe book toggled) + `CreativeModeInventoryScreen` (creative tabs). Other vanilla screens with recipe-book support — `CraftingScreen`, `SmokerScreen`, `BlastFurnaceScreen`, `FurnaceScreen` — are **candidate additions pending consumer evidence**. V2's completeness pass is expected to exercise one of these to prove the dynamic recipe-book provider pattern generalizes; if it does, v1 scope extends to all four.

**Out of scope for v1:**
- `BrewingStandScreen` potion bubbles, `HorseInventoryScreen` side panels, any non-recipe-book vanilla chrome — no consumer currently targets these.
- Screens that mutate `imageWidth` / `imageHeight` mid-session (none in vanilla 1.21.11).
- Modded screens beyond the vanilla registrations — modded consumers register their own via `MenuChrome.register(MyScreen.class, myProvider)`.

### 3.4 Registration timing

Library-shipped providers register in `MenuKit.onInitializeClient` before any screen opens. Modded consumers register from their own `ClientModInitializer.onInitializeClient()`. Same lifecycle as M1 channel registration and `RegionRegistry` registrations.

No unregister path. Process-lifetime state, same as `RegionRegistry`.

## 4. Consumer API changes

### 4.1 `ScreenOriginFn.compute` — signature change

`ScreenOriginFn.compute(ScreenBounds)` becomes `ScreenOriginFn.compute(ScreenBounds, AbstractContainerScreen<?>)`. The screen parameter is required; origin functions that don't use chrome (custom `ScreenOriginFns.fromScreenTopRight(...)` etc.) simply ignore it.

This is a breaking API change. Per Phase 13a migration discipline, this lands with V4.2's inventory decoration update and Phase 13a's per-decoration review catches any custom origin-function lambdas in current consumers.

### 4.2 `ScreenPanelAdapter.render` — signature change

`ScreenPanelAdapter.render(graphics, bounds, mouseX, mouseY)` becomes `ScreenPanelAdapter.render(graphics, bounds, mouseX, mouseY, screen)`. Screen parameter is required — the adapter passes it through to the origin function.

Same for `ScreenPanelAdapter.mouseClicked(bounds, mouseX, mouseY, button)` → `mouseClicked(bounds, mouseX, mouseY, button, screen)`.

Breaking change. One line per consumer call site to update. Phase 13a review checklist picks it up.

### 4.3 M5 §11 non-goal amendment

Current wording:
> **Vanilla-HUD-element awareness.** Regions do not know about vanilla hotbar / XP bar / boss bar / chat. Consumers that need clearance from vanilla HUD use the `.anchor(...)` path with manual offset.

Amended wording (lands with M7):
> **Vanilla-HUD-element awareness.** Regions do not know about vanilla hotbar / XP bar / boss bar / chat. Consumers that need clearance from vanilla HUD use the `.anchor(...)` path with manual offset. **Inventory chrome** (creative tabs, recipe book widget) is handled by M7 — consumers get chrome-aware region placement automatically; modded screens register their own chrome extents.

## 5. V2 chrome-overlap sub-check — what changes

Pre-M7 proposal was: "switch tabs; inventory probes should stay at the `CreativeModeInventoryScreen`'s frame edges (they'll visually overlap tabs — document as expected, M5 §11 non-goal)."

Post-M7: "switch tabs; inventory probes land at the chrome-extended edges. `TOP_ALIGN_RIGHT` lands above the top tab row, not under it. `BOTTOM_ALIGN_RIGHT` lands below the bottom tab row, not over it. Recipe book toggle on survival: `LEFT_ALIGN_TOP` lands left of the widget, not behind it."

The sub-check ships green because the library closed the gap, not because the test was re-scoped.

## 6. Non-goals

- **Vanilla-chrome-inside-frame awareness.** Some screens have chrome inside the declared frame (furnace flame indicator, brewing stand bubbles above potion slots). These are inside `imageWidth × imageHeight` and overlap with slot positions, not with region anchors. Out of scope.
- **Per-region chrome override.** All regions for a given screen share the same chrome. No `TOP_ALIGN_LEFT_IGNORE_CHROME` variant. Consumers that want flush-frame anchoring on a chrome-having screen use the `.anchor()` lambda path.
- **Chrome detection automation.** No scanning of vanilla render methods to auto-derive chrome. The registry is declaration-based.
- **Chrome-aware layout for `PanelPosition`-based inventory menus.** M5 §7 standalone-screen layout is region-agnostic; M7 changes nothing there. The MenuKit-native inventory-menu layout path (`MenuKitHandledScreen`) uses `PanelPosition`, not regions — also unaffected. M7 is scoped to region-based injection into vanilla screens.
- **HUD chrome awareness.** Explicitly out of scope per amended M5 §11. The screen-based registry model doesn't apply to HUD render — there's no "current screen" during HUD render. If HUD-chrome awareness emerges as a need, it's a separate primitive with its own design pass.

## 7. Implementation plan

1. `MenuChrome.java` — registry class, `ChromeExtents` record, `ChromeProvider` functional interface, `register()` + `of()` methods. Exact-class Map lookup, no ancestry walk.
2. `MenuKit.onInitializeClient` — register the two v1 providers (`CreativeModeInventoryScreen`, `InventoryScreen`).
3. `ScreenOriginFn.compute(ScreenBounds, AbstractContainerScreen<?>)` — signature change.
4. `RegionRegistry.menuOriginFn(panel, region)` — updated lambda consults `MenuChrome.of(screen)` and produces chrome-extended bounds before calling `RegionMath.resolveMenu`.
5. `ScreenPanelAdapter.render(graphics, bounds, mouseX, mouseY, screen)` + `mouseClicked(bounds, mouseX, mouseY, button, screen)` — signature changes. Thread the screen parameter through to the origin function.
6. V4.2's `V4CrossInventoryDecoration` call sites — update `adapter.render` / `adapter.mouseClicked` signatures (one line each).
7. Recipe-book chrome-provider implementation — reads `AbstractRecipeBookScreen`'s recipe-book state via cast + accessor; returns `ChromeExtents(0, 147, 0, 0)` when open, `NONE` when closed.
8. M5 §11 non-goal amendment (§4.3).
9. V2 resumes: main matrix + chrome-overlap sub-check both run against the M7-enabled library.

One commit. Small additive library surface (~60-80 LOC for `MenuChrome` + two providers + `RegionRegistry` lambda update + `ScreenPanelAdapter` signature change). Consumer updates: 2 call sites in V4.2.

## 8. Status

**Shipped.**

Advisor resolutions (round-1 + round-2) all implemented as agreed:
- **Migration**: Option A (breaking signature change, no compat layer) ✓
- **Provider interface**: unified functional interface, screen parameter always present ✓
- **Class resolution**: exact-class only, no ancestry walk ✓
- **v1 scope**: `InventoryScreen` + `CreativeModeInventoryScreen` only ✓
- **Namespace**: flat, `com.trevorschoeny.menukit.inject.MenuChrome` ✓
- **Principle 9 tie-in**: explicit §2.5 added ✓
- **Transition semantics**: documented in §3.1 ✓

**In-flight refinements from V2 testing (beyond what the original design anticipated):**
- Creative chrome values shipped as `25/0/0/26` (visible-tab-edge alignment), not `32/0/0/32` (sprite-edge alignment). `TAB_HEIGHT=32` includes transparent sprite padding; anchoring to 32 floats probes ~3-6px too far from visible tabs. Derived correct values from vanilla's own `checkTabHovering` hit-test geometry (21×27 at sprite offset +3, +3).
- Recipe-book chrome.left shipped as dynamic formula (`currentLeftPos − ((screen.width − 147) / 2 − 116)`), not constant 147. The 147 body width misses the ~31 px filter-tab column on the left. Derived correct formula from vanilla's `RecipeBookComponent.updateTabs` (`tab_left = (width − 147) / 2 − xOffset − 30`).
- Added `widthTooNarrow` short-circuit (`screen.width < 379` returns NONE) — vanilla's overlay mode has no "left of the book" space to anchor to.
- V2's completeness pass (follow-on commit) will exercise one additional recipe-book screen to prove the pattern generalizes to `CraftingScreen` / `FurnaceScreen` / `SmokerScreen` / `BlastFurnaceScreen`.

V4.2's inventory-context decoration un-deferred — now passes visual-parity with HUD and standalone contexts using only library primitives.
