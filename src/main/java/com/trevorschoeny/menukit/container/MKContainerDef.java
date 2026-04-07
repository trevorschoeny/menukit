package com.trevorschoeny.menukit.container;

import com.trevorschoeny.menukit.MenuKit;

/**
 * Immutable definition of a container — its name, binding type, and size.
 * Created once at mod init by {@link MenuKit#container(String)}, stored in
 * MenuKit's container registry.
 *
 * <p>Containers are the storage layer of MenuKit — they hold items. Panels
 * are the UI layer — they display slots that point at containers. This
 * separation allows:
 * <ul>
 *   <li>Multiple panels to share the same container (e.g., equipment visible
 *       in both inventory and a standalone screen)</li>
 *   <li>Player-bound containers that follow the player everywhere</li>
 *   <li>Instance-bound containers tied to a specific block in the world</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKContainerDef(
        String name,            // unique identifier, used as NBT key
        BindingType binding,    // PLAYER, INSTANCE, or EPHEMERAL
        Persistence persistence,// PERSISTENT, TRANSIENT, or OUTPUT
        int size,               // number of slots in the container
        MKContainerType containerType // functional classification (SIMPLE, CRAFTING, etc.)
) {

    /** Backward-compatible constructor — defaults containerType to SIMPLE. */
    public MKContainerDef(String name, BindingType binding, Persistence persistence, int size) {
        this(name, binding, persistence, size, MKContainerType.SIMPLE);
    }

    /** How a container's data is keyed and persisted (WHO sees it). */
    public enum BindingType {
        /** Container follows the player. Stored in player NBT. Same items everywhere. */
        PLAYER,
        /** Container is tied to a block position. Stored in world SavedData.
         *  Each block has its own independent container. */
        INSTANCE,
        /** Temporary container, not persisted to NBT. Stored in player maps for
         *  lookup but skipped during save/load. Designed to be bound to an external
         *  source (item contents, live container) via {@link MKContainer#bind}. */
        EPHEMERAL
    }

    /** What happens to items in the container (HOW items behave). Orthogonal to binding. */
    public enum Persistence {
        /** Items stay in the container across screen close. Default for most containers. */
        PERSISTENT,
        /** Items eject to the player's inventory when the screen closes.
         *  Like vanilla crafting grids — temporary workspace. */
        TRANSIENT,
        /** Read-only — items can be taken out but cannot be placed in.
         *  Like vanilla crafting result or furnace output. */
        OUTPUT
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    /**
     * Fluent builder for container definitions.
     *
     * <p>Usage:
     * <pre>{@code
     * MenuKit.container("equipment")
     *     .playerBound()    // default
     *     .size(2)
     *     .register();
     * }</pre>
     */
    public static class Builder {
        private final String name;
        private BindingType binding = BindingType.PLAYER;
        private Persistence persistence = Persistence.PERSISTENT;
        private int size = 0;
        private MKContainerType containerType = MKContainerType.SIMPLE;

        public Builder(String name) {
            this.name = name;
        }

        // ── Binding (WHO sees it) ────────────────────────────────────────

        /** Container follows the player (default). Stored in player NBT. */
        public Builder playerBound() {
            this.binding = BindingType.PLAYER; return this;
        }

        /** Container is tied to a block position. Stored in world SavedData. */
        public Builder instanceBound() {
            this.binding = BindingType.INSTANCE; return this;
        }

        /** Container is temporary — not persisted to NBT. Intended for binding
         *  to external sources (shulker box contents, ender chest, bundles). */
        public Builder ephemeral() {
            this.binding = BindingType.EPHEMERAL; return this;
        }

        // ── Persistence (HOW items behave) ───────────────────────────────

        /** Items persist across screen close (default). */
        public Builder persistent() {
            this.persistence = Persistence.PERSISTENT; return this;
        }

        /** Items eject to player inventory when the screen closes.
         *  Like vanilla crafting grids — temporary workspace. */
        public Builder transientItems() {
            this.persistence = Persistence.TRANSIENT; return this;
        }

        /** Read-only — items can be taken out but not placed in.
         *  Like vanilla crafting result or furnace output. */
        public Builder outputOnly() {
            this.persistence = Persistence.OUTPUT; return this;
        }

        // ── Container Type (WHAT it is) ──────────────────────────────────

        /** Sets the functional classification (SIMPLE, CRAFTING, PROCESSING,
         *  EQUIPMENT, HOTBAR, OTHER). Defaults to SIMPLE. */
        public Builder type(MKContainerType type) {
            this.containerType = type; return this;
        }

        // ── Size ─────────────────────────────────────────────────────────

        /** Sets the number of slots in the container. */
        public Builder size(int size) {
            this.size = size; return this;
        }

        /** Builds the definition and registers it with MenuKit. */
        public void register() {
            if (size <= 0) {
                throw new IllegalStateException(
                        "[MenuKit] Container '" + name + "' must have size > 0");
            }
            MenuKit.registerContainer(new MKContainerDef(name, binding, persistence, size, containerType));
        }
    }
}
