## 7.0.0

MenuKit is a UI library only now. Everything about slots moved to SlotKit, a separate library built on MenuKit, and what 6.x deprecated is gone. Mods built for MenuKit 6 need an update. [Upgrading to 7.0.0](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md) has the steps, and tells you whether your mod needs SlotKit.

### Moved to SlotKit

- Slot groups, categories, sets and `Storage` (`api.slot`), the window, addresses, slot operations, gates and vetoes (`api.window`), and `SlotGroupPanelAdapter`. SlotKit keeps their saved text, so choices a mod saved stay valid. A mod that uses any of them adds SlotKit and changes those imports.
- MenuKit no longer patches slot clicks, drops, hoppers, dispensers or the inventory. SlotKit does.

### Removed

- `ControlStyle` and its helpers, and `style(ControlStyle)` on `Button`, `Dropdown` and `DropdownMulti`. They did nothing since 6.1.0. Delete the call.
- `Tabs.SIDEBAR_SCROLLBAR_WIDTH`. Use `ScrollContainer.TRACK_WIDTH` on a container screen, 6 on an options screen.
- `MKTooltip.MIN_WRAP_WIDTH`. Nothing read it.
- The window's panel and element handles and the `VISIBILITY` and `OPACITY` keys. A panel's own `visibleWhen` and `opaque` do the job.

### Changed

- `Declarations` moved to `com.trevlar.menukit.api.Declarations`. Its `requireOpen` is public, so a library built on MenuKit freezes its own declarations with MenuKit's.

### Added

- `Anchor` and `AnchorPanelAdapter` place a panel around a rectangle a mod names and supplies on any screen, such as the pause menu's buttons. Panels on one anchor stack in its regions.
- `api.host`, for a library that ships its own screens. `OwnHost` lays out a screen's own panels and joins them to MenuKit's claims and draw order, and `SlotScreenHook` routes hover and clicks to slots drawn in panels.
