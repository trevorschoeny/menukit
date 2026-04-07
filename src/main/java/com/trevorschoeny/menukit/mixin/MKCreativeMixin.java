package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.widget.MKButton;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.event.MKEventBus;
import com.trevorschoeny.menukit.event.MKEventHelper;
import com.trevorschoeny.menukit.widget.MKSlot;
import com.trevorschoeny.menukit.event.MKSlotEvent;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MenuKit internal mixin — handles creative mode screen integration.
 *
 * <p>Creative mode has its own click and rendering pipeline that differs
 * from {@link AbstractContainerScreen}. This mixin ensures MKPanels work
 * correctly across all creative tabs by delegating to unified MenuKit
 * handler methods.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Tab switch handling — repositions slots, recreates buttons</li>
 *   <li>Click interception — prevents clicks on panels from switching tabs</li>
 *   <li>Tooltip suppression — prevents tab tooltips from showing through panels</li>
 *   <li>Click bounds — prevents items from dropping when clicking on panels</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(CreativeModeInventoryScreen.class)
public class MKCreativeMixin extends Screen {

    protected MKCreativeMixin() { super(Component.empty()); }

    @Shadow
    protected boolean checkTabClicked(CreativeModeTab tab, double mouseX, double mouseY) {
        throw new AssertionError(); // shadow — never called directly
    }

    // ── Key Press ────────────────────────────────────────────────────────────

