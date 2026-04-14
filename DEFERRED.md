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

## Architecturally Significant

- **Body panel visibility toggle limitation** (Phase 5 or later — evaluate when a real use case appears)
  If a body panel toggles visibility, `imageWidth`/`imageHeight` change in `renderBg()` but `leftPos`/`topPos` don't update (set once in `init()`). Screen would be offset. Not a problem while only relative panels toggle.

- **`onItemTransfer` / `onQuickMove` event scope ambiguity** (Phase 5 cleanup or resolve opportunistically)
  Task 1 added an `onItemTransfer`/`onQuickMove` event. The event currently fires from shift-click paths but the name could imply all transfer paths (drag, double-click collect, etc.). Decide scope and name; document clearly. Not a blocker but worth resolving before inventory-plus rebuilds against it.

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
