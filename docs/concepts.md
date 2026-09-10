# Concepts

Definitions of the terms MenuKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term.

## Two artifacts

| Artifact | Environment | Contents |
|---|---|---|
| MenuKit (`menukit`) | Client only | Elements, panels, layout helpers, HUD panels, placement on vanilla screens, standalone screens |
| MenuKit: Containers (`menukit-containers`) | Client and server | Created slots, custom container menus, per-slot state, storage attachments |

Containers depends on MenuKit. MenuKit does not depend on Containers. A client-only mod that depends on MenuKit alone cannot import a Containers type. The build fails.

## Panel

A `Panel` is a bounded rectangle that holds an ordered list of elements. It renders, receives input, and shows or hides as one unit. It has an id, a `PanelStyle`, a padding, and a visibility.

Panels do not nest. A panel holds elements. It does not hold other panels.

Panel ids are global. Prefix every id with the mod id: `"mymod:controls"`.

## Element

A `PanelElement` is one item inside a panel. Its position is relative to the panel's content area. The constructor sets it once. MenuKit ships these elements:

| Kind | Types |
|---|---|
| Render only | `TextLabel`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `InfoBox` |
| Interactive | `Button`, `Toggle`, `Checkbox`, `Radio` (with `RadioGroup`), `Slider`, `TextField`, `Dropdown`, `DropdownMulti` |
| Composite | `ScrollContainer`, `ConfirmDialog`, `AlertDialog` |
| Slot (Containers) | `SlotElement`, `SlotFlowElement` |

A consumer implements `PanelElement` for a custom element.

Constructor argument order is `(childX, childY, [width, height,] content, [callback])`. Elements that size from their content omit width and height.

## Mouse buttons

An element receives left clicks through its primary callback. `Button` and `Toggle` also accept `onSecondaryClick(Consumer<Click>)`, which receives every non-left button.

A `Click` record carries the button index and the shift, control, and alt state sampled when the click reaches the element. It answers `isRight()`, `isMiddle()`, `isShiftRight()`, and `isSecondary()`. On macOS the command key counts as control.

With no secondary handler attached, non-left clicks pass through to vanilla. A disabled control consumes neither kind.

## Tint

`Button` and `Toggle` accept `tint(IntSupplier)`. The supplier runs each frame and returns an ARGB value that fills the control inside its border, over the background, and under the label. Returning 0 draws no tint. The tint shows consumer-owned state, such as a pinned mode, that the control itself does not store.

## State

A stateful element does not own its state. It reads the value from a `Supplier` each frame and writes through a callback on interaction. Persistence is the consumer's. The exceptions are `Toggle`, `Checkbox`, and `Radio`, which hold a boolean value unless the `linked` factory constructs them.

## Structure does not change after build

`build()` freezes the panel's element list. No method adds an element to a built panel. Visibility, position, and supplier-driven content change at runtime. To change the element list, build a new panel.

A hidden element or panel is inert on every surface. It does not render, receive clicks, show a tooltip, or reserve layout.

## Layout helpers

`Row` and `Column` compute positions at build time and return a `List<PanelElement>`. They do not exist at runtime. An element enters a layout as an `ElementSpec`, produced by the element's static `spec(...)` factory. `.build()` returns positioned elements that go into a panel with `.add(...)`.

## Reference

A reference is the rectangle a panel is measured against. It is not the panel and has no relation to the panel's size. Three kinds exist: a container screen's frame, one slot group's bounding box, and the game window. `Reference` is the record that carries all three.

The call site picks the reference. `ScreenPanelAdapter` measures from the menu frame. `SlotGroupPanelAdapter` measures from the slot group it targets. `MKHudPanel.builder(...).region(...)` measures from the window. A region never names its reference.

## Region

A region names where a panel sits relative to its reference. Two enums exist, one for each side of the reference's edge:

| Type | Placement | Values |
|---|---|---|
| `OutsideRegion` | Outside the reference, along one of its edges | `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`, `CENTER` |
| `InsideRegion` | On the reference, at one of nine spots | `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `LEFT_CENTER`, `RIGHT_CENTER`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`, `CENTER` |

Panels in the same region stack in priority order. `region.priority(int)` returns a `RegionAnchor` with an explicit priority. Lower values stack first.

A panel wraps its width to the space its region leaves and scrolls its height when taller than its room. `Panel.size(w, h)`, `pinnedWidth(w)`, and `pinnedHeight(h)` override this.

`PanelPosition.pixel(Supplier<ScreenOrigin>)` places a panel at an absolute origin re-evaluated each frame. A pixel-positioned panel does not stack and does not wrap.

## The four contexts

A context is the answer to one question: what is this panel anchored to?

| Context | Anchor | Entry type | Artifact | Input |
|---|---|---|---|---|
| Menu | A container screen's frame | `ScreenPanelAdapter` | MenuKit | Yes |
| Slot group | A named slot group's bounds | `SlotGroupPanelAdapter` | MenuKit | Yes |
| HUD | The game window during play | `MKHudPanel` | MenuKit | No |
| Standalone | A screen the consumer opens | `MKScreen` (subclass) | MenuKit | Yes |

