package com.trevorschoeny.menukit.examples.shared;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.TextLabel;
import com.trevorschoeny.menukit.inject.ScreenBounds;
import com.trevorschoeny.menukit.inject.ScreenOriginFns;
import com.trevorschoeny.menukit.inject.ScreenPanelAdapter;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Phase 10 injection example: Pattern 1 — input-intercept mixin + pre-declared
 * panel with supplier-driven visibility. Shared state for
 * {@link com.trevorschoeny.menukit.examples.injection.ExampleKeybindTriggeredPanelMixin}
 * and
 * {@link com.trevorschoeny.menukit.examples.injection.ExampleKeybindTriggeredPanelRecipeBookMixin}.
 *
 * <p>Lives in {@code examples.shared} rather than {@code examples.injection}
 * because Fabric mixin's class-load rules forbid non-mixin classes inside a
 * declared mixin package. See {@link ExampleInventoryCornerButton} for the
 * full rule explanation.
 *
 * <p>Demonstrates the Phase 8/9 state-ownership pattern (Toggle.linked
 * precedent) extended to Panel visibility: consumer holds the state; the
 * library reads via supplier.
 *
 * <h3>Known caveat: z-order in survival inventory</h3>
 *
 * The text label renders via the primary {@code AbstractContainerScreen.render}
 * TAIL injection. In subclasses that continue rendering <em>after</em>
 * super.render returns (notably {@code AbstractRecipeBookScreen.render},
 * which draws the recipe-book widget on top), the text can be partially or
 * fully occluded. The toggle still works mechanically (press {@code P} and
 * the supplier flips), but the rendered text may appear behind vanilla's
 * later render passes.
 *
 * <p>This is a <b>positioning/ordering choice</b> for this example, not a
 * library bug. Real consumer mods wanting a decoration that paints on top
 * of the subclass's render work add a supplementary render mixin at the
 * subclass's declaration point — e.g., {@code
 * @Mixin(AbstractRecipeBookScreen.class) ... @Inject(method = "render",
 * at = @At("TAIL"))} — so their panel renders after the subclass's later
 * draw calls. See the corner-button example's {@code
 * ExampleInventoryCornerButtonRecipeBookRenderMixin} for the shape.
 *
 * <p><b>Dev-only.</b> See {@code DevOnlyExampleMixinsPlugin}.
 */
public final class ExampleKeybindTriggeredPanel {

    private ExampleKeybindTriggeredPanel() {}

    /**
     * Visibility state. Volatile because mixins on different screen classes
     * may read/write from the client thread at different ticks.
     */
    public static volatile boolean visible = false;

    /** Pre-declared panel. Supplier-driven visibility reads {@link #visible}. */
    public static final Panel PANEL = new Panel(
            "menukit_example_keybind_triggered",
            List.of(new TextLabel(0, 0, Component.literal("Hello from Phase 10 (P to toggle)")))
    ).showWhen(() -> visible);

    /** Shared adapter. Positioned just above the screen frame's top-left corner. */
    public static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
            PANEL,
            ScreenOriginFns.fromScreenTopLeft(/* dx */ 4, /* dy */ -24)
    );

    /** Bundle the vanilla screen fields into a {@link ScreenBounds}. */
    public static ScreenBounds bounds(int leftPos, int topPos, int imageWidth, int imageHeight) {
        return new ScreenBounds(leftPos, topPos, imageWidth, imageHeight);
    }
}
