package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.MKFocus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 18r-5 follow-up — widget-hover suppression. Compensates for MK's
 * panel-eat input contract leaving a corresponding VISUAL gap: vanilla
 * widgets (Buttons, sliders, list rows) underneath an MK panel — or
 * underneath an active element overlay like an open Dropdown popover —
 * still highlight on hover, because vanilla's per-widget {@code isHovered}
 * is purely a bounds-check against the cursor position. Clicks were
 * already correctly eaten by the opacity contract; this mixin closes
 * the visual loop.
 *
 * <h3>Mechanism</h3>
 *
 * Overrides {@link AbstractWidget#isHovered()} (the getter) to return
 * {@code false} when:
 * <ul>
 *   <li>the cursor is covered by any MK content (opaque panel background,
 *       active element-overlay bounds, or a solid interactive element — see
 *       {@link MKFocus#isCursorCovered}), AND</li>
 *   <li>this widget is NOT MK-managed — i.e., NOT registered via
 *       {@link MKFocus#addWidget}. MK-managed widgets (TextField's
 *       wrapped EditBox, Keybindery's SearchBox EditBox, any consumer
 *       widget intentionally placed inside an MK panel) keep their
 *       hover state because their visual feedback is desired.</li>
 * </ul>
 *
 * <h3>Why override the getter, not the field</h3>
 *
 * Vanilla widget code (e.g. {@code Button.renderWidget}) consults
 * {@code isHoveredOrFocused()} which calls {@code isHovered()} (the
 * method). Overriding the method covers the dominant rendering path
 * without needing to fight vanilla's per-frame field-set in
 * {@code AbstractWidget.render}. Subclasses that read the field
 * directly (rare) won't be covered — fold-on-evidence if a vanilla
 * widget kind exhibits the hover leak through a direct-field read.
 *
 * <h3>Sibling to slot hover suppression</h3>
 *
 * {@code MKModalHoverMixin} suppresses SLOT hover via
 * {@code AbstractContainerScreen.getHoveredSlot}. This mixin extends
 * the same conceptual rule (suppress visual feedback when cursor over
 * MK opaque content) to non-slot widgets. The hover-suppression query
 * here also considers active element overlays (Dropdown popovers etc.)
 * — the slot-side query currently checks panel bounds only, which is a
 * separate fold-on-evidence if slots-under-popover surface a need.
 */
@ApiStatus.Internal
@Mixin(AbstractWidget.class)
public abstract class MKWidgetHoverSuppressMixin {

    @Shadow protected boolean isHovered;

    @Inject(method = "isHovered()Z", at = @At("HEAD"), cancellable = true)
    private void mk$suppressHoverWhenOpaque(CallbackInfoReturnable<Boolean> cir) {
        // Already not hovered — nothing to suppress.
        if (!this.isHovered) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) return;

        // MK-managed widgets (TextField's wrapped EditBox, Keybindery's
        // SearchBox EditBox, any consumer widget intentionally registered
        // inside an MK panel) keep their hover state — their visual
        // feedback is the whole point of being inside the panel.
        if (MKFocus.isManaged(screen, (GuiEventListener) (Object) this)) return;

        // Compute current cursor coords. Matches
        // MKModalMouseHandlerMixin's formula — uses
        // Window.getScreenWidth/Height (logical pixels) for HiDPI
        // correctness, NOT Window.getWidth/Height (framebuffer pixels).
        var window = mc.getWindow();
        var mouseHandler = mc.mouseHandler;
        if (window == null || mouseHandler == null) return;
        double scaledX = mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaledY = mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight();

        // Unified inertness predicate (modal-global OR covered by an opaque
        // panel/element/overlay) — same question every other suppressor asks.
        if (MKFocus.isInertUnderPanel(scaledX, scaledY)) {
            cir.setReturnValue(false);
        }
    }
}
