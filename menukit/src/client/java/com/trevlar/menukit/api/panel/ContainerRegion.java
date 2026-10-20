package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.inject.ContainerRegions;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The container region (7.4.0): on a container screen, the player's inventory stays
 * where vanilla puts it, and everything above it is one column that scrolls together.
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
 * <h3>What the region does</h3>
 * <ul>
 *   <li><b>Grows, then scrolls.</b> It runs from just above the inventory up, as tall
 *       as its sections need, into the screen's free height, and scrolls only once
 *       that is used up. It is never shorter than the screen's own container part, so
 *       a tiny window still shows vanilla's height, scrolling. The inventory never
 *       moves; content scrolled down goes behind its top edge.</li>
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
 *   <li><b>MenuKit's scrollbar</b>, in the screen's look, at the region's right while
 *       anything scrolls.</li>
 * </ul>
 *
 * <h3>A section</h3>
 * Its {@link Builder#content} is placed at the region's content edge, where vanilla's
 * grid frame is: an element at {@code (0, 0)} with a title above a nine-wide grid lines
 * up with vanilla's own chest. The region draws one frame behind every section; a
 * section draws its own title and slot frames. Hidden sections take no room. The
 * screen's own container is a section too, if its slots are to scroll with the rest:
 * whoever puts the region on a screen presents that screen's grid in it (SlotKit's
 * {@code SlotElement.slot(Address)}), since what is behind the region is covered.
 *
 * <p>Declare from your client initializer: sections freeze with every other declaration.
 */
public final class ContainerRegion {

    private ContainerRegion() {}

    /** The room between two sections, and from a section's grid to vanilla's "Inventory" below. */
    public static final int GAP = ContainerRegions.GAP;

    /** The width a section has before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = ContainerRegions.CONTENT_WIDTH;

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

        /**
         * Declares the section.
         *
         * @throws IllegalStateException after declarations froze, or when {@code id} is
         *         already declared or no content was given
         */
        public void declare() {
            Declarations.requireOpen("ContainerRegion.section(" + id + ")");
            if (content == null) throw new IllegalStateException("ContainerRegion section '" + id + "': content(...) is required");
            ContainerRegions.declare(new ContainerRegions.Section(id, content, matcher, order, visibleWhen));
        }
    }
}
