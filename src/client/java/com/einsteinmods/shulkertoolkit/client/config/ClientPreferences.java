package com.einsteinmods.shulkertoolkit.client.config;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;

/**
 * Every player setting, saved in {@code config/shulkertoolkit.json}. Only {@link #toPlayer()}
 * reaches the server ({@code PlayerPreferencesPayload}); the click scheme reaches it as {@code
 * ControlSchemePayload} before a click. The rest only changes this client's view. The container
 * item options and their defaults are Easy Shulker Boxes' client options.
 *
 * @param itemStorageTooltip when tooltips show the contents grid
 * @param itemStorageColors tint the grid with the box colour
 * @param itemStorageHighlightSprite how the selected slot is marked
 * @param verticalTooltipScrolling key that makes the wheel move the selection vertically
 * @param iconMarker a fill bar, the "+" while the cursor stack fits, both or nothing
 * @param singleItemMovement key that makes clicks and the wheel move one item
 * @param itemHeldByCursorTooltip when the container item on the cursor shows its tooltip
 * @param controlScheme which buttons insert and extract
 * @param nestedItemTooltips open a container item inside a tooltip with Enter or Space
 * @param bundleInteractions bundles get the same tooltip and controls as shulker boxes instead of
 *     vanilla's (off by default, as Easy Shulker Boxes outside development); reaches the server
 * @param pickMode how much pick-block takes out of a shulker box (server-backed)
 * @param autoRefill refill the held stack from shulker boxes (server-backed)
 * @param litematicaAutoTakeOut with Litematica, right-clicking a schematic block takes the block it
 *     needs out of a shulker box (needs pick allowed by the server)
 * @param pickupOverflowToBoxes picked-up items that do not fit in the inventory go into its shulker
 *     boxes (server-backed)
 * @param pickupDirectToBox picked-up items go straight into a shulker box already holding them
 *     (server-backed)
 * @param craftFromBoxes the recipe book of the crafting table and the inventory counts and takes
 *     ingredients from shulker boxes (server-backed)
 * @param craftDepth where crafting from boxes looks (server-backed)
 * @param pinnedWindows middle click pins a container item's window next to a container screen
 *     (server-backed: each click is checked by the server)
 * @param pinnedWindowSounds pinning a window plays its container's opening sound
 * @param maxPinnedWindows how many windows may be pinned at once ({@link #MIN_PINNED_WINDOWS} to
 *     {@link #MAX_PINNED_WINDOWS})
 * @param pinnedWindowsAlwaysRight windows always go in columns beside the container, never in rows
 *     above and below it
 */
public record ClientPreferences(
    ShowMode itemStorageTooltip,
    boolean itemStorageColors,
    SlotHighlight itemStorageHighlightSprite,
    ModifierKey verticalTooltipScrolling,
    IconMarker iconMarker,
    ModifierKey singleItemMovement,
    ShowMode itemHeldByCursorTooltip,
    ClickScheme controlScheme,
    boolean nestedItemTooltips,
    boolean bundleInteractions,
    PickMode pickMode,
    boolean autoRefill,
    boolean litematicaAutoTakeOut,
    boolean pickupOverflowToBoxes,
    boolean pickupDirectToBox,
    boolean craftFromBoxes,
    CraftDepth craftDepth,
    boolean pinnedWindows,
    boolean pinnedWindowSounds,
    int maxPinnedWindows,
    boolean pinnedWindowsAlwaysRight) {
  public static final int MIN_PINNED_WINDOWS = 1;
  public static final int MAX_PINNED_WINDOWS = PinnedPaths.MAX_DEPTH;
  public static final int DEFAULT_MAX_PINNED_WINDOWS = 3;

  public static final ClientPreferences DEFAULTS =
      new ClientPreferences(
          ShowMode.ALWAYS,
          true,
          SlotHighlight.HIGHLIGHT,
          ModifierKey.SHIFT,
          IconMarker.BAR_AND_MINIATURE,
          ModifierKey.CONTROL_OR_COMMAND,
          ShowMode.ALT,
          ClickScheme.DEFAULT,
          true,
          false,
          PlayerPreferences.DEFAULTS.pickMode(),
          PlayerPreferences.DEFAULTS.autoRefill(),
          true,
          PlayerPreferences.DEFAULTS.pickupOverflowToBoxes(),
          PlayerPreferences.DEFAULTS.pickupDirectToBox(),
          PlayerPreferences.DEFAULTS.craftFromBoxes(),
          PlayerPreferences.DEFAULTS.craftDepth(),
          true,
          true,
          DEFAULT_MAX_PINNED_WINDOWS,
          false);

  public ClientPreferences withPickMode(PickMode newPickMode) {
    return new ClientPreferences(
        itemStorageTooltip,
        itemStorageColors,
        itemStorageHighlightSprite,
        verticalTooltipScrolling,
        iconMarker,
        singleItemMovement,
        itemHeldByCursorTooltip,
        controlScheme,
        nestedItemTooltips,
        bundleInteractions,
        newPickMode,
        autoRefill,
        litematicaAutoTakeOut,
        pickupOverflowToBoxes,
        pickupDirectToBox,
        craftFromBoxes,
        craftDepth,
        pinnedWindows,
        pinnedWindowSounds,
        maxPinnedWindows,
        pinnedWindowsAlwaysRight);
  }

  public ClientPreferences withAutoRefill(boolean newAutoRefill) {
    return new ClientPreferences(
        itemStorageTooltip,
        itemStorageColors,
        itemStorageHighlightSprite,
        verticalTooltipScrolling,
        iconMarker,
        singleItemMovement,
        itemHeldByCursorTooltip,
        controlScheme,
        nestedItemTooltips,
        bundleInteractions,
        pickMode,
        newAutoRefill,
        litematicaAutoTakeOut,
        pickupOverflowToBoxes,
        pickupDirectToBox,
        craftFromBoxes,
        craftDepth,
        pinnedWindows,
        pinnedWindowSounds,
        maxPinnedWindows,
        pinnedWindowsAlwaysRight);
  }

  public PlayerPreferences toPlayer() {
    return new PlayerPreferences(
        pickMode,
        autoRefill,
        bundleInteractions,
        pickupOverflowToBoxes,
        pickupDirectToBox,
        craftFromBoxes,
        craftDepth);
  }
}
