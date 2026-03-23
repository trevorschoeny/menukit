package com.trevorschoeny.menukit;

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
        BindingType binding,    // PLAYER or INSTANCE
        int size                // number of slots in the container
) {

    /** How a container's data is keyed and persisted. */
    public enum BindingType {
        /** Container follows the player. Stored in player NBT. Same items everywhere. */
        PLAYER,
        /** Container is tied to a block position. Stored in world SavedData.
         *  Each block has its own independent container. */
        INSTANCE
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
        private int size = 0;

        Builder(String name) {
            this.name = name;
        }

        /** Container follows the player (default). Stored in player NBT. */
        public Builder playerBound() {
            this.binding = BindingType.PLAYER; return this;
        }

        /** Container is tied to a block position. Stored in world SavedData. */
        public Builder instanceBound() {
            this.binding = BindingType.INSTANCE; return this;
        }

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
            MenuKit.registerContainer(new MKContainerDef(name, binding, size));
        }
    }
}
