package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.ContainerTabRows;
import com.trevlar.menukit.inject.PanelHost;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
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
 * <h3>Where a row stands</h3>
 * <ul>
 *   <li><b>On the region's panels</b> while the region is up (a container screen with a
 *       section showing), on any of the four edges.</li>
 *   <li><b>On the screen's own frame</b> (Fluorite) on every other container screen: the
 *       player's inventory, a shulker box, a chest with no section showing, a mod's own
 *       container screen. A {@link Edge#CONTAINERS_TOP} row stands on the frame's top edge and
 *       an {@link Edge#INVENTORY_BOTTOM} row on its bottom edge; the two middle edges have no
 *       gap to stand in there, and their tabs don't show. The screen keeps vanilla's layout,
 *       so screens of one size put the row in one place ({@link ScreenFrames} makes the
 *       inventory, 166 tall, a shulker box's 167), and a mod that opens one in place of another
 *       shows a steady row.</li>
 *   <li>Never on the creative inventory, which has tabs of its own.</li>
 * </ul>
 * A row's page carries over to a screen opened in place of the last, and starts over once
 * the player is back in the world.
 *
 * <h3>A tab per key</h3>
 * A mod whose tabs come and go at runtime (one per box the player has open, say) declares a
 * {@link #keyed keyed list} instead of a tab for each it might ever show. Its keys are read
 * every frame, any type with {@code equals}; each key's tab is built the frame the key first
 * appears and kept for as long as it is there, so what MenuKit keeps per tab (the page the
 * row is on, the selected tab kept in view) follows the key wherever the keys move it. The
 * list's tabs stand in the keys' order, together at the list's {@link Keyed#order order}
 * among the other tabs, on the frame or the region's panels as any tab does.
 *
 * <pre>{@code
 * ContainerTabs.keyed("mymod:boxes", ModeScope.BOTH, () -> boxes.open())
 *         .tab((box, t) -> t
 *                 .icon(box::stack)
 *                 .tooltip(() -> box.stack().getHoverName())
 *                 .selectedWhen(() -> box.equals(shown))
 *                 .onClick(() -> shown = box))
 *         .on(InventoryScreen.class, ContainerScreen.class, ShulkerBoxScreen.class)
 *         .order(200)
 *         .declare();
 * }</pre>
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

    /** A live key's tab ({@link ContainerRegion#sections}, {@link #keyed}): on the screens its kind of section, or its list, is on. */
    static Builder forKey(String id, ModeScope scope, ScreenMatcher screens) {
        Builder b = new Builder(id, scope);
        b.matcher = screens;
        b.targeted = true;
        return b;
    }

    /**
     * Starts a keyed list of tabs, its id namespaced and unique among tabs and lists
     * ({@code "mymod:boxes"}), for one mode scope: one tab per key {@code keys} gives, read
     * every frame a container screen is open, in the order it gives them. Keys compare with
     * {@code equals}; a repeated key has one tab, the first. A {@code null} list is no keys.
     */
    public static <K> Keyed<K> keyed(String id, ModeScope scope, Supplier<? extends @Nullable List<? extends K>> keys) {
        return new Keyed<>(id, Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(keys, "keys"));
    }

    /**
     * Builds a keyed list of tabs ({@link #keyed}). Its screens, order and edge are the
     * list's, shared by every tab in it; each key's tab sets the rest.
     */
    public static final class Keyed<K> {
        private final String id;
        private final ModeScope scope;
        private final Supplier<? extends @Nullable List<? extends K>> keys;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        private int order = 0;
        private Edge edge = Edge.CONTAINERS_TOP;
        private @Nullable BiConsumer<? super K, Builder> tab;

        private Keyed(String id, ModeScope scope, Supplier<? extends @Nullable List<? extends K>> keys) {
            this.id = Objects.requireNonNull(id, "id");
            this.scope = scope;
            this.keys = keys;
        }

        /**
         * Required: each key's tab, set up on a builder as a declared tab is (its icon,
         * tooltip, what a click does, when it shows, draws selected or is disabled), minus
         * {@code declare()}, its screens, order and edge, which are the list's: setting those
         * on it throws. Run once per key, the frame the key first appears; what the tab shows
         * that changes reads through suppliers, by key.
         */
        public Keyed<K> tab(BiConsumer<? super K, Builder> build) {
            this.tab = Objects.requireNonNull(build, "build");
            return this;
        }

        /** Where the list goes in its row, every tab of it together, in the keys' order: lower is further left; ties go by id. Default 0. */
        public Keyed<K> order(int order) {
            this.order = order;
            return this;
        }

        /** The edge its row stands on. Default {@link Edge#CONTAINERS_TOP}. */
        public Keyed<K> edge(Edge edge) {
            this.edge = Objects.requireNonNull(edge, "edge");
            return this;
        }

        /** The screens its tabs are on. Without {@code on} or {@link #onMatching}, every container screen (see "Where a row stands"). */
        @SafeVarargs
        public final Keyed<K> on(Class<? extends AbstractContainerScreen<?>>... screens) {
            if (screens.length == 0) {
                throw new IllegalArgumentException("ContainerTabs keyed '" + id + "': on() needs at least one screen class");
            }
            return onMatching(ScreenMatcher.only(screens));
        }

        /** The screens its tabs are on, by a matcher. */
        public Keyed<K> onMatching(ScreenMatcher matcher) {
            if (targeted) throw new IllegalStateException("ContainerTabs keyed '" + id + "': targeting declared twice");
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.targeted = true;
            return this;
        }

        /**
         * Declares the list.
         *
         * @throws IllegalStateException after declarations froze, when {@code id} is already a
         *         tab's or a list's, or when no tab was given
         */
        @SuppressWarnings("unchecked")
        public void declare() {
            Declarations.requireOpen("ContainerTabs.keyed(" + id + ")");
            if (tab == null) throw new IllegalStateException("ContainerTabs keyed '" + id + "': tab(...) is required");
            BiConsumer<? super K, Builder> tabOf = tab;
            ScreenMatcher screens = matcher;
            int at = order;
            Edge on = edge;
            // Each key's tab is named by the list and the key, so what the row keeps by id follows the key.
            ContainerTabRows.declareKeyed(new ContainerTabRows.Keyed(id, keys::get, key -> {
                Builder b = forKey(id + "#" + key, scope, screens);
                b.order = at;
                b.edge = on;
                b.fixed = true;
                tabOf.accept((K) key, b);
                return b.build();
            }));
        }
    }

    /** Builds one tab. Every part is optional; a tab with no icon is an empty tab. */
    public static final class Builder {
        private final String id;
        private final ModeScope scope;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        /** A keyed list's tab: its order and edge are the list's. */
        private boolean fixed = false;
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
            if (fixed) throw new IllegalStateException("ContainerTabs tab '" + id + "': a keyed list's tabs take its order");
            this.order = order;
            return this;
        }

        /** The edge its row stands on. Default {@link Edge#CONTAINERS_TOP}. */
        public Builder edge(Edge edge) {
            if (fixed) throw new IllegalStateException("ContainerTabs tab '" + id + "': a keyed list's tabs take its edge");
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

        /** The screens it is on. Without {@code on} or {@link #onMatching}, every container screen (see "Where a row stands"). */
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
            ContainerTabRows.declare(build());
        }

        /** The tab as built so far, its mode scope one more condition, read every frame with its own. */
        ContainerTabRows.Tab build() {
            BooleanSupplier own = visibleWhen;
            BooleanSupplier shown = scope == ModeScope.BOTH ? own : () -> scope.covers(PanelHost.currentMode()) && own.getAsBoolean();
            return new ContainerTabRows.Tab(id, matcher, order, edge, shown, selectedWhen, disabledWhen,
                    item, sprite, tooltip, onClick);
        }
    }
}
