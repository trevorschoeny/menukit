# MenuKit Migration — Deferred Concerns

Items deferred across phases. Scan this list at phase boundaries.

## Resolved

- ~~**Text rendering investigation**~~ **RESOLVED (Phase 4b)**
  Root cause: 1.21.11's `GuiGraphics.drawString()` has an `ARGB.alpha(color) != 0` guard — colors without an explicit alpha byte (e.g., `0xFFFFFF`, `0x404040`) have alpha=0 and are silently discarded. Fix: use ARGB colors with `0xFF` prefix (e.g., `0xFFFFFFFF`, `0xFF404040`). All future text rendering must use ARGB colors. Not a pipeline integration issue — just a color format convention. Now documented as a class-level note in `TextLabel.java`.

- ~~**HandlerRecognizerRegistry**~~ **RESOLVED (Phase 4b, pulled forward from Phase 5)**
  Built with `SlotGroupLike` uniform abstraction, default identity grouper, furnace and brewing stand recognizers, and public `register(Recognizer)` API for consumer extensibility.

- ~~**Drag mode full pipeline**~~ **CONFIRMED (Phase 4a Task 4)**
  Full click→drag→release pipeline wired and verified in-game.

- ~~**Public registration APIs**~~ **RESOLVED (Phase 4b)**
  `HandlerRecognizerRegistry.register(Recognizer)` and `PanelBuilder.element(PanelElement)` expose the extensibility hooks consumers need.

- ~~**`onItemTransfer` / `onQuickMove` event scope ambiguity**~~ **RESOLVED (Phase 5)**
  Code already uses `onQuickMove` — name intentionally matches vanilla's `quickMoveStack`, scope is shift-click only. Added explicit scope-statement to the JavaDoc enumerating which paths do NOT fire the event (drag-collect, double-click collect, hopper insertion, cursor placement, creative middle-click). The ambiguity existed only in the DEFERRED.md text where both candidate names were floated during design.

## Architecturally Significant

- **Body panel visibility toggle limitation** (Phase 5 or later — evaluate when a real use case appears)
  If a body panel toggles visibility, `imageWidth`/`imageHeight` change in `renderBg()` but `leftPos`/`topPos` don't update (set once in `init()`). Screen would be offset. Not a problem while only relative panels toggle.

## Must-Verify

- **Hover exit events** (verify opportunistically)
  Enter path confirmed working. Exit path logic is sound but should be empirically verified with debug logging.

## Wait for Real Use Case

- **Server-sync for right-click handlers** (Task 3 → when a storage-mutating right-click is needed)
  Current right-click handlers run client-only. Use `clickMenuButton` C2S mechanism when server authority is needed.

- **Modifier support for key registry** (YAGNI, extend to MKKeybind when needed)

- **InventoryMenu dedicated recognizer** (when a consumer needs finer-grained player inventory groups)
  Current default identity grouper produces 3 groups for InventoryMenu: CraftingContainer, ResultContainer, and one big Inventory group (41 slots: armor + offhand + main + hotbar). A dedicated recognizer could split by slot index ranges into armor (4), offhand (1), main (27), hotbar (9), crafting input (4), crafting result (1). Defer until a real consumer use case demands it — the default is honest about what it sees.

- **PanelElement `mouseReleased` / `mouseDragged` hooks** (when a draggable element is needed)
  Current `PanelElement` interface supports single-click only. Adding release/drag methods would enable draggable elements. No current consumer needs this.

- **Per-panel PANEL_PADDING configuration** (if panel padding becomes variable)
  Currently a global constant in `MenuKitHandledScreen`. Both slot positioning and element positioning use it. If it ever becomes per-panel, both code paths need to consult the panel's padding value.

## Phase 5 Cleanup

