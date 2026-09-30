package com.trevlar.menukit.containers;

import com.trevlar.menukit.MK;
import com.trevlar.menukit.MKClient;

import com.trevlar.menukit.containers.core.MKCContainerPanel;
import com.trevlar.menukit.containers.core.MKCSlotProjection;
import com.trevlar.menukit.containers.core.MKCSlotScreenHook;
import com.trevlar.menukit.inject.SlotScreenDispatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.jetbrains.annotations.ApiStatus;

/**
 * Client-side entry point for the MenuKit: Containers artifact.
 *
 * <p>Per §0043 (Complete-on-Side Feature Ownership): MK initializes its own
 * observation machinery (slot-group resolvers, dispatch registries) — this
 * artifact does not initialize MK-side features. The MKC client init is
 * narrowly scoped to MKC-side concerns:
 *
 * <ul>
 *   <li>M1 client-state init (delegated to
 *       {@link MKC#initClient}) — registers client-side
 *       networking handlers.</li>
 * </ul>
 *
 * <p>Post-§0043: {@code VanillaSlotGroupResolvers.registerAll()} and the
 * slot-group panel dispatch (now the slot-group hosts of MenuKit's
 * {@code ScreenPanelRegistry}) live on MenuKit's side: observation idioms,
 * complete there.
 */
@ApiStatus.Internal
public class MKCClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Delegate to MenuKit: Containers' client-state init.
        MKC.initClient();

        // Inventory-screen parity: plug the registered-slot input resolution into
        // MenuKit's library-owned screen dispatch (§0042 — MK exposes the neutral
        // hook + the per-screen mixins; MKC implements the registered-slot half
        // here). After this, a panel-hosted registered slot (a SlotElement) resolves
        // hover/click on every matching inventory-bearing screen, creative included,
        // with no per-screen consumer mixin. Drawing the slot is the panel pipeline's
        // job; this hook is the input limb only.
        SlotScreenDispatcher.setHook(new MKCSlotScreenHook());

        // THE ONE WINDOW — created-slot resolution port (§0042). MK resolves
        // vanilla/panel/element addresses itself; created slots are MKCSlots whose
        // position lives on that MKC type, so MKC installs the resolver here.
        com.trevlar.menukit.window.SlotWindowResolver.setCreatedSlotResolver(
                com.trevlar.menukit.containers.core.CreatedSlotAdapter.INSTANCE);

        // Slot state's client player lookup, kept out of common code.
        com.trevlar.menukit.containers.core.MKSlotState.installClientPlayer(
                () -> net.minecraft.client.Minecraft.getInstance().player);

        // The kind-aware slot address rule and the created-group resolver are
        // installed from MKC.init, on both sides (6.0.0: a server needs them too).

        // Container-parity chrome. Build each MKCContainerPanel's display panel
        // (chrome + slot presentation) and wire its ScreenPanelAdapter, scoped by
        // the registered parity matcher. Runs now (in the library's client init,
        // after every consumer's common-init register() has populated the
        // definitions — Fabric runs all main entrypoints before any client one),
        // so no GUI object was ever constructed on a dedicated server.
        MKCContainerPanel.wireRegisteredChrome();

        // MKCMenu turnkey screens: each defined custom menu's screen registered with
        // MenuScreens (default MKCHandledScreen, or the consumer's MKCMenu.screen(...)).
        // At client start, after every mod's client initializer (which may call
        // MKCMenu.screen(...)): Fabric does not order client entrypoints between mods.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> com.trevlar.menukit.containers.screen.MKCMenu.registerScreens());

        // Slot projection — client seam. Append a player's registered projected
        // slots onto a foreign container menu (chest/furnace/donkey) at screen
        // init, which fires synchronously inside handleOpenScreen BEFORE the
        // initial content packet is processed — mirroring the server's
        // ServerPlayer.initMenu HEAD seam so both menus carry the slots (same set,
        // same order) before the first sync.
        //
        // Skip the player's own inventory screens. The survival InventoryScreen's
        // menu is the InventoryMenu, already served by MKCInventoryMenuMixin; the
        // CreativeModeInventoryScreen's ItemPickerMenu is served by the creative
        // wrapper mixin. Projecting onto either here would DOUBLE-append the parity
        // slots. The server seam (ServerPlayer.initMenu) never fires for those, so
        // this guard keeps the client identical to the server — the sync invariant.
        // No-op otherwise for menus with no registered projection source.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
                return;
            }
            if (screen instanceof AbstractContainerScreen<?> acs && client.player != null) {
                MKCSlotProjection.appendProjectedSlots(acs.getMenu(), client.player);
            }
        });
    }
}
