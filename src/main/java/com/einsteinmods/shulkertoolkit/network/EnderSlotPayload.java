package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: one changed slot of the player's ender chest while an ender chest is open, as
 * Easy Shulker Boxes' {@code ClientboundEnderChestSlotMessage}. Sent only when vanilla's menu sync
 * reports that slot changed.
 *
 * @param slot index in the ender chest
 * @param item copy of the new stack
 */
public record EnderSlotPayload(int slot, ItemStack item) implements CustomPacketPayload {
  public static final Type<EnderSlotPayload> TYPE = new Type<>(ShulkerToolkit.id("ender_slot"));

  public static final StreamCodec<RegistryFriendlyByteBuf, EnderSlotPayload> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.VAR_INT,
          EnderSlotPayload::slot,
          ItemStack.OPTIONAL_STREAM_CODEC,
          EnderSlotPayload::item,
          EnderSlotPayload::new);

  @Override
  public Type<EnderSlotPayload> type() {
    return TYPE;
  }
}
