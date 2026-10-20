# Recipes

One task per section. Each names the artifact it needs. [concepts.md](concepts.md) defines the terms. The build compiles every Java block on this page (the `validator-mk` snippets), so each one matches the API as it ships. Imports are the `api` packages, and your IDE finds them by class name.

## Show a readout on the HUD

Needs: MenuKit. Call from the client entry point.

```java
HudPanel.builder("offshore:boat-health")
        .region(InsideRegion.BOTTOM_CENTER)
        .offset(0, -46)
        .padding(0)
        .style(PanelStyle.NONE)
        .visibleWhen(() -> Minecraft.getInstance().gui.screen() == null && boatActive())
        .bar(0, 0, 90, 7)
            .value(() -> boatHealth())
            .color(0xFFC08040)
            .label(() -> Component.literal("Hull"))
            .done()
        .build();
```

Result: a 7 pixel tall bar renders 50 pixels above the bottom centre of the window while `boatActive()` returns true and no screen is open. `value` is a `DoubleSupplier` in the range 0 to 1.

`.region(InsideRegion)` puts the panel on one of nine spots of the window, 4 pixels in from the edges it touches. It stacks with the other panels on that spot in `.priority(n)` order. `.offset(dx, dy)` nudges this panel alone. Sub-builders (`text`, `item`, `slot`, `bar`) end with `.done()`. `.element(PanelElement)` adds any element, and `.custom(x, y, w, h, ctx -> ...)` draws freely inside the panel.

## Put an icon beside the hotbar

Needs: MenuKit. Call from the client entry point.

```java
HudPanel.builder("mymod:totem-icon")
        .region(OutsideRegion.LEFT_ALIGN_BOTTOM)
        .priority(10)
        .offset(0, -3)
        .visibleWhen(() -> !totemShown().isEmpty())
        .element(ItemDisplay.builder().item(() -> totemShown())
                .size(12, 12).hideCount().hideDurability().build())
        .build();
```

Result: a 12 pixel icon sits 2 pixels left of the vanilla hotbar while `totemShown()` holds a stack, its bottom level with the bottom of the hotbar's items: the region puts it on the hotbar's bottom edge, and `.offset(0, -3)` lifts it the 3 pixels vanilla leaves below the item row. On the HUD an `OutsideRegion` is placed around the hotbar, so `.region(OutsideRegion.X)` reads as "on that side of the hotbar". Icons from several mods in one region stack upward 2 pixels apart in `.priority(n)` order, lowest at the bottom (`.gap(n)` sets that space before one icon, off the hotbar for the first): an elytra icon at `.priority(20)` with the same offset sits just above this one. A hidden one takes no room, so the next drops into its place and nothing overlaps or leaves a gap, whichever mods are installed. Use a distinct priority per icon. While vanilla draws the offhand slot beside the hotbar (the offhand holds something: on the left for a right-handed player, on the right for a left-handed one), the regions on that side start 2 pixels beyond the slot instead, so the icons never cover it.

## Put a button on every container screen

Needs: MenuKit. Call from the client entry point.

```java
Panel p = Panel.builder("mymod:region-everywhere")
        .style(PanelStyle.RAISED)
        .add(TextLabel.builder().text(Component.literal("Region panel"))
                .color(TextLabel.COLOR_LIGHT).shadow(true).build())
        .add(Button.builder().label(Component.literal("Click me")).at(0, 14).size(70, 14)
                .onClick(() -> {})
                .tooltip(Component.literal("A button inside a region panel.")).build())
        .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP).priority(20))
        .build();
new ScreenPanelAdapter(p);
```

Result: the panel renders in the top left gutter of the inventory, every chest, and the creative inventory. Add `.on(InventoryScreen.class)` to limit it to the survival inventory. To show it only some of the time, give the panel a `visibleWhen(...)`. Adapters are declared at init and cannot be added or removed during play.

The panel is opaque by default, so everything under it is inert. A slot it covers does not highlight, click or show a tooltip. `.opaque(false)` makes it see-through, and then only its buttons and other controls block what is behind them.

## Anchor a panel to a rectangle you supply

Needs: MenuKit. Declare the anchor and the adapter from the client entry point.

```java
public static final Anchor PAUSE_BUTTONS = Anchor.of(
        Identifier.fromNamespaceAndPath("mymod", "pause_buttons"),
        screen -> screen instanceof PauseScreen ? buttonsBox(screen) : null);

static void pauseRow(List<PanelElement> rowButtons) {
    Panel row = Panel.builder("mymod:pause_row")
            .style(PanelStyle.NONE)
            .elements(rowButtons)
            .position(PanelPosition.region(OutsideRegion.BOTTOM_CENTER))
            .build();
    new AnchorPanelAdapter(row).on(PAUSE_BUTTONS);
}

// The box around every widget on the screen, or null when it has none.
static Reference buttonsBox(Screen screen) {
    int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
    for (var child : screen.children()) {
        if (!(child instanceof AbstractWidget w)) continue;
        left = Math.min(left, w.getX());
        top = Math.min(top, w.getY());
        right = Math.max(right, w.getRight());
        bottom = Math.max(bottom, w.getBottom());
    }
    return left > right ? null : new Reference(left, top, right - left, bottom - top);
}
```

