package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.panel.Focus;

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
 * Suppresses vanilla widget hover under MenuKit panels. Panels already eat clicks
 * over vanilla widgets (buttons, sliders, list rows), but the widgets would still
 * highlight on hover under a panel or an open element overlay such as a Dropdown
 * popover, because vanilla's {@code isHovered} is only a bounds check against the
 * cursor.
 *
 * <p>{@link AbstractWidget#isHovered()} returns {@code false} when:
 * <ul>
 *   <li>the cursor is inert under MenuKit content (a modal, an opaque panel
 *       background, an active element overlay, or a solid interactive element;
 *       see {@link Focus#isCursorCovered}), and</li>
 *   <li>the widget was not registered through {@link Focus#addWidget}. Registered
 *       widgets (TextField's wrapped EditBox, Keybindery's SearchBox EditBox, any
 *       consumer widget placed inside a panel) keep their hover feedback.</li>
 * </ul>
 *
 * <p>The getter is overridden rather than the field because vanilla rendering
 * (such as {@code Button.renderWidget}) goes through {@code isHoveredOrFocused()},
 * which calls {@code isHovered()}. That avoids fighting the field that
 * {@code AbstractWidget.render} sets every frame. A subclass that reads the field
 * directly (rare) is not covered; handle that if a vanilla widget turns out to do
 * it.
 *
 * <p>{@code MKSlotInputMixin} does the same for slots via
 * {@code AbstractContainerScreen.getHoveredSlot}. This check also counts active
 * element overlays such as Dropdown popovers; the slot side checks panel bounds
 * only.
 */
@ApiStatus.Internal
@Mixin(AbstractWidget.class)
public abstract class MKWidgetHoverSuppressMixin {

    @Shadow protected boolean isHovered;

    @Inject(method = "isHovered()Z", at = @At("HEAD"), cancellable = true)
    private void mk$suppressHoverWhenOpaque(CallbackInfoReturnable<Boolean> cir) {
        if (!this.isHovered) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) return;

        // Widgets registered inside a panel keep their hover state.
        if (Focus.isManaged(screen, (GuiEventListener) (Object) this)) return;

        // Same formula as MKModalMouseHandlerMixin: Window.getScreenWidth/Height
        // (logical pixels) for HiDPI, not Window.getWidth/Height (framebuffer pixels).
        var window = mc.getWindow();
        var mouseHandler = mc.mouseHandler;
        if (window == null || mouseHandler == null) return;
        double scaledX = mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaledY = mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight();

        // The same inertness check (modal anywhere, or covered by an opaque
        // panel, element or overlay) every other suppressor uses.
        if (Focus.isInertUnderPanel(scaledX, scaledY)) {
            cir.setReturnValue(false);
        }
    }
}
