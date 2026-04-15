# Phase 10 Report — Injection Patterns

Phase 10 ships the consumer-facing infrastructure for decorating vanilla screens with MenuKit elements. Five injection patterns surfaced from the audit, three got substantive design + implementation work (the inventory-menu patterns), two got documentation of mostly-already-working surface (standalone, HUD). All five canonical contracts pass at the Phase 10 endpoint. The implementation surfaced a richer set of consumer-facing realities than the design anticipated; the design doc was reconciled to name them honestly.

Heavier phase than Phase 9 — one new package (`inject/`), one Panel API addition, six example mixins demonstrating the patterns, three pattern docs, plus a dev-tooling consolidation that fell out as a natural side effect.

---

## What was done

**Core injection package** under `menukit/inject/`:

- `ScreenPanelAdapter` — bundles the mechanical parts of rendering a Panel inside a vanilla screen and dispatching input to it. Class-agnostic — works on any Screen subclass, not just `AbstractContainerScreen`.
- `ScreenBounds` — vanilla-screen layout snapshot, consumer-constructed per call (no Supplier indirection on the adapter).
- `ScreenOrigin` — screen-space top-left of the injected panel.
- `ScreenOriginFn` — pure function from `ScreenBounds` to `ScreenOrigin`. Per-frame re-computation handles screen resizes naturally.
- `ScreenOriginFns` — four constructors for the audit-surfaced positioning cases: `fromScreenTopLeft`, `fromScreenTopRight`, `aboveSlotGrid`, `belowSlotGrid`.

**`Panel.showWhen(Supplier<Boolean>)`** ships on the core Panel surface with explicit precedence semantics (supplier is single source of truth while set; `setVisible` becomes silent no-op; `showWhen(null)` reverts to imperative). Matches the Phase 8/9 state-ownership pattern (Toggle.linked precedent). Documented sync-safety caveat: MenuKit-native inventory-menu panels with slot groups should continue using `setVisible` to drive the broadcastChanges sync pass — `showWhen` does not notify the owner.

**`PanelElement` coord-space contract lifted to class-level javadoc.** The screen-space rule for `mouseClicked` and `RenderContext.mouseX`/`Y` was previously buried in a per-parameter comment. New `Coordinate contract` section pins it down as the canonical home — `MenuKitHandledScreen`, `MenuKitScreen`, and the new `ScreenPanelAdapter` all conform.

