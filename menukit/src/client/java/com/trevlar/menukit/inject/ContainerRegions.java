package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * The container region's machinery (7.4.0; the public face is
 * {@code api.panel.ContainerRegion}). One panel, declared once, holds every section any
 * mod declares; on a container screen it sits from just above the player's inventory up,
 * and its one {@link ScrollContainer} scrolls them together.
 *
 * <h3>Built from what is there</h3>
 * <ul>
 *   <li><b>The panel</b> is an ordinary container panel ({@link ScreenPanelAdapter}): opaque,
 *       so it claims its rectangle and whatever it covers (vanilla's own grid frame, the
 *       screen's title) is hidden and inert; its slots draw right after it
 *       ({@link PresentedSlots}). Its frame is open at the bottom and meets the inventory
 *       part of vanilla's own texture, whose top edge is where content goes behind.</li>
 *   <li><b>The scroll area</b> is a {@link ScrollContainer} built {@code wheelLast()}: its
 *       children still get the wheel, but it never scrolls itself on one. This class
 *       scrolls it, after everyone else ({@link #wheel}).</li>
 *   <li><b>Slots</b> clip at its edges ({@code SlotClip}), and a row out of view is not
 *       rendered at any depth ({@code ChildDispatch.render}), so its library parks it.</li>
 * </ul>
 *
 * <h3>The wheel, last</h3>
 * Three places, one rule: the region scrolls on a turn nobody else used.
 * <ol>
 *   <li>{@code MouseHandler} HEAD ({@link ScreenPanelRegistry#dispatchCoveredScroll}): over a
 *       panel that claims the point, that panel's elements get the turn. Over the region
 *       itself an unused turn is not eaten but passed on, so vanilla and every other mod see
 *       it next; over any other panel it is eaten (what the panel covers stays inert) and
 *       offered to the region straight away.</li>
 *   <li>The screen's own {@code mouseScrolled}, and every other mod's handler on it.</li>
 *   <li>Fabric's {@code afterMouseScroll}, in a phase ordered after the default one, so
 *       after every listener registered in it, whenever registered: an unconsumed turn
 *       scrolls the region, wherever the mouse is.</li>
 * </ol>
 * A mod that uses the turn in step 2 or in its own {@code afterMouseScroll} listener (Mouse
 * Tweaks over any slot) answers "used", so the region does not move.
 *
 * <p>Render thread only. One container screen is open at a time; its state is reset when
 * another opens (a resize re-initialises the same screen and keeps it).
 */
@ApiStatus.Internal
public final class ContainerRegions {

    private ContainerRegions() {}

    // Layout, in GUI pixels, from the region's own top-left.

    /** Room left above the region at the screen's top. */
    public static final int TOP_MARGIN = 4;
    /** From the region's left edge to its content: vanilla's grid frame (slot x 8, frame x 7). */
    public static final int INSET_X = 7;
    /** From the region's top edge to the viewport's: content scrolled up passes under the border here. */
    public static final int VIEW_TOP = 4;
    /** The first section's top inside the content, so its title sits where vanilla's does (6 down). */
    public static final int CONTENT_TOP = 2;
    /** Between sections: vanilla's room from a grid to the "Inventory" label below it. */
    public static final int GAP = 3;
    /** A section's width before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = 9 * 18;
    /** The scrollbar lane at the right: the gutter and the track. */
    public static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
    /** A slot's cell. */
    private static final int CELL = 18;

    // Carrying an item to an edge.

    /** Inside the viewport, how far from an edge the scroll starts, slow, reaching full speed at the edge. */
    public static final int EDGE_IN = 8;
    /** Past an edge, how far it keeps scrolling at full speed (below: short of the inventory's first row). */
    public static final int EDGE_PAST = 12;
    /** Full speed, in pixels a second. */
    public static final double EDGE_SPEED = 120;

    /** The panel's id. */
    public static final String PANEL_ID = "menukit:container_region";

    /** The after-scroll phase the region's listener runs in: after the default phase. */
    private static final Identifier LAST = Identifier.fromNamespaceAndPath("menukit", "container_region_last");

    // Declarations

    /** One declared section (ContainerRegion.section). */
    public record Section(String id, PanelElement content, ScreenMatcher matcher, int order, BooleanSupplier visibleWhen) {}

    /** A section as placed: what it was declared as, and the element that holds it in the scroll area. */
    private record Placed(Section section, AbstractPanelElement holder) {}

    private static final List<Placed> SECTIONS = new CopyOnWriteArrayList<>();
    private static @Nullable Panel panel;
    private static @Nullable ScreenPanelAdapter adapter;
    /** The column the sections stack in, inside the scroll area. */
    private static @Nullable Flow column;

    /**
     * {@code ContainerRegion.Builder.declare}: adds a section and builds the region's panel
     * again around every section so far. Declarations are open (checked by the caller), so
     * the previous adapter can be withdrawn; no screen is open yet.
     */
    public static synchronized void declare(Section section) {
        for (Placed p : SECTIONS) {
            if (p.section().id().equals(section.id())) {
                throw new IllegalStateException("ContainerRegion section '" + section.id() + "' is already declared");
            }
        }
        AbstractPanelElement holder = Flow.builder().vertical().gap(0, 0).add(section.content())
                .visibleWhen(() -> shownOnCurrentScreen(section)).build();
        SECTIONS.add(new Placed(section, holder));
        SECTIONS.sort(Comparator.comparingInt((Placed p) -> p.section().order()).thenComparing(p -> p.section().id()));

        if (adapter != null) adapter.unregister();
        List<PanelElement> holders = new ArrayList<>();
        for (Placed p : SECTIONS) holders.add(p.holder());
        // One column stacks the shown sections, GAP apart; a hidden one takes no room.
        column = Flow.builder().vertical().gap(0, GAP).addAll(holders).at(0, CONTENT_TOP).build();
        ScrollContainer scroll = ScrollContainer.builder().at(INSET_X, VIEW_TOP).size(CONTENT_WIDTH + LANE, 1)
                .fillHeight().wheelLast()
                .state(() -> state.scroll, v -> state.scroll = v)
                .content(List.of(column)).build();
        panel = Panel.builder(PANEL_ID).style(PanelStyle.NONE)
                .position(PanelPosition.pixel(ContainerRegions::origin))
                .add(new Backdrop()).add(scroll)
                .build()
                .visibleWhen(() -> state.geometry != null);
        adapter = new ScreenPanelAdapter(panel, 0).onMatching(ScreenMatcher.where(ContainerRegions::targets));
    }

    /** Whether any section is declared for {@code screen}: the region's panel is put on it. */
    private static boolean targets(AbstractContainerScreen<?> screen) {
        for (Placed p : SECTIONS) if (p.section().matcher().matches(screen)) return true;
        return false;
    }

    /** A section shows on the region's screen when it targets it and its own condition holds. */
    private static boolean shownOnCurrentScreen(Section s) {
        return state.screen.get() instanceof AbstractContainerScreen<?> acs
                && s.matcher().matches(acs) && s.visibleWhen().getAsBoolean();
    }

    /** The region's panel, or {@code null} with no section declared. */
    public static @Nullable Panel panel() {
        return panel;
    }

    // Geometry: pure, so the checks can ask it.

    /**
     * Where the region is on a screen this frame.
     *
     * @param x          the region's left edge (the screen's {@code leftPos})
     * @param top        its top edge
     * @param width      its width: the screen's image width, and the scrollbar lane while it scrolls
     * @param height     its height, from {@code top} to {@code bottom}
     * @param bottom     its bottom edge: the top of the inventory part, which never moves
     * @param viewTop    the viewport's top edge
     * @param viewHeight the viewport's height, down to {@code bottom}
     * @param content    the height of everything in it
     * @param scrollable whether the content is taller than the viewport
     */
    public record Geometry(int x, int top, int width, int height, int bottom, int viewTop, int viewHeight,
                           int content, boolean scrollable) {}

    /**
     * Grow first, then scroll: as tall as its content, up to the screen's top margin; never
     * shorter than the screen's own container part ({@code bottom - topPos}), even when
     * that leaves no margin (a tiny window), in which case it scrolls.
     *
     * @param leftPos    the screen's left edge
     * @param topPos     the screen's top edge (vanilla's)
     * @param imageWidth the screen's width
     * @param bottom     the top of the inventory part
     * @param content    the content's height
     */
    public static Geometry layout(int leftPos, int topPos, int imageWidth, int bottom, int content) {
        int vanilla = bottom - topPos;
        int height = Math.max(vanilla, Math.min(VIEW_TOP + content, bottom - TOP_MARGIN));
        int viewHeight = height - VIEW_TOP;
        boolean scrollable = content > viewHeight;
        return new Geometry(leftPos, bottom - height, imageWidth + (scrollable ? LANE : 0), height, bottom,
                bottom - height + VIEW_TOP, viewHeight, content, scrollable);
    }

    /**
     * How fast carrying an item at {@code (mouseX, mouseY)} scrolls the region, in pixels a
     * second, negative upward: nothing over the region's middle, then faster nearer an edge
     * ({@link #EDGE_IN} inside it), full speed at the edge and {@link #EDGE_PAST} beyond it.
     * Below, that stops short of the inventory's first row. Nothing outside the region's
     * columns or when nothing scrolls.
     */
    public static double edgeSpeed(Geometry g, double mouseX, double mouseY) {
        if (!g.scrollable() || mouseX < g.x() || mouseX >= g.x() + g.width()) return 0;
        double intoTop = g.viewTop() + EDGE_IN - mouseY;          // > 0 near and past the top edge
        if (intoTop > 0 && intoTop <= EDGE_IN + EDGE_PAST) return -EDGE_SPEED * Math.min(1, intoTop / EDGE_IN);
        double intoBottom = mouseY - (g.bottom() - EDGE_IN);      // > 0 near and past the bottom edge
        if (intoBottom > 0 && intoBottom <= EDGE_IN + EDGE_PAST) return EDGE_SPEED * Math.min(1, intoBottom / EDGE_IN);
        return 0;
    }

    /**
     * The scroll that shows a slot whose cell starts {@code cellTop} pixels down the
     * content, whole, with the least movement: the scroll as it is when it shows already.
     */
    public static double revealScroll(double scroll, int cellTop, Geometry g) {
        return Scroller.intoView(scroll, cellTop, CELL, g.content(), g.viewHeight());
    }

    /**
     * Keeps what is on screen in place when sections above it change: the topmost section
     * that reached into the view before (and is still shown) moves the scroll by as much as
     * it moved. Tops of {@code -1} are hidden sections. At the top it stays at the top.
     */
    public static double keepInPlace(double scroll, int[] oldTop, int[] oldBottom, int[] newTop) {
        if (scroll <= 0) return scroll;
        for (int i = 0; i < oldTop.length; i++) {
            if (oldTop[i] < 0 || newTop[i] < 0 || oldBottom[i] <= scroll) continue;
            return scroll + (newTop[i] - oldTop[i]);
        }
        return scroll;
    }

    // The open screen's state

    /** The open screen's region: its scroll, this frame's geometry, what it learned. */
    private static final class State {
        WeakReference<Screen> screen = new WeakReference<>(null);
        double scroll;
        @Nullable Geometry geometry;
        /** Each section's top and bottom in the content last frame, -1 while hidden. */
        int[] tops = new int[0], bottoms = new int[0];
        /** Where each slot the region has shown sits in the content: its cell's top. */
        final Map<Slot, Integer> cellTops = new IdentityHashMap<>();
        long lastFrame = -1;
        /** A quick move landed out of view: the slot to show, and how the search for it goes. */
        @Nullable Reveal reveal;
    }

    /**
     * Showing where a quick move landed. Known (the region has shown that slot before), it
     * is one scroll. Not yet known (a section below the fold never drawn), the region pages
     * down a viewport a frame until the slot is drawn, then shows it; if no page has it, it
     * goes back to where it was.
     */
    private record Reveal(Slot slot, int page, double from) {}

    private static final State state = new State();

    /** Called once from MenuKit's client init. */
    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs) || adapter == null || !adapter.matches(acs)) return;
            if (state.screen.get() != screen) reset(screen);
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> frame(acs, mx, my));
            ScreenEvents.remove(screen).register(s -> { if (state.screen.get() == s) reset(null); });
            listenLast(ScreenMouseEvents.afterMouseScroll(screen));
        });
    }

    /**
     * The region's wheel listener on a screen's {@code afterMouseScroll}: in a phase after
     * the default one, so it hears every other listener's answer whenever that listener
     * registered, and scrolls only on a turn none of them (nor the screen) used.
     */
    public static void listenLast(Event<ScreenMouseEvents.AfterMouseScroll> after) {
        after.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
        after.register(LAST, (s, x, y, hAmount, vAmount, consumed) -> consumed || wheel(s, vAmount));
    }

    /** For MenuKit's checks: the region as if {@code screen} had just opened. */
    public static void openForChecks(@Nullable Screen screen) {
        reset(screen);
    }

    private static void reset(@Nullable Screen screen) {
        state.screen = new WeakReference<>(screen);
        state.scroll = 0;
        state.geometry = null;
        state.tops = new int[0];
        state.bottoms = new int[0];
        state.cellTops.clear();
        state.lastFrame = -1;
        state.reveal = null;
    }

    /** The region on {@code screen} this frame, or {@code null} when it is not there (no section shown). */
    public static @Nullable Geometry geometry(Screen screen) {
        return state.screen.get() == screen ? state.geometry : null;
    }

    /** The open region's scroll, in pixels. */
    public static double scroll() {
        return state.scroll;
    }

    /** Sets the open region's scroll (for checks and a mod's own buttons); clamped next frame. */
    public static void setScroll(double pixels) {
        state.scroll = pixels;
    }

    private static @Nullable ScreenOrigin origin() {
        Geometry g = state.geometry;
        return g == null ? null : new ScreenOrigin(g.x(), g.top());
    }

    /**
     * Before each frame of a screen the region is on: stack the shown sections, work out
     * where the region is, keep the view in place if sections above it changed, then carry
     * on a reveal and scroll for an item carried to an edge.
     */
    static void frame(AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        if (panel == null || state.screen.get() != screen) return;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        step(acc.mk$getLeftPos(), acc.mk$getTopPos(), acc.mk$getImageWidth(),
                acc.mk$getTopPos() + acc.mk$getInventoryLabelY() - 3,
                !screen.getMenu().getCarried().isEmpty(), mouseX, mouseY, Util.getMillis());
    }

    /**
     * One frame of the open region, from the screen's numbers: {@code bottom} is the top of
     * its inventory part (3 px above vanilla's "Inventory" label, where its texture's
     * inventory part starts), {@code carrying} whether an item is on the cursor.
     */
    public static void step(int leftPos, int topPos, int imageWidth, int bottom, boolean carrying,
                            int mouseX, int mouseY, long now) {
        if (panel == null) return;

        // Where each shown section sits in the content (the column stacks them).
        int n = SECTIONS.size();
        int[] tops = new int[n], bottoms = new int[n];
        int columnHeight = column.getHeight(), shown = 0;   // lays the column out first
        for (int i = 0; i < n; i++) {
            AbstractPanelElement holder = SECTIONS.get(i).holder();
            if (!holder.isVisible()) { tops[i] = bottoms[i] = -1; continue; }
            shown++;
            tops[i] = CONTENT_TOP + holder.getChildY();
            bottoms[i] = tops[i] + holder.getHeight();
        }
        if (shown == 0) { state.geometry = null; return; }
        int y = CONTENT_TOP + columnHeight;

        if (state.tops.length == n) state.scroll = keepInPlace(state.scroll, state.tops, state.bottoms, tops);
        state.tops = tops;
        state.bottoms = bottoms;

        Geometry g = layout(leftPos, topPos, imageWidth, bottom, y);
        state.geometry = g;
        if (panel.getPinnedWidth() != g.width() || panel.getPinnedHeight() != g.height()) panel.size(g.width(), g.height());

        double seconds = state.lastFrame < 0 ? 0 : Math.min(0.1, (now - state.lastFrame) / 1000.0);
        state.lastFrame = now;

        stepReveal(g);
        if (carrying) state.scroll += edgeSpeed(g, mouseX, mouseY) * seconds;
        state.scroll = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
    }

    /**
     * Right after the place pass ({@link ContainerScreenLayers#frameStart}): every slot the
     * region placed this frame, and where its cell sits in the content, is noted, so a
     * quick move into it later can be shown at once. Positions and scroll are this frame's.
     */
    static void placed(AbstractContainerScreen<?> screen) {
        Geometry g = state.geometry;
        if (panel == null || g == null || state.screen.get() != screen) return;
        int topPos = ((AbstractContainerScreenAccessor) screen).mk$getTopPos();
        for (Slot slot : PresentedSlots.of(panel)) state.cellTops.put(slot, cellTop(topPos, slot.y, g, state.scroll));
    }

    /** Where a slot placed at {@code slotY} (vanilla's, from {@code topPos}) sits in the content: its cell's top. */
    public static int cellTop(int topPos, int slotY, Geometry g, double scroll) {
        return (int) Math.round(topPos + slotY - 1 - g.viewTop() + scroll);
    }

    // The wheel

    /**
     * The region's turn of the wheel, last: scrolls it {@code Scroller}'s step a notch.
     *
     * @return whether it moved (the turn is used)
     */
    public static boolean wheel(Screen screen, double scrollY) {
        Geometry g = geometry(screen);
        if (g == null || !g.scrollable() || ScreenPanelRegistry.modalUpOn(screen)) return false;
        double before = Scroller.clamp(state.scroll, g.content(), g.viewHeight());
        state.scroll = Scroller.wheel(before, scrollY, g.content(), g.viewHeight());
        return state.scroll != before;
    }

    /** Whether {@code p} is the region's panel. */
    static boolean isRegion(Panel p) {
        return p == panel;
    }

    // Quick moves

    /** Whether a region is up on the open screen: the quick-move hook takes its snapshot only then. */
    public static boolean active() {
        Screen s = state.screen.get();
        return s != null && state.geometry != null && Minecraft.getInstance().gui.screen() == s;
    }

    /** Each slot's stack before a click (copies), for {@link #quickMoved}. */
    public static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        List<ItemStack> out = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) out.add(slot.getItem().copy());
        return out;
    }

    /**
     * After a quick move the client has predicted: the first slot that gained items, if it
     * is the region's and not in view, is shown. A move into the inventory gains its slots,
     * which are not the region's, so nothing scrolls.
     */
    public static void quickMoved(AbstractContainerMenu menu, List<ItemStack> before) {
        Screen screen = state.screen.get();
        Geometry g = state.geometry;
        if (!(screen instanceof AbstractContainerScreen<?> acs) || g == null || acs.getMenu() != menu) return;
        for (int i = 0; i < menu.slots.size() && i < before.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack was = before.get(i), now = slot.getItem();
            boolean gained = !now.isEmpty() && (was.isEmpty() || !ItemStack.isSameItemSameComponents(was, now)
                    || now.getCount() > was.getCount());
            if (!gained) continue;
            if (!regionsOwn(acs, slot)) return;         // landed in the inventory (or elsewhere in view)
            Integer cell = state.cellTops.get(slot);
            if (cell != null && revealScroll(state.scroll, cell, g) == state.scroll && PresentedSlots.lastPanel(slot) == panel
                    && onScreen(acs, slot)) return;     // landed in view
            state.reveal = new Reveal(slot, cell != null ? -1 : 0, state.scroll);
            return;
        }
    }

    /**
     * Whether a slot is the region's: one it showed, or one no panel ever showed that is not
     * on screen either (a section below the fold, never drawn; its library keeps it parked).
     * The player's inventory is on screen where vanilla put it, so it is never the region's.
     */
    private static boolean regionsOwn(AbstractContainerScreen<?> screen, Slot slot) {
        Panel last = PresentedSlots.lastPanel(slot);
        if (last != null) return last == panel;
        return !onScreen(screen, slot);
    }

    private static boolean onScreen(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int x = acc.mk$getLeftPos() + slot.x, y = acc.mk$getTopPos() + slot.y;
        return x > -CELL && y > -CELL && x < screen.width && y < screen.height;
    }

    /** One frame of a reveal: shown when its slot's place is known, else the next page. */
    private static void stepReveal(Geometry g) {
        Reveal r = state.reveal;
        if (r == null) return;
        Integer cell = state.cellTops.get(r.slot());
        if (cell != null) {
            state.scroll = revealScroll(state.scroll, cell, g);
            state.reveal = null;
            return;
        }
        int pageTop = r.page() * Math.max(1, g.viewHeight());
        if (r.page() < 0 || pageTop >= g.content()) {   // looked everywhere: not the region's after all
            state.scroll = r.from();
            state.reveal = null;
            return;
        }
        state.scroll = pageTop;
        state.reveal = new Reveal(r.slot(), r.page() + 1, r.from());
    }

    // The frame

    /**
     * The region's frame, behind every section: vanilla's raised panel, open at the bottom,
     * where it meets the inventory part of the screen's own texture. While it scrolls it is
     * wider by the scrollbar lane, and the part past the screen's edge closes on its own.
     */
    private static final class Backdrop implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = state.geometry; return g == null ? 0 : g.width(); }
        @Override public int getHeight() { Geometry g = state.geometry; return g == null ? 0 : g.height(); }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = state.geometry;
            if (g == null) return;
            GuiGraphicsExtractor gr = ctx.graphics();
            int x = ctx.originX(), y = ctx.originY();
            int imageRight = x + g.width() - (g.scrollable() ? LANE : 0);
            // Drawn taller than the region and cut at its bottom: no bottom edge where the
            // inventory part continues it.
            gr.enableScissor(x, y, x + g.width(), y + g.height());
            PanelRendering.renderPanel(gr, x, y, g.width(), g.height() + 8, PanelStyle.RAISED);
            gr.disableScissor();
            if (g.scrollable()) {
                // The lane hangs past the screen's right edge: give it a bottom edge of its own.
                gr.enableScissor(imageRight, y + g.height() - 4, x + g.width(), y + g.height());
                PanelRendering.renderPanel(gr, imageRight - 9, y, x + g.width() - imageRight + 9, g.height(),
                        PanelStyle.RAISED);
                gr.disableScissor();
            }
        }
    }
}
