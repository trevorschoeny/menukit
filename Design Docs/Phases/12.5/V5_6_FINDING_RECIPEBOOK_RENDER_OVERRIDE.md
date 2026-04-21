# V5.6 finding — `AbstractRecipeBookScreen.render` override shadows library render pipeline

**Surfaced during:** V5.6 manual smoke — opening a crafting table in survival.
**Status: confirmed library bug; Option 3 disposition per advisor.** Post-V2 rendering pipeline (`MenuKitPanelRenderMixin` injecting at `AbstractContainerScreen.render` INVOKE `renderCarriedItem`) is silent-inert for all five `AbstractRecipeBookScreen` subclasses. Fix: second library-internal mixin on `AbstractRecipeBookScreen.render`, ~10 lines, matching the existing mixin's render-ordering semantics.

---

## Symptom

`/mkverify v5` green (V5.1 unchanged). V5.6's handler-layer graft fires correctly — the log confirms `[V5.6] Grafted slot into CraftingMenu @ (180,30); total slots now 47` each time a crafting table is opened, server AND render thread. Slot count 47 matches 46 vanilla (9 craft grid + 1 result + 27 inv + 9 hotbar) + 1 grafted.

**Visual layer renders nothing.** No slot sprite visible at (180, 30), no INSET backdrop panel. Trevor's screenshots confirm a completely-vanilla crafting-table UI.

**Diagnostic logs in the render mixin never fire.** `V5CraftingScreenMixin.mkvalidator$renderV5Backdrop` logs `[V5.6] Mixin handler entered first time; screen={cls}` unconditionally on first entry — never appears in the log across multiple crafting-table opens. The mixin applies (no crash at boot; build succeeds with `defaultRequire: 1`) but the handler is never called.

## Root cause hypothesis — `AbstractRecipeBookScreen.render` override shadows `AbstractContainerScreen.render`

My mixin targets `AbstractContainerScreen.render(GuiGraphics, int, int, float)` at `@At("TAIL")`. `CraftingScreen` extends `AbstractRecipeBookScreen<CraftingMenu>` (not `AbstractContainerScreen` directly). Bytecode inspection of `AbstractRecipeBookScreen.render` (decompiled via `javap -c`) shows:

```
public void render(GuiGraphics, int, int, float):
  if (recipeBookComponent.isVisible() && widthTooNarrow) {
    this.renderBackground(...)
  } else {
    super.renderContents(...)             ← invokespecial → AbstractContainerScreen.renderContents
  }
  graphics.nextStratum();
  recipeBookComponent.render(...);
  graphics.nextStratum();
  this.renderCarriedItem(...);
  this.renderSnapbackItem(...);
```

`AbstractRecipeBookScreen.render` replaces the render pipeline entirely — it does NOT call `super.render(...)`. Instead it invokes `AbstractContainerScreen.renderContents(...)` directly. My TAIL hook on `AbstractContainerScreen.render` never executes for screens that go through `AbstractRecipeBookScreen`.

Screens affected: `InventoryScreen` (survival inventory), `CraftingScreen` (crafting table), `FurnaceScreen`, `SmokerScreen`, `BlastFurnaceScreen` — all of `AbstractRecipeBookScreen`'s subclasses. My V5.6 backdrop target (CraftingScreen) is one of them.

## Why IP's gear works while my backdrop doesn't

IP's `InventoryContainerMixin` uses the same pattern as my V5CraftingScreenMixin — `@Mixin(AbstractContainerScreen.class)` + `@Inject(method = "render", at = @At("TAIL"))`. But IP ships TWO mixins in its inject manifest:

- `InventoryContainerMixin` — targets `AbstractContainerScreen.render` (catches containers that do go through super — chest, shulker, hopper, furnace variants when the recipe book is closed, etc.)
- `RecipeBookMixin` — targets `AbstractRecipeBookScreen.render` (catches screens that skip super — InventoryScreen, CraftingScreen, etc.)

IP's `INVENTORY_INJECTION_PATTERN.md` explicitly documents this as **failure mode #1 — silent-inert dispatch**: "consumer decorations spanning multiple inventory variants typically need multiple mixin classes (one primary + supplementaries per silent-inert hook), not one. Realistic floor: 1 primary + 2-3 supplementaries + 1 shared state holder for any decoration touching survival inventory through `AbstractRecipeBookScreen`."

