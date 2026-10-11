package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.panel.Focus;

import net.minecraft.client.gui.components.AbstractSelectionList;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses scroll-list row hover under MenuKit panels. The counterpart of
 * {@link MKWidgetHoverSuppressMixin} for vanilla list rows, whose highlight does
 * not go through {@code AbstractWidget.isHovered}.
 *
 * <p>{@link AbstractSelectionList} extends {@code AbstractWidget}, but it draws
 * the row highlight from a per-list "hovered entry" tracked apart from the
 * widget's {@code isHovered} field. {@code renderItem} reads that entry through
 * {@link AbstractSelectionList#getHovered} and renderSelection paints the row
 * when it is non-null, so the {@code isHovered()} mixin never reaches it.
 *
 * <p>This returns {@code null} from {@code getHovered()} while the cursor is over
 * an opaque MenuKit region, so vanilla skips renderSelection.
 *
 * <p>Keyboard navigation and scroll-to-hovered also read {@code getHovered()}, so
 * they are skipped too. That is fine: a player with the mouse on MenuKit content
 * is not using the list.
 *
 * <p>Clicks are not affected. Lists dispatch clicks through
 * {@link AbstractSelectionList#getEntryAtPosition} with explicit coordinates, and
 * MenuKit's click-eating already keeps clicks off covered rows.
 */
@ApiStatus.Internal
@Mixin(AbstractSelectionList.class)
public abstract class MKListHoverSuppressMixin {

    // Entry is protected on AbstractSelectionList, so it can't be named here.
    // The type parameter is erased at runtime, so the wildcard is fine.
    @Inject(
            method = "getHovered()Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$suppressListHoverWhenOpaque(CallbackInfoReturnable<?> cir) {
        // The same inertness check (modal anywhere, or covered) every other suppressor uses.
        if (Focus.isInertUnderPanelAtCursor()) {
            cir.setReturnValue(null);
        }
    }
}
