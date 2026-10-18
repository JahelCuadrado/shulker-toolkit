package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Auto refill. A held stack runs out inside vanilla {@code ServerPlayerGameMode#useItemOn} (block
 * placement: {@code BlockItem#place} → {@code ItemStack#consume}; items used on blocks) and {@code
 * #useItem} (thrown items and other uses that shrink the stack at once). The count is noted when
 * the call starts and compared when it returns: one int write and two comparisons per use, nothing
 * per tick. The field is per player (one game mode per player) and server-thread only.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
  @Unique private int shulkertoolkit$countBeforeUse;

  @Inject(
      method = {"useItemOn", "useItem"},
      at = @At("HEAD"))
  private void shulkertoolkit$noteCountBeforeUse(
      CallbackInfoReturnable<?> callback, @Local(argsOnly = true) ItemStack itemStack) {
    shulkertoolkit$countBeforeUse = itemStack.getCount();
  }

  @Inject(
      method = {"useItemOn", "useItem"},
      at = @At("RETURN"))
  private void shulkertoolkit$refillIfRunOut(
      CallbackInfoReturnable<?> callback,
      @Local(argsOnly = true) ServerPlayer player,
      @Local(argsOnly = true) InteractionHand hand,
      @Local(argsOnly = true) ItemStack itemStack) {
    int countBefore = shulkertoolkit$countBeforeUse;
    shulkertoolkit$countBeforeUse = 0;
    ShulkerPick.afterItemUse(player, hand, itemStack, countBefore);
  }
}
