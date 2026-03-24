package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code recipeBookComponent} field on
 * {@link AbstractRecipeBookScreen} so that MenuKit's recipe book
 * utility methods can read and toggle visibility without consumers
 * needing to touch vanilla internals.
 *
 * <p>Client-only — the recipe book is purely a client-side UI widget.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Consumers should
 * use {@link com.trevorschoeny.menukit.MenuKitClient#isRecipeBookOpen()}
 * and {@link com.trevorschoeny.menukit.MenuKitClient#setRecipeBookOpen(boolean)}
 * instead of casting to this interface directly.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface MKRecipeBookAccessor {

    /**
     * @return the {@link RecipeBookComponent} that controls the recipe
     *         book overlay on any screen extending AbstractRecipeBookScreen
     */
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> menuKit$getRecipeBookComponent();
}
