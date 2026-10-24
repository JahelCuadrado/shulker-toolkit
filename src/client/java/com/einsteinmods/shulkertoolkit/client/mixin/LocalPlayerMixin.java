package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.PinnedWindows;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unpins every window when the player closes the open container (Escape, the inventory key):
 * vanilla {@code AbstractContainerScreen#onClose} calls {@code LocalPlayer#closeContainer}, which
 * sends the close packet; a close from the server ({@code clientSideCloseContainer} alone) may be a
 * switch to another menu, so it does not come here. Once per close.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
  @Inject(method = "closeContainer", at = @At("HEAD"))
  private void shulkertoolkit$unpinOnClose(CallbackInfo ci) {
    PinnedWindows.unpinAll();
  }
}
