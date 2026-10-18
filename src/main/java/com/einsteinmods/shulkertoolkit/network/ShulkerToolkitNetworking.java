package com.einsteinmods.shulkertoolkit.network;

import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Registers payloads and server-side handlers. Handlers run on the server thread. */
public final class ShulkerToolkitNetworking {
  private ShulkerToolkitNetworking() {}

  public static void register() {
    PayloadTypeRegistry.serverboundPlay()
        .register(PlayerPreferencesPayload.TYPE, PlayerPreferencesPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(SelectStoredItemPayload.TYPE, SelectStoredItemPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(ControlSchemePayload.TYPE, ControlSchemePayload.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(TakeOutPayload.TYPE, TakeOutPayload.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(PinnedWindowClickPayload.TYPE, PinnedWindowClickPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(ServerPolicyPayload.TYPE, ServerPolicyPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(EnderContentsPayload.TYPE, EnderContentsPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(EnderSlotPayload.TYPE, EnderSlotPayload.CODEC);

    ServerPlayNetworking.registerGlobalReceiver(
        PlayerPreferencesPayload.TYPE,
        (payload, context) ->
            ServerPlayerSessions.updatePreferences(
                context.player().getUUID(), payload.preferences()));

    ServerPlayNetworking.registerGlobalReceiver(
        SelectStoredItemPayload.TYPE,
        (payload, context) -> selectStoredItem(context.player(), payload));

    ServerPlayNetworking.registerGlobalReceiver(
        ControlSchemePayload.TYPE,
        (payload, context) -> setControlScheme(context.player(), payload.controlScheme()));

    ServerPlayNetworking.registerGlobalReceiver(
        TakeOutPayload.TYPE,
        (payload, context) -> {
          UUID playerId = context.player().getUUID();
          ShulkerPick.takeOutOnRequest(
              context.player(),
              payload.wanted(),
              ServerPlayerSessions.policy(playerId),
              ServerPlayerSessions.preferences(playerId));
        });

    ServerPlayNetworking.registerGlobalReceiver(
        PinnedWindowClickPayload.TYPE,
        (payload, context) -> PinnedWindowClicks.handle(context.player(), payload));

    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          ServerPolicy policy =
              ServerPlayerSessions.join(
                  handler.player.getUUID(), server.isSingleplayerOwner(handler.player.nameAndId()));
          if (ServerPlayNetworking.canSend(handler.player, ServerPolicyPayload.TYPE)) {
            sender.sendPacket(new ServerPolicyPayload(policy));
          }
          EnderChestSync.sendContents(handler.player);
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          ServerPlayerSessions.remove(handler.player.getUUID());
          StorageStates.forget(handler.player.getUUID());
        });
    ServerPlayerEvents.AFTER_RESPAWN.register(
        (oldPlayer, newPlayer, alive) -> EnderChestSync.sendContents(newPlayer));
    ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
        (player, origin, destination) -> EnderChestSync.sendContents(player));
  }

  /**
   * Applies a selection, as vanilla's {@code ServerboundSelectBundleItemPacket} handler: only for a
   * container item in the open menu (or on its cursor), only when transfer is allowed.
   */
  public static void selectStoredItem(ServerPlayer player, SelectStoredItemPayload payload) {
    if (payload.selectedItem() < StorageGrid.NO_ITEM || !TransferGate.allows(player)) {
      return;
    }
    AbstractContainerMenu menu = player.containerMenu;
    int menuSlot = payload.menuSlot();
    ItemStack stack;
    if (menuSlot == SelectStoredItemPayload.CARRIED) {
      stack = menu.getCarried();
    } else if (menuSlot >= 0 && menu.isValidSlotIndex(menuSlot)) {
      stack = menu.getSlot(menuSlot).getItem();
    } else {
      return;
    }
    StorageKind kind = StorageKind.of(stack);
    if (kind != null) {
      kind.toggleSelectedItem(stack, player, payload.selectedItem(), payload.slotClicked());
    }
  }

  /** Stores the control scheme; single item movement only when the policy allows it. */
  public static void setControlScheme(ServerPlayer player, ControlScheme scheme) {
    boolean single =
        scheme.moveSingleItem()
            && ServerPlayerSessions.policy(player.getUUID()).allowSingleItemMovement();
    StorageStates.of(player).setControlScheme(new ControlScheme(single, scheme.clickScheme()));
  }
}
