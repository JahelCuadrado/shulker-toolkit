package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: every slot of the player's ender chest, as Easy Shulker Boxes' {@code
 * ClientboundEnderChestContentMessage}. Sent on join, respawn and dimension change, when an ender
 * chest opens and after a click edits it from an ender chest item; only to clients with the mod and
 * only when the ender chest preview is allowed.
 *
 * @param items copies of the slots, in order
 */
public record EnderContentsPayload(List<ItemStack> items) implements CustomPacketPayload {
  public static final Type<EnderContentsPayload> TYPE =
      new Type<>(ShulkerToolkit.id("ender_contents"));

  public static final StreamCodec<RegistryFriendlyByteBuf, EnderContentsPayload> CODEC =
      ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(
          EnderContentsPayload::new, EnderContentsPayload::items);

  @Override
  public Type<EnderContentsPayload> type() {
    return TYPE;
  }
}
