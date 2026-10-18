package com.einsteinmods.shulkertoolkit.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The recipe book of the inventory, crafting and furnace screens: while it is open it takes the
 * left of the container, so pinned windows stay on the right.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
  @Accessor("recipeBookComponent")
  RecipeBookComponent<?> shulkertoolkit$recipeBookComponent();
}
