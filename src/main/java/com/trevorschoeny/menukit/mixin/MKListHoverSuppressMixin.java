package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.MKFocus;

import net.minecraft.client.gui.components.AbstractSelectionList;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 18s follow-up — scroll-list row-hover suppression. Sibling to
 * {@link MKWidgetHoverSuppressMixin} but for vanilla scroll-list
 * row highlights, which DON'T go through {@code AbstractWidget.isHovered}.
 *
 * <h3>Why this needs its own mixin</h3>
 *
 * {@link AbstractSelectionList} extends {@code AbstractWidget} but draws
 * its row-selection highlight based on a per-list-instance "hovered
 * entry" tracked separately from the widget's own {@code isHovered}
 * field. The hovered entry is read via {@link AbstractSelectionList#getHovered}
 * inside {@code renderItem}, and renderSelection paints the row
 * highlight when it's non-null. So the {@code isHovered()} mixin doesn't
 * reach the row-level decision — we need a separate mixin on the list
 * itself.
 *
 * <h3>Mechanism</h3>
 *
 * Overrides {@code getHovered()} to return {@code null} when the cursor
 * is over an MK opaque region. With no hovered entry, vanilla skips
 * renderSelection — the row stays visually inert behind MK content.
 *
 * <h3>What this affects beyond visuals</h3>
 *
 * {@code getHovered()} is also consulted by keyboard navigation and
 * scroll-to-hovered behaviors. Suppressing it means those skip too —
 * which is fine for the MK-cursor-over-list case: if the user has moved
 * the mouse onto MK content, they're not interacting with the list, so
 * suppressing list-level hover state is consistent with user intent.
 *
 * <h3>Click dispatch is NOT affected</h3>
 *
 * Click dispatch on lists uses {@link AbstractSelectionList#getEntryAtPosition}
 * (with explicit coords) — not {@code getHovered()}. The MK opacity-eat
 * input path already routes clicks away from covered rows; this mixin
 * doesn't touch that.
 */
@ApiStatus.Internal
@Mixin(AbstractSelectionList.class)
public abstract class MKListHoverSuppressMixin {

    // Entry is protected on AbstractSelectionList; can't reference it
    // from outside the vanilla package. Mixin's bytecode-level handling
    // erases the generic param at runtime — wildcard type is fine.
    @Inject(
            method = "getHovered()Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$suppressListHoverWhenOpaque(CallbackInfoReturnable<?> cir) {
        // Unified inertness predicate (modal-global OR covered) — same question
        // every other suppressor asks.
        if (MKFocus.isInertUnderPanelAtCursor()) {
            cir.setReturnValue(null);
        }
    }
}
