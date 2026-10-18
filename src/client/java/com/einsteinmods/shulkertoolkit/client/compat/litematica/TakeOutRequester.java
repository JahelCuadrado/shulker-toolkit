package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.network.TakeOutPayload;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Asks the server ({@link TakeOutPayload}) to take an item a schematic tool needs out of a shulker
 * box in the inventory. The server applies it like a pick block and syncs the hotbar, so the tool's
 * next attempt (next tick for Easy Place held down and the printer, next click otherwise) finds the
 * item loose and places it. Client thread only.
 *
 * <p>Cost, paid only when a tool needs an item that is not loose: one {@code
 * Inventory#findSlotMatchingItem} (36 slots); while a request is unanswered, one more for the
 * requested item; else, once per item per tick, one read of the boxes (an item no box holds is
 * remembered for the rest of the tick in a reused set, so a printer scanning hundreds of positions
 * pays once). At most one packet per request; no allocation unless a packet is sent.
 */
final class TakeOutRequester {
  /** Ticks to wait for the server's answer before a lost or refused request may be sent again. */
  private static final int ANSWER_TIMEOUT_TICKS = 20;

  private static final int NO_PLAYER = -1;
  private static final int REQUESTED_COUNT = 1;

  private final ClientShulkerState state;

  /** Items no box held during {@link #memoTick}. Reused: cleared, never reallocated. */
  private final ReferenceOpenHashSet<Item> notInAnyBox = new ReferenceOpenHashSet<>();

  /** Entity id of the player the tick counts below belong to (a respawn makes a new player). */
  private int playerId = NO_PLAYER;

  private int memoTick;
  private ItemStack pending = ItemStack.EMPTY;
  private int pendingSinceTick;

  TakeOutRequester(ClientShulkerState state) {
    this.state = state;
  }

  /**
   * Requests {@code wanted} when it is in no loose stack but a shulker box holds it.
   *
   * @return true when a request was sent or one is still awaiting the server's answer, so the
   *     caller should not move items itself meanwhile; false when the server cannot help (no mod,
   *     pick forbidden, creative, already loose, or in no box)
   */
  boolean request(LocalPlayer player, ItemStack wanted) {
    if (wanted.isEmpty() || !state.pickAllowed() || player.hasInfiniteMaterials()) {
      return false;
    }
    Inventory inventory = player.getInventory();
    if (inventory.findSlotMatchingItem(wanted) != PickPlanner.NO_SLOT) {
      return false;
    }
    int now = player.tickCount;
    if (player.getId() != playerId) {
      playerId = player.getId();
      pending = ItemStack.EMPTY;
      notInAnyBox.clear();
      memoTick = now;
    }
    if (awaitingAnswer(inventory, now)) {
      return true;
    }
    if (memoTick != now) {
      notInAnyBox.clear();
      memoTick = now;
    }
    Item item = wanted.getItem();
    if (notInAnyBox.contains(item)) {
      return false;
    }
    if (!ShulkerPick.anyBoxHolds(inventory.getNonEquipmentItems(), wanted)) {
      notInAnyBox.add(item);
      return false;
    }
    if (!ClientPlayNetworking.canSend(TakeOutPayload.TYPE)) {
      return false;
    }
    pending = wanted.copyWithCount(REQUESTED_COUNT);
    pendingSinceTick = now;
    ClientPlayNetworking.send(new TakeOutPayload(pending));
    return true;
  }

  /** Whether the last request is still unanswered: not yet loose and not timed out. */
  private boolean awaitingAnswer(Inventory inventory, int now) {
    if (pending.isEmpty()) {
      return false;
    }
    int waited = now - pendingSinceTick;
    if (waited < 0
        || waited >= ANSWER_TIMEOUT_TICKS
        || inventory.findSlotMatchingItem(pending) != PickPlanner.NO_SLOT) {
      pending = ItemStack.EMPTY;
      return false;
    }
    return true;
  }
}
