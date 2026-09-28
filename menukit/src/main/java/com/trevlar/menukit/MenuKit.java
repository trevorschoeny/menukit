package com.trevlar.menukit;

import com.trevlar.menukit.core.SlotGroupCategory;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotOperations;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MenuKit's common initializer, on both sides (§0062): the shared vocabularies
 * every other mod reads, declared before anyone can read them. The client half
 * is {@link MKClient}.
 */
public final class MenuKit implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    @Override
    public void onInitialize() {
        init();
    }

    private static void init() {
        // Declarations freeze at the first server start, after every entrypoint
        // (on a client, MKClient freezes at client start, which comes first).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
                server -> com.trevlar.menukit.window.Declarations.freeze("server starting"));
        // The shared vocabularies, on both sides: every vanilla slot category, and
        // the operations vanilla itself ships. A consumer reads these through
        // SlotGroupCategories.all() / SlotOperations.all() and adds its own with
        // SlotGroupCategories.declare(...) / SlotOperations.define(...).
        // Every vanilla slot group, one per category (the group a vanilla resolver
        // contributes), so SlotGroups lists them with no menu open. Declaring a group
        // declares its category too.
        SlotGroupCategory.vanilla().forEach(c ->
                com.trevlar.menukit.inject.SlotGroups.declare(com.trevlar.menukit.inject.SlotGroupId.category(c), c));
        // Vanilla menus' slot groups resolved from a live menu, on both sides: a
        // server resolves categories too (creative's picker is added by MKClient).
        com.trevlar.menukit.inject.VanillaSlotGroupResolvers.registerAll();
        // Each with the role it plays on a slot, so a settings screen can tell what
        // takes items out from what puts them in.
        // Where each one applies, from what vanilla does (26.2): an output slot takes
        // nothing in, the crafter's result can't be touched at all, double-click
        // collect skips the results whose menus override canTakeItemForPickAll, and a
        // pickup lands only in the hotbar, the main inventory and the offhand.
        SlotGroupCategory[] outputs = {
                SlotGroupCategory.CRAFTING_OUTPUT, SlotGroupCategory.CRAFTER_RESULT, SlotGroupCategory.FURNACE_OUTPUT,
                SlotGroupCategory.ANVIL_OUTPUT, SlotGroupCategory.GRINDSTONE_OUTPUT, SlotGroupCategory.SMITHING_OUTPUT,
                SlotGroupCategory.LOOM_OUTPUT, SlotGroupCategory.STONECUTTER_OUTPUT,
                SlotGroupCategory.CARTOGRAPHY_OUTPUT, SlotGroupCategory.MERCHANT_RESULT};
        var takes = SlotOperations.AppliesTo.vanillaExcept(SlotGroupCategory.CRAFTER_RESULT);
        var puts = SlotOperations.AppliesTo.vanillaExcept(outputs);
        SlotOperations.define(BehaviorKeys.CLICK_TAKE, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.CLICK_PUT, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.SHIFT_CLICK_OUT, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.SHIFT_CLICK_IN, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.COLLECT, SlotOperations.Role.TAKE, SlotOperations.AppliesTo.vanillaExcept(
                SlotGroupCategory.CRAFTER_RESULT, SlotGroupCategory.CRAFTING_OUTPUT,
                SlotGroupCategory.CARTOGRAPHY_OUTPUT, SlotGroupCategory.MERCHANT_RESULT,
                SlotGroupCategory.SMITHING_OUTPUT, SlotGroupCategory.STONECUTTER_OUTPUT));
        SlotOperations.define(BehaviorKeys.DRAG_FILL, SlotOperations.Role.PUT, puts);
        SlotOperations.define(BehaviorKeys.HOTBAR_SWAP, SlotOperations.Role.BOTH, takes);
        SlotOperations.define(BehaviorKeys.OFFHAND_SWAP, SlotOperations.Role.BOTH, takes);
        SlotOperations.define(BehaviorKeys.DROP, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.DROP_STACK, SlotOperations.Role.TAKE, takes);
        SlotOperations.define(BehaviorKeys.INVENTORY_INSERT, SlotOperations.Role.PUT, SlotOperations.AppliesTo.vanilla(
                SlotGroupCategory.PLAYER_HOTBAR, SlotGroupCategory.PLAYER_INVENTORY, SlotGroupCategory.PLAYER_OFFHAND));
        LOGGER.info("[MenuKit] Initialized");
    }
}
