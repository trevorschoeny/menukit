package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code recipeBookComponent} field on
 * {@link AbstractRecipeBookScreen} so MenuKit's recipe book methods can read and
 * toggle it. Consumers use {@link com.trevlar.menukit.MenuKitClient#isRecipeBookOpen()}
 * and {@link com.trevlar.menukit.MenuKitClient#setRecipeBookOpen(boolean)} instead.
 */
@ApiStatus.Internal
@Mixin(AbstractRecipeBookScreen.class)
public interface MKRecipeBookAccessor {

    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> mk$getRecipeBookComponent();
}
