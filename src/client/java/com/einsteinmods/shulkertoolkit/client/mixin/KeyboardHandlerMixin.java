package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.StorageInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds modifier presses to {@code ModifierTracker}. Vanilla {@code SDLEventHandler#handleKeyEvent}
 * hands every key down, up and repeat of the window to {@code KeyboardHandler#keyPress}. One switch
 * per key event, nothing per frame.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
  @Inject(method = "keyPress", at = @At("HEAD"))
  private void shulkertoolkit$trackModifiers(
      long handle, int action, KeyEvent event, CallbackInfo ci) {
    StorageInput.modifiers().onKey(event.key(), action);
  }
}
