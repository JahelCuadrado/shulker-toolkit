package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.craft.CraftFromBoxes;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes recipe book ingredients out of shulker boxes when vanilla {@code
 * ServerPlaceRecipe#moveItemToGrid} would find no loose stack ({@code
 * Inventory#findSlotMatchingCraftingIngredient} returns -1, which aborts the placement). Runs once
 * per grid slot and loose stack on a recipe click; never per tick.
 */
@Mixin(ServerPlaceRecipe.class)
abstract class ServerPlaceRecipeMixin {
  @Shadow @Final private Inventory inventory;

  @Inject(method = "moveItemToGrid", at = @At("HEAD"), cancellable = true)
  private void shulkertoolkit$moveFromBoxes(
      Slot target, Holder<Item> item, int count, CallbackInfoReturnable<Integer> callback) {
    int remaining = CraftFromBoxes.moveFromBoxes(inventory, target, item, count);
    if (remaining != CraftFromBoxes.NOT_HANDLED) {
      callback.setReturnValue(remaining);
    }
  }
}
