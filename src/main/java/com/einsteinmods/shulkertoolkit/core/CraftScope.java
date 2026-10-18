package com.einsteinmods.shulkertoolkit.core;

import org.jspecify.annotations.Nullable;

/**
 * Which containers crafting from boxes reads for one player: always the shulker boxes in the
 * inventory, plus what the depth and the server allow. Constants only, so resolving allocates
 * nothing.
 */
public enum CraftScope {
  INVENTORY_BOXES(false, false, false),
  WITH_BUNDLES(false, false, true),
  WITH_ENDER_CHEST_ITEMS(true, false, false),
  WITH_ENDER_CHEST_BOXES(true, true, false),
  WITH_ENDER_CHEST_BOXES_AND_BUNDLES(true, true, true);

  private final boolean enderChestItems;
  private final boolean enderChestBoxes;
  private final boolean bundles;

  CraftScope(boolean enderChestItems, boolean enderChestBoxes, boolean bundles) {
    this.enderChestItems = enderChestItems;
    this.enderChestBoxes = enderChestBoxes;
    this.bundles = bundles;
  }

  /** Loose items in the ender chest (only while an ender chest item is carried). */
  public boolean enderChestItems() {
    return enderChestItems;
  }

  /** Shulker boxes inside the ender chest (only while an ender chest item is carried). */
  public boolean enderChestBoxes() {
    return enderChestBoxes;
  }

  /** Bundles inside the shulker boxes read. */
  public boolean bundles() {
    return bundles;
  }

  /**
   * The scope for a player, or null when crafting from boxes is off (forbidden by the server or
   * turned off by the player). The ender chest also needs the server's ender chest preview: it is
   * what keeps the client's copy of the ender chest current.
   *
   * @param serverAllows the server policy's {@code allowCraftFromBoxes}
   * @param enabled the player's option
   * @param depth the player's depth
   * @param enderPreviewAllowed the server policy's {@code allowEnderPreview}
   */
  public static @Nullable CraftScope resolve(
      boolean serverAllows, boolean enabled, CraftDepth depth, boolean enderPreviewAllowed) {
    if (!serverAllows || !enabled) {
      return null;
    }
    boolean bundles = depth.includes(CraftDepth.BUNDLES_IN_BOXES);
    if (!enderPreviewAllowed || !depth.includes(CraftDepth.ENDER_CHEST_ITEMS)) {
      return bundles ? WITH_BUNDLES : INVENTORY_BOXES;
    }
    if (!depth.includes(CraftDepth.ENDER_CHEST_BOXES)) {
      return WITH_ENDER_CHEST_ITEMS;
    }
    return bundles ? WITH_ENDER_CHEST_BOXES_AND_BUNDLES : WITH_ENDER_CHEST_BOXES;
  }
}
