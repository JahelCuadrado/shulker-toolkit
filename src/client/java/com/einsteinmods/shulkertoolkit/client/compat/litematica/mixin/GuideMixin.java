package com.einsteinmods.shulkertoolkit.client.compat.litematica.mixin;

import com.einsteinmods.shulkertoolkit.client.compat.litematica.LitematicaTakeOut;
import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import me.aleksilassila.litematica.printer.guides.Guide;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The printer takes blocks out of shulker boxes. Litematica Printer 3.2.3 {@code
 * Guide#getRequiredItem} walks the guide's {@code getRequiredItems} and returns the first one in a
 * loose stack ({@code getSlotWithItem}, slots 0-35), else empty, and every guide's {@code
 * canExecute} then skips the position. This reads each {@code getSlotWithItem} result: when the
 * last item is not loose either, none is, so it asks the server for the first item a box holds; the
 * printer places it on a later tick, once the hotbar sync arrived. A loose item costs one
 * comparison. (The hook sits inside the loop because the compiled method drops the list from its
 * frame before the final {@code return Optional.empty()}.)
 */
@Pseudo
@Mixin(Guide.class)
abstract class GuideMixin {
  @ModifyExpressionValue(
      method = "getRequiredItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lme/aleksilassila/litematica/printer/guides/Guide;getSlotWithItem("
                      + "Lnet/minecraft/client/player/LocalPlayer;"
                      + "Lnet/minecraft/world/item/ItemStack;)I"),
      require = 0)
  private int shulkertoolkit$takeOutMissingItems(
      int slot,
      @Local(argsOnly = true) LocalPlayer player,
      @Local List<ItemStack> requiredItems,
      @Local ItemStack requiredItem) {
    if (slot == PickPlanner.NO_SLOT && requiredItems.getLast() == requiredItem) {
      LitematicaTakeOut.onPrinterItemsMissing(player, requiredItems);
    }
    return slot;
  }
}
