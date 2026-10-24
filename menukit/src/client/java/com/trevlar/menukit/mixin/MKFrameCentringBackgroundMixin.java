package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trevlar.menukit.inject.ContainerTabRows;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The image, and what each screen draws from it, of every vanilla container screen that places
 * its image from the window's middle rather than from {@code topPos}: drawn {@code dy} lower
 * with the frame ({@link MKFrameCentringMixin}). The rest place from {@code topPos} already.
 */
// ponytail: a few places vanilla works out from the window outside its background pass stay put
// (the anvil's name field, the crafter's arrow, the enchanting table's and merchant's clicks and
// offers): lens those methods too once a mod puts a tab row on one of those screens.
@ApiStatus.Internal
@Mixin({AbstractMountInventoryScreen.class, BeaconScreen.class, BrewingStandScreen.class, ContainerScreen.class,
        CrafterScreen.class, CraftingScreen.class, DispenserScreen.class, EnchantmentScreen.class, GrindstoneScreen.class,
        HopperScreen.class, MerchantScreen.class, ShulkerBoxScreen.class})
public abstract class MKFrameCentringBackgroundMixin {

    @ModifyExpressionValue(method = "extractBackground", require = 1, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = Opcodes.GETFIELD))
    private int mk$centredHeight(int height) {
        return height + 2 * ContainerTabRows.centring((Screen) (Object) this);
    }
}
