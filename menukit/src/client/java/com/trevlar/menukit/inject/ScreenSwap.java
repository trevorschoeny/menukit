package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.Transition;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.core.PackLook;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * A container screen opened in place of another (7.4.0): its frame moves from where the
 * old screen's was to its own, rather than jumping. Every container screen but the creative
 * inventory, whichever mod opens it.
 *
 * <ul>
 *   <li><b>In place of</b>: the new screen initializes before a frame is drawn without the old
 *       one. A screen swapped in directly ({@code setScreen} over it, as SlotKit's
 *       {@code MenuSwitch} does once the new menu's contents are in), and a server's close and
 *       open handled together, both are; the world showing between them is not.</li>
 *   <li><b>The frame</b> moves for {@link #MILLIS}, eased out, drawn by MenuKit in the
 *       resource pack's look of the new screen's own art ({@link PackLook}), where the screen's
 *       image goes. Its left, top, width and height each go from the old to the new.</li>
 *   <li><b>The new screen's contents</b> (its image, widgets, labels and slots) are at their own
 *       places at once, clipped inside the moving frame and inside its own image's border, so
 *       neither border shows twice. The carried item, tooltips and MenuKit's panels aren't
 *       clipped. At the end the vanilla screen draws as usual.</li>
 *   <li><b>The tab rows</b> on a screen's own frame stand on the moving one ({@link ContainerTabRows}).</li>
 *   <li><b>A swap mid-way</b> starts from the frame as it is drawn then.</li>
 *   <li>Not while the container region is up on either screen: its panels are the frame there.</li>
 * </ul>
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class ScreenSwap {

    private ScreenSwap() {}

    /** How long the frame takes to move. */
    public static final int MILLIS = 150;
    /** How far in from each side the clip starts at least: past vanilla's border and the highlight in its corners. */
    private static final int BORDER = 4;

    /** The frame of a container screen just taken down, until the next frame is drawn or a screen takes its place. */
    private static @Nullable Reference pending;
    /** The screen whose frame is moving, where from, and its move; {@code null} when none is. */
    private static @Nullable AbstractContainerScreen<?> moving;
    private static @Nullable Reference from;
    /** The move under way: one key, run forward from the swap, eased out. Made anew for each swap. */
    private static Transition move = Transition.of(MILLIS, Easing.EASE_OUT);
    private static final Object MOVE = new Object();

    /**
     * The after-init phase a screen is seen in: before the region's, so its taking down is
     * seen before the region's own hook puts vanilla's bounds back.
     */
    private static final Identifier PHASE = Identifier.fromNamespaceAndPath("menukit", "screen_swap");

    /** Hooks every container screen's opening and taking down. Called once from client init. */
    public static void init() {
        ScreenEvents.AFTER_INIT.addPhaseOrdering(PHASE, ContainerRegions.FIRST);
        ScreenEvents.AFTER_INIT.register(PHASE, (client, screen, w, h) -> {
            Reference was = pending;
            pending = null;
            if (!(screen instanceof AbstractContainerScreen<?> acs) || !moves(acs)) return;
            ScreenEvents.remove(screen).register(s -> {
                pending = moves(acs) ? frame(acs) : null;
                if (moving == acs) moving = null;
            });
            // A resize initializes again with nothing taken down: no move.
            if (was == null || was.equals(own(acs))) return;
            moving = acs;
            from = was;
            move = Transition.of(MILLIS, Easing.EASE_OUT);
            move.toward(MOVE, true, MenuKitClock.now());
        });
    }

    /** At the start of each frame drawn: whatever was taken down before it was not replaced in place. */
    public static void frameStart() {
        pending = null;
    }

    /** Whether {@code screen}'s frame is MenuKit's to move: not the creative inventory's, nor while the region is up. */
    private static boolean moves(AbstractContainerScreen<?> screen) {
        return !(screen instanceof CreativeModeInventoryScreen) && !ContainerRegions.replacesImage(screen);
    }

    private static Reference own(AbstractContainerScreen<?> screen) {
        return FrameCentring.frame(screen);
    }

    /** How far {@code screen}'s frame has moved, eased, 0 to 1; 1 when it isn't moving. */
    private static float progress(AbstractContainerScreen<?> screen) {
        if (moving != screen || !moves(screen)) return 1f;
        float e = move.toward(MOVE, true, MenuKitClock.now());
        if (e >= 1f) moving = null;
        return e;
    }

    /** Whether {@code screen}'s frame is moving now. */
    public static boolean moving(AbstractContainerScreen<?> screen) {
        return progress(screen) < 1f;
    }

    /** {@code screen}'s frame as it draws now: on its way from the old screen's, or its own. */
    public static Reference frame(AbstractContainerScreen<?> screen) {
        Reference to = own(screen);
        float e = progress(screen);
        Reference f = from;
        if (e >= 1f || f == null) return to;
        return new Reference(lerp(f.leftPos(), to.leftPos(), e), lerp(f.topPos(), to.topPos(), e),
                lerp(f.imageWidth(), to.imageWidth(), e), lerp(f.imageHeight(), to.imageHeight(), e));
    }

    private static int lerp(int a, int b, float e) {
        return Math.round(a + (b - a) * e);
    }

    /** The look the moving frame draws in: the new screen's own art's, once it has drawn one; else the pack's plain panel. */
    private static PackLook.Look look(Screen screen) {
        Identifier image = ScreenBackground.image(screen);
        return image != null ? PackLook.look(image, PackLook.Part.CONTAINER) : PackLook.panel();
    }

    /**
     * In the screen's background pass, where its image goes: the moving frame drawn, and the
     * clip on, for the image and the rest of the pass. {@code false}, with nothing drawn, when
     * the frame isn't moving.
     */
    public static boolean drawFrame(Screen screen, GuiGraphicsExtractor g) {
        if (!(screen instanceof AbstractContainerScreen<?> acs) || !moving(acs)) return false;
        Reference f = frame(acs);
        PackLook.drawPanel(g, f.leftPos(), f.topPos(), f.imageWidth(), f.imageHeight(), look(acs));
        ScreenRectangle c = clip(acs);
        // The move can end between the check above and this one (it runs on the clock, not
        // per frame): then nothing is pushed, and the caller pops nothing.
        if (c == null) return false;
        g.scissorStack.push(c);
        return true;
    }

    /**
     * The clip inside both the moving frame's border and the screen's own image's, in screen
     * space (the window's own pose, the one the screen draws its background in); {@code null}
     * when the frame isn't moving. It's asked once per pass: the move runs on the clock, so it
     * can end mid-frame, and a caller that pushes the clip pops what it pushed, not what this
     * says later.
     */
    public static @Nullable ScreenRectangle clip(AbstractContainerScreen<?> screen) {
        if (!moving(screen)) return null;
        Reference f = frame(screen), o = own(screen);
        PackLook.Look look = look(screen);
        PackLook.Cut c = look.drawsCut() ? look.cut() : null;
        int l = Math.max(BORDER, c != null ? c.l() : 0), t = Math.max(BORDER, c != null ? c.t() : 0),
                r = Math.max(BORDER, c != null ? c.r() : 0), b = Math.max(BORDER, c != null ? (c.mirror() ? c.t() : c.b()) : 0);
        int x0 = Math.max(f.leftPos(), o.leftPos()) + l, y0 = Math.max(f.topPos(), o.topPos()) + t;
        int x1 = Math.min(f.leftPos() + f.imageWidth(), o.leftPos() + o.imageWidth()) - r;
        int y1 = Math.min(f.topPos() + f.imageHeight(), o.topPos() + o.imageHeight()) - b;
        return new ScreenRectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }
}
