# Phase 12.5 — M8 + V2 interim REPORT

Interim close-out for the M8 reframe and V2 validation arc. Not the full Phase 12.5 close-out (that comes when V5 / V7 / V8 / M3 land). This artifact captures the sub-phase's architectural output while findings are fresh — per Phase 12's close-out discipline applied to a natural sub-phase boundary.

---

## 1. Arc summary

Phase 12.5's initial scope treated V0–V8 as sequential validator scenarios with library additions as in-scope-if-needed. V2's probe-validation work escalated mid-phase into an M8 library reframe — the four-context model — that restructured how MenuKit exposes context to consumers. The arc ran:

- **V0 → V1 → V3 → V4 → V6** shipped per plan. V4's three-context parity test caught a rendering-pipeline gap (`MenuKitScreen` double-blur) and four `ScreenPanelAdapter` completeness gaps; both surfaces closed additively, and the strategic output was THESIS Principle 9 (rendering pipelines uniform, embedding context-specific).
- **V2 began** as region-probe validation. During probe walkthrough, the Principle 9 test fired on a second concrete instance — inventory regions anchoring to the declared 176×166 frame but not to chrome drawn outside it (creative tabs, recipe-book widget). **M7 — chrome-aware inventory regions** landed as the closure, with vanilla providers for `CreativeModeInventoryScreen`, `InventoryScreen`, and `CraftingScreen`.
- **V2 continued** as probe-completeness pass. Advisor triage distinguished "worth doing" from "covered already" items; the automated-checks sub-scenario shipped (`/mkverify v2` with 3/3 passing) plus a CraftingScreen chrome provider to prove the M7 pattern generalizes.
- **V2 resumption exposed a conflation** at the context level. The previous "InventoryContext" name was load-bearing for both frame-anchored panels and slot-anchored panels, but those are distinct consumer mental models. The advisor's round-1 reframe produced **M8 — Four-context model**: MenuContext + SlotGroupContext + HudContext + StandaloneContext. SlotGroupContext was the new primitive; the rest were renames and separations of concerns already present.
- **M8 implementation shipped in six steps** (MenuRegion rename → adapter targeting → `ScreenPanelRegistry` dispatch → SlotGroupContext machinery → THESIS Principles 10+11 → doc alignment). All six landed cleanly; advisor round-1 returned 10 resolutions all confirming agent's source-verified divergences from the initial brief.
- **V2 resumed against the M8 model** and caught three more library-level issues: tooltip-layering bug (render stratum wrong — mixin fix), per-frame resolution (reversed the v1 Runtime-category-mutation non-goal), and ItemPickerMenu detection fragility under mod-grafted slots (size discriminator rewritten from fixed-count to `size != 54`). All three landed with doc updates in-phase rather than deferred to close-out.

What the sub-phase shipped: two new THESIS principles (10 + 11), one new library primitive (SlotGroupContext with 43 vanilla category constants and 22 menu resolvers), one library-owned listener pipeline replacing per-consumer boilerplate (`ScreenPanelRegistry`), one rendering-pipeline mixin restoring correct tooltip layering (`MenuKitPanelRenderMixin`), and three design-doc corrections landed (M5 §11 amendment, M7 rename, Phase 12.5 DESIGN.md scoping notes).

---

## 2. Commit timeline

```
8c57e9c  Phase 12.5 V2 — automated region checks + CraftingScreen chrome provider
68811a2  Docs: M8 design doc — four-context model + SlotGroupContext
f391dc9  M8 step 1 — MenuRegion / MenuChrome rename
4bf890f  M8 step 2 — adapter targeting API (.on / .onAny)
3293a8a  M8 step 3 — ScreenPanelRegistry dispatch + orphan checkpoint throw
6cc1287  M8 step 4 — SlotGroupContext machinery + 22 vanilla resolvers
538ce50  M8 step 5 — THESIS Principles 10 + 11
0a7329b  M8 step 6 — M5 / M7 / DESIGN alignment + 5 findings folded in
2dec366  V2 resume — SlotGroupContext probes (PLAYER_INVENTORY + CHEST_STORAGE)
d57b07e  V2 findings fold — render-ordering mixin + two doc notes
8ef8ab8  Per-frame slot-group resolution + ItemPickerMenu dynamic resolver
be8fb12  Fix ItemPickerMenu INVENTORY-tab slot count: 47, not 46
2a5580f  Fix ItemPickerMenu INVENTORY-tab detection: size != 54, not fixed count
```

13 commits. Precedent commits (M7 landing, prior V4 work) listed in the Phase 12.5/V4 commit chain.

---

## 3. What shipped, what dissolved

### Shipped — library primitives

