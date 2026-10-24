package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trevlar.menukit.inject.FrameCentring;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The window as vanilla's container screen code reads it while a frame is centered with its tab
 * rows ({@link FrameCentring#centre}): {@code 2 * dy} taller, in every method of every
 * vanilla container screen class that reads it, so everything vanilla works out from the
 * window's middle moves {@code dy} down with the frame, with vanilla's own rounding: the
 * frame's {@code topPos}, the images drawn from the window, the recipe book and its button,
 * the anvil's name field, the crafter's arrow, the enchanting table's and the merchant's
 * clicks and offers. What vanilla places from {@code topPos} moves with it already, and no
 * vanilla container screen keeps a place from the window anywhere else. 0 on a screen with no
 * tab row, so nothing moves there.
 */
@ApiStatus.Internal
@Mixin({AbstractContainerScreen.class, AbstractFurnaceScreen.class, AbstractMountInventoryScreen.class, AbstractRecipeBookScreen.class, AnvilScreen.class, BeaconScreen.class, BrewingStandScreen.class, ContainerScreen.class, CrafterScreen.class, CraftingScreen.class, DispenserScreen.class, EnchantmentScreen.class, GrindstoneScreen.class, HopperScreen.class, InventoryScreen.class, MerchantScreen.class, ShulkerBoxScreen.class})
public abstract class MKFrameCentringHeightMixin {

    // Read as each class's own field: javac names the class the read is in as its owner, so a
    // widget's own height (the beacon's buttons, the recipe book's) is never read taller.
    @ModifyExpressionValue(method = "*", require = 1, at = {
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractFurnaceScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractMountInventoryScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AnvilScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/BeaconScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/BrewingStandScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/ContainerScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/CrafterScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/CraftingScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/DispenserScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/EnchantmentScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/GrindstoneScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/HopperScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/ShulkerBoxScreen;height:I", opcode = Opcodes.GETFIELD)})
    private int mk$centredHeight(int height) {
        return height + 2 * FrameCentring.centring((Screen) (Object) this);
    }
}
