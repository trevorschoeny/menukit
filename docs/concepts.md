# Concepts

Definitions of the terms MenuKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term.

## MenuKit and SlotKit

MenuKit is one mod, `menukit`. It runs on the client and the server: the UI draws on the client, and the Kits built on MenuKit use its placement types and its declarations freeze on the server too. It holds no slot code. Slot groups, slot operations, created slots, custom menus and per-slot state are SlotKit's (`slotkit`), a separate library that depends on MenuKit. MenuKit does not depend on SlotKit, so a mod that depends on MenuKit alone cannot import a SlotKit type. The build fails. SlotKit's terms are on [SlotKit's concepts page](https://github.com/trevorschoeny/slotkit/blob/main/docs/concepts.md). A mod moving from MenuKit 6 follows [upgrading-7.md](upgrading-7.md).

## Public API

The API is the `api` packages: `com.trevlar.menukit.api.*`. [Versioning](versioning.md) covers them. Every other package carries `@ApiStatus.Internal` and is outside the contract, so a mod that calls into one can break in any release. MenuKit has a common half and a client half, and the compiler keeps screen code out of the common half. The `api` packages span both halves. A class that names a screen type is client-only, and its javadoc says so.

## Panel

A `Panel` is a bounded rectangle that holds an ordered list of elements. It renders, receives input, and shows or hides as one unit. It has an id, a `PanelStyle`, a padding, and a visibility.

Panels do not nest. A panel holds elements. It does not hold other panels.

Panel ids are global. Prefix every id with the mod id: `"mymod:controls"`.

## Element

A `PanelElement` is one item inside a panel. Its position is relative to the panel's content area. MenuKit ships these elements:

| Kind | Types |
|---|---|
| Render only | `TextLabel`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `InfoBox` |
| Interactive | `Button`, `Toggle`, `Checkbox`, `Radio` (with `RadioGroup`), `Slider`, `TextField`, `Dropdown`, `DropdownMulti`, `ItemPicker`, `ColorField` |
| Composite | `ScrollContainer`, `Flow`, `Section`, `Tabs`, `ListView`, `ConfirmDialog`, `AlertDialog`, `EditDialog` |
| Host | `WidgetHost`, for a vanilla-style widget made outside MenuKit |
| Slot (SlotKit) | `SlotElement`, `SlotFlowElement`, in SlotKit |

A consumer implements `PanelElement` for a custom element, or extends `AbstractPanelElement`.

## Builders and one vocabulary

Every element is built by its builder and does not change after `build()`: `Button.builder()...build()`. The same idea has the same name on every builder:

| Method | Meaning |
|---|---|
| `at(x, y)` | Position in the panel's content area. |
| `size(w, h)` | Size, on the elements that take one. The rest size themselves from their content. |
| `visibleWhen(supplier)` | Shown while it holds, read every frame. |
| `disabledWhen(supplier)` | Greyed and inert while it holds. On a container it disables everything inside. |
| `tooltip(text)` | The hover tooltip, fixed or read every frame. A `\n` in the text starts a new line. `tooltip(first, more...)` takes several lines, each in its own style. |
| `state(get, set)` | The lens onto the consumer's value (see State). |
| `onClick(Runnable)` | What a press does. |
| `opaque(boolean)` | Whether the element is solid on a transparent panel. |
| `declId(id)` | A stable id for the element's window address. |

No builder picks a look. An element draws in the look of the screen it is on (see Looks).

An instance keeps only what a container needs. That is `setChildPosition(x, y)`, which a `Flow`, `Row` or `Column` uses to place its children, and the layout calls a panel makes every pass. Anything that varies at runtime is read from a supplier.

## Mouse buttons

An element receives left clicks through its primary callback. `Button` and `Toggle` also accept `onSecondaryClick(Consumer<Click>)`, which receives every non-left button.

A `Click` record carries the button index and the shift, control, and alt state sampled when the click reaches the element. It answers `isRight()`, `isMiddle()`, `isShiftRight()`, and `isSecondary()`. On macOS the command key counts as control.

With no secondary handler attached, non-left clicks pass through to vanilla. A disabled control consumes neither kind.

## Keyboard, sound and narration

`Button`, `Toggle`, `Checkbox` and `Radio` behave as vanilla widgets do. A click plays vanilla's click sound. Tab moves keyboard focus onto them in screen order, and a focused control draws its hover look. Enter or Space presses the focused one. The narrator reads its label and, for the boolean controls, its on or off state. `Slider` and `TextField` are vanilla's own `AbstractSliderButton` and `EditBox`, exposed through `widget()` and hosted by a `WidgetHost` (see Widget host). An element that is not drawn (its panel hidden, its section closed, its tab not shown) cannot take focus.

While a modal panel is shown on a vanilla screen, a key goes to the panels' elements first. Escape then runs the modal's `onEscape`. Any other key then goes to the focused widget if MenuKit placed it (a dialog's text field, for instance). After that the key is dropped, so the screen under the modal never acts on it. Keys vanilla handles outside the screen still work: F11, the F3 combinations, the narrator hotkey and screenshots.

## Tint

`Button` and `Toggle` accept `tint(IntSupplier)`. The supplier runs each frame and returns an ARGB value. The tint fills the control inside its border, over the background and under the label. Returning 0 draws no tint. The tint shows consumer-owned state, such as a pinned mode, that the control itself does not store.

## State

A stateful element is only a lens onto the consumer's value. `state(get, set)` reads the value from `get` every frame and hands a change to `set`. The element stores nothing. A value changed elsewhere shows at once, and a setter that refuses a change shows the refusal on the next frame. Persistence belongs to the consumer. `Toggle`, `Checkbox`, `Slider`, `TextField`, `Dropdown`, `DropdownMulti`, `ItemPicker` and `ColorField` require a `state`. A `Radio` reads its `RadioGroup.state(get, set)`.

Some state is the element's own view state, not the consumer's. Examples are whether a `Section` is open, a `ScrollContainer`'s scroll, a `Tabs` body's scroll, and whether a dropdown's popover is open. `Section` and `ScrollContainer` take a `state(get, set)` to hand theirs to the consumer. `Tabs` takes `bodyScroll(get, set)`, in pixels keyed by tab id. A screen that rebuilds itself (a new `MKScreen`, a new `Tabs`) hands these over, so it reopens as the player left it.

`Slider` and `Dropdown` take typed lenses for whole numbers and enums: `Slider.ofInts(min, max)`, `Slider.ofEnum(Mode.class)`, `Dropdown.ofInts(min, max)`, `Dropdown.ofEnum(Mode.class)`. Their `state` and `label` take `Integer` or the enum. `Slider.builder()` is the 0 to 1 fraction.

## Disabled

`disabledWhen` on an element greys it and makes it inert. `Panel.disabledWhen(supplier)` does the same for every element of the panel, and a container's own `disabledWhen` (a `Section`, `Tabs`, `ScrollContainer` or `Flow`) does it for everything inside. Text greys too. `TextLabel`, `InfoBox` and a section's header draw in the disabled grey, which is the screen kind's `TextRole.DISABLED` colour. A disabled panel still claims its area, so what is behind it stays inert. Its elements take no clicks, wheel or keys. Mouse releases still arrive, so a drag can end.

## Looks

There are two looks, the Panel look and the Default look, and every element draws in the look of the screen it is on (since 6.1.0). There is one set of elements, and nothing else picks a look. Before 7.3.0 the two were `ScreenKind.CONTAINER` and `ScreenKind.OPTIONS`; those names still work, deprecated, until 8.0.0.

| Look | Screens | How it draws |
|---|---|---|
| Panel, `ScreenKind.PANEL` | Inventories, chests, anything built on vanilla's `AbstractContainerScreen` | MenuKit's: raised grey controls on a light grey panel, dark text without a shadow |
| Default, `ScreenKind.DEFAULT` | The screens reached from the pause menu and the title screen: Options, Video Settings, Controls, Create World | Vanilla's: stone buttons, vanilla's checkbox, tab and slot sprites, white text with a shadow, separators and the darker list band, over the blurred world or the panorama |

The look comes from the screen, by one rule (`ScreenKind.of(screen)`):

- A panel injected into a vanilla screen. An `AbstractContainerScreen` has the Panel look, and any other vanilla screen the Default look. A panel on Controls draws in the Default look with no code.
- An anchor's panels are in the look of the screen the anchor is on.
- A standalone `MKScreen` is in the look it declares when it is created, `super(title, ScreenKind.DEFAULT, panels)`. One that declares none has the Panel look, which every standalone screen had before 6.1.0. `screenKind()` reads it back. A library's own screen declares its look to its `OwnHost`.
- The HUD has the Panel look, the look it has always had.
- A dialog or any other panel on a screen is in that screen's look, and so is the dialog an `ItemPicker` opens.

The kind travels with the frame in `RenderContext.kind()`, and `at` and `disabledIf` keep it. An element built from other elements passes it on without reading it. The kind changes how an element draws, never its size or behaviour.

Text takes its colour from a role: `TextRole.HEADING`, `BODY`, `MUTED` or `DISABLED`. `ScreenKind.textColor(role)` resolves it. In the Panel look the colours are dark grey (`TextLabel.COLOR_DARK`) for headings and body, `ElementConstants.TEXT_MUTED` for muted text and the disabled grey for disabled text, all without a shadow (`textShadow()`). In the Default look they are vanilla's own white, `CommonColors.GRAY` and `CommonColors.LIGHT_GRAY`, with a shadow. A `TextLabel` is `BODY` unless `role(...)` sets another role. The labels of a checkbox, a radio button and a section's header follow the same table. `color(argb)` fixes a colour in both looks. Text on a control (a button's label) is white on both.

What each piece draws in the Default look, from vanilla's 26.2 sprites:

| Piece | In the Default look |
|---|---|
| A panel's background | `RAISED` is vanilla's popup frame (`popup/background`, the box of its own warning popups), `INSET` the list band, `DARK` unchanged, `NONE` nothing. A dialog reads as vanilla's own. |
| `Button`, `Dropdown`, `DropdownMulti` | `widget/button`, `button_highlighted` on hover, `button_disabled`; the popover on `button_disabled` |
| `Toggle` | A vanilla button whose text is the state: its label, or vanilla's `ON` / `OFF` without one |
| `Checkbox` | `widget/checkbox`, `checkbox_selected` and their `_highlighted` sprites, at MenuKit's 10 px box |
| `ItemPicker` | `container/slot`, the slot the Superflat layer list draws its blocks in |
| `Section` | The Controls screen's category header colours: the title white, the summary grey, read from the left as on a container screen |
| `Divider` | Vanilla's separator, `Screen.HEADER_SEPARATOR` (2 px) |
| `ScrollContainer` | The list band: `menu_list_background` behind the content, a separator directly outside it above and below, and vanilla's thin list scroller. The content is inset a little more than in vanilla's lists, `DEFAULT_INSET_X` (14) from the left and `DEFAULT_INSET_Y` (10) above and below. It is narrower by that much, as `viewportWidthFor(width, kind)` reports. In a dialog (a modal panel) the inset is the dialog's own gap, `DIALOG_INSET` (4), on every side it insets, because the frame and padding already make the margin |
| `Tabs` (`WRAP`, `SIDE_SCROLL`) | Create World's tab bar: white labels, the selected one underlined and open into a page of the list background, the separator either side of the tabs. The page insets the body as the list band does, from both sides, and lays it out that much narrower |
| `Slider`, `TextField` | Vanilla's own widgets in both looks |
| `ColorField` | Its swatch is a vanilla button with the colour inside. Its dialog is a dialog of the screen, so its tabs, buttons and hex field draw for the kind |
| `WidgetHost` | Whatever the hosted widget draws. It draws itself, the same in both looks |

Where vanilla has no counterpart (a radio button, an info box, a progress bar, a consumer's sprite), the element keeps MenuKit's drawing in both looks. While a world is open the separators and the list band use vanilla's in-world textures, as vanilla does.

## Item picker

An `ItemPicker` is a setting whose value is an item or a list of items. On the page it is one slot. For a list it is a wrapping row of slots in order, `columns(n)` to a row (9 by default), ending in an empty slot marked with a plus. Each filled slot has a small remove button over its top-right corner, a few pixels showing a minus, with the tooltip "Remove". A click on it, or a right click anywhere on the slot, empties the slot. In a list that drops the slot, and the items after it move up one place. A left click anywhere else on a slot (or Enter on the focused picker) opens a dialog at that slot. On hover a slot shows the item's own tooltip, followed by how the setting compares it.

The value is always whole stacks, read from `get` every frame and handed to `set` on every change:

| Lens | Value |
|---|---|
| `state(Supplier<Item>, Consumer<Item>)` | One item, the kind only. `Items.AIR` is none. The 6.1.0 lens, unchanged. |
| `stack(get, set)` | One `ItemStack`. `ItemStack.EMPTY` is none. |
| `stacks(get, set)` | A `List<ItemStack>` in the player's order, never with empty entries. |
| `rule(get, set)`, `rules(get, set)` | The same as `ItemMatch` values, each a stack and its own match mode. |

`match(ItemMatch.Match)` declares how the setting compares stacks. `ItemMatch` does the comparing, so a mod never writes its own. Call `rule.matches(stack)`, or `ItemMatch.matches(config.stack, ItemMatch.Match.EXACT, stack)` for a plain stack.

- `ITEM`, the default: the kind of item only. Count and components are ignored. The dialog has no Edit tab, and every slot holds one plain item.
- `EXACT`: vanilla's own stack equality, the same item, count and components. A rule with nothing set is a plain item, so an enchanted or renamed one does not match.
- `CONTAINS`: the same item and at least the components the rule sets. The count is ignored. A rule with nothing set matches any variant. Components compare as vanilla's item predicates compare them: enchantments must be there at their level (more may be), custom data must contain the rule's tag, a component the rule removes must be absent, anything else must be equal.

A rule can leave chosen component types out of `EXACT` and `CONTAINS`: `rule.ignoring(DataComponents.DAMAGE)` still matches the item once it is worn, since neither side's damage is looked at. The count is not a component, so `rule.ignoringCount()` leaves it out of `EXACT` (the other modes never compare it). The picker's `ignoring(...)` and `ignoringCount()` declare the same once: every rule it writes carries them, and with `ignoringCount()` the Edit tab has no count. A `stack` or `stacks` lens stores plain stacks, so test those with a rule built the same way, `new ItemMatch(stack, EXACT).ignoring(DataComponents.DAMAGE)`. `ItemMatch.CODEC` saves what a rule ignores, and a rule saved without it loads ignoring nothing.

The mode decides whether an empty rule means "any" or "plain", so MenuKit never guesses. `playerChoosesMatch()` lets the player choose per slot between `Any <item>` (`CONTAINS`) and "Exactly this" (`EXACT`). It needs `rule` or `rules`, which store the choice, and `match` sets the choice for a new slot. `ItemMatch.CODEC` saves a rule with vanilla's stack codec.

The dialog is a modal dialog of the screen, in its kind. It is laid out as every dialog is, and is as wide and as tall as the tab it shows. At the top is the slot row, the setting's slots with the selected one outlined. A click on a slot selects it, and it stays selected until another is clicked. Each filled slot has the same remove button as on the page. It empties the slot, and in a list it also drops the slot, so the slots after it move left. The Search tab comes first. It has a search box, focused so typing starts at once, over a grid of every item from the creative inventory's search tab. Each potion, tipped arrow, enchanted book level, goat horn, firework duration and ominous bottle is its own entry (with `ITEM`, each item appears once). Search reads names, ids and tooltips. A click on an entry puts one of it straight into the selected slot, replacing what was there, and the slot stays selected. The Edit tab's count sets more (with `ITEM` there is no count). Enter in the search box puts the first match in the selected slot the same way. The grid asks for its nine columns, so the dialog is always wide enough to show them whole. In a list, filling the slot at the end adds a new empty one after it, to select and fill next.

The Edit tab, for `EXACT` and `CONTAINS`, edits the selected slot. It has the slot's name and count, then its enchantments in two lists. Under "Added" are those on the item, each with its level, `-` and `+` buttons from I to its highest, and a remove button. Under "Not Added" are those that fit the item and can be added, each with an add button. One that an enchantment on the item excludes is greyed and says what it conflicts with. An item nothing enchants lists none, and an enchanted book lists its stored ones. When the player has the match choice, it follows as two round radio buttons, Any and Exactly this. Last is every other component as text in vanilla's `/give` syntax, `[unbreakable={},!food]`. The box's hint is a short example, and its tooltip explains the syntax. Vanilla's own item parser reads the text, and it is checked as `/give` checks a stack. While it is not valid it shows red with vanilla's error under it, and nothing is written.

Changes are written as they are made. Done keeps them, and Cancel and Escape write back the value from before the dialog opened. The value is a setting, never the player's item. The dialog's slots are not real slots, nothing touches the inventory or a container, and nothing is sent to the server, so the picker belongs to MenuKit, not a slot library. With no world open (a settings screen on the title screen) the picker uses vanilla's own data, loaded once.

## Widget host

A `WidgetHost` puts a vanilla-style widget (anything that is a `GuiEventListener` and a `Renderable`) in a panel, at the element's box. It is the one way MenuKit hosts a vanilla widget. `TextField` and `Slider` are built on it, and a mod hosts a widget another library hands it the same way (see [recipes](recipes.md#host-a-widget-another-library-made)). `WidgetHost.builder().widget(supplier).size(w, h)` builds one. Call `widget` first, since it sets the type `place` works with. The supplier runs when the widget is first needed, at the screen's init.

Every frame the host moves the widget to its box and draws it there. A vanilla `LayoutElement` is moved with `setX` and `setY`, and an `AbstractWidget` is also given the box's width and height. Anything else is moved by `place((widget, x, y, width, height) -> ...)`, which also wins over the `LayoutElement` path when given. Building a host whose widget MenuKit cannot move, with no `place`, fails at the first screen open with a message saying so. The widget is clipped to its box and to any scroll area it sits in.

The host routes the mouse itself, in screen coordinates, the way vanilla routes it to a screen's widgets: the click (with vanilla's double click and the modifier keys held), the drag while the press is held, the release of that press, and the wheel. A hosted widget therefore takes clicks on a standalone screen and on a panel over a vanilla screen alike. A click the widget takes gives it keyboard focus. Keys, typed characters, Tab and arrow focus and narration come through vanilla's focus, as for `Button`. A hosted widget that stops being drawn, or is disabled, gives up focus at the next key and Tab skips it.

`overflow()` is for a widget that draws past its box while it has focus, such as a dropdown's open list. While it has focus it draws unclipped on top of every panel on the screen, after the dialogs, and takes clicks anywhere until a click it does not take ends its focus. Under a dialog it does not belong to, it draws in place.

A hosted widget follows the rules of any element. Under another panel's claim it takes no click and shows no hover. Disabled, it takes no input. Its look is its own: it draws itself the same in both looks.

## Color field

A `ColorField` is a colour setting: a swatch of the colour and a lens onto it (`state(IntSupplier, IntConsumer)`). The value is `0xRRGGBB`, or `0xAARRGGBB` with `alpha(true)`. Without alpha the value's top byte is ignored when read and written as zero. `label(text)` names it for the dialog's title and the narrator.

A click on the swatch (or Enter on it) opens a modal dialog on the same screen, in its kind. The Basic tab comes first. It shows the 16 vanilla dye colours in rainbow order, two rows of six with the four neutrals centred under them. Each is shown by its dye item and named in a tooltip by vanilla's colour name (Light Blue), and the current one is ringed. A dye is opaque, so picking one writes alpha `FF`. The Custom tab has a saturation and brightness square, a hue bar, a transparency bar over a checkerboard when there is alpha, the colour before and now side by side, and a hex field. Typing in the hex field is free, and only valid hex is written: `#RRGGBB`, and with alpha also `#AARRGGBB`, where six digits keep the current transparency. While the text is not valid it shows red, and when the field loses focus it goes back to the colour's hex. `ColorField.parseHex` and `formatHex` read and write the same strings. The dialog is as wide and as tall as the shown tab, so it grows when the player switches to Custom and shrinks back on Basic.

Each change is written through the lens as it is made, so the screen behind the dialog shows it at once. Done closes the dialog and keeps the change. Cancel and Escape write back the colour from before the dialog opened. The dialog needs a screen, so a colour field on the HUD only shows its swatch.

## Input context

Every input method receives an `InputContext`, as `render` receives a `RenderContext`. It carries the content origin, the mouse, whether the element's surroundings are disabled, and the kind of screen. No element caches its origin or its hover state from the last frame. A container hands its children a context moved to their origin through `ChildDispatch`, the one dispatch every host and container uses. An open overlay (a dropdown's list) takes a click first, then the first child whose hit test contains the point.

## Structure does not change after build

`build()` freezes the panel's element list. No method adds an element to a built panel. Visibility, position, and supplier-driven content change at runtime. To change the element list, build a new panel. The one element whose children change while the screen is open is `ListView` (Live lists, below).

A hidden element or panel is inert on every surface. It does not render, receive clicks or show a tooltip. It takes no room in a panel's layout or a `Flow`. `Row` and `Column` place once, so a hidden element there keeps its gap.

## Size

Width flows down from the panel to its elements. A label wraps to it and a slider caps to it. Height flows down only to an element whose `fillsHeight()` returns true. Through `fillHeight(int)`, that element gets the room from its top edge to the bottom of the panel's viewport. The viewport is the panel's pinned height or, for a screen's MAIN panel, the screen's height. With no viewport the element gets -1 and takes its natural height.

Three built-in elements fill. A `Tabs` with no fixed size does. `ScrollContainer.builder().fillHeight()` (7.3.0) takes the height it is given and scrolls its content in it; with none given it is its declared height, or as tall as its content. A vertical `Flow` holding a filling child fills too, and gives that child what is left after its other children and gaps, so a header and a row of buttons sit above a list that takes the rest. A panel gives a filling element the room from its top edge down, so put what goes under it in a vertical `Flow` with it.

A `Tabs` page is handed to a body that fills (7.3.0). The page then does not scroll the body as a whole: the filling element gets the page's height from its top edge down to the band's bottom inset and scrolls itself, so the header above it stays put. The page's inset, band and separators are the same as for any body, and a `ScrollContainer` on the page draws no band of its own (`PanelElement.inBand`). A body with nothing filling scrolls as a whole, as before. The same page looks and works the same as a standalone screen's main panel.

## Live lists

A `ListView` (7.3.0) shows one row element per item of a list the mod owns, and follows it while the screen is open. `items(supplier)` is read every frame, `key(function)` gives each item a stable identity, and `row(factory)` makes a row for a key the first time it appears. A new key's row is attached to the screen at once, exactly as an element present when the screen opened (a `TextField` makes its text box, a button joins the keyboard's focus order). A key that goes has its row detached: keyboard focus it held is let go and an open popover in it closes. A key that stays keeps its row, the same object, so a rename in progress keeps its text and focus while the data refreshes. Rows are in the order of `items()`, stacked top to bottom `gap` apart, each as wide as the list. A row is any element MenuKit can place, usually a boxed `Flow`. It is built once, so it reads what can change through suppliers, by key.

The list's height is its rows', read every frame. A list longer than its room goes in a `ScrollContainer`, usually one that fills (Size). A list in the middle of a page pushes what is below it: its whole height, the toolbar it draws included, is its `extraLayoutHeight()`, so the elements after it are placed as if it took no room, as after a closed `Section`, and move down as rows are added or grow and up as they go, in the same frame. That holds on a panel, on a `Tabs` page with or without a filling element, in a `Section`, through a `Column` or `Row`, below a vertical `Flow` holding the list, and in a `ScrollContainer`, whose scroll range follows in the same frame. Scrolled down, a list above the view that grows or shrinks moves the scroll by as much, so what is on screen stays put. A `ScrollContainer` built with `fitHeight()`, as tall as its content up to its declared height, pushes what is below it the same way, its whole height its `extraLayoutHeight()`: place what follows it as if it took no room.

`selection(get, set)` is a lens onto one selected key. A click on a row where none of its controls takes the click selects it, and the selected row is marked (List editing, below). After a click in the list, Up and Down move the selection, except while a text field has the keyboard. When the selection changes other than by a click, by the mod's code or the arrow keys, the scroll area around the list scrolls the least distance that shows the selected row (`PanelElement.takeRevealRequest`). A selected key whose item is gone clears the selection through the setter. `empty(element)` shows in the list's place while there are no items.

The attach and detach path is shared: `Attachment` remembers the screen a container's children are attached to, so any container that adds a child after build attaches it the way the screen's init does.

## List editing

Every editing option is optional, and a list with none of them is the list above, exactly. The data stays the mod's: the list only calls back and shows what `items()` returns next, so an add or move the mod refuses changes nothing.

The options share one toolbar, `list.toolbar()`, an ordinary element the mod places above the list's scroll area so it stays put while the rows scroll. Left to right it holds the search field (`search(item -> text)`), taking the free width; the sort picker and its direction (two or more `sort(label, comparator)`, the first the default, `sortState(get, set)` to remember the choice); one add button per `add(label, action)`, with a hover tooltip saying what the kind is given as `add(label, tooltip, action)`; then `- Remove item` (`remove(key -> ...)`, its label overridable with `remove(label, ...)`, and its tooltip with `remove(label, tooltip, ...)`) and up and down arrows (`move((key, toIndex) -> ...)`). Remove and the arrows act on the selected row, and a click anywhere in a row selects it; without `selection(get, set)` the list keeps its own. They are grey while nothing is selected or the row can't do it: a `pinned(predicate)` row, up on the top row and down on the bottom one, a move past a pinned row, remove on a row a search hides, and either arrow while a query is typed. `confirmRemove(item -> body)` asks first in a `ConfirmDialog` whose body names the item. After a remove, the row that took its place is selected (the one before it when it was last), so repeated removes are quick. Alt+Up and Alt+Down move the selected row. An added row is scrolled into view, clearing a query that hides it. A list can't both sort and move: `build()` throws. The toolbar lines up with the edges of whatever it is laid out in: placed by the mod it is as wide as its container gives it, like any other element, so in a column with a scroll area its right end is the scroll area's outer edge, level with the controls above it. A list whose toolbar is never asked for draws it at its own top, as wide as the rows, inside the band and clear of the scrollbar. `title(component)`, or `title(() -> component)` read every frame for a live count, puts the list's heading on the toolbar's line at its left edge, so the heading and the buttons share one line: drawn in the look's heading style, centred on the buttons, before the search field (which then takes the width between them) or empty room, its styles kept (a grey count stays grey), and cut with "..." if it would reach the search field or the first button, shown whole while hovered. `title(title, tooltip)` gives it a hover tooltip of its own instead, fixed or read while hovered, shown cut or not, as a `Section` with a tooltip does. A list with a title and nothing else still has the title's line; without one the toolbar is unchanged.

For a short list where each row is its own thing to edit, such as a config list of text fields, `controlsInRows()` puts up, down and a remove cross in every row instead, after a thin divider line: inside a card, on a `Section`'s header line, or beside a bare row. No selection is needed. Add stays in the toolbar.

`addDisabledWhen(condition)` greys the add buttons and `removeDisabledWhen(condition)` greys the remove, the toolbar's `- Remove item` and every row's cross alike, while the condition holds, read every frame: a list at its longest or shortest. A click on a greyed one does nothing.

`disabledReason(text)` shows above the rows while the list's `disabledWhen` holds.

The selected row reads in both looks: a white outline in the Default look (grey while the list doesn't have the keyboard), and in the Panel look a card washed lighter inside its border (a row that isn't a card keeps the outline).

A row's own content is the mod's, and the list draws nothing in it beyond the selection and, with `controlsInRows()`, its controls. A mod's own controls in a row (a toggle, an Edit button) sit at the row's right end when a `Flow.spacer()` comes before them, and `crossAlign(CrossAlign.CENTER)` centres the row's labels on their line; any other placement is the row's own `Flow` layout.

## Edit dialog

An `EditDialog` (7.3.0) is a modal dialog for settings that take effect only on Save: a title, the mod's controls, Cancel and Save, laid out as every MenuKit dialog and as wide as its content. It is built and shown like a `ConfirmDialog`, a `Panel` whose visibility reads the mod's state. Its controls are lenses onto a draft, a copy the mod fills when it opens the dialog, so nothing changes behind it while the player edits. `onSave` writes the draft back and closes it; `onCancel`, which Escape also runs, just closes it. A mod opens it from any button of its own, such as an Edit button in a list row.

## Menu button

A `MenuButton` (7.3.0) is a button that opens a menu of actions: `action(label, runnable)` for each row, `danger(label, runnable)` for one that destroys something, drawn red. A click on a row runs it and closes the menu. The menu is as wide as its widest label and, when wider than the button, ends at the button's right edge, so a small button at the end of a row opens back over the row. It is a dropdown's popover: it draws above everything around it, is not cut off by a scroll area, closes on a click outside it, and Up, Down, Enter and Escape work while it is open. A `Dropdown` can also take its options from a supplier (`items(() -> list)`), for options that change while the screen is open.

## Text field

A `TextField` is vanilla's text box with a lens onto the mod's text. `onSubmit` runs on Enter. Escape on a focused field (7.3.0) cancels the edit: the text goes back to what it was when the field took focus, written through the lens, the field lets go of the keyboard, `onCancel` runs, and the screen stays open. A second Escape is the screen's. `focus()` gives the field the keyboard from code, for a rename field that appears when its button is clicked.

## Tabs

`Tabs` is a tab strip and the area below it that shows the selected tab's body. One element holds both, so the body's top edge follows the strip's height. Each tab has a string id, a label, a body, and an optional `visibleWhen`. A body is a list of elements positioned from the body's top-left. It is laid out to the body width the way a panel lays out its own elements, and scrolls when it is taller than the body area. With no fixed size and no viewport to fill, a `Tabs` is as tall as its tallest tab, so switching never resizes it. `fitShownTab()` instead makes it as wide and as tall as the shown tab, for a dialog that should fit what it shows.

Selection is consumer state. `state(get, set)` reads the selected id every frame and writes the id the player picks. To select a tab from outside, the mod writes its own field. When the selected tab is hidden, the next visible tab is shown, or else the previous one. Nothing is written, so the hidden tab takes the selection back when it reappears.

`Tabs` has three modes. `WRAP` breaks the strip into the fewest rows the width allows and splits the tabs so the rows are about equally full. A tab keeps its row and place when another is selected. `SIDE_SCROLL` keeps one row. When it overflows, it scrolls from the left with arrows at both ends, and a newly selected tab slides into view. `SIDEBAR` puts the tabs in a column down the left with the body to its right. The selected tab reaches further left and opens into the body. The column is as wide as its widest label, at most a third of the element. When the tabs are taller than the column, a scrollbar runs down its left edge. It is a `ScrollContainer`'s scrollbar, with the same track and handle at the same size. Drag the handle, or click the track to jump there. A newly selected tab slides into view. The tabs narrow to make room for the bar, so the body does not move. Both strips scroll the way a `ScrollContainer` scrolls its content. They move by pixels, 10 per wheel notch, at once under the wheel and the handle. An arrow slides the row one tab along. A selected tab that is partly off the strip slides into view instead of jumping. `sidebarHeader(elements...)` puts elements above the column, such as a Back button, in the column's width. The body beside the column still starts at the top. On a container screen the sidebar is drawn in vanilla's tab colors. Resource packs that restyle vanilla's tabs change the top strip there but not the sidebar.

In the Default look (see Looks), the strip of `WRAP` and `SIDE_SCROLL` is Create World's tab bar. Every label is white, and the selected one is underlined and open into the page below. The page is vanilla's list background down to a separator at the bottom. `SIDEBAR` there is the same tab bar turned on its side. Vanilla's tab sprites are turned a quarter and open toward the body on the right. The selected one opens into a page of the list background that runs from the column's edge to the element's right. Vanilla's separator runs down the column's edge above and below the tabs. Labels stay upright, white, the selected one underlined. Either page insets the body like the list band, `DEFAULT_INSET_X` from its sides and `DEFAULT_INSET_Y` above and below (in a dialog, `DIALOG_INSET` each way). Sizes, arrows and behaviour are the same in both looks. The sidebar's scrollbar is vanilla's thin list scroller there, as a `ScrollContainer`'s is.

`header(elements...)` puts a row across the whole element, above the strip or the sidebar column and above the body, in every mode. It is a menu's own title bar. Its elements get the element's full width, so a `Flow` with a spacer at each end centres a label across it. The strip, the column (with its `sidebarHeader`) and the body start under it after a 4 pixel gap, and the body is that much shorter. The screen's title is separate. A screen whose header names it can call `hideTitle()` so the name shows once.

`tabColumn(false)`, in `SIDEBAR` only, draws no tab column. The column is 0 wide, so the body takes the whole width of the same page the column would sit beside. In the Default look that is the same darker band, the same insets, bottom separator and scrollbar, so a menu with a single page looks like its tabbed siblings without the column. In the Panel look the body starts at the left edge. `header(...)` still runs across the top, and clicks where the column would be reach the body. The selected tab is the one shown, so with several tabs the `state` lens still chooses which. It cannot be combined with `sidebarHeader(...)`, which lives in the column. Put those elements in `header(...)` instead.

`align(...)` is `LEFT`, `CENTER`, `RIGHT`, or `FILL`, applied per row. In `SIDEBAR` it places each label in its tab, with `FILL` the same as `LEFT`, and labels stay put when the selection changes. `FILL` gives every tab in a row, the last row included, its label width plus an equal share of the leftover space. In `SIDE_SCROLL`, alignment applies only while the row fits. A label wider than its tab is cut short, ending in three dots, and shows in full on hover.

A tab can have an icon left of its label. `icon(texture, textureSize, drawSize)` takes a supplier of a standalone square texture's id, such as a mod's icon read from its jar into a `DynamicTexture`, and its side in texels. The whole texture is drawn shrunk to `drawSize` pixels square. `icon(sprite, drawSize)` takes a GUI sprite instead. The tab is wider by the icon and `ICON_LABEL_GAP` (4 pixels), so the icon never covers the label or cuts it shorter. The icon and label are placed together: centred as one in a strip tab, and aligned as one in a sidebar tab, so a label with an icon starts further right. The selected tab's underline in the Default look stays under the label. A supplier that returns null draws nothing but keeps the room, so the tab does not change width while its texture loads. A tab with no icon is drawn exactly as before. `Icon.builder().texture(id, textureSize)` draws a texture the same way as a standalone element.

By default `Tabs` fills its panel's width and height. Put it in a screen's MAIN panel for a full-screen tabbed menu. `size(width, height)` fixes both, for a panel that has no height to give it. Each tab keeps its body's scroll position while the element exists. `bodyScroll(get, set)` hands it to the consumer in pixels from the top, keyed by tab id. That is the unit `ScrollContainer.state` uses. `get` is read every frame (null is the top), and `set` gets each change. A screen rebuilt on the same lens opens each body where it was, on the first frame, without sliding from the top. An offset past the body's end is clamped. The selected tab is already the consumer's through `state(get, set)`.

A body's scroll stays where the player put it. It is stored as pixels from the top. When the body changes height (a section opening or closing, a label rewrapping, a dropdown's label growing a line), what is on screen stays put. It moves only when the end of the body comes up past it. A dropdown's popover draws over the body and never changes its height. A click on a control never scrolls the body, and a body never slides. Only a strip slides. A body that stops scrolling because everything fits is at the top, and starts from the top when it next scrolls. A panel's own auto-scroll behaves the same.