I tripped over this exact pattern-gotcha. My V5.6 needs a supplementary mixin on `AbstractRecipeBookScreen.render` (since CraftingScreen's effective render comes from there) in addition to — or instead of — the `AbstractContainerScreen.render` target.

## Structural question for advisor — is this a library gap or just a validator oversight?

The "split mixins as default" pattern is documented in IP's injection-pattern doc but isn't enforced or surfaced by the library. A consumer writing their first injection decoration (as V5.6 effectively did — the validator as a minimal consumer) would reasonably reach for a single `AbstractContainerScreen.render` target and silently miss 5+ screen types. The failure is diagnostically invisible — no mixin error, no apply failure, just "my decoration doesn't render on some screens."

Three ways to read this:

1. **Validator oversight.** V5.6 should have checked the injection-pattern doc before targeting — "silent-inert dispatch" is explicitly failure-mode-#1 for a reason. Fix is in V5CraftingScreenMixin: either (a) switch target to `AbstractRecipeBookScreen.render`, or (b) add a supplementary mixin covering both (IP's approach). No library change. Phase 12.5's purpose is still served — "validator catches library incompleteness through realistic usage" applies to consumer-side pattern-gotchas that are documented but easy to miss, not just library primitive gaps.

2. **Library documentation gap.** The pattern is documented in `menukit/Design Docs/Architecture Design Docs/INVENTORY_INJECTION_PATTERN.md` — Phase 10's output — but nothing on the `ScreenPanelAdapter` or `@Mixin` javadoc surface nudges consumers toward it at the obvious-point-of-use. Consumer with IDE open + library javadoc visible wouldn't see the pattern-level guidance until they grep for it after a failure. Possible fix shape: a note on `ScreenPanelAdapter`'s class javadoc calling out the `AbstractRecipeBookScreen` trap and linking to the pattern doc.

3. **Library primitive gap — missing "render-on-any-container-screen" helper.** Deeper claim: if every real consumer (IP, now validator) has to split-mixin to cover both render paths, and if Phase 10's pattern doc enumerates the minimum-viable-count as "1 primary + 2-3 supplementaries", maybe the library should ship a render-hook primitive that abstracts both paths. Shape: a `MenuKitScreenRenderHook` that consumers register against, library owns the mixin plumbing on both `AbstractContainerScreen.render` and `AbstractRecipeBookScreen.render`, consumers just get a callback. Would be the 5th primitive gap caught in Phase 12.5 (after V4 ScreenPanelAdapter completeness, V2 tooltip layering, M7 chrome awareness, V5 pairsWith-builder). Fits the pattern.

My pull is (1)-with-flavor-of-(2): ship the V5.6 fix (target switch), and add a library-level javadoc note on the obvious-consumption surface (ScreenPanelAdapter's class javadoc) to preempt the next consumer tripping over this. (3) is a bigger architectural move that earns a separate design pass if the evidence accumulates — Rule of Three on primitive gaps suggests waiting for a third real consumer to split-mixin before library abstraction.

## Proposed V5.6 fix

Two-mixin shape matching IP:

- `V5CraftingScreenMixin` retarget from `AbstractContainerScreen.render` → `AbstractRecipeBookScreen.render`. Correct target for CraftingScreen-family.

Since V5.6's only goal is the crafting-table backdrop, one mixin on `AbstractRecipeBookScreen.render` covers it. No supplementary needed. If V5.6 later extends coverage to non-recipe-book screens (ChestScreen etc.), a second supplementary mixin on `AbstractContainerScreen.render` gets added.

Optional: add a javadoc note on `ScreenPanelAdapter` pointing at `INVENTORY_INJECTION_PATTERN.md`'s failure mode #1. Low-effort library-side improvement.

---

## Advisor pre-check: does the library's own pipeline have this gap?

**Yes, confirmed on two independent evidence paths.**

**Evidence 1 — bytecode.** `menukit/src/main/java/com/trevorschoeny/menukit/mixin/MenuKitPanelRenderMixin.java` targets `AbstractContainerScreen.render` at `@At INVOKE target = "...renderCarriedItem(...)"`. For `AbstractRecipeBookScreen` subclasses the render method is the override in `AbstractRecipeBookScreen`, which (per decompilation) calls `renderContents(...)` directly via `invokespecial`, then `recipeBookComponent.render(...)`, then `renderCarriedItem(...)` — all within its own body, never invoking `super.render(...)`. The library mixin's inject point lives in a method that's never called for recipe-book screens. Silent-inert dispatch, Phase 10 failure mode #1 exactly.

**Evidence 2 — my V5.6 diagnostic empirically reproduces the library's failure.** My `V5CraftingScreenMixin` uses the same target (`AbstractContainerScreen.render` + `@At TAIL`) as IP's `InventoryContainerMixin`, and adds an unconditional log on first entry. Across multiple crafting-table opens the log never fires. The injection path the library also uses doesn't dispatch for CraftingScreen-class screens. Transitive proof: library's `MenuKitPanelRenderMixin` wouldn't fire either, for the same structural reason.

**Affected screens** (all extend `AbstractRecipeBookScreen`): `InventoryScreen` (survival inventory), `CraftingScreen`, `FurnaceScreen`, `SmokerScreen`, `BlastFurnaceScreen`. Highest-traffic screens in the game. `CreativeModeInventoryScreen` extends `EffectRenderingInventoryScreen` → `AbstractContainerScreen` (not the recipe-book path), so the library pipeline works there — explaining why V4's cross-inventory panel smoked green on creative but probably was never validated on survival.

## Disposition — Option 3 (library fix)

Ship a second library-internal mixin on `AbstractRecipeBookScreen.render`, delegating to the same `ScreenPanelRegistry.renderMatchingPanels` dispatch that `MenuKitPanelRenderMixin` calls. Principle 11's exhaustive-coverage exception applies: per-item cost is one mixin class (~10 lines, same handler body); incompleteness cost is Phase 13 consumers silently losing coverage on five of the highest-traffic screens. Library-not-platform: `ScreenPanelRegistry` owns screen dispatch → it owns knowing that vanilla's render pipeline has two entry points for container screens.

### Fix shape

```java
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MenuKitRecipeBookPanelRenderMixin {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            shift = At.Shift.AFTER
        ),
        require = 1
    )
    private void menuKit$renderPanels(GuiGraphics graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        ScreenPanelRegistry.renderMatchingPanels(self, graphics, mouseX, mouseY);
    }
}
```

Injection point rationale: right after `renderContents` returns (slots + labels drawn), BEFORE the first `GuiGraphics.nextStratum()` call. Panels render on the base stratum alongside slots — above them, below the recipe-book overlay if visible, below cursor and tooltips. Matches `MenuKitPanelRenderMixin`'s layering exactly: panels-above-slots, cursor-above-panels, tooltips-on-top.

Narrow-window-with-recipe-book-open path (`widthTooNarrow + recipeBookComponent.isVisible` → else-branch skipped, `renderContents` not called) won't fire panels in this fix. That's correct behavior — slots aren't rendered either in that path, so rendering panels would be out of place.

### V5.6-scenario fix

Separate small change — retarget V5.6's consumer-facing backdrop mixin from `AbstractContainerScreen.render` to `AbstractRecipeBookScreen.render` with the analogous injection point. V5.6's lambda-based adapter is opted out of `ScreenPanelRegistry`'s dispatch (per ScreenPanelAdapter's "lambda-based adapters don't participate" documentation), so it needs its own mixin either way. The library fix addresses region-based adapters; V5.6 uses the lambda path.

### Phase 12.5 close-out REPORT update

This is the **fifth primitive gap caught by Phase 12.5's validator-consumer pattern**, alongside:

1. V4 `ScreenPanelAdapter` completeness (background render, padding, overflow diagnostics, origin accessor)
2. V2 tooltip layering (`MenuKitPanelRenderMixin` itself — ironic, V2's fix spawned the sibling bug)
3. M7 chrome awareness
4. V5.1 `PanelBuilder.pairsWith` exposure
5. **V5.6 `AbstractRecipeBookScreen` render-pipeline coverage** (this finding)

Pattern worth naming explicitly in the close-out REPORT: *the validator-consumer pattern catches library incompleteness that isolated primitive tests miss — including incompleteness introduced by earlier phase-internal fixes that shipped with partial screen coverage*. V2's tooltip-layering fix landed a rendering primitive with 5 silent-inert screens; none of Phase 12's checks surfaced it because no scenario exercised the recipe-book path end-to-end. Phase 12.5 V5.6 ran a realistic graft-plus-backdrop on CraftingScreen — the bug fell out immediately.

---

**Awaiting greenlight to ship the library fix.** Scope is small and mechanical; no design doc needed beyond this finding. Once shipped, V5.6 scaffold mixin retargets in the same commit or a follow-up, depending on your preference.