Result: on the pause screen the row sits centred under the button column, and moves with it when the window resizes. On every other screen the box is `null`, so the row takes no place and claims nothing. Declare an anchor once and pass it to every adapter that places against it; panels on one anchor stack in its regions. SlotKit's slot-group panels are anchors built this way.

## Pop a panel up over the others

Needs: MenuKit.

```java
Panel row = Panel.builder("mymod:popup_row")
        .popover()
        .elements(rowSlots)
        .position(PanelPosition.pixel(() -> aboveHoveredSlot()))
        .build();
new ScreenPanelAdapter(row).onPlayerInventory();
```

Result: while `aboveHoveredSlot()` returns a point, the row draws over every other panel on the screen, another mod's anchored panels included, whichever registered first. Input follows what is drawn. Hover, clicks, the wheel and tooltips over the row go to the row, never to a panel under it. Its own slots stay live. A modal dialog still opens above it.

## Build a full-screen tabbed menu

Needs: MenuKit.

```java
private static String tab = "general";   // yours to keep, and to set before opening
```

```java
Tabs tabs = Tabs.builder()
        .mode(Tabs.Mode.WRAP)
        .align(Tabs.Align.FILL)
        .state(() -> tab, id -> tab = id)
        .tab("general", Component.literal("General"), generalBody)
        .tab(Tabs.tab("pockets")
                .label(Component.literal("Pockets"))
                .visibleWhen(() -> config.showPockets)
                .body(pocketsBody))
        .build();

Panel main = Panel.builder("mymod:settings")
        .style(PanelStyle.RAISED)
        .position(PanelPosition.main())
        .add(tabs)
        .build();
```

Result: a strip across the top of the screen that wraps into rows, with the selected tab's body below it filling the rest of the screen. A body is a `List<PanelElement>`, the same as `ScrollContainer` content. Put the panel in an `MKScreen` subclass. To open the menu on a given tab, set `tab` before `setScreen`. A screen that rebuilds itself when a setting changes can keep each body's scroll too. Hold a `Map<String, Double>` beside `tab` and pass `.bodyScroll(scroll::get, scroll::put)`. Hiding a tab re-splits the rows and moves the body. For one scrolling row, use `Tabs.Mode.SIDE_SCROLL`.

## Add a tab to another mod's menu

Needs: MenuKit. The owner names the menu, and you add to it at your client init.

```java
// Owner, when its settings screen opens
Tabs.builder()
        .menu(Identifier.fromNamespaceAndPath("ownermod", "settings"))
        .state(() -> tab, id -> tab = id)
        .tab("general", Component.literal("General"), generalBody)
        .tab(Tabs.tab("pockets").label(Component.literal("Pockets")).standIn()
                .body(() -> List.of(TextLabel.builder()
                        .text(Component.literal("Install My Mod to use Pockets.")).build())))
        .build();

// Your mod, client init
Tabs.addTo(Identifier.fromNamespaceAndPath("ownermod", "settings"),
        Tabs.tab("pockets").label(Component.literal("Pockets")).body(() -> pocketSettings()));
Tabs.addTo(Identifier.fromNamespaceAndPath("ownermod", "settings"),
        Tabs.tab("mending").label(Component.literal("Mending")).after("pockets").body(() -> mendSettings()));
```

Result: your Pockets tab replaces the owner's stand-in in the same place, and Mending sits right after it. Your bodies are built each time the menu opens and read your own config. Without your mod, the owner's Pockets stand-in shows with a dimmed label. The owner builds its `Tabs` when its screen opens, so every tab added at init is in it.

## Lay out elements in a row

Needs: MenuKit.

```java
List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
        .add(Button.builder().label(Component.literal("OK")).size(60, 20).onClick(() -> confirm()).build())
        .add(Button.builder().label(Component.literal("Cancel")).size(60, 20).onClick(() -> cancel()).build())
        .build();

Panel p = Panel.builder("mymod:confirm").elements(buttonRow).position(PanelPosition.center()).build();
```

Result: two 60 by 20 buttons at y 30, starting at x 20, with a 4 pixel gap. `Column` has the same shape on the vertical axis. `.crossAlign(CrossAlign.CENTER)` centres children on the cross axis. A hidden element keeps its space. `Row` places once, on the panel's first layout, and does not follow the panel after that. For a row that follows the panel's width, use a `Flow` (next recipe).

## Build a settings body: label left, control right, greyed while off

Needs: MenuKit. The body of a settings tab, built by the tab's body factory.