Other mods can add tabs to a menu that has a name. The owner names it with `menu(id)` on the builder, and another mod calls `Tabs.addTo(id, tab)` at its init. An added tab's body is a factory, `body(() -> elements)`. It is built every time the owner builds the menu, against the adding mod's own config. The owner's builder order is the menu's order. An owner tab marked `standIn()` holds a place for a tab another mod may add. An added tab with the same id takes its place. With nothing to replace it, the stand-in shows with a dimmed label. Only the place carries over. The replacement keeps its own `visibleWhen`, or always shows, so an owner's switch that hides its stand-ins never hides the real tabs. Any other added tab places itself with `after(id)` or `before(id)`, or goes at the end. Added tabs that share a place are ordered by id, so the result does not depend on which mod loaded first. When two mods add the same id to one menu, the second `addTo` is refused. A tab added to a menu that no mod builds is never shown.

## Section

`Section.builder(title)` is a collapsible section. Its header row has an arrow, an optional color swatch, the title, and a grey summary read every frame. `Section.builder(() -> title)` and `swatch(() -> argb)` (7.3.0) read the title and swatch every frame too, so a section built once, such as a `ListView` row, shows a rename or a recolour in place. Clicking the header opens and closes it. Its children are positioned from the top-left of the content area under the header and laid out to the section's width. They get clicks, keys and overlays as they do directly on the panel. A closed section's children are not drawn and get no input except mouse releases.

