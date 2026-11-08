## 8.0.0

#### Added

- `MK.drawBackdrop(screen, graphics, partialTick)` draws a screen that isn't open as it would look open, for a screen drawn behind another. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#backdrop).
- `Panel.transform(supplier)` and `PanelTransform(scale, pivotX, pivotY, alpha)` draw a panel scaled about a screen point and faded, without moving its layout. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#transform).
- `ItemAnimation.HIDDEN` hides a slot's item along with its count, durability bar and cooldown, for an item shown somewhere else meanwhile. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-item-looks).
- `HotbarDecorations.under(id, decoration)` and `.hideItem(id, rule)` draw on the HUD hotbar's own slots, under their items, and let a mod leave a slot's item out while drawing it elsewhere. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#hotbar-decorations).
- Public `DrawAlpha` and `ItemOutline`, for a mod drawing its own copy of an item: `DrawAlpha.push`/`pop` fades fills, sprites and text, and `ItemOutline.silhouettes` draws a flat outline color. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#item-copies).
- `ListView.Builder.controlsOutside()` hangs a row's controls outside the list, past its right edge, instead of inside each row.
- `Floating.at(originSupplier, element)` puts one element of a panel at a screen point of its own, read every frame. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#outside-the-panel).
- `Panel.litWhileDimmed(() -> List<ScreenRectangle>)` leaves parts of the screen lit while a panel dims behind it. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#outside-the-panel).
- `PanelPosition.pixelBottomLeft(Supplier<ScreenOrigin>)` places a panel like `pixel`, but from its bottom-left corner, so a panel that grows taller grows upward. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#placement).
- The item picker's dialog is rebuilt around one rule instead of match modes: `name()`, `enchantments()`, `components()`, `count()`, `advanced(row)`, `button(label, onClick)` and `onDone(...)` on the builder, and `open(screen)` to open the dialog with no picker on a page. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#item-picker).
- `ScrollContainer.Builder.fitRoom(step, least)` holds a scroll area at its declared height while there is room and gives rows back down to `least` instead of running off the window. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#scrolling-slot-grid).
- `Flow.Builder.ownWidths()` lays a row's children out at their own widths, a row of columns. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#layout-helpers).
- `SlotDecorations.draw(under, graphics, screen, slot, stack, x, y)` draws a slot's under or over decorations at a place of your choosing, for a mod that draws a slot somewhere other than its screen position. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-decorations).

#### Removed

- `HudCycleStrip` and everything in it (sources, views, styles, the arc, `slide`, `begin` and `slideProgress`). A ring of stacks beside the hotbar is the mod's own look now, on a `HudPanel` and `HotbarDecorations`.
- `AlertDialog` (use `ConfirmDialog`, or a `Panel.modal()` of your own) and `InfoBox` (a `TextLabel`), both unused.
- `HudNotification` with `MK.notify` and `MK.registerNotification`, unused.
- `HudSlot`, `HudPanel.SlotBuilder` and `HudPanel.Builder.slot(...)` with `HudPanel.STATUS_SCALE` (use `item(...)`, sized).
- `ElementSpec` with `Row`/`Column.add(ElementSpec)`; a layout takes built elements only.
- `ScreenKind.OPTIONS` and `CONTAINER` (use `DEFAULT`/`PANEL`).
- `ScrollContainer.OPTIONS_INSET_X`/`OPTIONS_INSET_Y` (use `DEFAULT_INSET_X`/`DEFAULT_INSET_Y`).
- `OwnHost.add(Panel, PanelPosition, int)` (use `add(Panel, int)`, which reads the position from the panel's placements).
- The item picker's match modes: `ItemMatch.Match` and its `CODEC`, `match(...)`, `playerChoosesMatch()`, `ignoring(...)`, `ignoringCount()`, the accessors `match()`, `ignored()` and `countIgnored()`, the four-argument `ItemMatch` constructor, and the picker's own `match()` and `countIgnored()` getters. Replaced by `name()`, `enchantments()`, `components()`, `count()` on the builder.

#### Fixed

- A tab row's band beside its tabs took a click meant to drop a held item outside the frame, so nothing dropped there.
- The item picker's "Show advanced settings" box no longer remembers being ticked across openings or between pickers; it starts hidden each time, opening on its own only for a slot that already has a count, name, enchantments or components set.

## 7.5.1

#### Fixed

- A scissor-stack underflow crash from tabbing fast between swapped container screens.

## 7.5.0

#### Added

- `SlotDecorations.under(id, decoration)` and `.over(id, decoration)` draw a mark under or over the item in every container slot, with an optional tooltip. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-decorations).
- `ContainerTabs.groups(id, scope, groups, keys)` declares tab groups that come and go at runtime, one per group key, each with its own header and sub-tabs. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-tabs).
- Tab groups nest. A group whose key is one of another live group's keys stands inside that group, in place of the sub-tab it would have been, at any depth.
- `ContainerTabs.frameStaysWhen(scope, screens, when)` keeps a container screen's frame at vanilla's place while its tab rows come and go, moving it only when a row wouldn't fit on the window.
- `Flow.Builder.wrapAfter(n)` caps how many children a column or row holds before it wraps, and `keepLast()` keeps the last ones in a vertical Flow's first column. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#layout-helpers).
- `Contribution.Builder.wrapAfter(scope, n)` caps how many contributions a shared panel holds in a row, or in a column for a vertical panel, per game mode, and the rest wrap. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#shared-panels).
- `ItemAnimation.swapped(...)` and `ItemAnimation.AS_IS` let a slot item look draw another stack in the item's place, not only animate the item. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-item-looks).
