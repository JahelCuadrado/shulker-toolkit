package com.einsteinmods.shulkertoolkit.client.compat.litematica.mixin;

import com.einsteinmods.shulkertoolkit.client.compat.litematica.MaterialListCounts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.util.data.ItemType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The material list's available count follows the depth level ({@link MaterialListCounts}).
 * Litematica 0.29.1 {@code MaterialListUtils#updateAvailableCounts} (the list screen and its info
 * HUD) and {@code #createMaterialListFromItems} count the player's inventory with {@code
 * #getInventoryItemCounts}, then read {@code materialListCountEnderCache} (their only {@code
 * ConfigBoolean#getBooleanValue} call) and, when on, add the ender chest cache. The first hook
 * replaces the inventory count; the second skips the cache while that count already holds the ender
 * chest.
 */
@Pseudo
@Mixin(MaterialListUtils.class)
abstract class MaterialListUtilsMixin {
  @Inject(method = "getInventoryItemCounts", at = @At("HEAD"), cancellable = true, require = 0)
  private static void shulkertoolkit$countByDepth(
      Container container, CallbackInfoReturnable<Object2IntOpenHashMap<ItemType>> callback) {
    Object2IntOpenHashMap<ItemType> counts = MaterialListCounts.countFor(container);
    if (counts != null) {
      callback.setReturnValue(counts);
    }
  }

  @ModifyExpressionValue(
      method = {"updateAvailableCounts", "createMaterialListFromItems"},
      at =
          @At(
              value = "INVOKE",
              target = "Lfi/dy/masa/malilib/config/options/ConfigBoolean;getBooleanValue()Z"),
      require = 0)
  private static boolean shulkertoolkit$skipCountedEnderChest(boolean countEnderCache) {
    return countEnderCache && !MaterialListCounts.countsEnderChest();
  }
}
