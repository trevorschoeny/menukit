package com.trevorschoeny.menukit.window;

/**
 * The Null Object for the server tier — the installed implementation when MKC is
 * absent (MK alone). Every write is a no-op and every read says "the server does
 * not override" ({@link Decl#inherit()}), so a SERVER-tier behavior authored on a
 * client-only install does nothing and the key resolves to its library default.
 * This is what makes an MK-alone consumer a full citizen: it can NAME server-tier
 * verbs (the keys live in MK) and calling them is harmless.
 */
final class NoServerTier implements ServerTierBridge, AuthoritativeDeclarations, VanillaSlotIdentity {

    static final NoServerTier INSTANCE = new NoServerTier();

    private NoServerTier() {}

    @Override
    public java.util.Optional<Resolved> identify(net.minecraft.world.Container container, int containerSlotIndex) {
        return java.util.Optional.empty(); // no §0050 => the minter uses a menu-based fallback
    }

    @Override
    public <V> void declare(Address address, BehaviorKey<V> key, Decl<V> decl) {
        // no-op — server-tier authoring requires MKC
    }

    @Override
    public <V> void declareGroup(GroupKey group, BehaviorKey<V> key, Decl<V> decl) {
        // no-op — server-tier authoring requires MKC
    }

    @Override
    public <V> Decl<V> resolve(Address address, BehaviorKey<V> key) {
        return Decl.inherit(); // never overrides
    }
}
