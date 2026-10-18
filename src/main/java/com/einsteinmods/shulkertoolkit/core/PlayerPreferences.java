package com.einsteinmods.shulkertoolkit.core;

/**
 * The part of a player's settings the server needs ({@code PlayerPreferencesPayload}). Client-only
 * options live in {@code client/config/ClientPreferences}.
 *
 * @param pickMode how much pick-block takes out of a shulker box
 * @param autoRefill refill the held stack from shulker boxes when it runs out
 * @param bundleInteractions bundles use the container item controls instead of vanilla's; the
 *     server must know it because it decides the clicks the client predicts
 * @param pickupOverflowToBoxes picked-up items that do not fit in the inventory go into the shulker
 *     boxes in it
 * @param pickupDirectToBox picked-up items go straight into a shulker box already holding them,
 *     unless a loose stack of them is carried
 * @param craftFromBoxes the recipe book of the crafting table and the inventory takes ingredients
 *     out of shulker boxes
 * @param craftDepth where crafting from boxes looks
 */
public record PlayerPreferences(
    PickMode pickMode,
    boolean autoRefill,
    boolean bundleInteractions,
    boolean pickupOverflowToBoxes,
    boolean pickupDirectToBox,
    boolean craftFromBoxes,
    CraftDepth craftDepth) {
  public static final PlayerPreferences DEFAULTS = new PlayerPreferences(PickMode.DEFAULT, true);

  /** Preferences with crafting from boxes on at the default depth, the default. */
  public PlayerPreferences(
      PickMode pickMode,
      boolean autoRefill,
      boolean bundleInteractions,
      boolean pickupOverflowToBoxes,
      boolean pickupDirectToBox) {
    this(
        pickMode,
        autoRefill,
        bundleInteractions,
        pickupOverflowToBoxes,
        pickupDirectToBox,
        true,
        CraftDepth.DEFAULT);
  }

  /** Preferences with both pickup options off, the default. */
  public PlayerPreferences(PickMode pickMode, boolean autoRefill, boolean bundleInteractions) {
    this(pickMode, autoRefill, bundleInteractions, false, false);
  }

  /** Preferences with bundles left to vanilla, the default. */
  public PlayerPreferences(PickMode pickMode, boolean autoRefill) {
    this(pickMode, autoRefill, false);
  }
}
