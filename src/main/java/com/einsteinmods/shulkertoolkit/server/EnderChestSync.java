package com.einsteinmods.shulkertoolkit.server;

import com.einsteinmods.shulkertoolkit.network.EnderContentsPayload;
import com.einsteinmods.shulkertoolkit.network.EnderSlotPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * and after an edit from an ender chest item, single slots while an ender chest is open, and the
 * whole chest once at the end of a tick in which something else changed it (a command, another
 * mod). Only to clients with the mod and only when the policy allows the ender chest preview. Per
 * tick, one emptiness check while nothing changed. Server thread only.
 */
public final class EnderChestSync {
  /** Players whose ender chest changed this tick and was not sent since. */
  private static final Set<ServerPlayer> CHANGED = new HashSet<>();

  private EnderChestSync() {}

  /** Sends the whole ender chest to {@code player}, when allowed. */
  public static void sendContents(ServerPlayer player) {
    if (!CHANGED.isEmpty()) {
      CHANGED.remove(player);
    }
    send(player);
  }

  /**
   * {@code player}'s ender chest changed ({@code SimpleContainerMixin}): sent at the tick's end.
   */
  public static void onChanged(ServerPlayer player) {
    CHANGED.add(player);
  }

  /** Whether a change of {@code player}'s ender chest waits for the tick's end (GameTests). */
  public static boolean hasPendingChange(ServerPlayer player) {
    return CHANGED.contains(player);
  }

  /**
   * End of a server tick: sends each changed ender chest once. Not with its menu open, whose slot
   * listener already sent each changed slot.
   */
  public static void sendChanged() {
    if (CHANGED.isEmpty()) {
      return;
    }
    for (ServerPlayer player : CHANGED) {
      if (!(player.containerMenu instanceof ChestMenu menu
          && menu.getContainer() == player.getEnderChestInventory())) {
        send(player);
      }
    }
    CHANGED.clear();
  }

  private static void send(ServerPlayer player) {
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
