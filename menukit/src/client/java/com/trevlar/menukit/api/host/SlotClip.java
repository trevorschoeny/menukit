package com.trevlar.menukit.api.host;

import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.inject.PresentedSlots;
import com.trevlar.menukit.inject.SlotClips;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * Part of the created-slot contract, beside {@link SlotScreenHook}: a slot library
 * tells MenuKit when an element places a slot, so the slot is clipped to what its
 * panel shows of it (7.2.0).
 *
 * <p>A slot element writes its slot's position into {@code Slot.x/y} and vanilla
 * draws and hit-tests the slot there, in its own slot pass. Inside a clipping
 * container (a {@code ScrollContainer}'s viewport, a {@code Tabs} body, or any element
 * that scissors what it draws) a slot can sit across the clip's edge: the element's
 * own frame is clipped like everything else it draws, but vanilla knows nothing of
 * the clip. Calling {@link #shown} from the element's render, right after writing the
 * position, hands MenuKit the clip in play. From then on:
 * <ul>
 *   <li>vanilla's draw of the slot (item, count, durability, highlight) is clipped to
 *       it, so the part past the edge does not draw;</li>
 *   <li>a point outside it neither hovers nor clicks the slot: it is the panel's, as a
 *       gap between slots is, so a carried item cannot drop through there either.</li>
 * </ul>
 * The slot keeps its real position, so nothing beside it moves. A slot wholly inside
 * its clip looks and behaves exactly as before. One wholly outside should not be
 * placed at all: a {@code ScrollContainer} does not render a child out of view, so its
 * slot stays where its library parks it.
 */
public final class SlotClip {

    private SlotClip() {}

    /**
     * Records that {@code slot} is placed this frame by an element rendering with
     * {@code ctx}, under the clip that context draws in. Call once a frame for each
     * slot placed, after writing its {@code Slot.x/y}; pass the slot that is in the
     * screen's own {@code menu.slots} (on the creative screen, the wrapper around it),
     * which is the one vanilla draws. Render thread only.
     */
    public static void shown(RenderContext ctx, Slot slot) {
        SlotClips.record(slot, ctx.graphics().scissorStack.peek());
    }

    /**
     * Whether the panel that last showed {@code slot} ({@link #shown}) was drawn this frame
     * (7.3.0). Ask it at the end of the frame ({@link SlotScreenHook#endFrame}) for a slot
     * that was not shown this frame, to tell why: {@code true}, its panel is on screen and
     * the slot was scrolled out of view or hidden by its element, so park it; {@code false},
     * its panel is hidden or not on this screen (or no panel ever showed the slot), so a
     * vanilla slot a panel took over goes back to its own place. Render thread only.
     */
    public static boolean panelShowing(Slot slot) {
        return PresentedSlots.panelShowing(slot);
    }

    /**
     * The rectangle {@code slot} is shown within, in screen space (GUI pixels), or {@code null}
     * when nothing clips it (7.4.0): what {@link #shown} was handed, every clipping ancestor's
     * scissor intersected. A slot library can tell from it whether a slot is wholly in view,
     * cut by a scroll edge, or out of it, say to hide a panel anchored on a slot whose slot is
     * cut.
     *
     * <p>It changes when the slot's element places it, with its {@code Slot.x/y}, so the two
     * always agree: during a frame, before the element renders, it is last frame's; after, this
     * frame's. A slot no panel showed last frame has none. Where two panels show one slot in a
     * frame, it is the clip of the one on top. Render thread only.
     */
    public static @Nullable Reference clipOf(Slot slot) {
        ScreenRectangle clip = SlotClips.clipOf(slot);
        return clip == null ? null : new Reference(clip.left(), clip.top(), clip.width(), clip.height());
    }
}