- **MenuKitTestSetup removal** — convert to proper test framework or remove entirely
- **MKSlotMixin's ultimate fate** — already in Phase 5 plan (justify or delete)
- **~20 dead methods in MenuKit.java** — harvest or remove; target under 500 lines, no per-instance state
- **`isMenuKitManagedSlot` package-name check removal** — `HandlerRecognizerRegistry` uses a `slot.getClass().getName().startsWith("com.trevorschoeny.menukit.widget.MKSlot")` check to filter old-architecture injected slots. When the old widget package is deleted in Phase 5, remove this check and confirm recognition still works correctly.
- **Delete superseded types** — per audit: `MKSlotState`, `MKSlotStateRegistry`, `MKContainerDef`, `MKContainer`, `MKContextLayout`, `MKContainerMapping`, `MKRegion`, `MKSlotWrapper` (if VirtualSlotGroup supersedes it)
- **Update MKMenu to extend MenuKitScreenHandler** — explicitly deferred from Phase 3; the standalone screen system's panel-definition-based construction needs work against the new API
- **Verify all five contracts** — via the procedures spec'd in the migration plan: composability, vanilla-slot substitutability, sync-safety, uniform abstraction, inertness

## Post-MenuKit

- **inventory-plus refactor against new MenuKit API** — The real test of Phase 4b's "extracted to consumer mods" claim. If IP can be rebuilt cleanly using `SlotGroupLike`, recognizer queries, the event bus, and panel elements, the library boundary is correct. If IP keeps reaching for things MenuKit doesn't expose, the audit missed something.

  **Pre-migration bug fixes to preserve during IP refactor** (captured from uncommitted working-tree state at start of Phase 5; discarded when IP's old-arch files couldn't compile post-demolition):
  - **Peek region arg**: the `registerDynamicRegion` (or its new-arch equivalent) call should receive the MKContainer directly, not `container.getDelegate()`. The delegate form was dropping a layer of indirection that the region system needed.
  - **Creative-mode ItemPickerMenu fix**: in creative, `screen.getMenu()` is `ItemPickerMenu` while `player.containerMenu` is `inventoryMenu`. Peek slots exist in both menus (sharing the same backing container), but hover detection needs regions registered on *both* menus — otherwise the peek keybind fails in creative because the hovered slot's index doesn't match the inventoryMenu region range. The fix was a symmetric `registerDynamicRegion` + `removeDynamicRegion` pair on `screen.getMenu()` when it differs from `containerMenu`. Both open-path and close-path were affected.

  **Phase 7 API changes consumers should know about during refactor:**
  - **`MenuKitSlot` constructor signature**: now takes `Panel panel` instead of `String panelId` as its sixth argument (the slot derives `panelId` from the Panel internally). Consumers constructing `MenuKitSlot` directly need to update. In-tree callers use the Builder path; this only matters if IP or another mod hand-rolled slot construction.
  - **Panel no longer holds slot groups**: `panel.getGroups()` is removed. Slot groups live on `MenuKitScreenHandler` via `handler.getGroupsFor(panelId)`. Consumer code that walked slot groups through the Panel needs to query the handler instead.
  - **`PanelElement.render` signature**: takes a `RenderContext` record (single parameter) instead of flat `graphics/contentX/contentY/mouseX/mouseY`. Custom `PanelElement` implementations need to migrate. Convenience: `ctx.isHovered(childX, childY, width, height)` and `ctx.isHovered(this)` via the default `PanelElement.isHovered(ctx)`.
  - **`MKHudElement` removed**: HUD elements now implement `PanelElement` like every other context. Consumer custom HUD elements migrate to `PanelElement`. The HUD builder's `.custom()` signature changed to `Consumer<RenderContext>`; the old `(graphics, x, y, w, h, deltaTracker)` signature is gone.
  - **HUD builder `.list()` and `.group()` methods removed**: deferred per palette, zero consumers at time of removal.

  **Phase 8 API additions and changes consumers should know about during refactor:**
  - **Eight new foundational elements ship**: Icon, Divider, ItemDisplay, ProgressBar, Toggle, Checkbox, Radio/RadioGroup, Tooltip. Each has a design doc in `Design Docs/Element Design Docs/`.
  - **TextLabel supplier variant added**: TextLabel now takes either `Component` or `Supplier<Component>`. Existing consumers passing `Component` keep working.
  - **Tooltip Form A setters added to five elements**: Button, Toggle, Checkbox, Radio, Icon each have `.tooltip(Component)` and `.tooltip(Supplier<Component>)` chainable setters. Additive; not breaking.
  - **MKHudItem and MKHudBar deleted**: subsumed into core ItemDisplay and ProgressBar. HUD builder `.item()` / `.bar()` / `BarBuilder` continue to work (retargeted internally). Consumers that directly imported these classes (none at time of writing) would break.
  - **MKHudBar.Direction moved to ProgressBar.Direction**: breaking if any consumer imported the enum directly (none at time of writing).
  - **PanelBuilder grew to ~20 methods**: all Phase 8 elements gained builder methods. Four past the original comfortable threshold of 15. Pocketed for Phase 12 evaluation; consumers not affected.

  **Phase 9 API additions and changes consumers should know about during refactor:**
  - **`Button.icon(...)` factory ships** (two forms: fixed `Identifier` and `Supplier<Identifier>`). Returns a square Button with a centered sprite, 2px inset, dim-when-disabled. Accessed via `.element(Button.icon(...))`; no new builder method. Accessibility recommendation on the factory javadoc: pair with a tooltip.
  - **`Toggle.linked(...)` factory ships**. State lives in consumer code via `BooleanSupplier` + `Runnable` callback. Persistence framing and self-healing documented on the factory javadoc. Accessed via `.element(Toggle.linked(...))`; no new builder method.
  - **Button and Toggle gained protected extension hooks** formally documented as stable consumer-facing extension points. Button: `renderBackground(ctx, sx, sy)` and `renderContent(ctx, sx, sy)`. Toggle: `currentState()` and `applyState(boolean)`. Consumer subclasses can override independently; signatures and semantic contracts are maintained across MenuKit versions.
  - **`Button.render(RenderContext)` is now `final`**. Consumer subclasses that previously overrode `render()` directly (none known in-tree) must migrate to the protected hooks. This is the blessed extension path.
  - **`Toggle.applyState(boolean)` contract expanded** to "commits new state AND fires the consumer callback" — both happen atomically. Any consumer subclass overriding `applyState` must honor this atomicity contract.
  - **`MenuKitHandledScreen.computePanelSize` now factors in element bounds** alongside slot-group dimensions. Panels with elements that extend beyond their slot-grid now render at the larger size. Not breaking for existing consumers (their panels have slot groups that already drive size).
  - **`MenuKitHandledScreen.renderLabels` skips the vanilla "Inventory" label** when the handler has no panel with id `"player"`. Existing consumers all have player panels → unaffected.
  - **Factor-then-specialize template** established as the repeatable pattern for future element specializations: factor the parent into protected hooks, then add a subclass factory. Phase 12 documentation should capture this pattern explicitly.
  - **Convention 5 refined** (locked in Phase 9): factory methods permitted when they return a different concrete type (specialization subclass); still rejected as preset-value shortcuts. `Button.icon` and `Toggle.linked` pass; hypothetical `Button.primary` would fail.
  - **`/mkverify elements` subcommand added** — dedicated element-demo screen for visual verification, separate from the contract-verification harness. Internal dev tooling; not a consumer-facing API.
  - **PanelBuilder method count still ~20** (no new builder methods in Phase 9; specializations accessed via `.element(...)`). Phase 12 consolidation evaluation still pending.

  **Phase 10 API additions and changes consumers should know about during refactor:**

  - **`menukit.inject` package ships** with five types for vanilla-screen injection:
    - `ScreenPanelAdapter` — bundles render + click dispatch for a Panel inside a vanilla screen. Class-agnostic (works on any Screen subclass).
    - `ScreenBounds` — record. Vanilla-screen layout snapshot (`leftPos, topPos, imageWidth, imageHeight`); consumer constructs per-call.
    - `ScreenOrigin` — record. Screen-space top-left of the injected panel.
    - `ScreenOriginFn` — functional interface from `ScreenBounds` to `ScreenOrigin`.
    - `ScreenOriginFns` — four constructors: `fromScreenTopLeft`, `fromScreenTopRight`, `aboveSlotGrid`, `belowSlotGrid`.
  - **`Panel.showWhen(Supplier<Boolean>)` ships** on the core Panel surface. Supplier becomes single source of truth for `isVisible()` while set; `setVisible(...)` is silent no-op until `showWhen(null)` reverts. Sync-safety caveat: MenuKit-native inventory-menu panels with slot groups should continue using `setVisible` to drive the broadcastChanges sync pass — `showWhen` does not notify the owner.
  - **`PanelElement.mouseClicked` coord-space contract is now class-level documented** in `PanelElement`'s javadoc as `Coordinate contract`. No API change; lifts the screen-space rule from a per-parameter comment to the canonical home. Consumer custom `PanelElement` implementations should trust screen-space for mouseX/mouseY.
  - **Three injection-pattern documents** under `Design Docs/Architecture Design Docs/`:
    - `INVENTORY_INJECTION_PATTERN.md` — Patterns 1/2/3 with four consumer-facing failure modes (silent-inert dispatch, render z-order occlusion, IllegalClassLoadError on non-mixin classes in mixin package, @Shadow on inherited fields in multi-target mixins). Required reading before writing any vanilla-inventory-screen mixin.
    - `STANDALONE_INJECTION_PATTERN.md` — Pattern 4. Two consumer approaches; `ScreenPanelAdapter` works unchanged for standalone screens.
    - `HUD_INJECTION_PATTERN.md` — Pattern 5. Already-shipped `MKHudPanel.builder().showWhen(...)` is the canonical answer.
  - **`/mkverify all` consolidation** — single subcommand replaces the previous five-subcommand suite (`composability`, `substitutability`, `syncsafety`, `uniform`, `inertness`). Internal dev tooling; if any external doc references the old subcommands, update to `/mkverify all`.
  - **`examples/injection/` + `examples/shared/` package convention** — non-mixin helpers (Panel + adapter state) live in a sibling package outside the mixin package, per Fabric's class-load rule (failure mode #3). Consumer mods writing injection mixins follow the same convention.
  - **Split-mixins-as-default reframing** — consumer decorations spanning multiple inventory variants typically need multiple mixin classes (one primary + supplementaries per silent-inert hook), not one. Realistic floor: 1 primary + 2-3 supplementaries + 1 shared state holder for any decoration touching survival inventory through `AbstractRecipeBookScreen`. Documented in the inventory-menu doc's "Split mixins are the default" subsection.

