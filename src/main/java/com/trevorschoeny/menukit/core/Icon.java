package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A sprite rendered at a fixed position and explicit size. No interaction.
 * The "show a picture" primitive of the component library.
 *
 * <p>Works in all three rendering contexts (inventory menus, HUDs, standalone
 * screens). Composable as the render portion of future icon-only Button
 * variants and state-indicating Toggle variants.
 *
 * <p>Two forms:
 * <ul>
 *   <li><b>Fixed sprite</b> — pass an {@link Identifier} directly.</li>
 *   <li><b>Supplier-driven sprite</b> — pass a {@code Supplier<Identifier>} to
 *   drive the sprite from consumer state (toggle states, gameplay events,
 *   flip-book animation).</li>
 * </ul>
 *
 * <p>Both forms share the render path — the fixed constructor wraps the
 * identifier in a one-shot supplier at construction time, so per-frame
 * render logic doesn't branch on which form was used.
 *
 * <h3>Hover-triggered tooltip support</h3>
 *
 * Icon tracks hover state internally to support hover-triggered tooltips
 * (via {@link #tooltip(Component)} / {@link #tooltip(Supplier)}). Hover
 * state is transient (recomputed each frame) and does not affect Icon's
 * structural contract as a render-only element. Icon remains non-interactive
 * — no {@code mouseClicked} override — even with hover tracking added.
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>No automatic sizing — sprite dimensions are explicit constructor
 *   parameters.</li>
 *   <li>No tint, alpha, or color modulation.</li>
 *   <li>No active/pressed state rendering.</li>
 *   <li>No timing — consumer-driven animation via supplier.</li>
 * </ul>
 *
 * @see PanelElement  The interface this implements
 * @see Button        Interactive button element (Phase 9 adds icon-only variant)
 * @see TextLabel     Text rendering primitive
 */
public class Icon implements PanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Supplier<Identifier> spriteSupplier;

    // Optional hover-triggered tooltip (post-construction configuration).
    private @Nullable Supplier<Component> tooltipSupplier;

    // Transient hover state — updated each frame. Does not make Icon
    // interactive; exists only to gate tooltip rendering.
    private boolean hovered = false;

    /**
     * Creates an Icon with a fixed sprite.
     */
    public Icon(int childX, int childY, int width, int height, Identifier sprite) {
        this(childX, childY, width, height, (Supplier<Identifier>) () -> sprite);
    }

    /**
     * Creates an Icon whose sprite is driven by a supplier.
     */
    public Icon(int childX, int childY, int width, int height,
                Supplier<Identifier> spriteSupplier) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.spriteSupplier = spriteSupplier;
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        // Update hover state — false on HUDs (no input dispatch).
        hovered = isHovered(ctx);

        ctx.graphics().blitSprite(
                RenderPipelines.GUI_TEXTURED,
                spriteSupplier.get(),
                ctx.originX() + childX, ctx.originY() + childY,
                width, height);

        // Hover-triggered tooltip — deferred to end-of-frame.
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        Minecraft.getInstance().font, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // mouseClicked, isVisible inherit their defaults from PanelElement.

    // ── Tooltip (optional post-construction configuration) ─────────────

    /** Attaches a hover-triggered tooltip with fixed text. Returns this for chaining. */
    public Icon tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Attaches a hover-triggered tooltip with supplier-driven text. Returns this for chaining. */
    public Icon tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    // ── Element Queries ────────────────────────────────────────────────

    /** Returns the sprite identifier the Icon would render right now. */
    public Identifier getCurrentSprite() {
        return spriteSupplier.get();
    }

    /** Returns whether the mouse is currently over this Icon (updated each frame). */
    public boolean isHovered() {
        return hovered;
    }
}
