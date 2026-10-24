package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.inject.RegionLayout.Geometry;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shift-click lands in view (7.4.0, the container region): after a quick move into one of
 * the region's slots that is scrolled out of view, the region scrolls to it.
 *
 * <p>Where a slot sits is noted each time the region draws it, as its section and how far
 * down that section it is ({@link Cell}), so it follows its section when sections above it
 * open or close. Known, a reveal is one scroll. Not known (a section below the fold never
 * drawn, or one whose height has changed since), the region pages down a viewport a frame
 * until the slot is drawn, then shows it; if no page has it, it goes back to where it was.
 *
 * <p>A quick move into a section not shown (its slots are off, {@link RegionSlots}) scrolls
 * nothing: that section's {@code onHiddenLanding} runs instead, so its mod can show it.
 * MenuKit shows nothing itself.
 *
 * <p>Render thread only; the state is the open region's, reset with it.
 */
@ApiStatus.Internal
public final class RegionReveal {

    private RegionReveal() {}

    /**
     * Where a slot the region drew sits in the content: in section {@code section}, its cell
     * {@code offset} pixels below the section's top, while the section was
     * {@code sectionHeight} tall.
     */
    public record Cell(int section, int offset, int sectionHeight) {

        /** The cell whose top is {@code cellTop} down the content, in the section holding it; {@code null} in none. Pure. */
        public static @Nullable Cell at(int cellTop, int[] tops, int[] bottoms) {
            for (int i = 0; i < tops.length; i++) {
                if (tops[i] >= 0 && cellTop >= tops[i] && cellTop < bottoms[i]) {
                    return new Cell(i, cellTop - tops[i], bottoms[i] - tops[i]);
                }
            }
            return null;
        }

        /**
         * Where the cell's top is in the content now, its section at {@code tops}; {@code null}
         * while the section is hidden or a different height (its content laid out again). Pure.
         */
        public @Nullable Integer top(int[] tops, int[] bottoms) {
            if (section >= tops.length || tops[section] < 0 || bottoms[section] - tops[section] != sectionHeight) return null;
            return tops[section] + offset;
        }
    }

    /** A quick move landed out of view: the slot to show, and how the search for it goes ({@code page} -1: known). */
    private record Reveal(Slot slot, int page, double from) {}

    /** Where each slot the region has drawn sits. */
    private static final Map<Slot, Cell> CELLS = new IdentityHashMap<>();
    private static @Nullable Reveal reveal;

    /** The open region went (or another opened): nothing carries over. */
    static void reset() {
        CELLS.clear();
        reveal = null;
    }

    /**
     * After the background pass placed the region's slots ({@code slots}, in {@code screen}'s
     * coordinates from {@code topPos}): each one's cell is noted, this frame's scroll and
     * section tops.
     */
    static void placed(Iterable<Slot> slots, int topPos, Geometry g, double scroll, int[] tops, int[] bottoms) {
        for (Slot slot : slots) {
            Cell c = Cell.at(RegionLayout.cellTop(topPos, slot.y, g, scroll), tops, bottoms);
            if (c != null) CELLS.put(slot, c);
        }
    }

    /**
     * The sections were restacked: each noted cell's section moves to its new place,
     * {@code to[old]}, and a cell whose section is gone ({@code -1}) is forgotten.
     */
    static void remap(int[] to) {
        CELLS.entrySet().removeIf(e -> e.getValue().section() >= to.length || to[e.getValue().section()] < 0);
        CELLS.replaceAll((slot, c) -> new Cell(to[c.section()], c.offset(), c.sectionHeight()));
    }

    /** The section the region last drew {@code slot} in, by stacking index, or {@code -1} for one it never drew. */
    static int sectionOf(Slot slot) {
        Cell c = CELLS.get(slot);
        return c == null ? -1 : c.section();
    }

