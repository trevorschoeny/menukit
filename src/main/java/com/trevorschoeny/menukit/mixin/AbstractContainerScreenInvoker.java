package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code slotClicked} on {@link AbstractContainerScreen} so that
 * framework code can programmatically simulate slot clicks through vanilla's
 * own input processing pipeline.
 *
 * <p>This is the core primitive behind {@link com.trevorschoeny.menukit.MKSlotActions}.
 * Consumer modules should use that class instead of casting to this invoker directly.
 *
 * <p>Why an invoker instead of direct packet construction: {@code slotClicked}
 * handles client prediction, packet sending, server validation, and container
 * state updates. Calling it means vanilla handles all the hard parts.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {

    @Invoker("slotClicked")
    void menuKit$invokeSlotClicked(@Nullable Slot slot, int slotId, int button, ClickType clickType);
}
