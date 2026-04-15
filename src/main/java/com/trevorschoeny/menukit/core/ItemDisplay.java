package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * An item icon rendered at a fixed position with optional count overlay
 * and durability bar. No interaction. Distinct from a slot — ItemDisplay
 * has no sync, no storage binding, no mutation; it only renders.
 *
 * <p>Works in all three rendering contexts. Render-only element.
 *
 * <p>Two forms for the item content:
 * <ul>
 *   <li><b>Fixed stack</b> — pass an {@link ItemStack} directly.</li>
 *   <li><b>Supplier-driven stack</b> — pass a {@code Supplier<ItemStack>}
 *   for stacks that change over time.</li>
 * </ul>
 *
 * <p>Items are always square. The {@code size} parameter is a single int;
 * width equals height. Defaults to {@link #DEFAULT_SIZE} (16 pixels),
 * matching vanilla's native item render size.
 *
 * <p>Count and durability overlays default to visible, matching vanilla
 * item rendering. Consumers wanting an icon-only display (no overlays)
 * pass {@code showCount=false, showDurability=false} explicitly.
 *
 * <p>Rendering delegates to vanilla's {@code GuiGraphics.renderItem} and
 * {@code renderItemDecorations} — MenuKit ships no custom visual of its
 * own for this element.
 *
 * @see PanelElement The interface this implements
 * @see Icon         The sprite-rendering primitive (non-item case)
 */
public class ItemDisplay implements PanelElement {

    /** Native item render size — vanilla renders items at this size. */
    public static final int DEFAULT_SIZE = 16;

    private final int childX;
    private final int childY;
    private final int size;
    private final Supplier<ItemStack> stackSupplier;
    private final boolean showCount;
    private final boolean showDurability;

    // ── Constructors: fixed stack ─────────────────────────────────────

    /**
     * Creates an ItemDisplay with a fixed stack at the default 16×16 size,
     * showing both count and durability overlays.
     */
    public ItemDisplay(int childX, int childY, ItemStack stack) {
        this(childX, childY, DEFAULT_SIZE, wrap(stack), true, true);
    }

    /**
     * Creates an ItemDisplay with a fixed stack, explicit size, and
     * explicit overlay visibility.
     *
     * @param childX         X position within panel content area
     * @param childY         Y position within panel content area
     * @param size           render size in pixels (width = height = size)
     * @param stack          the item stack
     * @param showCount      whether to render the count overlay
     * @param showDurability whether to render the durability bar
     */
    public ItemDisplay(int childX, int childY, int size, ItemStack stack,
                       boolean showCount, boolean showDurability) {
        this(childX, childY, size, wrap(stack), showCount, showDurability);
    }

    // ── Constructors: supplier-driven stack ───────────────────────────

    /**
     * Creates an ItemDisplay with a supplier-driven stack at the default
     * 16×16 size, showing both count and durability overlays.
     */
    public ItemDisplay(int childX, int childY, Supplier<ItemStack> stack) {
        this(childX, childY, DEFAULT_SIZE, stack, true, true);
    }

    /**
     * Creates an ItemDisplay with a supplier-driven stack, explicit size,
     * and explicit overlay visibility.
     *
     * @param childX         X position within panel content area
     * @param childY         Y position within panel content area
     * @param size           render size in pixels (width = height = size)
     * @param stack          supplier invoked each frame; must not return null
     * @param showCount      whether to render the count overlay
     * @param showDurability whether to render the durability bar
     */
    public ItemDisplay(int childX, int childY, int size, Supplier<ItemStack> stack,
                       boolean showCount, boolean showDurability) {
        this.childX = childX;
        this.childY = childY;
        this.size = size;
        this.stackSupplier = stack;
        this.showCount = showCount;
        this.showDurability = showDurability;
    }

    /** Wraps a fixed stack into a one-shot supplier, unifying the render path. */
    private static Supplier<ItemStack> wrap(ItemStack stack) {
        return () -> stack;
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return size; }
    @Override public int getHeight() { return size; }

    @Override
    public void render(RenderContext ctx) {
        ItemStack stack = stackSupplier.get();
        if (stack == null || stack.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var graphics = ctx.graphics();
        int drawX = ctx.originX() + childX;
        int drawY = ctx.originY() + childY;

        if (size != DEFAULT_SIZE) {
            // Vanilla renders items at 16×16. For non-default sizes, scale
            // through the pose matrix.
            float scale = size / (float) DEFAULT_SIZE;
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) drawX, (float) drawY);
            graphics.pose().scale(scale, scale);
            graphics.renderItem(stack, 0, 0);
            if (showCount || showDurability) {
                graphics.renderItemDecorations(mc.font, stack, 0, 0);
            }
            graphics.pose().popMatrix();
        } else {
            graphics.renderItem(stack, drawX, drawY);
            if (showCount || showDurability) {
                graphics.renderItemDecorations(mc.font, stack, drawX, drawY);
            }
        }
    }

    // mouseClicked, isVisible, isHovered inherit defaults from PanelElement.

    // ── Element Queries ────────────────────────────────────────────────

    /** Returns the item stack the ItemDisplay would render right now. */
    public ItemStack getCurrentStack() { return stackSupplier.get(); }

    /** Returns the render size in pixels (width and height are equal). */
    public int getSize() { return size; }

    /** Returns whether the count overlay renders. */
    public boolean showsCount() { return showCount; }

    /** Returns whether the durability bar renders. */
    public boolean showsDurability() { return showDurability; }
}
