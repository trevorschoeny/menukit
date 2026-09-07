package com.trevlar.menukit.inject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;

/**
 * The one owner of <b>frame composition order</b> on a container screen — what
 * MenuKit draws, and where in vanilla's frame it lands. Every MenuKit draw on an
 * {@code AbstractContainerScreen} goes through one of the two entry points here;
 * nothing else in the library registers a render hook on a container screen.
 *
 * <h2>The layer stack (bottom to top)</h2>
 *
 * <table>
 *   <tr><th>#</th><th>Layer</th><th>Drawn by</th></tr>
 *   <tr><td>0</td><td>screen background, container texture</td><td>vanilla ({@code extractBackground}, an earlier stratum)</td></tr>
 *   <tr><td>1</td><td><b>flow panels</b>: chrome + non-slot elements + created-slot frames</td><td>MenuKit — {@link #belowSlots}</td></tr>
 *   <tr><td>2</td><td><b>every slot, vanilla and created alike</b>: highlight, item, count, durability</td><td>vanilla ({@code extractSlots})</td></tr>
 *   <tr><td>3</td><td>modal dim, <b>overlay panels</b></td><td>MenuKit — {@link #aboveSlots}</td></tr>
 *   <tr><td>4</td><td>carried item, cursor</td><td>vanilla ({@code extractCarriedItem}, next stratum)</td></tr>
 *   <tr><td>5</td><td>tooltips</td><td>vanilla ({@code extractTooltip})</td></tr>
 * </table>
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
 * <h2>Why layer 1 sits BELOW the slot pass</h2>
 *
 * So that <b>vanilla draws every slot</b>. A created slot (MKC) is a real
 * {@code Slot} in {@code menu.slots}; its presenting element writes the panel-
 * resolved position into vanilla's own {@code Slot.x/y} during layer 1, and then
 * vanilla's {@code extractSlots} draws it exactly as it draws a vanilla slot —
 * same call, same frame position, same visibility to every other mod's
 * {@code extractSlot} mixin. That is what makes a created slot and a vanilla slot
 * <em>literally the same thing</em> to a third party: there is no MenuKit slot
 * pass for a decoration to be painted over by. (This is the model
 * {@code MKCHandledScreen} has always used on MenuKit's own screens; layer 1
 * generalises it to injected panels.)
 *
 * <h2>The one injection point</h2>
 *
 * {@code AbstractContainerScreen.extractContents} — {@code HEAD} for layer 1,
 * {@code RETURN} for layer 3 — reached by every container screen family: plain
 * screens via {@code AbstractContainerScreen.extractRenderState}, recipe-book
 * screens via {@code AbstractRecipeBookScreen.extractRenderState}, and creative via
 * its {@code super} call. Both points sit outside the {@code leftPos/topPos}
 * matrix push (absolute screen coordinates, which the adapters already use), after
 * {@code extractBackground}'s stratum, and before every {@code nextStratum()} and
 * {@code extractTooltip} — so cursor and tooltip layering, and same-frame tooltip
 * flushing for widgets that queue during render, are unchanged.
 *
 * <p>Non-container screens ({@link VanillaScreenPanelRegistry}) have no slot pass
 * and keep their renderable; they are a different surface with no layer question.
 *
 * <p>Internal — the mixin calls in; consumers never do.
 */
@ApiStatus.Internal
public final class ContainerScreenLayers {

    private ContainerScreenLayers() {}

    /**
     * Layer 1. Fired at {@code extractContents} HEAD, before vanilla's hover
     * resolution and slot pass. Flow-positioned menu panels first (their created
     * slots write {@code Slot.x/y} here), then slot-group panels on top of them
     * (the historical "MenuKit first, slot-group second" order).
     */
    public static void belowSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        // Created slots not presented this frame must not keep last frame's
        // position, or vanilla would draw them there. Park them all first; the
        // ones a panel presents this frame are re-placed during the panel render.
        SlotScreenDispatcher.fireBeginFrame(screen);
        ScreenPanelRegistry.renderFlowPanels(screen, graphics, mouseX, mouseY);
        SlotGroupPanelRegistry.renderMatchingPanels(screen, graphics, mouseX, mouseY);
    }

    /**
     * Layer 3. Fired at {@code extractContents} RETURN, after vanilla's slot pass.
     * The modal dim (covers vanilla content AND every layer-1 panel), then
     * overlay-positioned panels on top of it.
     */
    public static void aboveSlots(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        ScreenPanelRegistry.renderOverlayPanels(screen, graphics, mouseX, mouseY);
    }
}
