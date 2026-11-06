# Upgrading from older versions

What changes for a mod moving to each MenuKit release, newest first. The latest release's notes are on the [README](https://github.com/trevorschoeny/menukit#upgrading), and [Upgrading to 7.0.0](upgrading-7.md) walks through the move from 6.x and the 7.4.0 and 7.5.0 changes step by step. The [changelog](https://github.com/trevorschoeny/menukit/blob/main/menukit/changelog.md) has the full list for each.

## To 8.0.0

The item picker's match modes are gone. `ItemMatch.Match`, `match(...)`, `playerChoosesMatch()`, `ignoring(...)`, `ignoringCount()` and the dialog's Search and Edit tabs are replaced by one rule: the item always counts, each field filled in narrows the match, and a blank one is not checked. The dialog's advanced settings open in a column beside the grid, behind a "Show advanced settings" box MenuKit remembers for the player, and what they offer is now the picker's choice: `name()`, `enchantments()`, `components()` and `count()`. Then:

- `new ItemMatch(stack, Match.X)` becomes `new ItemMatch(stack)`, and `ItemMatch.matches(rule, Match.X, stack)` becomes `ItemMatch.matches(rule, stack)`.
- An `EXACT` picker that edited names, enchantments and components turns on `name().enchantments().components()`. One that ignored damage needs nothing, since damage is checked only when the player lists it. An `ITEM` picker needs nothing.
- An enchantment's level can be left blank, which matches any level: `ItemMatch.anyLevel()`, saved as `any_level`. `ItemMatch` gained that third component; `new ItemMatch(stack, count)` still makes a rule with none.
- A count was part of `EXACT`. It is now a number for the mod, never matched: `count()` on a `rule` or `rules` picker, read with `rule.count()`: `ItemMatch.NONE` when the player left it at N/A, else 1 up to the item's stack size.
- Rules saved by 7.x load through `ItemMatch.CODEC` as they matched, with one exception. An `exact` rule refused a stack that had more than the rule (a plain sword refused an enchanted one), and now that stack matches.

`ItemPicker.open(screen)` opens the dialog over any screen with no picker on a page, and `button(...)` and `onDone(...)` let a mod put its own button in the dialog and apply a draft on Done.

A centered dialog taller than the window now gives back rows from a list that fits the room (`ScrollContainer.Builder.fitRoom`), and past that it scrolls, instead of running off the window. `fitRoom(step, least)` holds a list at its declared height while there is room and gives rows back down to `least`. A `Slider`'s label is made every frame, so it may read more than the value. `Flow.Builder.ownWidths()` lays a row's children out at their own widths, a row of columns. A mod built for 7.x declares `"menukit": ">=8.0.0 <9.0.0"` once it is rebuilt.

The cycle strip is gone: a ring of stacks beside the hotbar is the mod's own look now, drawn on two building blocks. `HudCycleStrip` and everything in it (sources, views, styles, the arc, `slide`, `begin` and `slideProgress`) are removed:

- A row, column or cross beside the hotbar is a `HudPanel` in `region(OutsideRegion.RIGHT_ALIGN_BOTTOM)` holding one element of the ring's live size, `.gap(4)` to sit where the strip did. Another mod's panel there stacks above it. Time a slide with `Easing.SPRING` and `Util.getMillis()`.
- An arc over the hotbar's slots is a `HotbarDecorations.under(id, decoration)`, drawn after the hotbar's sprites and before its items. While a turn moves the held stack, `HotbarDecorations.hideItem(id, rule)` leaves the slot's own item out. On a screen, the same arc is a `SlotDecorations.under` on the hotbar's slots, as before.

Also removed, unused: `AlertDialog` (use `ConfirmDialog`, or a `Panel.modal()` of your own), `InfoBox` (a `TextLabel`), `HudNotification` with `MK.notify` and `MK.registerNotification`, `HudPanel.Builder.slot(...)` with `HudPanel.STATUS_SCALE` (use `item(...)`, sized), and `ElementSpec` with `Row`/`Column.add(ElementSpec)`: a layout takes built elements only.

## To 7.3.0

