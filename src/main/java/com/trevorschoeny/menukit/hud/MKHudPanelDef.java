package com.trevorschoeny.menukit.hud;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelStyle;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Immutable definition of a HUD panel — created at mod init, stored in
 * MenuKit's registry, rendered each frame by the HUD dispatch.
 *
 * <p>Holds a list of {@link PanelElement}s — the same element abstraction
 * used in inventory menus and standalone screens. HUD-specific machinery
 * (anchor, screen-open visibility, per-frame dispatch) lives on this def;
 * the elements themselves are context-neutral.
 *
 * <p><b>Positioning mode.</b> Exactly one of {@code region} or
 * {@code anchor}+{@code offsetX}+{@code offsetY} drives placement:
 * when {@code region != null}, the HUD dispatch resolves via
 * {@link com.trevorschoeny.menukit.core.RegionMath#resolveHud} with a
 * stacking prefix from
 * {@link com.trevorschoeny.menukit.inject.RegionRegistry#axialPrefix(MKHudPanelDef, HudRegion)};
 * otherwise it falls back to {@code anchor.resolve(...)}. Builder validation
 * guarantees at most one is set. See M5 design doc §4.3.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public record MKHudPanelDef(
        String name,
        MKHudAnchor anchor,
        int offsetX,
        int offsetY,
        int padding,
        boolean autoSize,
        int width,
        int height,
        PanelStyle style,
        List<PanelElement> elements,
        Supplier<Boolean> showWhen,
        boolean hideInScreen,
        @Nullable HudRenderCallback onRender,
        @Nullable HudRegion region
) {
    /**
     * Callback for custom rendering on a HUD panel.
     */
    @FunctionalInterface
    public interface HudRenderCallback {
        void render(GuiGraphics graphics, int x, int y, int width, int height,
                    net.minecraft.client.DeltaTracker deltaTracker);
    }

    /**
     * Computes the panel's size from its children + padding.
     * Used for auto-sized panels. Size = max(childX + width, childY + height)
     * across all elements, plus padding on both sides.
     */
    public int[] computeSize() {
        if (!autoSize || elements.isEmpty()) {
            return new int[]{width, height};
        }

        int maxRight = 0;
        int maxBottom = 0;

        for (PanelElement el : elements) {
            maxRight = Math.max(maxRight, el.getChildX() + el.getWidth());
            maxBottom = Math.max(maxBottom, el.getChildY() + el.getHeight());
        }

        return new int[]{
                maxRight + padding * 2,
                maxBottom + padding * 2
        };
    }
}
