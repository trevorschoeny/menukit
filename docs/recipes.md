# Recipes

One task per section. Each names the artifact it needs and the file the sample comes from. [concepts.md](concepts.md) defines the terms.

## Show a readout on the HUD

Needs: MenuKit. Call from the client entry point.

```java
// Source: offshore, hud/BoatHealth.java
MKHudPanel.builder("offshore:boat-health")
        .region(InsideRegion.BOTTOM_CENTER)
        .offset(0, -46)
        .padding(0)
        .style(PanelStyle.NONE)
        .showWhen(() -> Minecraft.getInstance().gui.screen() == null && BoatHealth.isActive())
        .bar(0, 0, WIDTH, 7)
            .value(BoatHealth::health)
            .color(0xFFC08040)
            .label(() -> Component.literal("Hull"))
            .done()
        .build();
```

Result: a 7 pixel tall bar renders 50 pixels above the bottom center of the window while `isActive()` returns true and no screen is open. `value` is a `DoubleSupplier` in the range 0 to 1.

`.region(InsideRegion)` puts the panel on one of nine spots of the window, 4 pixels in from the edges it touches, stacked with the other panels on that spot in `.priority(n)` order. `.offset(dx, dy)` nudges this panel alone. Sub-builders (`text`, `item`, `slot`, `bar`) end with `.done()`. `.element(PanelElement)` adds any element, and `.custom(x, y, w, h, ctx -> ...)` draws freely inside the panel.

## Put a button on every container screen

Needs: MenuKit. Call from the client entry point.

```java
// Source: validator-mk, MkToggleActivations.java (trimmed)
Panel p = Panel.builder("mkv:region-everywhere")
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

Result: the panel renders in the top left gutter of the inventory, every chest, and the creative inventory. Add `.on(InventoryScreen.class)` to limit it to the survival inventory. To show it only some of the time, give the panel a `showWhen(...)`: adapters are declared at init and cannot be added or removed during play.

The panel is opaque by default, so everything under it is inert: a slot it covers does not highlight, click or show a tooltip. `.opaque(false)` makes it see-through, and then only its buttons and other controls block what is behind them.

## Put a panel next to the player inventory on every screen

Needs: MenuKit. Call from the client entry point.

```java
// Source: inventory-plus, toolbar/Toolbar.java (trimmed)
Panel panel = Panel.builder("inventoryplus:toolbar.inventory")
        .style(PanelStyle.NONE)
        .elements(buildInventoryChildren())
        .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
        .build();
panel.showWhen(Toolbar::isToolbarScope);
new SlotGroupPanelAdapter(panel)
        .on(SlotGroupCategory.PLAYER_INVENTORY);
```

Result: the panel renders above the right edge of the player inventory grid on every screen that shows that grid. Pass several categories to `.on(...)` to render the panel once per matching group:

```java
// Source: inventory-plus, toolbar/Toolbar.java
new SlotGroupPanelAdapter(panel)
        .on(SlotGroupCategory.CHEST_STORAGE,
            SlotGroupCategory.SHULKER_STORAGE,
            SlotGroupCategory.DISPENSER_STORAGE,
            SlotGroupCategory.HOPPER_STORAGE);
```

## Build a full-screen tabbed menu

Needs: MenuKit.

```java
// Source: validator-mk, TabsDemoScreen.java (trimmed)
private static String tab = "general";   // yours to keep, and to set before opening

Tabs tabs = Tabs.builder()
        .mode(Tabs.Mode.WRAP)
        .align(Tabs.Align.FILL)
        .state(() -> tab, id -> tab = id)
        .tab("general", Component.literal("General"), generalBody)
        .tab(Tabs.tab("pockets")
                .label(Component.literal("Pockets"))
                .visibleWhen(() -> config.showMaxTabs)
                .body(pocketsBody))
        .build();

Panel main = Panel.builder("mymod:settings")
        .style(PanelStyle.RAISED)
        .position(PanelPosition.main())
        .add(tabs)
        .build();
```

Result: a strip across the top of the screen that wraps into rows, with the selected tab's body below it filling the rest of the screen. A body is a `List<PanelElement>`, the same as `ScrollContainer` content. To open the menu on a given tab, set `tab` before `setScreen`. Hiding a tab re-splits the rows and moves the body. For one scrolling row, use `Tabs.Mode.SIDE_SCROLL`.

## Add a tab to another mod's menu

Needs: MenuKit. The owner names the menu; you add to it at your client init.

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
        Tabs.tab("pockets").label(Component.literal("Pockets")).body(() -> PocketSettings.body()));
Tabs.addTo(Identifier.fromNamespaceAndPath("ownermod", "settings"),
        Tabs.tab("mending").label(Component.literal("Mending")).after("pockets").body(() -> MendSettings.body()));
```

Result: your Pockets tab replaces the owner's stand-in in the same place, and Mending sits right after it. Your bodies are built each time the menu opens and read your own config. Without your mod, the owner's Pockets stand-in shows with a dimmed label. The owner builds its `Tabs` when its screen opens, so every tab added at init is in it.

