# Concepts

Definitions of the terms MenuKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term.

## Two artifacts

| Artifact | Environment | Contents |
|---|---|---|
| MenuKit (`menukit`) | Client and server (UI on the client) | Elements, panels, layout helpers, HUD panels, placement on vanilla screens, standalone screens |
| MenuKit: Containers (`menukit-containers`) | Client and server | Created slots, custom container menus, per-slot state, storage attachments |

Containers depends on MenuKit. MenuKit does not depend on Containers. A mod that depends on MenuKit alone cannot import a Containers type. The build fails.

## Public API

The API is the `api` packages: `com.trevlar.menukit.api.*` and `com.trevlar.menukit.containers.api.*`. [Versioning](versioning.md) covers them. Every other package carries `@ApiStatus.Internal` and is outside the contract, so a mod that calls into one can break in any release. Each artifact has a common half and a client half, and the compiler keeps screen code out of the common half. The `api` packages span both halves. A class that names a screen type is client-only, and its javadoc says so.

## Panel

A `Panel` is a bounded rectangle that holds an ordered list of elements. It renders, receives input, and shows or hides as one unit. It has an id, a `PanelStyle`, a padding, and a visibility.

Panels do not nest. A panel holds elements. It does not hold other panels.

Panel ids are global. Prefix every id with the mod id: `"mymod:controls"`.

## Element

A `PanelElement` is one item inside a panel. Its position is relative to the panel's content area. MenuKit ships these elements:

| Kind | Types |
|---|---|
| Render only | `TextLabel`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `InfoBox` |
| Interactive | `Button`, `Toggle`, `Checkbox`, `Radio` (with `RadioGroup`), `Slider`, `TextField`, `Dropdown`, `DropdownMulti`, `ItemPicker` |
| Composite | `ScrollContainer`, `Flow`, `Section`, `Tabs`, `ConfirmDialog`, `AlertDialog` |
| Slot (Containers) | `SlotElement`, `SlotFlowElement` |

A consumer implements `PanelElement` for a custom element, or extends `AbstractPanelElement`.

## Builders and one vocabulary

Every element is built by its builder and does not change after `build()`: `Button.builder()...build()`. The same idea has the same name on every builder:

| Method | Meaning |
|---|---|
| `at(x, y)` | Position in the panel's content area. |
| `size(w, h)` | Size, on the elements that take one. The rest size themselves from their content. |
| `visibleWhen(supplier)` | Shown while it holds, read every frame. |
| `disabledWhen(supplier)` | Greyed and inert while it holds. On a container it disables everything inside. |
| `tooltip(text)` | The hover tooltip, fixed or read every frame. |
| `state(get, set)` | The lens onto the consumer's value (see State). |
| `onClick(Runnable)` | What a press does. |
| `opaque(boolean)` | Whether the element is solid on a transparent panel. |
| `declId(id)` | A stable id for the element's window address. |

No builder picks a look. An element draws for the kind of screen it is on (see Screen kinds).

An instance keeps only what a container needs. That is `setChildPosition(x, y)`, which a `Flow`, `Row` or `Column` uses to place its children, and the layout calls a panel makes every pass. Anything that varies at runtime is read from a supplier.

## Mouse buttons

An element receives left clicks through its primary callback. `Button` and `Toggle` also accept `onSecondaryClick(Consumer<Click>)`, which receives every non-left button.

A `Click` record carries the button index and the shift, control, and alt state sampled when the click reaches the element. It answers `isRight()`, `isMiddle()`, `isShiftRight()`, and `isSecondary()`. On macOS the command key counts as control.

With no secondary handler attached, non-left clicks pass through to vanilla. A disabled control consumes neither kind.

## Keyboard, sound and narration

`Button`, `Toggle`, `Checkbox` and `Radio` behave as vanilla widgets do. A click plays vanilla's click sound. Tab moves keyboard focus onto them in screen order, and a focused control draws its hover look. Enter or Space presses the focused one. The narrator reads its label and, for the boolean controls, its on or off state. `Slider` and `TextField` are vanilla's own `AbstractSliderButton` and `EditBox`, exposed through `widget()`. An element that is not drawn (its panel hidden, its section closed, its tab not shown) cannot take focus.

