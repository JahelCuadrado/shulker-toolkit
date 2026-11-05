package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.server.EnderChestOwner;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ender chest changes made outside the mod and outside an ender chest menu (commands, other mods):
 * vanilla {@code SimpleContainer#setItem}, {@code #removeItem} and {@code #addItem} all end in
 * {@code #setChanged}, which {@code PlayerEnderChestContainer} does not override. One {@code
 * instanceof} per change of any simple container.
 */
@Mixin(SimpleContainer.class)
abstract class SimpleContainerMixin {
  @Inject(method = "setChanged", at = @At("HEAD"))
  private void shulkertoolkit$noteEnderChestChange(CallbackInfo ci) {
    if ((Object) this instanceof EnderChestOwner enderChest) {
      ServerPlayer owner = enderChest.shulkertoolkit$owner();
      if (owner != null) {
        EnderChestSync.onChanged(owner);
      }
    }
  }
}
