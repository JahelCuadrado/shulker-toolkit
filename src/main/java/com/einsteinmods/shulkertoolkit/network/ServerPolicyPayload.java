package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client, on join: features this player may use. Its arrival also tells the client the
 * server has the mod; until then every server-backed feature stays off.
 */
public record ServerPolicyPayload(ServerPolicy policy) implements CustomPacketPayload {
  public static final Type<ServerPolicyPayload> TYPE = new Type<>(ShulkerToolkit.id("policy"));

  public static final StreamCodec<RegistryFriendlyByteBuf, ServerPolicyPayload> CODEC =
      StreamCodec.of(
          (buffer, payload) -> {
            ServerPolicy policy = payload.policy();
            buffer.writeBoolean(policy.allowTransfer());
            buffer.writeBoolean(policy.allowPick());
            buffer.writeBoolean(policy.allowRefill());
            buffer.writeBoolean(policy.allowEnderPreview());
            buffer.writeBoolean(policy.allowMouseDragging());
            buffer.writeBoolean(policy.allowSingleItemMovement());
            buffer.writeBoolean(policy.allowPickupIntoBoxes());
            buffer.writeBoolean(policy.allowCraftFromBoxes());
            buffer.writeBoolean(policy.allowPinnedWindows());
          },
          buffer ->
              new ServerPolicyPayload(
                  new ServerPolicy(
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean(),
                      buffer.readBoolean())));

  @Override
  public Type<ServerPolicyPayload> type() {
    return TYPE;
  }
}