Position the row after a section where it sits while the section is closed. When it opens, the panel's reflow pushes later rows down by the content's height and the scroll height follows, the same way a wrapped `Flow` pushes rows. Sections nest. Open state is the element's view state and starts closed. `open(true)` starts it open, and `state(get, set)` hands it to the consumer. A disabled section greys its header and everything in it. In the Default look the header takes the Controls screen's category header colours, with the title in white. It still reads from the left as in the Panel look, in line with the content under it.

## Layout helpers

`Row` and `Column` compute positions once and return a `List<PanelElement>`. They do not exist at runtime. The positions are computed on the panel's first layout pass, when the game's font can measure text. A panel or dialog laid out with them can therefore be built at mod init. An element enters a layout built: `.add(Button.builder()...build())`, and the helper moves it into place. A custom element that can only be constructed once its position is known implements `ElementSpec`. `.build()` returns positioned elements that go into a panel with `.add(...)` or `.elements(...)`.

`Flow` is the runtime row. Its children flow left to right at the panel's width and wrap to new rows. `Flow.spacer()` takes whatever a row's other children leave of that width. A label, a spacer and a control make a settings row with the control pinned to the right edge at any window width, with no width written anywhere. A spacer at each end of a row centres what is between them. When the row wraps, the control drops under its label. A row top-aligns its children. `crossAlign(CrossAlign.CENTER)` (7.3.0) centres each on its row's line instead, so a label sits level with the middle of a taller control and controls of different heights line up, with no pixel offsets; `CrossAlign.END` sets them on the line's bottom. The line is the tallest child's first line, so an open `Section` in the row keeps its header on it and grows downward.

