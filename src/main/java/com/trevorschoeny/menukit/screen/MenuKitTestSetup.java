package com.trevorschoeny.menukit.screen;

import com.trevorschoeny.menukit.core.*;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static net.minecraft.commands.Commands.literal;

/**
 * Temporary test setup for Phase 3 validation. Registers a test MenuType
 * and a {@code /mktest} command that opens a screen with two panels —
 * a visible "main" panel and a hidden "extras" panel.
 *
 * <p>Also registers {@code /mktest toggle} to flip the extras panel,
 * and {@code /mktest stress} to run the 100-toggle stress test.
 *
 * <p>Remove after Phase 3 validation is complete.
 */
public class MenuKitTestSetup {

    private static MenuType<MenuKitScreenHandler> testMenuType;

    /** Called during mod init (server side). */
    public static void registerServer() {
        // Register the test MenuType
        testMenuType = new MenuType<>(
                (syncId, playerInventory) -> createTestHandler(syncId, playerInventory),
                net.minecraft.world.flag.FeatureFlagSet.of()
        );
        Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath("menukit", "mk_test"),
                testMenuType);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mktest")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        player.openMenu(new MenuProvider() {
                            @Override
                            public Component getDisplayName() {
                                return Component.literal("MenuKit Test");
                            }

                            @Override
                            public MenuKitScreenHandler createMenu(int syncId, Inventory inv, Player p) {
                                return createTestHandler(syncId, inv);
                            }
                        });
                        return 1;
                    })
                    .then(literal("toggle").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        if (player.containerMenu instanceof MenuKitScreenHandler handler) {
                            Panel extras = handler.getPanel("extras");
                            if (extras != null) {
                                handler.setPanelVisible("extras", !extras.isVisible());
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Extras panel: "
                                                + (extras.isVisible() ? "VISIBLE" : "HIDDEN")),
                                        false);
                            }
                        }
                        return 1;
                    }))
                    .then(literal("stress").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        if (player.containerMenu instanceof MenuKitScreenHandler handler) {
                            // Toggle 100 times rapidly
                            for (int i = 0; i < 100; i++) {
                                handler.setPanelVisible("extras", i % 2 == 0);
                            }
                            // After 100 toggles (even count), extras should be visible
                            Panel extras = handler.getPanel("extras");
                            boolean finalState = extras != null && extras.isVisible();

                            // Verify consistency: check that all extras slots report
                            // the correct inertness state
                            boolean consistent = true;
                            if (extras != null) {
                                for (SlotGroup group : extras.getGroups()) {
                                    for (int s = group.getFlatIndexStart(); s < group.getFlatIndexEnd(); s++) {
                                        MenuKitSlot slot = (MenuKitSlot) handler.slots.get(s);
                                        boolean slotInert = slot.isInert();
                                        if (slotInert == finalState) {
                                            // inert should be opposite of visible
                                            consistent = false;
                                        }
                                    }
                                }
                            }

                            // Capture for lambda
                            final boolean passedConsistency = consistent;
                            final boolean passedFinal = finalState;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Stress test: final="
                                            + (passedFinal ? "VISIBLE" : "HIDDEN")
                                            + " consistent=" + passedConsistency),
                                    false);
                        }
                        return 1;
                    }))
            );
        });
    }

    /** Called during client init. */
    public static void registerClient() {
        MenuScreens.register(testMenuType, MenuKitHandledScreen::new);
    }

    /**
     * Creates the test handler with three panels:
     * - "main": 9 free slots (PlayerStorage) — visible, container-backed
     * - "extras": 4 input-filtered slots (EphemeralStorage, diamonds only) — hidden
     * - "player": 36 slots wrapping the player's actual inventory — visible
     *
     * <p>The player panel uses VirtualStorage to delegate to the vanilla Inventory.
     * This validates shift-click routing between container and player groups.
     */
    private static MenuKitScreenHandler createTestHandler(int syncId, Inventory playerInventory) {
        // Container: 9 free slots with some items pre-filled
        PlayerStorage mainStorage = PlayerStorage.of(9);
        mainStorage.setStack(0, new ItemStack(Items.DIAMOND, 16));
        mainStorage.setStack(1, new ItemStack(Items.IRON_INGOT, 32));
        mainStorage.setStack(2, new ItemStack(Items.COBBLESTONE, 64));

        // Extras: accepts only diamonds
        EphemeralStorage extrasStorage = EphemeralStorage.of(4);

        // Player inventory: 36 slots (27 main + 9 hotbar) wrapping vanilla Inventory.
        // Mapping: local 0-26 → inventory 9-35 (main), local 27-35 → inventory 0-8 (hotbar).
        // This matches vanilla's standard container layout order.
        VirtualStorage playerInvStorage = new VirtualStorage(
                36,
                i -> {
                    int invIndex = i < 27 ? i + 9 : i - 27;
                    return playerInventory.getItem(invIndex);
                },
                (i, stack) -> {
                    int invIndex = i < 27 ? i + 9 : i - 27;
                    playerInventory.setItem(invIndex, stack);
                },
                playerInventory::setChanged
        );

        return MenuKitScreenHandler.builder(testMenuType)
                .panel("main", p -> p
                        .group("container", mainStorage, InteractionPolicy.free()))
                .panel("extras", p -> p
                        .group("filtered", extrasStorage,
                                InteractionPolicy.input(stack -> stack.is(Items.DIAMOND)))
                        .hidden())
                .panel("player", p -> p
                        .group("inventory", playerInvStorage, InteractionPolicy.free(),
                                QuickMoveParticipation.BOTH, 0))
                .build(syncId);
    }
}
