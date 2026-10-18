package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.StorageFillBar;
import com.einsteinmods.shulkertoolkit.client.storage.StorageIndicator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item decorations of container items, for every item drawn in a GUI, every frame. Vanilla {@code
 * GuiGraphicsExtractor#itemBar} draws a bar only when {@code Item#isBarVisible}, false for an
 * undamaged block item; hooking the drawing instead keeps the client-only option out of the common
 * item code. {@code #itemDecorations} (bar, cooldown, count) is followed by the top right markers
 * ({@link StorageIndicator}: pinned window eye, Easy Shulker Boxes' "+"). Any other item pays one
 * {@code instanceof} and a few field reads.
 */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {
  @Inject(method = "itemBar", at = @At("HEAD"), cancellable = true)
  private void shulkertoolkit$drawFillBar(ItemStack itemStack, int x, int y, CallbackInfo ci) {
    if (StorageFillBar.draw((GuiGraphicsExtractor) (Object) this, itemStack, x, y)) {
      ci.cancel();
    }
  }

  @Inject(
      method =
          "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
      at = @At("TAIL"))
  private void shulkertoolkit$drawIndicator(
      Font font, ItemStack itemStack, int x, int y, @Nullable String countText, CallbackInfo ci) {
    if (!itemStack.isEmpty()) {
      StorageIndicator.extract((GuiGraphicsExtractor) (Object) this, font, itemStack, x, y);
    }
  }
}
