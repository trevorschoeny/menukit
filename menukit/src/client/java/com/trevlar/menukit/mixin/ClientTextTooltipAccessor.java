package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a tooltip line's text, so MKTooltip can tell one tooltip from the next
 * (its scroll resets when the text changes). Vanilla keeps the field private.
 */
@ApiStatus.Internal
@Mixin(ClientTextTooltip.class)
public interface ClientTextTooltipAccessor {
    @Accessor("text")
    FormattedCharSequence mk$text();
}