```java
List<PanelElement> body = new ArrayList<>();
BooleanSupplier off = () -> !config.enabled();

// A header row: the feature switch at the left, Reset pinned to the right edge at any width.
body.add(Flow.builder().at(0, 0)
        .add(Toggle.builder().state(config::enabled, config::setEnabled)
                .label(() -> Component.literal(config.enabled() ? "On" : "Off")).size(40, 16).build())
        .add(Flow.spacer())
        .add(Button.builder().label(Component.literal("Reset to Defaults")).size(0, 16)
                .onClick(config::reset).build())
        .build());
// A rule as wide as the body.
body.add(Divider.horizontal().at(0, 22).build());
// A setting: its label at the left, level with the middle of its control at the right,
// both grey while the feature is off.
body.add(Flow.builder().at(0, 28).disabledWhen(off).crossAlign(CrossAlign.CENTER)
        .add(TextLabel.builder().text(Component.literal("Rows")).build())
        .add(Flow.spacer())
        .add(Slider.ofInts(1, 6).size(100, 16).state(config::rows, config::setRows).build())
        .build());
body.add(Flow.builder().at(0, 50).disabledWhen(off).crossAlign(CrossAlign.CENTER)
        .add(TextLabel.builder().text(Component.literal("Speed")).build())
        .add(Flow.spacer())
        .add(Dropdown.ofEnum(Speed.class).size(80, 16).state(config::speed, config::setSpeed).build())
        .build());
```

Result: the switch sits at the left and Reset at the right edge of the tab body. Each setting's control sits at the right edge beside its label, whatever the window's width. When the window is too narrow the control drops under its label. The divider spans the body. While the feature is off, every setting row is grey and ignores the mouse and keys. `Panel.disabledWhen(...)` greys a whole panel the same way.

## A settings screen in the Default look

Needs: MenuKit 6.1.0. This follows the shape of Inventory Plus's settings. It has the mod's name at the top, tabs across, sections of settings, items the player picks, and Done at the bottom. It draws as Options, Video Settings and Controls do, not as an inventory. The screen declares the Default look once, and every element on it draws in that look.

```java
public final class ModSettingsScreen extends MKScreen {
    private static String tab = "general";

    public ModSettingsScreen(Screen parent) {
        super(Component.literal("My Mod"), ScreenKind.DEFAULT, List.of(menu(), done(parent)));
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    // The tabs across the top and their pages. No box, so the blurred world shows.
    private static Panel menu() {
        Tabs tabs = Tabs.builder()
                .mode(Tabs.Mode.SIDE_SCROLL)
                .state(() -> tab, id -> tab = id)
                .tab(Tabs.tab("general").label(Component.literal("General")).body(() -> general()))
                .tab(Tabs.tab("locks").label(Component.literal("Locked items")).body(() -> lockedItems()))
                .build();
        return Panel.builder("mymod:settings")
                .style(PanelStyle.NONE)
                .position(PanelPosition.main())
                .add(tabs)
                .build();
    }

    // Done at the bottom centre, the way every Options screen ends.
    private static Panel done(Screen parent) {
        return Panel.builder("mymod:settings-done")
                .style(PanelStyle.NONE)
                .position(PanelPosition.region(OutsideRegion.BOTTOM_CENTER))
                .add(Button.builder().label(CommonComponents.GUI_DONE).size(200, 20)
                        .onClick(() -> Minecraft.getInstance().gui.setScreen(parent)).build())
                .build();
    }

    // A section of settings: each its name at the left and its control at the right edge.
    private static List<PanelElement> general() {
        return List.of(Section.builder(Component.literal("Sorting")).open(true)
                .summary(() -> Component.literal(config.rows() + " rows"))
                .add(setting(0, "Sort button", Toggle.builder().size(60, 20)
                        .state(config::enabled, config::setEnabled).build()))
                .add(setting(24, "Rows", Slider.ofInts(1, 6).size(120, 20)
                        .state(config::rows, config::setRows).build()))
                .add(setting(48, "Speed", Dropdown.ofEnum(Speed.class).size(120, 20)
                        .state(config::speed, config::setSpeed).build()))
                .build());
    }

    // Items as setting values: a picker per place in the list. Click chooses, right-click empties.
    private static List<PanelElement> lockedItems() {
        Flow.Builder slots = Flow.builder().at(0, 14).gap(2, 2);
        for (int i = 0; i < 9; i++) {
            int place = i;
            slots.add(ItemPicker.builder()
                    .state(() -> config.lockedItem(place), item -> config.setLockedItem(place, item))
                    .build());
        }
        return List.of(
                TextLabel.builder().text(Component.literal("Items sorting leaves alone")).role(TextRole.MUTED).build(),
                slots.build());
    }

    private static PanelElement setting(int y, String name, PanelElement control) {
        return Flow.builder().at(0, y).crossAlign(CrossAlign.CENTER)
                .add(TextLabel.builder().text(Component.literal(name)).build())
                .add(Flow.spacer())
                .add(control)
                .build();
    }
}
```

