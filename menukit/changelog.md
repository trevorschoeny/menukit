## 7.2.0

Additions only. Nothing was removed, and mods built for 7.1.0 work as before.

### Added

- A colour setting. `ColorField` shows a swatch of a colour and opens a dialog when clicked. The first tab holds Minecraft's 16 dye colours, shown by their dye items. The second is a full picker: a saturation and brightness square, a hue bar, a transparency bar when the value has alpha, and a hex field that only accepts valid hex. Changes show at once, and Cancel puts back the colour from before. The value is an int, `0xRRGGBB`, or `0xAARRGGBB` with `alpha(true)`. The swatch and its dialog draw like the rest of the screen, on an inventory and on an options screen. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-colour-setting).
- A home for widgets made elsewhere. `WidgetHost` puts any vanilla-style widget, such as a custom control another config library hands over, in a panel. It moves the widget with the panel and its scroll, clips it to its box, passes it clicks, drags, the wheel and typed keys, and gives it keyboard focus and narration. A widget that opens a list under itself can draw over everything with `overflow()`. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#host-a-widget-another-library-made).
- A sidebar menu without its tabs. `tabColumn(false)` on a `SIDEBAR` `Tabs` leaves out the column and gives the page the full width. A settings screen for one mod can then look exactly like a tabbed one: the same darker page, insets, bottom line, scrollbar and header across the top.

### Fixed

- A `TextField` or `Slider` on a panel over a vanilla screen, such as the inventory, now takes clicks. Before, they only worked on a mod's own screen.
- A `TextField` or `Slider` whose panel is hidden, or whose tab is not shown, now lets go of keyboard focus instead of taking keys out of sight, and Tab skips it.