## Lay out elements in a row

Needs: MenuKit.

```java
// Source: MenuKit javadoc, core/layout/Row.java
List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
        .add(Button.builder().label(Component.literal("OK")).size(60, 20).onClick(this::onConfirm).build())
        .add(Button.builder().label(Component.literal("Cancel")).size(60, 20).onClick(this::onCancel).build())
        .build();

Panel p = Panel.builder("mymod:confirm").elements(buttonRow).build();
```

Result: two 60 by 20 buttons at y 30, starting at x 20, with a 4 pixel gap. `Column` has the same shape on the vertical axis. `.crossAlign(CrossAlign.CENTER)` centers children on the cross axis. A hidden element keeps its space. `Row` places at build time; for a row that follows the panel's width, use a `Flow` (next recipe).

## Build a settings body: label left, control right, greyed while off

Needs: MenuKit. The body of a settings tab, built by the tab's body factory.

```java
// Source: validator-mk, SettingsDemoScreen.java (trimmed)
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

Result: the switch sits at the left and Reset at the right edge of the tab body, and each setting's control sits at the right edge beside its label, whatever the window's width; when the window is too narrow the control drops under its label. The divider spans the body. While the feature is off, every setting row is grey and ignores the mouse and keys. `Panel.disabledWhen(...)` greys a whole panel the same way.

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

Needs: MenuKit 3.1.0.

```java
// Source: menukit, core/Button.java and core/Click.java
Button pin = Button.builder().label(Component.literal("Mode")).size(60, 16)
        .onClick(this::cycleMode)
        .onSecondaryClick(click -> {
            if (click.isShiftRight()) clearMode();
            else if (click.isRight()) cycleModeBackward();
        })
        .tint(() -> pinned ? 0x50FFC000 : 0)
        .build();
```

Result: left click runs `cycleMode`. Right click runs `cycleModeBackward`, and shift with right click runs `clearMode`. While `pinned` is true an amber wash fills the button under its label. `Toggle` takes the same two builder methods with the same contract.

A control with no `onSecondaryClick` handler passes non-left clicks to vanilla. A disabled control consumes neither kind.

## Add synced slots to the player, shown on every container screen

Needs: MenuKit: Containers. Declare the storage at common init. Call `register()` from the common entry point.

```java
// Source: MenuKit: Containers javadoc, core/MKCContainerPanel.java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);

