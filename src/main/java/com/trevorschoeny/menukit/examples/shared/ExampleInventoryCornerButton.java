package com.trevorschoeny.menukit.examples.shared;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOriginFns;
import com.trevorschoeny.menukit.inject.ScreenPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Phase 10 injection example: Pattern 3 — panel at vanilla-screen-bounds corner.
 * Shared state for
 * {@link com.trevorschoeny.menukit.examples.injection.ExampleInventoryCornerButtonMixin}.
 *
 * <h3>Why this lives in a separate package</h3>
 *
 * Fabric mixin's class-load rules forbid non-mixin classes from living inside
 * a declared mixin package. {@code menukit-examples.mixins.json} claims
 * {@code com.trevorschoeny.menukit.examples.injection} as its mixin package,
 * so plain helper classes like this one have to live elsewhere. The
 * {@code examples.shared} sibling package is the home for non-mixin state
 * referenced by the injection examples.
 *
 * <p>Consumer mods writing their own injection-pattern decorations follow the
 * same rule: keep the Panel and adapter state in a plain class outside the
 * mixin package. See the design doc's "What ships" section.
 *
 * <h3>Known caveat: creative-inventory tab overlap</h3>
 *
 * The chosen origin {@code fromScreenTopRight(11, -4, -16)} places the button
 * at the top-right of the vanilla frame. In survival {@link
 * net.minecraft.client.gui.screens.inventory.InventoryScreen} this sits above
 * the "Inventory" title cleanly. In {@link
 * net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}
 * the creative tab row occupies the same region — the button renders but
 * visually overlaps whichever tab is drawn last (creative renders tab icons
 * AFTER super.render returns, covering our button). Clicks on the tab area
 * hit the tab, not the button.
 *
 * <p>This is a <b>positioning choice</b> for this example, not a library
 * bug. Real consumer mods decorating creative inventory should either
 * (a) pick a position that doesn't collide with vanilla tabs (e.g.,
 * {@code belowSlotGrid(...)}), (b) render into a different screen subclass,
 * or (c) add a supplementary {@code @Mixin(CreativeModeInventoryScreen.class)}
 * at the render TAIL so the button draws on top of tabs, plus one at
 * mouseClicked HEAD to intercept clicks before vanilla's tab dispatch.
 *
 * <p><b>Dev-only.</b> See {@code DevOnlyExampleMixinsPlugin}.
 */
public final class ExampleInventoryCornerButton {

    private ExampleInventoryCornerButton() {}

    /**
     * The panel rendered in the corner. One 11×11 button that posts to chat
     * on click. Chat rather than {@code setOverlayMessage} because the overlay
     * renders on the hotbar strip, which is hidden while a screen is open —
     * and this button only appears inside a screen.
     */
    public static final Panel PANEL = new Panel(
            "menukit_example_corner_button",
            List.of(new Button(
                    0, 0, 11, 11,
                    Component.literal("!"),
                    btn -> Minecraft.getInstance().gui.getChat().addMessage(
                            Component.literal("[MenuKit example] Corner button clicked"))
            ))
    );

    /** Shared adapter — the mixin reads this for both render and click dispatch. */
    public static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
            PANEL,
            // Right-anchored: 4px inset from the right edge of the frame,
            // 16px above the top of the frame — sits above the inventory title.
            ScreenOriginFns.fromScreenTopRight(/* panelWidth */ 11,
                                               /* dx */ -4,
                                               /* dy */ -16)
    );

    /** Bundle the vanilla screen fields into a {@link ScreenBounds}. */
    public static ScreenBounds bounds(int leftPos, int topPos, int imageWidth, int imageHeight) {
        return new ScreenBounds(leftPos, topPos, imageWidth, imageHeight);
    }
}
