package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.StorageScreenHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the control scheme before a container click, as Easy Shulker Boxes' {@code
 * MultiPlayerGameModeMixin}: vanilla {@code MultiPlayerGameMode#handleContainerInput} predicts the
 * click and then sends it, so the scheme packet arrives first. Once per click.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
  @Inject(method = "handleContainerInput", at = @At("HEAD"))
  private void shulkertoolkit$sendControlScheme(
      int containerId,
      int slotNum,
      int buttonNum,
      ContainerInput containerInput,
      Player player,
      CallbackInfo ci) {
    if (player instanceof LocalPlayer localPlayer
        && containerId == player.containerMenu.containerId) {
      StorageScreenHooks.sendControlScheme(localPlayer);
    }
  }
}
