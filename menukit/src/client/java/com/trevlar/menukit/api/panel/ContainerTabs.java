package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.TabRegistry;
import com.trevlar.menukit.inject.PanelHost;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Tabs on the container screen's panels (7.4.0): a row of tabs, like the creative inventory's,
 * on an edge of the {@link ContainerRegion container region}'s container list or inventory
 * panel. MenuKit draws the row, hit-tests it, and pages it with arrows at its ends when it is
 * wider than its frame; everything a tab is and does is the mod's: what a click does, its icon
 * (anything the mod draws; an item or a sprite ready made), its tooltip, its order, whether it shows and whether it
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
 *   <li><b>On the screen's own frame</b> (7.4.0) on every other container screen: the
 *       player's inventory, a shulker box, a chest with no section showing, a mod's own
 *       container screen. A {@link Edge#CONTAINERS_TOP} row stands on the frame's top edge and
 *       an {@link Edge#INVENTORY_BOTTOM} row on its bottom edge; the two middle edges have no
 *       gap to stand in there, and their tabs don't show. The frame and those two rows are
 *       centered as one block, as the region's are: the frame moves from vanilla's place (half
 *       a top row lower, half a bottom row higher), and everything vanilla places from it
 *       with it; a row coming or going lays the screen out again. Screens of one size with
 *       the same rows put the row in one place ({@link ScreenFrames} makes a shulker box
 *       and a chest the inventory's 166), and a mod that opens one in place of another
 *       shows a steady row. Only vanilla's own screens move: a mod's own container screen
 *       class, a subclass of vanilla's included, may place things from the window itself, so
 *       its frame stays where vanilla centers it and its rows stand on it there.</li>
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
 * <h3>Groups</h3>
 * A keyed list with a {@link Keyed#header header} is a group: a header tab, and the keys' tabs
 * after it as its sub-tabs, smaller by default and standing on the same line. Groups are the
 * mod's to use or not, and everything about one is the mod's but how it moves:
 * <ul>
 *   <li><b>What a header click does</b> is its own {@code onClick}: show the header's own page,
 *       or pick its first sub-tab.</li>
 *   <li><b>When its sub-tabs are out</b> is {@link Keyed#expandedWhen}, read every frame:
 *       always, only while the group holds the selected tab, or on the mod's own conditions.</li>
 *   <li><b>How they move</b> is MenuKit's, one way only: they slide out from under the header
 *       to its right as the group opens, the tabs after it moving over to make room, and slide
 *       back under it as it closes, eased alike each way. A turn mid-way goes back from where
 *       they are. A slide that makes the row page opens the arrows' room with it, so the tabs
 *       move over rather than jump; a tab shows whole or not at all. A group stands
 *       as it is on a screen's first frame, so opening the inventory doesn't play it.</li>
 *   <li><b>Paging</b> counts the tabs out: a closed group is its header, an open one its header
 *       and every sub-tab, and the arrows page through those. A closed group's sub-tabs take no
 *       click.</li>
 *   <li><b>Pinned tabs</b> ({@link Builder#pinnedWhen}), a header or a sub-tab, stay in view
 *       while the row is paged either way past them: each stops at the end of the row it would
 *       leave by, the tabs beyond it going on behind it, and pinned tabs of one group stand
 *       there side by side in row order, until the next group's header (at the left end) or
 *       the previous group's pinned tabs (at the right end) push them off together, as section
 *       headers do in a phone's list. Which tabs pin is the mod's, read every frame: a header
 *       while its group is open, a sub-tab while it is selected.</li>
 * </ul>
 *
 * <pre>{@code
 * ContainerTabs.keyed("mymod:chest", ModeScope.BOTH, () -> chestBoxes())
 *         .header(h -> h
 *                 .icon(() -> new ItemStack(Items.CHEST))
 *                 .selectedWhen(() -> shown == CHEST)
 *                 .onClick(() -> shown = CHEST))
 *         .tab((box, t) -> t
 *                 .icon((g, x, y, w, h) -> drawBox(g, box, x, y, w, h))   // the mod's own drawing
 *                 .selectedWhen(() -> box.equals(shown))
 *                 .onClick(() -> shown = box))
 *         .expandedWhen(() -> keepAllOpen || groupOf(shown) == CHEST)
 *         .order(200)
 *         .declare();
 * }</pre>
 *
 * <h3>Groups that come and go</h3>
 * A mod whose groups come and go at runtime (one per open box that holds boxes, say) declares a
 * {@link #groups list of groups}: its group keys are read every frame as a keyed list's keys
 * are, and each is a group as {@link Keyed#header} makes one, with a header and its own keys'
 * sub-tabs, built the frame the key first appears and kept for as long as it is there. The groups
 * stand in the group keys' order, one after another, together at the list's order.
 *
 * <p><b>Nested groups</b> (7.5.0). A group whose key is also one of another live group's keys
 * stands inside that group, in place of the sub-tab that key would have: its header where the
 * sub-tab was, its own sub-tabs after it, then the rest of the parent's. So a box opened inside
 * a chest stays where its tab was, {@code [Chest] a b [Box ▸ its boxes] c d}, at any depth. The
 * nested group slides out from under its own header as any group does, and goes under its
 * parent's header with the parent's sub-tabs when the parent closes; its tabs are in the row
 * only while every group above it is expanded. A tab pinned in a nested group pins the headers
 * of every group its own group stands in, so paged past, the chain stands at the row's end side
 * by side ({@link Builder#pinnedWhen}). A key that is its own group's key stays a sub-tab, and a
 * group stands in one place only: the first group to hold it, in row order. Groups that stand
 * among each other belong in one list.
 *
 * <pre>{@code
 * ContainerTabs.groups("mymod:boxes", ModeScope.BOTH, () -> tree.groups(), group -> group.boxesInside())
 *         .header((group, h) -> h
 *                 .icon(group::stack)
 *                 .selectedWhen(() -> group.equals(shown))
 *                 .onClick(() -> shown = group))
 *         .tab((group, box, t) -> t
 *                 .icon(box::stack)
 *                 .selectedWhen(() -> box.equals(shown))
 *                 .onClick(() -> shown = box))
 *         .expandedWhen(group -> group.holds(shown))
 *         .order(200)
 *         .declare();
 * }</pre>
 *
 * <h3>Size and icons</h3>
 * {@link Builder#size} sets a tab's width and height, and {@link Keyed#tabSize} every key's
 * tab's. A tab's icon is the mod's own drawing ({@link Builder#icon(Icon)}), every frame, in an
 * icon box 16 px square on a full-size tab and smaller in proportion on a smaller one: an item,
 * a sprite, an animation, anything. The item and sprite forms are that, ready made.
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
        INVENTORY_BOTTOM
    }

    // A tab's measures, from vanilla's creative inventory.

    /** A tab's default width: vanilla's 26 px tab. Tabs stand side by side, touching, so no background shows between them. */
    public static final int TAB_WIDTH = 26;
    /**
     * A tab's default height beyond the frame edge it stands on: vanilla's creative top tab
     * stands 28 px above its frame, these 25, room for a full-size icon inside an unpicked
     * tab with two rows clear above and below it. A double chest with a row above it needs
     * 245 px (212 + 25 and the two margins), so in a 240 px window its grid scrolls by 5.
     */
    public static final int TAB_HEIGHT = 25;
    /** A group's sub-tabs by default: about two-thirds of a tab, on the same line. */
    public static final int SUB_WIDTH = 18, SUB_HEIGHT = 17;
    /** The smallest a tab can be, either way: its sprite's corners and edges, kept whole. */
    public static final int MIN_SIZE = 8;
    /** How long a group's sub-tabs take to slide open or closed, in ms. */
    public static final int SLIDE_MILLIS = 200;

    /**
     * Starts a tab, its id namespaced and unique ({@code "mymod:backpack"}), for one mode scope:
     * it shows only while the player is in that mode ({@link ModeScope#BOTH} for either), read
     * every frame, as its {@link Builder#visibleWhen} is.
     */
    public static Builder tab(String id, ModeScope scope) {
        return new Builder(id, Objects.requireNonNull(scope, "scope"));
    }

    /** A keyed list's tab ({@link #keyed}): on the screens its list is on. */
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
     * Starts a list of groups, its id namespaced and unique among tabs and lists
     * ({@code "mymod:boxes"}), for one mode scope: one group per key {@code groups} gives, read
     * every frame a container screen is open, in the order it gives them, and in each group one
     * sub-tab per key {@code keys} gives for it, read every frame as a keyed list's are. Keys of
     * both kinds compare with {@code equals}; a repeated key has one group or tab, the first. A
     * {@code null} list is no keys.
     */
    public static <G, K> Groups<G, K> groups(String id, ModeScope scope, Supplier<? extends @Nullable List<? extends G>> groups,
                                             Function<? super G, ? extends @Nullable List<? extends K>> keys) {
        return new Groups<>(id, Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(groups, "groups"),
                Objects.requireNonNull(keys, "keys"));
    }

    /** What a list of groups' sub-tab is ({@link Groups#tab}): its group, its key, and the builder to set up. */
    @FunctionalInterface
    public interface GroupTab<G, K> {
        void build(G group, K key, Builder tab);
    }

    /**
     * Builds a list of groups ({@link #groups}). Its screens, order, edge and sub-tab size are the
     * list's, shared by every group in it; each group's header and each key's tab set the rest.
     * Each group is a keyed list with a {@link Keyed#header header}, as one declared alone is,
     * and behaves as one: its header, its sub-tabs sliding out and back, paging and pinning.
     */
    public static final class Groups<G, K> {
        private final String id;
        private final ModeScope scope;
        private final Supplier<? extends @Nullable List<? extends G>> groups;
        private final Function<? super G, ? extends @Nullable List<? extends K>> keys;
        private ScreenMatcher matcher = ScreenMatcher.all();
        private boolean targeted = false;
        private int order = 0;
        private Edge edge = Edge.CONTAINERS_TOP;
        private @Nullable BiConsumer<? super G, Builder> header;
        private @Nullable GroupTab<? super G, ? super K> tab;
        private @Nullable Predicate<? super G> expanded;
        private int tabWidth, tabHeight;

        private Groups(String id, ModeScope scope, Supplier<? extends @Nullable List<? extends G>> groups,
                       Function<? super G, ? extends @Nullable List<? extends K>> keys) {
            this.id = Objects.requireNonNull(id, "id");
            this.scope = scope;
            this.groups = groups;
            this.keys = keys;
        }

        /**
         * Required: each group's header tab, set up as {@link Keyed#header} sets one up, by group.
         * Run once per group, the frame its key first appears. Its id is {@code id#group}.
         */
        public Groups<G, K> header(BiConsumer<? super G, Builder> build) {
            this.header = Objects.requireNonNull(build, "build");
            return this;
        }

        /**
         * Required: each key's sub-tab in a group, set up as {@link Keyed#tab} sets one up, by
         * group and key. Run once per key in a group, the frame it first appears there. Its id is
         * {@code id#group#key}.
         */
        public Groups<G, K> tab(GroupTab<? super G, ? super K> build) {
            this.tab = Objects.requireNonNull(build, "build");
            return this;
        }

        /** When a group's sub-tabs are out, by group, read every frame, as {@link Keyed#expandedWhen} reads. Default: always. */
        public Groups<G, K> expandedWhen(Predicate<? super G> condition) {
            this.expanded = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Every sub-tab's size, as {@link Keyed#tabSize} takes it. Default {@value #SUB_WIDTH} by {@value #SUB_HEIGHT}. */
        public Groups<G, K> tabSize(int width, int height) {
            requireSize("ContainerTabs groups '" + id + "'", width, height);
            this.tabWidth = width;
            this.tabHeight = height;
            return this;
        }

        /** Where the groups go in their row, every tab of them together, in the group keys' order: lower is further left; ties go by id. Default 0. */
        public Groups<G, K> order(int order) {
            this.order = order;
            return this;
        }

        /** The edge their row stands on. Default {@link Edge#CONTAINERS_TOP}. */
        public Groups<G, K> edge(Edge edge) {
            this.edge = Objects.requireNonNull(edge, "edge");
            return this;
        }

        /** The screens their tabs are on. Without {@code on} or {@link #onMatching}, every container screen (see "Where a row stands"). */
        @SafeVarargs
        public final Groups<G, K> on(Class<? extends AbstractContainerScreen<?>>... screens) {
            if (screens.length == 0) {
                throw new IllegalArgumentException("ContainerTabs groups '" + id + "': on() needs at least one screen class");
            }
            return onMatching(ScreenMatcher.only(screens));
        }

        /** The screens their tabs are on, by a matcher. */
        public Groups<G, K> onMatching(ScreenMatcher matcher) {
            if (targeted) throw new IllegalStateException("ContainerTabs groups '" + id + "': targeting declared twice");
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            this.targeted = true;
            return this;
        }

        /**
         * Declares the list.
         *
         * @throws IllegalStateException after declarations froze, when {@code id} is already a
         *         tab's or a list's or its tabs' ids could be another's, or when no header or no
         *         tab was given
         */
        @SuppressWarnings("unchecked")
        public void declare() {
            Declarations.requireOpen("ContainerTabs.groups(" + id + ")");
            if (header == null) throw new IllegalStateException("ContainerTabs groups '" + id + "': header(...) is required");
            if (tab == null) throw new IllegalStateException("ContainerTabs groups '" + id + "': tab(...) is required");
            BiConsumer<? super G, Builder> headerOf = header;
            GroupTab<? super G, ? super K> tabOf = tab;
            Predicate<? super G> open = expanded;
            // Each group is a keyed list with a header, kept by the list and its group key, so its
            // slide and what the row keeps by it follow the key, as a key's tab does.
            TabRegistry.declareGroups(new TabRegistry.Groups(id, groups::get, key -> {
                G group = (G) key;
                Keyed<K> list = new Keyed<>(id + "#" + group, scope, () -> keys.apply(group));
                list.ref = new TabRegistry.KeyRef(id, group);
                list.matcher = matcher;
                list.order = order;
                list.edge = edge;
                list.tabWidth = tabWidth;
                list.tabHeight = tabHeight;
                list.header = h -> headerOf.accept(group, h);
                list.tab = (k, b) -> tabOf.build(group, k, b);
                if (open != null) list.expanded = () -> open.test(group);
                return list.build();
            }));
        }
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
        private @Nullable Consumer<Builder> header;
        private @Nullable BooleanSupplier expanded;
        /** Each key's tab's size before its own {@code size}; 0 for the default. */
        private int tabWidth, tabHeight;
        /** What the rows keep the list's header and slide by: its id, or a list of groups' group's key ref. */
        private Object ref;

        private Keyed(String id, ModeScope scope, Supplier<? extends @Nullable List<? extends K>> keys) {
            this.id = Objects.requireNonNull(id, "id");
            this.scope = scope;
            this.keys = keys;
            this.ref = id;
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

        /**
         * Makes the list a group: {@code build} sets up its header tab, as a key's tab is set up,
         * and the keys' tabs become its sub-tabs, standing after it. The header shows while its
         * own {@code visibleWhen} holds, with or without keys, and its sub-tabs only with it. What
         * a click on it does is its {@code onClick}, the mod's (show its own page, pick its first
         * sub-tab). Its id is the list's. Without {@link #tabSize}, sub-tabs are
         * {@value #SUB_WIDTH} by {@value #SUB_HEIGHT}.
         */
        public Keyed<K> header(Consumer<Builder> build) {
            this.header = Objects.requireNonNull(build, "build");
            return this;
        }

        /**
         * When a group's sub-tabs are out, read every frame: always, only while one of them is
         * selected, or whatever the mod's own rule is. Default: always. MenuKit slides them out
         * from under the header to its right as this starts holding and back under it as it
         * stops, in {@value #SLIDE_MILLIS} ms, a turn mid-way going back from
         * where they are. Only for a list with a {@link #header}.
         */
        public Keyed<K> expandedWhen(BooleanSupplier condition) {
            this.expanded = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * Every key's tab's size, as {@link Builder#size} takes it, before a key's own {@code size}.
         * Default: a full tab, or a group's sub-tab size with a {@link #header}.
         */
        public Keyed<K> tabSize(int width, int height) {
            requireSize("ContainerTabs keyed '" + id + "'", width, height);
            this.tabWidth = width;
            this.tabHeight = height;
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
         *         tab's or a list's, when no tab was given, or with {@code expandedWhen} and no header
         */
        public void declare() {
            Declarations.requireOpen("ContainerTabs.keyed(" + id + ")");
            TabRegistry.declareKeyed(build());
        }

        /** The list as built so far, for the registry: on its own, or as a list of groups' group. */
        @SuppressWarnings("unchecked")
        TabRegistry.Keyed build() {
            if (tab == null) throw new IllegalStateException("ContainerTabs keyed '" + id + "': tab(...) is required");
            if (expanded != null && header == null) {
                throw new IllegalStateException("ContainerTabs keyed '" + id + "': expandedWhen(...) is for a group, one with a header(...)");
            }
            BiConsumer<? super K, Builder> tabOf = tab;
            ScreenMatcher screens = matcher;
            int at = order;
            Edge on = edge;
            // A group's header, built once, its id the list's; its sub-tabs show only with it.
            Object kept = ref;
            TabRegistry.Group group = header == null ? null
                    : new TabRegistry.Group(id, kept, expanded != null ? expanded : () -> true);
            TabRegistry.Tab head = null;
            if (group != null) {
                Builder h = forKey(id, scope, screens);
                h.order = at;
                h.edge = on;
                h.fixed = true;
                h.group = group;
                h.ref = kept;
                header.accept(h);
                head = h.build();
            }
            BooleanSupplier headShown = head == null ? null : head.visible();
            int w = tabWidth > 0 ? tabWidth : group != null ? SUB_WIDTH : TAB_WIDTH;
            int ht = tabHeight > 0 ? tabHeight : group != null ? SUB_HEIGHT : TAB_HEIGHT;
            // Each key's tab is named by the list and the key, so what the row keeps by id follows the key.
            return new TabRegistry.Keyed(id, keys::get, key -> {
                Builder b = forKey(id + "#" + key, scope, screens);
                b.ref = new TabRegistry.KeyRef(kept, key);
                b.order = at;
                b.edge = on;
                b.fixed = true;
                b.width = w;
                b.height = ht;
                b.group = group;
                b.sub = group != null;
                b.under = headShown;
                tabOf.accept((K) key, b);
                return b.build();
            }, head);
        }
    }

    /**
     * What a tab's icon draws: the mod's own drawing, every frame, in the icon box at
     * {@code (x, y)}, {@code width} by {@code height}. The box is 16 px square on a full-size tab
     * and smaller in proportion on a smaller one (a group's sub-tab's is 10), so the drawing fits
     * itself to it. Anything goes: an item, a sprite, an {@link com.trevlar.menukit.api.element.ItemAnimation}
     * at the point the mod's {@link com.trevlar.menukit.api.element.Transition} has got to, a
     * model the mod turns itself. MenuKit clips it only as a sliding group's tab is clipped.
     *
     * <pre>{@code
     * .icon((g, x, y, w, h) -> {
     *     g.pose().pushMatrix();
     *     g.pose().translate(x, y);
     *     g.pose().scale(w / 16f, h / 16f);
     *     OPEN.draw(g, box.stack(), 0, 0, OPENING.toward(box, box.equals(shown)));
     *     g.pose().popMatrix();
     * })
     * }</pre>
     */
    @FunctionalInterface
    public interface Icon {
        void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height);
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
        private @Nullable Icon icon;
        private int width = TAB_WIDTH, height = TAB_HEIGHT;
        /** A group's header or sub-tab: its group, whether a sub-tab, and its header's showing, which a sub-tab's adds to. */
        private TabRegistry.@Nullable Group group;
        private boolean sub;
        private @Nullable BooleanSupplier under;
        /** What the rows keep it by: its id, or for a key's tab its list and key. */
        private @Nullable Object ref;
        private @Nullable Supplier<@Nullable Component> tooltip;
        private Runnable onClick = () -> {};
        private BooleanSupplier visibleWhen = () -> true, selectedWhen = () -> false, disabledWhen = () -> false, pinnedWhen = () -> false;

        private Builder(String id, ModeScope scope) {
            this.id = Objects.requireNonNull(id, "id");
            this.scope = scope;
        }

        /**
         * The tab's icon, drawn by the mod every frame: anything at all (an item, a sprite, an
         * animation, a model it turns itself), fitted to the box MenuKit gives it. MenuKit only
         * says where the box is, and clips it with the tab as a group slides.
         */
        public Builder icon(Icon icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        /** The tab's icon, an item, read every frame, filling the icon box: 16 px on a full-size tab, smaller in proportion on a smaller one. */
        public Builder icon(Supplier<ItemStack> item) {
            Objects.requireNonNull(item, "item");
            return icon((g, x, y, w, h) -> {
                g.pose().pushMatrix();
                try {
                    g.pose().translate(x, y);
                    g.pose().scale(w / 16f, h / 16f);
                    g.item(item.get(), 0, 0);
                } finally {
                    g.pose().popMatrix();
                }
            });
        }

        /**
         * The tab's icon, a GUI sprite of the mod's own ({@code mymod:tab/backpack}, under
         * {@code textures/gui/sprites/}), filling the icon box, where an item icon goes.
         */
        public Builder icon(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return icon((g, x, y, w, h) -> g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h));
        }

        /**
         * The tab's size: {@code width} across and {@code height} out from the frame, each at
         * least {@value #MIN_SIZE}. Default 26 by 25, vanilla's creative tab as a
         * row has it. Every tab stands on the frame, so tabs of different heights share their
         * line, and the row is as tall as its tallest.
         *
         * @throws IllegalArgumentException when either is below {@value #MIN_SIZE}
         */
        public Builder size(int width, int height) {
            requireSize("ContainerTabs tab '" + id + "'", width, height);
            this.width = width;
            this.height = height;
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

        /**
         * Pinned while this holds, read every frame: as the row is paged past it, it stops at the
         * end of the row it would leave by, still clickable, and the tabs beyond it go on behind
         * it. Paged forward, it stops at the left end until the next group's header pushes it
         * off; paged back, at the right end, other groups' tabs going on behind it too, until the
         * previous group's pinned tabs push it off. A
         * group's pinned tabs, its header and any of its sub-tabs, stand there side by side in
         * row order and leave together. In a {@link #groups nested group}, a pinned tab pins the
         * headers of the groups above its own too, and the whole chain stands and leaves
         * together, pushed off by the next top group's header. A selected tab
         * behind a pinned one is brought out from behind it. Pinning is a group's: a tab outside
         * any group is pushed off by the tab after it, so it pages as before. Default: never.
         */
        public Builder pinnedWhen(BooleanSupplier condition) {
            this.pinnedWhen = Objects.requireNonNull(condition, "condition");
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
            TabRegistry.declare(build());
        }

        /** The tab as built so far, its mode scope one more condition, read every frame with its own. */
        TabRegistry.Tab build() {
            BooleanSupplier own = visibleWhen;
            BooleanSupplier scoped = scope == ModeScope.BOTH ? own : () -> scope.covers(PanelHost.currentMode()) && own.getAsBoolean();
            BooleanSupplier header = under;
            BooleanSupplier shown = header == null ? scoped : () -> header.getAsBoolean() && scoped.getAsBoolean();
            return new TabRegistry.Tab(id, ref != null ? ref : id, matcher, order, edge, shown, selectedWhen, disabledWhen, pinnedWhen,
                    icon, tooltip, onClick, width, height, group, sub);
        }
    }

    private static void requireSize(String what, int width, int height) {
        if (width < MIN_SIZE || height < MIN_SIZE) {
            throw new IllegalArgumentException(what + ": size(" + width + ", " + height + ") is below "
                    + MIN_SIZE + " either way");
        }
    }
}
