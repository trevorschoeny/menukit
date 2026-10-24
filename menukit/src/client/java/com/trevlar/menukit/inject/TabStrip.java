package com.trevlar.menukit.inject;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.inject.ContainerTabRows.View;
import com.trevlar.menukit.inject.TabRegistry.Tab;
import com.trevlar.menukit.inject.TabRowLayout.Fit;
import com.trevlar.menukit.inject.TabRowLayout.Line;
import com.trevlar.menukit.inject.TabRowLayout.Placed;
import com.trevlar.menukit.inject.TabRowLayout.Shown;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The container screen's tab rows drawn (7.4.0): one panel per edge on the region's panels, and
 * on the outer two edges one on a screen's own frame, each holding one {@link Strip} that draws
 * its edge's row as {@link ContainerTabRows} chose it and {@link TabRowLayout} placed it, and
 * hands its clicks back. Render thread only.
 */
@ApiStatus.Internal
public final class TabStrip {

    private TabStrip() {}

    /** Vanilla's tab sprite's size; the {@link #INTO} rows at its frame end are where a selected tab opens into the frame. */
    static final int SPRITE_WIDTH = 26, SPRITE_HEIGHT = 32, INTO = 4;
    /** The sprite's left and right edges, kept whole as a tab widens or narrows; the plain columns between repeat or are cut. */
    static final int EDGE_COLUMNS = 4;
    /** A tab's rows outside its fill, at the tip: the sprite's 2 empty rows, its outline and its 2-row highlight. */
    static final int TIP = 5;
    /** A disabled tab: its own shape drawn again over it in black at this alpha, so it and its icon read 0.3 as bright. */
    static final int GRAYED = 0xB3000000;

    private static boolean panelsBuilt;

    /**
     * The icons that have thrown: never drawn again, since a drawing cut off half way can leave
     * the pose it pushed behind it, and the tab draws empty from then on. Logged once.
     */
    private static final Set<ContainerTabs.Icon> BROKEN = Collections.newSetFromMap(new WeakHashMap<>());

