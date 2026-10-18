package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: take {@code wanted} out of a shulker box in the inventory, as a pick block
 * would. Sent only when a schematic tool (Litematica Easy Place, its printer, or a right-click on a
 * schematic block) needs an item the inventory holds only inside a box; at most one per request.
 *
 * <p>Decoded with vanilla's codec for untrusted client stacks ({@code
 * ServerboundSetCreativeModeSlotPacket}); the server only compares it with box contents.
 *
 * @param wanted the item to take out; only its item and components matter
 */
public record TakeOutPayload(ItemStack wanted) implements CustomPacketPayload {
  public static final Type<TakeOutPayload> TYPE = new Type<>(ShulkerToolkit.id("take_out"));

  public static final StreamCodec<RegistryFriendlyByteBuf, TakeOutPayload> CODEC =
      ItemStack.validatedStreamCodec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC)
          .map(TakeOutPayload::new, TakeOutPayload::wanted);

  @Override
  public Type<TakeOutPayload> type() {
    return TYPE;
  }
}
