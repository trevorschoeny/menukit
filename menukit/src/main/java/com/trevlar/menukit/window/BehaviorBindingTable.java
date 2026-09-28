package com.trevlar.menukit.window;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The window's server tier: server-authoritative declarations (gating, binding,
 * mending, the operations), keyed by {@link Address}, with the same per-slot &gt;
 * per-group walk the client engine uses. {@link #resolve} returns the server's
 * winning {@link Decl.Set}, or {@link Decl#inherit()} when the server does not
 * override, so the engine falls through to the client tier and the default.
 *
 * <p>Always present (§0062): until 6.0.0 this lived in Containers and MenuKit held
 * a null object until Containers installed it, so a MenuKit-only mod's server-tier
 * declarations did nothing and a declaration made before Containers' init had to
 * be buffered. 6.0.0's phase 2 folds it and the client store into one (§0063).
 *
 * <p>{@link #isEmpty()} and {@link #hasBinding(Address)} back "the slots nobody
 * touches stay exactly vanilla": a server seam early-outs on an address with no
 * binding.
 */
@org.jetbrains.annotations.ApiStatus.Internal
public final class BehaviorBindingTable {

    public static final BehaviorBindingTable INSTANCE = new BehaviorBindingTable();

    private BehaviorBindingTable() {}

    private final Map<Address, Map<BehaviorKey<?>, Decl<?>>> perAddress = new ConcurrentHashMap<>();
    private final List<GroupBinding> groups = new CopyOnWriteArrayList<>();

    private record GroupBinding(GroupKey group, Map<BehaviorKey<?>, Decl<?>> decls) {}

    // ── write ───────────────────────────────────────────────────────────

    public <V> void declare(Address address, BehaviorKey<V> key, Decl<V> decl) {
        perAddress.computeIfAbsent(address, a -> new ConcurrentHashMap<>()).put(key, decl);
    }

    public <V> void declareGroup(GroupKey group, BehaviorKey<V> key, Decl<V> decl) {
        bindingFor(group).put(key, decl);
    }

    // ── read (AXIS-1) ───────────────────────────────────────────────────

    public <V> Decl<V> resolve(Address address, BehaviorKey<V> key) {
        return resolve(address, key, java.util.List.of());
    }

    public <V> Decl<V> resolve(Address address, BehaviorKey<V> key, java.util.Collection<GroupKey> alsoMemberOf) {
        Decl<V> slot = declAt(address, key);
        if (slot instanceof Decl.Set<V>) return slot;           // server overrides at slot
        Decl<V> group = declForGroups(address, key, alsoMemberOf);
        if (group instanceof Decl.Set<V>) return group;         // server overrides at group
        return Decl.inherit();                                  // server does not override
    }

    // ── presence (Phase-4 seam fast-path) ───────────────────────────────

    /** Whether the table holds any binding at all. */
    public boolean isEmpty() {
        return perAddress.isEmpty() && groups.isEmpty();
    }

    /** Whether {@code address} has any server binding (per-slot or via a group). */
    public boolean hasBinding(Address address) {
        if (perAddress.containsKey(address)) return true;
        for (GroupBinding b : groups) {
            if (b.group().contains(address)) return true;
        }
        return false;
    }

    // ── internals (parallel to the MK client engine) ────────────────────

    @SuppressWarnings("unchecked")
    private <V> Decl<V> declAt(Address address, BehaviorKey<V> key) {
        Map<BehaviorKey<?>, Decl<?>> m = perAddress.get(address);
        return m == null ? null : (Decl<V>) m.get(key);
    }

    @SuppressWarnings("unchecked")
    private <V> Decl<V> declForGroups(Address address, BehaviorKey<V> key,
                                      java.util.Collection<GroupKey> alsoMemberOf) {
        Decl<V> result = null;
        int best = Integer.MIN_VALUE;
        for (GroupBinding b : groups) {                 // registration order
            if (!b.group().contains(address) && !alsoMemberOf.contains(b.group())) continue;
            Decl<?> d = b.decls().get(key);
            if (d == null) continue;
            // Same rule as the client engine: higher precedence wins, last-declared
            // breaks a tie within one precedence.
            if (b.group().precedence() >= best) {
                best = b.group().precedence();
                result = (Decl<V>) d;
            }
        }
        return result;
    }

    private synchronized Map<BehaviorKey<?>, Decl<?>> bindingFor(GroupKey group) {
        for (GroupBinding b : groups) {
            if (b.group().equals(group)) return b.decls();
        }
        Map<BehaviorKey<?>, Decl<?>> m = new ConcurrentHashMap<>();
        groups.add(new GroupBinding(group, m));
        return m;
    }
}
