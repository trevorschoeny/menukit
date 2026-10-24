package com.trevlar.menukit.core;

import com.trevlar.menukit.mixin.ItemLayerRenderStateAccessor;
import com.trevlar.menukit.mixin.ItemStackRenderStateAccessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

/**
 * The bookkeeping behind a turned item ({@code ItemAnimation.Pose}'s pitch, yaw and roll).
 *
 * <h3>How</h3>
 *
 * While {@link #draw} records an item, {@code MKItemTurnMixin} adds the turn to each of the
 * item's layers' display rotations, the GUI's {@code rotation} in its model (a block's
 * {@code [30, 225, 0]}, a head's {@code [30, 45, 0]}), so the model turns about its center as
 * a resource pack's own rotation would turn it, and adds the turn to the item's model identity,
 * so the GUI's item atlas keeps each angle apart. The item is marked oversized, so a corner
 * turned past the 16 px box draws there through vanilla's oversized-item path; one that stays
 * inside is drawn from the atlas as usual. Everything else is vanilla's.
 *
 * <p>Angles are rounded to whole degrees, so a turn makes a bounded number of atlas entries.
 * Render thread only.
 */
@ApiStatus.Internal
public final class ItemTurn {

    private ItemTurn() {}

    /** The turn items recorded right now draw with, in whole degrees: pitch, yaw, roll. All 0 when none. */
    private static int pitch, yaw, roll;

    /** The model identity element of a turned item: which angles. */
    private record Identity(int pitch, int yaw, int roll) {}

    /** Runs {@code draw}, every item it records turned by {@code pitch}, {@code yaw} and {@code roll} degrees on top of any turn already in force. */
    public static void draw(float pitch, float yaw, float roll, Runnable draw) {
        int p = ItemTurn.pitch, y = ItemTurn.yaw, r = ItemTurn.roll;
        ItemTurn.pitch = p + Math.round(pitch);
        ItemTurn.yaw = y + Math.round(yaw);
        ItemTurn.roll = r + Math.round(roll);
        try {
            draw.run();
        } finally {
            ItemTurn.pitch = p;
            ItemTurn.yaw = y;
            ItemTurn.roll = r;
        }
    }

    /** {@code output}, just resolved for the GUI: turned when a turn is in force. */
    public static void apply(ItemStackRenderState output) {
        if (pitch == 0 && yaw == 0 && roll == 0) return;
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers = state.mk$layers();
        for (int i = 0; i < state.mk$activeLayerCount(); i++) {
            ItemTransform t = ((ItemLayerRenderStateAccessor) layers[i]).mk$itemTransform();
            Vector3f rotation = new Vector3f(t.rotation()).add(pitch, yaw, roll);
            layers[i].setItemTransform(new ItemTransform(rotation, t.translation(), t.scale()));
        }
        output.appendModelIdentityElement(new Identity(pitch, yaw, roll));
        output.setOversizedInGui(true);
    }
}