    /** Whether a region is up on the open screen: the quick-move hook takes its snapshot only then. */
    public static boolean active() {
        Screen s = ContainerRegions.state.screen.get();
        return s != null && ContainerRegions.state.geometry != null && Minecraft.getInstance().gui.screen() == s;
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
     * which are not the region's, so nothing scrolls. One into a hidden section runs that
     * section's {@code onHiddenLanding}. One into a scrolling slot grid's slot scrolls that grid
     * ({@code SlotScroll}), with or without a region on the screen.
     */
    public static void quickMoved(AbstractContainerMenu menu, List<ItemStack> before) {
        ContainerRegions.State state = ContainerRegions.state;
        Geometry g = state.geometry;
        Panel panel = ContainerRegions.listPanel();
        AbstractContainerScreen<?> acs = state.screen.get() instanceof AbstractContainerScreen<?> s && g != null
                && s.getMenu() == menu ? s : null;
        for (int i = 0; i < menu.slots.size() && i < before.size(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack was = before.get(i), now = slot.getItem();
            boolean gained = !now.isEmpty() && (was.isEmpty() || !ItemStack.isSameItemSameComponents(was, now)
                    || now.getCount() > was.getCount());
            if (!gained) continue;
            int hidden = acs == null ? -1 : RegionSlots.inHidden(slot);
            if (hidden >= 0) {                                 // landed in a section not shown: its mod decides
                ContainerRegions.landedHidden(hidden);
                return;
            }
            // A scrolling slot grid holding it (a section's own, or any panel's) scrolls to it itself.
            // ponytail: a grid in a section the list has scrolled out of view scrolls, the list doesn't
            if (com.trevlar.menukit.api.element.SlotScroll.quickMoved(slot)) return;
            if (acs == null || !regionsOwn(acs, slot, panel)) return;   // landed in the inventory (or elsewhere in view)
            Integer cell = cellTop(slot, state.tops, state.bottoms);
            if (cell != null && RegionLayout.revealScroll(state.scroll, cell, g) == state.scroll
                    && PresentedSlots.lastPanel(slot) == panel && onScreen(acs, slot)) return;     // landed in view
            reveal = new Reveal(slot, cell != null ? -1 : 0, state.scroll);
            return;
        }
    }

    /** Where {@code slot}'s cell is in the content now, or {@code null} when that isn't known. */
    private static @Nullable Integer cellTop(Slot slot, int[] tops, int[] bottoms) {
        Cell c = CELLS.get(slot);
        return c == null ? null : c.top(tops, bottoms);
    }

    /**
     * Whether a slot is the region's: one it showed, or one no panel ever showed that is not
     * on screen either (a section below the fold, never drawn; its elements get
     * {@code renderCulled}, so its library keeps it parked, a vanilla slot too, not sent home).
     * The player's inventory is on screen where vanilla put it, so it is never the region's.
     */
    private static boolean regionsOwn(AbstractContainerScreen<?> screen, Slot slot, @Nullable Panel panel) {
        Panel last = PresentedSlots.lastPanel(slot);
        if (last != null) return last == panel;
        return !onScreen(screen, slot);
    }

    private static boolean onScreen(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int x = acc.mk$getLeftPos() + slot.x, y = acc.mk$getTopPos() + slot.y;
        return x > -RegionLayout.CELL && y > -RegionLayout.CELL && x < screen.width && y < screen.height;
    }

    /** One frame of a reveal: the scroll it asks for, {@code scroll} with none under way. */
    static double step(Geometry g, double scroll, int[] tops, int[] bottoms) {
        Reveal r = reveal;
        if (r == null) return scroll;
        Integer cell = cellTop(r.slot(), tops, bottoms);
        if (cell != null) {
            reveal = null;
            return RegionLayout.revealScroll(scroll, cell, g);
        }
        // Its place known when the move landed but not now (its section changed since): searched for.
        int page = Math.max(0, r.page());
        int pageTop = page * Math.max(1, g.viewHeight());
        if (pageTop >= g.content()) {   // looked everywhere: not the region's after all
            reveal = null;
            return r.from();
        }
        reveal = new Reveal(r.slot(), page + 1, r.from());
        return pageTop;
    }
}
