package com.trevorschoeny.menukit.examples.injection;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOriginFns;
import com.trevorschoeny.menukit.inject.ScreenPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Phase 10 injection example: Pattern 2 — button or small panel at a slot-grid
 * region.
 *
 * <p>Adds a small toolbar with two 9×9 buttons above the chest's slot grid in
 * {@link ContainerScreen} (the vanilla class for single and double chests,
 * shulker boxes, and other generic-container UIs in 1.21.11). Each button
 * posts a distinct message to the action bar when clicked.
 *
 * <h3>Mixin targeting strategy</h3>
 *
 * Targets {@link AbstractContainerScreen} (abstract parent) rather than
 * {@code ContainerScreen} directly, because {@code mouseClicked} is only
 * declared on the parent. Runtime {@code instanceof ContainerScreen} gate
 * narrows visibility. See {@link ExampleInventoryCornerButtonMixin} for a
 * fuller explanation of this pattern.
 *
 * <p><b>Dev-only.</b> See {@link DevOnlyExampleMixinsPlugin}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ExampleChestToolbarMixin {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Unique
    private static final Panel EXAMPLES$PANEL = new Panel(
            "menukit_example_chest_toolbar",
            List.of(
                    // Feedback goes through the chat overlay (addMessage) rather
                    // than gui.setOverlayMessage because the overlay message
                    // renders on the hotbar strip, which is hidden while a
                    // screen is open. Chat is visible either way, so the click
                    // feedback is observable in the demo.
                    new Button(0, 0, 9, 9, Component.literal("1"),
                            btn -> Minecraft.getInstance().gui.getChat().addMessage(
                                    Component.literal("[MenuKit example] Toolbar button 1"))),
                    new Button(11, 0, 9, 9, Component.literal("2"),
                            btn -> Minecraft.getInstance().gui.getChat().addMessage(
                                    Component.literal("[MenuKit example] Toolbar button 2")))
            )
    );

    @Unique
    private final ScreenPanelAdapter examples$adapter = new ScreenPanelAdapter(
            EXAMPLES$PANEL,
            // Vanilla container screens place the top slot grid at (8, 18)
            // inside the frame. A gap of 2 would put the toolbar at y=7, which
            // overlaps the container's title text at y=6. Using gap=14 floats
            // the toolbar ABOVE the frame (panel y = 18 - 9 - 14 = -5), clear
            // of the title. Tradeoff: buttons sit slightly outside the visual
            // screen body. For a tighter placement consumers can use a smaller
            // gap and accept partial title overlap, or pick a different origin
            // function (e.g. fromScreenTopRight) that sidesteps the title area.
            ScreenOriginFns.aboveSlotGrid(/* gridX */ 8,
                                          /* gridY */ 18,
                                          /* panelHeight */ 9,
                                          /* gap */ 14)
    );

    @Unique
    private boolean examples$appliesToThisScreen() {
        return ((Object) this) instanceof ContainerScreen;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void examples$render(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        if (!examples$appliesToThisScreen()) return;
        examples$adapter.render(g, examples$bounds(), mx, my, (AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void examples$click(MouseButtonEvent event, boolean doubleClick,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!examples$appliesToThisScreen()) return;
        if (examples$adapter.mouseClicked(examples$bounds(),
                event.x(), event.y(), event.button(),
                (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private ScreenBounds examples$bounds() {
        return new ScreenBounds(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }
}
