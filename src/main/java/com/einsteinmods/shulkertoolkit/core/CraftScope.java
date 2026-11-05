package com.einsteinmods.shulkertoolkit.core;

import org.jspecify.annotations.Nullable;

/**
 * Which containers one player's {@link CraftDepth} reads, for crafting from boxes, the pick from
 * boxes and Litematica's material list: always the shulker boxes and bundles in the inventory, plus
 * what the depth and the server allow. Constants only, so resolving allocates nothing.
 */
public enum CraftScope {
  INVENTORY(StorageSource.INVENTORY_CONTAINER, StorageSource.INVENTORY_CONTAINER),
  WITH_BUNDLES_IN_BOXES(
      StorageSource.BUNDLE_IN_INVENTORY_BOX, StorageSource.BUNDLE_IN_INVENTORY_BOX),
  WITH_ENDER_CHEST(StorageSource.BUNDLE_IN_INVENTORY_BOX, StorageSource.ENDER_CHEST_CONTAINER),
  WITH_ENDER_CHEST_BUNDLES_IN_BOXES(
      StorageSource.BUNDLE_IN_INVENTORY_BOX, StorageSource.BUNDLE_IN_ENDER_CHEST_BOX);

  private final StorageSource deepestWithoutEnderChest;
  private final StorageSource deepestWithEnderChest;

  CraftScope(StorageSource deepestWithoutEnderChest, StorageSource deepestWithEnderChest) {
    this.deepestWithoutEnderChest = deepestWithoutEnderChest;
    this.deepestWithEnderChest = deepestWithEnderChest;
  }

  /**
   * The last kind of source read: the scope reads every source number below its {@code end()}.
   *
   * @param enderChestCarried whether an ender chest item is in the inventory (slots 0-35)
   */
  public StorageSource deepest(boolean enderChestCarried) {
    return enderChestCarried ? deepestWithEnderChest : deepestWithoutEnderChest;
  }

  /** Whether the ender chest is read while an ender chest item is carried. */
  public boolean readsEnderChest() {
    return deepestWithEnderChest != deepestWithoutEnderChest;
  }

  /**
   * The scope for a player, or null when the feature is off (forbidden by the server or turned off
   * by the player). The ender chest also needs the server's ender chest preview: it is what keeps
   * the client's copy of the ender chest current.
   *
   * @param serverAllows the server policy's switch for the feature ({@code allowCraftFromBoxes},
   *     {@code allowPick})
   * @param enabled the player's option for the feature
   * @param depth the player's depth
   * @param enderPreviewAllowed the server policy's {@code allowEnderPreview}
   */
  public static @Nullable CraftScope resolve(
      boolean serverAllows, boolean enabled, CraftDepth depth, boolean enderPreviewAllowed) {
    if (!serverAllows || !enabled) {
      return null;
    }
    if (!depth.includes(CraftDepth.DEEP)) {
      return INVENTORY;
    }
    if (!enderPreviewAllowed) {
      return WITH_BUNDLES_IN_BOXES;
    }
    return depth.includes(CraftDepth.DEEPEST)
        ? WITH_ENDER_CHEST_BUNDLES_IN_BOXES
        : WITH_ENDER_CHEST;
  }
}
