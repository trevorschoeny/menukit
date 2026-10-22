package com.trevlar.menukit.inject;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.inject.RegionLayout.Geometry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The container screen's tab rows (7.4.0; the public face is {@code api.panel.ContainerTabs}):
 * the declared tabs, this frame's row on each {@link Edge}, and each row drawn, hit and paged.
 * {@link ContainerRegions} asks each frame which edges have a row, so the layout makes room for
 * it ({@link RegionLayout.Shape}); what a tab does is the mod's, through its callbacks.
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class ContainerTabRows {

    private ContainerTabRows() {}

    // The row's measures, from vanilla's creative inventory.

    /** A tab's width, and its place in the row: vanilla's 26 px tab, a pixel apart. */
    public static final int TAB_WIDTH = 26, STEP = 27;
    /** Vanilla's tab sprite's height; the {@link #INTO} rows at its frame end are where a selected tab opens into the frame. */
    static final int SPRITE_HEIGHT = 32, INTO = 4;
    /**
     * The rows left out of the plain middle of a sprite, so a tab stands {@link RegionLayout#TAB_ROW}
     * beyond its frame rather than vanilla's 28: from row {@link #CUT_FROM} on, sides and fill.
     * A bottom tab that isn't selected leaves out one: vanilla's ends 4 px short of the selected
     * one, where a top one ends 2 px short, so with one row out the two edges match.
     */
    static final int CUT = SPRITE_HEIGHT - INTO - RegionLayout.TAB_ROW, CUT_FROM = 12, BOTTOM_CUT = 1;
    /** The icon, full size, across the middle of the tab, and 2 px clear of the frame: 18 px out from it to the icon's far side. */
    static final int ICON_X = (TAB_WIDTH - 16) / 2, ICON_FROM_FRAME = 18;
    /** The paging arrows: vanilla's recipe book page arrows, at the row's two ends. */
    public static final int ARROW_WIDTH = 12, ARROW_HEIGHT = 17;
    /** {@link #hit} answers: nothing, the back arrow, the forward arrow (a tab is its place in the row, 0 up). */
    public static final int NONE = -1, BACK = -2, FORWARD = -3;
    /** A disabled tab: its own shape drawn again over it in black at this alpha, so it and its icon read 0.3 as bright. */
    static final int GRAYED = 0xB3000000;

    // The declared tabs

    /**
     * One declared tab ({@code ContainerTabs.tab}). {@code visible} already holds its mode
     * scope; its icon is {@code item} or {@code sprite}, at most one.
     */
    public record Tab(String id, ScreenMatcher matcher, int order, Edge edge, BooleanSupplier visible,
                      BooleanSupplier selected, BooleanSupplier disabled, @Nullable Supplier<ItemStack> item,
                      @Nullable Identifier sprite, @Nullable Supplier<@Nullable Component> tooltip, Runnable onClick) {}

    private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
    private static boolean panelsBuilt;

    /**
     * A tab one live key made ({@code ContainerRegion.sections}): placed among the declared
     * tabs by its order, then by its section kind's id, then by its key's place in the keys.
     */
    record Live(Tab tab, String kind, int index) {}

    private static List<Live> live = List.of();
    /** Every tab there is now, declared and live, in row order. */
    private static List<Tab> all = List.of();

    /** {@code ContainerTabs.Builder.declare}: adds a tab in its place, and the rows' panels with the first one. */
    public static synchronized void declare(Tab tab) {
        for (Tab t : TABS) {
            if (t.id().equals(tab.id())) throw new IllegalStateException("ContainerTabs tab '" + tab.id() + "' is already declared");
        }
        TABS.add(tab);
        merge();
        buildPanels();
    }

    /** The live keys' tabs now, from the container region whenever its sections restack. */
    static void live(List<Live> tabs) {
        live = List.copyOf(tabs);
        merge();
    }

    private static void merge() {
        List<Live> every = new ArrayList<>(TABS.size() + live.size());
        for (Tab t : TABS) every.add(new Live(t, t.id(), 0));
        every.addAll(live);
        every.sort(Comparator.comparingInt((Live l) -> l.tab().order()).thenComparing(Live::kind).thenComparingInt(Live::index));
        all = every.stream().map(Live::tab).toList();
    }

    /** The rows' panels, made once, with the first tab or the first kind of section with tabs. */
    static synchronized void buildPanels() {
        if (panelsBuilt) return;
        panelsBuilt = true;
        // One background panel per edge, drawn over the region's two so a selected tab opens
        // into its frame, on the screens the region is on.
        for (Edge edge : Edge.values()) {
            Panel row = Panel.builder("menukit:container_tabs_" + edge.name().toLowerCase(Locale.ROOT))
                    .style(PanelStyle.NONE).background()
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> origin(edge))
                            .priority(PanelPosition.DEFAULT_PRIORITY + 1)))
                    .add(new Strip(edge))
                    .build()
                    .visibleWhen(() -> ContainerRegions.state.geometry != null && ContainerRegions.state.applied != null
                            && rows[edge.ordinal()].length > 0);
            new ScreenPanelAdapter(row, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
        }
    }

    // This frame's rows

    /** Each edge's row this frame, in order, by {@link Edge#ordinal}: empty where none shows. */
    private static Tab[][] rows = empty();
    /** Each row's first tab in view when they don't all fit, and the selected tab last brought into view. */
    private static final int[] FIRST = new int[Edge.values().length];
    private static final @Nullable String[] IN_VIEW = new String[Edge.values().length];

    private static Tab[][] empty() {
        Tab[][] out = new Tab[Edge.values().length][];
        Arrays.fill(out, new Tab[0]);
        return out;
    }

    /** The open region went (or another opened): the rows and their paging go with it. */
    static void reset() {
        rows = empty();
        Arrays.fill(FIRST, 0);
        Arrays.fill(IN_VIEW, null);
    }

    /**
     * This frame's rows on {@code screen}: on each edge, the tabs that show there now, in order.
     * None with no screen (MenuKit's checks of the bare layout) or no region on it.
     */
    static void choose(@Nullable Screen screen) {
        Tab[][] out = empty();
        if (screen instanceof AbstractContainerScreen<?> acs) {
            List<List<Tab>> by = new ArrayList<>();
            for (int i = 0; i < out.length; i++) by.add(new ArrayList<>());
            for (Tab t : all) if (t.matcher().matches(acs) && t.visible().getAsBoolean()) by.get(t.edge().ordinal()).add(t);
            for (int i = 0; i < out.length; i++) out[i] = by.get(i).toArray(Tab[]::new);
        }
        rows = out;
    }

    /** How far {@code edge}'s row stands out from its frame this frame: {@link RegionLayout#TAB_ROW}, or 0 with none. */
    static int height(Edge edge) {
        return rows[edge.ordinal()].length > 0 ? RegionLayout.TAB_ROW : 0;
    }

    /** The ids in {@code edge}'s row this frame, in order, for the checks. */
    @VisibleForTesting
    public static List<String> rowForChecks(Edge edge) {
        return Arrays.stream(rows[edge.ordinal()]).map(Tab::id).toList();
    }

    /** The first tab in view in {@code edge}'s row, for the checks. */
    @VisibleForTesting
    public static int firstForChecks(Edge edge) {
        return FIRST[edge.ordinal()];
    }

    // Where each row is

    /**
     * The frame edge a row stands on: from {@code x}, {@code width} across, at {@code y}, and
     * whether the tabs stand up from it ({@code top}) or hang down.
     */
    public record Line(int x, int width, int y, boolean top) {

        /** The row's top: a row above its edge ends there, one below starts there. */
        public int rowTop() { return top ? y - RegionLayout.TAB_ROW : y; }

        /** Where a tab's icon goes, its top: 18 px out from the frame to the icon's far side. */
        public int iconY() { return top ? y - ICON_FROM_FRAME : y + ICON_FROM_FRAME - 16; }
    }

    /** {@code edge}'s line on {@code g}: the list's top or bottom, or the inventory panel's. */
    public static Line line(Geometry g, Edge edge) {
        Reference inv = g.inventory();
        return switch (edge) {
            case CONTAINERS_TOP -> new Line(g.x(), g.width(), g.top(), true);
            case CONTAINERS_BOTTOM -> new Line(g.x(), g.width(), g.bottom(), false);
            case INVENTORY_TOP -> new Line(inv.leftPos(), inv.imageWidth(), inv.topPos(), true);
            case INVENTORY_BOTTOM -> new Line(inv.leftPos(), inv.imageWidth(), inv.topPos() + inv.imageHeight(), false);
        };
    }

    /** {@code edge}'s panel's origin: the row's top-left, a bottom row's {@link #INTO} rows up to take its selected tab. */
    private static @Nullable ScreenOrigin origin(Edge edge) {
        Geometry g = ContainerRegions.state.geometry;
        if (g == null) return null;
        Line l = line(g, edge);
        return new ScreenOrigin(l.x(), l.top() ? l.rowTop() : l.y() - INTO);
    }

    // The row, pure

    /**
     * How the row lays out: the first tab in view, how many are, and whether they are paged
     * (the arrows at the row's ends take room then).
     */
    public record Fit(int first, int shown, boolean paged) {}

    /** How many of {@code count} tabs fit a {@code width} wide frame: all, or with the arrows at its ends, as many as fit between them (one at least). */
    public static int fits(int width, int count) {
        if (count * STEP - (STEP - TAB_WIDTH) <= width) return count;
        return Math.max(1, (width - 2 * (ARROW_WIDTH + 1) + (STEP - TAB_WIDTH)) / STEP);
    }

    /** The row for {@code count} tabs on a {@code width} wide frame, from {@code first} on, clamped so the last page is full. */
    public static Fit fit(int width, int count, int first) {
        int shown = fits(width, count);
        return new Fit(Math.max(0, Math.min(first, count - shown)), shown, shown < count);
    }

    /** Tab {@code i}'s left edge (its place in the row, 0 up, in view), for a frame whose left edge is {@code x}. */
    public static int tabX(int x, Fit f, int i) {
        return x + (f.paged() ? ARROW_WIDTH + 1 : 0) + (i - f.first()) * STEP;
    }

    /**
     * What is at {@code (mx, my)} in the row on line {@code l}: a tab in view (its place in the
     * row), an arrow there is one for, or {@link #NONE}. A tab runs across the row, from the
     * frame to {@link RegionLayout#TAB_ROW} out.
     */
    public static int hit(Line l, int count, Fit f, double mx, double my) {
        if (my < l.rowTop() || my >= l.rowTop() + RegionLayout.TAB_ROW || mx < l.x() || mx >= l.x() + l.width()) return NONE;
        if (f.paged()) {
            if (mx < l.x() + ARROW_WIDTH) return f.first() > 0 ? BACK : NONE;
            if (mx >= l.x() + l.width() - ARROW_WIDTH) return f.first() + f.shown() < count ? FORWARD : NONE;
        }
        for (int i = f.first(); i < f.first() + f.shown(); i++) {
            int tx = tabX(l.x(), f, i);
            if (mx >= tx && mx < tx + TAB_WIDTH) return i;
        }
        return NONE;
    }

    /**
     * Once the region is laid out: each row's first tab is the one it draws from, and in each
     * row a tab that has started drawing selected comes into view (paging leaves the selection
     * alone, and a tab selected all along stays wherever the arrows put the row).
     */
    static void afterLayout(Geometry g) {
        for (Edge edge : Edge.values()) {
            Tab[] row = rows[edge.ordinal()];
            int e = edge.ordinal(), sel = -1;
            // Kept as drawn: on a frame wide enough for every tab (a list that took a
            // scrollbar's lane, a wider section) the row draws from its first, and a row
            // kept on an earlier page would go back there once the frame narrows again,
            // the selected tab out of view though it never moved.
            Fit f = fit(line(g, edge).width(), row.length, FIRST[e]);
            FIRST[e] = f.first();
            for (int i = 0; i < row.length && sel < 0; i++) if (row[i].selected().getAsBoolean()) sel = i;
            String id = sel < 0 ? null : row[sel].id();
            if (id == null || id.equals(IN_VIEW[e])) {
                IN_VIEW[e] = id;
                continue;
            }
            IN_VIEW[e] = id;
            if (sel < f.first()) FIRST[e] = sel;
            else if (sel >= f.first() + f.shown()) FIRST[e] = sel - f.shown() + 1;
        }
    }

    // The row, drawn and clicked

    /**
     * A left click at {@code (mx, my)} on {@code edge}'s row: on an enabled tab it runs the
     * tab's action, on an arrow it pages, each with vanilla's click sound. Whether the click
     * was the row's: anywhere on a tab or an arrow, an item held or not, so it goes no further
     * and a held item is neither dropped nor placed. A disabled tab takes it and does nothing.
     */
    public static boolean click(Edge edge, double mx, double my) {
        Geometry g = ContainerRegions.state.geometry;
        Tab[] row = rows[edge.ordinal()];
        if (g == null || row.length == 0) return false;
        Line l = line(g, edge);
        int e = edge.ordinal();
        Fit f = fit(l.width(), row.length, FIRST[e]);
        int what = hit(l, row.length, f, mx, my);
        if (what == NONE) return false;
        if (what == BACK) FIRST[e] = Math.max(0, f.first() - 1);
        else if (what == FORWARD) FIRST[e] = f.first() + 1;
        else if (row[what].disabled().getAsBoolean()) return true;
        else row[what].onClick().run();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) AbstractWidget.playButtonClickSound(mc.getSoundManager());
        return true;
    }

    /**
     * One edge's row: vanilla's creative tabs ({@link ControlLook.Control#CONTAINER_TAB}), top
     * ones on a top edge and bottom ones on a bottom edge, each with its icon and its tooltip.
     * A tab not selected stops at the frame, as vanilla's goes behind its image; a selected one
     * runs {@link #INTO} px into the frame, so it opens into the panel. A disabled one is
     * grayed. Past {@link #fits} tabs the row pages, a tab at a time, with vanilla's page
     * arrows at its ends. Drawn from the screen's coordinates, not the panel's.
     */
    static final class Strip implements PanelElement {
        private final Edge edge;

        Strip(Edge edge) {
            this.edge = edge;
        }

        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : line(g, edge).width(); }
        @Override public int getHeight() { return RegionLayout.TAB_ROW + INTO; }
        @Override public boolean isInteractive() { return true; }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = ContainerRegions.state.geometry;
            Tab[] row = rows[edge.ordinal()];
            if (g == null || row.length == 0) return;
            GuiGraphicsExtractor graphics = ctx.graphics();
            Line l = line(g, edge);
            Fit f = fit(l.width(), row.length, FIRST[edge.ordinal()]);
            int over = ctx.hasMouseInput() ? hit(l, row.length, f, ctx.mouseX(), ctx.mouseY()) : NONE;
            for (int i = f.first(); i < f.first() + f.shown(); i++) {
                Tab t = row[i];
                int x = tabX(l.x(), f, i);
                boolean selected = t.selected().getAsBoolean();
                Identifier sprite = ControlLook.Control.containerTab(!l.top(), selected, !f.paged() && i == 0);
                tab(graphics, sprite, x, l, selected, 0xFFFFFFFF);
                if (t.item() != null) graphics.item(t.item().get(), x + ICON_X, l.iconY());
                else if (t.sprite() != null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, t.sprite(), x + ICON_X, l.iconY(), 16, 16);
                // Grayed: the tab's own shape again in translucent black, over the tab and its icon alike.
                if (t.disabled().getAsBoolean()) tab(graphics, sprite, x, l, selected, GRAYED);
            }
            if (f.paged()) {
                int ay = l.rowTop() + (RegionLayout.TAB_ROW - ARROW_HEIGHT) / 2;
                if (f.first() > 0) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(false, over == BACK),
                            l.x(), ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
                if (f.first() + f.shown() < row.length) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(true, over == FORWARD),
                            l.x() + l.width() - ARROW_WIDTH, ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
            }
            if (over == BACK || over == FORWARD || (over >= 0 && !row[over].disabled().getAsBoolean()
                    && !row[over].selected().getAsBoolean())) graphics.requestCursor(CursorTypes.POINTING_HAND);
            if (over >= 0 && row[over].tooltip() != null) {
                Component text = row[over].tooltip().get();
                if (text != null) MKTooltip.queue(graphics, text, ctx.mouseX(), ctx.mouseY());
            }
        }

        /**
         * One tab at {@code x} on line {@code l}, tinted {@code color}: vanilla's sprite with
         * {@link #CUT} rows of its plain middle left out, so it stands {@link RegionLayout#TAB_ROW}
         * out from the frame. Selected, its {@link #INTO} rows at the frame end go over the
         * frame's edge, open into it; not selected, they are left out, so it stops at the edge.
         */
        private static void tab(GuiGraphicsExtractor graphics, Identifier sprite, int x, Line l, boolean selected, int color) {
            if (l.top()) {
                // Rows 0 to CUT_FROM at the row's top, then the rest after the cut, down to the frame or into it.
                int y = l.rowTop(), end = SPRITE_HEIGHT - (selected ? 0 : INTO);
                part(graphics, sprite, x, y, 0, CUT_FROM, color);
                part(graphics, sprite, x, y + CUT_FROM, CUT_FROM + CUT, end - CUT_FROM - CUT, color);
            } else {
                // From the frame (selected: INTO rows up, over its edge) down, the cut in the plain middle.
                int from = selected ? 0 : INTO, cut = selected ? CUT : BOTTOM_CUT, mid = SPRITE_HEIGHT / 2;
                int y = l.y() - (selected ? INTO : 0);
                part(graphics, sprite, x, y, from, mid - from, color);
                part(graphics, sprite, x, y + mid - from, mid + cut, SPRITE_HEIGHT - mid - cut, color);
            }
        }

        /** Sprite rows {@code v} to {@code v + h} at {@code (x, y)}. */
        private static void part(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int v, int h, int color) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, TAB_WIDTH, SPRITE_HEIGHT, 0, v, x, y, TAB_WIDTH, h, color);
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            return button == Click.LEFT && click(edge, in.mouseX(), in.mouseY());
        }
    }
}
