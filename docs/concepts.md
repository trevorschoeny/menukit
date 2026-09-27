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
| Composite | `ScrollContainer`, `Tabs`, `ConfirmDialog`, `AlertDialog` |
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

## Size

Width flows down from the panel to its elements. A label wraps to it and a slider caps to it. Height does not flow down unless an element asks: an element whose `fillsHeight()` returns true takes the room from its top edge to the bottom of the panel's viewport, through `fillHeight(int)`. The viewport is the panel's pinned height or, for a screen's MAIN panel, the screen's height. With no viewport the element gets -1 and takes its natural height. No built-in element but `Tabs` fills height.

## Tabs

`Tabs` is a tab strip and the area below it that shows the selected tab's body. One element holds both, so the body's top edge follows the strip's height. Each tab has a string id, a label, a body, and an optional `visibleWhen`. A body is a list of elements positioned from the body's top-left, laid out to the body width the way a panel lays out its own elements, and scrolled when it is taller than the body area.

Selection is consumer state. `selected(supplier, onSelect)` reads the selected id every frame and writes the id the player picks. To select a tab from outside, write your own field. When the selected tab is hidden, the next visible tab is shown, else the previous one, and nothing is written, so the tab takes the selection back when it reappears.

Three modes. `WRAP` breaks the strip into the fewest rows the width allows and splits the tabs so the rows are about equally full. A tab keeps its row and place when another is selected. `SIDE_SCROLL` keeps one row. When it overflows it scrolls from the left, arrows appear at both ends, and a newly selected tab scrolls into view. `SIDEBAR` puts the tabs in a column down the left with the body to its right. It is the top tab turned on its side: the selected tab reaches further left and opens into the page. The column is as wide as its widest label, at most a third of the element. When the column is taller than the element it scrolls the way `SIDE_SCROLL` does, with arrows at the top and bottom. Resource packs that restyle vanilla's tabs change the top strip but not the sidebar, which is drawn in vanilla's tab colors.

`align(...)` is `LEFT`, `CENTER`, `RIGHT`, or `FILL`, applied per row. In `SIDEBAR` it places each label in its tab, with `FILL` the same as `LEFT`, and labels stay put when the selection changes. `FILL` gives every tab in a row its label width plus an equal share of the leftover space, the last row included. In `SIDE_SCROLL`, alignment applies only while the row fits. A label wider than its tab is cut with "..." and shows in full on hover.

By default `Tabs` fills its panel's width and height. Put it in a screen's MAIN panel for a full-screen tabbed menu. `size(width, height)` fixes both, for a panel with no height to give. Each tab keeps its body's scroll position while the element exists.

Other mods can add tabs to a menu that has a name. The owner names it with `menu(id)` on the builder, and another mod calls `Tabs.addTo(id, tab)` at its init. An added tab's body is a factory, `body(() -> elements)`, because it is built every time the owner builds the menu, against the adding mod's own config. The owner's builder order is the menu's order. An owner tab marked `standIn()` holds a place for a tab another mod may add: an added tab with the same id takes its place, and with nothing to replace it the stand-in shows with a dimmed label. Only the place carries over: the replacement keeps its own `visibleWhen`, or always shows, so an owner's switch that hides its stand-ins never hides the real tabs. Any other added tab places itself with `after(id)` or `before(id)`, or goes at the end. Added tabs that share a place are ordered by id, so the result does not depend on which mod loaded first. Two mods adding the same id to one menu is refused at the second `addTo`. A tab added to a menu nobody builds is never shown.

## Layout helpers

`Row` and `Column` compute positions at build time and return a `List<PanelElement>`. They do not exist at runtime. An element enters a layout as an `ElementSpec`, produced by the element's static `spec(...)` factory. `.build()` returns positioned elements that go into a panel with `.add(...)`.

## Reference

A reference is the rectangle a panel is measured against. It is not the panel and has no relation to the panel's size. Three kinds exist: a container screen's frame, one slot group's bounding box, and the game window. `Reference` is the record that carries all three.

The call site picks the reference. `ScreenPanelAdapter` measures from the menu frame. `SlotGroupPanelAdapter` measures from the slot group it targets. `MKHudPanel.builder(...).region(...)` measures from the window. A region never names its reference.

## Panel on a vanilla screen

`VanillaScreenPanelAdapter` anchors a panel onto any non-container screen — Options, Controls, KeyBinds, world-select, server-list, the title screen, the pause menu, anywhere `Screen` (not `AbstractContainerScreen`) is the superclass. `.on(ScreenClass...)` or `.onAny()` declares targeting; `InsideRegion` anchors it, the same enum a HUD panel uses. Render and click both gate on the panel's visibility, so `showWhen(...)` holds here exactly as it does on a container screen: a hidden panel draws nothing and eats no clicks. v1 is region-based only, with no modal or dim-behind machinery — fold on evidence.

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