**Six example mixins** under `examples/injection/` (mixins) + `examples/shared/` (non-mixin helpers, sibling package per Fabric's class-load rule) demonstrating the three inventory-menu patterns:

- `ExampleInventoryCornerButtonMixin` (Pattern 3 primary) + `ExampleInventoryCornerButtonRecipeBookRenderMixin` (Pattern 3 supplementary for survival z-order)
- `ExampleChestToolbarMixin` (Pattern 2)
- `ExampleKeybindTriggeredPanelMixin` (Pattern 1 primary) + `ExampleKeybindTriggeredPanelRecipeBookMixin` (Pattern 1 supplementary keyPressed) + `ExampleKeybindTriggeredPanelRecipeBookRenderMixin` (Pattern 1 supplementary render)

`DevOnlyExampleMixinsPlugin` gates application via `FabricLoader.isDevelopmentEnvironment()`. Production `menukit.jar` loads the example classes as dormant bytes — the "no defaults" rule from the design doc is preserved end-to-end.

**Three pattern docs** under `Design Docs/Architecture Design Docs/`:

- `INVENTORY_INJECTION_PATTERN.md` — substantive doc covering Patterns 1/2/3, the `ScreenPanelAdapter` design, four consumer-facing failure modes, the cross-mod composition boundary, and the resolved design questions for `Panel.showWhen` + `ScreenBounds`. Includes the split-mixins-as-default reframe.
- `STANDALONE_INJECTION_PATTERN.md` — Pattern 4. Two consumer approaches (vanilla widgets via `Screen.addRenderableWidget`, or MenuKit Panel via `ScreenPanelAdapter`). Failure modes refer back to the inventory-menu doc. Short — the territory is mostly already working.
- `HUD_INJECTION_PATTERN.md` — Pattern 5. Confirms `MKHudPanel.builder().showWhen(...)` as the canonical answer for runtime-visibility HUD overlays. Adds positioning guidance vs vanilla HUD elements + the render-only caveat from THESIS principle. Shortest — already shipped pre-Phase-10.

**`/mkverify all` consolidation** — refactored `ContractVerification` to expose a single `all` subcommand that runs all five contract probes in a single execution with one VERDICT line per contract. Replaces the old five-subcommand suite that required ESC-and-retype between contracts at every phase boundary. Not Phase 10 scope (dev tooling improvement); separated into its own commit.

---

## Commits

Six on main across Phase 10 (plus one separate commit for the unrelated /mkverify consolidation):

```
fa60d03 MK: Phase 10 — STANDALONE + HUD pattern docs (Patterns 4 + 5)
c782929 MK: /mkverify consolidation — single "all" subcommand replaces five
7c0d4fd MK: Phase 10 — example mixins (3 patterns) + dev-only plugin gating
2c6bbd2 MK: Phase 10 — Panel.showWhen for supplier-driven visibility
d425102 MK: Phase 10 — core inject package (ScreenPanelAdapter + supporting types)
3bd2f09 MK: Phase 10 — design doc (inventory-menu injection patterns) + PanelElement coord contract
```

Each commit independently compilable and bisectable. The Phase 10 chain is dependency-ordered: design doc → primitives → API addition → examples → pattern docs.

---

## Bug-fix detour

Visual verification of the example mixins surfaced three bugs that took several rebuild cycles to fully resolve. Useful signal — the bugs forced the design doc to confront vanilla's actual shape rather than its idealized shape, and produced four failure-mode entries that consumers will encounter in Phase 11 refactors.

**Bug 1 — chest toolbar clicks didn't appear to work.** Root cause turned out to be visibility-of-feedback, not a click-dispatch issue. Diagnostic logging confirmed `Button.onClick` was firing; the `setOverlayMessage` call painted to the hotbar overlay strip, which is hidden while a screen is open. Switched the example feedback to `Minecraft.getInstance().gui.getChat().addMessage(...)`, which is visible regardless of screen state.

**Bug 2 — corner button rendered nowhere in either inventory variant, eventually crashed creative.** Root cause was a Fabric mixin limitation — `@Shadow` on inherited fields fails refmap resolution in multi-target mixins. The original split-mixin design used `@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})` with `@Shadow protected int leftPos`, where `leftPos` is inherited from `AbstractContainerScreen`. Fabric's refmap couldn't resolve the field across both targets and the shadow accesses returned garbage. Compounded by a class-load rule violation: the plain helper class `ExampleInventoryCornerButton` was in the same package as the mixins, which Fabric's loader refused to load when transformed code referenced it.

Fixes: collapsed the multi-target mixin to a single broad-target on `AbstractContainerScreen` with `instanceof` runtime gating; moved plain helpers to a sibling `examples/shared/` package; added an `extends-AbstractContainerScreen` supplementary mixin for survival inventory's z-order issue (see Bug 3 below).

**Bug 3 — keybind P-key worked everywhere except survival inventory.** Root cause: `AbstractRecipeBookScreen.keyPressed` overrides without super-calling, so the primary mixin on `AbstractContainerScreen.keyPressed` never fires for `InventoryScreen`. Fix: supplementary mixin on `AbstractRecipeBookScreen.keyPressed` with `instanceof InventoryScreen` runtime gate, cancelling at HEAD via `cir.setReturnValue(true)` to prevent double-toggle if vanilla ever starts super-calling.

A late-discovered companion to Bug 3: the same render path has the same issue. The primary render mixin on `AbstractContainerScreen.render` doesn't reliably fire for `InventoryScreen` either (`AbstractRecipeBookScreen.render` doesn't reliably super-call). Fix: a third supplementary mixin (`ExampleKeybindTriggeredPanelRecipeBookRenderMixin`) for the keybind decoration, mirroring the supplementary already added for the corner button.

The four failure modes captured in the design doc — silent-inert dispatch, render z-order occlusion, `IllegalClassLoadError` on non-mixin classes in mixin package, `@Shadow` on inherited fields in multi-target mixins — are direct distillations of these three bug-fix cycles.

---

## Deviations from the plan

**`ScreenPanelAdapter.render` signature dropped `DeltaTracker`.** Design doc sketch had `(GuiGraphics, ScreenBounds, int mouseX, int mouseY, DeltaTracker delta)` with the note "final API decided at implementation time." Implementation chose four params — `RenderContext` doesn't need DeltaTracker, so the parameter was vestigial. Design doc updated to match.

**Example mixin gating uses a config plugin, not dev-module mixins.json.** Design doc said *"Mixin registration is dev-only (in the dev module's mixins.json)."* Implementation chose a different mechanism: `IMixinConfigPlugin` (`DevOnlyExampleMixinsPlugin`) registered in `menukit-examples.mixins.json`, with `shouldApplyMixin` returning `FabricLoader.isDevelopmentEnvironment()`. Strict improvement — keeps examples co-located with the primitives they demonstrate, in the same module, with the same dev-only-application property. Design doc updated.