Open it with `Minecraft.getInstance().gui.setScreen(new ModSettingsScreen(Minecraft.getInstance().gui.screen()))`. Result: "My Mod" in white at the top centre, over the blurred world (the panorama on the title screen). Under it, Create World's tab bar runs across the whole width. With more tabs than fit, it scrolls with arrows. The page is the darker list band between two thin lines. The section header is centred as a category on Controls is, and its rows are white text. The toggle is a stone button reading ON or OFF, and the slider and dropdown are vanilla's own. Locked items shows nine slots in vanilla's slot sprite. A click opens the item dialog over the screen, with a search box and every item. A click on an item in the grid fills the selected slot, and a right click on the page empties a slot. The items are setting values, so nothing is taken from the inventory or sent to the server. Done at the bottom and Escape both return to the screen it opened from. Nothing on it picks a look.

A dialog on this screen (a `ConfirmDialog` for Reset to Defaults, for example) is in the same look. A `RAISED` panel draws vanilla's popup frame, with white text, and keeps the same 4 px between that frame and its elements as inside MenuKit's own frame (the popup frame is 3 px thicker, so the panel is 6 px bigger). Keep labels on roles (next recipe) rather than `TextLabel.COLOR_DARK`, which reads only on the Panel look's grey. A screen that declares no look (`super(title, panels)`) keeps the Panel look, MenuKit's grey. Panels injected into vanilla's own screens need nothing, because a panel on Options or Controls is already in the Default look.

## Colour text by role

Needs: MenuKit 6.1.0.

```java
List<PanelElement> body = new ArrayList<>();
body.add(TextLabel.builder().text(Component.literal("Sorting")).role(TextRole.HEADING).build());
body.add(TextLabel.builder().at(0, 14).text(Component.literal("Rows")).build());
body.add(TextLabel.builder().at(0, 26).text(Component.literal("Applies to chests too."))
        .role(TextRole.MUTED).build());
```

Result: the heading and "Rows" are dark grey in the Panel look and white in the Default look. The note is mid grey on the first and vanilla's grey on the second. A label without a role is `BODY`. Disabled text takes the look's disabled grey whatever its role. `color(argb)` and `shadow(b)` fix a value in both looks instead, for text over the world on the HUD. `TextLabel.COLOR_DARK` is right only on the Panel look's grey.

## Outline an item in its group's colour

Needs: MenuKit.

```java
// In a slot decoration, where the item would be drawn:
SlotRendering.drawItemOutline(graphics, stack, x, y, 0xFFE0A030);

// Or as an element:
ItemDisplay.builder().item(() -> stack).outline(() -> lockColour(stack)).build();
```

Result: the item is drawn with a one pixel line of that colour around its own shape (not its square). A colour of 0 draws the item alone.

## Handle a right click and tint a button

Needs: MenuKit.

```java
Button pin = Button.builder().label(Component.literal("Mode")).size(60, 16)
        .onClick(() -> cycleMode())
        .onSecondaryClick(click -> {
            if (click.isShiftRight()) clearMode();
            else if (click.isRight()) cycleModeBackward();
        })
        .tint(() -> pinned ? 0x50FFC000 : 0)
        .build();
```

Result: left click runs `cycleMode`. Right click runs `cycleModeBackward`, and shift with right click runs `clearMode`. While `pinned` is true an amber wash fills the button under its label. `Toggle` takes the same two builder methods with the same contract.

A control with no `onSecondaryClick` handler passes non-left clicks to vanilla. A disabled control consumes neither kind.

## Hide item tooltips separately

Needs: MenuKit 7.1.0. Call from the client entry point.

```java
MKTooltip.hideWhen(kind -> kind == MKTooltip.Kind.ITEM && config.hideItemTooltips());
MKTooltip.hideWhen(kind -> kind == MKTooltip.Kind.OTHER && config.hideOtherTooltips());
```

Result: while `hideItemTooltips()` returns true, no item stack shows a tooltip: slots on every container screen, the creative menu, the recipe book, an item in chat. Buttons, tabs and MenuKit elements still show theirs. While `hideOtherTooltips()` returns true it is the other way round. Each tooltip asks every registered predicate with its kind, and any one returning true hides it. `MKTooltip.hideWhen(() -> ...)` with no kind still hides both.

A tooltip is `ITEM` when it comes through vanilla's item paths, which other mods' item tooltips use too: `setTooltipForNextFrame(font, stack, x, y)`, a container slot, or lines from `Screen.getTooltipFromItem`. A mod that builds its own lines from `ItemStack.getTooltipLines` and queues them as plain lines draws an `OTHER` tooltip.

## Host a widget another library made

Needs: MenuKit 7.2.0. For a widget built outside MenuKit, such as a config library's custom control or a vanilla widget MenuKit has no element for.

```java
// A widget a config library hands over, in a row of your settings page.
PanelElement custom = WidgetHost.builder()
        .widget(() -> library.makeWidget())
        .size(150, 20)
        .build();
```