- **Post-Phase-5 audit findings: consumer primitives and patterns revealed by existing mods**

  Phase 5's Step 0 audit read the three small consumer mods (sandboxes, agreeable-allays, shulker-palette) and IP to understand what they actually do with MenuKit. Three mods share a dominant pattern: they inject UI into vanilla screens rather than build their own. The current new-architecture has first-class support for "build your own screen" (`MenuKitScreenHandler.builder`) but nothing equivalent for "decorate a vanilla screen," which turns out to be the majority use case.

  **Primitives the three small mods use — status after Phase 9:**
  - ~~Icon-only button (small image-only, with tooltip) — sandboxes (11×11 icons), shulker-palette (9×9 icons)~~ **SHIPPED in Phase 9** via `Button.icon(...)` (both fixed and supplier sprite forms). Tooltip inherited from parent Button's `.tooltip(...)` setters.
  - ~~Toggle button with supplier-based pressed state — shulker-palette~~ **SHIPPED in Phase 9** via `Toggle.linked(...)`.
  - ~~Icon swap by state (two sprites, pressed/unpressed) — shulker-palette~~ **SHIPPED in Phase 8** via Icon's `Supplier<Identifier>` constructor; also covered by Phase 9's `Button.icon(supplier)` overload.
  - ~~Dynamic tooltip (`Supplier<Component>`) — shulker-palette~~ **SHIPPED in Phase 8** via Tooltip Form A setters on Button/Toggle/Checkbox/Radio/Icon.
  - ~~Dynamic text content (`Supplier<String>` or equivalent) — agreeable-allays~~ **SHIPPED in Phase 8** via TextLabel's `Supplier<Component>` constructor.

  All five audit-surfaced element primitives are now addressed.

  ~~**Pattern the three small mods + IP all need that the new architecture doesn't provide:**~~ **ADDRESSED in Phase 10.** The injection-into-vanilla-screens pattern is now documented in `INVENTORY_INJECTION_PATTERN.md` (Patterns 1/2/3) + `STANDALONE_INJECTION_PATTERN.md` (Pattern 4) + `HUD_INJECTION_PATTERN.md` (Pattern 5). Library-not-platform discipline: MenuKit ships `ScreenPanelAdapter` + `Panel.showWhen` as composable primitives; consumer mods write their own mixins into specific vanilla screen classes. The old `MKPanel.builder().showIn(...)` + `MenuKit.buttonAttachment()` shape is replaced by per-consumer mixins composing the new primitives — see the Phase 10 API notes above.

  **Old global event bus removed.**
  - The old `MenuKit.on(Type)` event bus and its entire `event/` package (MKEvent, MKEventBus, MKEventBuilder, MKSlotEvent, MKUIEvent, MKEventPhase, MKEventResult, MKDismountReason) were deleted in Phase 5. That bus was a global pub/sub coupled to old-arch types (MKButton, MKRegion, MKSlotState) and had zero new-arch consumers. The new architecture uses `MenuKitHandledScreen.ScreenEventListener` — per-screen, scoped, declared in Phase 4b Task 1 — which handles the screen-scoped event needs cleanly. Consumers needing ecosystem-wide events outside a specific screen write their own event system; that's consumer work under library-not-platform discipline.

  **Unresolved subsystem relationship:**
  - `MKHudPanel` is a separate builder subsystem that survives Phase 5 (no old-arch type dependencies once `MKPanel.Style` is extracted — see Step 5 below). But its relationship to the rest of the new architecture is undocumented. Is it a first-class part of MenuKit's canonical surface, or a separate library that happens to ship in the same jar? Decide post-Phase-5.

  Post-Phase-5 work will evaluate each gap against the library-not-platform discipline before deciding what to ship versus what to document as a consumer pattern. The audit provides the data; decisions happen when the evaluation begins. Some of these might resolve to "the library ships this," others to "consumers handle it themselves, here's a documented recipe," and others to "defer until more real mods need it."

