package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.transfer.StorageClicks;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Container item clicks. Vanilla {@code AbstractContainerMenu#doClick} asks {@code
 * #tryItemClickBehaviourOverride} on every pickup click, which consults the carried stack's {@code
 * Item#overrideStackedOnOther}, then the clicked one's {@code Item#overrideOtherStackedOnMe} (only
 * {@code BundleItem} overrides them). Deciding here first replaces the bundle's own rules too, as
 * Easy Shulker Boxes does. Runs on both sides; a click without a container item pays two {@code
 * instanceof} checks per stack.
 */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
  @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
  private void shulkertoolkit$containerItemClick(
      Player player,
      ClickAction clickAction,
      Slot slot,
      ItemStack clicked,
      ItemStack carried,
      CallbackInfoReturnable<Boolean> cir) {
    TriState result =
        StorageClicks.onItemClicked(
            (AbstractContainerMenu) (Object) this, player, clickAction, slot, clicked, carried);
    if (result != TriState.DEFAULT) {
      cir.setReturnValue(result.get());
    }
  }
}
