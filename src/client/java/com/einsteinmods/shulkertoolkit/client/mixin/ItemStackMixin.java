package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.CollapsibleTooltip;
import com.einsteinmods.shulkertoolkit.client.storage.StorageTooltips;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Contents tooltips of container items. Vanilla {@code ItemStack#getTooltipImage} asks {@code
 * Item#getTooltipImage}, empty for block items and vanilla's bundle tooltip for bundles; deciding
 * here first replaces both, as Easy Shulker Boxes does. Also drops vanilla's text list of a shulker
 * box's contents ("Stone x64", "and 3 more..."), which {@code ItemStack#addDetailsToTooltip} adds
 * through {@code addToTooltip(DataComponents.CONTAINER, ...)}; every other component's call is kept
 * (one reference comparison each).
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
  @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
  private void shulkertoolkit$contentsTooltip(
      CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
    CollapsibleTooltip tooltip = StorageTooltips.imageFor((ItemStack) (Object) this);
    if (tooltip != null) {
      cir.setReturnValue(tooltip.image());
    }
  }

  @WrapWithCondition(
      method = "addDetailsToTooltip",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V"))
  private boolean shulkertoolkit$keepUnlessInGrid(
      ItemStack stack,
      DataComponentType<?> type,
      Item.TooltipContext context,
      TooltipDisplay display,
      Consumer<Component> builder,
      TooltipFlag flag) {
    return type != DataComponents.CONTAINER || !StorageTooltips.replacesContainerLines(stack);
  }
}
