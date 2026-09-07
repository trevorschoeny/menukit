package com.trevlar.menukit.inject;

import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.window.ClientSlotAddressing;
import com.trevlar.menukit.window.KindTag;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The vanilla slots a visible opaque panel is covering this frame. The render
 * half of §0058's covered→inert law: a vanilla slot under a panel is
 * <b>inactive</b> for the frame, so vanilla's own slot pass does not draw it and
 * vanilla's own hover resolution does not find it — the same mechanism vanilla
 * uses for the crafting slots the recipe book slides over.
 *
 * <h2>Why this exists (layer 1 is below the slot pass)</h2>
 *
 * {@link ContainerScreenLayers} draws flow panels <em>below</em> vanilla's slot
 * pass so that vanilla draws every slot, created ones included. The price is that
 * a panel can no longer hide the vanilla slots behind it by painting over them:
 * their items, and every other mod's decorations on them, would come out on top
 * of the panel. Painting over was only ever the visual stand-in for inertness;
 * this is the real thing. Input was already covered (the click/hover/tooltip
 * suppressors all reduce to {@code MKFocus.isInertUnderPanel}); this closes the
 * render side with vanilla's own switch.
 *
 * <h2>What counts as covered</h2>
 *
 * A slot of kind {@link KindTag#VANILLA_SLOT} whose 16×16 item box intersects the
 * padded bounds of a visible, opaque menu-context panel on the screen (flow or
 * overlay) — §0058's bounding-box rule, not pixels. Created slots are never
 * covered: a created slot sits <em>in</em> its panel, not behind it. Kind comes
 * from {@link ClientSlotAddressing}, which MKC makes kind-aware; MK-alone every
 * slot is vanilla.
 *
 * <p>Recomputed once per frame at the end of layer 1 (panel origins are resolved
 * by then; vanilla's hover resolution and slot pass follow in the same call).
 * Between frames the set holds what was drawn, so input agrees with the picture.
 * Weak, identity-keyed: holds only the live screen's slot instances, and they go
 * with the screen. Server-side {@code Slot} instances are different objects and
 * never appear here.
 */
@ApiStatus.Internal
public final class CoveredSlots {

    private CoveredSlots() {}

    // Slot does not override equals/hashCode, so a WeakHashMap-backed set is an
    // identity set that lets a closed screen's slots be collected.
    private static final Set<Slot> COVERED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** Whether {@code slot} is behind a visible opaque panel this frame. */
    public static boolean isCovered(Slot slot) {
        return !COVERED.isEmpty() && COVERED.contains(slot);
    }

    /** Rebuilds the set for {@code screen}. Called once per frame from layer 1. */
    static void recompute(AbstractContainerScreen<?> screen) {
        COVERED.clear();
        if (!ScreenPanelRegistry.hasVisibleOpaquePanel(screen)) return;
        AbstractContainerMenu menu = screen.getMenu();
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int left = acc.mk$getLeftPos();
        int top = acc.mk$getTopPos();
        for (Slot slot : menu.slots) {
            if (ClientSlotAddressing.addressOf(menu, slot).kind() != KindTag.VANILLA_SLOT) continue;
            if (ScreenPanelRegistry.anyOpaquePanelCoversBox(screen,
                    left + slot.x, top + slot.y, 16, 16)) {
                COVERED.add(slot);
            }
        }
    }
}