    /** The rows' panels, made once, with the first tab or keyed list. */
    static synchronized void buildPanels() {
        if (panelsBuilt) return;
        panelsBuilt = true;
        for (Edge edge : Edge.values()) {
            String id = "menukit:container_tabs_" + edge.name().toLowerCase(Locale.ROOT);
            // One background panel per edge, drawn over the region's two so a selected tab opens
            // into its frame, on the screens the region is on.
            Panel row = Panel.builder(id)
                    .style(PanelStyle.NONE).background()
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> ContainerTabRows.origin(edge))
                            .priority(PanelPosition.DEFAULT_PRIORITY + 1)))
                    .add(new Strip(edge))
                    .build()
                    .visibleWhen(() -> ContainerRegions.state.geometry != null && ContainerRegions.state.applied != null
                            && ContainerTabRows.hasRow(edge));
            new ScreenPanelAdapter(row, 0).onMatching(ScreenMatcher.where(RegionDeclarations::targets));
            if (edge != Edge.CONTAINERS_TOP && edge != Edge.INVENTORY_BOTTOM) continue;
            // And on a screen's own frame, where the region isn't up: an ordinary panel, drawn
            // after the screen's image, so a selected tab opens into it.
            Panel onFrame = Panel.builder(id + "_on_frame")
                    .style(PanelStyle.NONE)
                    .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> ContainerTabRows.origin(edge))
                            .priority(PanelPosition.DEFAULT_PRIORITY + 1)))
                    .add(new Strip(edge))
                    .build()
                    .visibleWhen(() -> ContainerTabRows.onBareFrame() && ContainerTabRows.hasRow(edge));
            new ScreenPanelAdapter(onFrame, 0).onMatching(ScreenMatcher.where(ContainerTabRows::carries));
        }
    }

    /**
     * One edge's row: vanilla's creative tabs ({@link ControlLook.Control#CONTAINER_TAB}), top
     * ones on a top edge and bottom ones on a bottom edge, each with its icon and its tooltip,
     * at its own size. A tab not selected stops at the frame, as vanilla's goes behind its
     * image; a selected one runs {@link #INTO} px into the frame, so it opens into the panel. A
     * disabled one is grayed. Past {@link TabRowLayout#fit what fits} the row pages, a tab at a
     * time, with vanilla's page arrows at its ends. A group's sub-tabs draw under the tabs, so
     * they slide out from under their header, and pinned tabs over every other. Drawn from the
     * screen's coordinates, not the panel's.
     */
    static final class Strip implements PanelElement {
        private final Edge edge;

        Strip(Edge edge) {
            this.edge = edge;
        }

        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Line l = ContainerTabRows.lineNow(edge); return l == null ? 0 : l.width(); }
        @Override public int getHeight() { return ContainerTabRows.drawnHeight(edge) + INTO; }
        @Override public boolean isInteractive() { return true; }

        @Override
        public void render(RenderContext ctx) {
            View v = ContainerTabRows.view(edge);
            if (v == null) return;
            Line l = v.line();
            Shown[] row = v.row();
            Fit f = v.fit();
            GuiGraphicsExtractor graphics = ctx.graphics();
            int over = ctx.hasMouseInput()
                    ? TabRowLayout.hit(l, row.length, f, v.inset(), v.placed(), ctx.mouseX(), ctx.mouseY()) : TabRowLayout.NONE;
            // Tabs show between the arrows; sub-tabs right of their header too. The band takes
            // in a selected tab's rows into the frame, either way up, and a sliding tab taller
            // than the row.
            int left = TabRowLayout.roomLeft(l, v.inset()), right = TabRowLayout.roomRight(l, v.inset());
            int reach = ContainerTabRows.drawnHeight(edge);
            int bandTop = l.y() - reach - INTO, bandBottom = l.y() + reach + INTO;
            graphics.enableScissor(left, bandTop, right, bandBottom);
            for (int layer = 0; layer <= 2; layer++) {
                for (Placed p : v.placed()) {
                    if (TabRowLayout.layer(p) != layer) continue;
                    if (p.sub()) graphics.enableScissor(Math.max(left, p.clipLeft()), bandTop, right, bandBottom);
                    draw(graphics, p, l, !f.paged() && p.index() == 0);
                    if (p.sub()) graphics.disableScissor();
                }
            }
            graphics.disableScissor();
            if (f.paged()) {
                int ay = l.rowTop() + (l.height() - TabRowLayout.ARROW_HEIGHT) / 2;
                if (f.first() > 0) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(false, over == TabRowLayout.BACK),
                            l.x(), ay, TabRowLayout.ARROW_WIDTH, TabRowLayout.ARROW_HEIGHT);
                }
                if (f.first() + f.shown() < row.length) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(true, over == TabRowLayout.FORWARD),
                            l.x() + l.width() - TabRowLayout.ARROW_WIDTH, ay, TabRowLayout.ARROW_WIDTH, TabRowLayout.ARROW_HEIGHT);
                }
            }
            if (over == TabRowLayout.BACK || over == TabRowLayout.FORWARD
                    || (over >= 0 && !row[over].disabled() && !row[over].selected())) graphics.requestCursor(CursorTypes.POINTING_HAND);
            if (over >= 0 && row[over].tab().tooltip() != null) {
                Tab t = row[over].tab();
                Component text = Guard.get(t.tooltip(), "tooltip", t.id());
                if (text != null) MKTooltip.queue(graphics, text, ctx.mouseX(), ctx.mouseY());
            }
        }

        /** Tab {@code p} on line {@code l}: its sprite, its icon, and grayed over both when disabled. */
        private static void draw(GuiGraphicsExtractor graphics, Placed p, Line l, boolean first) {
            Tab t = p.tab();
            boolean selected = p.shown().selected();
            Identifier sprite = ControlLook.Control.containerTab(!l.top(), selected, first);
            tab(graphics, sprite, p.x(), l, t.width(), t.height(), selected, 0xFFFFFFFF);
            icon(graphics, t, p.x(), l);
            // Grayed: the tab's own shape again in translucent black, over the tab and its icon alike.
            if (p.shown().disabled()) tab(graphics, sprite, p.x(), l, t.width(), t.height(), selected, GRAYED);
        }

        /**
         * Tab {@code t}'s icon box, centered across it and centered in its fill, between the frame
         * and the tip's {@link #TIP} rows (on a full-size tab that is 2 px clear of the frame, as
         * vanilla's is): 16 px square on a full-size tab, smaller in proportion on a smaller
         * one (an even size, so it centers on the pixel grid), and the mod's drawing in it. A
         * drawing that throws is logged once and left out from then on.
         */
        private static void icon(GuiGraphicsExtractor graphics, Tab t, int x, Line l) {
            ContainerTabs.Icon icon = t.icon();
            if (icon == null || BROKEN.contains(icon)) return;
            float scale = Math.min(1f, Math.min((float) t.width() / ContainerTabs.TAB_WIDTH, (float) t.height() / ContainerTabs.TAB_HEIGHT));
            int size = 2 * Math.round(8f * scale);
            int clear = Math.max(0, (t.height() - TIP - size) / 2);
            int ix = x + (t.width() - size) / 2, iy = l.top() ? l.y() - clear - size : l.y() + clear;
            try {
                icon.draw(graphics, ix, iy, size, size);
            } catch (RuntimeException e) {
                BROKEN.add(icon);
                Guard.failed(icon, "icon", t.id(), e);
            }
        }

        /**
         * One tab at {@code x} on line {@code l}, {@code width} across and {@code height} out from
         * the frame, tinted {@code color}: vanilla's sprite, its corners and edges whole and its
         * plain middle repeated or cut to the size. Selected, its {@link #INTO} rows at the frame
         * end go over the frame's edge, open into it; not selected, they are left out, so it stops
         * at the edge. Vanilla's bottom tab not selected ends 4 px short of its sprite where a
         * top one ends 2 px short, so it draws 2 px longer and the two edges match.
         */
        private static void tab(GuiGraphicsExtractor graphics, Identifier sprite, int x, Line l, int width, int height,
                                boolean selected, int color) {
            // The sprite rows drawn, the rows kept whole at each end, and where they go.
            int from, to, head, tail, y, length;
            if (l.top()) {
                // The tip at the top, its rounded corners in the first 6 rows; selected, the INTO rows open into the frame.
                from = 0; to = SPRITE_HEIGHT - (selected ? 0 : INTO); head = 6; tail = selected ? INTO : 0;
                y = l.y() - height; length = height + (selected ? INTO : 0);
            } else if (selected) {
                // The frame end first, INTO rows up over the edge, the tip's 3 rows last.
                from = 0; to = SPRITE_HEIGHT; head = INTO; tail = 3;
                y = l.y() - INTO; length = height + INTO;
            } else {
                // From under the frame's edge; the tip's 3 rows and the 4 empty ones after them last.
                from = INTO; to = SPRITE_HEIGHT; head = 0; tail = 7;
                y = l.y(); length = height + 2;
            }
            for (int[] v : TabRowLayout.runs(from, to, head, tail, length)) {
                if (v[2] <= 0) continue;
                for (int[] u : TabRowLayout.runs(0, SPRITE_WIDTH, EDGE_COLUMNS, EDGE_COLUMNS, width)) {
                    if (u[2] <= 0) continue;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SPRITE_WIDTH, SPRITE_HEIGHT, u[0], v[0],
                            x + u[1], y + v[1], u[2], v[2], color);
                }
            }
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            return button == Click.LEFT && ContainerTabRows.click(edge, in.mouseX(), in.mouseY());
        }
    }
}
