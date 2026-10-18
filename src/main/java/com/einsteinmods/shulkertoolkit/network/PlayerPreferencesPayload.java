package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the preferences the server acts on. Sent when the server policy arrives (join)
 * and when the player saves the settings; never per tick.
 */
public record PlayerPreferencesPayload(PlayerPreferences preferences)
    implements CustomPacketPayload {
  public static final Type<PlayerPreferencesPayload> TYPE =
      new Type<>(ShulkerToolkit.id("preferences"));

  public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPreferencesPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            buffer.writeByte(payload.preferences().pickMode().id());
            buffer.writeBoolean(payload.preferences().autoRefill());
            buffer.writeBoolean(payload.preferences().bundleInteractions());
            buffer.writeBoolean(payload.preferences().pickupOverflowToBoxes());
            buffer.writeBoolean(payload.preferences().pickupDirectToBox());
            buffer.writeBoolean(payload.preferences().craftFromBoxes());
            buffer.writeByte(payload.preferences().craftDepth().id());
          },
          buffer ->
              new PlayerPreferencesPayload(
                  new PlayerPreferences(
                      PickMode.fromId(buffer.readByte()),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      CraftDepth.fromId(buffer.readByte()))));

  @Override
  public Type<PlayerPreferencesPayload> type() {
    return TYPE;
  }
}
