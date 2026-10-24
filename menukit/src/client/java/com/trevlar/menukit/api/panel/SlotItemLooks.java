package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.inject.SlotItemLookRegistry;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * What a slot's item draws as on a container screen (Fluorite): a mod says, per slot and per
 * frame, which {@link ItemAnimation} the item draws with and how far through it is, and the
 * slot draws that in place of the item. The slot's count and durability bar, its highlight
 * and tooltip, and what a click does stay vanilla's.
 *
 * <pre>{@code
 * static final Transition OPENING = Transition.of(250, Easing.EASE_OUT);
 * static final ItemAnimation OPEN = ItemAnimation.OPEN_LID.posed(t -> new ItemAnimation.Pose(1f - 0.25f * t, 0f, 0f));
 *
 * SlotItemLooks.declare("mymod:open_boxes", (screen, slot, stack) -> {
 *     Object key = boxKey(slot);
 *     if (key == null) return null;
 *     return OPEN.at(OPENING.toward(key, isOpen(key)));
 * });
 * }</pre>
 *
 * <ul>
 *   <li><b>Asked every frame</b> for every slot the screen draws with an item, vanilla's and
 *       created ones alike, on every container screen. {@code null} leaves the slot to the
 *       next look, and with none, to vanilla.</li>
 *   <li><b>Timing is the mod's</b>, through a {@link com.trevlar.menukit.api.element.Transition}
 *       keyed by something that outlives the screen (the container, not the {@link Slot}), so
 *       a screen that opens in place of another picks a finished animation up where it
 *       stood.</li>
 *   <li><b>Several mods</b>: looks are asked in id order and the first that answers draws.</li>
 *   <li>A look that throws is logged once and skipped from then on.</li>
 * </ul>
 *
 * <p>Declare from your client initializer: looks freeze with every other declaration.
 */
public final class SlotItemLooks {

    private SlotItemLooks() {}

    /** What one mod draws a slot's item as. */
    @FunctionalInterface
    public interface Look {
        /**
         * The animation and its progress {@code slot}'s {@code stack} draws with this frame on
         * {@code screen}, or {@code null} to leave it be. {@code stack} is what the slot is
         * drawing, which during a drag is the stack it will get.
         */
        ItemAnimation.@Nullable Frame look(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack);
    }

    /**
     * Declares a look, its id namespaced and unique ({@code "mymod:open_boxes"}).
     *
     * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
     */
    public static void declare(String id, Look look) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(look, "look");
        Declarations.requireOpen("SlotItemLooks.declare(" + id + ")");
        SlotItemLookRegistry.declare(id, look);
    }
}
