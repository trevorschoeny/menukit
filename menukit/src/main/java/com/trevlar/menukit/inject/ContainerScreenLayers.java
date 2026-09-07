package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one owner of <b>frame composition order</b> on a container screen — what
 * MenuKit draws, and where in vanilla's frame it lands. Every MenuKit draw on an
 * {@code AbstractContainerScreen} goes through one of the entry points here;
 * nothing else in the library registers a render hook on a container screen.
 *
 * <h2>The layer stack (bottom to top)</h2>
 *
 * <table>
 *   <tr><th>#</th><th>Layer</th><th>Drawn by</th></tr>
 *   <tr><td>0</td><td>screen background, container texture</td><td>vanilla ({@code extractBackground}, an earlier stratum)</td></tr>
 *   <tr><td>1</td><td><b>vanilla slots</b>: highlight, item, count, durability</td><td>vanilla ({@code extractSlots}, up to the first created slot)</td></tr>
 *   <tr><td>2</td><td><b>flow panels</b>: chrome + non-slot elements + created-slot frames</td><td>MenuKit — {@link #beforeSlot} / {@link #aboveSlots}</td></tr>
 *   <tr><td>3</td><td><b>created slots</b>: the same {@code extractSlot} call as layer 1</td><td>vanilla ({@code extractSlots}, the rest of the list)</td></tr>
 *   <tr><td>4</td><td>modal dim, <b>overlay panels</b></td><td>MenuKit — {@link #aboveSlots}</td></tr>
 *   <tr><td>5</td><td>carried item, cursor</td><td>vanilla ({@code extractCarriedItem}, next stratum)</td></tr>
 *   <tr><td>6</td><td>tooltips</td><td>vanilla ({@code extractTooltip})</td></tr>
 * </table>
 *
 * <h2>Why layers 1 and 3 are one vanilla pass with MenuKit in the middle</h2>
 *
 * Two things have to be true at once. <b>A panel hides exactly what it covers</b>
 * — pixel for pixel, the way paint does: a panel over half a vanilla slot hides
 * half of it, and the slot stays a normal slot underneath (§0058's opacity is
 * about input; visually a panel is simply on top). So panel chrome must draw
 * <em>after</em> the vanilla slots it may cover. And <b>vanilla draws every
 * slot</b>, created ones included, so a created slot's item must draw
 * <em>after</em> the chrome of the panel that hosts it. The one order that
 * satisfies both is vanilla slots, then panels, then created slots — and vanilla's
 * own {@code extractSlots} already walks {@code menu.slots} in exactly that order,
 * because MenuKit-Containers appends created slots last. So flow panels are drawn
 * from the {@code extractSlot} HEAD of the <em>first created slot</em> in the walk
 * ({@link #beforeSlot}); on a menu with no created slots to draw they fall to the
 * end of the slot pass ({@link #aboveSlots}), which is the historical
 * "panels above slots" order. Either way a created slot and a vanilla slot go
 * through the identical {@code extractSlot} call, which is what keeps them the
 * same thing to every other mod's slot hook.
 *
 * <p>A hovered created slot gets vanilla's back-highlight re-issued right after
 * the panels (vanilla drew it before the slot pass, under the chrome); the front
 * highlight is drawn by vanilla after the pass and needs nothing.
 *
 * <h2>Why this class exists</h2>
 *
 * Before it, order was an <em>emergent property of injection points</em>: three
 * registries each registered a {@code Screen.addRenderableOnly} renderable (which
 * vanilla iterates inside {@code extractContents}, before the slot pass), and a
 * fourth hook re-rendered one of them after the slot pass on recipe-book screens
 * only. Panels drew twice per frame on the inventory screen; panel-vs-slot order
 * differed by screen family; and nothing could be layered reliably because there
 * was no defined "last pass". Nobody owned the decision, so it drifted. This class
 * is the module that hides it (Parnas): a change to composition order is a change
 * here and nowhere else.
 *
 * <h2>The injection points</h2>
 *
 * All on {@code AbstractContainerScreen}, reached by every container screen family
 * (plain via {@code extractRenderState}, recipe-book via
 * {@code AbstractRecipeBookScreen.extractRenderState}, creative via its
 * {@code super} call): {@code extractContents} HEAD ({@link #frameStart}),
 * {@code extractSlot} HEAD ({@link #beforeSlot}), {@code extractContents} RETURN
 * ({@link #aboveSlots}). The contents points sit outside the {@code leftPos/topPos}
 * matrix push; the slot point sits inside it, so {@link #beforeSlot} pops to
 * absolute coordinates around the panel draw. All are before every
 * {@code nextStratum()} and before {@code extractTooltip}, so cursor and tooltip
 * layering, and same-frame tooltip flushing, are unchanged.
 *
 * <p>Non-container screens ({@link VanillaScreenPanelRegistry}) have no slot pass
 * and keep their renderable; they are a different surface with no layer question.
 *
 * <p>Internal — the mixin calls in; consumers never do.
 */
@ApiStatus.Internal
public final class ContainerScreenLayers {

    private ContainerScreenLayers() {}

    /** Whether layer 2 has been drawn this frame. Render thread only. */
    private static boolean flowDrawn = false;

    /** {@code extractContents} HEAD — a new frame; layer 2 not yet drawn. */
    public static void frameStart(AbstractContainerScreen<?> screen) {
        flowDrawn = false;
    }

    /**
     * {@code extractSlot} HEAD, for every slot vanilla is about to draw. The first
     * <em>created</em> slot of the frame is the layer 1 / layer 3 boundary: draw
     * layer 2 now, before vanilla draws it. Inside vanilla's slot pass the pose is
     * translated by {@code leftPos/topPos}; the panels draw in absolute screen
     * coordinates, so the translation is undone around them.
     */
    public static void beforeSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  Slot slot, int mouseX, int mouseY) {
        if (flowDrawn || !SlotScreenDispatcher.fireIsCreated(slot)) return;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        graphics.pose().pushMatrix();
        graphics.pose().translate(-acc.mk$getLeftPos(), -acc.mk$getTopPos());
        renderFlow(screen, graphics, mouseX, mouseY);
        graphics.pose().popMatrix();
        // Vanilla drew the hovered slot's back highlight before the slot pass; for
        // a created slot that is under the chrome just drawn, so draw it again on
        // top. (Runs translated, like vanilla's own call.)
        Slot hovered = acc.mk$getHoveredSlot();
        if (hovered != null && SlotScreenDispatcher.fireIsCreated(hovered)) {
            acc.mk$extractSlotHighlightBack(graphics);
        }
    }

    /**
     * {@code extractContents} RETURN — after vanilla's slot pass. Layer 2 if no
     * created slot triggered it (a menu with none to draw), then layer 4: the
     * modal dim (covers vanilla content AND every flow panel) and overlay
     * panels on top of it. Finally the end-of-frame park: a created slot that no
     * panel presented this frame goes off-screen, so vanilla neither draws nor
     * hit-tests it at a stale position next frame.
     */
    public static void aboveSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        if (!flowDrawn) renderFlow(screen, graphics, mouseX, mouseY);
        ScreenPanelRegistry.renderOverlayPanels(screen, graphics, mouseX, mouseY);
        SlotScreenDispatcher.fireEndFrame(screen);
    }

    /** Layer 2: menu-context flow panels, then slot-group panels on top of them. */
    private static void renderFlow(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY) {
        flowDrawn = true;
        ScreenPanelRegistry.renderFlowPanels(screen, graphics, mouseX, mouseY);
        SlotGroupPanelRegistry.renderMatchingPanels(screen, graphics, mouseX, mouseY);
    }
}
