package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.server.EnderChestOwner;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ender chest sync. The ender chest container learns its owner on creation and when {@code
 * ServerPlayer#restoreFrom} hands the old player's container to the new one (respawn, End exit).
 * Sync on opening. Vanilla {@code ServerPlayer#openMenu} (and the inventory menu setup) calls the
 * private {@code #initMenu}, which adds the menu's slot listener; Fabric has no event for a menu
 * opening. Once per opened menu.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
  @Inject(method = "initMenu", at = @At("TAIL"))
  private void shulkertoolkit$syncEnderChestOnOpen(AbstractContainerMenu menu, CallbackInfo ci) {
    EnderChestSync.onMenuOpened((ServerPlayer) (Object) this, menu);
  }

  @Inject(method = "<init>", at = @At("TAIL"))
  private void shulkertoolkit$ownEnderChest(CallbackInfo ci) {
    ownEnderChest();
  }

  @Inject(method = "restoreFrom", at = @At("TAIL"))
  private void shulkertoolkit$ownRestoredEnderChest(
      ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
    ownEnderChest();
  }

  private void ownEnderChest() {
    ServerPlayer self = (ServerPlayer) (Object) this;
    if ((Object) self.getEnderChestInventory() instanceof EnderChestOwner enderChest) {
      enderChest.shulkertoolkit$setOwner(self);
    }
  }
}
