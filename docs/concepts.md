# Concepts

Definitions of the terms MenuKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term.

## Two artifacts

| Artifact | Environment | Contents |
|---|---|---|
| MenuKit (`menukit`) | Client and server (UI on the client) | Elements, panels, layout helpers, HUD panels, placement on vanilla screens, standalone screens |
| MenuKit: Containers (`menukit-containers`) | Client and server | Created slots, custom container menus, per-slot state, storage attachments |

Containers depends on MenuKit. MenuKit does not depend on Containers. A mod that depends on MenuKit alone cannot import a Containers type; the build fails.

## Public API

The API is the `api` packages: `com.trevlar.menukit.api.*` and `com.trevlar.menukit.containers.api.*`. [Versioning](versioning.md) covers them. Every other package carries `@ApiStatus.Internal` and is outside the contract, so a mod that calls into one can break in any release. Each artifact has a common half and a client half, and the compiler keeps screen code out of the common half. The `api` packages span both halves; a class that names a screen type is client-only, and the javadoc says so.

## Panel

A `Panel` is a bounded rectangle that holds an ordered list of elements. It renders, receives input, and shows or hides as one unit. It has an id, a `PanelStyle`, a padding, and a visibility.

Panels do not nest. A panel holds elements. It does not hold other panels.

Panel ids are global. Prefix every id with the mod id: `"mymod:controls"`.

## Element

A `PanelElement` is one item inside a panel. Its position is relative to the panel's content area. MenuKit ships these elements:

| Kind | Types |
|---|---|
| Render only | `TextLabel`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `InfoBox` |
| Interactive | `Button`, `Toggle`, `Checkbox`, `Radio` (with `RadioGroup`), `Slider`, `TextField`, `Dropdown`, `DropdownMulti` |
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
| `style(ControlStyle)` | MenuKit's look or vanilla's. |
| `opaque(boolean)` | Whether the element is solid on a transparent panel. |
| `declId(id)` | A stable id for the element's window address. |

An instance keeps only what a container needs: `setChildPosition(x, y)` (a `Flow`, `Row` or `Column` places its children with it) and the layout calls a panel makes every pass. Anything that varies at runtime is read from a supplier.

## Mouse buttons

An element receives left clicks through its primary callback. `Button` and `Toggle` also accept `onSecondaryClick(Consumer<Click>)`, which receives every non-left button.

A `Click` record carries the button index and the shift, control, and alt state sampled when the click reaches the element. It answers `isRight()`, `isMiddle()`, `isShiftRight()`, and `isSecondary()`. On macOS the command key counts as control.

With no secondary handler attached, non-left clicks pass through to vanilla. A disabled control consumes neither kind.

## Keyboard, sound and narration

`Button`, `Toggle`, `Checkbox` and `Radio` behave like vanilla widgets: a click plays vanilla's click sound, Tab moves keyboard focus onto them in screen order (a focused control draws its hover look), Enter or Space presses the focused one, and the narrator reads its label and, for the boolean controls, its on or off state. `Slider` and `TextField` are vanilla's own `AbstractSliderButton` and `EditBox`, exposed through `widget()`. An element that is not drawn (its panel hidden, its section closed, its tab not shown) cannot take focus.

