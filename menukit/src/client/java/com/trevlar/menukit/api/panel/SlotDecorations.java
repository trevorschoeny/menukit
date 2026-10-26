package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.SlotDecorationRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * What a mod draws on a container screen's slots (7.5.0), under the item or over it, so no
 * mod mixes into the slot's draw. A slot draws in three layers:
 * <ol>
 *   <li><b>Under</b>: after the slot's background (its empty-slot icon, a drag's tint),
 *       before the item. A ghost of the item that goes there, a tint behind it.</li>
 *   <li><b>The item</b>, as vanilla draws it or as a {@link SlotItemLooks} look says
 *       (animated, or another stack drawn in its place).</li>
 *   <li><b>Over</b>: after the item's count and durability bar. Icons, corner marks, a grey
 *       fill over the slot.</li>
 * </ol>
 *
 * <pre>{@code
 * SlotDecorations.over("mymod:padlock", new SlotDecorations.Decoration() {
 *     public void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
 *         if (isLocked(slot)) g.blit(RenderPipelines.GUI_TEXTURED, PADLOCK, x + 10, y + 1, 0, 0, 5, 6, 5, 6);
 *     }
 *     public List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int mouseX, int mouseY) {
 *         return isLocked(slot) && mouseX >= 10 && mouseX < 15 && mouseY >= 1 && mouseY < 7
 *                 ? List.of(Component.literal("Locked")) : null;
 *     }
 * });
 * }</pre>
 *
 * <ul>
 *   <li><b>Every slot, every frame</b>, empty or not, on every container screen: vanilla's
 *       slots, created ones, the slots a panel shows (inside the panel's clip, scrolled with
 *       it) and the creative screen's, whose slots are the creative menu's own wrappers.
 *       {@code stack} is what the slot draws, which during a drag is the stack it will get.</li>
 *   <li><b>All of them draw</b>, each layer in id order, so two mods' marks on one slot both
 *       show. Mind where another mod draws: the corners are the usual places.</li>
 *   <li><b>Tooltips</b>: while the mouse is on a slot, its decorations are asked in id order
 *       (under, then over) for a tooltip at the mouse; the first answer shows in place of the
 *       item's own tooltip.</li>
 *   <li>A decoration that throws is logged once and skipped from then on.</li>
 * </ul>
 *
 * <p>Declare from your client initializer: decorations freeze with every other declaration.
 */
public final class SlotDecorations {

    private SlotDecorations() {}

    /** What one mod draws on a slot, and the tooltip it answers there. */
    @FunctionalInterface
    public interface Decoration {
        /**
         * Draws on {@code slot} this frame, or nothing. {@code (x, y)} is the top left of the
         * slot's 16 px item box, where vanilla draws the item; the slot's 18 px frame starts a
         * pixel up and left of it.
         */
        void draw(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y);

        /**
         * The tooltip with the mouse at {@code (mouseX, mouseY)} on the slot, counted from the
         * item box's top left (so -1 to 16 across the slot's frame), or {@code null} for none.
         */
        default @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                  int mouseX, int mouseY) {
            return null;
        }
    }

    /**
     * Declares a decoration drawn under the item, its id namespaced and unique among every
     * decoration ({@code "mymod:ghost"}).
     *
     * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
     */
    public static void under(String id, Decoration decoration) {
        declare(id, true, decoration);
    }

    /**
     * Declares a decoration drawn over the item, its count and its durability bar, its id
     * namespaced and unique among every decoration ({@code "mymod:padlock"}).
     *
     * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
     */
    public static void over(String id, Decoration decoration) {
        declare(id, false, decoration);
    }

    private static void declare(String id, boolean under, Decoration decoration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(decoration, "decoration");
        Declarations.requireOpen("SlotDecorations." + (under ? "under" : "over") + "(" + id + ")");
        SlotDecorationRegistry.declare(id, under, decoration);
    }
}