Nothing breaks, and mods built for 7.2.0 work as before. The looks have new names: `ScreenKind.OPTIONS` is now `DEFAULT` and `CONTAINER` is now `PANEL`. The old names still work and go in 8.0.0, but a `switch` over `ScreenKind` must name the new ones. Slots shown in a panel now draw on top of it, so a mod that hooks vanilla's whole slot pass sees those slots drawn after it. A mod that uses anything new in 7.3.0 declares `"menukit": ">=7.3.0 <8.0.0"`.

## To 7.2.0

One change can affect existing code. `ItemPicker`'s protected constructor changed, so a mod that subclasses `ItemPicker` updates its constructor call. Other mods built for 7.1.0 work as before, and an item picker a mod already has opens the new item dialog with no change. A mod that uses anything new in 7.2.0 declares `"menukit": ">=7.2.0 <8.0.0"`.

## To 7.1.0

Nothing breaks, and mods built for 7.0.0 work as before. A mod that uses anything new in 7.1.0 declares `"menukit": ">=7.1.0 <8.0.0"`.

## To 7.0.0

MenuKit keeps no slot code now. Everything about slots moved to SlotKit, a separate library built on MenuKit, and the deprecated parts of 6.x are gone. [Upgrading to 7.0.0](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md) walks through the move. Declare `"menukit": ">=7.0.0 <8.0.0"`.

- Slot groups, categories, slot operations, vetoes, addresses, the window, and `SlotGroupPanelAdapter` are SlotKit's. A mod that uses any of them depends on SlotKit and changes those imports. Nothing else changes for it.
- `Declarations` moved to `com.trevlar.menukit.api.Declarations`.
- `ControlStyle`, `style(ControlStyle)` on `Button`, `Dropdown`, and `DropdownMulti`, `Tabs.SIDEBAR_SCROLLBAR_WIDTH`, and `MKTooltip.MIN_WRAP_WIDTH` are removed. Delete a `style(...)` call, since it has done nothing since 6.1.0. Use `ScrollContainer.TRACK_WIDTH` (6 on an options screen) for the scrollbar width.
- The window's panel and element handles (`Window.panel`, `Window.element`) and the `VISIBILITY` and `OPACITY` keys are gone. Use the panel's own `visibleWhen` and `opaque`.
- `Anchor` and `AnchorPanelAdapter` are new. They place a panel around a rectangle a mod names and supplies on any screen, and SlotKit's slot-group panels are built on them.

## To 6.2.0

`MKTooltip.avoidCoverWhen` docks tooltips at the bottom or top center of the screen instead of choosing a side of the cursor. The method keeps its name, so a mod that registers it needs no change. `MKTooltip.MIN_WRAP_WIDTH` is deprecated and goes in 7.0.0.

## To 6.1.0

Elements draw for the kind of screen they are on (`ScreenKind`), and nothing else picks a look. A mod that uses screen kinds, text roles, or the item picker declares `"menukit": ">=6.1.0 <7.0.0"`.

- `ControlStyle` and `style(ControlStyle)` on `Button`, `Dropdown`, and `DropdownMulti` are deprecated and do nothing, and 7.0.0 removes them. A mod built against 6.0.0 still loads. Delete the call. An element already draws in vanilla's look on Options, Controls, and every other vanilla screen that is not a container, and in MenuKit's look on a container screen.
- Panels injected into a vanilla screen that is not a container (the pause screen, Options, Controls, or a config screen) draw in vanilla's look there. Before 6.1.0 they drew MenuKit's gray. Panels on inventories, chests, and the HUD are unchanged.
- To move a standalone screen to the pause-menu look, declare the kind when the screen is made, with `super(title, ScreenKind.OPTIONS, panels)`. Give its panels `PanelStyle.NONE`, and replace hand-set label colors (`TextLabel.COLOR_DARK`, `Section.colors`) with roles, which read on both kinds. A screen that declares nothing stays as it was. [Recipes](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md) builds a whole settings screen this way.

## To 6.0.0

6.0.0 is a consolidation release. It adds little and renames and removes a lot at once, so every mod migrates one time. These are the changes most mods meet.