While a modal panel is shown on a vanilla screen, a key goes to the panels' elements first, then Escape runs the modal's `onEscape`, then any other key goes to the focused widget if MenuKit placed it (a dialog's text field, for instance), and then the key is dropped, so the screen under the modal never acts on it. Keys vanilla handles outside the screen still work: F11, the F3 combinations, the narrator hotkey and screenshots.

## Tint

`Button` and `Toggle` accept `tint(IntSupplier)`. The supplier runs each frame and returns an ARGB value that fills the control inside its border, over the background, and under the label. Returning 0 draws no tint. The tint shows consumer-owned state, such as a pinned mode, that the control itself does not store.

## State

A stateful element is a lens and only a lens. `state(get, set)` reads the value from `get` every frame and hands a change to `set`; the element stores nothing, so a value changed elsewhere shows at once and a setter that refuses a change shows the refusal on the next frame. Persistence is the consumer's. `Toggle`, `Checkbox`, `Slider`, `TextField`, `Dropdown` and `DropdownMulti` require a `state`; a `Radio` reads its `RadioGroup.state(get, set)`.

Some state is the element's own view state, not the consumer's: whether a `Section` is open, a `ScrollContainer`'s scroll, a `Tabs` body's scroll, whether a dropdown's popover is open. `Section` and `ScrollContainer` take a `state(get, set)` to hand theirs to the consumer.

`Slider` and `Dropdown` take typed lenses for whole numbers and enums: `Slider.ofInts(min, max)`, `Slider.ofEnum(Mode.class)`, `Dropdown.ofInts(min, max)`, `Dropdown.ofEnum(Mode.class)`. Their `state` and `label` speak `Integer` or the enum. `Slider.builder()` is the 0 to 1 fraction.

## Disabled

`disabledWhen` on an element greys it and makes it inert. `Panel.disabledWhen(supplier)` does the same for every element of the panel, and a container's own `disabledWhen` (a `Section`, `Tabs`, `ScrollContainer` or `Flow`) does it for everything inside. Text greys too: `TextLabel`, `InfoBox` and a section's header draw in the disabled grey. A disabled panel still claims its area, so what is behind it stays inert; its elements take no clicks, wheel or keys. Mouse releases still arrive, so a drag can end.

## Input context

Every input method receives an `InputContext`, as `render` receives a `RenderContext`: the content origin, the mouse, and whether the element's surroundings are disabled. No element caches its origin or its hover state from the last frame. A container hands its children a context moved to their origin through `ChildDispatch`, the one dispatch every host and container uses: an open popover takes a click first, then the first child whose hit test contains the point.

## Structure does not change after build

`build()` freezes the panel's element list. No method adds an element to a built panel. Visibility, position, and supplier-driven content change at runtime. To change the element list, build a new panel.

A hidden element or panel is inert on every surface. It does not render, receive clicks or show a tooltip. It takes no room in a panel's layout or a `Flow`; `Row` and `Column` place at build time, so a hidden element there keeps its gap.

## Size

Width flows down from the panel to its elements. A label wraps to it and a slider caps to it. Height does not flow down unless an element asks: an element whose `fillsHeight()` returns true takes the room from its top edge to the bottom of the panel's viewport, through `fillHeight(int)`. The viewport is the panel's pinned height or, for a screen's MAIN panel, the screen's height. With no viewport the element gets -1 and takes its natural height. No built-in element but `Tabs` fills height.

## Tabs

`Tabs` is a tab strip and the area below it that shows the selected tab's body. One element holds both, so the body's top edge follows the strip's height. Each tab has a string id, a label, a body, and an optional `visibleWhen`. A body is a list of elements positioned from the body's top-left, laid out to the body width the way a panel lays out its own elements, and scrolled when it is taller than the body area.

Selection is consumer state. `state(get, set)` reads the selected id every frame and writes the id the player picks. To select a tab from outside, the mod writes its own field. When the selected tab is hidden, the next visible tab is shown, else the previous one, and nothing is written, so the tab takes the selection back when it reappears.

Three modes. `WRAP` breaks the strip into the fewest rows the width allows and splits the tabs so the rows are about equally full. A tab keeps its row and place when another is selected. `SIDE_SCROLL` keeps one row. When it overflows it scrolls from the left, arrows appear at both ends, and a newly selected tab scrolls into view. `SIDEBAR` puts the tabs in a column down the left with the body to its right. The selected tab reaches further left and opens into the body. The column is as wide as its widest label, at most a third of the element. When the tabs are taller than the column, vanilla's thin list scrollbar runs down its left edge. Drag the handle, or click the track to jump there. The wheel moves one tab at a time, and a newly selected tab scrolls into view. The tabs narrow to make room for the bar, so the body does not move. `sidebarHeader(elements...)` puts elements above the column, such as a Back button, in the column's width. The body beside the column still starts at the top. Resource packs that restyle vanilla's tabs change the top strip but not the sidebar, which is drawn in vanilla's tab colors.

`header(elements...)` puts a row across the whole element, above the strip or the sidebar column and above the body, in every mode: a menu's own title bar. Its elements get the element's full width, so a `Flow` with a spacer at each end centres a label across it. The strip, the column (with its `sidebarHeader`) and the body start under it and a 4 pixel gap, and the body is that much shorter. The screen's title is separate: a screen whose header names it can call `hideTitle()` so the name shows once.

`align(...)` is `LEFT`, `CENTER`, `RIGHT`, or `FILL`, applied per row. In `SIDEBAR` it places each label in its tab, with `FILL` the same as `LEFT`, and labels stay put when the selection changes. `FILL` gives every tab in a row its label width plus an equal share of the leftover space, the last row included. In `SIDE_SCROLL`, alignment applies only while the row fits. A label wider than its tab is cut short, ending in three dots, and shows in full on hover.

By default `Tabs` fills its panel's width and height. Put it in a screen's MAIN panel for a full-screen tabbed menu. `size(width, height)` fixes both, for a panel with no height to give. Each tab keeps its body's scroll position while the element exists.

Other mods can add tabs to a menu that has a name. The owner names it with `menu(id)` on the builder, and another mod calls `Tabs.addTo(id, tab)` at its init. An added tab's body is a factory, `body(() -> elements)`, because it is built every time the owner builds the menu, against the adding mod's own config. The owner's builder order is the menu's order. An owner tab marked `standIn()` holds a place for a tab another mod may add: an added tab with the same id takes its place, and with nothing to replace it the stand-in shows with a dimmed label. Only the place carries over: the replacement keeps its own `visibleWhen`, or always shows, so an owner's switch that hides its stand-ins never hides the real tabs. Any other added tab places itself with `after(id)` or `before(id)`, or goes at the end. Added tabs that share a place are ordered by id, so the result does not depend on which mod loaded first. Two mods adding the same id to one menu is refused at the second `addTo`. A tab added to a menu that no mod builds is never shown.

## Section

`Section.builder(title)` is a collapsible section: a header row with an arrow, an optional color swatch, the title, and a grey summary read every frame. Clicking the header opens and closes it. Its children are positioned from the top-left of the content area under the header, laid out to the section's width, and get clicks, keys and overlays as they do directly on the panel. A closed section's children are not drawn and get no input except mouse releases.

Position the row after a section where it sits while the section is closed. When it opens, the panel's reflow pushes later rows down by the content's height, and the scroll height follows, the same way a wrapped `Flow` pushes rows. Sections nest. Open state is the element's view state and starts closed; `open(true)` starts it open, and `state(get, set)` hands it to the consumer. A disabled section greys its header and everything in it.

## Layout helpers

`Row` and `Column` compute positions at build time and return a `List<PanelElement>`. They do not exist at runtime. An element enters a layout built: `.add(Button.builder()...build())`, and the helper moves it into place. A custom element that can only be constructed once its position is known implements `ElementSpec`. `.build()` returns positioned elements that go into a panel with `.add(...)` or `.elements(...)`.

`Flow` is the runtime row: its children flow left to right at the panel's width and wrap to new rows. `Flow.spacer()` takes whatever a row's other children leave of that width, so a label, a spacer and a control make a settings row with the control pinned to the right edge at any window width, and no width is written anywhere. A spacer at each end of a row centres what is between them. When the row wraps, the control drops under its label.

`Row.width(px)` declares the row's overall pixel budget and `.addSpacer()` adds a flexible gap that expands to fill whatever the other children and spacing leave over, so one thing pins to the row's left edge and another to its right (`Back .... Reset`). Several spacers split the leftover evenly, the odd pixel to the last one. `addSpacer()` without `width(px)` throws, since a spacer with nothing to expand into is a mistake in the layout.

## Placement

Where a panel sits is declared once, on the panel: `Panel.builder(id).position(PanelPosition...)`. Every host reads it. An adapter only says which screens the panel appears on.

| Placement | Where |
|---|---|
| `PanelPosition.main()` | A standalone screen's frame, centred. One per screen. |
| `PanelPosition.region(OutsideRegion)` | Outside the host's reference rectangle, along one of its edges. |
| `PanelPosition.screenAnchor(InsideRegion)` | On one of nine spots of the screen or game window, inset from the edges it touches. |
| `PanelPosition.center()` | Centred on the screen as an overlay, drawn on top. |
| `PanelPosition.pixel(Supplier<ScreenOrigin>)` | At an absolute origin re-evaluated each frame. Returning `null` skips the frame. It does not stack and does not wrap. |

Two modifiers ride any placement. `.priority(n)` orders panels that share a region (lower sits nearer the anchor edge) and is the z-order within a host (lower draws underneath). The default is 100; ties go by the registering mod's id, then by registration order, so the order is the same on every launch. `.offset(dx, dy)` nudges one panel after it is placed; its siblings stack on its un-nudged position.

A panel with no placement is unplaced. An adapter or the HUD rejects it at registration, naming the panel. `MKScreen` and a custom menu's screen give it a default instead: the first unplaced panel becomes `main()`, and each later one stacks below it.

A panel wraps its width to the space its placement leaves and scrolls its height when taller than its room. `Panel.size(w, h)`, `pinnedWidth(w)`, and `pinnedHeight(h)` override this.

On a standalone screen, the `main()` panel makes room for the `region(...)` panels around it. Its room leaves out the height of the panels above and below it and the width of the panels beside it, gap included, so a panel above a full-height menu still fits and the menu gets a little shorter. A main panel with room to spare stays centred.

## Reference

A reference is the rectangle a `region(...)` placement is measured against. It is not the panel and has no relation to the panel's size. The host decides it: a container screen's frame (extended by its chrome, such as creative's tab rows or an open recipe book), one slot group's bounding box, or a standalone screen's main panel. `Reference` is the record that carries it. A region never names its reference.

## Region

A region names where a panel sits relative to its reference. Two enums exist, one for each side of the reference's edge:

| Type | Placement | Values |
|---|---|---|
| `OutsideRegion` | Outside the reference, along one of its edges | `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`, `CENTER` |
| `InsideRegion` | On the screen, at one of nine spots | `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `LEFT_CENTER`, `RIGHT_CENTER`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`, `CENTER` |

Panels in the same region stack in priority order. `InsideRegion` spots resolve through one resolver with each context's inset. On the HUD and on vanilla screens the inset is 4 pixels, and `CENTER` on the HUD starts directly below the crosshair. On a standalone screen the inset is the safe-area margin for the screen's chrome, which always places even when oversized.

## Five contexts, one host each

A context is the answer to one question: what is this panel placed against? Each is one `PanelHost`, which sorts its panels, places them, draws them in layers and routes their input the same way.

| Context | Placed against | Entry type | Placements | Input |
|---|---|---|---|---|
| Container screen | The menu frame | `ScreenPanelAdapter` | region, screenAnchor, center, pixel | Yes |
| Other vanilla screen | The screen | `VanillaScreenPanelAdapter` | screenAnchor, center, pixel | Yes |
| Slot group | One slot group's box | `SlotGroupPanelAdapter` | region, center, pixel | Yes |
| HUD | The game window during play | `HudPanel` | screenAnchor (`.region(...)`), pixel | No |
| Standalone | A screen the consumer opens | `MKScreen` (subclass), `CustomContainerScreen` | main, region, screenAnchor, center, pixel | Yes |

A placement a context cannot resolve is rejected at registration. HUD panels do not receive input; for a clickable HUD control, open a standalone screen from a key binding.

Every host draws in the same order: FLOW panels, then a dim when any shown panel dims behind it, then OVERLAY panels (those that `center()`, dim behind, or track as modal) on top. An element renders the same in every context.

## Claims

What a panel covers is inert. A point on the screen is claimed by the topmost panel that takes it:

- An opaque panel (the default) takes every point of its rectangle. Nothing inside it lets a point through to what is behind the panel.
- A see-through panel, `opaque(false)`, takes only its solid elements: shown, opaque and interactive ones, such as a button. A label on it takes nothing.
- An open popover, such as a `Dropdown` list, takes its area whatever its panel's opacity.
- A shown modal (`tracksAsModal`) takes every point on its screen.

Under a claim, vanilla gets nothing: no slot highlight, click or tooltip, no widget or list hover, no creative tab. The claim routes the point to the claiming panel instead. A panel's own slots stay live under its own claim (a created slot it presents, or a standalone screen's own slot group), and so do a standalone screen's own widgets under its own panels. Every surface asks the same question, so a slot-group panel and a standalone screen's panels block what they cover exactly as an adapter's panel does.

Tooltips go through one MenuKit check before vanilla queues them. To hide every tooltip in the game for a while, register a condition once from client init with `MKTooltip.hideWhen(() -> ...)`. While any registered condition is true, no tooltip draws, a panel's own element tooltips included. Inventory Plus's "hold Ctrl to hide tooltips" setting uses it.

The same check places every tooltip, so a mod can scroll one too. `MKTooltip.onWheel((horizontal, vertical) -> ...)` hears the mouse wheel whenever it turns over a shown tooltip, before anything else sees it and whether or not something then uses it; `MKTooltip.scrollBy(dx, dy)` moves the tooltip on screen by that many pixels (positive y moves it down). MenuKit keeps a scrolled tooltip inside its own extent, so one that fits on screen never moves, and resets the offset when the tooltip's text changes or it goes away (`MKTooltip.resetScroll()` does it by hand). While a tooltip is scrolled with its top above the screen, its first line stays pinned at the top edge and the rest scrolls under it. Inventory Plus's "scroll long tooltips" setting uses it.

Two more switches on the same check keep a tooltip on screen, both off unless a mod registers them from client init. `MKTooltip.wrapWhen(() -> ...)` breaks any text line wider than the screen (less a 4 pixel margin and the frame's padding) onto more lines with vanilla's splitter and keeps the tooltip inside both sides; images such as a bundle's grid are left as they are, and a wrapped first line stays one title, so the scroll pin keeps all of its lines. `MKTooltip.clampWhen(() -> ...)` keeps the tooltip's top on screen, so one taller than the screen starts at its title and runs off the bottom, and `MKTooltip.scrollBy` with a negative y brings up the rest, down to its last line. Inventory Plus's "keep tooltips on screen" setting uses them.

## Targeting

A `ScreenPanelAdapter` with no target renders on every container screen. `.on(Class...)` limits it to those screen classes and their subclasses. `.onAny()` states the default explicitly. `.onPlayerInventory()` limits it to the player inventory screen in both game modes.

A `VanillaScreenPanelAdapter` requires `.on(Class...)` or `.onAny()`.

A `SlotGroupPanelAdapter` requires a target. `.on(SlotGroupCategory...)` renders once per category that resolves in the open menu. `.onGroup(SlotGroupId...)` renders once per named created group. `ContainerPanel.groupId(panelId, groupId)` and `CreatedSlots.groupId(panelId, groupId)` return the id. Categories cover every vanilla menu. A mod with its own menu registers a `SlotGroupResolver` for it.

A panel anchored to a slot group is measured from that one group, not from every slot sharing its category.

Adapters and HUD panels are declarations. Declare them from the mod's initializer: constructing, targeting or unregistering one after the client starts throws, the same as every other MenuKit declaration. A panel that comes and goes at runtime keeps its adapter and gates itself with `visibleWhen(...)`; a hidden panel is not measured, takes no room in its region and claims nothing.

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

A category cannot tell a mod's pockets from the main inventory when both declare `PLAYER_INVENTORY`; a group can. A mod that splits one thing into many groups, one per anchor, puts them in one `SlotGroupSet` with `SlotGroups.declare(id, category, set)`, and the listing shows the set as one row. A choice saved under a set's key reaches every group in it.

Groups and sets are named like operations. `SlotGroups.name(id)` and `name(set)` are translatable on `slot_group.<...>` and `slot_group_set.<namespace>.<path>`; MenuKit ships vanilla's, and a mod ships its own. `SlotGroupId.asString()` and `SlotGroupSet.asString()` are stable text for a config file, with `parse` and a `CODEC` each. A row's `source()` names the mod it comes from, for a screen to show as "Pockets (Inventory Max)": `null` for vanilla's groups, otherwise the display name of the mod whose namespace the row is named in (a set's namespace, a created group's panel id before the colon, a mod's own category). With no loaded mod of that id it is the namespace itself. `SlotGroups.source(id)` answers the same for a group on a live menu.

`SlotGroups.of(slotRef)` names the group of the slot a `SlotRef` describes: a created slot's own group, never its category's, so a veto can tell a pocket from the main inventory; a vanilla slot's group on its menu; and for a player-inventory slot with no menu, as a world pickup has, the vanilla group its index sits in. A created slot needs Containers installed to be told apart.

A container-panel group registers at init and is listed from the title screen. A group built with a menu (`CreatedSlots.onto`) is listed after the first menu that carries it; to list it from the title screen, its mod declares it at init with `SlotGroups.declare`.

Pick a vanilla category when the group is one of those things. A pocket group that declares `PLAYER_INVENTORY` appears in every inventory search run by a mod that has never heard of pockets. Mint a category when no vanilla one gives another mod the right answer: `new SlotGroupCategory("mymod", "pouch")`. A category name is a public contract once another mod depends on it. Renaming one is a breaking change.

A category says what a slot is. An operation says what may be done to it.

## Operation

An operation is something done to a slot. Vanilla ships eleven, split one key per thing a player can do: `CLICK_TAKE`, `CLICK_PUT`, `SHIFT_CLICK_OUT`, `SHIFT_CLICK_IN`, `COLLECT` (double-click), `DRAG_FILL`, `HOTBAR_SWAP`, `OFFHAND_SWAP`, `DROP`, `DROP_STACK`, and `INVENTORY_INSERT`. The last is every item given to the inventory, not only a pickup from the ground. `/give`, pick block and a crafting grid's returns go through the same two vanilla methods. They are `BehaviorKeys.VANILLA_OPERATIONS`, every one on by default, and MenuKit enforces them at vanilla's own seams for every slot kind. A plain click takes, puts, or both when it swaps different items. A swap key has two slots, and both must allow it. The client does not send a click its own slot does not allow, so that part holds on any server. Q, Ctrl-Q and F while playing, with no screen open, are the same `DROP`, `DROP_STACK` and `OFFHAND_SWAP` on the selected hotbar slot, refused by the client before it drops or sends and by the server when the action arrives. An offhand swap also needs the offhand slot to allow it.

An operation has a role: it takes items out of a slot, puts items in, or both. `SlotOperations.define(op, Role.TAKE)` records it and `SlotOperations.role(op)` reads it, so a settings screen can tell which operations matter for a lock on an item already in a slot. `define(op)` without a role means both.

An operation also says which slot groups it can act on at all: `SlotOperations.define(op, role, AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_INVENTORY))`. It names vanilla's groups; every group a mod adds applies automatically, unless the operation opts out with `.except(groupId)` or `.except(slotGroupSet)`. `AppliesTo.vanillaExcept(...)` names the vanilla groups it skips instead, and `AppliesTo.EVERY_GROUP` is what an operation defined without saying gets, so nothing defined earlier changes. `SlotOperations.appliesTo(op, groupId)` answers for one group and `SlotOperations.groups(op)` lists every declared group it applies to: what a reach setting offers. MenuKit declares vanilla's eleven from what vanilla does. Nothing is placed, dragged or shift-clicked into an output slot. The crafter's result slot can't be touched by anything. Double-click collect skips the crafting, smithing, stonecutter, cartography and trade results, whose menus refuse it. An inventory insert lands only in the hotbar, the main inventory and the offhand. This is a declaration, not a rule MenuKit enforces: it tells a settings screen where an operation can reach.

The vocabulary is open. An operation is a `BehaviorKey`. A mod declares the key, publishes it with `SlotOperations.define`, ships a name and a description in its lang file, and asks `SlotOperations.allows` in its own code before acting. `SlotOperations.all()` lists every published operation. `SlotOperations.name(op)` and `description(op)` are translatable components on `slot_operation.<namespace>.<path>` and `.description`, so a settings screen can list them. MenuKit needs no change for a new operation to exist.

Two things decide what a slot allows. The first is the cascade, what the slot's author declared:

```
per-slot declaration  >  the slot's group  >  the group's category  >  the key's default
```

Each level is more specific than the next, so the winner never depends on which mod declared last. `SlotOperations.inherent(category, operation, value)` sets the category rung once. A group's own declaration outranks it. A per-slot declaration outranks the group. A vanilla slot has one group per category on a menu, so for vanilla slots the group and the category are one rung, and "shift-click may land in the 9 by 3 but not the hotbar" is `inherent(PLAYER_HOTBAR, SHIFT_CLICK_IN, FALSE)`.

The second is a veto. `SlotOperations.veto(rule)` registers a rule that can only say no, for a mod that is not the slot's author: a player's lock is the case. A veto sits beside the cascade and subtracts, so it never overwrites what the slot's author declared and has nothing to restore when the lock lifts. A veto that throws is logged once and skipped.

`SlotOperations.allows(menu, slot, player, operation)` is the one question: the cascade says yes and no veto says no. Every seam asks it, and every operation a mod adds asks it too. The `SlotRef` a veto sees carries the container and index, the live slot and menu when there is one, the acting player when there is one, and the slot's category. A move a mod makes on the server with no click behind it, such as a direct `quickMoveStack` call, still names a player: the one whose inventory the menu shows.

A mod that performs its operation by sending clicks wraps them: `SlotOperations.as(take, put, () -> sendClicks())`, or `as(op, ...)` when both sides are one operation. Each click sent inside carries the operation it serves, the first for the slot it takes from and the second for the slot it puts into. The vetoes judge that operation instead of the gesture, so a lock that refuses shift-click but allows restock lets the restock's shift-click through. The slot's author still judges the gesture. The tag reaches the integrated server. Clicks must be sent inside the block, on the calling thread. A simulated click that is not wrapped counts as the gesture it looks like.

Registration order does not matter. Declarations freeze once every mod has initialised. Declare at init. Read during play.

On a `SlotSpec` the per-group form is `shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)` and `dragFill(false)`.

A gate is the slot author's other rule: what the slot accepts and releases, and how many of an item it holds. It is a `SlotGate` on `BehaviorKeys.GATING`, set with `Window.slot(address).gate(...)` or `SlotSpec.gate(...)`, and `BehaviorKeys.BINDING` adds Curse of Binding to any slot. MenuKit enforces both at the slot's own `mayPlace`, `mayPickup` and `getMaxStackSize`, which every click, shift-click and automation path consults, plus the merge pass of a shift-click and the hopper and dispenser seams, once per vanilla method. A gate is not a lock. A lock is another mod's policy laid over a slot, and that is a veto: it only says no and leaves the author's gate alone.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu through Containers. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner: a player, block entity, entity, or item stack.

`ContainerPanel` creates slots on the player's inventory menu and projects them onto every container screen. `CustomContainerMenu` creates slots on a custom menu. `CreatedSlot.of(slot)` tells a created slot from any other, on any screen, creative's included.

Containers has the same two halves as MenuKit, and the compiler enforces the split. What builds slots, stores state and judges writes is common: it runs on the server and the client alike. What draws is client-only: `SlotElement`, `CustomContainerScreen`, and the client halves `ClientMenu`, `ClientContainerPanel` and `ClientSlots`. A custom menu's handler knows each panel's id, its slot groups and whether it is shown; how a panel looks is a MenuKit `Panel`, built by the client screen from `ClientMenu.of(menu).panel(id, ...)`. Whether it is shown is the server's, synced to the client. A player may toggle only a panel declared `toggleable()`, and a client may open a menu only when its `validWhen` holds, which is also how long it stays open.

A server with Containers requires it on every client. Before the world loads, it checks that a joining client has Containers and disconnects one that does not, with a message naming MenuKit and MenuKit: Containers and linking their pages. A client with Containers on a server without it builds no created slots and sends nothing Containers-only, instead of desyncing.

## Slot rendering

Vanilla draws every slot. MenuKit runs no slot pass of its own on container screens. A created slot's panel writes the position into vanilla's `Slot.x` and `Slot.y` before vanilla's slot pass. Vanilla then draws the item, count, hover highlight, and ghost icon for created and vanilla slots alike.

One consequence reaches mods that do not depend on MenuKit. A mod that decorates slots through a mixin on vanilla's slot draw call marks created slots too. It needs no dependency on MenuKit and no interface to adopt. Inventory Plus is the worked example. Its lock icons and item marks appear on Inventory Max's pockets and equipment slots, with no code in either mod for that case.

A panel hides exactly what it covers. Flow panels composite between the vanilla slots and the created slots, inside vanilla's slot pass. A panel over part of a vanilla slot hides that part and no more. A created slot's item still draws over the chrome of the panel hosting it. Overlay panels draw after the slot pass, on top of it. An overlay panel is one that centers, dims behind, or tracks as modal.

`SlotElement` positions a created slot and draws its frame. It does not draw the item.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `Address.createdSlot(group, index)` names a created slot, with the group from `CreatedSlots.groupId(panelId, groupId)` or `ContainerPanel.groupId(...)`. `Window.slot(address).set(key, value)` attaches behaviour by address. A veto reads a slot's address from its `SlotRef` with `address()`. `asString()`, `Address.parse` and `Address.CODEC` save one as text; never save its parts.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `SlotState.register(id, codec, streamCodec, defaultValue, visibility, canWrite)` creates one at common init. Read and write it by address (`get(address)`, `set(address, value)`), or by the live `Slot` when one is at hand (`get(slot)`, `set(slot, value)`, and the forms that take a player). `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`. A value equal to the default is no entry at all, and reading never creates one.

The server judges every slot-state write a client sends, in order: the channel exists; the value parses with the channel's `StreamCodec`, and what is stored is the channel's own re-encoding of it; the write names the player's open menu and an active slot on it; the channel's `canWrite(player, slot)` allows it; the player is within a write rate. A `SHARED` channel's rule defaults to refusing every client, so a shared channel names who may write it. A refused write changes nothing, and the writer's client gets the server's value back. Values travel as the channel's `StreamCodec` bytes, and a client that cannot decode one skips it.

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. `EphemeralStorage.of(size)` builds one that lasts for the menu session. Containers also provides block-scoped and item-scoped attachments.

## One styling change

MenuKit draws a pressed look on every vanilla button and every YACL controller while it is held down, including buttons MenuKit did not create. It is always on and changes nothing but the drawing. It is the one change MenuKit makes to how vanilla looks; the library otherwise leaves vanilla's appearance and behaviour to the mods that use it.
