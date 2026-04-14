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

- **Post-Phase-5 audit findings: consumer primitives and patterns revealed by existing mods**

  Phase 5's Step 0 audit read the three small consumer mods (sandboxes, agreeable-allays, shulker-palette) and IP to understand what they actually do with MenuKit. Three mods share a dominant pattern: they inject UI into vanilla screens rather than build their own. The current new-architecture has first-class support for "build your own screen" (`MenuKitScreenHandler.builder`) but nothing equivalent for "decorate a vanilla screen," which turns out to be the majority use case.

  **Primitives the three small mods use that the new architecture doesn't currently ship:**
  - Icon-only button (small image-only, with tooltip) — sandboxes (11×11 icons), shulker-palette (9×9 icons)
  - Toggle button with supplier-based pressed state — shulker-palette
  - Icon swap by state (two sprites, pressed/unpressed) — shulker-palette
  - Dynamic tooltip (`Supplier<Component>`) — shulker-palette
  - Dynamic text content (`Supplier<String>` or equivalent) — agreeable-allays

  **Pattern the three small mods + IP all need that the new architecture doesn't provide:**
  - Inject a MenuKit panel / button into a vanilla inventory screen, with predicate-based visibility (by context, region, or runtime state). The old architecture provided this via `MKPanel.builder().showIn(...)` + `MenuKit.buttonAttachment()`. The new architecture currently has neither a mixin hook for this nor a documented consumer pattern for writing their own.

  **Old global event bus removed.**
  - The old `MenuKit.on(Type)` event bus and its entire `event/` package (MKEvent, MKEventBus, MKEventBuilder, MKSlotEvent, MKUIEvent, MKEventPhase, MKEventResult, MKDismountReason) were deleted in Phase 5. That bus was a global pub/sub coupled to old-arch types (MKButton, MKRegion, MKSlotState) and had zero new-arch consumers. The new architecture uses `MenuKitHandledScreen.ScreenEventListener` — per-screen, scoped, declared in Phase 4b Task 1 — which handles the screen-scoped event needs cleanly. Consumers needing ecosystem-wide events outside a specific screen write their own event system; that's consumer work under library-not-platform discipline.

  **Unresolved subsystem relationship:**
  - `MKHudPanel` is a separate builder subsystem that survives Phase 5 (no old-arch type dependencies once `MKPanel.Style` is extracted — see Step 5 below). But its relationship to the rest of the new architecture is undocumented. Is it a first-class part of MenuKit's canonical surface, or a separate library that happens to ship in the same jar? Decide post-Phase-5.

  Post-Phase-5 work will evaluate each gap against the library-not-platform discipline before deciding what to ship versus what to document as a consumer pattern. The audit provides the data; decisions happen when the evaluation begins. Some of these might resolve to "the library ships this," others to "consumers handle it themselves, here's a documented recipe," and others to "defer until more real mods need it."

- **`MKPanel.Style` enum extraction** — resolved during Step 5 as a concrete cleanup, not deferred. Live callers (`core/Button`, `hud/MKHudPanel`, `hud/MKHudPanelDef`, `hud/MKHudNotification`, `screen/MenuKitHandledScreen`) are updated to use `core/PanelStyle` instead; rendering helpers move out of `panel/MKPanel.java` before that file is deleted.