- Public types moved into `api` packages, and Containers has its own root, `com.trevlar.menukit.containers`. Every import changes. The classes in the table below were renamed as well.
- Placement is declared on the panel, as `Panel.builder(id).position(PanelPosition.region(OutsideRegion.X).priority(n).offset(dx, dy))`. The adapters take only `(panel)` or `(panel, padding)`. A HUD panel uses `.region(InsideRegion.X).offset(dx, dy)` where it used `.anchor(MKHudAnchor.X, dx, dy)`.
- Elements are built by builders, for example `Button.builder().label(text).size(w, h).onClick(run).build()`. A control that shows a value takes `state(get, set)` over a field the mod owns.
- Declarations happen at init. Adapters, HUD panels, operations, slot groups, channels, and menus declared after startup throw.
- Custom menus split by side. A menu's slots are common code. How its panels look, and its screen class, are declared on the client with `ClientMenu.of(menu)`.
- A shared slot-state channel names its writers. Without a `canWrite` rule the server refuses every client write to it.
- Item Tips left MenuKit. Durability and food lines on tooltips are now a consuming mod's feature.

| 5.x | 6.0.0 |
| --- | --- |
| `MKHudPanel`, `MKHudNotification`, `MKHudIcon`, `MKHudSlot` | `HudPanel`, `HudNotification`, `HudIcon`, `HudSlot` |
| `MKFocus`, `MKText` | `Focus`, `Text` |
| `MKCContainerPanel` | `ContainerPanel` |
| `MKCSlot`, `MKCSlots` | `CreatedSlot`, `CreatedSlots` |
| `MKCMenu` | `CustomMenu` |
| `MKCScreenHandler` | `CustomContainerMenu` |
| `MKCHandledScreen` | `CustomContainerScreen` |
| `MKCBehaviorKeys` | `ContainerKeys` |
| `MKSlotState` | `SlotState` |
| `MKCSlots.renderGroup` | `ClientSlots.renderGroup` |
| `MKCContainerPanel.Builder.chrome(...)`, `.parity(...)` | `ClientContainerPanel.of(id).chrome(...).parity(...)`, on the client |
| `CreatedSlotAdapter.addressOf(panelId, groupId, index)` | `Address.createdSlot(CreatedSlots.groupId(panelId, groupId), index)` |
| `SlotSpec.quickMove(...)`, `MKCBehaviorKeys.QUICK_MOVE` | `SlotSpec.shiftClickOut(false)`, `shiftClickIn(false)`, and `BehaviorKeys.SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN` |
| `WORLD_PICKUP` | `INVENTORY_INSERT` |
| `showWhen(...)` on a panel, a HUD panel, or a container panel | `visibleWhen(...)` |
| `getId()` | `id()` |

## To 5.1.0

Nothing breaks. `SlotSpec.quickMove(...)` and `MKCBehaviorKeys.QUICK_MOVE` are deprecated for removal in 6.0.0. Declare `BehaviorKeys.SHIFT_CLICK_OUT` and `SHIFT_CLICK_IN` instead.

## To 5.0.0

Four region enums are two, and two bounds records are one. Replace the type names. The constants inside are unchanged.

| Was | Is |
| --- | --- |
| `MenuRegion`, `SlotGroupRegion` | `OutsideRegion` |
| `HudRegion`, `ScreenRegion` | `InsideRegion` |
| `ScreenBounds`, `SlotGroupBounds` | `Reference` |
| `HudRegion.CENTER_CROSSHAIR_CLEARANCE` | `RegionConstants.CENTER_CROSSHAIR_CLEARANCE` |

With Containers, every created slot group declares a `SlotGroupCategory`, through `SlotSpec.at(id, category)`, `MKCSlots.onto(...).category(...)`, and `PanelBuilder.group(id, category, storage)`. A group without one fails at runtime on the first menu it opens.

## To 4.0.0

Vanilla draws every slot, and MenuKit runs no slot pass of its own. `CreatedSlotResolver.resolve` returned the live in-menu `Slot` instead of a position, and `SlotRendering` kept the frame helper and dropped the item-drawing helpers.

## To 3.0.0

The Java package moved from `com.trevorschoeny.menukit` to `com.trevlar.menukit`. No class or method names changed.
