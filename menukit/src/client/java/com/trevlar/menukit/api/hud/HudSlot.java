package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A HUD element that draws an item in a hotbar slot. The background is a slice of
 * vanilla's {@code hud/hotbar} sprite, and the item is drawn at 16x16 inside the
 * 20x22 slot.
 *
 * <p>Display only. For a slot the player can use, put a {@code CreatedSlot} in a
 * {@code Panel} on a {@code CustomContainerMenu}.
 */
public class HudSlot implements PanelElement {

    // Vanilla's hotbar sprite: 182x22, nine slots of 20 px
    private static final Identifier HOTBAR_SPRITE =
            Identifier.withDefaultNamespace("hud/hotbar");

    // One slot is 20x22 of the sprite; the item sits at (3, 3) in it.
    public static final int SLOT_WIDTH = 20;
    public static final int SLOT_HEIGHT = 22;
    private static final int ITEM_OFFSET_X = 3; // center 16px item in 20px slot
    private static final int ITEM_OFFSET_Y = 3; // center 16px item in 22px slot

    private final int childX, childY;
    private final Supplier<ItemStack> itemSupplier;
    private final boolean showCount;
    private final boolean showDurability;
    private final @Nullable Runnable onRender;

    HudSlot(int childX, int childY, Supplier<ItemStack> itemSupplier,
              boolean showCount, boolean showDurability,
              @Nullable Runnable onRender) {
        this.childX = childX;
        this.childY = childY;
        this.itemSupplier = itemSupplier;
        this.showCount = showCount;
        this.showDurability = showDurability;
        this.onRender = onRender;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return SLOT_WIDTH; }
    @Override public int getHeight() { return SLOT_HEIGHT; }

    @Override
    public void render(RenderContext ctx) {
        if (onRender != null) onRender.run();

        var graphics = ctx.graphics();
        int slotX = ctx.originX() + childX;
        int slotY = ctx.originY() + childY;

        // A 20x22 slice of the 182x22 hotbar sprite from (1, 0), skipping the
        // hotbar's 1 px left border.
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                HOTBAR_SPRITE,
                182, 22,        // full sprite dimensions
                1, 0,           // source X, Y (skip 1px left border)
                slotX, slotY,   // destination X, Y
                SLOT_WIDTH, SLOT_HEIGHT  // destination size
        );

        ItemStack stack = itemSupplier.get();
        if (!stack.isEmpty()) {
            int itemX = slotX + ITEM_OFFSET_X;
            int itemY = slotY + ITEM_OFFSET_Y;
            graphics.item(stack, itemX, itemY);
            if (showCount || showDurability) {
                graphics.itemDecorations(
                        Minecraft.getInstance().font, stack, itemX, itemY);
            }
        }
    }
}
