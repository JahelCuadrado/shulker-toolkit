package com.einsteinmods.shulkertoolkit.server;

import com.einsteinmods.shulkertoolkit.network.EnderContentsPayload;
import com.einsteinmods.shulkertoolkit.network.EnderSlotPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the client's copy of the player's ender chest current, as Easy Shulker Boxes' {@code
 * EnderChestSyncHandler}: the whole chest on join, respawn, dimension change, ender chest opening
 * and after an edit from an ender chest item, single slots while an ender chest is open. Only on
 * those events (nothing per tick), only to clients with the mod and only when the policy allows the
 * ender chest preview. Server thread only.
 */
public final class EnderChestSync {
  private EnderChestSync() {}

  /** Sends the whole ender chest to {@code player}, when allowed. */
  public static void sendContents(ServerPlayer player) {
    if (receives(player)) {
      ServerPlayNetworking.send(player, contentsOf(player));
    }
  }

  /** Sends one ender chest slot to {@code player}, when allowed: after an edit outside a menu. */
  public static void sendSlot(ServerPlayer player, int slot) {
    if (receives(player)) {
      ServerPlayNetworking.send(
          player, new EnderSlotPayload(slot, player.getEnderChestInventory().getItem(slot).copy()));
    }
  }

  /**
   * Called when a menu is set up for {@code player} ({@code ServerPlayer#initMenu}): an ender chest
   * menu sends the whole chest, then each slot change while it stays open. Every other menu pays
   * one {@code instanceof}.
   */
  public static void onMenuOpened(ServerPlayer player, AbstractContainerMenu menu) {
    if (menu instanceof ChestMenu chestMenu
        && chestMenu.getContainer() == player.getEnderChestInventory()
        && receives(player)) {
      ServerPlayNetworking.send(player, contentsOf(player));
      chestMenu.addSlotListener(new SlotForwarder(player));
    }
  }

  /** Copies of every ender chest slot: the payload must not share stacks with the server. */
  public static EnderContentsPayload contentsOf(Player player) {
    List<ItemStack> items = player.getEnderChestInventory().getItems();
    List<ItemStack> copies = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      copies.add(item.copy());
    }
    return new EnderContentsPayload(copies);
  }

  private static boolean receives(ServerPlayer player) {
    return player.connection != null
        && ServerPlayNetworking.canSend(player, EnderContentsPayload.TYPE)
        && ServerPlayerSessions.policy(player.getUUID()).allowEnderPreview();
  }

  /** Forwards vanilla's per-slot menu sync of the ender chest slots. */
  private record SlotForwarder(ServerPlayer player) implements ContainerListener {
    @Override
    public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack itemStack) {
      Slot slot = menu.getSlot(slotIndex);
      if (slot.container == player.getEnderChestInventory()) {
        ServerPlayNetworking.send(
            player, new EnderSlotPayload(slot.getContainerSlot(), itemStack.copy()));
      }
    }

    @Override
    public void dataChanged(AbstractContainerMenu menu, int id, int value) {
      // Ender chest menus have no data slots.
    }
  }
}
