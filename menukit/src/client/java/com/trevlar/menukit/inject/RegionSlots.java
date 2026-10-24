package com.trevlar.menukit.inject;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Hidden sections' slots are off (7.4.0, the container region): while the region is up, every
 * slot of a section whose own condition doesn't hold this frame ({@code visibleWhen}, its mode
 * scope, its screens) answers {@code isActive} false on the client, so nothing draws, hovers or
 * takes a click there, and a slot library's element leaves it out.
 *
 * <p>A slot is a section's once the region has drawn it there ({@link RegionReveal#sectionOf}).
 * A container's own slot (not the player's, not a created one) no section has drawn yet is the
 * screen's own container's: the first section on the screen with no source, shown or not. So a
 * screen whose own container starts hidden has its own slots off from the first frame, unless
 * that would hide a shown section, one that presents those slots itself ({@code ContainerRegions}).
 *
 * <p>Only the client's open menu is touched; the integrated server's slots are its own objects,
 * never off. Render thread, but {@link #switchedOff} is read from any thread (an identity set
 * swapped whole).
 */
@ApiStatus.Internal
public final class RegionSlots {

    private RegionSlots() {}

    /** The client's slots off this frame. */
    private static volatile Set<Slot> off = Set.of();
    /** This frame's hidden sections, by stacking index, and the screen's own container's ({@code -1}: none). */
    private static boolean[] hidden = new boolean[0];
    private static int own = -1;

    /** Whether MenuKit has {@code slot} off now: a hidden section's, on the client's open menu. */
    public static boolean switchedOff(Slot slot) {
        Set<Slot> o = off;
        return !o.isEmpty() && o.contains(slot);
    }

    /**
     * Every slot back on, so this frame's question of which sections show sees their content
     * as it is: a section's slots being off last frame must not hide it now.
     */
    static void allOn() {
        off = Set.of();
        hidden = new boolean[0];
        own = -1;
    }

    /**
     * This frame's {@code hidden} sections (by stacking index) and the screen's own container's
     * section {@code ownSection}: every slot of {@code menuSlots} that is a hidden section's goes off.
     */
    static void switchOff(@Nullable List<Slot> menuSlots, boolean[] hiddenSections, int ownSection) {
        hidden = hiddenSections;
        own = ownSection;
        if (menuSlots == null) return;
        Set<Slot> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : menuSlots) if (inHidden(slot) >= 0) out.add(slot);
        off = out.isEmpty() ? Set.of() : out;
    }

    /** The hidden section {@code slot} is in this frame, by stacking index, or {@code -1} (shown, the player's, or no section's). */
    static int inHidden(Slot slot) {
        if (RegionBounds.stays(slot)) return -1;
        int s = RegionReveal.sectionOf(slot);
        if (s < 0 && !SlotScreenDispatcher.fireIsCreated(slot)) s = own;
        return s >= 0 && s < hidden.length && hidden[s] ? s : -1;
    }
}
