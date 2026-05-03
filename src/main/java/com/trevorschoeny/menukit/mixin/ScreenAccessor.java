package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Phase 14d-3 — accessor mixin exposing {@code Screen.addWidget} and
 * {@code Screen.removeWidget}. Used by {@link
 * com.trevorschoeny.menukit.core.TextField} to register its wrapped
 * {@code EditBox} for input dispatch (children + narratables) WITHOUT
 * adding it to the renderables list.
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
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addWidget")
    <T extends GuiEventListener & NarratableEntry> T menuKit$addWidget(T widget);

    @Invoker("removeWidget")
    void menuKit$removeWidget(GuiEventListener widget);
}
