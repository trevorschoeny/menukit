# Limits

What MenuKit does not do, and the open gaps a consumer can hit. Slots are SlotKit's, and SlotKit lists its own. [concepts.md](concepts.md) defines the terms.

## Out of scope

These are not planned. Use vanilla or another library.

- A config framework (option types, save files, a generated screen). MenuKit provides the controls, tabs and settings rows. Where a value lives and how it saves is up to the mod. For a generated config screen, use YACL or Cloth Config.
- Chat and the F3 overlay. Both render outside the `Screen` system `VanillaScreenPanelAdapter` targets.
- Nested panels. A panel holds elements only.
- Themes and skins. `PanelStyle` and the two screen kinds (`ScreenKind`) are the full set. An element's look follows its screen's kind and cannot be picked per element, panel or screen. MenuKit restyles nothing of vanilla's except the pressed look of buttons ([concepts](concepts.md#one-styling-change)).
- Tooltip content. Item Tips (durability and food lines) left MenuKit in 6.0.0 and is a feature of Inventory Plus.
- Animation beyond HUD notifications.
- A cross-mod event bus.
- Drag and drop between elements.
- In-world rendering.
- Persistence for element state. The consumer stores the value; the element reads it through a supplier.
- Input on the HUD. HUD panels render only. For a clickable control, open a standalone screen from a key binding.
- Testing UI. The library ships no test panels or commands.

## Open gaps

Behavior that is incomplete in the current release.

| Area | Current behavior |
|---|---|
| Unplaced panels on a standalone screen | The default stacks later panels below the main one with a 14 pixel gap, computed for the case where every earlier stacked panel is shown. With one hidden, the panels after it sit 12 pixels lower than the gap alone places them. Declaring `region(BOTTOM_CENTER)` with an explicit `.offset` gives exact spacing. |
| Offsets and stacking | `.offset(dx, dy)` moves one panel after placement, and siblings stack on its un-nudged size. An offset that pushes a panel over its sibling overlaps it. |
| Item picker | Holds one kind of item (an `Item`, with no count, enchantments or other components). A list of items is a row of pickers. Its chooser lists every registered item in registry order, searched by name and id, not by creative tab. Clearing from the keyboard is not supported. A right click or the chooser's Clear empties it. |
| A look per element | None since 6.1.0, and 7.0.0 removed `ControlStyle`. No switch makes a control differ from its screen's kind (a MenuKit-grey button on an options screen). Draw such a control with a sprite of your own (`Button.builder().sprite(...)`). |

## Element gaps

| Element | Current behavior |
|---|---|
| `Row`, `Column` | Build time only. For a row that follows the panel's width at runtime, use `Flow` with `Flow.spacer()`. No grid helper. |
| `Dropdown` | Fixed item list. No type-to-filter. |
| Secondary click and tint | `Button` and `Toggle` only. Other shipped elements take left clicks alone. A custom `PanelElement` handles any button in its own `mouseClicked`. |
| `ScrollContainer` | Vertical only. No keyboard scrolling. The scrollbar stays visible. |
| `Slider` | A 0 to 1 fraction, whole numbers (`ofInts`) or an enum's constants (`ofEnum`). No range handle, no vertical orientation. |
| `Slider`, `TextField` on a panel injected onto a vanilla screen | The claim routes clicks to the panel's elements before vanilla sees them, and these two take their clicks as vanilla widgets, so a click does not reach them there. On a standalone screen (`MKScreen`, or a library's own screen with an `OwnHost`) they work. |
| `Slider`, `TextField` in a hidden panel | Stay registered with the screen and can keep keyboard focus. Blur them (`screen.setFocused(null)`) before hiding their panel. |
| Keyboard focus on `Button`, `Toggle`, `Checkbox`, `Radio` | Follows the screen's widget order, which is panel order. An element that stops being drawn loses focusability a quarter second later, not the same frame. |
| Disabled `Tabs` | The strip and the body are inert and the body greys; the strip's labels keep their colours. |
| Item outline (`SlotRendering.drawItemOutline`, `ItemDisplay.outline`) | Items whose model reaches outside their 16 by 16 box, which vanilla draws through a separate path, draw without the outline. |
| Auto-sizing elements with supplier content | The build measures width once. Reserve width for the longest expected content. |

## Runtime constraints

- `build()` freezes a panel's element list, and every element is immutable after its own `build()`. Build a new panel to change the list.
- A `ScreenPanelAdapter` with no target renders on every container screen. An `AnchorPanelAdapter` or `VanillaScreenPanelAdapter` with no `.on(...)` fails at the first screen open with the panel id in the message.
- Adapters, anchors, HUD panels and notifications are declarations. Constructing, targeting or unregistering one after the client starts throws. Gate a panel at runtime with `visibleWhen`.
- A panel with no placement is rejected by adapters and the HUD. Only `MKScreen` and a library's own screen (`OwnHost`) place an unplaced panel.
- Panel ids are global across mods. Prefix them with the mod id.
- Only the `api` packages are public. Everything else is internal and may change in any release.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
