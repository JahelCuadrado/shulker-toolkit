package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Auto refill after eating, drinking and other uses that finish over time. Vanilla {@code
 * LivingEntity#completeUsingItem} (reached from {@code ServerPlayer#completeUsingItem}) calls
 * {@code ItemStack#finishUsingItem}, which consumes the item ({@code Consumable#onConsume}) and may
 * turn it into a remainder ({@code UseRemainder}), puts the result in the hand, then calls {@code
 * stopUsingItem}. The count is noted right before {@code finishUsingItem} and compared right before
 * {@code stopUsingItem}, when {@code useItem} still holds the used stack. Only on a finished use,
 * never per tick; other entities pay one {@code instanceof}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
  @Shadow protected ItemStack useItem;

  @Inject(
      method = "completeUsingItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
  private void shulkertoolkit$noteCountBeforeFinish(
      CallbackInfo callback, @Share("countBeforeFinish") LocalIntRef countBefore) {
    countBefore.set(useItem.getCount());
  }

  @Inject(
      method = "completeUsingItem",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/LivingEntity;stopUsingItem()V"))
  private void shulkertoolkit$refillIfRunOut(
      CallbackInfo callback,
      @Local InteractionHand hand,
      @Share("countBeforeFinish") LocalIntRef countBefore) {
    if ((Object) this instanceof ServerPlayer player) {
      ShulkerPick.afterUseFinished(player, hand, useItem, countBefore.get());
    }
  }
}