HUD panels do not receive input. For a clickable HUD control, open a standalone screen from a key binding.

An element renders the same in every context. The context owns the machinery around it.

## Targeting

A `ScreenPanelAdapter` with no target renders on every container screen. `.on(Class...)` limits it to those screen classes and their subclasses. `.onAny()` states the default explicitly. `.onPlayerInventory()` limits it to the player inventory screen.

A `SlotGroupPanelAdapter` requires a target. `.on(SlotGroupCategory...)` renders once per category that resolves in the open menu. `.onGroup(SlotGroupId...)` renders once per named created group. `MKCContainerPanel.groupId(panelId, groupId)` and `MKCSlots.groupId(panelId, groupId)` return the id. Categories cover every vanilla menu. A mod with its own menu registers a `SlotGroupResolver` for it.

A panel anchored to a slot group is measured from that one group, not from every slot sharing its category.

Both adapters register in their constructor. `unregister()` removes them.

## Slot group category

A `SlotGroupCategory` is a name for a group of slots, such as `PLAYER_INVENTORY`, `HOTBAR`, `CHEST_STORAGE`, or `FURNACE_INPUT`. It carries no rendering rule. MenuKit maps categories to slot indices per menu each frame.

Every created slot group (Containers) declares a category and is listed under it. Read the registry three ways:

| Call | Answers |
|---|---|
| `SlotGroupCategories.all()` | every category that exists, with no menu open |
| `SlotGroupCategories.of(menu)` | every category on this menu, with its slots |
| `SlotGroupCategories.categoriesBySlot(menu)` | the category of each slot on this menu |

A mod finds another mod's slots this way, with MenuKit types only.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search run by a mod that has never heard of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Operation

An operation is a bulk or shortcut action performed on a slot. Vanilla ships three: shift-click (`MKCBehaviorKeys.QUICK_MOVE`, Containers), double-click collect (`BehaviorKeys.COLLECT`), and drag-fill (`BehaviorKeys.DRAG_FILL`).

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, and consults it in its own code. `SlotOperations.all()` lists every published operation. MenuKit needs no change for a new operation to exist.

A slot resolves an operation in this order:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. A group's `collect(false)` outranks its category's inherent value. A per-slot declaration outranks the group.

`SlotOperations.inherent(category, operation, value)` sets an operation for every group in a category. A mod that mints a category states once that nothing in it may be collected. Registration order does not matter. Read time does. Every operation is a server-tier key. It returns the key default until Containers installs its tier, and mod init order is not fixed. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `collect(false)`, `dragFill(false)`, and `quickMove(NONE)`. A per-slot declaration still wins, so a slot can count as inventory storage for search and still sit out the bulk shortcuts.

Inherent operations reach created slots, whose category travels with the group. A vanilla slot's category depends on its menu, which the window cannot ask about. A vanilla slot therefore resolves from its own declaration or the key's default. Every built-in operation defaults to vanilla's behavior, so the result is the same in practice.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu through Containers. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner: a player, block entity, entity, or item stack.

`MKCContainerPanel` creates slots on the player's inventory menu and projects them onto every container screen. `MKCScreenHandler` creates slots on a custom menu.

## Slot rendering

Vanilla draws every slot. MenuKit runs no slot pass of its own on container screens. A created slot's panel writes the position into vanilla's `Slot.x` and `Slot.y` before vanilla's slot pass. Vanilla then draws the item, count, hover highlight, and ghost icon for created and vanilla slots alike.

One consequence reaches mods that do not depend on MenuKit. A mod that decorates slots through a mixin on vanilla's slot draw call marks created slots too. It needs no dependency on MenuKit and no interface to adopt. Inventory Plus is the worked example. Its lock icons and item marks appear on Inventory Max's pockets and equipment slots, with no code in either mod for that case.

A panel hides exactly what it covers. Flow panels composite between the vanilla slots and the created slots, inside vanilla's slot pass. A panel over part of a vanilla slot hides that part and no more. A created slot's item still draws over the chrome of the panel hosting it. Overlay panels draw after the slot pass, on top of it. An overlay panel is one that centers, dims behind, or tracks as modal.

`SlotElement` positions a created slot and draws its frame. It does not draw the item.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `CreatedSlotAdapter.addressOf(panelId, groupId, index)`, `MKCContainerPanel.address(...)`, and `MKCScreenHandler.address(...)` produce addresses for created slots. `Window.slot(address).set(key, value)` attaches behavior by address.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `MKSlotState.register(id, codec, streamCodec, defaultValue, visibility)` creates one at common init. `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`.

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. `EphemeralStorage.of(size)` builds one that lasts for the menu session. Containers also provides block-scoped and item-scoped attachments.
