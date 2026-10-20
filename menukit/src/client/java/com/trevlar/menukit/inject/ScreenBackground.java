package com.trevlar.menukit.inject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * The screen's background pass, and where the {@link LayerPlan.Layer#BACKGROUND
 * BACKGROUND} layer draws in it (7.4.0).
 *
 * <h3>Where</h3>
 * Vanilla's frame begins with {@code Screen.extractBackground}: the base method draws the
 * screen's dim (or blur and menu backdrop), and a container screen's override then blits
 * its own image (the chest texture) after calling it. The background layer draws at the
 * seam between the two, at the base method's return: on the dim, where the screen's image
 * goes. So:
 * <ul>
 *   <li>vanilla's {@code extractBackground} still runs, whole, and so does every other
 *       mod's hook in it;</li>
 *   <li>what a mod draws in a screen's background after vanilla's image (a tint over the
 *       chest texture, extra slot frames) still lands over the background panels, as it
 *       would over the image;</li>
 *   <li>everything after the background pass, Fabric's {@code afterBackground} listeners
 *       included, draws over them: the screen's widgets and every other mod's, its labels,
 *       its slots, every other panel layer, the carried item, tooltips.</li>
 * </ul>
 * A screen whose background never reaches the base method gets the layer once its
 * background pass is done.
 *
 * <h3>The screen's own image, replaced</h3>
 * A panel that takes the place of the screen's image (the container region's two panels)
 * hides it for the rest of the pass: a blit of one of vanilla's own container textures
 * ({@code minecraft:textures/gui/container/...}) is skipped while the region says so
 * ({@link ContainerRegions#replacesImage}). Nothing else is: another mod's textures and
 * every sprite, fill and text still draw.
 *
 * <p>Render thread only. A background pass inside another (a screen drawing its parent's)
 * keeps its own state and puts the outer one back.
 */
@ApiStatus.Internal
public final class ScreenBackground {

    private ScreenBackground() {}

    /** The screen whose background pass is running, or {@code null}. */
    private static @Nullable Screen pass;
    /** Whether the background layer has drawn in this pass. */
    private static boolean drawn;
    /** Whether the rest of this pass leaves out vanilla's own container image. */
    private static boolean hidingImage;

    /** The namespace and path every vanilla container screen's own image is under. */
    private static final String IMAGE_PATH = "textures/gui/container/";

    /**
     * Around the screen's {@code extractBackground} call: runs it ({@code vanilla}), with
     * the background layer drawn where the screen's image goes ({@link #afterDim}), or
     * after it when the screen's background never reached the base method.
     */
    public static void around(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, Runnable vanilla) {
        Screen outerPass = pass;
        boolean outerDrawn = drawn, outerHiding = hidingImage;
        pass = screen;
        drawn = false;
        hidingImage = false;
        try {
            vanilla.run();
            if (!drawn) draw(screen, graphics, mouseX, mouseY);
        } finally {
            pass = outerPass;
            drawn = outerDrawn;
            hidingImage = outerHiding;
        }
    }

    /**
     * At the return of the base {@code Screen.extractBackground}: the dim is down and the
     * screen's own image comes next. The background layer draws here, once per pass, and
     * only in the pass of the screen it belongs to.
     */
    public static void afterDim(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (pass != screen || drawn) return;
        draw(screen, graphics, mouseX, mouseY);
    }

    private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawn = true;
        ScreenPanelRegistry.renderBackground(screen, graphics, mouseX, mouseY);
        // From here to the pass's end the screen's own image would be drawn over the
        // panels that replace it.
        hidingImage = ContainerRegions.replacesImage(screen);
    }

    /** At a blit: whether it is vanilla's own container image, left out of this pass. */
    public static boolean hidesBlit(Identifier texture) {
        return hidingImage && texture.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
                && texture.getPath().startsWith(IMAGE_PATH);
    }
}