Groups are listed too, with no menu open, for a settings screen that runs from the title screen:

| Call | Answers |
|---|---|
| `SlotGroups.all()` | every declared group: one per vanilla category, and every created group |
| `SlotGroups.listing()` | the player-facing rows: each lone group, and each named set once |
| `SlotGroups.entryKey(id)` | the key a choice about a group on a live menu was saved under |

A category cannot tell a mod's pockets from the main inventory when both declare `PLAYER_INVENTORY`; a group can. A mod that splits one thing into many groups, one per anchor, puts them in one `SlotGroupSet` with `SlotGroups.declare(id, category, set)`, and the listing shows the set as one row. A choice saved under a set's key reaches every group in it.

Groups and sets are named like operations. `SlotGroups.name(id)` and `name(set)` are translatable on `slot_group.<...>` and `slot_group_set.<namespace>.<path>`; MenuKit ships vanilla's, and a mod ships its own. `SlotGroupId.asString()` and `SlotGroupSet.asString()` are stable text for a config file, with `parse` and a `CODEC` each. A row's `source()` names the mod it comes from, for a screen to show as "Pockets (Inventory Max)": `null` for vanilla's groups, otherwise the display name of the mod whose namespace the row is named in (a set's namespace, a created group's panel id before the colon, a mod's own category). With no loaded mod of that id it is the namespace itself. `SlotGroups.source(id)` answers the same for a group on a live menu.

A container-panel group registers at init and is listed from the title screen. A group built with a menu (`MKCSlots.onto`) is listed after the first menu that carries it; to list it from the title screen, its mod declares it at init with `SlotGroups.declare`.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search run by a mod that has never heard of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Operation

An operation is something done to a slot. Vanilla ships eleven, split one key per thing a player can do: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT` (double-click), `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `WORLD_PICKUP`. They are `BehaviorKeys.VANILLA_OPERATIONS`, every one on by default, and MenuKit enforces them at vanilla's own seams for every slot kind. A plain click takes, puts, or both when it swaps different items. A swap key has two slots, and both must allow it. The client refuses to send a click its own slot refuses, so that part holds on any server. Q, Ctrl-Q and F while playing, with no screen open, are the same `DROP`, `DROP_STACK` and `OFFHAND_SWAP` on the selected hotbar slot, refused by the client before it drops or sends and by the server when the action arrives. An offhand swap also needs the offhand slot to allow it.

An operation has a role: it takes items out of a slot, puts items in, or both. `SlotOperations.define(op, Role.TAKE)` records it and `SlotOperations.role(op)` reads it, so a settings screen can tell which operations matter for a lock on an item already in a slot. `define(op)` without a role means both.

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, ships a name and a description in its lang file, and asks `SlotOperations.allows` in its own code before acting. `SlotOperations.all()` lists every published operation. `SlotOperations.name(op)` and `description(op)` are translatable components on `slot_operation.<namespace>.<path>` and `.description`, so a settings screen can list them. MenuKit needs no change for a new operation to exist.

Two things decide what a slot allows. The first is the cascade, what the slot's author declared:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. `SlotOperations.inherent(category, operation, value)` sets the category rung once. A group's own declaration outranks it. A per-slot declaration outranks the group. A vanilla slot has one group per category on a menu, so for vanilla slots the group and the category are one rung, and "shift-click may land in the 9x3 but not the hotbar" is `inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)`.

The second is a veto. `SlotOperations.veto(rule)` registers a rule that can only say no, for a mod that is not the slot's author: a player's lock is the case. A veto sits beside the cascade and subtracts, so it never overwrites what the slot's author declared and has nothing to restore when the lock lifts. A veto that throws is logged once and skipped.

`SlotOperations.allows(menu, slot, player, operation)` is the one question: the cascade says yes and no veto says no. Every seam asks it, and so should every operation a mod adds. The `SlotRef` a veto sees carries the container and index, the live slot and menu when there is one, the acting player when there is one, and the slot's category. A move a mod makes on the server with no click behind it, such as a direct `quickMoveStack` call, still names a player: the one whose inventory the menu shows.

A mod that performs its operation by sending clicks wraps them: `SlotOperations.as(take, put, () -> sendClicks())`, or `as(op, ...)` when both sides are one operation. Each click sent inside carries the operation it serves, the first for the slot it takes from and the second for the slot it puts into. The vetoes judge that operation instead of the gesture, so a lock that refuses shift-click but allows restock lets the restock's shift-click through. The slot's author still judges the gesture. The tag reaches the integrated server. Clicks must be sent inside the block, on the calling thread. A simulated click that is not wrapped counts as the gesture it looks like.

Registration order does not matter. Read time does. Every operation is a server-tier key. It returns the key default until Containers installs its tier, and mod init order is not fixed. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `collect(false)` and `dragFill(false)`; any other operation is `set(key, value)`. `quickMove(NONE)` is deprecated sugar for the two shift-click keys.

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
