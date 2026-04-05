package com.trevorschoeny.menukit;

import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Defines a custom drag behavior that activates when specific conditions are met
 * during a click-drag across inventory slots.
 *
 * <p>Register via {@code MenuKit.dragMode("my_mode").when(...).onSlotEntered(...).register()}.
 *
 * <p>Part of the <b>MenuKit</b> drag mode API.
 */
public record MKDragMode(
        String id,
        Predicate<MKDragContext> activationTest,
        @Nullable Consumer<MKDragContext> onDragStart,
        Consumer<MKDragSlotEvent> onSlotEntered,
        @Nullable Consumer<MKDragContext> onDragEnd
) {

    /** Builder for creating drag modes. */
    public static class Builder {
        private final String id;
        private @Nullable Predicate<MKDragContext> activationTest;
        private @Nullable Consumer<MKDragContext> onDragStart;
        private @Nullable Consumer<MKDragSlotEvent> onSlotEntered;
        private @Nullable Consumer<MKDragContext> onDragEnd;

        public Builder(String id) {
            this.id = id;
        }

        /** Condition for this drag mode to activate. Checked on mouse-down. */
        public Builder when(Predicate<MKDragContext> test) {
            this.activationTest = test;
            return this;
        }

        /** Called once when the drag starts (optional). */
        public Builder onDragStart(Consumer<MKDragContext> handler) {
            this.onDragStart = handler;
            return this;
        }

        /** Called each time the cursor enters a new slot during the drag. */
        public Builder onSlotEntered(Consumer<MKDragSlotEvent> handler) {
            this.onSlotEntered = handler;
            return this;
        }

        /** Called once when the drag ends (mouse released). Optional. */
        public Builder onDragEnd(Consumer<MKDragContext> handler) {
            this.onDragEnd = handler;
            return this;
        }

        /** Registers this drag mode with MenuKit. */
        public void register() {
            if (activationTest == null) throw new IllegalStateException(
                    "[MenuKit] Drag mode '" + id + "' must have a .when() condition");
            if (onSlotEntered == null) throw new IllegalStateException(
                    "[MenuKit] Drag mode '" + id + "' must have an .onSlotEntered() handler");
            MKDragRegistry.register(new MKDragMode(id, activationTest, onDragStart, onSlotEntered, onDragEnd));
        }
    }
}
