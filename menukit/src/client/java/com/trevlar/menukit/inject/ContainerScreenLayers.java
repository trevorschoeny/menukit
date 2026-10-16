package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

/**
 * The container-screen instance of the one {@link LayerPlan} (FLOW, POPOVER, dim, OVERLAY):
 * the one owner of <b>frame composition order</b> on a container screen, meaning
 * what MenuKit draws and where in vanilla's frame it lands. Every MenuKit draw on an
 * {@code AbstractContainerScreen} goes through one of the entry points here;
 * nothing else in the library registers a render hook on a container screen.
 *
 * <h2>The layer stack (bottom to top)</h2>
 *
 * <table>
 *   <tr><th>#</th><th>Layer</th><th>Drawn by</th></tr>
 *   <tr><td>0</td><td>screen background, container texture</td><td>vanilla ({@code extractBackground}, an earlier stratum)</td></tr>
 *   <tr><td>1</td><td><b>vanilla slots</b>: highlight, item, count, durability</td><td>vanilla ({@code extractSlots}, up to the first created slot)</td></tr>
 *   <tr><td>2</td><td><b>flow panels</b>, then <b>popover panels</b> of every host: chrome + non-slot elements + created-slot frames</td><td>MenuKit: {@link #beforeSlot} / {@link #aboveSlots} (a standalone screen's own flow panels draw earlier, at its {@code extractContents} head, below every slot)</td></tr>
 *   <tr><td>3</td><td><b>created slots</b>: the same {@code extractSlot} call as layer 1</td><td>vanilla ({@code extractSlots}, the rest of the list)</td></tr>
 *   <tr><td>4</td><td>modal dim, <b>overlay panels</b> of every host on the screen</td><td>MenuKit: {@link #aboveSlots}</td></tr>
 *   <tr><td>5</td><td>carried item, cursor</td><td>vanilla ({@code extractCarriedItem}, next stratum)</td></tr>
 *   <tr><td>6</td><td>tooltips</td><td>vanilla ({@code extractTooltip})</td></tr>
 * </table>
 *
 * <h2>Why layers 1 and 3 are one vanilla pass with MenuKit in the middle</h2>
 *
 * Two things have to be true at once. <b>A panel hides exactly what it covers</b>,
 * pixel for pixel, the way paint does: a panel over half a vanilla slot hides
 * half of it, and the slot stays a normal slot underneath (panel opacity is about
 * input; visually a panel is simply on top). So panel chrome must draw
 * <em>after</em> the vanilla slots it may cover. And <b>vanilla draws every
 * slot</b>, created ones included, so a created slot's item must draw
 * <em>after</em> the chrome of the panel that hosts it. The one order that
 * satisfies both is vanilla slots, then panels, then created slots, and vanilla's
 * own {@code extractSlots} already walks {@code menu.slots} in exactly that order,
 * because a slot library appends created slots last. So flow panels are drawn
 * from the {@code extractSlot} HEAD of the <em>first created slot</em> in the walk
 * ({@link #beforeSlot}); on a menu with no created slots to draw they fall to the
 * end of the slot pass ({@link #aboveSlots}), the older "panels above slots" order. Either way a created slot and a vanilla slot go
 * through the identical {@code extractSlot} call, which is what keeps them the
 * same thing to every other mod's slot hook.
 *
 * <h2>Popovers in layer 2</h2>
 *
 * A popover ({@code Panel.Builder.popover()}) draws after every flow panel and in
 * the same layer, so it covers them, and before the created slots, so its own
 * created slots (Hotbar Pockets' pocket row) draw on its background exactly as a
 * flow panel's do. The one thing layer 2 cannot do is put a popover above another
 * panel's <em>created slot</em>: vanilla draws every created slot in layer 3, in
 * {@code menu.slots} order. Vanilla slots under a popover are covered (layer 1), and
 * a covered slot is inert either way (the claim).
 *
 * <p>Known limit: a flow panel's created slot under a popover shows through it.
 * Split layer 3 by the presenting panel's layer (popover panels' created slots
 * last) if a screen ever puts one under the other.
 *
 * <p>A hovered created slot gets vanilla's back-highlight re-issued right after
 * the panels (vanilla drew it before the slot pass, under the chrome); the front
 * highlight is drawn by vanilla after the pass and needs nothing.
 *
 * <h2>A slot shown in part (7.2.0)</h2>
 *
 * A slot a panel places across a clip's edge (a {@code ScrollContainer}'s viewport)
 * is drawn by vanilla, in layer 1 or 3, scissored to the clip it was placed under
 * ({@link SlotClips}), and so are vanilla's hover highlights on it. A slot no clip
 * cuts is drawn exactly as before.
 *
 * <h2>Why this class exists</h2>
 *
 * Without it, order fell out of wherever the hooks happened to be: three
 * registries each added a {@code Screen.addRenderableOnly} renderable (which
 * vanilla runs inside {@code extractContents}, before the slot pass), and a
 * fourth hook re-rendered one of them after the slot pass on recipe-book screens
 * only. Panels drew twice per frame on the inventory screen, panel-versus-slot
 * order differed by screen family, and nothing could be layered reliably because
 * there was no defined last pass. Now a change to composition order is a change
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
 * <p>Non-container screens have no slot pass: {@link ScreenPanelRegistry} composes
 * the whole plan in one renderable there.
 *
 * <p>Internal: the mixin calls in, consumers never do.
 */