- **Phase 8 reconsideration triggers**

  Four items where in-game testing or Phase 11 consumer refactors may reveal a need to revisit Phase 8 design decisions:

  - **Toggle visual legibility.** Toggle ships with pure RAISED/INSET panel styles rather than a custom switch sprite. This is an explicit Phase 8 trade-off, documented in the Toggle design doc. Reconsideration trigger: in-game testing or Phase 11 feedback reveals that isolated unlabeled toggles read as "button that's pre-pressed" rather than "on/off switch." Fallback: subtle internal indicator (small mark that shifts L/R by state) or custom sprite.
  - **PanelBuilder method count consolidation.** Phase 8 ended with ~20 builder methods on PanelBuilder (four past the comfortable 15 threshold). Consolidation candidates for Phase 12: sub-builders for related elements, grouped accessors, or simply accept the count. Evaluate during Phase 12 documentation work.
  - **Shared styling constants consolidation.** The default text-on-panel color `0xFF404040` appears in Divider, Checkbox, Radio, Tooltip (Form B). Similarly `0xFF808080` (disabled label) and `0xFF606060` (indicator) appear in multiple elements. A shared `StyleDefaults` or similar constants class would consolidate these. Phase 12 cleanup target.
  - **MKHudSlot still HUD-specific.** After Phase 8 subsumed MKHudItem into ItemDisplay and MKHudBar into ProgressBar, MKHudSlot is the only HUD-specific element-like class remaining (MKHudNotification is intentionally HUD-specific per the palette). A hotbar-sprite-background ItemDisplay variant is plausibly Phase 9 or Phase 10 work if shulker-palette or another consumer reveals demand for the pattern. Not Phase 8 scope.

