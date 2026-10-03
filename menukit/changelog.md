## 6.1.0

The pause-menu look. A menu can now look like the screens reached from the pause menu (Options, Video Settings, Controls) instead of an inventory. Nothing breaks, and nothing changes look unless a mod asks.

### Added

- One switch for a whole screen or panel. `MKScreen.elementStyle(ControlStyle.VANILLA)` sets the look of every element on a standalone screen; it reaches every panel with no background of its own (`PanelStyle.NONE`), where vanilla's blurred backdrop shows, and a panel that draws MenuKit's grey keeps MenuKit's look. `Panel.Builder.elementStyle(...)` sets it for one panel on any screen. Every element follows it, inside sections, scroll areas, tabs and rows, and inside elements built from other elements (Keybindery's key button). An element's own `style(...)` still wins, and every element's builder now takes it. The look travels in `RenderContext.style()`.
- Text colour by role. `TextRole` (`HEADING`, `BODY`, `MUTED`, `DISABLED`), `TextLabel.Builder.role(...)`, and `ControlStyle.textColor(role)` and `textShadow()`: dark grey on MenuKit's panel, vanilla's white and greys with a shadow on its backdrop. A label with no role is `BODY`, which in MenuKit's look is the colour it was. Checkbox, radio and section header labels follow the same table. `ElementConstants.TEXT_MUTED` names MenuKit's muted grey.
- The controls in vanilla's sprites: `Checkbox` draws vanilla's checkbox, `Toggle` is a vanilla button whose text is its state (its label, or vanilla's ON and OFF), a `Section` header is the Controls screen's category header, a `Divider` is vanilla's separator, and `Button` and the dropdowns follow the switch.
- `ScrollContainer` in the vanilla look is the band every Options screen has: vanilla's darker list background, a separator above and below, and vanilla's thin list scroller.
- `Tabs` in `WRAP` and `SIDE_SCROLL` in the vanilla look is the tab bar of Create World: white labels, the selected one underlined and open into a page of the list background, the separator on either side. `SIDEBAR` keeps MenuKit's look.

The look changes how an element draws, never its size or behaviour, so a screen switches without its layout moving. `Slider` and `TextField` are vanilla's own widgets and look the same in both. Where vanilla has no twin (radio buttons, info boxes, progress bars, colour swatches, consumer sprites) MenuKit's drawing stays.
