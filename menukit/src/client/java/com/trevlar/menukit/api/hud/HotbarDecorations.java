package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.HotbarDecorationRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What a mod draws on the HUD hotbar's slots (8.0.0), so no mod mixes into vanilla's hotbar:
 * the HUD's counterpart of {@link com.trevlar.menukit.api.panel.SlotDecorations}. The hotbar
 * draws in three steps, and a decoration goes in the middle one:
 * <ol>
 *   <li>Vanilla's bar and selection sprites.</li>
 *   <li><b>Under</b>: every hotbar slot's decorations, all nine slots before any item, so a
 *       decoration that reaches past its slot (icons in an arc above it, a stack moving into
 *       it) is still covered by every slot's own item.</li>
 *   <li>The nine items, their counts and durability bars, each left out of a slot that a
 *       {@link #hideItem} rule hides.</li>
 * </ol>
 *
 * <pre>{@code
 * HotbarDecorations.under("mymod:ring", (g, hotbarSlot, stack, x, y) -> {
 *     if (hasRing(hotbarSlot)) drawRingAbove(g, hotbarSlot, x + 8, y + 8);
 * });
 * // While a turn moves the held stack, the ring draws it, and the slot draws nothing.
 * HotbarDecorations.hideItem("mymod:turn", (hotbarSlot, stack) -> turning(hotbarSlot));
 * }</pre>
 *
 * <ul>
 *   <li><b>Every slot, every frame</b> the hotbar draws, empty or not. Not while vanilla
 *       shows the spectator menu in the hotbar's place.</li>
 *   <li><b>All of them draw</b>, in id order, so two mods' decorations on one slot both show.
 *       A slot's item is hidden when any rule hides it.</li>
 *   <li>{@code stack} is the slot's own stack, also when a rule hides it, so a decoration can
 *       draw that stack somewhere else.</li>
 *   <li>A decoration or rule that throws is logged once and skipped from then on.</li>
 * </ul>
 *
 * <p>Beside the hotbar rather than on it, a mod claims room with a {@link HudPanel} in a side
 * region ({@code region(OutsideRegion.RIGHT_ALIGN_BOTTOM)}), where every mod's panels stack.
 * On a container screen, draw on the hotbar's slots with
 * {@link com.trevlar.menukit.api.panel.SlotDecorations}.
 *
 * <p>Declare from your client initializer: decorations freeze with every other declaration.
 */
public final class HotbarDecorations {

    private HotbarDecorations() {}

    /** What one mod draws on a hotbar slot. */
    @FunctionalInterface
    public interface Decoration {
        /**
         * Draws on hotbar slot {@code hotbarSlot} (0 to 8) this frame, or nothing.
         * {@code (x, y)} is the top left of the slot's 16 px item box, where vanilla draws
         * the item.
         */
        void draw(GuiGraphicsExtractor graphics, int hotbarSlot, ItemStack stack, int x, int y);
    }

    /** When a hotbar slot draws without its item. */
    @FunctionalInterface
    public interface HideRule {
        /** Whether hotbar slot {@code hotbarSlot} (0 to 8), holding {@code stack}, draws no item, count or bar this frame. */
        boolean hides(int hotbarSlot, ItemStack stack);
    }

    /**
     * Declares a decoration drawn under the hotbar's items, its id namespaced and unique among
     * every hotbar decoration and rule ({@code "mymod:ring"}).
     *
     * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
     */
    public static void under(String id, Decoration decoration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(decoration, "decoration");
        Declarations.requireOpen("HotbarDecorations.under(" + id + ")");
        HotbarDecorationRegistry.under(id, decoration);
    }

    /**
     * Declares a rule that hides a hotbar slot's item, its id namespaced and unique among every
     * hotbar decoration and rule ({@code "mymod:turn"}).
     *
     * @throws IllegalStateException after declarations froze, or when {@code id} is already declared
     */
    public static void hideItem(String id, HideRule rule) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        Declarations.requireOpen("HotbarDecorations.hideItem(" + id + ")");
        HotbarDecorationRegistry.hideItem(id, rule);
    }
}
