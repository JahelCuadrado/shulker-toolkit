package com.einsteinmods.shulkertoolkit.client.compat.litematica.mixin;

import com.einsteinmods.shulkertoolkit.client.compat.litematica.LitematicaTakeOut;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fi.dy.masa.litematica.util.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Easy Place and schematic pick block take the block out of a shulker box. Litematica 0.29.1 {@code
 * InventoryUtils#schematicWorldPickBlock}, in survival, only selects a loose stack ({@code
 * Inventory#findSlotMatchingItem}); with none it reads {@code pickBlockShulkers} (its only {@code
 * ConfigBoolean#getBooleanValue} call, evaluated only when no loose stack was found) and may move
 * the box itself to the hand with container clicks. This hook sits on that read: it asks the server
 * for the item and, when it did, skips Litematica's box move so the two never race. Creative stays
 * Litematica's.
 */
@Pseudo
@Mixin(InventoryUtils.class)
abstract class InventoryUtilsMixin {
  @ModifyExpressionValue(
      method = "schematicWorldPickBlock",
      at =
          @At(
              value = "INVOKE",
              target = "Lfi/dy/masa/malilib/config/options/ConfigBoolean;getBooleanValue()Z"),
      require = 0)
  private static boolean shulkertoolkit$takeOutOfShulkerBox(
      boolean pickBoxes,
      @Local(argsOnly = true) ItemStack wanted,
      @Local(argsOnly = true) Minecraft minecraft) {
    boolean requested = LitematicaTakeOut.onSchematicPickWithoutLooseStack(wanted, minecraft);
    return pickBoxes && !requested;
  }
}
