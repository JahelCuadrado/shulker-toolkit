package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.pick.PickupIntoBoxes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Picked-up items into shulker boxes. Vanilla {@code ItemEntity#playerTouch} (server side, once the
 * pickup delay is over and the player may take the item) calls {@code Inventory#add} on the item's
 * own stack, then on success {@code Player#take}, {@code Stats.ITEM_PICKED_UP} and {@code
 * Player#onItemPickup}. The first hook runs right before that call, the second on its result; both
 * delegate to {@link PickupIntoBoxes}. Hoppers, mobs and other pickups never reach this call. No
 * operation wrapper: a wrapper would allocate on every touch.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
  private static final String INVENTORY_ADD =
      "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z";

  @ModifyReceiver(method = "playerTouch", at = @At(value = "INVOKE", target = INVENTORY_ADD))
  private Inventory shulkertoolkit$storeInBoxesFirst(Inventory inventory, ItemStack picked) {
    PickupIntoBoxes.beforeInventory(inventory, picked);
    return inventory;
  }

  @ModifyExpressionValue(method = "playerTouch", at = @At(value = "INVOKE", target = INVENTORY_ADD))
  private boolean shulkertoolkit$storeOverflowInBoxes(
      boolean added, @Local(argsOnly = true) Player player, @Local ItemStack picked) {
    return PickupIntoBoxes.afterInventory(player, picked, added);
  }
}
