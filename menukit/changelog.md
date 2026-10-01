## 6.0.0

One of everything. MenuKit had grown two engines, two slot-group identities, five copies of panel placement and two ways to build every element. This release keeps one of each and names it once. It adds little and breaks a lot, one time: every mod that uses MenuKit migrates. The README's "To 6.0.0" section lists the changes most mods meet.

### Breaking: the public API is the `api` packages

The public API is `com.trevlar.menukit.api.element`, `.layout`, `.dialog`, `.panel`, `.hud`, `.slot` and `.window`. Every other package is internal and outside the version contract, which `versioning.md` now states. Every import changes.

### Breaking: renames

| 5.x | 6.0.0 |
|---|---|
| `MKHudPanel`, `MKHudNotification`, `MKHudIcon`, `MKHudSlot` | `HudPanel`, `HudNotification`, `HudIcon`, `HudSlot` |
| `MKFocus`, `MKText` | `Focus`, `Text` |
| `showWhen(...)` on a panel, a HUD panel or a container panel | `visibleWhen(...)`, the same word as on every element. `Panel.Builder` takes it too. |
| `getId()` | `id()`: `Panel.id()`, `HudNotification.id()`, and an element's `declId()` |
| `WORLD_PICKUP` | `INVENTORY_INSERT`, named for what it gates. Its id is kept. |
| `SlotGroupId.Vanilla` | `SlotGroupId.Category`, with `isVanilla()` |

`MKScreen` and `MKTooltip` keep the prefix because the plain names are vanilla's.

### Breaking: declarations freeze

Once every mod has initialised, a late `define`, `veto`, `declare`, `register`, `addTo`, adapter or HUD panel throws, naming the call. Changing a slot at runtime is still fine. Nothing depends on load order. Two mods declaring the same thing is an error at the second. Where several coexist, their order is sorted: operations by namespace, panels by priority, then mod id, then registration order within a mod.

### Breaking: one placement, declared on the panel

Where a panel sits is declared once, on the panel, with `position(PanelPosition...)`, `.priority(n)` and `.offset(dx, dy)`. The adapters take only `(panel)` or `(panel, padding)`. A HUD panel is an ordinary `Panel` placed with `.region(InsideRegion.X)`. Each context (container screen, other vanilla screen, slot group, HUD, standalone screen) is one host that sorts, places, draws and routes its panels.

The claim rule is the same everywhere. An opaque panel takes its whole rectangle and a see-through one only its controls, so a slot-group panel and a standalone screen's own panels now block what they cover. A vanilla screen gets dialogs and dimming.

### Breaking: elements are built by builders

Every element has a builder and does not change after `build()`. The names are the same on every builder: `at`, `size`, `visibleWhen`, `disabledWhen`, `tooltip`, `state(get, set)`, `onClick`, `style`, `opaque`, `declId`. A control that shows a value reads the mod's field every frame and stores nothing. The positional constructors and `spec(...)` factories are gone. Input methods take an `InputContext`, and one `ChildDispatch` routes input in every container.

### Breaking: removed

- Item Tips, the durability and food lines MenuKit added to every tooltip. It is a feature, and moved to Inventory Plus.
- The older slot-recognition classes (`HandlerRecognizerRegistry`, `VirtualSlotGroup`, `SlotGroupLike`, `ReadOnlyStorage`), `SlotIdentity`, `SlotWindowResolver` and `CreatedSlotResolver`. Nothing read them, and `SlotGroups` is the one slot-group identity.
- `PanelOwner`, `MKHudAnchor`, `MKHudPanelDef`, `RegionAnchor`, `PanelPosition.BODY` and `SlotGroups.entryKey`.
- API that did nothing. `BehaviorKeys.ON_INSERT` and `ON_TAKE` with `SlotHandle.onInsert` and `onTake`, and the causes `ReactCause.CLICK`, `SHIFT_CLICK`, `HOPPER` and `DISPENSER`: nothing fired them, and the observed reactions stay. `BehaviorKeys.INERTNESS` and `PanelHandle.inertness`: nothing read them, and an opaque panel is what makes its cover inert. `GatingContext.actingPlayerCapable`: always true, since a server with Containers requires it on every client.

The pressed look on vanilla and YACL buttons stays. It is MenuKit's one styling change to vanilla, now recorded as such.

### MenuKit runs on servers

