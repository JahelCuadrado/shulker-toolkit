package com.einsteinmods.shulkertoolkit.mixin;

import com.einsteinmods.shulkertoolkit.server.EnderChestOwner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives the ender chest container its owner, for {@code SimpleContainerMixin}. */
@Mixin(PlayerEnderChestContainer.class)
abstract class PlayerEnderChestContainerMixin implements EnderChestOwner {
  @Unique private @Nullable ServerPlayer shulkertoolkit$owner;

  @Override
  public @Nullable ServerPlayer shulkertoolkit$owner() {
    return shulkertoolkit$owner;
  }

  @Override
  public void shulkertoolkit$setOwner(ServerPlayer owner) {
    shulkertoolkit$owner = owner;
  }
}