`Flow.builder().boxed()` draws a card behind the children, for a row of a list that reads as one unit (a world in a world list). The children sit `Flow.BOX_PADDING` (6) inside the card. A boxed row fills the width its panel gives it, and its height hugs its rows plus the padding. In the Default look the card is a dark translucent box with a one-pixel light border that brightens while hovered. In the Panel look it is the `INSET` panel style. An icon beside a stack of lines is a boxed Flow holding the icon and a vertical Flow.

`Flow.builder().vertical()` is the runtime column. Children flow top to bottom and wrap into a new column to the right. It wraps at the frame band, which is the room from the Flow's top down to the bottom of the screen's frame beside its panel (a container screen's `topPos + imageHeight`). The host hands the band to the panel the way it hands it its width, and the panel hands it to its elements. A panel has a band when it is in a side region (`LEFT_ALIGN_*`, `RIGHT_ALIGN_*`) on a container screen, on an anchor on a container screen, or beside a standalone screen's main panel. On an anchor the band still runs to the container screen's frame, not the anchor's box. Anywhere else the Flow is one column. The band is not where the panel scrolls. The panel still scrolls at the screen's edge. In a column, `gap(horizontal, vertical)` is the gap between columns and the gap between children, and a spacer takes no room. In either direction a hidden child takes no room, so the Flow reflows when one hides.

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

The two placements that stack, `region(...)` and `screenAnchor(...)`, take a third: `.gap(n)`, the space in pixels before this panel. In a region that is the space between the panel and the reference on the side the region sits, and between the panel and the sibling before it in the stack. `region(RIGHT_ALIGN_TOP).gap(0)` sits flush against the frame's right edge, and a second panel there with `gap(0)` sits flush under the first. On a screen spot it is the space between the panel and the sibling before it; the first panel on a spot keeps the edge inset. Without `.gap(n)` each host keeps its own spacing: 2 pixels in every region (container screens, anchors, the HUD's hotbar, a standalone screen's main panel), and on a screen spot 4 pixels on a vanilla screen and 2 on the HUD. The gap belongs to the panel that declares it: the space after a panel is the next panel's gap, not its own. A negative gap throws when the position is built; to overlap a neighbour, use `.offset(dx, dy)`.

A panel with no placement is unplaced. An adapter or the HUD rejects it at registration, naming the panel. `MKScreen` and a custom menu's screen give it a default instead. The first unplaced panel becomes `main()`, and each later one stacks below it.

A panel wraps its width to the space its placement leaves and scrolls its height when taller than its room. `Panel.size(w, h)`, `pinnedWidth(w)`, and `pinnedHeight(h)` override this.

On a standalone screen, the `main()` panel makes room for the `region(...)` panels around it. Its room leaves out the height of the panels above and below it and the width of the panels beside it, gaps included. A panel above a full-height menu still fits, and the menu gets a little shorter. A main panel with room to spare stays centred.

## Reference

A reference is the rectangle a `region(...)` placement is measured against. It is not the panel and has no relation to the panel's size. The host decides it. It is a container screen's frame (extended by its chrome, such as creative's tab rows or an open recipe book), an anchor's box, a standalone screen's main panel, or on the HUD the vanilla hotbar. `Reference` is the record that carries it. A region never names its reference.

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
| Anchor | A rectangle a mod supplies on any screen | `AnchorPanelAdapter` | region, center, pixel | Yes |
| HUD | The game window during play, and the vanilla hotbar | `HudPanel` | screenAnchor (`.region(InsideRegion)`), region around the hotbar (`.region(OutsideRegion)`), pixel | No |
| Standalone | A screen the consumer opens | `MKScreen` (subclass), or a library's own screen through `api.host.OwnHost` | main, region, screenAnchor, center, pixel | Yes |

A placement a context cannot resolve is rejected at registration. HUD panels do not receive input. For a clickable HUD control, open a standalone screen from a key binding.

Every surface draws in the same order across all its hosts. FLOW panels come first, then POPOVER panels, then a dim when any shown panel dims behind it. OVERLAY panels (those that `center()`, dim behind, or track as modal) draw on top. Within a layer, a lower host draws before an upper one: a standalone screen's own host, then the screen's adapters, then the anchors. Within a host the sorted order holds. An element renders the same in every context.

A popover, `Panel.builder(id).popover()`, is a panel that pops up over the screen. It draws above every ordinary panel of every host, whichever registered first, and below a modal and its dim. Its placement is still its own `position(...)`. On a container screen, popovers draw after the ordinary panels and before the slots a slot library adds to panels (SlotKit's created slots), so a popover's own slots draw on it. Another panel's created slot under a popover still draws over it.

## Claims

What a panel covers is inert. A point on the screen is claimed by the topmost panel that takes it. Topmost follows the draw order above (OVERLAY, then POPOVER, then FLOW), so what is drawn on top takes the point:

- An opaque panel (the default) takes every point of its rectangle. Nothing inside it lets a point through to what is behind the panel.
- A see-through panel, `opaque(false)`, takes only its solid elements, meaning the shown, opaque and interactive ones, such as a button. A label on it takes nothing.
- An element's open overlay, such as a `Dropdown`'s list, takes its area whatever its panel's opacity.
- A shown modal (`tracksAsModal`) takes every point on its screen.

Under a claim, vanilla gets nothing. There is no slot highlight, click or tooltip, no widget or list hover, and no creative tab. The claim routes the point to the claiming panel instead. A panel's own slots stay live under its own claim (a created slot it presents, or a standalone screen's own slot group). A standalone screen's own widgets stay live under its own panels too. Every surface runs the same check, so an anchored panel and a standalone screen's panels block what they cover exactly as an adapter's panel does.

Tooltips go through one MenuKit check before vanilla queues them. To hide every tooltip in the game for a time, register a condition once from client init with `MKTooltip.hideWhen(() -> ...)`. While any registered condition is true, no tooltip draws, a panel's own element tooltips included. Inventory Plus's "hold Ctrl to hide tooltips" setting uses it.

To hide one kind of tooltip, register a predicate of `MKTooltip.Kind` instead: `MKTooltip.hideWhen(kind -> kind == MKTooltip.Kind.ITEM && ...)`. A tooltip is `ITEM` when an item stack's tooltip comes through vanilla's item paths: a slot on any container screen (creative included), the recipe book, an item in chat, `setTooltipForNextFrame(font, stack, x, y)`, or lines from `Screen.getTooltipFromItem`. Everything else is `OTHER`, including a mod's item tooltip built from `ItemStack.getTooltipLines` and queued as plain lines. MenuKit marks the item paths just before they queue, and the mark goes with the next tooltip or ends with the frame, so it never reaches a later tooltip. Hiding by kind changes nothing else: a tooltip that shows is docked, wrapped, clamped and scrolled as before. Dock and Hide Tooltips' "hide item tooltips" and "hide all other tooltips" settings use it.

The same check places every tooltip, so a mod can scroll one too. `MKTooltip.onWheel((horizontal, vertical) -> ...)` receives the mouse wheel whenever it turns over a shown tooltip. It runs before any other handler, whether or not something then uses the event. `MKTooltip.scrollBy(dx, dy)` moves the tooltip on screen by that many pixels (positive y moves it down). MenuKit keeps a scrolled tooltip inside its own extent, so one that fits on screen never moves. It resets the offset when the tooltip's text changes or the tooltip goes away, and `MKTooltip.resetScroll()` resets it by hand. While a tooltip is scrolled with its top above the screen, its first line stays pinned at the top edge and the rest scrolls under it. Inventory Plus's "scroll long tooltips" setting uses it.

Two more switches on the same check keep a tooltip on screen. Both are off unless a mod registers them from client init. `MKTooltip.wrapWhen(() -> ...)` breaks any text line wider than the screen (less a 4 pixel margin and the frame's padding) onto more lines with vanilla's splitter, and keeps the tooltip inside both sides. Images such as a bundle's grid are left as they are. A wrapped first line stays one title, so the scroll pin keeps all of its lines. `MKTooltip.clampWhen(() -> ...)` keeps the tooltip's top on screen. A tooltip taller than the screen starts at its title and runs off the bottom, and `MKTooltip.scrollBy` with a negative y brings up the rest, down to its last line. Inventory Plus's "keep tooltips on screen" setting uses them.

Vanilla puts a tooltip beside the cursor, so a wide one near an inventory lands over the slots the player is reading. `MKTooltip.avoidCoverWhen(() -> ...)` docks it instead, and is also off unless a mod registers it. While it holds, a tooltip vanilla would place beside the cursor sits centred at the bottom of the screen, 4 pixels (`MKTooltip.EDGE`) above the edge. When the cursor is within 20 pixels (`MKTooltip.DOCK_MARGIN`, a bit more than a slot) of where that tooltip would sit, not only directly over it, it sits centred at the top instead. Nothing wraps or changes side. `clampWhen`, `wrapWhen` and the scroll work on a docked tooltip as on any other, so one taller than the screen starts at its title under `clampWhen`. A tooltip vanilla anchors to a widget for the keyboard stays where vanilla puts it. Inventory Plus's "keep long tooltips out of the way" setting uses it.

## Targeting

A `ScreenPanelAdapter` with no target renders on every container screen. `.on(Class...)` limits it to those screen classes and their subclasses. `.onAny()` states the default explicitly. `.onPlayerInventory()` limits it to the player inventory screen in both game modes. `.onMatching(ScreenMatcher)` takes `all()`, `allExcept(...)`, `only(...)`, or (7.3.0) `where(test)`, a test asked of each container screen as it opens, for a scope known only at runtime, such as the menus a slot group's slots were added to ([recipe](recipes.md#show-a-panel-on-the-screens-that-hold-your-slots)).

A `VanillaScreenPanelAdapter` requires `.on(Class...)` or `.onAny()`.

An `AnchorPanelAdapter` requires `.on(Anchor...)`. It renders once per targeted anchor whose box is on the open screen, and is measured from that box.

A panel an adapter places whose elements are all hidden or empty (7.3.0), and that has no pinned size, is not placed: no empty frame, no claim. A panel of slot elements whose slots are not in the open menu is absent that way.

Adapters and HUD panels are declarations. Declare them from the mod's initializer. Constructing, targeting or unregistering one after the client starts throws, the same as every other MenuKit declaration. A panel that comes and goes at runtime keeps its adapter and gates itself with `visibleWhen(...)`. A hidden panel is not measured, takes no room in its region and claims nothing.

## Anchor

An `Anchor` is a rectangle a mod names and supplies per screen: `Anchor.of(id, screen -> box)`. MenuKit asks for the box every frame on every open screen, a container screen or not, and places the anchor's panels around it by the same region rules as the menu frame. A `null` box means the anchor is not on that screen, and its panels take no place and claim nothing. Panels on one anchor share one host and stack in its regions. Each id is declared once; a second `Anchor.of` with the same id throws. SlotKit's slot-group panels are anchors, one per slot group, and a mod can anchor to anything it can measure, such as the pause menu's button column.

The other direction needs no declaration: `Panel.placedBounds()` returns a panel's own rectangle on the current screen (or `null` when it isn't placed right now), read fresh every call. Use it to put something against a panel the mod itself made, such as a close button in its corner, with `PanelPosition.pixel(() -> ...)`.

## Slot rendering

Vanilla draws every slot, through its own `extractSlot`, so every other mod's slot hook marks a slot wherever it is drawn. What MenuKit decides is when. The rule (7.3.0): a slot a panel shows draws right after that panel, and a panel above covers the slots of the panels below it. A slot no panel shows is vanilla's, drawn where vanilla draws it, and a panel over it hides it. A panel over part of a slot hides that part and no more, and what a panel covers is inert (Claims). Menu order decides nothing, so a vanilla slot a panel shows draws on the panel, not under it.

To know which slots each panel shows before vanilla draws its slots, MenuKit lays the panels out once at the start of the frame without showing anything, and the slot elements report their slots. Vanilla's slot pass then leaves those slots out, and each panel draws its own right after its elements, with the hover highlights around the hovered one. Overlay panels do the same after the dim. A library's own screen draws its own panels under every slot, as before. A slot library that does not report its slots keeps the order it had before 7.3.0: its slots draw after the panels. At the end of a frame, `SlotClip.panelShowing(slot)` tells a slot library why a slot was not shown: its panel was drawn (the slot scrolled out of view, so it parks) or not (the panel is hidden, so a vanilla slot it took over goes back to its own place).

A slot in a `ScrollContainer` (SlotKit's `SlotElement`) scrolls and clips like any element. A slot wholly out of view is not drawn, and its slot library parks it, so nothing hovers or clicks it. A slot across the viewport's top or bottom edge stays where it is, so nothing beside it moves, and is cut at the edge: vanilla draws only the part inside (item, count, durability, highlight), and only that part hovers, shows its tooltip and takes a click. A click on the part past the edge does nothing, so a carried item cannot drop there. The same holds under any other clip, such as a `Tabs` body. A slot library makes this work by calling `SlotClip.shown(ctx, slot)` (in `api.host`) when an element places a slot, which hands MenuKit the clip that element draws under. SlotKit's slot elements do, so a mod that puts slots in a scrolling panel has nothing to do.

`SlotRendering` draws a slot's frame and an item's outline, for elements such as `ItemDisplay` and `ItemPicker` that show an item without being a slot.

## One styling change

MenuKit draws a pressed look on every vanilla button and every YACL controller while it is held down, including buttons MenuKit did not create. It is always on and changes nothing but the drawing. It is the one change MenuKit makes to how vanilla looks. Otherwise the library leaves vanilla's appearance and behaviour to the mods that use it.
