package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Phase 14d-3 — accessor mixin exposing {@code Screen.addWidget} and
 * {@code Screen.removeWidget}. Used by {@link
 * com.trevorschoeny.menukit.core.TextField} to register its wrapped
 * {@code EditBox} for input dispatch (children + narratables) WITHOUT
 * adding it to the renderables list.
 *
 * <p>Phase 17 — also exposes {@code Screen.addRenderableOnly} so library
 * code outside the Screen subclass (e.g.
 * {@link com.trevorschoeny.menukit.inject.ScreenPanelRegistry}) can register
 * MK panel rendering as a vanilla {@code Renderable}. Renderables participate
 * in {@code Screen.render}'s renderables iteration, which fires BEFORE the
 * end-of-frame tooltip flush — so widgets calling
 * {@code GuiGraphics.setTooltipForNextFrame} during render get their tooltip
 * picked up in the same frame's flush.
 *
 * <p>Why not {@code addRenderableWidget} (or Fabric's {@code Screens.getButtons}
 * which has the same effect)? Both add the widget to the renderables list,
 * which means the widget renders during {@code super.render}. In MenuKit's
 * own screens, panel backgrounds render AFTER super.render — so a widget
 * added to renderables draws first, then gets covered by the panel
 * background. Visually invisible (but still hover-testable, hence the
 * confusing "cursor changes but no field visible" smoke result).
 *
 * <p>Solution: register the widget for input dispatch only (via
 * addWidget), then render it manually in the element's own
 * {@code render} method which runs AFTER panel backgrounds. The widget
 * still receives charTyped/keyPressed/focus dispatch via vanilla's
 * {@code Screen.children()} pipeline.
 */
@ApiStatus.Internal
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addWidget")
    <T extends GuiEventListener & NarratableEntry> T menuKit$addWidget(T widget);

    @Invoker("removeWidget")
    void menuKit$removeWidget(GuiEventListener widget);

    /**
     * Exposes {@code Screen.addRenderableOnly} so library code outside the
     * Screen hierarchy can register a {@link Renderable} that participates
     * in the renderables iteration. Phase 17 — needed so MK panel rendering
     * fires before the end-of-frame tooltip flush.
     */
    @Invoker("addRenderableOnly")
    <T extends Renderable> T menuKit$addRenderableOnly(T renderable);
}
