package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The container screen's declared tabs (7.4.0; the public face is {@code api.panel.ContainerTabs}):
 * each tab a mod declares, each keyed list and the tabs its live keys have made, and all of them
 * in row order. {@link ContainerTabRows} reads them each frame to choose the rows. Declared from
 * client init; read on the render thread.
 *
 * <p><b>Keyed lists</b> ({@code ContainerTabs.keyed}): each list's keys are read as the rows are
 * chosen, every frame a container screen is open, and its tabs placed among the others as one
 * declared tab is, by the list's order. A list keeps its tabs while no container screen is
 * open, so a screen opened in place of another finds the same ones.
 */
@ApiStatus.Internal
public final class TabRegistry {

    private TabRegistry() {}

    /**
     * One declared tab ({@code ContainerTabs.tab}, or a keyed list's). {@code visible} already
     * holds its mode scope (and, for a sub-tab, its header's showing); its icon, the mod's
     * drawing, or none. {@code width} across and {@code height} out from its frame. A group's
     * header and its sub-tabs carry the {@code group}; {@code sub} marks the sub-tabs.
     * {@code pinned}, read every frame, pins it ({@link TabRowLayout#pin}). {@code ref} is what
     * the rows keep it by from frame to frame (the page, the selected tab kept in view): its id
     * for a declared tab or a header, its list and key ({@link KeyRef}) for a key's.
     */
    public record Tab(String id, Object ref, ScreenMatcher matcher, int order, Edge edge, BooleanSupplier visible,
                      BooleanSupplier selected, BooleanSupplier disabled, BooleanSupplier pinned, ContainerTabs.@Nullable Icon icon,
                      @Nullable Supplier<@Nullable Component> tooltip, Runnable onClick,
                      int width, int height, @Nullable Group group, boolean sub) {}

    /** A group of tabs (a keyed list with a header): the list's id, and whether its sub-tabs are out, read every frame. */
    public record Group(String id, BooleanSupplier expanded) {}

    /**
     * One keyed list of tabs ({@code ContainerTabs.keyed}): {@code keys} read every frame a
     * container screen is open, and {@code tab} making a key's tab, the frame it first appears.
     * With a {@code header}, a group: the header first, keys or none, its sub-tabs after it.
     */
    public record Keyed(String id, Supplier<? extends @Nullable List<?>> keys, Function<Object, Tab> tab, @Nullable Tab header) {}

    /** A key's tab's {@link Tab#ref}: its list and its key, so keys that read alike as text are still two tabs. */
    public record KeyRef(String list, @Nullable Object key) {}

    private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
    private static final List<Keyed> KEYED = new CopyOnWriteArrayList<>();
    /** Each list's live keys, in the order its keys gave them, and each one's tab ({@code null}: its making threw). */
    private static final Map<Keyed, Map<Object, @Nullable Tab>> KEYED_LIVE = new IdentityHashMap<>();
    /** Every tab there is now, declared and keyed, in row order. */
    private static List<Tab> all = List.of();
    /** A list was declared: its header stands before any key comes, so the next frame places the lists anew. */
    private static boolean keyedDirty;

    /** {@code ContainerTabs.Builder.declare}: adds a tab in its place, and the rows' panels with the first one. */
    public static synchronized void declare(Tab tab) {
        requireFree(tab.id(), false);
        TABS.add(tab);
        merge();
        TabStrip.buildPanels();
    }

    /** {@code ContainerTabs.Keyed.declare}: adds a list, and the rows' panels with the first. */
    public static synchronized void declareKeyed(Keyed list) {
        requireFree(list.id(), true);
        KEYED.add(list);
        keyedDirty = true;
        TabStrip.buildPanels();
    }

    /**
     * Refuses an id another tab or list has, and one that a key's tab could take: a key's tab is
     * named {@code list#key}, so a declared tab named after a list and a {@code #} is refused,
     * and so is a list whose {@code id#} starts a declared tab's id.
     */
    private static void requireFree(String id, boolean list) {
        for (Tab t : TABS) {
            if (t.id().equals(id)) throw new IllegalStateException("ContainerTabs tab '" + id + "' is already declared");
            if (list && t.id().startsWith(id + "#")) {
                throw new IllegalStateException("ContainerTabs keyed '" + id + "': its keys' tabs would take tab '" + t.id() + "''s id");
            }
        }
        for (Keyed k : KEYED) {
            if (k.id().equals(id)) throw new IllegalStateException("ContainerTabs keyed '" + id + "' is already declared");
            if (!list && id.startsWith(k.id() + "#")) {
                throw new IllegalStateException("ContainerTabs tab '" + id + "': a key's tab of keyed list '" + k.id() + "' takes ids like it");
            }
        }
    }

    /** Every tab in row order, as of the last {@link #sync}. */
    static List<Tab> all() {
        return all;
    }

    /**
     * Each keyed list's keys now ({@link LiveKeys}): a key that is new makes its tab, one that is
     * gone takes it with it, and one that stays keeps the tab it had. The tabs re-place only when
     * some list's keys changed. A list whose keys throw has none this frame.
     */
    static void sync() {
        boolean changed = keyedDirty;
        keyedDirty = false;
        for (Keyed list : KEYED) {
            List<?> keys = Guard.get(list.keys(), "keys", list.id());
            Map<Object, @Nullable Tab> now = LiveKeys.resync(KEYED_LIVE.getOrDefault(list, Map.of()), keys, list.tab(), "tab", list.id());
            if (now == null) continue;
            if (now.isEmpty()) KEYED_LIVE.remove(list);
            else KEYED_LIVE.put(list, now);
            changed = true;
        }
        if (changed) merge();
    }

    /**
     * Every tab in row order: by order, then by its id or its list's (a keyed list's tabs
     * together), then a list's header first and its keys' tabs in the keys' order.
     */
    private static void merge() {
        record Entry(Tab tab, String base, int index) {}
        List<Entry> every = new ArrayList<>();
        for (Tab t : TABS) every.add(new Entry(t, t.id(), 0));
        for (Keyed list : KEYED) {
            int i = 0;
            if (list.header() != null) every.add(new Entry(list.header(), list.id(), i++));
            for (Tab t : KEYED_LIVE.getOrDefault(list, Map.of()).values()) if (t != null) every.add(new Entry(t, list.id(), i++));
        }
        every.sort(Comparator.comparingInt((Entry e) -> e.tab().order()).thenComparing(Entry::base).thenComparingInt(Entry::index));
        all = every.stream().map(Entry::tab).toList();
    }
}