Result: the widget draws in the element's 150 by 20 box wherever the panel puts it, and moves with the panel and its scroll. Clicks, drags, the wheel and the release reach it on a standalone screen and on a panel over a vanilla screen alike. A click on it gives it keyboard focus, so it takes typed keys, Tab moves focus on and off it, and the narrator reads it. While its panel is hidden or its tab not shown, it gives up focus and Tab skips it. The supplier runs once, when the screen opens, so the widget can use the game's font.

A widget that is not a vanilla `LayoutElement` needs a `place` hook to be moved. A widget that opens a list under itself needs `overflow()` to show it:

```java
PanelElement dropdown = WidgetHost.builder()
        .widget(() -> library.makeDropdown())
        .place((widget, x, y, width, height) -> widget.setBounds(x, y, width, height))
        .size(150, 20)
        .overflow()
        .build();
```

Result: `place` is called every frame with the box in screen coordinates. While the widget has keyboard focus it draws on top of every panel, past its box, and takes clicks anywhere on the screen. A click it does not take ends its focus, which closes its list.

## A colour setting

Needs: MenuKit 7.2.0.

```java
PanelElement glow = ColorField.builder()
        .label(Component.literal("Glow color"))
        .state(config::glowColor, config::setGlowColor)
        .alpha(true)
        .build();
```

Result: a swatch of the colour, over a checkerboard because it has alpha. Clicking it opens a dialog over the screen, titled Glow color. The Basic tab shows the 16 dye colours by their dye items in three rows, in rainbow order, and clicking one sets the colour. The Custom tab has a saturation and brightness square, a hue bar, a transparency bar and a hex field. Every change is written at once. Done keeps it, and Cancel or Escape puts back the colour from before the dialog opened. Without `alpha(true)` the value is `0xRRGGBB` and there is no transparency bar.

## An item setting

Needs: MenuKit 7.2.0, and 7.3.0 for `ignoring` and `ignoringCount`.

```java
// One item, the kind only.
PanelElement lockedItem = ItemPicker.builder()
        .state(config::lockedItem, config::setLockedItem)
        .build();

// A list of exact stacks, edited in full.
PanelElement kit = ItemPicker.builder()
        .label(Component.literal("Starting kit"))
        .stacks(config::kit, config::setKit)
        .match(ItemMatch.Match.EXACT)
        .build();

// A list the player matches per slot: "Any Diamond Sword" or "Exactly this".
PanelElement locks = ItemPicker.builder()
        .label(Component.literal("Locked items"))
        .rules(config::locks, config::setLocks)
        .match(ItemMatch.Match.CONTAINS)
        .playerChoosesMatch()
        .build();

// Exact items that still match once worn, in any count (7.3.0).
PanelElement exactLocks = ItemPicker.builder()
        .label(Component.literal("Exact locks"))
        .rules(config::exactLocks, config::setExactLocks)
        .match(ItemMatch.Match.EXACT)
        .ignoring(DataComponents.DAMAGE)
        .ignoringCount()
        .build();

// In play, one matcher for every mode.
boolean locked = config.locks().stream().anyMatch(rule -> rule.matches(stack));
```

Result: one slot with the item, and three rows of slots, each with an empty slot marked with a plus at the end. Clicking a slot opens a dialog over the screen: the setting's slots at the top, then a search box over every item and variant. Click a slot in the row to select it, then click an item in the grid to put it there. The small minus in a filled slot's corner removes it, on the page and in the dialog. The two lists have an Edit tab for the selected slot: name, count, enchantments that fit the item, the other components in `/give` syntax, and for the locks the choice between Any and Exactly. The exact locks have no count, and their rules leave damage out, so a locked sword stays locked as it wears. The first picker never shows a count or an Edit tab. Every change is written at once. Cancel or Escape puts back the value from before. Save a rule with `ItemMatch.CODEC`.

## A list that changes while the screen is open

Needs: MenuKit 7.3.0. For a page of things the player adds, renames and deletes while it is open: worlds, sandboxes, saved groups.

```java
// A page: a title and a New button pinned above a list that scrolls by itself.
ListView<Sandbox> list = ListView.<Sandbox>builder()
        .items(sandboxes::list)
        .key(Sandbox::id)
        .row(sandbox -> sandboxRow(sandbox.id()))
        .selection(() -> selectedId, id -> selectedId = id)
        .empty(TextLabel.builder().text(Component.literal("No sandboxes yet")).build())
        .build();
PanelElement page = Flow.builder().vertical().gap(6)
        .add(TextLabel.builder().role(TextRole.HEADING).text(Component.literal("Sandboxes")).build())
        .add(Button.builder().label(Component.literal("+ New")).onClick(sandboxes::create).build())
        .add(ScrollContainer.builder().fitWidth().fillHeight().content(List.of(list)).build())
        .build();
```

Each row reads what can change by its id, renames in place and keeps its actions in a menu:

