package com.trevlar.menukit.inject;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.inject.ContainerRegions.Declared;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.inject.RegionLayout.Shape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * The container list's tabs mode (the public face is {@code ContainerRegion.tabs}): the list
 * shows one container at a time, and a row of tabs on its top edge, one per section that
 * could show, picks which. {@link ContainerRegions} is the region itself; this class is
 * which screens are in tabs mode, which section is picked, the hidden sections' slots, and
 * the tab row, drawn and hit. How the list then stands ({@link Shape}: no gap, one frame,
 * shrinking to the shown section, the tab row counted) is {@link RegionLayout}'s.
 *
 * <h3>The pick</h3>
 * Kept for the open screen, across a resize, and reset when another screen opens: it starts
 * on the screen's own container (the first section with no source), a click on a tab (an item
 * held or not) or {@code ContainerRegion.select} picks another, and a shift-click into a
 * container not shown picks that one. A pick that is not there yet (a box whose section shows
 * a frame later) waits for it; once shown, a pick whose section hides falls back to the
 * screen's own container, and stays there.
 *
 * <h3>Hidden sections' slots are off</h3>
 * Every section not shown has its slots inactive on the client ({@code Slot.isActive}), so
 * nothing draws, hovers or takes a click there; a slot library's element leaves out a slot the
 * menu turned off. A slot is known to a section once the section has shown it; a container's
 * own slot (not the player's, not a created one) no section has shown yet is the screen's own
 * container's. Only the client's open menu is touched, and only in tabs mode.
 *
 * <p>Render thread only, but {@link #switchedOff} is read from any thread (the integrated
 * server asks its own slots, which are never off).
 */
@ApiStatus.Internal
public final class RegionTabs {

    private RegionTabs() {}

    private static final Logger LOG = LoggerFactory.getLogger("MenuKit/ContainerRegion");

    // The tab row's measures, from vanilla's creative inventory.

    /** A tab's width, and its place in the row: vanilla's 26 px tab, a pixel apart. */
    public static final int TAB_WIDTH = 26, STEP = 27;
    /** Vanilla's tab sprite's height; its last {@link #INTO} rows are where a picked tab opens into the frame. */
    static final int SPRITE_HEIGHT = 32, INTO = 4;
    /**
     * The rows left out of the middle of the sprite, so the tab stands {@link RegionLayout#TAB_ROW}
     * above the frame rather than vanilla's 28: rows {@link #CUT_FROM} on, plain side and fill.
     */
    static final int CUT = SPRITE_HEIGHT - INTO - RegionLayout.TAB_ROW, CUT_FROM = 12;
    /**
     * The first row of an unpicked tab's fill: vanilla's unpicked sprite starts two clear rows
     * down, then three of border.
     */
    static final int UNPICKED_FILL = 5;
    /**
     * The item icon, full size, centred across the tab and in an unpicked tab's fill, down to the
     * frame; picked tabs draw it on the same spot, so the icon stays put as the pick moves.
     */
    static final int ICON_X = (TAB_WIDTH - 16) / 2, ICON_Y = (UNPICKED_FILL + RegionLayout.TAB_ROW - 16) / 2;
    /** The paging arrows: vanilla's recipe book page arrows, at the row's two ends. */
    public static final int ARROW_WIDTH = 12, ARROW_HEIGHT = 17;
    /** {@link #hit} answers: nothing, the back arrow, the forward arrow (a tab is its place in the row, 0 up). */
    public static final int NONE = -1, BACK = -2, FORWARD = -3;

    // Which screens are in tabs mode

    /** One declaration of a screen's mode: tabs or the list, for a game mode, on the screens a matcher takes. */
    private record Mode(boolean tabs, ModeScope scope, ScreenMatcher matcher) {
        boolean on(AbstractContainerScreen<?> screen) {
            return scope.covers(PanelHost.currentMode()) && matcher.matches(screen);
        }
    }

    private static final List<Mode> MODES = new CopyOnWriteArrayList<>();
    /** The screen classes a clash was logged for: once each. */
    private static final Set<Class<?>> CLASHED = ConcurrentHashMap.newKeySet();

    /** {@code ContainerRegion.tabs} or {@code list}: the screens {@code matcher} takes show their list that way in {@code scope}. */
    public static void declare(boolean tabs, ModeScope scope, ScreenMatcher matcher) {
        MODES.add(new Mode(tabs, Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(matcher, "matcher")));
    }

    /**
     * Whether {@code screen} shows its list as tabs now: a tabs declaration takes it in the
     * player's mode. Where a list declaration takes it too, tabs win (a mod asking for tabs has
     * many containers to show) and it is logged once for the screen's class.
     */
    static boolean on(AbstractContainerScreen<?> screen) {
        boolean tabs = false, list = false;
        for (Mode m : MODES) {
            if (!m.on(screen)) continue;
            if (m.tabs()) tabs = true;
            else list = true;
        }
        if (tabs && list && CLASHED.add(screen.getClass())) {
            LOG.info("{}: one mod asks for the container list as tabs and another as a list; it shows as tabs",
                    screen.getClass().getSimpleName());
        }
        return tabs;
    }

    // The open screen's tabs

    /**
     * This frame's tab row: the sections that could show, in stacking order (indexes into the
     * region's sections, and their ids), which of them is the screen's own container
     * ({@code -1}: none), and which is shown.
     */
    record Row(int[] sections, String[] ids, int own, int shown) {

        int count() { return sections.length; }

        /** The section shown, its id. */
        String shownId() { return ids[shown]; }
    }

    /** This frame's row, or {@code null} in list mode. */
    private static @Nullable Row row;
    /** The pick asked for (by a click, {@code select}, a shift-click), by id; {@code null}: the screen's own. */
    private static @Nullable String wanted;
    /** Whether {@link #wanted} has shown since it was asked for: then its section hiding drops it. */
    private static boolean wantedShown;
    /** The first tab in view when they don't all fit, and the pick last brought into view. */
    private static int first;
    private static @Nullable String inView;
    /** The section each slot was shown in, by section id: what a section's slots are. Weak, keyed by the slot object. */
    private static final Map<Slot, String> OWNERS = new WeakHashMap<>();
    /** The block the open screen was opened on, as an item: the screen's own section's icon. */
    private static ItemStack screenIcon = ItemStack.EMPTY;

    /** The open region went (or another opened): the pick, the paging and what was learned go with it. */
    static void reset(@Nullable Screen screen) {
        row = null;
        wanted = null;
        wantedShown = false;
        first = 0;
        inView = null;
        OWNERS.clear();
        off = Set.of();
        screenIcon = screen == null ? ItemStack.EMPTY : openedIcon();
    }

    /**
     * {@code ContainerRegion.select}: shows section {@code sectionId} on the open screen, now
     * if it is there, else once it is.
     */
    public static void select(String sectionId) {
        wanted = Objects.requireNonNull(sectionId, "sectionId");
        wantedShown = false;
    }

    /**
     * This frame's row on {@code screen}, from the sections that could show (asked of each
     * with {@code available}, only in tabs mode), or {@code null} where the screen is in list mode: the pick asked for
     * while it is there, else the screen's own container's ({@link #shown}). A pick that has
     * shown and is gone is dropped.
     */
    static @Nullable Row choose(@Nullable Screen screen, List<Declared> sections, Predicate<Declared> available) {
        if (!(screen instanceof AbstractContainerScreen<?> acs) || !on(acs)) {
            row = null;
            return null;
        }
        List<String> ids = new ArrayList<>();
        List<Integer> at = new ArrayList<>();
        String own = null;
        for (int i = 0; i < sections.size(); i++) {
            Declared s = sections.get(i);
            if (!available.test(s)) continue;
            ids.add(s.id());
            at.add(i);
            if (own == null && s.source() == null) own = s.id();
        }
        if (ids.isEmpty()) {
            row = null;
            return null;
        }
        if (wanted != null && ids.contains(wanted)) wantedShown = true;
        else if (wantedShown) {
            wanted = null;
            wantedShown = false;
        }
        int shown = shown(ids, wanted, own);
        int[] idx = at.stream().mapToInt(Integer::intValue).toArray();
        row = new Row(idx, ids.toArray(String[]::new), own == null ? -1 : ids.indexOf(own), shown);
        return row;
    }

    /**
     * Which of the {@code available} sections (by id, in stacking order) the row shows, pure:
     * {@code wanted} while it is there, else the screen's own container's ({@code own}), else
     * the first.
     */
    @VisibleForTesting
    public static int shown(List<String> available, @Nullable String wanted, @Nullable String own) {
        int w = wanted == null ? -1 : available.indexOf(wanted);
        if (w >= 0) return w;
        int o = own == null ? -1 : available.indexOf(own);
        return Math.max(0, o);
    }

    /** How the list stands with {@code row}: list mode without one, a tab row with two sections or more. */
    static Shape shape(@Nullable Row row) {
        if (row == null) return Shape.LIST;
        return row.count() > 1 ? Shape.TAB_ROW : Shape.TABS;
    }

    /** Whether section {@code s} goes in the list's column this frame: every one in list mode, the shown one in tabs mode. */
    static boolean inColumn(Declared s) {
        Row r = row;
        return r == null || r.shownId().equals(s.id());
    }

    /** This frame's row, for the checks: the ids that have tabs and the one shown, or {@code null} in list mode. */
    @VisibleForTesting
    public static @Nullable List<String> rowForChecks() {
        Row r = row;
        if (r == null) return null;
        List<String> out = new ArrayList<>(List.of(r.ids()));
        out.add(0, r.shownId());
        return out;
    }

    // The hidden sections' slots

    /** The client's slots off this frame, an identity set swapped whole, so the server thread reads it safely. */
    private static volatile Set<Slot> off = Set.of();

    /** Whether MenuKit has {@code slot} off now: a hidden tab's, on the client's open menu. */
    public static boolean switchedOff(Slot slot) {
        Set<Slot> o = off;
        return !o.isEmpty() && o.contains(slot);
    }

    /**
     * Turns every slot back on, so this frame's question of which sections could show
     * ({@link ContainerRegions#step}) sees their content as it is: a section's slots being
     * off must not hide its tab.
     */
    static void allOn() {
        off = Set.of();
    }

    /** After the pick: every slot of {@code menuSlots} whose section is not the shown one is off. */
    static void switchOff(@Nullable List<Slot> menuSlots) {
        Row r = row;
        if (r == null || menuSlots == null) {
            off = Set.of();
            return;
        }
        Set<Slot> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menuSlots) {
            String owner = ownerOf(slot, r);
            if (owner != null && !owner.equals(r.shownId())) out.add(slot);
        }
        off = out.isEmpty() ? Set.of() : out;
    }

    /**
     * The section {@code slot} is in: the one that showed it, else, for a container's own slot
     * (not the player's, not a created one), the screen's own container's. {@code null} for the
     * player's inventory and a slot no section is known to have.
     */
    // ponytail: known by having shown, so a box's slots are its own once its tab has been picked; before that its library parks them.
    private static @Nullable String ownerOf(Slot slot, Row r) {
        if (ContainerRegions.stays(slot)) return null;
        String owner = OWNERS.get(slot);
        if (owner != null) return owner;
        return r.own() >= 0 && !SlotScreenDispatcher.fireIsCreated(slot) ? r.ids()[r.own()] : null;
    }

    /** After the background pass placed the list's slots ({@code shown}): each is the shown section's. */
    static void placed(Iterable<Slot> shown) {
        Row r = row;
        if (r == null) return;
        for (Slot slot : shown) OWNERS.put(slot, r.shownId());
    }

    /**
     * A quick move landed in {@code slot}: when it is in a container not shown, its tab is
     * picked, the tabs version of the list's scroll to it. Whether it was.
     */
    static boolean revealed(Slot slot) {
        Row r = row;
        if (r == null) return false;
        String owner = ownerOf(slot, r);
        if (owner == null || owner.equals(r.shownId()) || !List.of(r.ids()).contains(owner)) return false;
        select(owner);
        return true;
    }

    // The tab row, pure

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
     * What is at {@code (mx, my)} in the tab row standing on a frame from {@code x} to
     * {@code x + width} whose top is {@code frameTop}: a tab in view (its place in the row),
     * an arrow there is one for, or {@link #NONE}. A tab runs from the row's top to the frame.
     */
    public static int hit(int x, int width, int frameTop, int count, Fit f, double mx, double my) {
        if (my < frameTop - RegionLayout.TAB_ROW || my >= frameTop || mx < x || mx >= x + width) return NONE;
        if (f.paged()) {
            if (mx < x + ARROW_WIDTH) return f.first() > 0 ? BACK : NONE;
            if (mx >= x + width - ARROW_WIDTH) return f.first() + f.shown() < count ? FORWARD : NONE;
        }
        for (int i = f.first(); i < f.first() + f.shown(); i++) {
            int tx = tabX(x, f, i);
            if (mx >= tx && mx < tx + TAB_WIDTH) return i;
        }
        return NONE;
    }

    /** The row as it lays out on {@code g} this frame. */
    private static Fit fit(Geometry g, Row r) {
        return fit(g.width(), r.count(), first);
    }

    /** What is under {@code (mx, my)} in this frame's row on {@code g}. */
    private static int hitAt(Geometry g, Row r, double mx, double my) {
        return hit(g.x(), g.width(), g.top(), r.count(), fit(g, r), mx, my);
    }

    /** Once the list is laid out: a newly picked tab comes into view (paging leaves the pick alone). */
    static void afterLayout(Geometry g) {
        Row r = row;
        if (r == null || r.count() < 2) return;
        Fit f = fit(g, r);
        if (!r.shownId().equals(inView)) {
            inView = r.shownId();
            if (r.shown() < f.first()) first = r.shown();
            else if (r.shown() >= f.first() + f.shown()) first = r.shown() - f.shown() + 1;
            else first = f.first();
        }
    }

    /** A click on {@code what} ({@link #hit}): picks the tab, or moves the row a tab along. */
    private static void act(Row r, int what, Fit f) {
        if (what == BACK) first = Math.max(0, f.first() - 1);
        else if (what == FORWARD) first = f.first() + 1;
        else if (what >= 0 && what != r.shown()) select(r.ids()[what]);
    }

    // Each tab's icon and name

    /**
     * Section {@code s}'s icon: its {@code icon(...)}, else its source's block, else for the
     * screen's own container the block the screen was opened on.
     */
    private static ItemStack icon(Declared s) {
        if (s.icon() != null) return s.icon().get();
        ContainerRegion.Source source = s.source() == null ? ContainerRegions.state.opened : s.source().get();
        if (source != null && source.block() != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.getValue(source.block()).asItem());
            if (!stack.isEmpty()) return stack;
        }
        return s.source() == null ? screenIcon : ItemStack.EMPTY;
    }

    /**
     * The block under the crosshair as the screen opened, as an item, when it holds a
     * container: the icon of a screen's own container where the region can't tell it for sure
     * (a double chest's half is a chest, whose item is the right icon anyway).
     */
    private static ItemStack openedIcon() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return ItemStack.EMPTY;
        BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
        if (!(be instanceof Container)) return ItemStack.EMPTY;
        return new ItemStack(be.getBlockState().getBlock().asItem());
    }

    /**
     * The name tab {@code i} shows as its tooltip: its section's shown name, as the list works
     * it out from its title, name and source (a title a pack blanked takes its name), else, with
     * no title, its name or its container's.
     */
    private static @Nullable Component name(Row r, int i, List<Declared> sections) {
        Declared s = sections.get(r.sections()[i]);
        ContainerRegions.Frame f = ContainerRegions.state.frame;
        Component shown = f == null || r.sections()[i] >= f.titles().length ? null : f.titles()[r.sections()[i]];
        if (!RegionTitles.blank(shown)) return shown;
        if (s.name() != null && !RegionTitles.blank(s.name().get())) return s.name().get();
        Component named = RegionTitles.sourceName(s.source() == null ? ContainerRegions.state.opened : s.source().get());
        if (!RegionTitles.blank(named)) return named;
        return s.title() == null || RegionTitles.blank(s.title().get()) ? null : s.title().get();
    }

    // The tab row, drawn and clicked

    /**
     * The tab row, on the list's top edge: vanilla's creative top tabs ({@link ControlLook.Control#CONTAINER_TAB}),
     * each with its container's icon and its name as its tooltip, in a background panel
     * drawn over the list's. A tab not picked stops at the frame, as vanilla's goes behind its
     * image; the picked one runs {@link #INTO} px into the frame, so it opens into the panel.
     * Past {@link #fits} tabs the row pages, a tab at a time, with vanilla's page arrows at its ends.
     */
    static final class Strip implements PanelElement {
        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { Geometry g = ContainerRegions.state.geometry; return g == null ? 0 : g.width(); }
        @Override public int getHeight() { return RegionLayout.TAB_ROW + INTO; }
        @Override public boolean isInteractive() { return true; }

        @Override
        public void render(RenderContext ctx) {
            Geometry g = ContainerRegions.state.geometry;
            Row r = row;
            if (g == null || r == null || r.count() < 2) return;
            GuiGraphicsExtractor graphics = ctx.graphics();
            List<Declared> sections = ContainerRegions.declared();
            Fit f = fit(g, r);
            int y = g.tabTop();
            int over = ctx.hasMouseInput() ? hitAt(g, r, ctx.mouseX(), ctx.mouseY()) : NONE;
            for (int i = f.first(); i < f.first() + f.shown(); i++) {
                int x = tabX(g.x(), f, i);
                boolean picked = i == r.shown();
                tab(graphics, ControlLook.Control.containerTab(picked, !f.paged() && i == 0), x, y, picked);
                graphics.item(icon(sections.get(r.sections()[i])), x + ICON_X, y + ICON_Y);
            }
            if (f.paged()) {
                int ay = y + (RegionLayout.TAB_ROW - ARROW_HEIGHT) / 2;
                if (f.first() > 0) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(false, over == BACK),
                            g.x(), ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
                if (f.first() + f.shown() < r.count()) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ControlLook.Control.pageArrow(true, over == FORWARD),
                            g.x() + g.width() - ARROW_WIDTH, ay, ARROW_WIDTH, ARROW_HEIGHT);
                }
            }
            if (over == BACK || over == FORWARD || (over >= 0 && over != r.shown())) graphics.requestCursor(CursorTypes.POINTING_HAND);
            if (over >= 0) {
                Component name = name(r, over, sections);
                if (name != null) MKTooltip.queue(graphics, name, ctx.mouseX(), ctx.mouseY());
            }
        }

        /**
         * One tab at {@code (x, y)}, the row's top: vanilla's sprite with {@link #CUT} rows of its
         * plain middle left out, so it stands {@link RegionLayout#TAB_ROW} above the frame.
         * Picked, its last {@link #INTO} rows go over the frame's top edge, open into it.
         */
        private static void tab(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, boolean picked) {
            int below = SPRITE_HEIGHT - CUT_FROM - CUT - (picked ? 0 : INTO);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, TAB_WIDTH, SPRITE_HEIGHT, 0, 0, x, y, TAB_WIDTH, CUT_FROM);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, TAB_WIDTH, SPRITE_HEIGHT, 0, CUT_FROM + CUT,
                    x, y + CUT_FROM, TAB_WIDTH, below);
        }

        /**
         * A left click on a tab picks it and on an arrow pages, held item or not. Taking the
         * click keeps it from vanilla, so a held item is neither dropped nor placed.
         */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            Geometry g = ContainerRegions.state.geometry;
            Row r = row;
            if (button != Click.LEFT || g == null || r == null || r.count() < 2) return false;
            int what = hitAt(g, r, in.mouseX(), in.mouseY());
            if (what == NONE) return false;
            act(r, what, fit(g, r));
            return true;
        }
    }
}
