package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * HUD element that renders an item inside a hotbar-style slot background.
 *
 * <p>Uses vanilla's {@code hud/hotbar} sprite for the slot background,
 * matching the look and feel of the in-game hotbar. The item is rendered
 * at native 16×16 centered within the 20×22 slot.
 *
 * <p>This is a display-only element — no interaction. For interactive
 * slots, use a {@code MenuKitSlot} inside a {@code Panel} on a
 * {@code MenuKitScreenHandler}.
 *
 * <p>Implements {@link PanelElement}. Phase 8 may subsume this into a
 * hotbar-styled variant of the core {@code ItemDisplay}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudSlot implements PanelElement {

    // Vanilla hotbar sprite — 182x22, contains 9 slots of 20px each
    private static final Identifier HOTBAR_SPRITE =
            Identifier.withDefaultNamespace("hud/hotbar");

    // A single slot occupies 20x22 pixels within the hotbar sprite.
    // Item renders at (3, 3) within the slot for proper centering.
    public static final int SLOT_WIDTH = 20;
    public static final int SLOT_HEIGHT = 22;
    private static final int ITEM_OFFSET_X = 3; // center 16px item in 20px slot
    private static final int ITEM_OFFSET_Y = 3; // center 16px item in 22px slot

    private final int childX, childY;
    private final Supplier<ItemStack> itemSupplier;
    private final boolean showCount;
    private final boolean showDurability;
    private final @Nullable Runnable onRender;

    MKHudSlot(int childX, int childY, Supplier<ItemStack> itemSupplier,
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

        // Render a single slot from the hotbar sprite.
        // The hotbar sprite is 182x22. We render a 20x22 slice starting at
        // source (1, 0) — skipping the 1px left border of the full hotbar.
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                HOTBAR_SPRITE,
                182, 22,        // full sprite dimensions
                1, 0,           // source X, Y (skip 1px left border)
                slotX, slotY,   // destination X, Y
                SLOT_WIDTH, SLOT_HEIGHT  // destination size
        );

        // Render the item centered inside the slot
        ItemStack stack = itemSupplier.get();
        if (!stack.isEmpty()) {
            int itemX = slotX + ITEM_OFFSET_X;
            int itemY = slotY + ITEM_OFFSET_Y;
            graphics.renderItem(stack, itemX, itemY);
            if (showCount || showDurability) {
                graphics.renderItemDecorations(
                        Minecraft.getInstance().font, stack, itemX, itemY);
            }
        }
    }
}