**Corner-button example became a multi-file split, not a single file.** Design doc named one `ExampleInventoryCornerButtonMixin`. Implementation needed three files for that decoration: a shared state holder + render mixin + supplementary render mixin. Cause: the failure modes named above force per-(hook, declaration-point) mixins. The design doc's "Targeting multiple screen classes" section was expanded to reframe split-mixins-as-the-default; the Pattern 3 example description in the same doc lists the actual file structure.

**Keybind example ended up with three mixins, not one.** Same cause as the corner-button finding, applied to a different vanilla hierarchy traversal. The doc reflects this — one primary plus two supplementaries (one per silent-inert method) is the realistic floor for a decoration spanning survival inventory.

---

## Architectural decisions

### Split mixins are the default, not the exception

The design doc originally framed multi-target mixins as the natural shape and split-mixins as a workaround for hierarchy edge cases. Bug-fix experience proved this backward. Reframed: **for any decoration spanning multiple inventory variants and using more than one render/input hook, the realistic floor is one primary mixin per hook plus one supplementary per (subclass, silent-inert hook) pair, sharing state via a non-mixin helper class in a sibling package.** Three to four mixin classes per logical decoration is the norm.

This is a documentation truth, not a code change. The library doesn't ship infrastructure to reduce the burden — that would cross the library-not-platform line. But naming the burden honestly lets Phase 11 consumer refactors plan accurately.

### Coord-space contract canonical home

`PanelElement.mouseClicked` receives screen-space coords. `RenderContext.mouseX`/`Y` are screen-space. `RenderContext.originX`/`Y` are screen-space; element `childX`/`Y` are panel-local; the element composes them in `render`. All three dispatchers (`MenuKitHandledScreen`, `MenuKitScreen`, `ScreenPanelAdapter`) conform.

This was always implicitly true but lived only in a per-parameter comment on `PanelElement.mouseClicked`. Phase 10's `ScreenPanelAdapter` had to guess at the contract during implementation — the right answer was already in the codebase, but not in a place where independent consumers (or independent dispatchers) would necessarily find it. Lifted to a class-level "Coordinate contract" javadoc section as part of Phase 10. Consumers and future internal dispatchers have one canonical home.

### Library does not ship reduction-of-mixin-burden infrastructure

Considered and rejected during the bug-fix detour: a `ScreenPanelAdapter.debug(String name)` helper that logs first-fire to help consumers diagnose silent-inert mixins. Considered: a Fabric-event facade that sidesteps per-screen mixins entirely. Rejected both per advisor guidance.

The bugs are evidence that consumer mixin-writing is harder than the design doc implied — but the response is documentation, not new infrastructure. Shipping the diagnostic helper would cross into platform territory by mediating consumer's mixin lifecycle; shipping the event facade would require MenuKit to take ownership of vanilla event paths. Both are exactly the platform behavior Phase 5 rewrote out.

The four failure modes get prominent doc placement instead. If Phase 11 consumer refactors reveal that two or more consumers independently build the same `did-this-fire?` diagnostic, ship the helper then.

---

## Surprises

**Vanilla's super-call asymmetry is per-method, per-version, sometimes per-runtime-state.** `AbstractRecipeBookScreen` super-calls reliably for `mouseClicked`, doesn't super-call for `keyPressed`, and doesn't reliably super-call for `render` (depends on recipe-book widget state). The same parent class can be silent-inert for one hook and pass-through for another. Each hook has to be tested independently — there's no "this parent always super-calls" rule that consumers can rely on. The design doc names this explicitly so Phase 11 doesn't fall into the assumption.

**The convergence of failure modes #1 and #2 for render hooks.** "Silent-inert dispatch" (parent never reached) and "render z-order occlusion" (parent reached but subclass paints over) sound like distinct problems but converge on the same fix: supplementary render mixin at the subclass declaration point. Consumers planning render decorations for `InventoryScreen` should add the supplementary proactively without distinguishing which mode caused the issue. Captured as a "Connection to failure mode #1" subsection in failure mode #2.

**The class-load rule fires only after the mixin successfully applies.** The early-iteration corner-button mixin had `@Shadow` errors that prevented it from working at all. When those were fixed, the *next* error surfaced — `IllegalClassLoadError` on the helper class in the mixin package. The first bug masked the second. Lesson: bug fixes can unmask latent problems. Especially in mixin land, where load-order and transformation-order matter.

