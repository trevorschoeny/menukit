## 6.1.0

Screen kinds and the pause-menu look. Every element now draws for the kind of screen it is on: MenuKit's grey look on container screens (inventories, chests), vanilla's pause-menu look on options screens (Options, Video Settings, Controls, Create World). A settings page can let the player pick items with the new item picker.

### Added

- `ScreenKind` (`CONTAINER`, `OPTIONS`) and one rule for where it comes from, `ScreenKind.of(screen)`: a vanilla `AbstractContainerScreen` is a container screen, any other vanilla screen an options screen, a standalone `MKScreen` the kind it declares when it is made (`super(title, ScreenKind.OPTIONS, panels)`, read back with `screenKind()`), and one that declares none a container screen, as before. Slot-group panels and the HUD are container. A dialog or any panel on a screen is that screen's kind. The kind travels in `RenderContext.kind()`, so every element follows it, inside sections, scroll areas, tabs and rows, and inside elements built from other elements (Keybindery's key button).
- Text colour by role. `TextRole` (`HEADING`, `BODY`, `MUTED`, `DISABLED`), `TextLabel.Builder.role(...)`, and `ScreenKind.textColor(role)` and `textShadow()`: dark grey on a container screen, vanilla's white and greys with a shadow on an options screen. A label with no role is `BODY`, which on a container screen is the colour it was. Checkbox, radio and section header labels follow the same table. `ElementConstants.TEXT_MUTED` names MenuKit's muted grey.
- On an options screen, the controls in vanilla's sprites: `Button` and the dropdowns are vanilla's buttons, `Checkbox` vanilla's checkbox, `Toggle` a vanilla button whose text is its state (its label, or vanilla's ON and OFF), a `Section` header the Controls screen's category header, a `Divider` vanilla's separator. A panel's `RAISED` background is vanilla's popup frame, so a dialog reads as vanilla's own.
- `ScrollContainer` on an options screen is the band every Options screen has: vanilla's darker list background, a separator above and below, and vanilla's thin list scroller. Its content is inset as in vanilla's lists: `ScrollContainer.OPTIONS_INSET_X` (10) from the left, the scrollbar lane on the right, `OPTIONS_INSET_Y` (4) above the first child and below the last, scrolling with the content. Size content for it with the new `ScrollContainer.viewportWidthFor(width, kind)`. A panel that auto-scrolls on an options screen lays its elements out inside the inset itself.
- `Tabs` in `WRAP` and `SIDE_SCROLL` on an options screen is the tab bar of Create World: white labels, the selected one underlined and open into a page of the list background, the separator on either side. The page insets its body the same way, `OPTIONS_INSET_X` from both sides (the scrollbar lane on the right when it scrolls) and `OPTIONS_INSET_Y` above and below, so a spacer row, a full-width divider and wrapped text stay inside the margin. `SIDEBAR` keeps MenuKit's drawing.
- `InputContext.kind()`, the kind of screen, as `RenderContext.kind()` carries it, so an element answers input with the geometry it drew. The five-argument constructor stays and means a container screen.
- `ItemPicker`, a setting whose value is an item: a slot with a lens onto an `Item` (`state(get, set)`, `Items.AIR` for none). A click opens a chooser over the screen, of the same kind: every item in a scrolling grid, a search field, Clear and Cancel. A right click empties it. It is client-only and never touches the inventory or the server: the item is a setting value, not the player's. It draws as vanilla's slot on an options screen and MenuKit's on a container screen.

The kind changes how an element draws, never its size or behaviour, except that the options list band (a `ScrollContainer`, a `Tabs` page) insets its content. `Slider` and `TextField` are vanilla's own widgets and look the same on both. Where vanilla has no twin (radio buttons, info boxes, progress bars, colour swatches, consumer sprites) MenuKit's drawing stays.

### Changed

- Panels injected into a vanilla screen that is not a container (the pause screen, Options, Controls, a mod's config screen) draw in vanilla's look there, where they drew MenuKit's grey.
- `ScrollContainer` with no `contentHeight` reads its children's extent every frame, so the range follows a child that grows or shrinks while shown.

### Deprecated

- `ControlStyle`, and `style(ControlStyle)` on `Button`, `Dropdown` and `DropdownMulti`. They do nothing now, since nothing picks a look but the kind of screen, and they go in 7.0.0. A mod built against 6.0.0 still loads. Delete the call; on an options screen the element already draws in vanilla's look.

### Fixed

- A dialog or a panel laid out with `Row` or `Column` can be built at mod init, where adapters are declared. `Row` and `Column` measured their elements in `build()`, and a label's or button's size needs the game's font, which does not exist yet at init, so `ConfirmDialog` and `AlertDialog` could only be built after the declaration freeze had closed. They now place their elements on the first read of any element's position, in practice the panel's first layout pass. A `Row` or `Column` holding a custom `ElementSpec` still lays out in `build()`, and a spacer without `width(px)` still throws there.