- **Phase 9 Toggle.linked variant — persistence framing** **SHIPPED in Phase 9**

  Persistence framing and self-healing note live on `Toggle.linked`'s factory javadoc. MenuKit does not ship a persistence abstraction; state-linked is the answer. Consumer backs supplier + callback with whatever storage they need (block entity, player attachment, config file, in-memory field, etc.).

  **Reconsideration trigger (still active).** If Phase 11 reveals that inventory-plus, shulker-palette, and agreeable-allays all build similar persistence adapters independently (e.g., each rolling a "config-file-backed boolean" helper), that's evidence the library might ship a small persistence abstraction. Until then: no shipped abstraction, documented pattern only.

- **Phase 9 reconsideration triggers**

  Four items where in-game testing or Phase 11 consumer refactors may reveal a need to revisit Phase 9 design decisions:

  - **Button.icon inset configurability.** `Button.icon` uses a fixed 2px inset. At the audit's small button sizes (9×9 shulker-palette, 11×11 sandboxes), 2px leaves 5×5 or 7×7 usable icon area — readable. Reconsideration trigger: Phase 11 testing reveals icons are illegible at small sizes, or consumers want the icon flush with the button edge for a different visual style. Cheap one-field addition to support an explicit inset parameter, or a scaled-with-size formula.
  - **Toggle.linked callback type.** Currently `Runnable`. Reconsideration trigger: Phase 11 consumer refactors reveal that consumers repeatedly query the supplier inside their Runnable callback to determine new-state (e.g., `() -> { boolean newVal = !config.autoSort; config.autoSort = newVal; doSomethingWith(newVal); }`). That pattern is evidence for migrating to `Consumer<Boolean>` which receives the new state directly. One-field migration if it happens.
  - **`disabledWhen` overloads for specializations.** Neither `Button.icon` nor `Toggle.linked` ships with a `disabledWhen` overload. Consumers needing disabled-predicate on an icon button or linked toggle subclass the parent class directly using the Phase 9 hooks. Reconsideration trigger: multiple consumer mods independently build the same disabled-subclass pattern. Add the overload if the pattern is common.
  - **Additional `*.linked` variants.** Phase 9 shipped `Toggle.linked` only. Checkbox.linked and Radio.linked are plausible follow-ons if consumer state-linking demand extends beyond Toggle. Reconsideration trigger: Phase 11 surfaces a real use case. The factor-then-specialize template is mechanical once demand materializes.