**The Pattern 4 + 5 docs mostly confirmed already-working surface.** Standalone-screen decoration is `ScreenPanelAdapter`-supported with no new code; HUD decoration was already shipped pre-Phase-10 via `MKHudPanel.builder().showWhen(...)`. Both pattern docs landed at ~140 / ~110 lines respectively vs. the inventory-menu doc's ~570. Phase 10's actual heavy lifting was the inventory-menu chunk; the other two patterns benefited from the audit's framing without needing equivalent depth.

---

## Cross-pattern review

No new shared abstractions emerged that earn factoring beyond what already shipped. The shared surface is:

- `ScreenPanelAdapter` covers Patterns 2, 3, 4 (all three "panel inside a vanilla screen via a consumer mixin" cases) — already factored in `inject/`.
- `Panel.showWhen` covers Patterns 1 and 5 (predicate-driven visibility from consumer state) — already factored on `Panel`.
- `PanelElement` + `Panel` composition covers all five — already in `core/`.

The "broad-target + instanceof gate" pattern, the "extends-target for inherited @Shadow" pattern, and the "supplementary-mixin for silent-inert" pattern are recipes documented in `INVENTORY_INJECTION_PATTERN.md`, not code that could be lifted into the library. Each is consumer-side work; the library can name the recipes but cannot encapsulate them without crossing into platform territory.

---

## Contract verification evidence

All five contracts PASS at the Phase 10 endpoint. Run via the consolidated `/mkverify all` command.

```
[Verify.Composability]    VERDICT — mixin fired on both vanilla and MK slot types; global cobblestone filter applied uniformly
[Verify.Substitutability] VERDICT — all 46 MK slots pass `instanceof Slot`, and Slot.getItem RETURN mixin fires on MK slots with the composed return value (including inertness-driven EMPTY for any hidden panel)
[Verify.Uniform]          VERDICT — same findGroup() API used against both vanilla and MenuKit handlers; both return Optional<SlotGroupLike>, concrete implementations (VirtualSlotGroup vs SlotGroup) transparent to caller
[Verify.SyncSafety]       VERDICT — 10 toggles, 0 inconsistencies. PASS — the protocol's view stayed consistent with visibility.
[Verify.Inertness]        VERDICT — inertness holds: hidden slots report fully inert (4/4 OK); visible slots flip back (4/4 restored)
```

| Contract | Result |
|---|---|
| 1. Composability | PASS |
| 2. Vanilla-slot substitutability | PASS |
| 3. Sync-safety | PASS |
| 4. Uniform abstraction | PASS |
| 5. Inertness | PASS |

Phase 10 touched render and visibility code paths, not slot infrastructure or the sync protocol. Expected impact (per the design doc's Verification section): Composability strengthens, Sync-safety unaffected, others unaffected. All confirmed.

---

## Status

Phase 10 complete. Inventory-menu, standalone, and HUD injection patterns documented; three implementations land in `inject/` + `examples/`; design docs reconciled with bug-fix discoveries; contracts verify; commits chain bisectable.

Phase 11 (consumer mod refactors) is the next phase. The audit-surfaced consumer mods — sandboxes, agreeable-allays, shulker-palette, inventory-plus — get rewritten against the new architecture. The injection patterns documented this phase are the templates Phase 11 will follow. Real validation comes when the rewrites actually land cleanly using the documented patterns + failure modes.

---

## Decisions not in the brief

**Visual verification is load-bearing for any phase touching render or input.** Phase 5 contracts pass cleanly while Phase 10's injection layer had three bugs — the contracts validate the foundation (sync, slot semantics, inertness), they cannot validate the layer above the foundation. This is correct scoping of the contract harness, not a gap. Visual verification is the regression gate for the layer above contracts. The Phase 10 design doc requires it explicitly; Phase 11 should treat it as a non-negotiable phase-completion gate.

**`/mkverify all` consolidation is permanent dev tooling, not Phase 10 scope.** Surfaced naturally during the bug-fix detour — running five contract subcommands at every phase boundary was painful enough that consolidation paid for itself in one cycle. Committed separately from the Phase 10 chain so the Phase 10 commit history stays focused on the injection-pattern surface. Lives indefinitely as repo infrastructure.

**The `examples/shared/` package convention emerged as a discovery, not a planned design.** The class-load rule violation forced helper classes out of the mixin package; `examples/shared/` was the smallest sibling-package change that worked. Worth surfacing to Phase 11 — consumer mods following this pattern will encounter the same constraint and benefit from the same convention.
