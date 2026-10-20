package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.inject.ContainerRegions;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.DyeColor;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The container region (7.4.0): on a container screen, the player's inventory never
 * scrolls, and everything above it is one column that scrolls together.
 * A chest, a shulker box opened below it and another one below that are one region,
 * never a scroll area each. Mods put their containers' slot elements in it as
 * <b>sections</b>, stacked top to bottom; there is one region per screen, however many
 * mods add to it.
 *
 * <pre>{@code
 * ContainerRegion.section("mymod:opened-shulker")
 *         .content(shulkerGrid)            // a title and SlotKit's slot elements, say
 *         .on(ContainerScreen.class)
 *         .order(10)                       // below the chest's own section (order 0)
 *         .visibleWhen(() -> session.isOpen())
 *         .declare();
 * }</pre>
 *
 * <h3>Two panels</h3>
 * The containers and the inventory are two panels, each with its own frame: the
 * <b>container list</b> on top, holding every section with its scrollbar inside its
 * frame, and the <b>inventory panel</b> below it, MenuKit's gap between panels apart. The
 * inventory panel is around the player's inventory, and moves only as the list grows.
 * Scrolled content clips at the container list's own bottom edge.
 *
 * <p>Both are the screen's background ({@link Panel.Builder#background()}): drawn where
 * the screen draws its own image, under everything else on the screen, vanilla's labels
 * and slots and every other mod's widgets included, and claiming nothing, so another
 * mod's button on either panel stays live and gets its clicks first. The region's own
 * slots draw with the list, right after it, so another mod's button over one covers its
 * item; they are cut at the list's edges.
 *
 * <h3>The screen's bounds</h3>
 * The screen's {@code leftPos}, {@code topPos}, {@code imageWidth} and {@code imageHeight}
 * are the rectangle around both panels, scrollbar lane included, set whenever the layout
 * changes (the screen opening, a resize, a section opening or closing), never on a
 * scroll. So a panel placed on the menu frame, another mod's buttons by the screen's
 * edges and a recipe viewer keeping clear of the screen all see both panels, and a click
 * in the gap between them with an item held is inside the screen, so it drops nothing.
 * Vanilla's title is moved back by as much as the origin moved, so it stays put on
 * screen, and the player's inventory slots and "Inventory" label go with the inventory
 * panel.
 *
 * <p>When the layout changes while the screen is open (a section opening or closing, the
 * list starting to scroll), the screen is laid out again the way a window resize does
 * ({@code Screen.resize} at its own size): every mod adds its widgets again from its own
 * init hook, at the new bounds, so a button another mod placed by the screen's edges
 * follows the panel it was by. Only then, never on a scroll, and once a frame however
 * many sections changed. What a resize keeps is kept (the item on the cursor, the scroll,
 * which sections are open, the anvil's name and the creative search); what it drops is
 * dropped (which widget had focus). While the region is up, the
 * screen's own background still runs, every mod's hook in it included, but vanilla's own
 * container texture and the screen's title are left out: the two panels and the sections'
 * titles are in their place.
 *
 * <h3>Anchors</h3>
 * To put a button by one panel rather than by the whole screen, place it on
 * {@link #CONTAINERS} (the container list) or {@link #INVENTORY} (the inventory panel)
 * with an {@link AnchorPanelAdapter}. Either is absent on a screen the region is not on.
 *
 * <pre>{@code
 * Panel sort = Panel.builder("mymod:sort_chest").add(sortButton)
 *         .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP)).build();
 * new AnchorPanelAdapter(sort).on(ContainerRegion.CONTAINERS);
 * }</pre>
 *
 * <h3>What the region does</h3>
 * <ul>
 *   <li><b>Grows, centred, then scrolls.</b> The container list starts at the screen's
 *       own container part, where vanilla's is, and grows with its sections. While the
 *       two panels fit, they stay centred on the screen as one block: the list's top
 *       moves up and the inventory panel down by half the growth each. Where one side
 *       has less room (a window too short for vanilla's own margins), that side holds and
 *       the other moves until the block is centred. Only once the block reaches the top
 *       and bottom margins does it scroll. Each panel moves at most a pixel per pixel of
 *       growth, so nothing jumps. It is never shorter than the screen's own container
 *       part, so a tiny window still shows vanilla's height, scrolling. A resize lays it
 *       out again.</li>
 *   <li><b>The wheel, last, from anywhere on the screen.</b> It scrolls the region
 *       only when vanilla and every other handler declined the turn: over a slot, a
 *       mod that moves items with the wheel (Mouse Tweaks, Inventory Profiles Next)
 *       keeps it, and a recipe viewer's list keeps its own; anywhere nothing wants it,
 *       the region scrolls. Nothing detects any mod.</li>
 *   <li><b>A shift-click lands in view.</b> A quick move into a slot of the region
 *       that is scrolled out of view scrolls it there. A quick move out of it (into the
 *       inventory) scrolls nothing.</li>
 *   <li><b>Carrying an item to an edge scrolls it</b>, faster nearer the edge.</li>
 *   <li><b>Smooth.</b> Pixels, never whole rows; a slot across the edge is drawn, hovered
 *       and clicked only where it shows ({@code SlotClip}).</li>
 *   <li><b>MenuKit's scrollbar</b>, in the screen's look, inside the container list's
 *       frame at its right while anything scrolls.</li>
 * </ul>
 *
 * <h3>A section</h3>
 * Its {@link Builder#content} is placed at the region's content edge, where vanilla's
 * grid frame is: an element at {@code (0, 0)} with a title above a nine-wide grid lines
 * up with vanilla's own chest. A section draws its own title and slot frames. Hidden
 * sections take no room. The screen's own container is a section too: whoever puts the
 * region on a screen presents that screen's grid in it (SlotKit's
 * {@code SlotElement.slot(Address)}), since what is behind the container list is covered.
 *
 * <p>A section shows while its own {@link Builder#visibleWhen} holds <b>and its content is
 * visible</b>: content that hides itself takes its section with it. A slot library's element
 * for a slot group that is not on the open menu, or whose slots are all hidden, hides
 * itself, so a section of such a group needs no condition of its own.
 *
 * <h3>A section's colour</h3>
 * {@link Builder#tint} colours the section's part of the container list: a recoloured
 * shulker box's section in its dye's colour, say. The tint is laid over the section, its
 * title and slot frames included, and under the items in its slots, from just left of the
 * content edge to just right of the nine slots (never over the scrollbar), and scrolls and
 * clips with it. None by default.
 *
 * <h3>Resource packs</h3>
 * The two panels follow the resource pack's container art, as vanilla's own screen
 * would under it: the container list takes the look of the screen's container texture,
 * the inventory panel the inventory half of it. A section names the container it stands
 * for with {@link Builder#texture} (vanilla's chest by default): its slot frames take that
 * texture's slots, and where it differs from the list's (a shulker box in a chest's list)
 * its part of the list takes that texture's fill. A tint colours grey art only; over art
 * the pack already coloured it is left out.
 *
 * <p>Title a section that is a vanilla container's with vanilla's translation key
 * ({@code Component.translatable("container.shulkerBox")}, or the screen's own title,
 * which is one) rather than a literal: packs whose art covers the title strip blank those
 * keys, and the section's title then goes with vanilla's.
 *
 * <p>Declare from your client initializer: sections freeze with every other declaration.
 */
public final class ContainerRegion {

    private ContainerRegion() {}

    /** The room between two sections, and from a section's grid to vanilla's "Inventory" below. */
    public static final int GAP = ContainerRegions.GAP;

    /** The width a section has before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = ContainerRegions.CONTENT_WIDTH;

    /**
     * The container list's rectangle on a screen the region is on, scrollbar lane
     * included while it scrolls: for buttons that act on the containers.
     */
    public static final Anchor CONTAINERS = ContainerRegions.CONTAINERS;

    /** The inventory panel's rectangle on a screen the region is on: for buttons that act on the inventory. */
    public static final Anchor INVENTORY = ContainerRegions.INVENTORY;

    /** Vanilla's chest texture: the default look of a section, and of the list on a chest's screen. */
    public static final Identifier CHEST = PackLook.CHEST;

    /** Vanilla's shulker box texture: for a section that shows a shulker box. */
    public static final Identifier SHULKER_BOX = PackLook.SHULKER_BOX;

    /** How opaque a dye's tint is ({@link #dyeTint}): enough to read as the dye over the panel and its slot frames. */
    public static final int DYE_TINT_ALPHA = 0x70;

    /**
     * The tint a section of a box dyed {@code dye} gets ({@link Builder#tint(DyeColor)}): the
     * dye's colour at {@link #DYE_TINT_ALPHA}, or none ({@code 0}) for {@code null}, an
     * undyed box. For a {@link Builder#tint(IntSupplier)} that follows the box shown.
     */
    public static int dyeTint(@Nullable DyeColor dye) {
        return dye == null ? 0 : (DYE_TINT_ALPHA << 24) | (dye.getTextureDiffuseColor() & 0xFFFFFF);
    }

    /** Starts a section, its id namespaced and unique ({@code "mymod:opened-shulker"}). */
    public static Builder section(String id) {
        return new Builder(id);
    }

    /** Builds one section of the container region. {@link #content} is required. */
    public static final class Builder {
        private final String id;
        private PanelElement content;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        private int order = 0;
        private BooleanSupplier visibleWhen = () -> true;
        private IntSupplier tint = () -> 0;
        private Identifier texture = CHEST;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Required: what the section shows, placed from the region's content edge. */
        public Builder content(PanelElement content) {
            this.content = Objects.requireNonNull(content, "content");
            return this;
        }

        /** The screens it is on. Without {@code on} or {@link #onMatching}, every container screen. */
        @SafeVarargs
        public final Builder on(Class<? extends AbstractContainerScreen<?>>... screens) {
            if (screens.length == 0) {
                throw new IllegalArgumentException("ContainerRegion section '" + id + "': on() needs at least one screen class");
            }
            return onMatching(ScreenMatcher.only(screens));
        }

        /** The screens it is on, by a matcher. */
        public Builder onMatching(ScreenMatcher matcher) {
            if (targeted) throw new IllegalStateException("ContainerRegion section '" + id + "': targeting declared twice");
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.targeted = true;
            return this;
        }

        /** Where it stacks: lower is higher up; ties go by id. Default 0. */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Shown while this holds, read every frame. Hidden, it takes no room. */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** A fixed tint over the section, ARGB (its alpha is how strong it is); {@code 0} for none, the default. */
        public Builder tint(int argb) {
            return tint(() -> argb);
        }

        /** The section in a dye's colour ({@link #dyeTint}); {@code null} for none. */
        public Builder tint(@Nullable DyeColor dye) {
            return tint(dyeTint(dye));
        }

        /**
         * A tint read every frame, ARGB, {@code 0} for none: for a section that shows
         * whichever box is open, {@code tint(() -> ContainerRegion.dyeTint(session.color()))}.
         */
        public Builder tint(IntSupplier argb) {
            this.tint = Objects.requireNonNull(argb, "argb");
            return this;
        }

        /**
         * The container texture this section stands for, a full texture path
         * ({@link #CHEST}, {@link #SHULKER_BOX}, or a mod's own container texture laid out
         * like vanilla's): under a resource pack that restyles it, the section's slot frames
         * and its part of the list take its look. Vanilla's chest by default.
         */
        public Builder texture(Identifier texture) {
            this.texture = Objects.requireNonNull(texture, "texture");
            return this;
        }

        /**
         * Declares the section.
         *
         * @throws IllegalStateException after declarations froze, or when {@code id} is
         *         already declared or no content was given
         */
        public void declare() {
            Declarations.requireOpen("ContainerRegion.section(" + id + ")");
            if (content == null) throw new IllegalStateException("ContainerRegion section '" + id + "': content(...) is required");
            ContainerRegions.declare(new ContainerRegions.Section(id, content, matcher, order, visibleWhen, tint, texture));
        }
    }
}
