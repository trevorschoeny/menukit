package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKDragContext;
import com.trevorschoeny.menukit.MKDragMode;
import com.trevorschoeny.menukit.MKDragRegistry;
import com.trevorschoeny.menukit.MKDragSlotEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/**
 * Tracks click-drag operations across inventory slots and dispatches
 * to registered {@link MKDragMode} handlers.
 *
 * <p>On left-click (button 0), checks modifier keys and asks
 * {@link MKDragRegistry#findActive} if a custom drag mode should activate.
 * If so, tracks the drag through mouseDragged and fires onSlotEntered
 * for each new slot the cursor visits. On mouseReleased, fires onDragEnd
 * and clears state.
 *
 * <p>Does NOT cancel vanilla behavior — runs alongside it. The drag mode's
 * handlers are responsible for any slot manipulation (e.g., quick-moving items).
 *
 * <p>Runs on CLIENT only. Part of the <b>MenuKit</b> drag mode API.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKDragMixin {

    @Shadow protected @Nullable Slot hoveredSlot;

    /** The currently active custom drag mode, or null if none. */
    @Unique private @Nullable MKDragMode menukit$activeDragMode;

    /** Context for the current drag operation. */
    @Unique private @Nullable MKDragContext menukit$dragContext;

    /** The last slot the cursor was over during this drag (to detect transitions). */
    @Unique private @Nullable Slot menukit$lastDragSlot;

    // ── Mouse Click: Check for Custom Drag Activation ────────────────────────
    //
    // On left-click, sample modifier keys from GLFW, build a context, and ask
    // the registry if any custom drag mode wants to activate. If yes, store the
    // mode+context and fire the initial events.

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void menukit$onMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        // Only intercept left-click (button 0). Right-click drags are vanilla's
        // distribute-one, middle-click is creative duplicate.
        if (button != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // ── Sample GLFW modifier keys ────────────────────────────────────
        // We read key state directly from GLFW rather than relying on
        // event parameters, since mouseClicked doesn't pass modifiers.
        long window = mc.getWindow().handle();
        int mods = 0;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1)
            mods |= 0x1;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1)
            mods |= 0x2;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == 1)
            mods |= 0x4;

        // ── Ask the registry for a matching drag mode ────────────────────
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        MKDragContext ctx = new MKDragContext(mods, mc.player, self.getMenu());
        MKDragMode mode = MKDragRegistry.findActive(ctx);
        if (mode == null) return;

        // ── Activate the custom drag ─────────────────────────────────────
        menukit$activeDragMode = mode;
        menukit$dragContext = ctx;
        menukit$lastDragSlot = null;

        // Fire onDragStart callback if the mode defined one
        if (mode.onDragStart() != null) {
            mode.onDragStart().accept(ctx);
        }

        // Process the first slot if cursor is already over one
        if (hoveredSlot != null) {
            menukit$lastDragSlot = hoveredSlot;
            ctx.addVisitedSlot(hoveredSlot);
            mode.onSlotEntered().accept(new MKDragSlotEvent(hoveredSlot, ctx));
        }
    }

    // ── Mouse Drag: Track Slot Transitions ───────────────────────────────────
    //
    // While a custom drag is active, check if the cursor has moved to a new
    // slot. If so, record it and fire onSlotEntered. Each slot is only
    // processed once per drag (deduplication via visitedSlots).

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void menukit$onMouseDragged(double mouseX, double mouseY, int button,
                                         double dragX, double dragY,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (menukit$activeDragMode == null || menukit$dragContext == null) return;
        if (button != 0) return;

        // Check if cursor moved to a new slot
        if (hoveredSlot != null && hoveredSlot != menukit$lastDragSlot) {
            menukit$lastDragSlot = hoveredSlot;
            // Only process each slot once per drag
            if (!menukit$dragContext.visitedSlots().contains(hoveredSlot)) {
                menukit$dragContext.addVisitedSlot(hoveredSlot);
                menukit$activeDragMode.onSlotEntered().accept(
                        new MKDragSlotEvent(hoveredSlot, menukit$dragContext));
            }
        }
    }

    // ── Mouse Release: End the Drag ──────────────────────────────────────────
    //
    // When the mouse is released, fire onDragEnd and clear all drag state.

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void menukit$onMouseReleased(double mouseX, double mouseY, int button,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (menukit$activeDragMode == null || menukit$dragContext == null) return;
        if (button != 0) return;

        // Fire onDragEnd callback if the mode defined one
        if (menukit$activeDragMode.onDragEnd() != null) {
            menukit$activeDragMode.onDragEnd().accept(menukit$dragContext);
        }

        // Clear all drag state
        menukit$activeDragMode = null;
        menukit$dragContext = null;
        menukit$lastDragSlot = null;
    }
}