Code that runs on a server and code that touches a screen are in separate source sets, so a server class that reaches a client class does not compile. The common entry point is `com.trevlar.menukit.MenuKit`, and `MK` is only the HUD facade. The window's server tier is always present, container identity is MenuKit's, and vanilla menus' slot groups resolve on both sides, so a server answers operations, `AppliesTo` and `SlotGroups.of` the way singleplayer does.

### One engine, one identity

One store answers every key, and clearing a declaration removes it, so nothing piles up per player or block. An `Address` saves as text with `asString()`, `parse` and `CODEC`, with one public minter per kind: `Address.createdSlot(group, index)`, `Address.panel(id)` and `Address.panelElement(id, declId)`. `GroupKey.of(SlotGroupId)` makes a slot group and its cascade group one identity, and a vanilla slot gets a group rung as well as its category's.

### One seam per vanilla method

`SlotGate`, `GatingContext`, `BehaviorKeys.GATING` and `BINDING` are MenuKit's, so a MenuKit-only mod gates a vanilla slot the same way Containers gates a created one, with `Window.slot(address).gate(...)`. Each vanilla method asks the gate once, and a click is judged once on the client and once on the server. A lock is a veto. A `SlotRef` carries its slot's `Address`, and a veto can ask for the slot's group with `SlotOperations.GroupVeto`. `OperationProbe` is the probe a test mod checks click tags through.

### Added

- `Button`, `Toggle`, `Checkbox` and `Radio` play vanilla's click sound, take keyboard focus and read to the narrator. `Slider` and `TextField` are vanilla's own widgets.
- For settings screens: `Tabs` (wrapping, scrolling or sidebar, extendable by other mods with `Tabs.addTo`), `Flow.spacer()` to pin a control to the right edge, `Panel.disabledWhen` to grey a whole panel, a filling `Divider`, and whole-number and enum lenses for `Slider` and `Dropdown`.
- `SlotRendering.drawItemOutline` and `ItemDisplay`'s `outline` draw an item with a coloured line around its own shape.
- A modal panel on a vanilla screen hands a key to its elements, then to the focused widget MenuKit placed, before dropping it. F11, the F3 combinations, the narrator hotkey and screenshots work while a modal is up. They were eaten.
- On a standalone screen the `main()` panel makes room for the panels placed around it with `region(...)`. Its height leaves out the panels above and below, and its width the panels beside it. A panel above a full-height menu used to have nowhere to go and was not shown.
- `MKTooltip.hideWhen(condition)` hides every tooltip in the game, vanilla's, MenuKit's and other mods', on every frame the condition holds. A mod that offers "hold a key to hide tooltips" registers its condition there instead of adding its own mixin on vanilla's tooltip method.
- `MKTooltip.scrollBy(dx, dy)` and `MKTooltip.onWheel(listener)` let a mod scroll a tooltip taller than the screen with the mouse wheel. The listener hears every wheel turn over a shown tooltip, consumed or not. MenuKit keeps the scroll in range, pins the title line while scrolled, and resets it on the next tooltip. `MKTooltip.resetScroll()` puts it back by hand.
- `MKTooltip.wrapWhen(condition)` and `MKTooltip.clampWhen(condition)` keep a tooltip on screen while their condition holds. Wrap breaks text lines wider than the screen onto more lines and keeps the tooltip inside the sides. Clamp keeps its top on screen, so a tooltip taller than the screen starts at its title and scrolls to its last line. Both are off unless a mod registers them.
- `Tabs.Builder.header(elements...)` puts a row across the whole `Tabs`, above the tab strip or the sidebar and above the body, in every mode, so a menu can have its own title bar. Everything else starts under it, and `sidebarHeader` still sits at the top of the sidebar, now under the header.
- A `Flow` with a spacer at each end centres what is between them. Before, a spacer at either end of a row took no room.

### Fixed

- A panel anchored to a slot group, and a standalone screen's own panels, let the slots under them be hovered and clicked.
- A `Button` built with width 0 drew no box.
- A `Dropdown` inside a tab body or a `ScrollContainer` never closed on an outside click.
- `ItemDisplay` could not show the count without the durability bar.
- `TextField` reported its starting text as a change.
- A notification sent from a server thread could break the HUD.
- A YACL release that renamed a method could stop the game at load.
- A click inside another click lost the acting player.
- A `Checkbox` or `Radio` whose label wrapped reported its one-line width.
- A player's ender chest never resolved to its owner, and a registered block-entity resolver was never consulted.