```java
static PanelElement sandboxRow(String id) {
    TextField rename = TextField.builder().size(120, 18)
            .state(() -> draft, v -> draft = v)
            .onSubmit(name -> { sandboxes.rename(id, name); renaming = null; })
            .onCancel(() -> renaming = null)
            .visibleWhen(() -> id.equals(renaming))
            .build();
    return Flow.builder().boxed().gap(4)
            .add(TextLabel.builder().text(() -> Component.literal(sandboxes.name(id)))
                    .visibleWhen(() -> !id.equals(renaming)).build())
            .add(rename)
            .add(Flow.spacer())
            .add(Button.builder().label(Component.literal("Rename"))
                    .visibleWhen(() -> id.equals(selectedId))
                    .onClick(() -> { draft = sandboxes.name(id); renaming = id; rename.focus(); })
                    .build())
            .add(MenuButton.builder().size(20, 20)
                    .action(Component.literal("Duplicate"), () -> sandboxes.duplicate(id))
                    .danger(Component.literal("Delete"), () -> sandboxes.delete(id))
                    .build())
            .build();
}
```

Result: one card per sandbox. Creating, duplicating or deleting one adds or removes its card at once, and the other cards stay as they are, a rename in progress included. Clicking a card's empty space selects it and shows its Rename button; Up and Down move the selection after that. Rename turns the name into a text field that already has the keyboard: Enter keeps the new name, Escape puts the old one back and leaves the screen open. The "..." button opens a menu whose Delete is red. The title and New stay where they are while the cards scroll under them. Put `page` in a standalone screen's main panel or in a `Tabs` page; it looks and works the same in both.

## A list the player adds to, removes from and reorders

Needs: MenuKit 7.3.0. For a short list of things with their own settings: lock groups, saved layouts, waypoint groups.

```java
// Three kinds to add, each saying what it is on hover; remove and the arrows act on the selected group, the two defaults pinned.
ListView<LockGroup> groupList = ListView.<LockGroup>builder()
        .items(groups::all)
        .key(LockGroup::id)
        .row(group -> groupRow(group.id()))
        .title(() -> Component.literal("Lock groups  ")   // the heading, on the toolbar's line
                .append(Component.literal(groups.all().size() + " groups").withStyle(ChatFormatting.GRAY)))
        .add(Component.literal("+ Slot group"), Component.literal("Locks the slots you pick"), groups::addSlotGroup)
        .add(Component.literal("+ Item group"), Component.literal("Locks the items you pick"), groups::addItemGroup)
        .add(Component.literal("+ Tag group"), Component.literal("Locks every item in a tag"), groups::addTagGroup)
        .remove(Component.literal("- Delete group"), groups::delete)
        .confirmRemove(g -> Component.literal("Delete " + g.name() + "? Its " + g.locks() + " locks move to Slot lock."))
        .move(groups::move)
        .pinned(LockGroup::isDefault)
        .build();
// The toolbar above the scroll area stays put while the groups scroll under it.
return Flow.builder().vertical().gap(2)
        .add(groupList.toolbar())
        .add(ScrollContainer.builder().fitWidth().fillHeight().content(List.of(groupList)).build())
        .build();
```

Each row is a card holding a section that opens on the group's settings. The list builds a row once per key, so the section reads its title and swatch every frame: a rename or a recolour shows on the same row, with no rebuild. The row is the mod's own: its toggle sits at the right end because a `Flow.spacer()` comes before it, and `crossAlign(CrossAlign.CENTER)` centres the label on the toggle's line.

```java
static PanelElement groupRow(String id) {
    return Flow.builder().boxed()
            .add(Section.builder(() -> Component.literal(groups.name(id)))
                    .swatch(() -> groups.color(id))
                    .summary(() -> Component.literal(groups.locks(id) + " locks"))
                    .add(Flow.builder().crossAlign(CrossAlign.CENTER)
                            .add(TextLabel.builder().text(Component.literal("Show in tooltips")).build())
                            .add(Flow.spacer())
                            .add(Toggle.builder().size(40, 20)
                                    .label(() -> groups.inTooltips(id) ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)
                                    .state(() -> groups.inTooltips(id), on -> groups.setInTooltips(id, on))
                                    .build())
                            .build())
                    .build())
            .build();
}
```

Result: one line with "Lock groups" and a grey count at its left and + Slot group, + Item group, + Tag group, - Delete group and up and down arrows at its right end, over a list of cards. The count changes as groups come and go, and a heading too long for the line is cut with "..." before the first button, shown whole while hovered. `title(heading, tooltip)` gives the heading a hover tooltip of its own, a config option's description say, fixed or read while hovered; it shows instead of the whole heading when the heading is cut, as a `Section` with a tooltip does. Without `title(...)` the buttons sit alone at the right. Hovering an add button shows its tooltip, saying what that kind of group locks; `add(label, () -> tooltip, action)` takes one read while hovered instead. Clicking anywhere on a card selects it: a white outline in the Default look, a lighter card in the Panel look. Delete and the arrows stay grey until a group is selected, and stay grey for the two default groups, for up on the top movable group and down on the bottom one. Delete asks first, naming the group and its lock count, and then selects the group that took its place, so a second click deletes the next. Alt+Up and Alt+Down move the selected group. An added group appears at the bottom, scrolled into view. A group's card opens in place and stays open through renames and refreshes.

