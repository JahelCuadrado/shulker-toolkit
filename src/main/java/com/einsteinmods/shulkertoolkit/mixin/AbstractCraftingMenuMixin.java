package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.craft.CraftFromBoxes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts ingredients stored in shulker boxes for the recipe book of the crafting table and the
 * inventory. Vanilla {@code AbstractCraftingMenu#fillCraftSlotsStackedContents} runs right after
 * {@code Inventory#fillStackedContents} in the client's {@code RecipeBookComponent} refresh and in
 * the server's {@code ServerPlaceRecipe#placeRecipe}; nothing else calls it.
 */
@Mixin(AbstractCraftingMenu.class)
abstract class AbstractCraftingMenuMixin {
  @Shadow
  protected abstract Player owner();

  @Inject(method = "fillCraftSlotsStackedContents", at = @At("TAIL"))
  private void shulkertoolkit$accountBoxes(StackedItemContents contents, CallbackInfo callback) {
    CraftFromBoxes.accountBoxes(owner(), contents);
  }
}
