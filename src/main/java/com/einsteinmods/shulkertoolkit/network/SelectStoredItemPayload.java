package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the slot selected in a container item by scrolling or with the arrow keys, as
 * vanilla {@code ServerboundSelectBundleItemPacket} and Easy Shulker Boxes' {@code
 * ServerboundSelectedItemMessage}. Sent only when the selection changes or is dropped (the item
 * stops being hovered, is quick-moved or swapped), never per tick.
 *
 * @param menuSlot slot of the item in the open menu, or {@link #CARRIED} for the cursor item
 * @param selectedItem selected slot inside the item, or {@code StorageGrid.NO_ITEM}
 * @param slotClicked the reset comes from a click (containers keep their selection then)
 */
public record SelectStoredItemPayload(int menuSlot, int selectedItem, boolean slotClicked)
    implements CustomPacketPayload {
  /** {@link #menuSlot} of the item held by the cursor. */
  public static final int CARRIED = -1;

  public static final Type<SelectStoredItemPayload> TYPE =
      new Type<>(ShulkerToolkit.id("select_stored_item"));

  public static final StreamCodec<RegistryFriendlyByteBuf, SelectStoredItemPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            buffer.writeVarInt(payload.menuSlot());
            buffer.writeVarInt(payload.selectedItem());
            buffer.writeBoolean(payload.slotClicked());
          },
          buffer ->
              new SelectStoredItemPayload(
                  buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));

  @Override
  public Type<SelectStoredItemPayload> type() {
    return TYPE;
  }
}