## A config list of values, each its own text field

Needs: MenuKit 7.3.0. For a short list where every row is a value to edit: names, commands, item ids.

```java
// Each row is a text field; up, down and remove sit in the row, add in the toolbar.
return ListView.<Entry>builder()
        .items(names::all)
        .key(Entry::id)
        .row(entry -> TextField.builder().size(120, 20).fill()
                .state(() -> names.text(entry.id()), text -> names.setText(entry.id(), text))
                .build())
        .add(Component.literal("+ Add name"), names::add)
        .remove(names::remove)
        .removeDisabledWhen(() -> names.all().size() <= 1)   // never fewer than one
        .move(names::move)
        .controlsInRows()
        .build();
```

Result: one text field per value, each with a thin divider line after it and then up, down and a remove cross. Nothing needs selecting: the first row's up and the last row's down are grey, and the cross removes its row at once. With one name left every cross is grey, so the list never empties; `addDisabledWhen` caps the other end the same way. + Add name in the toolbar adds an empty row at the bottom. In a card row the controls sit inside the card after the same divider. Placed in the middle of a page, the list pushes whatever is below it down as rows are added and pulls it back up as they go: place the next element as if the list took no room, as after a closed `Section`.

## A list the player searches and sorts

Needs: MenuKit 7.3.0. For a long list the player looks things up in: waypoints, saved worlds, recipes.

```java
// A search field, and a sort picker with its direction, remembered by the mod.
return ListView.<Waypoint>builder()
        .items(waypoints::all)
        .key(Waypoint::id)
        .row(w -> Flow.builder().boxed().gap(6).crossAlign(CrossAlign.CENTER)
                .add(TextLabel.builder().text(Component.literal(w.name())).build())
                .add(Flow.spacer())
                .add(TextLabel.builder().role(TextRole.MUTED).text(() -> Component.literal(waypoints.distance(w.id()) + " m")).build())
                .build())
        .search(Waypoint::name)
        .sort(Component.literal("Name"), Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER))
        .sort(Component.literal("Distance"), Comparator.comparingInt((Waypoint w) -> waypoints.distance(w.id())))
        .sortState(() -> waypointSort, sort -> waypointSort = sort)
        .empty(TextLabel.builder().role(TextRole.MUTED).text(Component.literal("No waypoints yet")).build())
        .build();
```

Result: a toolbar with a search field taking the free width, a "Sort: Name" picker and a direction button. Typing keeps the rows whose name holds the text, in any case; text nothing holds shows "No matches", and an empty list shows "No waypoints yet". Picking Distance reorders the rows, the direction button reverses them, and the choice is still there the next time the screen opens. Place `list.toolbar()` above the scroll area as in the first recipe. A list can't both sort and move: `build()` throws.

## Edit a row in a dialog from its own button

Needs: MenuKit 7.3.0. For a row whose settings are too many for the row and should only take effect on Save.

```java
static PanelElement saveRow(String id) {
    // The row: the save's name, and its own Edit button at the right end.
    return Flow.builder().boxed().gap(6).crossAlign(CrossAlign.CENTER)
            .add(TextLabel.builder().text(() -> Component.literal(saves.name(id))).build())
            .add(Flow.spacer())
            .add(Button.builder().label(Component.literal("Edit")).size(40, 20)
                    .onClick(() -> { saveDraft.name = saves.name(id); saveDraft.locked = saves.locked(id); editingSave = id; })
                    .build())
            .build();
}
```

The dialog is built once and added with the screen's other panels, as a `ConfirmDialog` is:

```java
static Panel editSaveDialog() {
    // Its controls are lenses on the draft; Save writes the draft back, Cancel and Escape drop it.
    return EditDialog.builder()
            .title(() -> Component.literal("Edit " + saves.name(editingSave)))
            .content(Flow.builder().vertical().gap(4)
                    .add(TextLabel.builder().text(Component.literal("Name")).build())
                    .add(TextField.builder().size(160, 20).state(() -> saveDraft.name, v -> saveDraft.name = v).build())
                    .add(Toggle.builder().size(160, 20)
                            .label(() -> Component.literal(saveDraft.locked ? "Locked" : "Not locked"))
                            .state(() -> saveDraft.locked, v -> saveDraft.locked = v)
                            .build())
                    .build())
            .onSave(() -> { saves.update(editingSave, saveDraft.name, saveDraft.locked); editingSave = null; })
            .onCancel(() -> editingSave = null)
            .build()
            .visibleWhen(() -> editingSave != null);
}
```