    /**
     * Fires KEY_PRESS events on the creative screen. Vanilla's
     * CreativeModeInventoryScreen overrides keyPressed() without calling
     * super, so the @Inject on AbstractContainerScreen (MKKeyPressMixin)
     * never fires. This duplicates that logic for the creative screen.
     */
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onCreativeKeyPress(KeyEvent event,
                                             CallbackInfoReturnable<Boolean> cir) {
        // Access hoveredSlot via the accessor (it's on AbstractContainerScreen)
        var acc = (AbstractContainerScreenAccessor)(Object) this;
        Slot hoveredSlot = acc.trevorMod$getHoveredSlot();

        if (hoveredSlot == null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        MKSlotEvent mkEvent = MKEventHelper.buildKeyEvent(
                hoveredSlot, self, player, event.key(), event.modifiers());
        if (mkEvent == null) return;

        boolean consumed = MKEventBus.fire(mkEvent);
        if (consumed) {
            cir.setReturnValue(true);
        }
    }

    // ── Tab Switch ──────────────────────────────────────────────────────────

    /**
     * After selectTab: delegate to MenuKit for slot repositioning, then
     * recreate buttons for the new context.
     */
    @Inject(method = "selectTab", at = @At("RETURN"))
    private void menuKit$onCreativeTabChanged(CreativeModeTab tab, CallbackInfo ci) {
        CreativeModeInventoryScreen self = (CreativeModeInventoryScreen)(Object) this;
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // Reposition all MKSlots for the new context
        MenuKit.onCreativeTabChanged(self, context);

        // Recreate buttons (requires protected Screen methods)
        var toRemove = this.children().stream()
                .filter(child -> child instanceof MKButton).toList();
        for (var widget : toRemove) this.removeWidget(widget);

        var acc = (AbstractContainerScreenAccessor)(Object) this;
        var buttons = MenuKit.createButtonsForMenu(context,
                acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos());
        for (MKButton btn : buttons) this.addRenderableWidget(btn);
    }

    // ── Click Interception ──────────────────────────────────────────────────

    /**
     * Prevents creative tabs from capturing clicks that land inside MKPanels.
     *
     * <p>Vanilla's {@code mouseClicked()} calls {@code checkTabClicked()} for
     * each tab BEFORE processing widgets. By redirecting this call, we can
     * return false (tab not clicked) when the mouse is inside an MKPanel,
     * allowing the click to fall through to widget processing instead.
     */
    @Redirect(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;checkTabClicked(Lnet/minecraft/world/item/CreativeModeTab;DD)Z"))
    private boolean menuKit$blockTabClickInsidePanel(
            CreativeModeInventoryScreen self, CreativeModeTab tab, double relMouseX, double relMouseY) {
        var acc = (AbstractContainerScreenAccessor)(Object) this;
        MKContext context = MKContext.fromScreen((AbstractContainerScreen<?>)(Object) this);
        if (context != null) {
            double screenMouseX = relMouseX + acc.trevorMod$getLeftPos();
            double screenMouseY = relMouseY + acc.trevorMod$getTopPos();
            if (MenuKit.isClickInsideAnyPanel(screenMouseX, screenMouseY,
                    acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos(), context)) {
                return false;
            }
        }
        return this.checkTabClicked(tab, relMouseX, relMouseY);
    }

    /**
     * Same redirect for mouseReleased — vanilla ALSO checks tabs on mouse release.
     * Without this, the tab switches when the mouse button is released even though
     * the click was blocked.
     */
    @Redirect(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;checkTabClicked(Lnet/minecraft/world/item/CreativeModeTab;DD)Z"))
    private boolean menuKit$blockTabReleaseInsidePanel(
            CreativeModeInventoryScreen self, CreativeModeTab tab, double relMouseX, double relMouseY) {
        var acc = (AbstractContainerScreenAccessor)(Object) this;
        MKContext context = MKContext.fromScreen((AbstractContainerScreen<?>)(Object) this);
        if (context != null) {
            double screenMouseX = relMouseX + acc.trevorMod$getLeftPos();
            double screenMouseY = relMouseY + acc.trevorMod$getTopPos();
            if (MenuKit.isClickInsideAnyPanel(screenMouseX, screenMouseY,
                    acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos(), context)) {
                return false;
            }
        }
        return this.checkTabClicked(tab, relMouseX, relMouseY);
    }

    // ── Tooltip Suppression ─────────────────────────────────────────────────

    /**
     * Suppresses creative tab tooltips when the mouse is over an MKPanel.
     * Without this, tab tooltips render on top of panel content.
     */
    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
    private void menuKit$suppressTabTooltip(GuiGraphics graphics, CreativeModeTab tab,
                                             int mouseX, int mouseY,
                                             CallbackInfoReturnable<Boolean> cir) {
        var acc = (AbstractContainerScreenAccessor)(Object) this;
        MKContext context = MKContext.fromScreen(
                (AbstractContainerScreen<?>)(Object) this);
        if (context == null) return;

        if (MenuKit.isClickInsideAnyPanel(mouseX, mouseY,
                acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos(), context)) {
            cir.setReturnValue(false);
        }
    }

    // ── Click Bounds ────────────────────────────────────────────────────────

    /**
     * Expands the "clicked inside" bounds to include MKPanel areas.
     * Prevents items from being dropped when clicking on panels.
     */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void menuKit$expandClickBounds(double mouseX, double mouseY,
                                            int leftPos, int topPos,
                                            CallbackInfoReturnable<Boolean> cir) {
        MKContext context = MKContext.fromScreen(
                (AbstractContainerScreen<?>)(Object) this);
        if (context != null && MenuKit.isClickInsideAnyPanel(
                mouseX, mouseY, leftPos, topPos, context)) {
            cir.setReturnValue(false);
        }
    }

    // ── Screen Close ───────────────────────────────────────────────────────

    /**
     * When the creative screen closes, reset inventoryMenu slot positions
     * back to survival layout. onCreativeTabChanged repositions them for
     * the creative layout so overlay panels compute correct bounds — this
     * undoes that so survival inventory overlays aren't mispositioned.
     */
    @Inject(method = "removed", at = @At("HEAD"))
    private void menuKit$onCreativeScreenClosed(CallbackInfo ci) {
        MenuKit.onCreativeScreenClosed();
    }

}