- **Phase 10 reconsideration triggers**

  Items where Phase 11 consumer refactors may reveal a need to revisit Phase 10 design decisions:

  - **`ScreenPanelAdapter.debug(String name)` diagnostic helper.** Considered and rejected during Phase 10 per advisor guidance — documentation-only is the library-not-platform-aligned response to the silent-inert failure mode. Reconsideration trigger: two or more Phase 11 consumer refactors independently build the same "did this fire?" diagnostic logging in their mixins. If they do, ship a small `.debug(String name)` method that logs first-fire only, dev-environment-gated. Until then: documented pattern only.
  - **Fifth `ScreenOriginFns` constructor.** `fromScreenTopLeft`, `fromScreenTopRight`, `aboveSlotGrid`, `belowSlotGrid` cover the audit-surfaced cases. Reconsideration trigger: multiple consumer use cases need the same custom positioning that's awkward to express as a lambda. Bar for adding: a concrete consumer case, not hypothetical demand.
  - **Cross-mod composition mechanism.** Library does not ship one — consumers expose public Java APIs to other consumers directly. Reconsideration trigger: Phase 11 reveals two or more consumer mods independently invent the same decorator-registry shape for inter-mod composition. Until then: documented as out-of-scope in the design doc.
  - **`ScreenBounds.window(int width, int height)` factory for standalone screens.** Pattern 4 demonstrates `ScreenPanelAdapter` works for vanilla standalone screens by passing `ScreenBounds(0, 0, this.width, this.height)`. Slightly awkward — the `imageWidth`/`imageHeight` field names imply a frame, but for full-window screens the values are window dimensions. Reconsideration trigger: Phase 11 standalone-screen decorations show this awkwardness recurring. Possible response: a `ScreenBounds.window(...)` factory that signals intent without changing the record's shape.
  - **`Panel.showWhen` on inventory-menu panels with slot groups.** Currently no-ops on supplier-value-change — `showWhen` is documented for client-side rendering decisions, not for panels driving slot sync. Reconsideration trigger: a consumer use case wants supplier-driven visibility on an inventory-menu panel that has slots. Possible response: `showWhen` triggers `owner.onPanelVisibilityChanged` on supplier value change (would require comparing across frames). Currently deferred; native inventory-menu panels use `setVisible` directly.
  - **Cross-mod composition example.** Phase 10 design doc mentioned a possible "fourth example" for cross-mod composition (Pattern 2 + direct API call to another consumer mod). Deferred — cross-mod composition is a consumer concern, not a library-primitive demonstration. Reconsideration trigger: Phase 11 surfaces a clean example shape worth shipping.

