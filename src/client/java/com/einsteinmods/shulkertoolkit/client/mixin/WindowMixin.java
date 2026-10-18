package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.StorageInput;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forgets modifier presses when the window gains or loses focus. Vanilla {@code Window#handleEvent}
 * calls the private {@code #onFocus} for SDL's focus gained and lost events; a key released while
 * another app has focus never reaches the game. Only on focus changes.
 */
@Mixin(Window.class)
abstract class WindowMixin {
  @Inject(method = "onFocus", at = @At("HEAD"))
  private void shulkertoolkit$forgetModifiers(boolean focused, CallbackInfo ci) {
    StorageInput.modifiers().clear();
  }
}
