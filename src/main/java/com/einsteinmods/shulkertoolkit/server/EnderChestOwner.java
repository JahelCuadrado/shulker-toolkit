package com.einsteinmods.shulkertoolkit.server;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The player whose ender chest a {@code PlayerEnderChestContainer} is: vanilla's container does not
 * know it. Set on the server only ({@code ServerPlayerMixin}); null on the client.
 */
public interface EnderChestOwner {
  @Nullable ServerPlayer shulkertoolkit$owner();

  void shulkertoolkit$setOwner(ServerPlayer owner);
}
