## 7.2.0

Additions only. Nothing was removed, and mods built for 7.1.0 work as before.

### Added

- A colour setting. `ColorField` shows a swatch of a colour and opens a dialog when clicked. The first tab holds Minecraft's 16 dye colours in rainbow order, shown by their dye items and named by colour. The second is a full picker: a saturation and brightness square, a hue bar, a transparency bar when the value has alpha, and a hex field that only accepts valid hex. Changes show at once, and Cancel puts back the colour from before. The value is an int, `0xRRGGBB`, or `0xAARRGGBB` with `alpha(true)`. The swatch and its dialog draw like the rest of the screen, on an inventory and on an options screen. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-colour-setting).
- A home for widgets made elsewhere. `WidgetHost` puts any vanilla-style widget, such as a custom control another config library hands over, in a panel. It moves the widget with the panel and its scroll, clips it to its box, passes it clicks, drags, the wheel and typed keys, and gives it keyboard focus and narration. A widget that opens a list under itself can draw over everything with `overflow()`. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#host-a-widget-another-library-made).
- A `Tabs` as tall as the tab it shows. `fitShownTab()` on a `Tabs` with no fixed size makes it as tall as the shown tab instead of the tallest, so a dialog holding it resizes when you switch tabs and its buttons follow. The colour dialog uses it.
- A sidebar menu without its tabs. `tabColumn(false)` on a `SIDEBAR` `Tabs` leaves out the column and gives the page the full width. A settings screen for one mod can then look exactly like a tabbed one: the same darker page, insets, bottom line, scrollbar and header across the top.

### Fixed

- Dialogs have the same margin on every side. `ConfirmDialog`, `AlertDialog` and the colour dialog leave 4 px between the frame and their content on all four sides, and the buttons sit centred between the content above them and the frame below. Before, the content sat 8 px further from the left and top of the frame than from the right and bottom. `ConfirmDialog.PADDING` and `SECTION_GAP` (and `AlertDialog`'s) are now 4.
- A `RAISED` panel on an options screen keeps 4 px between vanilla's popup frame and its elements, as it does inside MenuKit's frame on an inventory screen. Before, its content came within 1 px of the frame. The panel is 6 px bigger each way.
- A `Tabs` with no fixed size that grows after it is placed, for example on an options screen's page, pushes the elements below it down instead of covering them.
- A `TextField` or `Slider` on a panel over a vanilla screen, such as the inventory, now takes clicks. Before, they only worked on a mod's own screen.
- A `TextField` or `Slider` whose panel is hidden, or whose tab is not shown, now lets go of keyboard focus instead of taking keys out of sight, and Tab skips it.
