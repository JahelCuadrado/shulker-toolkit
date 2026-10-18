package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: the player's control scheme, as Easy Shulker Boxes' {@code
 * ServerboundContainerClientInputMessage}. Sent right before a container click when it changed
 * since the last one (the single item key went down or up, or the setting changed), never per tick.
 */
public record ControlSchemePayload(ControlScheme controlScheme) implements CustomPacketPayload {
  public static final Type<ControlSchemePayload> TYPE =
      new Type<>(ShulkerToolkit.id("control_scheme"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ControlSchemePayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            buffer.writeBoolean(payload.controlScheme().moveSingleItem());
            buffer.writeByte(payload.controlScheme().clickScheme().id());
          },
          buffer ->
              new ControlSchemePayload(
                  new ControlScheme(buffer.readBoolean(), ClickScheme.fromId(buffer.readByte()))));

  @Override
  public Type<ControlSchemePayload> type() {
    return TYPE;
  }
}
