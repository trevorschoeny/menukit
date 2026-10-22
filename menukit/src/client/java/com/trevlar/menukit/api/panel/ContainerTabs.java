package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.ContainerTabRows;
import com.trevlar.menukit.inject.PanelHost;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Tabs on the container screen's panels (7.4.0): a row of tabs, like the creative inventory's,
 * on an edge of the {@link ContainerRegion container region}'s container list or inventory
 * panel. MenuKit draws the row, hit-tests it, and pages it with arrows at its ends when it is
 * wider than its frame; everything a tab is and does is the mod's: what a click does, its icon
 * (an item or the mod's own sprite), its tooltip, its order, whether it shows and whether it
 * draws selected. MenuKit keeps no selection of its own.
 *
 * <pre>{@code
 * ContainerTabs.tab("mymod:backpack", ModeScope.BOTH)
 *         .icon(() -> backpack.stack())
 *         .tooltip(() -> Component.translatable("mymod.backpack"))
 *         .on(ContainerScreen.class)
 *         .order(10)
 *         .selectedWhen(() -> shown == Shown.BACKPACK)
 *         .onClick(() -> shown = Shown.BACKPACK)
 *         .declare();
 * }</pre>
 *
 * <h3>The row</h3>
 * <ul>
 *   <li><b>One row per edge per screen.</b> Every mod's tabs on one {@link Edge} share it,
 *       left to right by {@link Builder#order}, ties by id. A row with no tab showing takes no
 *       room.</li>
 *   <li><b>Vanilla's creative tabs</b>, top ones on a top edge and bottom ones on a bottom
 *       edge, so they follow a resource pack that restyles those. A selected tab
 *       ({@link Builder#selectedWhen}) runs into the frame, open into it as creative's does; the
 *       others stop at it. A disabled one ({@link Builder#disabledWhen}) is grayed and takes no
 *       click.</li>
 *   <li><b>Counted in the layout.</b> The row stands outside its panel, never over a slot, and
 *       the region makes room for it: in the block it centers and fits, and in the screen's
 *       bounds, so other mods' buttons and recipe viewers keep clear of it. A row between the
 *       two panels (the list's bottom edge, the inventory panel's top) stands in the gap
 *       between them, widened to hold it.</li>
 *   <li><b>Overflow.</b> Past what fits, vanilla's page arrows at the row's ends move it a
 *       tab at a time. A tab that starts drawing selected comes into view. There is no wheel
 *       on the row.</li>
 *   <li><b>A click</b>, left button only, runs the tab's {@link Builder#onClick} with vanilla's
 *       click sound, an item on the cursor or not; the click goes no further, so a held item
 *       is neither dropped nor placed.</li>
 * </ul>
 *
 * <p>Tabs stand on the region's panels, so they show only where the region is up: a container
 * screen with a section showing. Where it isn't (no section shows, or the player's own
 * inventory, which the region is never on), no row shows.
 *
 * <p>To show one container at a time under the tabs, as the creative inventory shows one
 * page, give each section a {@code visibleWhen} that reads the mod's own pick, have each tab's
 * {@code onClick} set it, and declare the screen {@link ContainerRegion#fitted fitted}, so the
 * frame fits the one shown.
 *
 * <p>Declare from your client initializer: tabs freeze with every other declaration.
 */
public final class ContainerTabs {

    private ContainerTabs() {}

    /** The panel edge a tab's row stands on. */
    public enum Edge {
        /** Above the container list's top edge: the default. */
        CONTAINERS_TOP,
        /** Below the container list's bottom edge, in the gap above the inventory panel. */
        CONTAINERS_BOTTOM,
        /** Above the inventory panel's top edge, in the gap below the container list. */
        INVENTORY_TOP,
        /** Below the inventory panel's bottom edge. */
        INVENTORY_BOTTOM;

        /** Whether the row stands on a top edge, its tabs standing up from it. */
        public boolean top() {
            return this == CONTAINERS_TOP || this == INVENTORY_TOP;
        }
    }

    /**
     * Starts a tab, its id namespaced and unique ({@code "mymod:backpack"}), for one mode scope:
     * it shows only while the player is in that mode ({@link ModeScope#BOTH} for either), read
     * every frame, as its {@link Builder#visibleWhen} is.
     */
    public static Builder tab(String id, ModeScope scope) {
        return new Builder(id, Objects.requireNonNull(scope, "scope"));
    }

    /** Builds one tab. Every part is optional; a tab with no icon is an empty tab. */
    public static final class Builder {
        private final String id;
        private final ModeScope scope;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        private int order = 0;
        private Edge edge = Edge.CONTAINERS_TOP;
        private @Nullable Supplier<ItemStack> item;
        private @Nullable Identifier sprite;
        private @Nullable Supplier<@Nullable Component> tooltip;
        private Runnable onClick = () -> {};
        private BooleanSupplier visibleWhen = () -> true, selectedWhen = () -> false, disabledWhen = () -> false;

        private Builder(String id, ModeScope scope) {
            this.id = Objects.requireNonNull(id, "id");
            this.scope = scope;
        }

        /** The tab's icon, an item, read every frame, drawn full size in the middle of the tab. */
        public Builder icon(Supplier<ItemStack> item) {
            this.item = Objects.requireNonNull(item, "item");
            this.sprite = null;
            return this;
        }

        /**
         * The tab's icon, a GUI sprite of the mod's own ({@code mymod:tab/backpack}, under
         * {@code textures/gui/sprites/}), drawn 16 px square in the middle of the tab, where an
         * item icon goes.
         */
        public Builder icon(Identifier sprite) {
            this.sprite = Objects.requireNonNull(sprite, "sprite");
            this.item = null;
            return this;
        }

        /**
         * The tab's tooltip, read every frame it is hovered. A {@code \n} in it starts a new
         * line; for a list of lines return {@link com.trevlar.menukit.api.element.MKTooltip#joinLines}.
         * A {@code null} result shows none.
         */
        public Builder tooltip(Supplier<@Nullable Component> tooltip) {
            this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
            return this;
        }

        /** Where it goes in its row: lower is further left; ties go by id. Default 0. */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** The edge its row stands on. Default {@link Edge#CONTAINERS_TOP}. */
        public Builder edge(Edge edge) {
            this.edge = Objects.requireNonNull(edge, "edge");
            return this;
        }

        /** What a left click on it does, with vanilla's click sound. Not run while it is disabled. */
        public Builder onClick(Runnable action) {
            this.onClick = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Drawn selected, open into the frame, while this holds, read every frame. MenuKit keeps no selection. */
        public Builder selectedWhen(BooleanSupplier condition) {
            this.selectedWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Shown while this holds, read every frame. Hidden, it takes no place in its row. */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Grayed and taking no click while this holds, read every frame. Its tooltip still shows. */
        public Builder disabledWhen(BooleanSupplier condition) {
            this.disabledWhen = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** The screens it is on. Without {@code on} or {@link #onMatching}, every container screen the region is on. */
        @SafeVarargs
        public final Builder on(Class<? extends AbstractContainerScreen<?>>... screens) {
            if (screens.length == 0) {
                throw new IllegalArgumentException("ContainerTabs tab '" + id + "': on() needs at least one screen class");
            }
            return onMatching(ScreenMatcher.only(screens));
        }

        /** The screens it is on, by a matcher. */
        public Builder onMatching(ScreenMatcher matcher) {
            if (targeted) throw new IllegalStateException("ContainerTabs tab '" + id + "': targeting declared twice");
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.targeted = true;
            return this;
        }

        /**
         * Declares the tab.
         *
         * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
         */
        public void declare() {
            Declarations.requireOpen("ContainerTabs.tab(" + id + ")");
            // The mode scope is one more condition, read every frame with the tab's own.
            BooleanSupplier own = visibleWhen;
            BooleanSupplier shown = scope == ModeScope.BOTH ? own : () -> scope.covers(PanelHost.currentMode()) && own.getAsBoolean();
            ContainerTabRows.declare(new ContainerTabRows.Tab(id, matcher, order, edge, shown, selectedWhen, disabledWhen,
                    item, sprite, tooltip, onClick));
        }
    }
}
