package com.einsteinmods.shulkertoolkit.core;

/**
 * Where the recipe book of the crafting table and the inventory looks for ingredients besides the
 * loose inventory stacks: four levels in declaration order, each including the previous ones. The
 * ender chest counts only while an ender chest item is in the inventory.
 *
 * <p>Saved settings store the constant name and the network the {@link #id()}, so both stay stable:
 * level 2 was added later with a new id, and a saved {@code ENDER_CHEST_BOXES} or {@code
 * BUNDLES_IN_BOXES} (levels 2 and 3 before it) now reads as levels 3 and 4, the same containers
 * plus the new loose ender chest items.
 */
public enum CraftDepth {
  /** Level 1: shulker boxes in the inventory. */
  INVENTORY_BOXES(1),
  /** Level 2: also loose items in the ender chest. */
  ENDER_CHEST_ITEMS(4),
  /** Level 3: also shulker boxes in the ender chest. */
  ENDER_CHEST_BOXES(2),
  /** Level 4: also bundles inside the shulker boxes read (inventory and ender chest). */
  BUNDLES_IN_BOXES(3);

  public static final CraftDepth DEFAULT = INVENTORY_BOXES;

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

  /** Unknown ids (a newer client) fall back to {@link #DEFAULT}. */
  public static CraftDepth fromId(int id) {
    for (CraftDepth depth : values()) {
      if (depth.id == id) {
        return depth;
      }
    }
    return DEFAULT;
  }
}
