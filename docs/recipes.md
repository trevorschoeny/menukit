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
// A setting: its label at the left, its control at the right, both grey while the feature is off.
body.add(Flow.builder().at(0, 28).disabledWhen(off)
        .add(TextLabel.builder().text(Component.literal("Rows")).build())
        .add(Flow.spacer())
        .add(Slider.ofInts(1, 6).size(100, 16).state(config::rows, config::setRows).build())
        .build());
body.add(Flow.builder().at(0, 50).disabledWhen(off)
        .add(TextLabel.builder().text(Component.literal("Speed")).build())
        .add(Flow.spacer())
        .add(Dropdown.ofEnum(Speed.class).size(80, 16).state(config::speed, config::setSpeed).build())
        .build());
```

Result: the switch sits at the left and Reset at the right edge of the tab body. Each setting's control sits at the right edge beside its label, whatever the window's width. When the window is too narrow the control drops under its label. The divider spans the body. While the feature is off, every setting row is grey and ignores the mouse and keys. `Panel.disabledWhen(...)` greys a whole panel the same way.

## A settings screen in the pause-menu look

Needs: MenuKit 6.1.0. This follows the shape of Inventory Plus's settings. It has the mod's name at the top, tabs across, sections of settings, items the player picks, and Done at the bottom. It draws as Options, Video Settings and Controls do, not as an inventory. The screen declares once that it is an options screen, and every element on it draws for that kind.

```java
public final class ModSettingsScreen extends MKScreen {
    private static String tab = "general";

    public ModSettingsScreen(Screen parent) {
        super(Component.literal("My Mod"), ScreenKind.OPTIONS, List.of(menu(), done(parent)));
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
        return Flow.builder().at(0, y)
                .add(TextLabel.builder().text(Component.literal(name)).build())
                .add(Flow.spacer())
                .add(control)
                .build();
    }
}
```

Open it with `Minecraft.getInstance().gui.setScreen(new ModSettingsScreen(Minecraft.getInstance().gui.screen()))`. Result: "My Mod" in white at the top centre, over the blurred world (the panorama on the title screen). Under it, Create World's tab bar runs across the whole width. With more tabs than fit, it scrolls with arrows. The page is the darker list band between two thin lines. The section header is centred as a category on Controls is, and its rows are white text. The toggle is a stone button reading ON or OFF, and the slider and dropdown are vanilla's own. Locked items shows nine slots in vanilla's slot sprite. A click opens the item dialog over the screen, with a search box and every item; a click on an item in the grid fills the selected slot, and a right click on the page empties a slot. The items are setting values, so nothing is taken from the inventory or sent to the server. Done at the bottom and Escape both return to the screen it opened from. Nothing on it picks a look.

A dialog on this screen (a `ConfirmDialog` for Reset to Defaults, for example) is the same kind. A `RAISED` panel draws vanilla's popup frame, with white text, and keeps the same 4 px between that frame and its elements as inside MenuKit's own frame (the popup frame is 3 px thicker, so the panel is 6 px bigger). Keep labels on roles (next recipe) rather than `TextLabel.COLOR_DARK`, which reads only on a container screen's grey. A screen that declares no kind (`super(title, panels)`) stays a container screen, in MenuKit's grey look. Panels injected into vanilla's own screens need nothing, because a panel on Options or Controls is already on an options screen.

## Colour text by role

Needs: MenuKit 6.1.0.

```java
List<PanelElement> body = new ArrayList<>();
body.add(TextLabel.builder().text(Component.literal("Sorting")).role(TextRole.HEADING).build());
body.add(TextLabel.builder().at(0, 14).text(Component.literal("Rows")).build());
body.add(TextLabel.builder().at(0, 26).text(Component.literal("Applies to chests too."))
        .role(TextRole.MUTED).build());
```

Result: the heading and "Rows" are dark grey on a container screen and white on an options screen. The note is mid grey on the first and vanilla's grey on the second. A label without a role is `BODY`. Disabled text takes the kind's disabled grey whatever its role. `color(argb)` and `shadow(b)` fix a value on every kind instead, for text over the world on the HUD. `TextLabel.COLOR_DARK` is right only on a container screen's grey.

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

Needs: MenuKit 7.2.0. For a widget built outside MenuKit: a config library's custom control, or a vanilla widget MenuKit has no element for.

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

Result: a swatch of the colour, over a checkerboard because it has alpha. Clicking it opens a dialog over the screen, titled Glow color. The Basic tab shows the 16 dye colours by their dye items in three rows, in rainbow order, and clicking one sets the colour. The Custom tab has a saturation and brightness square, a hue bar, a transparency bar and a hex field. Every change is written at once. Done keeps it; Cancel or Escape puts back the colour from before the dialog opened. Without `alpha(true)` the value is `0xRRGGBB` and there is no transparency bar.

## An item setting

Needs: MenuKit 7.2.0.

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

// In play, one matcher for every mode.
boolean locked = config.locks().stream().anyMatch(rule -> rule.matches(stack));
```

Result: one slot with the item, and two rows of slots, each with an empty slot marked with a plus at the end. Clicking a slot opens a dialog over the screen: the setting's slots at the top, then a search box over every item and variant. Click a slot in the row to select it, then click an item in the grid to put it there. The small minus in a filled slot's corner removes it, on the page and in the dialog. The two lists have an Edit tab for the selected slot: name, count, enchantments that fit the item, the other components in `/give` syntax, and for the locks the choice between Any and Exactly. The first picker never shows a count or an Edit tab. Every change is written at once; Cancel or Escape puts back the value from before. Save a rule with `ItemMatch.CODEC`.
