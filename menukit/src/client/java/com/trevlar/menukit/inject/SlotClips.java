package com.trevlar.menukit.inject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The clip each panel-placed slot is shown under ({@code SlotClip.shown}), and what
 * holds vanilla's slot pass and slot hover to it.
 *
 * <p>A slot's panel draws its frame clipped like any element, but vanilla draws the
 * slot itself (item, count, highlight) in its own pass, and finds it in
 * {@code getHoveredSlot}, from {@code Slot.x/y} alone. A slot placed across a
 * {@code ScrollContainer}'s edge would draw and hover whole. So the clip in play when
 * the slot was placed (every clipping ancestor's scissor, intersected) is kept here
 * per slot, and:
 * <ul>
 *   <li>{@link ContainerScreenLayers} scissors the slot's {@code extractSlot} and its
 *       hover highlights to it;</li>
 *   <li>{@link SlotScreenDispatcher} counts a point outside it as a gap in the panel:
 *       no slot is hovered there, and a click there is eaten, as a click between
 *       slots is.</li>
 * </ul>
 *
 * <p>A slot shown under no clip has no entry and is left exactly as vanilla has it.
 * One shown wholly outside its clip is never placed in the first place (a
 * {@code ScrollContainer} does not render a child out of view, so its slot stays
 * parked).
 *
 * <h3>Which frame's clip</h3>
 *
 * A slot's clip changes only when its panel places it again, the same moment its
 * {@code Slot.x/y} does, so the two always agree: hover before the panels draw, and
 * the vanilla slots drawn before them, use last frame's position and last frame's
 * clip; the created slots drawn after them use this frame's. At the end of a frame a
 * slot no panel showed loses its clip (its library parks it at the same moment).
 *
 * <p>Render thread only, like the slot pass and vanilla's input.
 */
@ApiStatus.Internal
public final class SlotClips {

    private SlotClips() {}

    /** Slot to the clip it was last shown under. Identity-keyed: a slot is one object, not a value. */
    private static final Map<Slot, ScreenRectangle> CLIPS = new IdentityHashMap<>();

    /** The slots shown this frame, so one no panel shows any more loses its clip at the frame's end. */
    private static final Set<Slot> SHOWN = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * {@code SlotClip.shown}: {@code slot} is placed this frame under {@code clip}, the
     * scissor in play (screen space), or under none.
     */
    public static void record(Slot slot, @Nullable ScreenRectangle clip) {
        SHOWN.add(slot);
        // The same report says which panel shows the slot (7.3.0): it draws after that panel.
        PresentedSlots.record(slot);
        if (clip == null) CLIPS.remove(slot);
        else CLIPS.put(slot, clip);
    }

    /** The clip {@code slot} is shown under, or {@code null} when nothing clips it. */
    public static @Nullable ScreenRectangle clipOf(Slot slot) {
        return CLIPS.get(slot);
    }

    /**
     * Whether {@code slot} is placed under a clip that leaves the screen point out: the
     * part of a slot past a {@code ScrollContainer}'s edge. False for {@code null} and
     * for a slot no clip cuts.
     */
    public static boolean hides(@Nullable Slot slot, double mouseX, double mouseY) {
        if (slot == null) return false;
        ScreenRectangle clip = CLIPS.get(slot);
        return clip != null && !clip.containsPoint((int) Math.floor(mouseX), (int) Math.floor(mouseY));
    }

    /**
     * Before vanilla draws {@code slot} (or a highlight on it): scissors to its clip.
     * The rectangle goes on the stack as it is, in screen space, so it holds whatever
     * translation the slot pass is drawing under.
     *
     * @return whether a scissor was pushed, which the caller pops after the draw
     */
    static boolean push(GuiGraphicsExtractor graphics, @Nullable Slot slot) {
        ScreenRectangle clip = slot == null ? null : CLIPS.get(slot);
        if (clip == null) return false;
        graphics.scissorStack.push(clip);
        return true;
    }

    /** End of frame: a slot no panel showed this frame keeps no clip. */
    public static void frameEnd() {
        CLIPS.keySet().retainAll(SHOWN);
        SHOWN.clear();
    }
}
