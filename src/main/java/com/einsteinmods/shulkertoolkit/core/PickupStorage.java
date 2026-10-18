package com.einsteinmods.shulkertoolkit.core;

/**
 * Rules for storing picked-up items in shulker boxes, independent of Minecraft types. Runs only
 * when a player touches an item it may pick up; work is bounded by the box slot count.
 */
public final class PickupStorage {
  /** Slot count of an empty box slot. */
  public static final int EMPTY_SLOT = 0;

  private PickupStorage() {}

  /**
   * Whether boxes already holding a picked-up item take it before the inventory ("direct" option),
   * unless a loose stack of it is carried (the caller checks that afterwards: it costs a scan).
   */
  public static boolean directToBox(ServerPolicy policy, PlayerPreferences preferences) {
    return policy.allowPickupIntoBoxes() && preferences.pickupDirectToBox();
  }

  /** Whether what the inventory cannot take goes to the boxes ("overflow" option). */
  public static boolean overflowToBoxes(ServerPolicy policy, PlayerPreferences preferences) {
    return policy.allowPickupIntoBoxes() && preferences.pickupOverflowToBoxes();
  }

  /**
   * Items that one more box slot holding {@code slotCount} of the picked-up item takes, at most
   * {@code remaining} ({@link #EMPTY_SLOT} for an empty slot). Never negative: a slot already over
   * {@code maxCount} takes nothing.
   */
  public static int addable(int slotCount, int maxCount, int remaining) {
    return Math.min(Math.max(maxCount - slotCount, 0), remaining);
  }
}
