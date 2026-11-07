package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Where a recipe book stands, read from the book itself, so its chrome is right whichever
 * screen shows it: a recipe-book screen's own, or one a mod lends to a screen without one
 * ({@code MenuChrome}).
 */
@ApiStatus.Internal
@Mixin(RecipeBookComponent.class)
public interface MKRecipeBookComponentAccessor {

    /** The book body's left edge, its filter tabs further left of it. */
    @Invoker("getXOrigin")
    int mk$xOrigin();

    /** Whether the book stands over the frame instead of beside it. */
    @Accessor("widthTooNarrow")
    boolean mk$widthTooNarrow();
}
