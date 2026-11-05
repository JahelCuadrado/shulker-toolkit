package com.einsteinmods.shulkertoolkit.core;

import com.google.gson.annotations.SerializedName;

/**
 * How deep crafting from boxes, the pick from boxes and Litematica's material list look besides the
 * loose inventory stacks: three levels in declaration order, each including the previous ones. The
 * ender chest counts only while an ender chest item is in the inventory.
 *
 * <p>Saved settings store the constant name and the network the {@link #id()}, so both stay stable.
 * The four earlier levels (ids 1-4) read as the level that now holds their containers: their saved
 * names through the serialized alternates, their ids through {@link #fromId}.
 */
public enum CraftDepth {
  /** Level 1: shulker boxes and bundles in the inventory. */
  @SerializedName(
      value = "SHALLOW",
      alternate = {"INVENTORY_BOXES"})
  SHALLOW(5),
  /**
   * Level 2: also bundles inside those boxes, and the ender chest: its loose items, shulker boxes
   * and bundles.
   */
  @SerializedName(
      value = "DEEP",
      alternate = {"ENDER_CHEST_ITEMS", "ENDER_CHEST_BOXES"})
  DEEP(6),
  /** Level 3: also bundles inside the shulker boxes in the ender chest. */
  @SerializedName(
      value = "DEEPEST",
      alternate = {"BUNDLES_IN_BOXES"})
  DEEPEST(7);

  public static final CraftDepth DEFAULT = SHALLOW;

  /** Ids of the four earlier levels, never reused; an older client may still send them. */
  private static final int OLD_INVENTORY_BOXES_ID = 1;

  private static final int OLD_ENDER_CHEST_BOXES_ID = 2;
  private static final int OLD_BUNDLES_IN_BOXES_ID = 3;
  private static final int OLD_ENDER_CHEST_ITEMS_ID = 4;

  private final int id;

  CraftDepth(int id) {
    this.id = id;
  }

  /** Stable network id; never reuse an id for another depth. */
  public int id() {
    return id;
  }

  /** Whether this level includes everything {@code other} reads. */
  public boolean includes(CraftDepth other) {
    return ordinal() >= other.ordinal();
  }

  /** Old ids read as their new level; unknown ids (a newer client) as {@link #DEFAULT}. */
  public static CraftDepth fromId(int id) {
    for (CraftDepth depth : values()) {
      if (depth.id == id) {
        return depth;
      }
    }
    return switch (id) {
      case OLD_INVENTORY_BOXES_ID -> SHALLOW;
      case OLD_ENDER_CHEST_ITEMS_ID, OLD_ENDER_CHEST_BOXES_ID -> DEEP;
      case OLD_BUNDLES_IN_BOXES_ID -> DEEPEST;
      default -> DEFAULT;
    };
  }
}