- **Factor-then-specialize template — capture in Phase 12 docs**

  Both Phase 9 specializations followed the same structural pattern: factor the parent element into protected hooks, then add a subclass factory overriding those hooks, with a stable-extension-point contract on the hooks.

  This is a repeatable template for future element specializations — either MenuKit-shipped variants or consumer-side customization. Phase 12's documentation should capture the pattern explicitly as "the shape of how MenuKit elements grow variants without API bloat or convention violations."

  Key steps of the template:
  1. Identify the variation points in the parent element.
  2. Factor those variation points into `protected` hook methods with stable-extension-point javadoc.
  3. Mark the parent's orchestration method `final` if the hooks fully cover the extension surface.
  4. Add a static factory on the parent returning a package-private subclass overriding the hooks.
  5. Factory javadoc covers usage, any emergent properties (e.g., self-healing for Toggle.linked), and accessibility notes where relevant.

  Capture this in Phase 12's STORY.md updates or equivalent external documentation.

- **`MKPanel.Style` enum extraction** — resolved during Step 5 as a concrete cleanup, not deferred. Live callers (`core/Button`, `hud/MKHudPanel`, `hud/MKHudPanelDef`, `hud/MKHudNotification`, `screen/MenuKitHandledScreen`) are updated to use `core/PanelStyle` instead; rendering helpers move out of `panel/MKPanel.java` before that file is deleted.

## Phase 12.5 M3 scope-down — follow-on items

Surfaced during M3 smoke; full resolution record lives in `Phase 11/POST_PHASE_11.md` M3 entry.

- **Sandboxes in-UI Settings button click.** `SandboxScreen.settingsButton` (top-right) doesn't open the config screen — `setScreen(yaclScreen)` takes effect but the screen never becomes visible. Working hypothesis: press-release propagation across the screen transition (vanilla `Button.onPress` fires on mouse-press, subsequent release hits the newly-active YACL screen at same coords). Failure likely pre-existed M3 (structurally identical click path). ModMenu entry works — users can configure. Target: Phase 13 sandboxes refactor; mitigation options documented in POST_PHASE_11.md.

- **MenuKit own-config primitive.** Wait-for-real-need candidate. If MenuKit later grows user-facing toggles of its own (beyond the `SHOW_ITEM_TIPS` hardcode retired in M3), that's when it ships a library-internal GSON-backed single-file config — no YACL, no ModMenu, just its own JSON under its mod-id. Trigger: a second or third own-toggle surfacing. Until then, hardcode-on is the default.

- **Keybind-category-sharing review (Phase 13 public-release prep).** §11 scope-down preserved `getKeybindCategory()` as Layer A on the rationale that grouping is a real user-facing coordination primitive. Question that surfaced during M3 smoke: when a non-Trevor author adopts MenuKit, their mod isn't necessarily part of a "family" — MenuKit shipping a grouping primitive may be the same shape of ecosystem-shaping assumption Layer B was. Trigger: public-release prep begins. If the answer is "keybind-sharing leaves too," MKFamily's Layer A shrinks further to identity + mod-id roster only.