- **MenuRegion / MenuChrome rename.** The pre-M8 `InventoryRegion` and `InventoryChrome` names were misleading (they applied to every `AbstractContainerScreen`, not just the player inventory). Mechanical rename across 12 files. Semantics unchanged; naming now matches vanilla's `AbstractContainerMenu` terminology.
- **Adapter targeting (`.on` / `.onAny`).** Region-based `ScreenPanelAdapter`s now declare which screens they apply to via `.on(Class...)` (class-ancestry resolution) or `.onAny()` (explicit every-screen). Default = none; orphan adapters fail at client-boot checkpoint with `IllegalStateException` naming the panel ID. Lambda adapters exempt — they're the escape hatch scoped by consumer-owned mixins.
- **`ScreenPanelRegistry`.** Library-owned `ScreenEvents.AFTER_INIT` listener replaces per-consumer boilerplate. Consumers stop writing screen-filter + per-screen-afterRender + per-screen-allowMouseClick scaffolding; the library dispatches. Caches MenuContext matches per screen-instance; re-resolves SlotGroupContext per frame.
- **`MenuKitPanelRenderMixin`.** Library-private mixin on `AbstractContainerScreen.render` at `@At INVOKE renderCarriedItem`. Fixes tooltip layering: `ScreenEvents.afterRender` (Fabric's hook) fires after vanilla flushes deferred tooltips, so panels rendered there overdraw tooltips. Injecting before `renderCarriedItem` puts panels in the right painter's-algorithm stratum — above slots, below cursor + tooltips.
- **SlotGroupContext primitive.** New type family covering `SlotGroupRegion` (enum, eight values), `SlotGroupCategory` (record, 43 vanilla constants), `SlotGroupResolver` (functional interface), `SlotGroupCategories` (registry), `SlotGroupPanelAdapter` (adapter parallel to ScreenPanelAdapter), `SlotGroupBounds` (record). Panels anchor to a slot group's bounding box wherever it renders — across screens — rather than to a specific screen's frame.
- **22 vanilla slot-group resolvers.** In `VanillaSlotGroupResolvers`, covering every `AbstractContainerMenu` whose slots map to named categories in v1: player inventory menus (including creative's dynamic `ItemPickerMenu`), storage containers (chest/shulker/dispenser/hopper), crafting family (crafting table, crafter), furnace family (shared across furnace/smoker/blast furnace), utility blocks (enchanting, anvil, grindstone, smithing, loom, stonecutter, cartography), brewing, trading, beacon, mounts (horse, nautilus). LecternMenu deferred per M8 §6.10.
- **THESIS Principle 10** — Contexts are consumer mental models, not implementation boundaries. Every panel belongs to exactly one context; the library's contexts align with the natural answers to "what am I anchoring to?" — not with the rendering pipeline's decomposition. Structural test sentence closes debates that a subjective test would admit.
- **THESIS Principle 11** — Evidence drives primitive scope; exhaustive coverage is available when per-item cost is low and incompleteness cost is high. Rule of Three remains the default; the exception is named-only invocation with both tests explicit. v1 SlotGroupContext's 43 vanilla categories invoke the exception.

### Dissolved — library surface

- **`InventoryRegion`** → `MenuRegion` (rename; file renamed via `git mv`, 98% similarity preserved).
- **`InventoryChrome`** → `MenuChrome` (same).
- **`ProbeRenderMixin` + `ProbeRenderRecipeBookMixin`** deleted. Probes migrated to `.onAny()` dispatch via `ScreenPanelRegistry`; the per-screen mixins' job transfers to the registry. Fixes the pre-M8 coverage gap where probes didn't render on `CraftingScreen` / `FurnaceScreen` / `SmokerScreen` / `BlastFurnaceScreen` (the `instanceof InventoryScreen` guard in the recipe-book mixin).
- **`Runtime category mutation` non-goal** (M8 §12) withdrawn. V1 drafted it as deferred. V2 probe validation showed per-frame re-resolution is the correct model — resolver interface already supports it cleanly; ItemPickerMenu is the canonical dynamic case it now handles.

### THESIS.md additions

- Principle 10 + 11 texts (§2.1, §2.2) with structural test sentences.
- Principle 9 text revised — pre-M8 "three rendering contexts" conflated contexts with pipelines; post-M8 there are 4 contexts but 3 rendering pipelines. Revised to "rendering pipelines host the same panels and elements across every context" with a parenthetical noting MenuContext + SlotGroupContext share an underlying pipeline.
- Summary section enumerates the four contexts.
- Principle 5's relationship to Principle 9 retained from V4's original retrofit.

---

## 4. Architectural findings

### Finding 1 — `MenuKitScreen` double-blur (V4)

`MenuKitScreen.render` called both `this.renderBackground()` and `super.render()`; the latter invokes `renderBackground` internally on the 1.21.x Screen base class. Two blur passes triggered the "Can only blur once per frame" guard. Latent since Phase 10 — no consumer of `MenuKitScreen` existed before V4's standalone cross-context test. **Fix:** remove the explicit `renderBackground()` call; let super handle it. One-line patch, but only visible under live rendering.

### Finding 2 — ScreenPanelAdapter completeness gaps (V4)

The V4 silent-workaround incident surfaced four primitives missing from the pre-12.5 adapter: no auto-background render, no content padding, silent `OUT_OF_REGION` on overflow, hidden origin math. All closed additively in V4's commit. The strategic output was THESIS Principle 9 — adapter renders panels identically across contexts.

### Finding 3 — Chrome-aware regions (V2 → M7)

Probe validation showed regions anchored to the declared frame, not the effective visible boundary. Creative tab rows (25/26 px) and recipe-book widget (~178 px) draw outside the frame. **Fix:** M7 `MenuChrome` registry with per-screen `ChromeProvider` functional interface. Values derived from vanilla hit-test geometry (`checkTabHovering` for creative, `RecipeBookComponent.updateTabs` formula for survival). This is the second Principle 9 instance — library-closed gap, gameplay-rooted ordering restored.

### Finding 4 — PlayerInventoryContext / ContainerContext conflation (M8)

The pre-M8 library had separate code paths for "player inventory decorations" and "arbitrary container decorations." Advisor's round-1 reframe showed these were the same context with different targeting, not two contexts. The actual second context was slot-group-anchored panels — the natural mental model "decorate this slot group wherever it renders." **Fix:** four-context model, MenuContext unifying frame-anchored panels (both player inventory and containers) with targeting, SlotGroupContext carving out the slot-anchored mental model as its own type family.

### Finding 5 — Tooltip layering (V2, post-M8)

SlotGroupContext probes landing inside the screen frame exposed that panels rendered above tooltips. Root cause: Fabric's `ScreenEvents.afterRender` fires in `GameRendererMixin.onRenderScreen` AFTER `Screen.renderWithTooltipAndSubtitles` returns, which is AFTER `GuiGraphics.renderDeferredElements()` flushes the tooltip queue. Panels in that hook overdraw tooltips. **Fix:** library-private mixin on `AbstractContainerScreen.render` at `@At INVOKE renderCarriedItem`. Third Principle 9 instance — tooltip layering is gameplay-rooted (tooltips must visually dominate), panels-over-tooltips had no gameplay reason.

### Finding 6 — Per-frame resolution (V2, post-M8)

Draft M8 §5.4 specified cached-once slot-group resolution. V2 showed this breaks for menus whose slot composition changes mid-session (creative tab transitions). **Fix:** drop the cache for SlotGroupContext matches; re-resolve per frame via `SlotGroupCategories.of(menu)`. Cost negligible (resolvers do constant-time subList slicing). Effect: flipped the v1 "Runtime category mutation" non-goal from deferred to supported. The non-goal wasn't architecturally justified — the resolver interface already supports the right semantics; v1 draft over-conservatively deferred it.

### Finding 7 — ItemPickerMenu detection fragility (V2, post-M8)

Diagnostic logging during creative-tab probe verification showed the INVENTORY tab has 49 slots in the dev client, not the 46 or 47 I'd assumed from vanilla-only analysis. Root cause: inventory-plus's `InventoryMenuMixin` grafts 2 equipment slots into `InventoryMenu` at construction; vanilla's creative `selectTab` wraps the full `player.inventoryMenu.slots`, so any mod grafting slots shifts the total. **Fix:** detect INVENTORY-tab state via `size != 54` (non-INVENTORY is fixed at 54; INVENTORY is variable) rather than a specific count. Player-inventory category indices 0-45 remain stable under mod grafting because vanilla's rebuild loop preserves `InventoryMenu`'s slot order.

### Finding 8 — "Validate the product" Principle 7 payout

Findings 1, 5, 6, and 7 are all bugs that would have shipped invisibly without Phase 12.5's validation-scenario discipline. Each was caught by probe-validation running against real rendering, not by unit-test-style analysis. Phase 12.5's existence as a full validation phase — rather than "ship and test during Phase 13 consumer migration" — is paying out. Worth naming as evidence for Principle 7 in future phase-planning.

---

## 5. Design-doc corrections landed

- **M5 §11 non-goal amended** — "Vanilla-HUD-element awareness" split into per-context chrome scoping: MenuContext (M7), HudContext (non-goal), SlotGroupContext (N/A), StandaloneContext (N/A).
- **M5 grafted-slot backdrop non-goal dissolved** — SlotGroupContext is the natural home for grafted-slot decorations post-M8; the pre-M8 "fixed-anchor, non-stacking region variant" Rule-of-Three trigger becomes irrelevant.
- **M7 renamed** — "Chrome-aware inventory regions" → "Chrome-aware MenuContext regions". `InventoryChrome` → `MenuChrome` throughout.
- **M8 itself** — advisor round-1 resolutions (§13) folded, then V2-finding additions to §5.4 (ItemPickerMenu canonical dynamic case), §8.2 (mixin-over-Fabric-hook rationale with Principle 9 cross-reference), §12 (Runtime-category-mutation non-goal withdrawn).
- **Phase 12.5 DESIGN.md** — top-of-file post-M8 reframe note, V2 creative-screen probe behavior scoping note, `resolveInventory` → `resolveMenu` mechanical rename.
- **THESIS.md** — Principle 9 text revised (contexts vs pipelines), Principle 10 + 11 added, Summary enumerates four contexts.

---

## 6. Cross-reference map

| Work item | Status | Next-phase successor |
|---|---|---|
| V0 Consumer mini-app | Shipped (pre-M8) | — |
| V1 Element palette sweep | Shipped (pre-M8) | — |
| V2 Regions × element palette | Closed (post-M8 + findings) | — |
| V3 Visibility + inertness | Shipped (pre-M8) | — |
| V4 Native screen lifecycle + cross-context | Shipped (pre-M8) | — |
| V5 Slot interactions | Pending | Next after M3 |
| V6 M1 persistence scenarios | Shipped (pre-M8) | — |
| V7 HUD behavior | Pending | After V5 |
| V8 MKFamily cross-mod | Pending | After V7; blocked on M3 |
| M3 scope-down | Pending | Before V5 per advisor sequencing |
| M7 chrome-aware regions | Shipped | — |
| M8 four-context model | Shipped | — |
| Phase 12.5 close-out REPORT | Pending | After V5/V7/V8/M3 |

Sequencing for remaining Phase 12.5 per advisor: **M3 → V5 → V7 → V8 → close-out**. M3 first because it's library cleanup (natural follow-on to M8's library restructuring) and unblocks V8; V5 after M3 because slot-interaction bugs may surface and deserve fresh attention rather than running against fatigued context.

---

## 7. Pattern distilled — what makes sub-phase boundaries productive

Phase 12's REPORT named "fresh writeups at phase boundaries, design-doc drift corrected at close-out, advisor decisions preserved with their alternatives" as the discipline that made its close-out useful. The M8 + V2 arc applied the same discipline to a natural sub-phase boundary. Patterns worth naming:

**Findings fold in-phase, not at close-out.** Each of findings 5, 6, 7 was folded into the design doc the same commit that shipped the library fix. Deferring them to end-of-phase roll-up would have lost the implementation context — the "why `size != 54` instead of `size == 47`" reasoning evaporates once the diagnostic log is removed. Writing the finding alongside the fix preserves the evidence and the reasoning.

**Advisor round-1 resolutions captured as audit trail.** §13 of M8 keeps the 10 question-resolution pairs from round-1 review rather than erasing them once applied. Future readers see what was open, what was picked, and why. This is what Phase 12's REPORT called "advisor decisions preserved with their alternatives."

**Principle test sentences stay structural.** Principle 10's first draft had a subjective test ("does a consumer recognize this as distinct?"); advisor corrected to structural ("does this context have a distinct answer to 'what am I anchoring to?' that isn't available under any existing context?"). Structural tests close debates; subjective tests admit them. The feedback memory `feedback_test_sentences_structural_not_subjective.md` captures this for future principle drafts.

**Non-goals are reversible when evidence demands.** The Runtime-category-mutation non-goal (M8 §12) got withdrawn mid-phase when V2 evidence showed per-frame resolution was both correct and cheap. Principle 11's exception clause runs both ways: evidence expands exhaustive coverage, but evidence also deflates deferrals that turn out to be architecturally unjustified.

**Validate the product pays out.** Principle 7's discipline — validate that primitives compose into real product behavior — caught four rendering-pipeline bugs during Phase 12.5 that would otherwise have shipped invisibly. Worth remembering when planning future phase boundaries: "ship and test during consumer migration" is cheaper up-front and much more expensive overall.

---

## 8. Status of record

**M8 + V2 arc complete.** Library primitives, mixin fix, doc corrections, and findings all landed. Phase 12.5 continues with M3 → V5 → V7 → V8 per §6's sequencing. Close-out REPORT opens when those four land.

Agent working-memory files updated:
- `project_four_context_model.md` — project memory, pointer to M8 design doc
- `feedback_test_sentences_structural_not_subjective.md` — feedback memory, structural-over-subjective principle test framing

No memory-file updates needed for this interim REPORT. The REPORT itself is the record.
