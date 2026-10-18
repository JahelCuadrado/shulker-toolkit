package com.einsteinmods.shulkertoolkit.core;

import org.jspecify.annotations.Nullable;

/**
 * Decides what taking an item out of the shulker boxes in an inventory does, independent of
 * Minecraft types: which box, how many items, and where the item already in the target hotbar slot
 * goes. Runs only on a pick or when a held stack runs out, never per tick.
 */
public final class PickPlanner {
  /** No slot: nothing found, or nothing to move. */
  public static final int NO_SLOT = -1;

  private static final int SINGLE_ITEM_AMOUNT = 1;

  private PickPlanner() {}

  /** The inventory as the planner reads it: matching items per slot of each shulker box. */
  public interface BoxView {
    /** Inventory slots searched, in search order (vanilla slot order). */
    int inventorySlots();

    /** Slots of the shulker box held in {@code inventorySlot}; 0 when it holds no box. */
    int boxSlots(int inventorySlot);

    /** Items in {@code boxSlot} of that box matching the wanted item; 0 when none. */
    int matchingCount(int inventorySlot, int boxSlot);
  }

  /**
   * Take {@code amount} matching items out of the box in {@code inventorySlot}, draining its
   * matching slots in slot order.
   */
  public record Extraction(int inventorySlot, int amount) {}

  /**
   * Where the picked stack goes.
   *
   * @param hotbarSlot slot that receives the picked stack and becomes selected
   * @param displacedTo slot that receives the stack previously in {@code hotbarSlot}, or {@link
   *     #NO_SLOT} when {@code hotbarSlot} is empty
   */
  public record Placement(int hotbarSlot, int displacedTo) {}

  /** Items one pick takes: one, or a full stack of {@code maxStackSize}. */
  public static int wantedAmount(PickMode mode, int maxStackSize) {
    return mode == PickMode.SINGLE_ITEM ? SINGLE_ITEM_AMOUNT : Math.max(1, maxStackSize);
  }

  /**
   * The first box (in inventory order) with any matching item, and up to {@code wanted} of its
   * matching items; only that box is used, even if it holds fewer. Null when no box matches.
   */
  public static @Nullable Extraction findExtraction(BoxView view, int wanted) {
    if (wanted <= 0) {
      return null;
    }
    for (int inventorySlot = 0; inventorySlot < view.inventorySlots(); inventorySlot++) {
      int boxSlots = view.boxSlots(inventorySlot);
      int found = 0;
      for (int boxSlot = 0; boxSlot < boxSlots && found < wanted; boxSlot++) {
        found += view.matchingCount(inventorySlot, boxSlot);
      }
      if (found > 0) {
        return new Extraction(inventorySlot, Math.min(found, wanted));
      }
    }
    return null;
  }

  /**
   * Where a picked stack goes, never losing an item: an empty target slot takes it as is; an
   * occupied one first moves its stack to {@code freeSlot}. Null (refuse the pick) when the target
   * is occupied and there is no other free slot. Unlike vanilla {@code Inventory#addAndPickItem},
   * which overwrites the held stack when the inventory is full.
   *
   * @param targetHotbarSlot vanilla {@code Inventory#getSuitableHotbarSlot}
   * @param targetEmpty whether that slot is empty
   * @param freeSlot vanilla {@code Inventory#getFreeSlot}, or {@link #NO_SLOT}
   */
  public static @Nullable Placement placement(
      int targetHotbarSlot, boolean targetEmpty, int freeSlot) {
    if (targetEmpty) {
      return new Placement(targetHotbarSlot, NO_SLOT);
    }
    if (freeSlot == NO_SLOT || freeSlot == targetHotbarSlot) {
      return null;
    }
    return new Placement(targetHotbarSlot, freeSlot);
  }

  /** Items to take from a slot holding {@code available} when {@code remaining} are still due. */
  public static int takeFromSlot(int available, int remaining) {
    return Math.max(0, Math.min(available, remaining));
  }
}
