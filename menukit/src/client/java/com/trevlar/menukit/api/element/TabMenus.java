package com.trevlar.menukit.api.element;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tabs other mods have added to named menus ({@link Tabs#addTo}), and the one
 * rule that merges them into an owner's tabs when the owner builds.
 *
 * <h3>Order</h3>
 *
 * The owner's builder order is the order, and each owner tab's id is a slot. A
 * contribution whose id names a {@linkplain Tabs.TabSpec#standIn() stand-in} takes
 * that slot, so the stand-in is both the owner's anchor and its default content;
 * there is no other stand-in path. Every other contribution hangs off an anchor,
 * {@code after(id)} or {@code before(id)}, owner's or contributed, and the menu is
 * read as a tree: each tab's {@code before} contributions, the tab, then its
 * {@code after} contributions, each group ordered by id. Ordering by id, never by
 * registration, is what makes the result the same whichever mod initialised
 * first. A contribution with no anchor, an anchor that is not in the menu, or an
 * anchor cycle goes at the end, ordered by id.
 */
final class TabMenus {

    private TabMenus() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** menu -> contributions by id, in registration order (order is decided at build, not here). */
    private static final Map<Identifier, Map<String, Tabs.TabSpec>> CONTRIBUTIONS = new HashMap<>();
    /** Menus some owner has built at least once: a later add is shown from the next open. */
    private static final Set<Identifier> BUILT = new HashSet<>();
    /** Menus already warned about a late add, and (menu, id) pairs already warned about a bad anchor. */
    private static final Set<String> WARNED = new HashSet<>();

    static synchronized void add(Identifier menu, Tabs.TabSpec spec) {
        Map<String, Tabs.TabSpec> byId = CONTRIBUTIONS.computeIfAbsent(menu, m -> new LinkedHashMap<>());
        if (byId.containsKey(spec.id())) {
            throw new IllegalStateException("Tabs: two mods added a tab with the id '" + spec.id()
                    + "' to the menu " + menu + ". Tab ids must be unique within a menu; one of them "
                    + "must change its id.");
        }
        byId.put(spec.id(), spec);
        if (BUILT.contains(menu) && WARNED.add("late " + menu)) {
            LOGGER.warn("[MenuKit] a tab was added to {} after that menu was first built; it shows from "
                    + "the next time the menu opens. Add tabs at mod init.", menu);
        }
    }

    /**
     * The owner's tabs with every contribution to {@code menu} merged in, in the
     * menu's order. Bodies are not built here; the caller builds each spec.
     */
    static synchronized List<Tabs.TabSpec> merge(Identifier menu, List<Tabs.TabSpec> owner) {
        BUILT.add(menu);
        Map<String, Tabs.TabSpec> pending = new LinkedHashMap<>(CONTRIBUTIONS.getOrDefault(menu, Map.of()));

        // 1. The owner's slots. A stand-in gives its slot to a contribution with its id.
        List<Tabs.TabSpec> roots = new ArrayList<>();
        for (Tabs.TabSpec o : owner) {
            Tabs.TabSpec c = pending.remove(o.id());
            if (c == null) {
                roots.add(o);
            } else if (o.isStandIn()) {
                // Only the place carries over. The contribution is added as it came,
                // so its own visibleWhen (or none) applies, never the stand-in's.
                roots.add(c);
            } else {
                roots.add(o);
                LOGGER.error("[MenuKit] a tab added to {} has the id '{}', which the menu's owner uses for "
                        + "its own tab. The owner's tab is kept and the added one is dropped.", menu, o.id());
            }
        }

        // 2. Everything left hangs off an anchor, in two lists per anchor id.
        Set<String> ids = new HashSet<>();
        for (Tabs.TabSpec r : roots) ids.add(r.id());
        ids.addAll(pending.keySet());
        Map<String, List<Tabs.TabSpec>> before = new HashMap<>();
        Map<String, List<Tabs.TabSpec>> after = new HashMap<>();
        List<Tabs.TabSpec> loose = new ArrayList<>();
        for (Tabs.TabSpec c : pending.values()) {
            String anchor = c.anchor();
            if (anchor == null) {
                loose.add(c);
            } else if (!ids.contains(anchor)) {
                loose.add(c);
                if (WARNED.add("anchor " + menu + " " + c.id())) {
                    LOGGER.warn("[MenuKit] the tab '{}' added to {} is placed relative to '{}', which is not "
                            + "in the menu; it goes at the end.", c.id(), menu, anchor);
                }
            } else {
                (c.anchorIsBefore() ? before : after).computeIfAbsent(anchor, k -> new ArrayList<>()).add(c);
            }
        }
        Comparator<Tabs.TabSpec> byId = Comparator.comparing(Tabs.TabSpec::id);
        before.values().forEach(l -> l.sort(byId));
        after.values().forEach(l -> l.sort(byId));
        loose.sort(byId);

        // 3. Read the tree: owner's slots, then the loose ones, then whatever a cycle
        //    kept unreached. The visited set is what stops a cycle from looping.
        List<Tabs.TabSpec> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Tabs.TabSpec r : roots) walk(r, before, after, visited, out);
        for (Tabs.TabSpec l : loose) walk(l, before, after, visited, out);
        List<Tabs.TabSpec> unreached = new ArrayList<>();
        for (Tabs.TabSpec c : pending.values()) if (!visited.contains(c.id())) unreached.add(c);
        unreached.sort(byId);
        for (Tabs.TabSpec c : unreached) {
            if (WARNED.add("cycle " + menu + " " + c.id())) {
                LOGGER.warn("[MenuKit] the tab '{}' added to {} is part of an anchor cycle; it goes at the end.",
                        c.id(), menu);
            }
            walk(c, before, after, visited, out);
        }
        return out;
    }

    private static void walk(Tabs.TabSpec t, Map<String, List<Tabs.TabSpec>> before,
                             Map<String, List<Tabs.TabSpec>> after, Set<String> visited,
                             List<Tabs.TabSpec> out) {
        if (!visited.add(t.id())) return;
        for (Tabs.TabSpec b : before.getOrDefault(t.id(), List.of())) walk(b, before, after, visited, out);
        out.add(t);
        for (Tabs.TabSpec a : after.getOrDefault(t.id(), List.of())) walk(a, before, after, visited, out);
    }
}
