package com.trevlar.menukit.inject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <h3>The screen's own image</h3>
 * The first texture the pass blits after the layer has drawn is the screen's own image
 * ({@link #image}): what its override blits once the base method returns. It is seen at the
 * head of the texture blit, before OptiGUI swaps the texture for a pack's rule
 * ({@code MKContainerImageMixin}), so it is the screen's own texture, whatever OptiGUI later
 * draws in its place. A panel that takes the place of the image (the container region's two
 * panels) hides exactly that texture for the rest of the pass, while the region says so
 * ({@link ContainerRegions#replacesImage}); every other blit, sprite, fill and text in the
 * pass still draws, another mod's container texture included.
 *
 * <p>Render thread only. A background pass inside another (a screen drawing its parent's)
 * keeps its own state and puts the outer one back.
 */
@ApiStatus.Internal
public final class ScreenBackground {

    private ScreenBackground() {}

    /** The screen whose background pass is running, or {@code null}. */
    private static @Nullable Screen pass;
    /** Whether the background layer has drawn in this pass; whether it is done, so the screen's image comes next. */
    private static boolean drawn, watching;
    /** Whether the rest of this pass leaves out the screen's own image. */
    private static boolean hidingImage;
    /** The screen's own image in this pass once it is seen, or {@code null} before. */
    private static @Nullable Identifier passImage;

    // ponytail: by screen class, so a screen knows its image from its first frame on every opening after the first; a class that blits a different image per instance is right from its second frame.
    private static final Map<Class<?>, Identifier> IMAGES = new ConcurrentHashMap<>();

    /**
     * The texture {@code screen}'s background pass blits as its own image, as it gave it
     * (before any OptiGUI swap), or {@code null} before a screen of its kind has drawn one.
     */
    public static @Nullable Identifier image(Screen screen) {
        return IMAGES.get(screen.getClass());
    }

    /**
     * Around the screen's {@code extractBackground} call: runs it ({@code vanilla}), with
     * the background layer drawn where the screen's image goes ({@link #afterDim}), or
     * after it when the screen's background never reached the base method.
     */
    public static void around(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, Runnable vanilla) {
        Screen outerPass = pass;
        boolean outerDrawn = drawn, outerWatching = watching, outerHiding = hidingImage;
        Identifier outerImage = passImage;
        pass = screen;
        drawn = watching = false;
        hidingImage = false;
        passImage = null;
        try {
            vanilla.run();
            if (!drawn) draw(screen, graphics, mouseX, mouseY);
        } finally {
            pass = outerPass;
            drawn = outerDrawn;
            watching = outerWatching;
            hidingImage = outerHiding;
            passImage = outerImage;
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
        // From here to the pass's end the screen's own image comes, and would be drawn over
        // the panels that replace it. The layer's own blits above are not the image: it is
        // only looked for from here.
        watching = true;
        hidingImage = ContainerRegions.replacesImage(screen);
    }

    /**
     * At the head of a texture blit, before OptiGUI's swap: notes the screen's own image (the
     * first blit after the layer drew) and says whether this blit is it, left out of this pass.
     */
    public static boolean hidesBlit(Identifier texture) {
        Screen screen = pass;
        if (screen == null || !watching) return false;
        if (passImage == null) {
            passImage = texture;
            if (!texture.equals(IMAGES.get(screen.getClass()))) IMAGES.put(screen.getClass(), texture);
        }
        return hidingImage && texture.equals(passImage);
    }
}
