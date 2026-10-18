package com.einsteinmods.shulkertoolkit.client.compat.litematica.mixin;

import com.einsteinmods.shulkertoolkit.client.compat.litematica.AutoTakeOut;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Auto Take Out on a right-click; applied only with Litematica installed. Vanilla {@code
 * Minecraft#startUseItem} runs once per use-key press and every 4 ticks while held: it sets that
 * 4-tick delay, then does nothing while {@code LocalPlayer#isHandsBusy}. Reporting busy hands when
 * {@link AutoTakeOut#onUseKey} requested a block while another one was held skips this use (the
 * wrong block is not placed) and keeps vanilla's repeat, with no callback object per press.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  @ModifyExpressionValue(
      method = "startUseItem",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"),
      require = 0)
  private boolean shulkertoolkit$autoTakeOut(boolean handsBusy) {
    return handsBusy || AutoTakeOut.onUseKey();
  }
}
