package com.einsteinmods.shulkertoolkit.client.config;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import org.jspecify.annotations.Nullable;

/**
 * On-disk shape of {@code config/shulkertoolkit.json}. Fields are nullable so a missing or unknown
 * value (Gson reads both as null) gets its default instead of {@code false}.
 *
 * <p>Bump {@link #CURRENT_SCHEMA_VERSION} when a field is renamed or changes meaning, and migrate
 * older files in {@link #toPreferences()}. Version 2 (Easy Shulker Boxes options) renamed {@code
 * previewColor} to {@code itemStorageColors} (kept) and replaced {@code fillBar} with {@code
 * overrideItemStorageBar}, which counts filled slots and is off by default, so the old value is
 * dropped. Version 3 merged {@code itemStorageIndicator} and {@code overrideItemStorageBar} into
 * {@code iconMarker} (both kept) and removed {@code reverseSingleItemScrolling} (fixed off, Easy
 * Shulker Boxes' default). Version 4 renamed the {@code iconMarker} values (the bar shows with the
 * "+" by default): {@code IconMarker}'s serialized alternates map the old values.
 */
public record ClientSettingsFile(
    int schemaVersion,
    @Nullable ShowMode itemStorageTooltip,
    @Nullable Boolean itemStorageColors,
    @Nullable SlotHighlight itemStorageHighlightSprite,
    @Nullable ModifierKey verticalTooltipScrolling,
    @Nullable IconMarker iconMarker,
    @Nullable ModifierKey singleItemMovement,
    @Nullable ShowMode itemHeldByCursorTooltip,
    @Nullable ClickScheme controlScheme,
    @Nullable Boolean nestedItemTooltips,
    @Nullable Boolean bundleInteractions,
    @Nullable PickMode pickMode,
    @Nullable Boolean autoRefill,
    @Nullable Boolean litematicaAutoTakeOut,
    @Nullable Boolean previewColor,
    @Nullable Boolean pickupOverflowToBoxes,
    @Nullable Boolean pickupDirectToBox,
    @Nullable Boolean itemStorageIndicator,
    @Nullable Boolean overrideItemStorageBar,
    @Nullable Boolean craftFromBoxes,
    @Nullable CraftDepth craftDepth,
    @Nullable Boolean pinnedWindows,
    @Nullable Boolean pinnedWindowSounds,
    @Nullable Integer maxPinnedWindows,
    @Nullable Boolean pinnedWindowsAlwaysRight) {
  public static final int CURRENT_SCHEMA_VERSION = 4;
  public static final String FILE_NAME = "shulkertoolkit.json";

  public static final ClientSettingsFile DEFAULTS = from(ClientPreferences.DEFAULTS);

  public static ClientSettingsFile from(ClientPreferences preferences) {
    return new ClientSettingsFile(
        CURRENT_SCHEMA_VERSION,
        preferences.itemStorageTooltip(),
        preferences.itemStorageColors(),
        preferences.itemStorageHighlightSprite(),
        preferences.verticalTooltipScrolling(),
        preferences.iconMarker(),
        preferences.singleItemMovement(),
        preferences.itemHeldByCursorTooltip(),
        preferences.controlScheme(),
        preferences.nestedItemTooltips(),
        preferences.bundleInteractions(),
        preferences.pickMode(),
        preferences.autoRefill(),
        preferences.litematicaAutoTakeOut(),
        null,
        preferences.pickupOverflowToBoxes(),
        preferences.pickupDirectToBox(),
        null,
        null,
        preferences.craftFromBoxes(),
        preferences.craftDepth(),
        preferences.pinnedWindows(),
        preferences.pinnedWindowSounds(),
        preferences.maxPinnedWindows(),
        preferences.pinnedWindowsAlwaysRight());
  }

  public ClientPreferences toPreferences() {
    ClientPreferences defaults = ClientPreferences.DEFAULTS;
    Boolean colors = itemStorageColors == null ? previewColor : itemStorageColors;
    return new ClientPreferences(
        orDefault(itemStorageTooltip, defaults.itemStorageTooltip()),
        orDefault(colors, defaults.itemStorageColors()),
        orDefault(itemStorageHighlightSprite, defaults.itemStorageHighlightSprite()),
        orDefault(verticalTooltipScrolling, defaults.verticalTooltipScrolling()),
        iconMarker == null ? legacyIconMarker(defaults.iconMarker()) : iconMarker,
        orDefault(singleItemMovement, defaults.singleItemMovement()),
        orDefault(itemHeldByCursorTooltip, defaults.itemHeldByCursorTooltip()),
        orDefault(controlScheme, defaults.controlScheme()),
        orDefault(nestedItemTooltips, defaults.nestedItemTooltips()),
        orDefault(bundleInteractions, defaults.bundleInteractions()),
        orDefault(pickMode, defaults.pickMode()),
        orDefault(autoRefill, defaults.autoRefill()),
        orDefault(litematicaAutoTakeOut, defaults.litematicaAutoTakeOut()),
        orDefault(pickupOverflowToBoxes, defaults.pickupOverflowToBoxes()),
        orDefault(pickupDirectToBox, defaults.pickupDirectToBox()),
        orDefault(craftFromBoxes, defaults.craftFromBoxes()),
        orDefault(craftDepth, defaults.craftDepth()),
        orDefault(pinnedWindows, defaults.pinnedWindows()),
        orDefault(pinnedWindowSounds, defaults.pinnedWindowSounds()),
        maxPinnedWindows(defaults.maxPinnedWindows()),
        orDefault(pinnedWindowsAlwaysRight, defaults.pinnedWindowsAlwaysRight()));
  }

  /** Version 2 kept the two parts apart; a missing part takes its part of the default. */
  private IconMarker legacyIconMarker(IconMarker defaultMarker) {
    return IconMarker.ofLegacy(
        orDefault(itemStorageIndicator, defaultMarker.showsIndicator()),
        orDefault(overrideItemStorageBar, defaultMarker.showsFillBar()));
  }

  /** The saved maximum when it is in range, else the default (a hand-edited file stays valid). */
  private int maxPinnedWindows(int defaultValue) {
    return maxPinnedWindows != null
            && maxPinnedWindows >= ClientPreferences.MIN_PINNED_WINDOWS
            && maxPinnedWindows <= ClientPreferences.MAX_PINNED_WINDOWS
        ? maxPinnedWindows
        : defaultValue;
  }

  private static <T> T orDefault(@Nullable T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }
}