Result: each card has an Edit button at its right end. Edit opens a dialog over the dimmed screen, titled with the save's name, as wide as its fields, with Cancel and Save centred under them. Changes in the dialog touch only the draft: the card behind it doesn't change until Save, and Cancel or Escape closes it with the save as it was.

## Show a panel on the screens that hold your slots

Needs: MenuKit 7.3.0. For a panel of slot elements whose slots the server adds to some menus and not others.

```java
// Show your panel on exactly the screens whose menu holds your slots.
new ScreenPanelAdapter(panel).onMatching(ScreenMatcher.where(screen -> hasMySlots(screen.getMenu())));
```

Result: the test runs once as each container screen opens, when its menu already holds every slot, and the panel is on exactly the screens that pass. Gate it further at runtime with `visibleWhen`. A slot element whose slot is not in the menu reports itself hidden and takes no room, and a panel with nothing left to show is not placed at all, so a mismatch shows nothing rather than an empty frame.

## A panel behind everything on a container screen

Needs: MenuKit 7.4.0. For a panel that stands in for part of the screen and must never get in another mod's way.

```java
// A panel drawn as the screen's background: the screen's slots, other mods' buttons and every other panel draw over it, and it blocks none of them.
Panel tray = Panel.builder("mymod:tray").style(PanelStyle.RAISED).background().add(trayLabel)
        .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)).build();
new ScreenPanelAdapter(tray).on(ContainerScreen.class);
```

Result: the tray draws right where the chest screen draws its own image, before the screen's widgets, labels and slots, so all of them, and every other mod's widgets and panels, draw over it. It claims nothing: a button another mod puts on it stays live and takes its own clicks. Its own elements get a click only when nothing on the screen took it, and the wheel only when nothing else used it. Slots it shows draw with it, right after it, so they are over the tray and under every widget: a button over one hides it, and is what the mouse is over there. The container region's two panels are built this way.

## Stack containers in one scrolling column above the inventory

Needs: MenuKit 7.4.0, and a slot library's elements for the grids (SlotKit's `SlotElement`). For a mod that opens more containers on a chest's screen, the way Shulkers in Inventory opens a shulker box below the chest.

```java
// The chest's own grid, then the box the player opened below it: one column above the inventory.
ContainerRegion.section("mymod:chest")
        .content(chestGrid)
        .on(ContainerScreen.class)
        .visibleWhen(() -> session.isOpen())
        .declare();
ContainerRegion.section("mymod:opened-box")
        .content(boxGrid)
        .on(ContainerScreen.class)
        .order(10)
        .visibleWhen(() -> session.isOpen())
        .tint(() -> ContainerRegion.dyeTint(session.color()))   // the opened box's dye; none undyed
        .declare();
```

Result: while the session is open, the chest's screen is two panels: the chest and the box as one column in the container list, and the player's inventory in its own panel below it. The column grows before it scrolls: its top stays where vanilla's is and the inventory panel moves down below it until it reaches the screen's bottom, then the column grows up into the room above, and it scrolls only once the screen is full, by the wheel anywhere nothing else wants it, by its scrollbar (inside the list's frame), and by carrying an item to its top or bottom edge. A shift-click into the box while it is scrolled out of view scrolls to where the item landed. Each grid is a title over nine-wide rows of slot elements, the title 11 pixels above the first row as vanilla's is; `chestGrid` presents the chest's own slots, since the container list takes the place of the screen's own grid. Both panels are the screen's background, and the slots draw with the list, so other mods' buttons on them draw over them, slots included, and keep their clicks. Another mod's sections on the same screen join the same column, by their `order`. The screen's bounds are the rectangle around both panels, so panels on the menu frame and other mods' buttons by the screen's edges go around both; when the box opens or closes while the screen is open, the screen is laid out again as a window resize lays it out, so those buttons follow. The box's section is in its dye's colour, over its title and slot frames and under its items; `tint(DyeColor.PURPLE)` or `tint(argb)` fixes one instead.

A grid from a slot library can decide the section by itself: a section shows only while its content is visible, so with content that hides itself when its slot group is not on the open menu, or not revealed (SlotKit's slot elements do), the section needs no `visibleWhen`.

To put a button by one of the two panels rather than by the whole screen, place it on the panel's anchor:

```java
// A sort button beside the container list's top, and one beside the inventory panel's.
Panel sortChest = Panel.builder("mymod:sort-chest").style(PanelStyle.RAISED).add(sortChestButton)
        .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)).build();
new AnchorPanelAdapter(sortChest).on(ContainerRegion.CONTAINERS);
Panel sortInventory = Panel.builder("mymod:sort-inventory").style(PanelStyle.RAISED).add(sortInventoryButton)
        .position(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)).build();
new AnchorPanelAdapter(sortInventory).on(ContainerRegion.INVENTORY);
```

Result: each button stays by its panel as the container list grows, shrinks and scrolls, and is gone on screens the region is not on.