MKCContainerPanel.define("mymod:pockets")
        .at(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP).priority(20), 7)
        .style(PanelStyle.RAISED)
        .parity(ScreenMatcher.all())
        .chrome(() -> List.of(Button.builder().label(Component.literal("Sort")).size(60, 14).build()))
        .addSlot(SlotSpec.at("pockets", SlotGroupCategory.PLAYER_INVENTORY).count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

Result: nine real slots render in the top left gutter of the survival inventory, the creative inventory, and every container screen. Their contents persist on the player and sync through vanilla's slot protocol. `ScreenMatcher.allExcept(Class...)` removes the panel from named screens. The slots still exist on those menus; they are not drawn there.

The category is required. It says what the group is to other mods: `SlotGroupCategories.of(menu)` lists the group under it next to the vanilla categories, on every menu it sits on, so a search that walks the player's inventory menu can treat pockets as inventory and leave an elytra slot alone. Use a vanilla category when the group is one of those things; declare your own (`new SlotGroupCategory("mymod", "equipment")`) when it is not. The name is a contract once another mod depends on it.

`SlotSpec.shiftClickOut(false)`, `shiftClickIn(false)`, `collect(false)`, and `dragFill(false)` keep a group out of the bulk shortcuts: shift-click, double-click collect, and drag-fill. The slot stays storage; the shortcuts skip it.

`SlotSpec.accepts(Predicate<ItemStack>)` limits what a slot takes. `SlotSpec.revealWhen(BooleanSupplier)` hides the slots until the supplier returns true. Death, keepInventory, Curse of Vanishing, and Curse of Binding behave as they do for vanilla slots. `POCKETS.dropsOnDeath(DropRule.KEEP)` overrides the death rule.

Shift-click into a created slot is not routed. Direct click works.

## Attach a flag to a slot

Needs: MenuKit: Containers. Register the channel at common init, before any container menu opens.

```java
// Source: inventory-max, containerlocks/ContainerLocks.java
public static SlotStateChannel<Boolean> CHANNEL;

public static void register() {
    CHANNEL = MKSlotState.register(
            Identifier.fromNamespaceAndPath("inventoryplus", "container_lock"),
            Codec.BOOL,
            StreamCodec.<RegistryFriendlyByteBuf, Boolean>of(
                    (buf, v) -> buf.writeBoolean(v),
                    buf -> buf.readBoolean()),
            false,
            SlotStateChannel.Visibility.SHARED);
}
```

Result: every slot in every menu has a boolean that defaults to false. `CHANNEL.get(slot)` and `CHANNEL.set(slot, true)` read and write it for the viewer's open menu. `SHARED` stores one value per slot for all viewers. Omit the last argument for `PRIVATE`, one value per viewer. The value persists as NBT on the slot's owner.

With no open menu, use `CHANNEL.get(player, key, index)` with a `PersistentContainerKey`, or `CHANNEL.get(container, index)` for a shared value at a placed container.

## Open a custom menu with its own slots

Needs: MenuKit: Containers. Define at common init.

```java
// Source: MenuKit: Containers javadoc, screen/MKCMenu.java
public static final MKCMenu CUSTOM = MKCMenu
        .define(Identifier.fromNamespaceAndPath(MOD_ID, "custom_menu"), MyMenu::buildHandler)
        .title(Component.literal("My Custom Menu"))
        .register();

public static MKCScreenHandler buildHandler(
        MenuType<MKCScreenHandler> type, int syncId, Inventory inv) {
    return MKCScreenHandler.builder(type)
            .panel("mymod:menu:main", p -> p
                    .main()
                    .group("items", EphemeralStorage.of(9)))
            .build(syncId);
}

// Client:
CUSTOM.requestOpen();
// Server:
CUSTOM.open(serverPlayer);
```

Result: `requestOpen()` sends one payload; the server opens the menu; the client shows a screen with one nine-slot group. The handler factory runs on both sides and must build the same storages in the same order. Pass the `type` argument straight to `MKCScreenHandler.builder(type)`.

`p.group(id, storage, priority, columns)` sets shift-click priority and column count. `p.button(...)`, `p.text(...)`, and `p.element(...)` add elements to the panel. `p.region(OutsideRegion)` anchors a second panel to the main one, and `p.position(...)` takes any placement. A panel that declares none takes the standalone default: the first is the main panel, and later ones stack below it.

## Attach behavior to a slot by address

Needs: MenuKit: Containers. Call at common init.

```java
// Source: inventory-max, equipment/EquipmentSlots.java (trimmed)
Address a = Address.createdSlot(MKCSlots.groupId("mymod:ring", "ring"), 0);
Window.slot(a).gate(new SlotGate() {
    @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) { return stack.is(Items.GOLD_INGOT); }
    @Override public boolean mayPickup(Player player, GatingContext ctx) { return true; }
    @Override public int maxStackSize(ItemStack stack, int vanillaMax) { return Math.min(1, vanillaMax); }
});
```

Result: slot 0 of the `ring` group accepts gold ingots only, one per slot, whenever a slot with that address exists in an open menu. `SlotGate` and `GatingContext` are `com.trevlar.menukit.window` types, and `BehaviorKeys.GATING` and `BINDING` are MenuKit keys enforced for every slot kind, so a MenuKit-only mod can gate a vanilla slot the same way. `MKCBehaviorKeys.MENDING` stays in Containers. The operations are `BehaviorKeys.VANILLA_OPERATIONS`. `SlotSpec.gate(SlotGate)` and `SlotSpec.accepts(Predicate)` set the same gate at declaration time for container-parity slots.

## Block operations on a locked slot

Needs: MenuKit. Call at common init.

```java
SlotOperations.veto((ref, op) ->
        ref.container() instanceof Inventory inv
                && ref.player() != null && ref.player().getUUID().equals(inv.player.getUUID())
                && MyLocks.isLocked(ref.containerSlot())
                && MyLocks.blocks(op));
```

Result: every operation in the game, vanilla's and any other mod's, asks `SlotOperations.allows` before it acts, and this rule refuses the ones the player chose on the slots the player locked. A veto can only say no, so it never overwrites what the slot's author declared and there is nothing to restore on unlock. `SlotOperations.all()` is the list a settings screen shows, with `SlotOperations.name(op)` and `description(op)` for the words. The player check keeps a per-player lock from answering for another player on the same integrated server. Declare nothing on the slots themselves.

## Add an operation of your own

Needs: MenuKit. Define at common init; ask before acting.

```java
public static final BehaviorKey<TriBool> RESTOCK_TAKE = BehaviorKey.of(
        Identifier.fromNamespaceAndPath("mymod", "restock_take"), TriBool.class, TriBool.TRUE,
        Tier.SERVER, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);

// init
SlotOperations.define(RESTOCK_TAKE, SlotOperations.Role.TAKE);

// wherever the operation picks a slot
if (!SlotOperations.allows(menu, slot, player, RESTOCK_TAKE)) continue;
```

With two lang lines:

```json
"slot_operation.mymod.restock_take": "Restock takes from",
"slot_operation.mymod.restock_take.description": "Auto-restock may pull a refill out of this slot."
```

If the operation moves items by sending clicks, send them under its name:

```java
SlotOperations.as(RESTOCK_TAKE, RESTOCK_PUT, () -> {
    gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.QUICK_MOVE, player);
});
```

Result: the operation appears in `SlotOperations.all()` for every mod to list, a slot's author can turn it off per slot, group, or category (`SlotOperations.inherent`), and a locking mod's veto reaches it. An operation that moves items between two slots is two keys, one for the slot being emptied and one for the slot being filled, so a lock can answer each side on its own. Its clicks are judged as the operation, not as the shift-click or plain click they look like, so a lock that blocks shift-click does not block it.

