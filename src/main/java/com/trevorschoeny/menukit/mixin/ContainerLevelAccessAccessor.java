package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKAccessors;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixins for the private {@code access} field on vanilla menus.
 * Each extends {@link MKAccessors.HasContainerLevelAccess} which lives
 * outside the mixin package so it can be used for runtime casting.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class ContainerLevelAccessAccessor {

    @Mixin(EnchantmentMenu.class)
    public interface EnchantmentMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }

    @Mixin(CraftingMenu.class)
    public interface CraftingMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }

    @Mixin(ItemCombinerMenu.class)
    public interface ItemCombinerMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }

    @Mixin(LoomMenu.class)
    public interface LoomMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }

    @Mixin(StonecutterMenu.class)
    public interface StonecutterMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }

    @Mixin(CartographyTableMenu.class)
    public interface CartographyTableMenuAccessor extends MKAccessors.HasContainerLevelAccess {
        @Override @Accessor("access") ContainerLevelAccess menuKit$getAccess();
    }
}
