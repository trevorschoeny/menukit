package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code Screen.addWidget} and {@code Screen.removeWidget}.
 * {@link com.trevlar.menukit.api.element.TextField} uses them to register its
 * wrapped {@code EditBox} for input dispatch (children and narratables) without
 * adding it to the renderables list.
 *
 * <p>Also exposes {@code Screen.addRenderableOnly}, so code outside the Screen
 * subclass (such as {@link com.trevlar.menukit.inject.ScreenPanelRegistry}) can
 * register panel rendering as a vanilla {@code Renderable}. Renderables run
 * before the end-of-frame tooltip flush, so a widget that calls
 * {@code GuiGraphicsExtractor.setTooltipForNextFrame} while rendering gets its
 * tooltip shown in the same frame.
 *
 * <p>Why not {@code addRenderableWidget}, or Fabric's {@code Screens.getButtons},
 * which does the same? Both put the widget in the renderables list, so it renders
 * during {@code super.render}. MenuKit's own screens draw panel backgrounds after
 * super.render, so the widget would be drawn and then covered: invisible, but
 * still hoverable, which shows up as "the cursor changes but no field is visible".
 *
 * <p>So the widget is registered for input only (via addWidget) and the element
 * renders it itself, after the panel backgrounds. It still gets
 * charTyped/keyPressed/focus dispatch through vanilla's {@code Screen.children()}.
 */
@ApiStatus.Internal
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addWidget")
    <T extends GuiEventListener & NarratableEntry> T mk$addWidget(T widget);

    @Invoker("removeWidget")
    void mk$removeWidget(GuiEventListener widget);

    /** Lets code outside the Screen hierarchy add a {@link Renderable}, which runs before the tooltip flush. */
    @Invoker("addRenderableOnly")
    <T extends Renderable> T mk$addRenderableOnly(T renderable);
}
