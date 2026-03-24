package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKEventBus;
import com.trevorschoeny.menukit.MKEventHelper;
import com.trevorschoeny.menukit.MKSlotEvent;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks hover state changes across slots and fires hover/drag events
 * through the {@link MKEventBus}.
 *
 * <p>Fires three event types:
 * <ul>
 *   <li>{@link MKSlotEvent.Type#HOVER_ENTER} — cursor moves onto a slot</li>
 *   <li>{@link MKSlotEvent.Type#HOVER_EXIT} — cursor leaves a slot</li>
 *   <li>{@link MKSlotEvent.Type#DRAG_OVER} — cursor moves onto a slot while
 *       holding items (cursor stack is not empty)</li>
 * </ul>
 *
 * <p><b>Injection point:</b> HEAD of {@code renderContents}. This is the
 * single reliable capture point across ALL screen subclasses — vanilla's
 * InventoryScreen and CreativeModeInventoryScreen both override render(),
 * but renderContents() is NOT overridden. By the time renderContents fires,
 * vanilla has already resolved {@code hoveredSlot} via getHoveredSlot().
 *
 * <p><b>Guard against menu reconstruction:</b> When vanilla rebuilds slots
 * (e.g., creative tab switch), hoveredSlot can change to a different Slot
 * object even though the cursor didn't move. We guard against this by
 * tracking the last known mouse position and only firing events when the
 * mouse has actually moved OR the slot identity genuinely changed under
 * mouse movement.
 *
 * <p>Runs on CLIENT only. Part of the <b>MenuKit</b> event system.
 */
@Mixin(AbstractContainerScreen.class)
public class MKHoverTrackingMixin {

    // ── Vanilla Field Access ─────────────────────────────────────────────────
    //
    // hoveredSlot is the slot currently under the cursor, set by vanilla's
    // getHoveredSlot() during the render pipeline. May be null if the cursor
    // is not over any slot.

    @Shadow private Slot hoveredSlot;

    // ── Tracking State ───────────────────────────────────────────────────────
    //
    // We track the PREVIOUS hovered slot so we can detect transitions.
    // Also track the last mouse position to distinguish genuine mouse-driven
    // hover changes from menu reconstruction artifacts.

    /** The slot that was hovered on the PREVIOUS frame. Null if no slot was hovered. */
    @Unique
    private Slot menuKit$previousHoveredSlot;

    /** Last known mouse X, used to detect actual mouse movement. */
    @Unique
    private int menuKit$lastMouseX = -1;

    /** Last known mouse Y, used to detect actual mouse movement. */
    @Unique
    private int menuKit$lastMouseY = -1;

    // ── Hover Tracking Injection ─────────────────────────────────────────────
    //
    // Runs at HEAD of renderContents, AFTER vanilla has set hoveredSlot
    // but BEFORE any rendering. This is the earliest reliable point where
    // we know which slot the cursor is over.

    /**
     * Checks for hover state changes each frame and fires the appropriate
     * MenuKit events through the event bus.
     *
     * <p>Logic:
     * <ol>
     *   <li>Compare current hoveredSlot to previousHoveredSlot</li>
     *   <li>If they're the same object (identity check), no change — skip</li>
     *   <li>If they differ AND the mouse has moved, fire events:
     *       <ul>
     *         <li>HOVER_EXIT for the old slot (if it was non-null)</li>
     *         <li>HOVER_ENTER or DRAG_OVER for the new slot (if non-null)</li>
     *       </ul>
     *   </li>
     *   <li>Update the tracking state for next frame</li>
     * </ol>
     */
    @Inject(method = "renderContents", at = @At("HEAD"))
    private void menuKit$trackHoverChanges(GuiGraphics graphics, int mouseX, int mouseY,
                                            float partialTick, CallbackInfo ci) {

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        // ── Context gate ─────────────────────────────────────────────────
        // Only fire for screens MenuKit recognizes. Screens without a
        // context (e.g., standalone MKMenu screens) are skipped.
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // ── Player resolution ────────────────────────────────────────────
        // Need the player for event construction. Should always be
        // present on client, but guard against edge cases.
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Mouse movement detection ─────────────────────────────────────
        // Use MenuKit's captured mouse position (set during this same
        // renderContents call by MKScreenMixin). Fall back to the method
        // params if capture hasn't happened yet this frame.
        int currentMouseX = MenuKit.getLastMouseX();
        int currentMouseY = MenuKit.getLastMouseY();
        if (currentMouseX == 0 && currentMouseY == 0) {
            // Capture hasn't fired yet this frame — use method params
            currentMouseX = mouseX;
            currentMouseY = mouseY;
        }

        boolean mouseMoved = (currentMouseX != menuKit$lastMouseX
                           || currentMouseY != menuKit$lastMouseY);

        // ── Slot change detection ────────────────────────────────────────
        // Identity comparison (==) is intentional. We want to detect when
        // the actual Slot object changes, not just value equality.
        Slot currentSlot = this.hoveredSlot;
        Slot previousSlot = this.menuKit$previousHoveredSlot;

        boolean slotChanged = (currentSlot != previousSlot);

        if (slotChanged && mouseMoved) {
            // ── Fire HOVER_EXIT for the old slot ─────────────────────────
            // The cursor was on a slot last frame and has now moved away
            // (either to a different slot or to empty space).
            if (previousSlot != null) {
                MKSlotEvent exitEvent = MKEventHelper.buildHoverEvent(
                        MKSlotEvent.Type.HOVER_EXIT, previousSlot, self, player);
                if (exitEvent != null) {
                    MKEventBus.fire(exitEvent);
                }
            }

            // ── Fire HOVER_ENTER or DRAG_OVER for the new slot ───────────
            // The cursor has moved onto a new slot. If the player is
            // holding items (cursor stack not empty), this is a DRAG_OVER
            // event instead of HOVER_ENTER — useful for visual feedback
            // during item dragging.
            if (currentSlot != null) {
                boolean isDragging = !self.getMenu().getCarried().isEmpty();
                MKSlotEvent.Type enterType = isDragging
                        ? MKSlotEvent.Type.DRAG_OVER
                        : MKSlotEvent.Type.HOVER_ENTER;

                MKSlotEvent enterEvent = MKEventHelper.buildHoverEvent(
                        enterType, currentSlot, self, player);
                if (enterEvent != null) {
                    MKEventBus.fire(enterEvent);
                }
            }
        }

        // ── Update tracking state ────────────────────────────────────────
        // Always update, even if no events fired (e.g., slot changed due
        // to menu reconstruction without mouse movement — we silently
        // accept the new state so the NEXT genuine move fires correctly).
        this.menuKit$previousHoveredSlot = currentSlot;
        this.menuKit$lastMouseX = currentMouseX;
        this.menuKit$lastMouseY = currentMouseY;
    }
}
