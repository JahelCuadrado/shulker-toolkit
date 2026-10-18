package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * Client → server: one click in a pinned window, as vanilla's {@code
 * ServerboundContainerClickPacket} carries one click in a menu. Sent once per click, never per
 * tick. The server finds the container item again from {@link #levels} and checks every level still
 * holds the item the client showed before it applies anything.
 *
 * @param containerId the open menu, as vanilla's click packet
 * @param levels the item's path: the menu slot first, then the slot inside each item before it
 * @param slot the window slot clicked ({@link PinnedAction#QUICK_MOVE_IN}: the menu slot moved in;
 *     bundles: -1 for the free part of the window)
 * @param button the mouse button ({@code PICKUP}) or the hotbar slot ({@code SWAP})
 */
public record PinnedWindowClickPayload(
    int containerId, List<Level> levels, PinnedAction action, int slot, int button)
    implements CustomPacketPayload {
  public static final Type<PinnedWindowClickPayload> TYPE =
      new Type<>(ShulkerToolkit.id("pinned_window_click"));

  private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_CODEC =
      ByteBufCodecs.registry(Registries.ITEM);

  public static final StreamCodec<RegistryFriendlyByteBuf, PinnedWindowClickPayload> CODEC =
      StreamCodec.of(PinnedWindowClickPayload::write, PinnedWindowClickPayload::read);

  /**
   * One level of the path.
   *
   * @param index slot of the item in the menu (first level) or in the item above
   * @param item what the client saw there
   */
  public record Level(int index, Item item) {}

  @Override
  public Type<PinnedWindowClickPayload> type() {
    return TYPE;
  }

  private static void write(RegistryFriendlyByteBuf buffer, PinnedWindowClickPayload payload) {
    buffer.writeVarInt(payload.containerId());
    buffer.writeByte(payload.levels().size());
    for (Level level : payload.levels()) {
      buffer.writeVarInt(level.index());
      ITEM_CODEC.encode(buffer, level.item());
    }
    buffer.writeByte(payload.action().id());
    buffer.writeVarInt(payload.slot());
    buffer.writeVarInt(payload.button());
  }

  private static PinnedWindowClickPayload read(RegistryFriendlyByteBuf buffer) {
    int containerId = buffer.readVarInt();
    int depth = buffer.readUnsignedByte();
    if (!PinnedPaths.isValidDepth(depth)) {
      throw new DecoderException("Pinned window path of " + depth + " levels");
    }
    List<Level> levels = new ArrayList<>(depth);
    for (int level = 0; level < depth; level++) {
      levels.add(new Level(buffer.readVarInt(), ITEM_CODEC.decode(buffer)));
    }
    int actionId = buffer.readUnsignedByte();
    PinnedAction action = PinnedAction.fromId(actionId);
    if (action == null) {
      throw new DecoderException("Unknown pinned window action " + actionId);
    }
    return new PinnedWindowClickPayload(
        containerId, List.copyOf(levels), action, buffer.readVarInt(), buffer.readVarInt());
  }
}