@ApiStatus.Internal
public final class ContainerScreenLayers {

    private ContainerScreenLayers() {}

    /** Whether layer 2 has been drawn this frame. Render thread only. */
    private static boolean flowDrawn = false;

    /** Whether the slot (or highlight) vanilla is drawing now is scissored to its clip. Render thread only. */
    private static boolean slotClipped = false;

    /** At {@code extractContents} HEAD: a new frame, layer 2 not yet drawn. */
    public static void frameStart(AbstractContainerScreen<?> screen) {
        flowDrawn = false;
    }

    /**
     * At {@code extractSlot} HEAD, for every slot vanilla is about to draw. The first
     * <em>created</em> slot of the frame is the layer 1 / layer 3 boundary: draw
     * layer 2 now, before vanilla draws it. Inside vanilla's slot pass the pose is
     * translated by {@code leftPos/topPos}; the panels draw in absolute screen
     * coordinates, so the translation is undone around them.
     */
    public static void beforeSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  Slot slot, int mouseX, int mouseY) {
        if (!flowDrawn && SlotScreenDispatcher.fireIsCreated(slot)) {
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
        // The slot itself, clipped to what its panel shows of it (SlotClips): a slot
        // across a ScrollContainer's edge draws only the part inside.
        slotClipped = SlotClips.push(graphics, slot);
    }

    /** At {@code extractSlot} RETURN: ends the slot's clip, if {@link #beforeSlot} began one. */
    public static void afterSlot(GuiGraphicsExtractor graphics) {
        if (slotClipped) graphics.disableScissor();
        slotClipped = false;
    }

    /**
     * At the HEAD of vanilla's hover highlights (back and front, and the back one
     * re-issued above): clipped to the hovered slot's clip, as the slot is. Never
     * inside a slot's own draw, so it shares that draw's flag.
     */
    public static void beforeHighlight(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        slotClipped = SlotClips.push(graphics, ((AbstractContainerScreenAccessor) screen).mk$getHoveredSlot());
    }

    /** At the highlights' RETURN: ends the clip {@link #beforeHighlight} began. */
    public static void afterHighlight(GuiGraphicsExtractor graphics) {
        afterSlot(graphics);
    }

    /**
     * At {@code extractContents} RETURN, after vanilla's slot pass. Layer 2 if no
     * created slot triggered it (a menu with none to draw), then layer 4: the
     * modal dim (over vanilla content and every flow panel) and the overlay
     * panels on top of it. Finally the end-of-frame park: a created slot that no
     * panel presented this frame goes off-screen, so vanilla neither draws nor
     * hit-tests it at a stale position next frame, and loses any clip it had.
     */
    public static void aboveSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        if (!flowDrawn) renderFlow(screen, graphics, mouseX, mouseY);
        ScreenPanelRegistry.renderOverlay(screen, graphics, mouseX, mouseY);
        SlotScreenDispatcher.fireEndFrame(screen);
        SlotClips.frameEnd();
    }

    /** Layer 2: the FLOW layer of the screen's registry hosts (container adapters, then anchors), then every host's POPOVER layer. */
    private static void renderFlow(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY) {
        flowDrawn = true;
        ScreenPanelRegistry.renderFlow(screen, graphics, mouseX, mouseY);
    }
}
