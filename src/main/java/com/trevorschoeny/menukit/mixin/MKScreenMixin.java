package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.widget.MKButton;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.panel.MKPanelStateRegistry;
import com.trevorschoeny.menukit.widget.MKSlot;
import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * MenuKit internal mixin — adds MKButtons to container screens and renders
 * panel + slot backgrounds in two stages for correct z-ordering.
 *
 * <p>Stage A: Panel backgrounds render at RETURN of {@code renderBackground}
 * (after inventory texture, BEFORE widgets/buttons). This is in screen space
 * so positions need leftPos/topPos offset.
 *
 * <p>Stage B: Slot backgrounds render at HEAD of {@code renderSlots}
 * (after widgets, BEFORE slot items). This is in container-translated space
 * so positions use 0,0 offset.
 *
 * <p>Targets {@link AbstractContainerScreen} so it works for ALL container
 * screens (inventory, chests, crafting tables, etc.). Only creates buttons
 * for panels registered to the screen's context.
 *
 * <p>Uses {@link MKContext#fromScreen} to resolve the active context,
 * replacing scattered menuClass + isCreative logic.
 *
 * <p>Runs on CLIENT only.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(value = AbstractContainerScreen.class, priority = 900)
public class MKScreenMixin extends Screen {

    @Shadow private Slot hoveredSlot;

    protected MKScreenMixin() { super(Component.empty()); }

    /**
     * After screen initialization: create and add MKButtons for all panels
     * registered to this screen's context.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void menuKit$addButtons(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        // Resolve context — handles survival, creative inventory, creative tabs, etc.
        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // Reset panels that were registered with .hidden() back to hidden state.
        // This ensures toggle-revealed panels (like pockets) don't persist
        // across screen open/close cycles.
        MenuKit.resetStartHiddenPanels();

        // Clear element visibility overrides so stale state doesn't persist
        // across screen reopens.
        MKPanelStateRegistry.cleanup();

        int leftPos = acc.trevorMod$getLeftPos();
        int topPos  = acc.trevorMod$getTopPos();

        // Remove any previously-added MKButtons (init can be called multiple times
        // due to screen resize, chunk loading, etc.)
        // removeWidget handles both children and renderables lists
        var toRemove = this.children().stream()
                .filter(child -> child instanceof MKButton)
                .toList();
        for (var widget : toRemove) {
            this.removeWidget(widget);
        }

        var buttons = MenuKit.createButtonsForMenu(context, leftPos, topPos);
        for (MKButton btn : buttons) {
            this.addRenderableWidget(btn);
        }
    }

    /**
     * Stage A: Renders panel backgrounds at RETURN of renderBackground.
     * This runs AFTER the container texture is drawn but BEFORE widgets/buttons,
     * so the panel background appears BEHIND buttons correctly.
     *
     * <p>Also resolves collision avoidance positions, updates slot/button positions,
     * and tracks panel hover state via MenuKit's own detection.
     */
    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void menuKit$renderPanelBackgrounds(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float f, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        int leftPos = acc.trevorMod$getLeftPos();
        int topPos  = acc.trevorMod$getTopPos();

        // Resolve collision avoidance positions
        boolean effectsActive = self.showsActiveEffects();
        int effectsHeight = 0;
        if (effectsActive && (context == MKContext.SURVIVAL_INVENTORY
                || context == MKContext.CREATIVE_INVENTORY)) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                effectsHeight = mc.player.getActiveEffects().size() * 33;
            }
        }
        MenuKit.resolvePositionsWithAvoidance(context, effectsActive, effectsHeight);

        // Create button widgets for any panels registered after init() ran.
        // Dynamically-registered panels (e.g., sort/move-matching buttons for
        // peek containers) miss the initial createButtonsForMenu() call in init().
        // This lazy check runs every frame but only creates buttons once per panel.
        var missingButtons = MenuKit.createMissingButtons(self, context, leftPos, topPos);
        for (var btn : missingButtons) {
            this.addRenderableWidget(btn);
        }

        // Update slot + button positions to match resolved positions
        MenuKit.updateSlotPositions(self.getMenu(), context);
        MenuKit.updateButtonPositions(self, context, leftPos, topPos);

        // Render panel backgrounds in screen space (leftPos/topPos offset).
        // Also tracks which panel the mouse is hovering via MenuKit's own detection.
        // NOTE: mouseX/mouseY from renderBackground may be unreliable in 1.21.11.
        // We use lastMouseX/Y captured from render() instead.
        int mx = MenuKit.getLastMouseX();
        int my = MenuKit.getLastMouseY();
        MenuKit.renderPanelBackgrounds(graphics, context, leftPos, topPos, mx, my);
    }

    /**
     * Stage B: Renders slot backgrounds, ghost icons, and hover highlights
     * at HEAD of renderSlots. This runs inside the container-translated
     * coordinate space, AFTER widgets, BEFORE slot items.
     */
    @Inject(method = "renderSlots", at = @At("HEAD"))
    private void menuKit$renderSlotBackgrounds(GuiGraphics graphics, int mouseX,
                                                int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        int leftPos = acc.trevorMod$getLeftPos();
        int topPos  = acc.trevorMod$getTopPos();

        // Use mouse position captured during renderBackground (which gets correct
        // screen-space coords from render()). The renderSlots params are unreliable
        // in 1.21.11 — they can be negative or stale.
        int actualMouseX = MenuKit.getLastMouseX();
        int actualMouseY = MenuKit.getLastMouseY();

        // Container-translated space — (0, 0) offset.
        // Convert screen-space mouse to container-relative to match slot.x/slot.y.
        MenuKit.renderSlotBackgrounds(graphics, self.getMenu(), context, 0, 0,
                actualMouseX - leftPos, actualMouseY - topPos);
    }

    /**
     * Stage C: Renders overlay icons and border decorations ON TOP of slot items.
     * Injected at RETURN of renderSlots — after vanilla has drawn all items —
     * so overlays and borders appear above the item layer.
     *
     * <p>Only processes slots with decorations set, so undecorated slots have
     * zero rendering overhead.
     */
    @Inject(method = "renderSlots", at = @At("RETURN"))
    private void menuKit$renderSlotOverlays(GuiGraphics graphics, int mouseX,
                                             int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // Container-translated space — (0, 0) offset, matching renderSlotBackgrounds.
        MenuKit.renderSlotOverlays(graphics, self.getMenu(), context, 0, 0);

        // Lock overlays — renders lock and sort-lock tints for ALL menu slots
        // (both MK-panel slots and vanilla container slots like chest/furnace).
        // Done after overlays so the tint appears on top of everything.
        MenuKit.renderLockOverlays(graphics, self.getMenu());
    }

    /**
     * Captures the actual screen-space mouse position from renderContents().
     *
     * <p>Why renderContents and not render()? In 1.21.11, InventoryScreen and
     * CreativeModeInventoryScreen override render(), so an @Inject on
     * AbstractContainerScreen.render() HEAD never fires for those screens.
     * But renderContents() is NOT overridden — it's where vanilla calls
     * getHoveredSlot() with the correct mouse coords. This is the single
     * reliable capture point across ALL screen subclasses.
     */
    @Inject(method = "renderContents", at = @At("HEAD"))
    private void menuKit$captureMousePosition(GuiGraphics graphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        MenuKit.captureMousePosition(mouseX, mouseY);
    }

    /**
     * Renders a tooltip for empty MKSlots that have an emptyTooltip supplier.
     * Vanilla's renderTooltip only shows tooltips for slots WITH items, so
     * this fills the gap — showing "Mark as empty hand" / "Remove empty hand"
     * when hovering an empty slot with an empty cursor.
     *
     * <p>Uses MenuKit's own hover detection ({@link MenuKit#getHoveredMKSlot()})
     * instead of vanilla's {@code hoveredSlot}. MenuKit's detection runs during
     * {@code renderSlotBackgrounds} and works reliably in ALL screen contexts,
     * whereas vanilla's {@code hoveredSlot} can miss MKSlots in certain screen
     * subclasses.
     *
     * <p>Injected at RETURN of {@code renderContents} — NOT {@code render()}.
     * InventoryScreen and CreativeModeInventoryScreen both override render(),
     * so an injection on AbstractContainerScreen.render() never fires for those
     * screens. renderContents() is not overridden, so this works everywhere.
     * Uses stored mouse coordinates from the same renderContents capture point.
     */
    @Inject(method = "renderContents", at = @At("RETURN"))
    private void menuKit$renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo ci) {
        // Only show when cursor is empty — if holding an item, vanilla handles it
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        if (!self.getMenu().getCarried().isEmpty()) return;

        // Use MenuKit's own hover detection first (works in all injected-panel
        // contexts), then fall back to vanilla's hoveredSlot (works for standalone
        // screens where MenuKit.renderSlotBackgrounds isn't called).
        // Check for empty-slot tooltip on any MenuKit-managed slot
        net.minecraft.world.inventory.Slot hoveredMK = MenuKit.getHoveredMKSlot();
        if (hoveredMK == null && hoveredSlot != null) {
            MKSlotState hState = MKSlotStateRegistry.get(hoveredSlot);
            if (hState != null && hState.isMenuKitSlot()) {
                hoveredMK = hoveredSlot;
            }
        }
        if (hoveredMK != null && !hoveredMK.hasItem() && hoveredMK.isActive()) {
            MKSlotState hState = MKSlotStateRegistry.get(hoveredMK);
            if (hState != null) {
                var tooltipSupplier = hState.getEmptyTooltip();
                if (tooltipSupplier != null) {
                    Component text = tooltipSupplier.get();
                    if (text != null) {
                        graphics.setTooltipForNextFrame(text, mouseX, mouseY);
                    }
                }
            }
        }
    }

    /**
     * Prevents MKSlots outside the container bounds from being treated as
     * "clicked outside" (which vanilla interprets as THROW/drop item).
     *
     * <p>MKSlots are positioned outside the container (e.g., to the right of
     * the inventory). Without this fix, vanilla's {@code hasClickedOutside()}
     * returns true for these slots, causing items to drop instead of being
     * picked up.
     *
     * <p>If the click hits any MKPanel's bounds, we report it as "inside."
     */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void menuKit$expandClickBounds(double mouseX, double mouseY,
                                            int leftPos, int topPos,
                                            CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        // Check if the click is within any registered MKPanel's bounds
        if (MenuKit.isClickInsideAnyPanel(mouseX, mouseY, leftPos, topPos, context)) {
            cir.setReturnValue(false); // NOT outside — it's on a panel
        }
    }

    // ── Hovered Slot Fix ─────────────────────────────────────────────────────
    //
    // Vanilla's getHoveredSlot() misses MKSlots positioned outside the
    // container image bounds (e.g., peek panels to the left of the inventory).
    // The vanilla isHovering() method clips to imageWidth/imageHeight, so
    // slots with negative x or x >= imageWidth are never detected as hovered.
    //
    // This injection runs at RETURN of getHoveredSlot(). If vanilla found
    // nothing, we do our own unconstrained scan of all active slots using
    // the same hit-test logic as MKDragMixin.menukit$findSlotAt().

    /**
     * Fixes vanilla's hovered slot detection for MKSlots outside the container
     * image bounds. Vanilla's {@code isHovering} clips to imageWidth/imageHeight,
     * so panels positioned to the left or right of the container are invisible
     * to vanilla's hover detection. If vanilla returned null, we scan all active
     * slots without the image bounds constraint.
     */
    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void menuKit$fixHoveredSlotForMKSlots(double mouseX, double mouseY,
                                                   CallbackInfoReturnable<Slot> cir) {
        // Only intervene when vanilla found nothing — don't override its result
        if (cir.getReturnValue() != null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        // Convert screen-space mouse position to container-relative coordinates
        double relX = mouseX - acc.trevorMod$getLeftPos();
        double relY = mouseY - acc.trevorMod$getTopPos();

        // Scan all slots with the same hit-test as vanilla but without image bounds clipping
        for (Slot slot : self.getMenu().slots) {
            if (!slot.isActive()) continue;
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                cir.setReturnValue(slot);
                return;
            }
        }
    }

    // ── Scroll Container Input ──────────────────────────────────────────────
    //
    // Intercepts mouseScrolled at HEAD (BEFORE MKScrollMixin which handles
    // slot-level scroll events). If the mouse is over a scroll container
    // viewport, the scroll offset is updated and the event is consumed,
    // preventing both the slot-level SCROLL event and vanilla's own scroll
    // handling from firing.
    //
    // Priority note: Both MKScreenMixin and MKScrollMixin inject at HEAD
    // of mouseScrolled. MKScreenMixin uses priority = 900 (lower = fires
    // first) while MKScrollMixin uses the default priority of 1000, so
    // this mixin's scroll container handler always fires before the
    // slot-level scroll handler. If the scroll container consumes the
    // event, MKScrollMixin never sees it.

    /**
     * Handles mouse wheel scrolling for scroll container viewports.
     * If the cursor is over a scroll viewport, updates the scroll offset
     * and cancels vanilla's scroll handling.
     *
     * <p>Fires BEFORE MKScrollMixin's slot-level scroll handler.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void menuKit$scrollContainerInput(double mouseX, double mouseY,
                                               double scrollX, double scrollY,
                                               CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        int leftPos = acc.trevorMod$getLeftPos();
        int topPos  = acc.trevorMod$getTopPos();

        // Check if scroll should be consumed by a scroll container viewport
        if (MenuKit.handleScrollContainerInput(mouseX, mouseY, scrollY,
                leftPos, topPos, context)) {
            cir.setReturnValue(true); // Consumed — don't let vanilla or MKScrollMixin handle it
        }
    }

    // ── Tab Click Input ─────────────────────────────────────────────────────
    //
    // Intercepts mouseClicked at HEAD to check for tab bar button clicks.
    // If a tab button was clicked, switches the active tab and consumes the
    // click so it doesn't reach vanilla's slot click handling.
    //
    // In 1.21.11, mouseClicked takes (MouseButtonEvent, boolean) but the
    // underlying Screen.mouseClicked(double, double, int) is also available.
    // We inject on the Screen-level method since AbstractContainerScreen
    // delegates to it. The 1.21.11 signature wraps it in MouseButtonEvent.

    /**
     * Handles mouse clicks on tab bar buttons. If the click lands on a tab
     * button, switches the active tab and cancels the event.
     */
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$tabClickInput(net.minecraft.client.input.MouseButtonEvent event,
                                        boolean flag,
                                        CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        MKContext context = MKContext.fromScreen(self);
        if (context == null) return;

        int leftPos = acc.trevorMod$getLeftPos();
        int topPos  = acc.trevorMod$getTopPos();

        // Extract mouse position and button from the event.
        // Use MenuKit's captured mouse position for consistency with rendering.
        double mouseX = MenuKit.getLastMouseX();
        double mouseY = MenuKit.getLastMouseY();
        int button = event.button();

        // Check if click should be consumed by a tab bar button
        if (MenuKit.handleTabClick(mouseX, mouseY, button, leftPos, topPos, context)) {
            cir.setReturnValue(true); // Consumed — tab was clicked
        }
    }

    // ── Hover Detection Fix ─────────────────────────────────────────────────
    //
    // Vanilla's getHoveredSlot() misses MKSlots positioned outside the
    // container's image bounds (e.g., peek panels to the left). This inject
    // extends vanilla's detection to ALL active slots when vanilla found nothing.
    // Fixes hoveredSlot for key presses, hover events, scroll, tooltips, etc.

    /**
     * Extends vanilla's hover detection to MKSlots outside the container image.
     *
     * <p>Vanilla's {@code getHoveredSlot()} only finds slots within the
     * {@code imageWidth × imageHeight} bounds. MKSlots in panels positioned
     * outside those bounds (left-side peek panels, right-side pockets) are
     * invisible. This RETURN inject does the same hit-test without the bounds
     * clipping when vanilla returned null.
     */
    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void menuKit$extendHoverToAllSlots(double mouseX, double mouseY,
                                                CallbackInfoReturnable<Slot> cir) {
        // Only act if vanilla found nothing — never override vanilla's result.
        if (cir.getReturnValue() != null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor)(Object) this;

        // Convert screen-space mouse to container-relative coordinates.
        double relX = mouseX - acc.trevorMod$getLeftPos();
        double relY = mouseY - acc.trevorMod$getTopPos();

        // Same hit-test as vanilla, but without image bounds clipping.
        for (Slot slot : self.getMenu().slots) {
            if (!slot.isActive()) continue;
            if (relX >= slot.x - 1 && relX < slot.x + 17
                    && relY >= slot.y - 1 && relY < slot.y + 17) {
                cir.setReturnValue(slot);
                return;
            }
        }
    }
}
