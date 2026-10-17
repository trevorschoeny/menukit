package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import com.trevlar.menukit.core.OverlayDraws;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The container-screen instance of the one {@link LayerPlan} (FLOW, POPOVER, dim, OVERLAY):
 * the one owner of <b>frame composition order</b> on a container screen, meaning
 * what MenuKit draws and where in vanilla's frame it lands. Every MenuKit draw on an
 * {@code AbstractContainerScreen} goes through one of the entry points here;
 * nothing else in the library registers a render hook on a container screen.
 *
 * <h2>The rule (7.3.0)</h2>
 *
 * <b>A slot a panel shows draws right after that panel's own drawing, and a panel
 * above covers the slots of the panels below it.</b> Menu order no longer decides
 * it. A slot no panel shows is vanilla's, drawn where vanilla draws it, and a panel
 * over it covers it.
 *
 * <h2>The layer stack (bottom to top)</h2>
 *
 * <table>
 *   <tr><th>#</th><th>Layer</th><th>Drawn by</th></tr>
 *   <tr><td>0</td><td>screen background, container texture</td><td>vanilla ({@code extractBackground}, an earlier stratum)</td></tr>
 *   <tr><td>1</td><td><b>slots no panel shows</b>: highlight, item, count, durability</td><td>vanilla ({@code extractSlots}; the slots panels show are left out)</td></tr>
 *   <tr><td>2</td><td><b>flow panels</b>, then <b>popover panels</b> of every host, <b>each followed by the slots it shows</b></td><td>MenuKit: {@link #aboveSlots} (or {@link #beforeSlot}, below); each slot through the screen's own {@code extractSlot}</td></tr>
 *   <tr><td>3</td><td>created slots no panel shows (a slot library that does not report its slots)</td><td>vanilla ({@code extractSlots}, after the first such slot)</td></tr>
 *   <tr><td>4</td><td>modal dim, <b>overlay panels</b> of every host, each followed by its slots</td><td>MenuKit: {@link #aboveSlots}</td></tr>
 *   <tr><td>5</td><td>carried item, cursor</td><td>vanilla ({@code extractCarriedItem}, next stratum)</td></tr>
 *   <tr><td>6</td><td>tooltips</td><td>vanilla ({@code extractTooltip})</td></tr>
 * </table>
 *
 * <h2>How a frame goes</h2>
 *
 * <ol>
 *   <li><b>Place first</b> ({@link #frameStart}, at {@code extractContents} HEAD,
 *       before vanilla works out the hovered slot and before its slot pass). Every
 *       panel drawn after the slot pass is laid out once into a scratch render state
 *       that is thrown away: nothing it draws is seen, but its slot elements write
 *       their slots' positions and report them ({@code SlotClip.shown}), and
 *       {@link PresentedSlots} files each slot under its panel. So hover and the
 *       slot pass use this frame's positions, and the panel's slots are known before
 *       vanilla's pass. Slots no panel reported stay parked or where vanilla put
 *       them, as before.</li>
 *   <li><b>Vanilla's pass leaves the presented slots out</b>
 *       ({@link #drawsInVanillaPass}: the walk is wrapped, not replaced), and its back
 *       and front hover highlights too when the hovered slot is presented
 *       ({@link #highlightsInVanillaPass}). Unpresented slots draw exactly as
 *       before.</li>
 *   <li><b>Each panel, then its slots.</b> The panels draw in the one draw order;
 *       each one's host calls {@link PresentedSlots#drawAfter} between its elements
 *       and their open popovers. A slot goes through the screen's own
 *       {@code extractSlot} (an invoker), so every other mod's {@code extractSlot}
 *       mixin still marks it and vanilla still draws its item, count and
 *       decorations. The hovered one draws between vanilla's back and front
 *       highlights, clipped as the slot is. Overlay panels do the same after the
 *       dim, so a slot in an overlay panel draws on it, in the same frame.</li>
 *   <li><b>Park</b> ({@link #aboveSlots} end): a created slot no panel presented goes
 *       off screen (its library's {@code endFrame}), and loses its clip.</li>
 * </ol>
 *
 * <p>Why the panels cannot simply draw earlier: a panel's chrome has to cover the
 * slots it sits over (the claim rule: what a panel covers is inert and hidden), so it
 * draws after vanilla's slots. Only moving the slots it <em>shows</em> keeps both.
 *
 * <p>The place pass draws each panel a second time, into a render state that is
 * dropped. That costs one more extraction of each panel drawn after the slot pass
 * per frame; an element's {@code render} should therefore not count frames or
 * change the consumer's state, which MenuKit's own elements never do. Deferred
 * draws ({@code OverlayDraws}) are closed during it, so nothing it does reaches the
 * frame.
 *
 * <h2>A library that does not report its slots</h2>
 *
 * A created slot whose element never reports it ({@code SlotClip.shown}, the 7.2.0
 * contract; a slot library built before it) is not presented. For it the old
 * boundary still holds: flow and popover panels draw at the {@code extractSlot} HEAD
 * of the first such created slot in vanilla's walk ({@link #beforeSlot}), so it and
 * the created slots after it draw over the panels, as they did before 7.3.0. With no
 * such slot the panels draw at the end of the slot pass.
 *
 * <h2>A library's own screen</h2>
 *
 * A screen with its own host draws its own FLOW panels before vanilla's content
 * pass ({@code OwnHost.renderFlow}), under every slot, so their slots draw in
 * vanilla's pass as they always did and the place pass leaves those panels out. Its
 * popover and overlay panels draw here with everyone else's.
 *
 * <h2>A slot shown in part (7.2.0)</h2>
 *
 * A slot a panel places across a clip's edge (a {@code ScrollContainer}'s viewport)
 * is drawn scissored to the clip it was placed under ({@link SlotClips}), and so are
 * vanilla's hover highlights on it. A slot no clip cuts is drawn exactly as before.
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
 * {@code super} call): {@code extractContents} HEAD ({@link #frameStart}), the
 * {@code extractSlot} call in {@code extractSlots} and the two highlight calls in
 * {@code extractContents} (wrapped: {@link #drawsInVanillaPass},
 * {@link #highlightsInVanillaPass}), {@code extractSlot} HEAD ({@link #beforeSlot})
 * and {@code extractContents} RETURN ({@link #aboveSlots}). The contents points sit
 * outside the {@code leftPos/topPos} matrix push; the slot point sits inside it, so
 * {@link #beforeSlot} pops to absolute coordinates around the panel draw. All are
 * before every {@code nextStratum()} and before {@code extractTooltip}, so cursor and
 * tooltip layering, and same-frame tooltip flushing, are unchanged.
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

    /** Nobody owns the pointer in the place pass: every panel lays out as if not hovered. */
    private static final PanelHost.PointerPolicy NOT_LIVE = (host, placed) -> false;

    /** The render state the place pass draws into and throws away. Made on first use; render thread only. */
    private static @Nullable GuiRenderState scratch;

    /**
     * At {@code extractContents} HEAD: a new frame, layer 2 not yet drawn, then the
     * place pass. Every panel drawn after the slot pass is laid out into a scratch
     * render state, in the one draw order, with no pointer and no deferred draws, so
     * its slot elements place and report their slots ({@link PresentedSlots}) before
     * vanilla works out the hovered slot and walks the slots.
     */
    public static void frameStart(AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        flowDrawn = false;
        PresentedSlots.beginFrame(screen, mouseX, mouseY);
        List<LayerPlan.Drawn> order = ScreenPanelRegistry.drawnAfterSlots(screen);
        if (order.isEmpty()) return;
        if (scratch == null) scratch = new GuiRenderState();
        GuiGraphicsExtractor place = new GuiGraphicsExtractor(Minecraft.getInstance(), scratch, mouseX, mouseY);
        PresentedSlots.recording(true);
        OverlayDraws.allow(false);
        try {
            for (LayerPlan.Drawn d : order) d.host().render(place, -1, -1, d.placed(), NOT_LIVE);
        } finally {
            PresentedSlots.recording(false);
            scratch.reset();
        }
    }

    /**
     * Vanilla's slot pass, per slot: whether vanilla draws it. A slot a panel shows
     * this frame is left out; its panel draws it ({@link PresentedSlots#drawAfter}).
     */
    public static boolean drawsInVanillaPass(Slot slot) {
        return !PresentedSlots.isPresented(slot);
    }

    /**
     * Vanilla's back and front hover highlights around its slot pass: drawn there
     * unless the hovered slot is one a panel shows, which gets them around its own
     * draw, after its panel.
     */
    public static boolean highlightsInVanillaPass(AbstractContainerScreen<?> screen) {
        return !PresentedSlots.isPresented(((AbstractContainerScreenAccessor) screen).mk$getHoveredSlot());
    }

    /**
     * At {@code extractSlot} HEAD, for every slot vanilla is about to draw (and every
     * slot a panel draws after itself, which arrives here too). The first created
     * slot no panel shows, in vanilla's own walk, is the layer 1 / layer 3 boundary
     * for a library that does not report its slots: draw layer 2 now, before vanilla
     * draws it. Inside vanilla's slot pass the pose is translated by
     * {@code leftPos/topPos}; the panels draw in absolute screen coordinates, so the
     * translation is undone around them.
     */
    public static void beforeSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  Slot slot, int mouseX, int mouseY) {
        if (!flowDrawn && SlotScreenDispatcher.fireIsCreated(slot) && !PresentedSlots.isPresented(slot)) {
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            graphics.pose().pushMatrix();
            graphics.pose().translate(-acc.mk$getLeftPos(), -acc.mk$getTopPos());
            renderFlow(screen, graphics, mouseX, mouseY);
            graphics.pose().popMatrix();
            // Vanilla drew the hovered slot's back highlight before the slot pass; for
            // an unreported created slot that is under the chrome just drawn, so draw
            // it again on top. (Runs translated, like vanilla's own call.) A presented
            // hovered slot had its highlights with its panel.
            Slot hovered = acc.mk$getHoveredSlot();
            if (hovered != null && SlotScreenDispatcher.fireIsCreated(hovered) && !PresentedSlots.isPresented(hovered)) {
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
     * unreported created slot triggered it (the usual case), each panel followed by
     * its slots, then layer 4: the modal dim (over vanilla content and every flow
     * panel) and the overlay panels on top of it, each followed by its slots. Finally the end-of-frame park: a created slot that no
     * panel presented this frame goes off-screen, so vanilla neither draws nor
     * hit-tests it at a stale position next frame, and loses any clip it had.
     */
    public static void aboveSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        if (!flowDrawn) renderFlow(screen, graphics, mouseX, mouseY);
        ScreenPanelRegistry.renderOverlay(screen, graphics, mouseX, mouseY);
        SlotScreenDispatcher.fireEndFrame(screen);
        SlotClips.frameEnd();
        PresentedSlots.endFrame();
    }

    /** Layer 2: the FLOW layer of the screen's registry hosts (container adapters, then anchors), then every host's POPOVER layer. */
    private static void renderFlow(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY) {
        flowDrawn = true;
        ScreenPanelRegistry.renderFlow(screen, graphics, mouseX, mouseY);
    }
}