While a modal panel is shown on a vanilla screen, a key goes to the panels' elements first. Escape then runs the modal's `onEscape`. Any other key then goes to the focused widget if MenuKit placed it (a dialog's text field, for instance). After that the key is dropped, so the screen under the modal never acts on it. Keys vanilla handles outside the screen still work: F11, the F3 combinations, the narrator hotkey and screenshots.

## Tint

`Button` and `Toggle` accept `tint(IntSupplier)`. The supplier runs each frame and returns an ARGB value. The tint fills the control inside its border, over the background and under the label. Returning 0 draws no tint. The tint shows consumer-owned state, such as a pinned mode, that the control itself does not store.

## State

A stateful element is only a lens onto the consumer's value. `state(get, set)` reads the value from `get` every frame and hands a change to `set`. The element stores nothing. A value changed elsewhere shows at once, and a setter that refuses a change shows the refusal on the next frame. Persistence belongs to the consumer. `Toggle`, `Checkbox`, `Slider`, `TextField`, `Dropdown`, `DropdownMulti` and `ItemPicker` require a `state`. A `Radio` reads its `RadioGroup.state(get, set)`.

Some state is the element's own view state, not the consumer's. Examples are whether a `Section` is open, a `ScrollContainer`'s scroll, a `Tabs` body's scroll, and whether a dropdown's popover is open. `Section` and `ScrollContainer` take a `state(get, set)` to hand theirs to the consumer. `Tabs` takes `bodyScroll(get, set)`, in pixels keyed by tab id. A screen that rebuilds itself (a new `MKScreen`, a new `Tabs`) hands these over, so it reopens as the player left it.

`Slider` and `Dropdown` take typed lenses for whole numbers and enums: `Slider.ofInts(min, max)`, `Slider.ofEnum(Mode.class)`, `Dropdown.ofInts(min, max)`, `Dropdown.ofEnum(Mode.class)`. Their `state` and `label` take `Integer` or the enum. `Slider.builder()` is the 0 to 1 fraction.

## Disabled

`disabledWhen` on an element greys it and makes it inert. `Panel.disabledWhen(supplier)` does the same for every element of the panel, and a container's own `disabledWhen` (a `Section`, `Tabs`, `ScrollContainer` or `Flow`) does it for everything inside. Text greys too. `TextLabel`, `InfoBox` and a section's header draw in the disabled grey, which is the screen kind's `TextRole.DISABLED` colour. A disabled panel still claims its area, so what is behind it stays inert. Its elements take no clicks, wheel or keys. Mouse releases still arrive, so a drag can end.

## Screen kinds

There are two kinds of screen, and every element draws for the kind it is on (since 6.1.0). There is one set of elements, and nothing else picks a look.

| Kind | Screens | Look |
|---|---|---|
| `ScreenKind.CONTAINER` | Inventories, chests, anything built on vanilla's `AbstractContainerScreen` | MenuKit's: raised grey controls on a light grey panel, dark text without a shadow |
| `ScreenKind.OPTIONS` | The screens reached from the pause menu and the title screen: Options, Video Settings, Controls, Create World | Vanilla's: stone buttons, vanilla's checkbox, tab and slot sprites, white text with a shadow, separators and the darker list band, over the blurred world or the panorama |

The kind comes from the screen, by one rule (`ScreenKind.of(screen)`):

- A panel injected into a vanilla screen. An `AbstractContainerScreen` is a container screen, and any other vanilla screen is an options screen. A panel on Controls draws in the options look with no code.
- A slot group's panels are on a container screen.
- A standalone `MKScreen` is the kind it declares when it is created, `super(title, ScreenKind.OPTIONS, panels)`. One that declares none is a container screen, which is the look every standalone screen had before 6.1.0. `screenKind()` reads it back. A `CustomContainerScreen` is a container screen.
- The HUD is a container screen, the look it has always had.
- A dialog or any other panel on a screen is that screen's kind, and so is the chooser an `ItemPicker` opens.

The kind travels with the frame in `RenderContext.kind()`, and `at` and `disabledIf` keep it. An element built from other elements passes it on without reading it. The kind changes how an element draws, never its size or behaviour.

Text takes its colour from a role: `TextRole.HEADING`, `BODY`, `MUTED` or `DISABLED`. `ScreenKind.textColor(role)` resolves it. On a container screen the colours are dark grey (`TextLabel.COLOR_DARK`) for headings and body, `ElementConstants.TEXT_MUTED` for muted text and the disabled grey for disabled text, all without a shadow (`textShadow()`). On an options screen they are vanilla's own white, `CommonColors.GRAY` and `CommonColors.LIGHT_GRAY`, with a shadow. A `TextLabel` is `BODY` unless `role(...)` sets another role. The labels of a checkbox, a radio button and a section's header follow the same table. `color(argb)` fixes a colour on every kind. Text on a control (a button's label) is white on both.

What each piece draws on an options screen, from vanilla's 26.2 sprites:

| Piece | On an options screen |
|---|---|
| A panel's background | `RAISED` is vanilla's popup frame (`popup/background`, the box of its own warning popups), `INSET` the list band, `DARK` unchanged, `NONE` nothing. A dialog reads as vanilla's own. |
| `Button`, `Dropdown`, `DropdownMulti` | `widget/button`, `button_highlighted` on hover, `button_disabled`; the popover on `button_disabled` |
| `Toggle` | A vanilla button whose text is the state: its label, or vanilla's `ON` / `OFF` without one |
| `Checkbox` | `widget/checkbox`, `checkbox_selected` and their `_highlighted` sprites, at MenuKit's 10 px box |
| `ItemPicker` | `container/slot`, the slot the Superflat layer list draws its blocks in |
| `Section` | The Controls screen's category header colours: the title white, the summary grey, read from the left as on a container screen |
| `Divider` | Vanilla's separator, `Screen.HEADER_SEPARATOR` (2 px) |
| `ScrollContainer` | The list band: `menu_list_background` behind the content, a separator directly outside it above and below, and vanilla's thin list scroller. The content is inset a little more than in vanilla's lists, `OPTIONS_INSET_X` (14) from the left and `OPTIONS_INSET_Y` (10) above and below. It is narrower by that much, as `viewportWidthFor(width, kind)` reports |
| `Tabs` (`WRAP`, `SIDE_SCROLL`) | Create World's tab bar: white labels, the selected one underlined and open into a page of the list background, the separator either side of the tabs. The page insets the body as the list band does, from both sides, and lays it out that much narrower |
| `Slider`, `TextField` | Vanilla's own widgets on both kinds |

Where vanilla has no counterpart (a radio button, an info box, a progress bar, a colour swatch, a consumer's sprite), the element keeps MenuKit's drawing on both kinds. While a world is open the separators and the list band use vanilla's in-world textures, as vanilla does.

## Item picker

An `ItemPicker` is a setting whose value is an item. It is a slot plus a lens onto an `Item` (`state(get, set)`), for a settings page where the player picks the items a setting applies to. A left click (or Enter on the focused picker) opens a chooser over the screen. The chooser has every item in a scrolling grid, a search field that narrows it by name or id, and Clear and Cancel buttons. A click on an item hands it to `set` and returns. Escape or Cancel returns with nothing changed. A right click empties the picker by handing `set` `Items.AIR`, which is also how `get` reports "none". On hover, it shows the item's name.

The value is a setting, never the player's item. The picker and its chooser are client-only. They never touch the inventory or a container and send nothing to the server, so the picker belongs to MenuKit, not Containers. It holds one item. A list of items is a row of pickers, one per position (see [recipes](recipes.md#a-settings-screen-in-the-pause-menu-look)).

## Input context

Every input method receives an `InputContext`, as `render` receives a `RenderContext`. It carries the content origin, the mouse, whether the element's surroundings are disabled, and the kind of screen. No element caches its origin or its hover state from the last frame. A container hands its children a context moved to their origin through `ChildDispatch`, the one dispatch every host and container uses. An open overlay (a dropdown's list) takes a click first, then the first child whose hit test contains the point.

## Structure does not change after build

`build()` freezes the panel's element list. No method adds an element to a built panel. Visibility, position, and supplier-driven content change at runtime. To change the element list, build a new panel.

A hidden element or panel is inert on every surface. It does not render, receive clicks or show a tooltip. It takes no room in a panel's layout or a `Flow`. `Row` and `Column` place once, so a hidden element there keeps its gap.

## Size

Width flows down from the panel to its elements. A label wraps to it and a slider caps to it. Height flows down only to an element whose `fillsHeight()` returns true. Through `fillHeight(int)`, that element gets the room from its top edge to the bottom of the panel's viewport. The viewport is the panel's pinned height or, for a screen's MAIN panel, the screen's height. With no viewport the element gets -1 and takes its natural height. No built-in element but `Tabs` fills height.

## Tabs

`Tabs` is a tab strip and the area below it that shows the selected tab's body. One element holds both, so the body's top edge follows the strip's height. Each tab has a string id, a label, a body, and an optional `visibleWhen`. A body is a list of elements positioned from the body's top-left. It is laid out to the body width the way a panel lays out its own elements, and scrolls when it is taller than the body area.

Selection is consumer state. `state(get, set)` reads the selected id every frame and writes the id the player picks. To select a tab from outside, the mod writes its own field. When the selected tab is hidden, the next visible tab is shown, or else the previous one. Nothing is written, so the hidden tab takes the selection back when it reappears.

`Tabs` has three modes. `WRAP` breaks the strip into the fewest rows the width allows and splits the tabs so the rows are about equally full. A tab keeps its row and place when another is selected. `SIDE_SCROLL` keeps one row. When it overflows, it scrolls from the left with arrows at both ends, and a newly selected tab slides into view. `SIDEBAR` puts the tabs in a column down the left with the body to its right. The selected tab reaches further left and opens into the body. The column is as wide as its widest label, at most a third of the element. When the tabs are taller than the column, a scrollbar runs down its left edge. It is a `ScrollContainer`'s scrollbar, with the same track and handle at the same size. Drag the handle, or click the track to jump there. A newly selected tab slides into view. The tabs narrow to make room for the bar, so the body does not move. Both strips scroll the way a `ScrollContainer` scrolls its content. They move by pixels, 10 per wheel notch, at once under the wheel and the handle. An arrow slides the row one tab along. A selected tab that is partly off the strip slides into view instead of jumping. `sidebarHeader(elements...)` puts elements above the column, such as a Back button, in the column's width. The body beside the column still starts at the top. On a container screen the sidebar is drawn in vanilla's tab colors. Resource packs that restyle vanilla's tabs change the top strip there but not the sidebar.

On an options screen (see Screen kinds), the strip of `WRAP` and `SIDE_SCROLL` is Create World's tab bar. Every label is white, and the selected one is underlined and open into the page below. The page is vanilla's list background down to a separator at the bottom. `SIDEBAR` there is the same tab bar turned on its side. Vanilla's tab sprites are turned a quarter and open toward the body on the right. The selected one opens into a page of the list background that runs from the column's edge to the element's right. Vanilla's separator runs down the column's edge above and below the tabs. Labels stay upright, white, the selected one underlined. Either page insets the body like the list band, `OPTIONS_INSET_X` from its sides and `OPTIONS_INSET_Y` above and below. Sizes, arrows and behaviour are the same on both kinds. The sidebar's scrollbar is vanilla's thin list scroller there, as a `ScrollContainer`'s is.

`header(elements...)` puts a row across the whole element, above the strip or the sidebar column and above the body, in every mode. It is a menu's own title bar. Its elements get the element's full width, so a `Flow` with a spacer at each end centres a label across it. The strip, the column (with its `sidebarHeader`) and the body start under it after a 4 pixel gap, and the body is that much shorter. The screen's title is separate. A screen whose header names it can call `hideTitle()` so the name shows once.

`align(...)` is `LEFT`, `CENTER`, `RIGHT`, or `FILL`, applied per row. In `SIDEBAR` it places each label in its tab, with `FILL` the same as `LEFT`, and labels stay put when the selection changes. `FILL` gives every tab in a row, the last row included, its label width plus an equal share of the leftover space. In `SIDE_SCROLL`, alignment applies only while the row fits. A label wider than its tab is cut short, ending in three dots, and shows in full on hover.

By default `Tabs` fills its panel's width and height. Put it in a screen's MAIN panel for a full-screen tabbed menu. `size(width, height)` fixes both, for a panel that has no height to give it. Each tab keeps its body's scroll position while the element exists. `bodyScroll(get, set)` hands it to the consumer in pixels from the top, keyed by tab id. That is the unit `ScrollContainer.state` uses. `get` is read every frame (null is the top), and `set` gets each change. A screen rebuilt on the same lens opens each body where it was, on the first frame, without sliding from the top. An offset past the body's end is clamped. The selected tab is already the consumer's through `state(get, set)`.

A body's scroll stays where the player put it. It is stored as pixels from the top. When the body changes height (a section opening or closing, a label rewrapping, a dropdown's label growing a line), what is on screen stays put. It moves only when the end of the body comes up past it. A dropdown's popover draws over the body and never changes its height. A click on a control never scrolls the body, and a body never slides. Only a strip slides. A body that stops scrolling because everything fits is at the top, and starts from the top when it next scrolls. A panel's own auto-scroll behaves the same.

Other mods can add tabs to a menu that has a name. The owner names it with `menu(id)` on the builder, and another mod calls `Tabs.addTo(id, tab)` at its init. An added tab's body is a factory, `body(() -> elements)`. It is built every time the owner builds the menu, against the adding mod's own config. The owner's builder order is the menu's order. An owner tab marked `standIn()` holds a place for a tab another mod may add. An added tab with the same id takes its place. With nothing to replace it, the stand-in shows with a dimmed label. Only the place carries over. The replacement keeps its own `visibleWhen`, or always shows, so an owner's switch that hides its stand-ins never hides the real tabs. Any other added tab places itself with `after(id)` or `before(id)`, or goes at the end. Added tabs that share a place are ordered by id, so the result does not depend on which mod loaded first. When two mods add the same id to one menu, the second `addTo` is refused. A tab added to a menu that no mod builds is never shown.

## Section

`Section.builder(title)` is a collapsible section. Its header row has an arrow, an optional color swatch, the title, and a grey summary read every frame. Clicking the header opens and closes it. Its children are positioned from the top-left of the content area under the header and laid out to the section's width. They get clicks, keys and overlays as they do directly on the panel. A closed section's children are not drawn and get no input except mouse releases.

Position the row after a section where it sits while the section is closed. When it opens, the panel's reflow pushes later rows down by the content's height and the scroll height follows, the same way a wrapped `Flow` pushes rows. Sections nest. Open state is the element's view state and starts closed. `open(true)` starts it open, and `state(get, set)` hands it to the consumer. A disabled section greys its header and everything in it. On an options screen the header takes the Controls screen's category header colours, with the title in white. It still reads from the left as on a container screen, in line with the content under it.

## Layout helpers

`Row` and `Column` compute positions once and return a `List<PanelElement>`. They do not exist at runtime. The positions are computed on the panel's first layout pass, when the game's font can measure text. A panel or dialog laid out with them can therefore be built at mod init. An element enters a layout built: `.add(Button.builder()...build())`, and the helper moves it into place. A custom element that can only be constructed once its position is known implements `ElementSpec`. `.build()` returns positioned elements that go into a panel with `.add(...)` or `.elements(...)`.

`Flow` is the runtime row. Its children flow left to right at the panel's width and wrap to new rows. `Flow.spacer()` takes whatever a row's other children leave of that width. A label, a spacer and a control make a settings row with the control pinned to the right edge at any window width, with no width written anywhere. A spacer at each end of a row centres what is between them. When the row wraps, the control drops under its label.

`Flow.builder().vertical()` is the runtime column. Children flow top to bottom and wrap into a new column to the right. It wraps at the frame band, which is the room from the Flow's top down to the bottom of the screen's frame beside its panel (a container screen's `topPos + imageHeight`). The host hands the band to the panel the way it hands it its width, and the panel hands it to its elements. A panel has a band when it is in a side region (`LEFT_ALIGN_*`, `RIGHT_ALIGN_*`) on a container screen, on a slot group, or beside a standalone screen's main panel. On a slot group the band still runs to the container screen's frame, not the group's box. Anywhere else the Flow is one column. The band is not where the panel scrolls. The panel still scrolls at the screen's edge. In a column, `gap(horizontal, vertical)` is the gap between columns and the gap between children, and a spacer takes no room. In either direction a hidden child takes no room, so the Flow reflows when one hides.

`Row.width(px)` declares the row's overall pixel budget. `.addSpacer()` adds a flexible gap that expands to fill whatever the other children and spacing leave over. One thing then pins to the row's left edge and another to its right (`Back .... Reset`). Several spacers split the leftover evenly, with the odd pixel going to the last one. `addSpacer()` without `width(px)` throws, since a spacer with nothing to expand into is a mistake in the layout.

## Placement

Where a panel sits is declared once, on the panel: `Panel.builder(id).position(PanelPosition...)`. Every host reads it. An adapter only says which screens the panel appears on.

| Placement | Where |
|---|---|
| `PanelPosition.main()` | A standalone screen's frame, centred. One per screen. |
| `PanelPosition.region(OutsideRegion)` | Outside the host's reference rectangle, along one of its edges. |
| `PanelPosition.screenAnchor(InsideRegion)` | On one of nine spots of the screen or game window, inset from the edges it touches. |
| `PanelPosition.center()` | Centred on the screen as an overlay, drawn on top. |
| `PanelPosition.pixel(Supplier<ScreenOrigin>)` | At an absolute origin re-evaluated each frame. Returning `null` skips the frame. It does not stack and does not wrap. |

Any placement takes two modifiers. `.priority(n)` orders panels that share a region (lower sits nearer the anchor edge) and is the z-order within a host (lower draws underneath). The default is 100. Ties go by the registering mod's id, then by registration order, so the order is the same on every launch. `.offset(dx, dy)` nudges one panel after it is placed, and its siblings stack on its un-nudged position.

A panel with no placement is unplaced. An adapter or the HUD rejects it at registration, naming the panel. `MKScreen` and a custom menu's screen give it a default instead. The first unplaced panel becomes `main()`, and each later one stacks below it.

A panel wraps its width to the space its placement leaves and scrolls its height when taller than its room. `Panel.size(w, h)`, `pinnedWidth(w)`, and `pinnedHeight(h)` override this.

On a standalone screen, the `main()` panel makes room for the `region(...)` panels around it. Its room leaves out the height of the panels above and below it and the width of the panels beside it, gaps included. A panel above a full-height menu still fits, and the menu gets a little shorter. A main panel with room to spare stays centred.

## Reference

A reference is the rectangle a `region(...)` placement is measured against. It is not the panel and has no relation to the panel's size. The host decides it. It is a container screen's frame (extended by its chrome, such as creative's tab rows or an open recipe book), one slot group's bounding box, or a standalone screen's main panel. `Reference` is the record that carries it. A region never names its reference.

## Region

A region names where a panel sits relative to its reference. There are two enums, one for each side of the reference's edge:

| Type | Placement | Values |
|---|---|---|
| `OutsideRegion` | Outside the reference, along one of its edges | `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`, `CENTER` |
| `InsideRegion` | On the screen, at one of nine spots | `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `LEFT_CENTER`, `RIGHT_CENTER`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`, `CENTER` |

Panels in the same region stack in priority order. `InsideRegion` spots resolve through one resolver with each context's inset. On the HUD and on vanilla screens the inset is 4 pixels, and `CENTER` on the HUD starts directly below the crosshair. On a standalone screen the inset is the safe-area margin for the screen's chrome. The chrome is always placed, even when it is oversized.

## Five contexts, one host each

A context answers one question: what is this panel placed against? Each context is one `PanelHost`. Every host sorts its panels, places them, draws them in layers and routes their input the same way.

| Context | Placed against | Entry type | Placements | Input |
|---|---|---|---|---|
| Container screen | The menu frame | `ScreenPanelAdapter` | region, screenAnchor, center, pixel | Yes |
| Other vanilla screen | The screen | `VanillaScreenPanelAdapter` | screenAnchor, center, pixel | Yes |
| Slot group | One slot group's box | `SlotGroupPanelAdapter` | region, center, pixel | Yes |
| HUD | The game window during play | `HudPanel` | screenAnchor (`.region(...)`), pixel | No |
| Standalone | A screen the consumer opens | `MKScreen` (subclass), `CustomContainerScreen` | main, region, screenAnchor, center, pixel | Yes |

A placement a context cannot resolve is rejected at registration. HUD panels do not receive input. For a clickable HUD control, open a standalone screen from a key binding.

Every surface draws in the same order across all its hosts. FLOW panels come first, then POPOVER panels, then a dim when any shown panel dims behind it. OVERLAY panels (those that `center()`, dim behind, or track as modal) draw on top. Within a layer, a lower host draws before an upper one: a standalone screen's own host, then the screen's adapters, then the slot groups. Within a host the sorted order holds. An element renders the same in every context.

A popover, `Panel.builder(id).popover()`, is a panel that pops up over the screen. It draws above every ordinary panel of every host, whichever registered first, and below a modal and its dim. Its placement is still its own `position(...)`. On a container screen, popovers draw after the ordinary panels and before created slots, so a popover's own slots draw on it. Another panel's created slot under a popover still draws over it.

## Claims

What a panel covers is inert. A point on the screen is claimed by the topmost panel that takes it. Topmost follows the draw order above (OVERLAY, then POPOVER, then FLOW), so what is drawn on top takes the point:

- An opaque panel (the default) takes every point of its rectangle. Nothing inside it lets a point through to what is behind the panel.
- A see-through panel, `opaque(false)`, takes only its solid elements, meaning the shown, opaque and interactive ones, such as a button. A label on it takes nothing.
- An element's open overlay, such as a `Dropdown`'s list, takes its area whatever its panel's opacity.
- A shown modal (`tracksAsModal`) takes every point on its screen.

Under a claim, vanilla gets nothing. There is no slot highlight, click or tooltip, no widget or list hover, and no creative tab. The claim routes the point to the claiming panel instead. A panel's own slots stay live under its own claim (a created slot it presents, or a standalone screen's own slot group). A standalone screen's own widgets stay live under its own panels too. Every surface runs the same check, so a slot-group panel and a standalone screen's panels block what they cover exactly as an adapter's panel does.

Tooltips go through one MenuKit check before vanilla queues them. To hide every tooltip in the game for a time, register a condition once from client init with `MKTooltip.hideWhen(() -> ...)`. While any registered condition is true, no tooltip draws, a panel's own element tooltips included. Inventory Plus's "hold Ctrl to hide tooltips" setting uses it.

The same check places every tooltip, so a mod can scroll one too. `MKTooltip.onWheel((horizontal, vertical) -> ...)` receives the mouse wheel whenever it turns over a shown tooltip. It runs before any other handler, whether or not something then uses the event. `MKTooltip.scrollBy(dx, dy)` moves the tooltip on screen by that many pixels (positive y moves it down). MenuKit keeps a scrolled tooltip inside its own extent, so one that fits on screen never moves. It resets the offset when the tooltip's text changes or the tooltip goes away, and `MKTooltip.resetScroll()` resets it by hand. While a tooltip is scrolled with its top above the screen, its first line stays pinned at the top edge and the rest scrolls under it. Inventory Plus's "scroll long tooltips" setting uses it.

Two more switches on the same check keep a tooltip on screen. Both are off unless a mod registers them from client init. `MKTooltip.wrapWhen(() -> ...)` breaks any text line wider than the screen (less a 4 pixel margin and the frame's padding) onto more lines with vanilla's splitter, and keeps the tooltip inside both sides. Images such as a bundle's grid are left as they are. A wrapped first line stays one title, so the scroll pin keeps all of its lines. `MKTooltip.clampWhen(() -> ...)` keeps the tooltip's top on screen. A tooltip taller than the screen starts at its title and runs off the bottom, and `MKTooltip.scrollBy` with a negative y brings up the rest, down to its last line. Inventory Plus's "keep tooltips on screen" setting uses them.

Vanilla puts a tooltip beside the cursor, so a wide one near an inventory lands over the slots the player is reading. `MKTooltip.avoidCoverWhen(() -> ...)` docks it instead, and is also off unless a mod registers it. While it holds, a tooltip vanilla would place beside the cursor sits centred at the bottom of the screen, 4 pixels (`MKTooltip.EDGE`) above the edge. When the cursor is inside where that tooltip would sit, it sits centred at the top instead. Nothing wraps or changes side. `clampWhen`, `wrapWhen` and the scroll work on a docked tooltip as on any other, so one taller than the screen starts at its title under `clampWhen`. A tooltip vanilla anchors to a widget for the keyboard stays where vanilla puts it. Inventory Plus's "keep long tooltips out of the way" setting uses it.

## Targeting

A `ScreenPanelAdapter` with no target renders on every container screen. `.on(Class...)` limits it to those screen classes and their subclasses. `.onAny()` states the default explicitly. `.onPlayerInventory()` limits it to the player inventory screen in both game modes.

A `VanillaScreenPanelAdapter` requires `.on(Class...)` or `.onAny()`.

A `SlotGroupPanelAdapter` requires a target. `.on(SlotGroupCategory...)` renders once per category that resolves in the open menu. `.onGroup(SlotGroupId...)` renders once per named created group. `ContainerPanel.groupId(panelId, groupId)` and `CreatedSlots.groupId(panelId, groupId)` return the id. Categories cover every vanilla menu. A mod with its own menu registers a `SlotGroupResolver` for it.

A panel anchored to a slot group is measured from that one group, not from every slot sharing its category.

Adapters and HUD panels are declarations. Declare them from the mod's initializer. Constructing, targeting or unregistering one after the client starts throws, the same as every other MenuKit declaration. A panel that comes and goes at runtime keeps its adapter and gates itself with `visibleWhen(...)`. A hidden panel is not measured, takes no room in its region and claims nothing.

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
| `SlotGroups.of(slotRef)` | the group one slot is in, as a veto sees it; `null` when nothing is known |

A category cannot tell a mod's pockets from the main inventory when both declare `PLAYER_INVENTORY`, but a group can. A mod that splits one thing into many groups, one per anchor, puts them in one `SlotGroupSet` with `SlotGroups.declare(id, category, set)`. The listing then shows the set as one row. A choice saved under a set's key reaches every group in it.

Groups and sets are named like operations. `SlotGroups.name(id)` and `name(set)` are translatable on `slot_group.<...>` and `slot_group_set.<namespace>.<path>`. MenuKit ships vanilla's names, and a mod ships its own. `SlotGroupId.asString()` and `SlotGroupSet.asString()` are stable text for a config file, with `parse` and a `CODEC` each. A row's `source()` names the mod it comes from, for a screen to show as "Pockets (Inventory Max)". It is `null` for vanilla's groups. Otherwise it is the display name of the mod whose namespace the row is named in (a set's namespace, a created group's panel id before the colon, a mod's own category). With no loaded mod of that id it is the namespace itself. `SlotGroups.source(id)` answers the same for a group on a live menu.

`SlotGroups.of(slotRef)` names the group of the slot a `SlotRef` describes. For a created slot it is the slot's own group, never its category's, so a veto can tell a pocket from the main inventory. For a vanilla slot it is the slot's group on its menu. For a player-inventory slot with no menu, as in a world pickup, it is the vanilla group its index sits in. A created slot needs Containers installed to be told apart.

A container-panel group registers at init and is listed from the title screen. A group built with a menu (`CreatedSlots.onto`) is listed after the first menu that carries it. To list it from the title screen, its mod declares it at init with `SlotGroups.declare`.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search, including searches run by mods with no knowledge of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Operation

An operation is something done to a slot. Vanilla ships eleven, one key per thing a player can do: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT` (double-click), `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `INVENTORY_INSERT`. The last covers every item given to the inventory, not only a pickup from the ground. `/give`, pick block and a crafting grid's returns go through the same two vanilla methods. The eleven are `BehaviorKeys.VANILLA_OPERATIONS`, every one on by default. MenuKit enforces them at vanilla's own seams for every slot kind. A plain click takes, puts, or both when it swaps different items. A swap key has two slots, and both must allow it. The client does not send a click its own slot does not allow, so that part holds on any server. Q, Ctrl-Q and F while playing, with no screen open, are the same `DROP`, `DROP_STACK` and `OFFHAND_SWAP` on the selected hotbar slot. The client refuses them before it drops or sends, and the server refuses them when the action arrives. An offhand swap also needs the offhand slot to allow it.

An operation has a role: it takes items out of a slot, puts items in, or both. `SlotOperations.define(op, Role.TAKE)` records it and `SlotOperations.role(op)` reads it. A settings screen uses the role to tell which operations matter for a lock on an item already in a slot. `define(op)` without a role means both.

An operation also declares which slot groups it can act on at all, as in `SlotOperations.define(op, role, AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_INVENTORY))`. It names vanilla's groups. Every group a mod adds applies automatically, unless the operation opts out with `.except(groupId)` or `.except(slotGroupSet)`. `AppliesTo.vanillaExcept(...)` names the vanilla groups it skips instead. An operation defined without an `AppliesTo` gets `AppliesTo.EVERY_GROUP`, so nothing defined earlier changes. `SlotOperations.appliesTo(op, groupId)` answers for one group. `SlotOperations.groups(op)` lists every declared group the operation applies to, which is what a reach setting offers. MenuKit declares vanilla's eleven from what vanilla does. Nothing is placed, dragged or shift-clicked into an output slot. The crafter's result slot can't be touched by anything. Double-click collect skips the crafting, smithing, stonecutter, cartography and trade results, whose menus refuse it. An inventory insert lands only in the hotbar, the main inventory and the offhand. This is a declaration, not a rule MenuKit enforces. It tells a settings screen where an operation can reach.

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, ships a name and a description in its lang file, and asks `SlotOperations.allows` in its own code before acting. `SlotOperations.all()` lists every published operation. `SlotOperations.name(op)` and `description(op)` are translatable components on `slot_operation.<namespace>.<path>` and `.description`, for a settings screen to list. MenuKit needs no change for a new operation to exist.

Two things decide what a slot allows. The first is the cascade, which holds what the slot's author declared:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. `SlotOperations.inherent(category, operation, value)` sets the category rung once. A group's own declaration outranks it. A per-slot declaration outranks the group. A vanilla slot has one group per category on a menu, so for vanilla slots the group and the category are one rung. "Shift-click may land in the 9 by 3 but not the hotbar" is `inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)`.

The second is a veto. `SlotOperations.veto(rule)` registers a rule that can only say no, for a mod that is not the slot's author. A player's lock is the example it exists for. A veto sits beside the cascade and subtracts. It never overwrites what the slot's author declared, and it has nothing to restore when the lock lifts. A veto that throws is logged once and skipped.

`SlotOperations.allows(menu, slot, player, operation)` is the one check. It passes when the cascade says yes and no veto says no. Every seam calls it, and every operation a mod adds calls it too. The `SlotRef` a veto sees carries the container and index, the live slot and menu when there is one, the acting player when there is one, and the slot's category. A move a mod makes on the server with no click behind it, such as a direct `quickMoveStack` call, still names a player. That player is the one whose inventory the menu shows.

A mod that performs its operation by sending clicks wraps them in `SlotOperations.as(take, put, () -> sendClicks())`, or in `as(op, ...)` when both sides are one operation. Each click sent inside carries the operation it serves. The first operation applies to the slot it takes from and the second to the slot it puts into. The vetoes judge that operation instead of the gesture. A lock that refuses shift-click but allows restock therefore lets the restock's shift-click through. The slot's author still judges the gesture. The tag reaches the integrated server. Clicks must be sent inside the block, on the calling thread. A simulated click that is not wrapped counts as the gesture it looks like.

Registration order does not matter. Declarations freeze once every mod has initialised. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)` and `dragFill(false)`.

A gate is the slot author's other rule. It sets what the slot accepts and releases, and how many of an item it holds. It is a `SlotGate` on `BehaviorKeys.GATING`, set with `Window.slot(address).gate(...)` or `SlotSpec.gate(...)`. `BehaviorKeys.BINDING` adds Curse of Binding to any slot. MenuKit enforces both at the slot's own `mayPlace`, `mayPickup` and `getMaxStackSize`, which every click, shift-click and automation path consults. It also enforces them in the merge pass of a shift-click and at the hopper and dispenser seams, once per vanilla method. A gate is not a lock. A lock is another mod's policy laid over a slot, and that is a veto. It only says no and leaves the author's gate alone.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu through Containers. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner, which is a player, block entity, entity, or item stack.

`ContainerPanel` creates slots on the player's inventory menu and projects them onto every container screen. `CustomContainerMenu` creates slots on a custom menu. `CreatedSlot.of(slot)` tells a created slot from any other, on any screen, creative's included.

Containers has the same two halves as MenuKit, and the compiler enforces the split. The code that builds slots, stores state and judges writes is common, and runs on the server and the client alike. The code that draws is client-only: `SlotElement`, `CustomContainerScreen`, and the client halves `ClientMenu`, `ClientContainerPanel` and `ClientSlots`. A custom menu's handler holds each panel's id, its slot groups and whether it is shown. How a panel looks is a MenuKit `Panel`, built by the client screen from `ClientMenu.of(menu).panel(id, ...)`. The server decides whether a panel is shown and syncs that to the client. A player may toggle only a panel declared `toggleable()`, and a client may open a menu only when its `validWhen` holds, which is also how long it stays open.

A server with Containers requires it on every client. Before the world loads, it checks that a joining client has Containers. It disconnects a client that does not, with a message naming MenuKit and MenuKit: Containers and linking their pages. A client with Containers on a server without it builds no created slots and sends nothing Containers-only, instead of desyncing.

## Slot rendering

Vanilla draws every slot. MenuKit runs no slot pass of its own on container screens. A created slot's panel writes the position into vanilla's `Slot.x` and `Slot.y` before vanilla's slot pass. Vanilla then draws the item, count, hover highlight, and ghost icon for created and vanilla slots alike.

One consequence reaches mods that do not depend on MenuKit. A mod that decorates slots through a mixin on vanilla's slot draw call marks created slots too. It needs no dependency on MenuKit and no interface to adopt. Inventory Plus is the worked example. Its lock icons and item marks appear on Inventory Max's pockets and equipment slots, with no code in either mod for that case.

A panel hides exactly what it covers. Flow panels composite between the vanilla slots and the created slots, inside vanilla's slot pass. A panel over part of a vanilla slot hides that part and no more. A created slot's item still draws over the chrome of the panel hosting it. Overlay panels draw after the slot pass, on top of it. An overlay panel is one that centers, dims behind, or tracks as modal.

`SlotElement` positions a created slot and draws its frame. It does not draw the item.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `Address.createdSlot(group, index)` names a created slot, with the group from `CreatedSlots.groupId(panelId, groupId)` or `ContainerPanel.groupId(...)`. `Window.slot(address).set(key, value)` attaches behaviour by address. A veto reads a slot's address from its `SlotRef` with `address()`. `asString()`, `Address.parse` and `Address.CODEC` save one as text. Never save its parts.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `SlotState.register(id, codec, streamCodec, defaultValue, visibility, canWrite)` creates one at common init. Read and write it by address (`get(address)`, `set(address, value)`), or by the live `Slot` when one is at hand (`get(slot)`, `set(slot, value)`, and the forms that take a player). `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`. A value equal to the default is no entry at all, and reading never creates one.

The server judges every slot-state write a client sends with these checks, in order:

1. The channel exists.
2. The value parses with the channel's `StreamCodec`. What is stored is the channel's own re-encoding of it.
3. The write names the player's open menu and an active slot on it.
4. The channel's `canWrite(player, slot)` allows it.
5. The player is within a write rate.

A `SHARED` channel's rule refuses every client by default, so a shared channel names who may write it. A refused write changes nothing, and the writer's client gets the server's value back. Values travel as the channel's `StreamCodec` bytes, and a client that cannot decode one skips it.

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. `EphemeralStorage.of(size)` builds one that lasts for the menu session. Containers also provides block-scoped and item-scoped attachments.

## One styling change

MenuKit draws a pressed look on every vanilla button and every YACL controller while it is held down, including buttons MenuKit did not create. It is always on and changes nothing but the drawing. It is the one change MenuKit makes to how vanilla looks. Otherwise the library leaves vanilla's appearance and behaviour to the mods that use it.
