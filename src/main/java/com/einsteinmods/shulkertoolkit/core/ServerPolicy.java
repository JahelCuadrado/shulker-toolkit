package com.einsteinmods.shulkertoolkit.core;

/**
 * Server-backed features a server lets one player use. The client enables each of them only after
 * receiving this policy, so servers without the mod never see their packets.
 *
 * @param allowTransfer move items into and out of container items (shulker boxes, bundles, the
 *     ender chest) from the inventory
 * @param allowPick pick blocks from shulker boxes in the inventory
 * @param allowRefill refill the held stack from shulker boxes in the inventory
 * @param allowEnderPreview show and edit the ender chest contents from an ender chest item
 * @param allowMouseDragging drag a container item over slots to insert or extract on each one
 * @param allowSingleItemMovement move a single item per click or scroll while a key is held
 * @param allowPickupIntoBoxes store picked-up items in the shulker boxes in the inventory
 * @param allowCraftFromBoxes the recipe book takes crafting ingredients out of shulker boxes
 * @param allowPinnedWindows pin container item windows next to a container screen and click in them
 */
public record ServerPolicy(
    boolean allowTransfer,
    boolean allowPick,
    boolean allowRefill,
    boolean allowEnderPreview,
    boolean allowMouseDragging,
    boolean allowSingleItemMovement,
    boolean allowPickupIntoBoxes,
    boolean allowCraftFromBoxes,
    boolean allowPinnedWindows) {

  /** Every feature allowed: the default policy file and the singleplayer/LAN host. */
  public static final ServerPolicy UNRESTRICTED =
      new ServerPolicy(true, true, true, true, true, true, true, true, true);

  /** A policy that allows pinned windows, as files written before that flag existed. */
  public ServerPolicy(
      boolean allowTransfer,
      boolean allowPick,
      boolean allowRefill,
      boolean allowEnderPreview,
      boolean allowMouseDragging,
      boolean allowSingleItemMovement,
      boolean allowPickupIntoBoxes,
      boolean allowCraftFromBoxes) {
    this(
        allowTransfer,
        allowPick,
        allowRefill,
        allowEnderPreview,
        allowMouseDragging,
        allowSingleItemMovement,
        allowPickupIntoBoxes,
        allowCraftFromBoxes,
        true);
  }

  /** A policy that allows crafting from boxes, as files written before that flag existed. */
  public ServerPolicy(
      boolean allowTransfer,
      boolean allowPick,
      boolean allowRefill,
      boolean allowEnderPreview,
      boolean allowMouseDragging,
      boolean allowSingleItemMovement,
      boolean allowPickupIntoBoxes) {
    this(
        allowTransfer,
        allowPick,
        allowRefill,
        allowEnderPreview,
        allowMouseDragging,
        allowSingleItemMovement,
        allowPickupIntoBoxes,
        true);
  }

  /** A policy that allows storing picked-up items in boxes and crafting from boxes. */
  public ServerPolicy(
      boolean allowTransfer,
      boolean allowPick,
      boolean allowRefill,
      boolean allowEnderPreview,
      boolean allowMouseDragging,
      boolean allowSingleItemMovement) {
    this(
        allowTransfer,
        allowPick,
        allowRefill,
        allowEnderPreview,
        allowMouseDragging,
        allowSingleItemMovement,
        true);
  }
}
