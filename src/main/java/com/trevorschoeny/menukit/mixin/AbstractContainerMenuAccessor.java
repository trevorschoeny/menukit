package com.trevorschoeny.menukit.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code AbstractContainerMenu.addSlot(Slot)} for use by
 * {@link com.trevorschoeny.menukit.core.SlotInjector}. The method is
 * {@code protected} on vanilla; this invoker makes it callable from the
 * library's grafting utility.
 *
 * <p>Both-sides mixin — {@code addSlot} runs during handler construction
 * on both client and server.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("addSlot")
    Slot menuKit$addSlot(Slot slot);
}
