package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * HUD item icon element — renders an ItemStack at configurable size.
 *
 * <p>Supports 8×8 (mini), 16×16 (native), and 24×24 (large) sizes via
 * JOML matrix scaling. Optionally shows count text and durability bar
 * using vanilla's {@code renderItemDecorations()}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKHudItem implements MKHudElement {

    private final int relX, relY;
    private final Supplier<ItemStack> item;
    private final int size;
    private final boolean showCount;
    private final boolean showDurability;
    private final @Nullable Runnable onRender;

    MKHudItem(int relX, int relY, Supplier<ItemStack> item,
              int size, boolean showCount, boolean showDurability,
              @Nullable Runnable onRender) {
        this.relX = relX;
        this.relY = relY;
        this.item = item;
        this.size = size;
        this.showCount = showCount;
        this.showDurability = showDurability;
        this.onRender = onRender;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, DeltaTracker dt) {
        if (onRender != null) onRender.run();

        ItemStack stack = item.get();
        if (stack == null || stack.isEmpty()) return;

        var mc = Minecraft.getInstance();
        int drawX = x + relX;
        int drawY = y + relY;

        if (size != 16) {
            // Scale to target size — vanilla renders items at 16×16
            float scaleFactor = size / 16.0f;
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) drawX, (float) drawY);
            graphics.pose().scale(scaleFactor, scaleFactor);
            graphics.renderItem(stack, 0, 0);
            if (showCount || showDurability) {
                graphics.renderItemDecorations(mc.font, stack, 0, 0);
            }
            graphics.pose().popMatrix();
        } else {
            // Native 16×16 — no scaling needed
            graphics.renderItem(stack, drawX, drawY);
            if (showCount || showDurability) {
                graphics.renderItemDecorations(mc.font, stack, drawX, drawY);
            }
        }
    }

    @Override
    public int getWidth() {
        return relX + size;
    }

    @Override
    public int getHeight() {
        return relY + size;
    }
}
