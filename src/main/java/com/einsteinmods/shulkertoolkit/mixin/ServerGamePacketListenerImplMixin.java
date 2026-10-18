package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pick block from shulker boxes. Vanilla {@code ServerGamePacketListenerImpl#tryPickItem} (block
 * and entity picks) selects a loose stack found by {@code Inventory#findSlotMatchingItem}, or in
 * creative adds a new one; otherwise it does nothing. This runs right before it builds the
 * selected-slot packet, so vanilla's own sync ({@code ClientboundSetHeldSlotPacket} and {@code
 * inventoryMenu.broadcastChanges()}) also sends the change.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
  @Shadow public ServerPlayer player;

  @Inject(
      method = "tryPickItem",
      at =
          @At(
              value = "NEW",
              target = "net/minecraft/network/protocol/game/ClientboundSetHeldSlotPacket"))
  private void shulkertoolkit$pickFromShulkerBoxes(
      ItemStack itemStack, CallbackInfo callback, @Local int slotWithExistingItem) {
    if (slotWithExistingItem == PickPlanner.NO_SLOT) {
      ShulkerPick.onPickNotInInventory(player, itemStack);
    }
  }
}
