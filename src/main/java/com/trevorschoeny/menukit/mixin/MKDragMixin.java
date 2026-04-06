package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKDragContext;
import com.trevorschoeny.menukit.MKDragMode;
import com.trevorschoeny.menukit.MKDragRegistry;
import com.trevorschoeny.menukit.MKDragSlotEvent;
import com.trevorschoeny.menukit.MKVanillaDragSuppress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

/**
 * Implements custom drag modes by polling every render frame.
 *
 * <p>Vanilla does NOT fire {@code mouseDragged} after a shift-click (the click
 * is fully consumed). So we can't use mouse event hooks. Instead:
 * <ol>
 *   <li>{@code mouseClicked} — arm the drag when conditions match</li>
 *   <li>{@code renderContents} — every frame, read live cursor position,
 *       find slot under it, fire onSlotEntered if it's a new slot</li>
 *   <li>{@code mouseReleased} — end the drag</li>
 * </ol>
 *
 * <p>We use vanilla's own {@link MouseHandler#getScaledXPos} for coordinate
 * conversion and replicate {@code isHovering} for slot hit-testing.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKDragMixin {

    @Unique private @Nullable MKDragMode menukit$activeDragMode;
    @Unique private @Nullable MKDragContext menukit$dragContext;
    @Unique private int menukit$lastDragSlotIndex = -1;
    @Unique private int menukit$activeDragButton = -1;  // which mouse button started the drag

    // ── mouseClicked: Arm the drag ──────────────────────────────────────────

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"))
    private void menukit$onMouseClicked(MouseButtonEvent event, boolean flag,
                                         CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        MKDragContext ctx = new MKDragContext(event.button(), event.modifiers(),
                mc.player, self.getMenu());
        MKDragMode mode = MKDragRegistry.findActive(ctx);
        if (mode == null) return;

        // Arm — record starting slot so we skip it (vanilla already handled it)
        menukit$activeDragMode = mode;
        menukit$dragContext = ctx;
        menukit$activeDragButton = event.button();

        // Suppress vanilla quick-craft for non-LMB drags (RMB/MMB would
        // conflict with vanilla's hold-and-drag distribution). LMB drags
        // only suppress if the mode explicitly requests it.
        if (event.button() != 0 || mode.suppressVanillaDrag()) {
            MKVanillaDragSuppress.suppress(self);
        }

        var acc = (AbstractContainerScreenAccessor) (Object) this;
        Slot startSlot = menukit$findSlotAt(event.x(), event.y(),
                self, acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos());
        menukit$lastDragSlotIndex = startSlot != null ? startSlot.index : -1;

        if (mode.onDragStart() != null) {
            mode.onDragStart().accept(ctx);
        }
    }

    // ── renderContents: Poll every frame ────────────────────────────────────
    //
    // This is the same injection point as MKHoverTrackingMixin. It runs every
    // frame while any container screen is open. We use the live cursor position
    // from MouseHandler (not the render params, which are stale during drags).

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void menukit$pollDragSlot(GuiGraphics graphics, int mouseX, int mouseY,
                                       float partialTick, CallbackInfo ci) {
        if (menukit$activeDragMode == null || menukit$dragContext == null) return;

        // Check if the activating mouse button is still held
        Minecraft mc = Minecraft.getInstance();
        boolean mouseHeld = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                mc.getWindow().handle(),
                menukit$activeDragButton) == 1;
        if (!mouseHeld) {
            // Mouse was released without mouseReleased firing (edge case)
            menukit$endDrag();
            return;
        }

        // Read live cursor position using vanilla's own scaling
        MouseHandler mh = mc.mouseHandler;
        var window = mc.getWindow();
        double liveX = MouseHandler.getScaledXPos(window, mh.xpos());
        double liveY = MouseHandler.getScaledYPos(window, mh.ypos());

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        var acc = (AbstractContainerScreenAccessor) (Object) this;
        Slot slot = menukit$findSlotAt(liveX, liveY,
                self, acc.trevorMod$getLeftPos(), acc.trevorMod$getTopPos());

        int slotIndex = slot != null ? slot.index : -1;
        if (slot != null && slotIndex != menukit$lastDragSlotIndex) {
            menukit$lastDragSlotIndex = slotIndex;
            menukit$dragContext.addVisitedSlot(slot);
            menukit$activeDragMode.onSlotEntered().accept(
                    new MKDragSlotEvent(slot, menukit$dragContext));
        }
    }

    // ── mouseReleased: End the drag ─────────────────────────────────────────

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("HEAD"))
    private void menukit$onMouseReleased(MouseButtonEvent event,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (menukit$activeDragMode == null || menukit$dragContext == null) return;
        if (event.button() != menukit$activeDragButton) return;
        menukit$endDrag();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @Unique
    private void menukit$endDrag() {
        if (menukit$activeDragMode != null && menukit$dragContext != null
                && menukit$activeDragMode.onDragEnd() != null) {
            menukit$activeDragMode.onDragEnd().accept(menukit$dragContext);
        }

        // Restore vanilla quick-craft state if we suppressed it
        if (MKVanillaDragSuppress.isSuppressed()) {
            MKVanillaDragSuppress.restore(
                    (AbstractContainerScreen<?>) (Object) this);
        }

        menukit$activeDragMode = null;
        menukit$dragContext = null;
        menukit$lastDragSlotIndex = -1;
        menukit$activeDragButton = -1;
    }

    @Unique
    private static @Nullable Slot menukit$findSlotAt(double mouseX, double mouseY,
                                                      AbstractContainerScreen<?> screen,
                                                      int leftPos, int topPos) {
        double relX = mouseX - leftPos;
        double relY = mouseY - topPos;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                return slot;
            }
        }
        return null;
    }
}
