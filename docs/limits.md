# Limits

What MenuKit does not do, and the open gaps a consumer can hit. Slots are SlotKit's, and SlotKit lists its own. [concepts.md](concepts.md) defines the terms.

## Out of scope

These are not planned. Use vanilla or another library.

- A config framework (option types, save files, a generated screen). MenuKit provides the controls, tabs and settings rows. Where a value lives and how it saves is up to the mod. For a generated config screen, use YACL or Cloth Config.
- Chat and the F3 overlay. Both render outside the `Screen` system `VanillaScreenPanelAdapter` targets.
- Nested panels. A panel holds elements only.
- Themes and skins. `PanelStyle` and the two looks (`ScreenKind.PANEL` and `ScreenKind.DEFAULT`) are the full set. An element's look follows its screen's and cannot be picked per element, panel or screen. A resource pack can restyle the Panel look ([resource packs](resource-packs.md)), but a mod can't. MenuKit restyles nothing of vanilla's except the pressed look of buttons ([concepts](concepts.md#one-styling-change)).
- Tooltip content. Item Tips (durability and food lines) left MenuKit in 6.0.0 and is a feature of Inventory Plus.
- Animation beyond HUD notifications and the cycle strip's slide.
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
| Offsets and stacking | `.offset(dx, dy)` moves one panel after placement, and siblings stack on its un-nudged size. An offset that pushes a panel over its sibling overlaps it. `.gap(n)` takes 0 or more pixels, so stacked panels can touch but a gap cannot overlap them. |
| Item picker | The components box offers no suggestions while the player types, although vanilla's `/give` does. Its errors are vanilla's. Search reads names, ids and tooltips, not `#tags` or `@mods`. No quick-move with the number keys or a double click to collect. The dialog opens only on a screen. Emptying from the keyboard is not supported. With no world open, the first picker shown loads vanilla's data once (a short pause), and enchantments and stacks are vanilla's own. |
| A look per element | None since 6.1.0, and 7.0.0 removed `ControlStyle`. No switch makes a control differ from its screen's look (a MenuKit-grey button in the Default look). Draw such a control with a sprite of your own (`Button.builder().sprite(...)`). |

## Container region

| Area | Current behavior |
|---|---|
| Mods that work out slot positions themselves | A mod that computes where a container's slot is (`leftPos + 8 + column * 18`) instead of reading the slot's `x` and `y`, or hit-tests slots itself instead of asking vanilla, can point at the wrong place, since the slots are where the list puts them and the screen's bounds are both panels'. |
| The screen's own container | A section with no `source` is the block under the crosshair as the screen opened, when that block holds a container the screen's size and the screen has its title. A screen opened by a key while looking at another unnamed container of that size and kind (a remote chest's screen in front of a chest) takes that container's art and name. Give such a section a `source`. |

## Resource packs

| Area | Current behavior |
|---|---|
| Fully textured art | A container texture whose fill isn't one flat color (wood grain, a picture over the inventory), or whose edges have no plain stretch to tile, can't be cut, and the panel keeps MenuKit's look. |
| Per-color art | Art per dye or per name comes only through OptiGUI rules, and MenuKit follows them only while OptiGUI 2.x is installed. Without it, every shulker box section takes the pack's one shulker box texture. |
| OptiGUI selectors | Selectors no section can meet never match, so a rule that uses one never applies to a section: `comparator.output`, `beacon.levels`, `book.page.count`, `book.page.current`, and the horse, donkey, llama and villager selectors. Rules for entities, the beacon and the player inventory never apply to a section. `interaction.hand` always reads as the main hand. |
| Fallback names | A section whose title a pack blanks is called by its source's or its block's name, in the player's language. One with nothing to name it by (no `name(...)`, no source with a block or custom name, or for the screen's own container no block found) is called by vanilla's built-in English for its title's key ("Shulker Box"), whatever the player's language. Give it a `name(...)` to have it translated. |
| HUD panels | They keep MenuKit's art and follow only a pack's override of `menukit:panel_raised`, not container art. |

## Element gaps

| Element | Current behavior |
|---|---|
| `Row`, `Column` | Build time only. For a row that follows the panel's width at runtime, use `Flow` with `Flow.spacer()`. No grid helper. |
| `Dropdown` | No type-to-filter. Options from a supplier (`items(() -> list)`) are read as the popover needs them; a picked option that leaves the list stays the consumer's value. |
| `ListView` | Vertical only. A row must be a MenuKit element (an `AbstractPanelElement`) so the list can place it. The list itself takes no Tab focus; a click in it gives it the arrow keys. A row is built once, from the item as it first appeared. A selected key is scrolled into view only by a `ScrollContainer` around the list or a `Flow` inside one. Rows move by up and down only, no dragging. With `move` on an `items()` the mod filters, `toIndex` is a place in what the list shows, so turn `move` off while a filter is active. |
| `MenuButton` | A label on the trigger, no icon. No submenus, no disabled rows. |
| `fillHeight` | Fills in a panel, a vertical `Flow` and a `Tabs` page. In a panel the filling element takes the room down to the viewport's bottom, so an element placed under it by `Column` is overlapped: put the two in a vertical `Flow`. |
| Secondary click and tint | `Button` and `Toggle` only. Other shipped elements take left clicks alone. A custom `PanelElement` handles any button in its own `mouseClicked`. |
| `ScrollContainer` | Vertical only. No keyboard scrolling. |
| `Slider` | A 0 to 1 fraction, whole numbers (`ofInts`) or an enum's constants (`ofEnum`). No range handle, no vertical orientation. |
| `WidgetHost` | A drag reaches the widget once a frame, not once per mouse event. A hosted widget draws itself, so it does not grey when disabled unless it greys itself. `overflow()` knows a widget's popup is open only by its keyboard focus. A hosted widget, like a control, stops being focusable a quarter second after it stops being drawn. |
| `ColorField` | No eyedropper and no list of recent colours. The dialog opens only on a screen. Hex takes six or eight digits, not the three-digit short form. |
| Keyboard focus on `Button`, `Toggle`, `Checkbox`, `Radio` | Follows the screen's widget order, which is panel order. An element that stops being drawn loses focusability a quarter second later, not the same frame. |
| Disabled `Tabs` | The strip and the body are inert and the body greys; the strip's labels keep their colours. |
| Item outline (`SlotRendering.drawItemOutline`, `ItemDisplay.outline`) | Items whose model reaches outside their 16 by 16 box, which vanilla draws through a separate path, draw without the outline. |
| Auto-sizing elements with supplier content | The build measures width once. Reserve width for the longest expected content. |

## Runtime constraints

- `build()` freezes a panel's element list, and every element is immutable after its own `build()`. Build a new panel to change the list, or show the changing part in a `ListView`.
- A `ScreenPanelAdapter` with no target renders on every container screen. An `AnchorPanelAdapter` or `VanillaScreenPanelAdapter` with no `.on(...)` fails at the first screen open with the panel id in the message.
- Adapters, anchors, HUD panels and notifications are declarations. Constructing, targeting or unregistering one after the client starts throws. Gate a panel at runtime with `visibleWhen`.
- A panel with no placement is rejected by adapters and the HUD. Only `MKScreen` and a library's own screen (`OwnHost`) place an unplaced panel.
- Panel ids are global across mods. Prefix them with the mod id.
- Only the `api` packages are public. Everything else is internal and may change in any release.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
