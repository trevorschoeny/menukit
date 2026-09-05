# Recipes

One task per section. Each names the artifact it needs and the file the sample comes from. [concepts.md](concepts.md) defines the terms.

## Show a readout on the HUD

Needs: MenuKit. Call from the client entry point.

```java
// Source: offshore, hud/BoatHealth.java
MKHudPanel.builder("offshore:boat-health")
        .anchor(MKHudAnchor.BOTTOM_CENTER, 0, -50)
        .autoSize().padding(0)
        .style(PanelStyle.NONE)
        .hideInScreen()
        .showWhen(BoatHealth::isActive)
        .bar(0, 0, WIDTH, 7)
            .value(BoatHealth::health)
            .color(0xFFC08040)
            .label(() -> Component.literal("Hull"))
            .done()
        .build();
```

Result: a 7 pixel tall bar renders 50 pixels above the bottom center of the window while `isActive()` returns true. `value` is a `Supplier<Float>` in the range 0 to 1.

Sub-builders (`text`, `item`, `slot`, `bar`) end with `.done()`. `.element(PanelElement)` adds any element. `.region(HudRegion)` replaces `.anchor(...)` and stacks the panel with other panels in that region.

## Put a button on every container screen

Needs: MenuKit. Call from the client entry point.

```java
// Source: validator-mk, MkToggleActivations.java (trimmed)
Panel p = Panel.builder("mkv:region-everywhere")
        .style(PanelStyle.RAISED)
        .add(new TextLabel(0, 0, Component.literal("Region panel"), TextLabel.COLOR_LIGHT, true))
        .add(((Button) Button.spec(70, 14, Component.literal("Click me"), b -> {}).at(0, 14))
                .tooltip(Component.literal("A button inside a region panel.")))
        .build();
ScreenPanelAdapter adapter = new ScreenPanelAdapter(p, MenuRegion.LEFT_ALIGN_TOP.priority(20));
```

Result: the panel renders in the top left gutter of the inventory, every chest, and the creative inventory. Add `.on(InventoryScreen.class)` to limit it to the survival inventory. Call `adapter.unregister()` to remove it.

## Put a panel next to the player inventory on every screen

Needs: MenuKit. Call from the client entry point.

```java
// Source: inventory-plus, toolbar/Toolbar.java (trimmed)
Panel panel = Panel.builder("inventoryplus:toolbar.inventory")
        .style(PanelStyle.NONE)
        .elements(buildInventoryChildren())
        .build();
panel.showWhen(Toolbar::isToolbarScope);
new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
        .on(SlotGroupCategory.PLAYER_INVENTORY);
```

Result: the panel renders above the right edge of the player inventory grid on every screen that shows that grid. Pass several categories to `.on(...)` to render the panel once per matching group:

```java
// Source: inventory-plus, toolbar/Toolbar.java
new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
        .on(SlotGroupCategory.CHEST_STORAGE,
            SlotGroupCategory.SHULKER_STORAGE,
            SlotGroupCategory.DISPENSER_STORAGE,
            SlotGroupCategory.HOPPER_STORAGE);
```

## Lay out elements in a row

Needs: MenuKit.

```java
// Source: MenuKit javadoc, core/layout/Row.java
List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
        .add(Button.spec(60, 20, Component.literal("OK"),     this::onConfirm))
        .add(Button.spec(60, 20, Component.literal("Cancel"), this::onCancel))
        .build();

Panel p = Panel.builder("mymod:confirm").elements(buttonRow).build();
```

Result: two 60 by 20 buttons at y 30, starting at x 20, with a 4 pixel gap. `Column` has the same shape on the vertical axis. `.crossAlign(CrossAlign.CENTER)` centers children on the cross axis. A hidden element keeps its space.

## Add synced slots to the player, shown on every container screen

Needs: MenuKit: Containers. Declare the storage at common init. Call `register()` from the common entry point.

```java
// Source: MenuKit: Containers javadoc, core/MKCContainerPanel.java
public static final PlayerStorageAttachment<NonNullList<ItemStack>> POCKETS =
        StorageAttachment.playerAttached("mymod", "pockets", 9);

MKCContainerPanel.define("mymod:pockets")
        .at(MenuRegion.LEFT_ALIGN_TOP, 7)
        .style(PanelStyle.RAISED)
        .parity(ScreenMatcher.all())
        .chrome(() -> List.of(new Button(0, 0, 60, 14, Component.literal("Sort"), b -> {})))
        .addSlot(SlotSpec.at("pockets").count(9)
                .storage(player -> POCKETS.bind(player)))
        .register();
```

Result: nine real slots render in the top left gutter of the survival inventory, the creative inventory, and every container screen. Their contents persist on the player and sync through vanilla's slot protocol. `ScreenMatcher.allExcept(Class...)` removes the panel from named screens. The slots still exist on those menus; they are not drawn there.

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

`p.group(id, storage, priority, columns)` sets shift-click priority and column count. `p.button(...)`, `p.text(...)`, and `p.element(...)` add elements to the panel. `p.region(MenuRegion)` anchors a second panel to the main one.

## Attach behavior to a slot by address

Needs: MenuKit: Containers. Call at common init.

```java
// Source: inventory-max, equipment/EquipmentSlots.java (trimmed)
Address a = CreatedSlotAdapter.addressOf("mymod:ring", "ring", 0);
Window.slot(a).set(MKCBehaviorKeys.GATING, new SlotGate() {
    @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) { return stack.is(Items.GOLD_INGOT); }
    @Override public boolean mayPickup(Player player, GatingContext ctx) { return true; }
    @Override public int maxStackSize(ItemStack stack, int vanillaMax) { return Math.min(1, vanillaMax); }
});
```

Result: slot 0 of the `ring` group accepts gold ingots only, one per slot, whenever a slot with that address exists in an open menu. `MKCBehaviorKeys` lists the keys: `GATING`, `QUICK_MOVE`, `BINDING`, `MENDING`. `SlotSpec.gate(SlotGate)` and `SlotSpec.accepts(Predicate)` set the same gate at declaration time for container-parity slots.
