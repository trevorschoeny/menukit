package com.trevlar.menukit.window;

/**
 * Port (MK defines, MKC implements): the WRITE side of the server tier. When a
 * consumer declares a SERVER-tier behavior (e.g. gating on a slot), the engine
 * routes the declaration here rather than into its client store — server-tier
 * behavior is authoritative and lives in MKC's server-side store
 * ({@code BehaviorBindingTable}, Phase 3c).
 *
 * <p>With MKC absent the installed bridge is the {@link NoServerTier} null-object,
 * so declaring a server-tier behavior client-only is a safe no-op (the key then
 * resolves to its library default). This is the DIP ownership-inversion seam:
 * MK owns the abstraction; MKC conforms and registers via {@link ServerTier}.
 */
public interface ServerTierBridge {

    /** Declare a server-tier behavior on one address (per-slot specificity). */
    <V> void declare(Address address, BehaviorKey<V> key, Decl<V> decl);

    /** Declare a server-tier behavior as a group default (per-group specificity). */
    <V> void declareGroup(GroupKey group, BehaviorKey<V> key, Decl<V> decl);
}
