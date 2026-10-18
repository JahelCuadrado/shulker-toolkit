package com.einsteinmods.shulkertoolkit.pick;

import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.core.PickupStorage;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.server.PlayerSession;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Stores items a player picks up from the ground in the shulker boxes of their hotbar and main
 * inventory (slots 0-35, in slot order), around vanilla {@code ItemEntity#playerTouch}'s {@code
 * Inventory#add}. Two options, both off by default: "overflow" (what the inventory cannot take goes
 * to the boxes) and "direct" (boxes already holding the item take it first, unless a loose stack of
 * it is carried). Only players whose client has the mod, and only when the server policy allows it.
 *
 * <p>Vanilla's pickup effects follow the stack: {@code playerTouch} calls {@code Player#take} (the
 * {@code ClientboundTakeItemEntityPacket} that plays the pickup animation and sound on the client),
 * awards {@code Stats.ITEM_PICKED_UP} and calls {@code Player#onItemPickup} (the thrown item
 * advancement trigger) only when {@code Inventory#add} reports the whole stack taken, so storing
 * the rest in boxes reports it taken; a partial pickup leaves the rest on the ground with no
 * animation, as vanilla. Server thread only.
 *
 * <p>Cost, paid by the server: {@code playerTouch} reaches {@code Inventory#add} once per tick for
 * each item a player touches and may pick up (every tick while the inventory is full), so a touch
 * that stores nothing creates nothing. With both options off: one {@code instanceof} and one
 * session lookup before the inventory, nothing after a full pickup. With an option on: at most 36
 * inventory slots × 27 box slots read once per option, no allocation unless something is stored
 * (the new box contents). Measured per touch, 0 bytes allocated in every case: options off about 4
 * ns (6 ns with a full inventory), no mod client 2 ns; worst cases with a full inventory and 36
 * full boxes: overflow only 1.3 µs, both options 2.7-2.8 µs. Measured 2026-10-16, Apple M5 Pro, JDK
 * 25, 10^6 (2×10^4) warm calls, allocation from {@code
 * ThreadMXBean#getCurrentThreadAllocatedBytes}.
 */
public final class PickupIntoBoxes {
  private PickupIntoBoxes() {}

  /**
   * Hook right before {@code Inventory#add} in {@code ItemEntity#playerTouch}: with the "direct"
   * option, stores {@code picked} in the boxes already holding it unless a loose stack of it is
   * carried. Cheapest checks first.
   */
  public static void beforeInventory(Inventory inventory, ItemStack picked) {
    if (!(inventory.player instanceof ServerPlayer player)) {
      return;
    }
    PlayerSession session = ServerPlayerSessions.modClientSession(player.getUUID());
    if (session != null) {
      storeFirst(player, picked, session.policy(), session.preferences());
    }
  }

  /**
   * Hook on the result of {@code Inventory#add} in {@code ItemEntity#playerTouch}: with the
   * "overflow" option, stores what the inventory left in the boxes. Returns the result {@code
   * playerTouch} acts on: whether the whole stack was taken.
   *
   * @param added vanilla's result: true once the stack is empty (or for infinite materials)
   */
  public static boolean afterInventory(Player toucher, ItemStack picked, boolean added) {
    if (added || !(toucher instanceof ServerPlayer player)) {
      return added;
    }
    PlayerSession session = ServerPlayerSessions.modClientSession(player.getUUID());
    if (session == null) {
      return false;
    }
    return storeOverflow(player, picked, session.policy(), session.preferences());
  }

  /**
   * The "direct" step: stores {@code picked} in the boxes holding it when {@code policy} allows
   * pickups into boxes, the player turned "direct" on and no loose stack of it is carried.
   *
   * @return items stored
   */
  public static int storeFirst(
      ServerPlayer player, ItemStack picked, ServerPolicy policy, PlayerPreferences preferences) {
    if (!PickupStorage.directToBox(policy, preferences) || !fitsInBoxes(picked)) {
      return 0;
    }
    Inventory inventory = player.getInventory();
    if (carriesLoose(inventory, picked)) {
      return 0;
    }
    NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
    DataComponentPatch components = picked.getComponentsPatch();
    int maxCount = StorageKind.maxCount(picked);
    int stored = 0;
    for (int slot = 0; slot < items.size() && !picked.isEmpty(); slot++) {
      ItemStack box = items.get(slot);
      if (BoxContents.roomFor(box, picked, components, maxCount) > 0) {
        stored += BoxContents.store(box, picked, components, maxCount);
      }
    }
    return stored;
  }

  /**
   * The "overflow" step: stores what is left of {@code picked} in the boxes holding it, then in any
   * box with room, when {@code policy} allows pickups into boxes and the player turned "overflow"
   * on.
   *
   * @return whether the whole stack is now taken (inventory and boxes), as {@code Inventory#add}
   *     reports it; also true when the "direct" step already took it all
   */
  public static boolean storeOverflow(
      ServerPlayer player, ItemStack picked, ServerPolicy policy, PlayerPreferences preferences) {
    boolean overflow = PickupStorage.overflowToBoxes(policy, preferences);
    if (!overflow && !PickupStorage.directToBox(policy, preferences)) {
      return false;
    }
    if (overflow && fitsInBoxes(picked)) {
      storeOverflowInBoxes(player.getInventory().getNonEquipmentItems(), picked);
    }
    return picked.isEmpty();
  }

  /** Never a shulker box or another item boxes refuse ({@code ShulkerBoxSlot#mayPlace}). */
  private static boolean fitsInBoxes(ItemStack picked) {
    return !picked.isEmpty() && picked.getItem().canFitInsideContainerItems();
  }

  /**
   * Whether a loose stack of the item is in the hotbar, main inventory ({@code
   * Inventory#findSlotMatchingItem}) or offhand: the slots vanilla {@code
   * Inventory#getSlotWithRemainingSpace} merges into.
   */
  private static boolean carriesLoose(Inventory inventory, ItemStack picked) {
    return inventory.findSlotMatchingItem(picked) != PickPlanner.NO_SLOT
        || ItemStack.isSameItemSameComponents(inventory.getItem(Inventory.SLOT_OFFHAND), picked);
  }

  /**
   * One pass over the boxes: fills those already holding the item, in slot order, and notes the
   * first other box with an empty slot, which then takes the rest (one picked-up stack always fits
   * in one empty slot). Nothing is created unless something goes in.
   */
  private static void storeOverflowInBoxes(NonNullList<ItemStack> items, ItemStack picked) {
    DataComponentPatch components = picked.getComponentsPatch();
    int maxCount = StorageKind.maxCount(picked);
    int firstWithEmptySlot = PickPlanner.NO_SLOT;
    for (int slot = 0; slot < items.size() && !picked.isEmpty(); slot++) {
      ItemStack box = items.get(slot);
      int room = BoxContents.roomFor(box, picked, components, maxCount);
      if (room > 0) {
        BoxContents.store(box, picked, components, maxCount);
      } else if (room == BoxContents.NOT_HOLDING_WITH_EMPTY_SLOT
          && firstWithEmptySlot == PickPlanner.NO_SLOT) {
        firstWithEmptySlot = slot;
      }
    }
    if (!picked.isEmpty() && firstWithEmptySlot != PickPlanner.NO_SLOT) {
      BoxContents.store(items.get(firstWithEmptySlot), picked, components, maxCount);
    }
  }
}
