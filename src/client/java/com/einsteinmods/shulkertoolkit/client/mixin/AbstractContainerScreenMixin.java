package com.einsteinmods.shulkertoolkit.client.mixin;

import com.einsteinmods.shulkertoolkit.client.storage.StorageIndicator;
import com.einsteinmods.shulkertoolkit.client.storage.StorageScreenHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container screen hooks of Easy Shulker Boxes' {@code AbstractContainerScreenMixin}: vanilla
 * {@code AbstractContainerScreen#extractSlot} draws one slot's item and decorations, so the slot is
 * noted around it for the "+" indicator (two field writes per slot per frame); {@code
 * #onMouseClickAction} runs before every slot click, including the creative screen's client-side
 * ones, so the control scheme is current for the click and its prediction.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
  @Inject(method = "extractSlot", at = @At("HEAD"))
  private void shulkertoolkit$noteSlot(
      GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
    StorageIndicator.setSlotBeingDrawn((AbstractContainerScreen<?>) (Object) this, slot);
  }

  @Inject(method = "extractSlot", at = @At("RETURN"))
  private void shulkertoolkit$forgetSlot(
      GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
    StorageIndicator.setSlotBeingDrawn(null, null);
  }

  @Inject(method = "onMouseClickAction", at = @At("HEAD"))
  private void shulkertoolkit$sendControlScheme(
      @Nullable Slot slot, ContainerInput containerInput, CallbackInfo ci) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player != null) {
      StorageScreenHooks.sendControlScheme(player);
    }
  }
}
