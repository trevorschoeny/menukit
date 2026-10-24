package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.ScreenSeams;
import com.trevlar.menukit.inject.ShortFrames;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Objects;

/**
 * A vanilla container screen made taller or shorter at a seam (7.4.0): its frame gains
 * (or loses) {@code by} rows at row {@code seam} of its own layout, so screens of nearly one
 * size can be made exactly one, and a tab row standing on their frames doesn't move as one
 * opens in place of another.
 *
 * <pre>{@code
 * // A mod's screen, 170 tall, made 166 like vanilla's: four rows out of the gap above its inventory.
 * ScreenFrames.stretch(MyBoxScreen.class, 76, -4);
 * }</pre>
 *
 * <h3>Vanilla's screens are one size already</h3>
 * MenuKit makes every vanilla container screen whose frame is nearly 166 tall exactly 166,
 * the size of nearly all of them (the inventory, the crafting table, the furnaces, the
 * dispenser, the enchanting table, the anvil and the rest), with no declaration:
 * <ul>
 *   <li><b>The shulker box</b>, 167 tall with an image of 166, loses the blank row under its image.</li>
 *   <li><b>The 3-row chest</b> (chests, barrels, the ender chest), 168 tall with an image of
 *       167, loses that blank row and one row of the gap under its slots, so its inventory
 *       slots and label sit where the shulker box's do, its inventory where every 166 screen's is.</li>
 *   <li><b>Every other chest size</b> (the double chest's 222) loses only its blank row.</li>
 * </ul>
 * So a vanilla screen's frame is exactly what it draws, and the moving frame of a screen
 * opened in place of another lands on the art. A mod's own seam is added to MenuKit's.
 *
 * <ul>
 *   <li><b>The screen's size</b> is vanilla's plus {@code by}, centered by vanilla as any
 *       screen of that size is, so it sits exactly where a vanilla screen of that size does:
 *       the inventory and a shulker box share one frame on every window.</li>
 *   <li><b>Above the seam</b> everything keeps its place in the frame: slots, labels, the
 *       player model, the recipe book button. <b>From the seam down</b> everything is
 *       {@code by} lower in the frame: slots (another mod's added to the menu included) and
 *       the "Inventory" label.</li>
 *   <li><b>The screen's own image</b> draws in parts with the seam row repeated
 *       {@code by} times between them, or with {@code -by} rows from the seam left out.
 *       OptiGUI's swap and a resource pack's texture apply to each part as to the whole.</li>
 *   <li>Exactly the class given; a subclass keeps its own size. While the
 *       {@link ContainerRegion container region} takes the screen's image, its panels draw in
 *       place of the image; the region lays out from the stretched size, slots and label.</li>
 *   <li>Another mod reading the screen's bounds ({@code topPos}, {@code imageHeight}) reads
 *       the stretched frame; one placing something by vanilla's fixed offsets from the top,
 *       below the seam, is {@code by} off.</li>
 * </ul>
 *
 * <h3>A grid that scrolls when the frame doesn't fit (7.4.0)</h3>
 * {@link #scrollsWhenShort} turns it on for the screens a mod chooses; MenuKit never does on its own.
 * On such a screen, while its frame wouldn't fit the window, its own container grid scrolls inside
 * the frame and the frame is as much shorter, so the whole screen fits:
 *
 * <pre>{@code
 * // A large chest on a short window: its six rows scroll, the inventory stays put.
 * ScreenFrames.scrollsWhenShort(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));
 * }</pre>
 * <ul>
 *   <li><b>When.</b> The frame (its seams included) and the tab rows on its top and bottom edges
 *       are taller than the window less the 4 px margins the container region keeps, the same
 *       block the shared centering rule centers. Read every frame; a resize lays it out again.
 *       On a window it fits, the screen is vanilla's, pixel for pixel, and nothing on it is moved.</li>
 *   <li><b>What scrolls.</b> The grid's rows, by pixels, inside the frame, as few given back as
 *       make the screen fit, never under three; the title, the player's inventory, the hotbar and
 *       the rest of the frame stay put. The scrollbar's lane is beside the grid, the frame that
 *       much wider. A window too short even for three rows scrolls the column as the container
 *       region does.</li>
 *   <li><b>How.</b> It is the {@link ContainerRegion container region}, fitted, with the screen's
 *       own grid as its first section, a {@code SlotScroll}: a row cut at the edge shows, hovers and
 *       takes clicks only where it shows, a row out of view is inert, the wheel scrolls it, a
 *       shift-click into a hidden row scrolls there, and the screen's bounds are the frame's.</li>
 *   <li><b>Its slots are vanilla's.</b> Each one shown sits where it is drawn, so a recipe viewer or
 *       a mod reading {@code slot.x/y} finds it there; one scrolled out of view is parked off the
 *       screen; all of them go back to vanilla's places once the frame fits again or the screen
 *       closes.</li>
 *   <li><b>Which screens.</b> Any container screen whose own container is a full grid of 18 px
 *       slots under its title at the frame's content edge, as a chest's, a barrel's, a shulker box's
 *       or a mod's chest's are (slot x 8, y 18). A screen without one is left as it is, and so are
 *       the player's own inventory screens. While another mod's section shows on the screen too,
 *       the grid's section is first.</li>
 * </ul>
 *
 * <p>Declare from your client initializer: frames freeze with every other declaration.
 */
public final class ScreenFrames {

    private ScreenFrames() {}

    /**
     * Stretches every {@code screen} by {@code by} rows at row {@code seam} of its frame
     * (negative to shrink it, leaving out rows {@code seam} to {@code seam - by - 1}).
     *
     * @throws IllegalStateException after declarations froze, or when the class is already stretched
     */
    public static synchronized void stretch(Class<? extends AbstractContainerScreen<?>> screen, int seam, int by) {
        Objects.requireNonNull(screen, "screen");
        Declarations.requireOpen("ScreenFrames.stretch(" + screen.getSimpleName() + ")");
        if (by == 0 || seam <= 0) throw new IllegalArgumentException("ScreenFrames.stretch needs a seam inside the frame and a non-zero size");
        if (!ScreenSeams.declare(screen, seam, by))
            throw new IllegalStateException("ScreenFrames: " + screen.getName() + " is already stretched");
    }

    /**
     * Lets the screens {@code screens} takes scroll their own container grid inside their frame
     * while the frame wouldn't fit the window, in {@code scope}'s game mode ({@link ModeScope#BOTH}
     * for either), read every frame (see "A grid that scrolls when the frame doesn't fit"). Off for
     * every screen no declaration takes.
     *
     * @throws IllegalStateException after declarations froze
     */
    public static void scrollsWhenShort(ModeScope scope, ScreenMatcher screens) {
        Declarations.requireOpen("ScreenFrames.scrollsWhenShort(" + scope + ")");
        ShortFrames.declare(Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(screens, "screens"));
    }
}
