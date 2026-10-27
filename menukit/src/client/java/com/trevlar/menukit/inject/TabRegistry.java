package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.ContainerTabs;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *
 * <p><b>Lists of groups</b> ({@code ContainerTabs.groups}): each one's group keys are read the
 * same way, and each live group is a keyed list with a header, made the frame its key first
 * appears and kept while it is there; its own keys are then read as a declared list's are. A
 * group whose key is also among another live group's keys stands nested in that group, in place
 * of the sub-tab that key would have ({@link #path}).
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

    /**
     * A group of tabs (a keyed list with a header): the list's id, what its slide is kept by from
     * frame to frame ({@code ref}: its id, or a list of groups' group's {@link KeyRef}), and
     * whether its sub-tabs are out, read every frame.
     */
    public record Group(String id, Object ref, BooleanSupplier expanded) {}

    /**
     * One keyed list of tabs ({@code ContainerTabs.keyed}): {@code keys} read every frame a
     * container screen is open, and {@code tab} making a key's tab, the frame it first appears.
     * With a {@code header}, a group: the header first, keys or none, its sub-tabs after it.
     */
    public record Keyed(String id, Supplier<? extends @Nullable List<?>> keys, Function<Object, Tab> tab, @Nullable Tab header) {}

    /**
     * One list of groups ({@code ContainerTabs.groups}): {@code groups}, its group keys, read every
     * frame a container screen is open, and {@code group} making a group key's keyed list, with
     * its header, the frame the key first appears.
     */
    public record Groups(String id, Supplier<? extends @Nullable List<?>> groups, Function<Object, Keyed> group) {}

    /**
     * A key's tab's {@link Tab#ref}: its list's ref (a declared list's id, or a group's own
     * {@code KeyRef}) and its key, so keys that read alike as text are still two tabs.
     */
    public record KeyRef(Object list, @Nullable Object key) {}

    private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
    private static final List<Keyed> KEYED = new CopyOnWriteArrayList<>();
    /** Each list's live keys, in the order its keys gave them, and each one's tab ({@code null}: its making threw). */
    private static final Map<Keyed, Map<Object, @Nullable Tab>> KEYED_LIVE = new IdentityHashMap<>();
    private static final List<Groups> GROUPS = new CopyOnWriteArrayList<>();
    /** Each list of groups' live groups, in the order its group keys gave them, and each one's list ({@code null}: its making threw). */
    private static final Map<Groups, Map<Object, @Nullable Keyed>> GROUPS_LIVE = new IdentityHashMap<>();
    /** Every tab there is now, declared and keyed, in row order. */
    private static List<Tab> all = List.of();
    /** Each nested group, as of the last merge, and the group it stands in. */
    private static Map<Group, Group> within = Map.of();
    /** A list was declared: its header stands before any key comes, so the next frame places the lists anew. */
    private static boolean keyedDirty;

    /** {@code ContainerTabs.Builder.declare}: adds a tab in its place, and the rows' panels with the first one. */
    public static synchronized void declare(Tab tab) {
        requireFree(tab.id(), null);
        TABS.add(tab);
        merge();
        TabStrip.buildPanels();
    }

    /** {@code ContainerTabs.Keyed.declare}: adds a list, and the rows' panels with the first. */
    public static synchronized void declareKeyed(Keyed list) {
        requireFree(list.id(), "keyed");
        KEYED.add(list);
        keyedDirty = true;
        TabStrip.buildPanels();
    }

    /** {@code ContainerTabs.Groups.declare}: adds a list of groups, and the rows' panels with the first. */
    public static synchronized void declareGroups(Groups list) {
        requireFree(list.id(), "groups");
        GROUPS.add(list);
        TabStrip.buildPanels();
    }

    /**
     * Refuses an id another tab or list has, and one that a list's tabs could take: a key's tab is
     * named {@code list#key} (a group's {@code list#group}, its sub-tabs {@code list#group#key}),
     * so a declared tab named after a list and a {@code #} is refused, and so is a list whose
     * {@code id#} starts another tab's or list's id, or whose id starts with another list's
     * {@code id#}. {@code list} names a list's kind ({@code "keyed"}, {@code "groups"}), or is
     * {@code null} for a tab.
     */
    private static void requireFree(String id, @Nullable String list) {
        String what = "ContainerTabs " + (list == null ? "tab" : list) + " '" + id + "'";
        for (Tab t : TABS) {
            if (t.id().equals(id)) throw new IllegalStateException(what + ": tab '" + id + "' is already declared");
            if (list != null && t.id().startsWith(id + "#")) {
                throw new IllegalStateException(what + ": its keys' tabs would take tab '" + t.id() + "''s id");
            }
        }
        List<String> lists = new ArrayList<>();
        for (Keyed k : KEYED) lists.add(k.id());
        for (Groups g : GROUPS) lists.add(g.id());
        for (String other : lists) {
            if (other.equals(id)) throw new IllegalStateException(what + ": list '" + id + "' is already declared");
            if (id.startsWith(other + "#")) throw new IllegalStateException(what + ": list '" + other + "''s tabs take ids like it");
            if (list != null && other.startsWith(id + "#")) {
                throw new IllegalStateException(what + ": its tabs would take list '" + other + "''s ids");
            }
        }
    }

    /** Every tab in row order, as of the last {@link #sync}. */
    static List<Tab> all() {
        return all;
    }

    /**
     * The groups {@code t} stands in, as of the last {@link #sync}, outermost first: none for a
     * tab outside any group or a group's header at the top of its row, its group for a sub-tab,
     * and for a nested group's header or sub-tab every group above it as well. A tab shows in
     * the row only while each of them is expanded.
     */
    static List<Group> path(Tab t) {
        Group g = t.group();
        if (g == null) return List.of();
        List<Group> out = new ArrayList<>();
        if (t.sub()) out.add(g);
        for (Group up = within.get(g); up != null; up = within.get(up)) out.add(up);
        return out.reversed();
    }

    /**
     * Each keyed list's keys now ({@link LiveKeys}): a key that is new makes its tab, one that is
     * gone takes it with it, and one that stays keeps the tab it had. Each list of groups' group
     * keys likewise make and keep its groups, and then each live group's keys its tabs. The tabs
     * re-place only when some list's keys changed. A list whose keys throw has none this frame.
     */
    static void sync() {
        boolean changed = keyedDirty;
        keyedDirty = false;
        for (Keyed list : KEYED) changed |= syncKeys(list);
        for (Groups list : GROUPS) {
            List<?> keys = Guard.get(list.groups(), "groups", list.id());
            Map<Object, @Nullable Keyed> was = GROUPS_LIVE.getOrDefault(list, Map.of());
            Map<Object, @Nullable Keyed> now = LiveKeys.resync(was, keys, list.group(), "group", list.id());
            if (now != null) {
                // A group that went takes its tabs with it.
                Map<Keyed, Boolean> kept = new IdentityHashMap<>();
                for (Keyed g : now.values()) if (g != null) kept.put(g, true);
                for (Keyed g : was.values()) if (g != null && !kept.containsKey(g)) KEYED_LIVE.remove(g);
                if (now.isEmpty()) GROUPS_LIVE.remove(list);
                else GROUPS_LIVE.put(list, now);
                changed = true;
            }
            for (Keyed g : GROUPS_LIVE.getOrDefault(list, Map.of()).values()) if (g != null) changed |= syncKeys(g);
        }
        if (changed) merge();
    }

    /** {@code list}'s keys now, its tabs made, kept and dropped with them: whether they changed. */
    private static boolean syncKeys(Keyed list) {
        List<?> keys = Guard.get(list.keys(), "keys", list.id());
        Map<Object, @Nullable Tab> now = LiveKeys.resync(KEYED_LIVE.getOrDefault(list, Map.of()), keys, list.tab(), "tab", list.id());
        if (now == null) return false;
        if (now.isEmpty()) KEYED_LIVE.remove(list);
        else KEYED_LIVE.put(list, now);
        return true;
    }

    /**
     * Every tab in row order: by order, then by its id or its list's (a keyed list's tabs
     * together, and a list of groups' every group's), then a list's header first and its keys'
     * tabs in the keys' order, a list of groups' groups one after another in its group keys' order.
     * A list of groups' group whose key is among another live group's keys stands in that group
     * instead, its header and sub-tabs where that key's sub-tab would be.
     */
    private static void merge() {
        List<Entry> every = new ArrayList<>();
        Map<Group, Group> nested = new IdentityHashMap<>();
        for (Tab t : TABS) every.add(new Entry(t, t.id(), 0));
        for (Keyed list : KEYED) add(every, list, list.id(), 0);
        for (Groups list : GROUPS) {
            Map<Object, @Nullable Keyed> live = GROUPS_LIVE.getOrDefault(list, Map.of());
            // The group keys that are another group's keys too: those stand in it, not at the top.
            Set<Object> inside = new HashSet<>();
            live.forEach((key, g) -> {
                if (g != null) for (Object k : KEYED_LIVE.getOrDefault(g, Map.of()).keySet()) {
                    if (!Objects.equals(k, key) && live.get(k) != null) inside.add(k);
                }
            });
            Set<Object> placed = new HashSet<>();
            int[] i = {0};
            for (Object key : live.keySet()) if (!inside.contains(key)) i[0] = nest(every, list.id(), live, key, null, placed, nested, i[0]);
            // Groups that only stand in each other, a loop no top group reaches: the first of them stands at the top.
            for (Object key : live.keySet()) i[0] = nest(every, list.id(), live, key, null, placed, nested, i[0]);
        }
        every.sort(Comparator.comparingInt((Entry e) -> e.tab().order()).thenComparing(Entry::base).thenComparingInt(Entry::index));
        all = every.stream().map(Entry::tab).toList();
        within = nested;
    }

    /**
     * Adds group {@code key} of a list of groups ({@code live}) to {@code every} under {@code base},
     * numbered on from {@code i}, standing in {@code in} ({@code null}: at the top): its header,
     * then its keys' tabs, a key that is another live group not yet placed standing as that group,
     * nested, in its sub-tab's place. A group is placed once; the next number.
     */
    private static int nest(List<Entry> every, String base, Map<Object, @Nullable Keyed> live, @Nullable Object key,
                            @Nullable Group in, Set<Object> placed, Map<Group, Group> nested, int i) {
        Keyed list = live.get(key);
        if (list == null || !placed.add(key)) return i;
        Tab head = list.header();
        if (in != null) nested.put(head.group(), in);
        every.add(new Entry(head, base, i++));
        for (Map.Entry<Object, @Nullable Tab> e : KEYED_LIVE.getOrDefault(list, Map.of()).entrySet()) {
            Object k = e.getKey();
            if (!Objects.equals(k, key) && live.get(k) != null && !placed.contains(k)) i = nest(every, base, live, k, head.group(), placed, nested, i);
            else if (e.getValue() != null) every.add(new Entry(e.getValue(), base, i++));
        }
        return i;
    }

    private record Entry(Tab tab, String base, int index) {}

    /** Adds {@code list}'s header and live tabs to {@code every} under {@code base}, numbered on from {@code i}: the next number. */
    private static int add(List<Entry> every, Keyed list, String base, int i) {
        if (list.header() != null) every.add(new Entry(list.header(), base, i++));
        for (Tab t : KEYED_LIVE.getOrDefault(list, Map.of()).values()) if (t != null) every.add(new Entry(t, base, i++));
        return i;
    }
}
