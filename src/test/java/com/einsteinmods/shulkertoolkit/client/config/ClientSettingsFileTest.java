package com.einsteinmods.shulkertoolkit.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.einsteinmods.shulkertoolkit.config.JsonConfigFile;
import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientSettingsFileTest {
  private static final int LEVEL_THREE = 3;
  private static final int LEVEL_FOUR = 4;

  private static final String PARTIAL_JSON =
      """
      {
        "schemaVersion": 2,
        "itemStorageTooltip": "SHIFT",
        "controlScheme": "SPLIT_INPUT",
        "pickMode": "SINGLE_ITEM"
      }
      """;

  private static final String UNKNOWN_VALUES_JSON =
      """
      {
        "schemaVersion": 2,
        "singleItemMovement": "KEY",
        "pickMode": "WHOLE_BOX"
      }
      """;

  /** A file written by version 1, with its renamed and replaced options. */
  private static final String VERSION_1_JSON =
      """
      {
        "schemaVersion": 1,
        "previewColor": false,
        "fillBar": true,
        "pickMode": "SINGLE_ITEM",
        "autoRefill": false,
        "litematicaAutoTakeOut": true
      }
      """;

  private static JsonConfigFile<ClientSettingsFile> file(Path directory) {
    return new JsonConfigFile<>(
        directory.resolve(ClientSettingsFile.FILE_NAME),
        ClientSettingsFile.class,
        ClientSettingsFile.DEFAULTS);
  }

  private static ClientPreferences loadFrom(Path directory, String json) throws IOException {
    Files.writeString(
        directory.resolve(ClientSettingsFile.FILE_NAME), json, StandardCharsets.UTF_8);
    return file(directory).load().toPreferences();
  }

  /** Easy Shulker Boxes' defaults ({@code iteminteractions-client.toml}) plus the pick options. */
  @Test
  void defaultsAreEasyShulkerBoxesDefaults() {
    assertEquals(
        new ClientPreferences(
            ShowMode.ALWAYS,
            true,
            SlotHighlight.HIGHLIGHT,
            ModifierKey.SHIFT,
            IconMarker.BAR_AND_MINIATURE,
            ModifierKey.CONTROL_OR_COMMAND,
            ShowMode.ALT,
            ClickScheme.SINGLE_INPUT,
            true,
            false,
            PickMode.FULL_STACK,
            true,
            true,
            false,
            false,
            true,
            CraftDepth.INVENTORY_BOXES,
            true,
            true,
            3,
            false),
        ClientPreferences.DEFAULTS);
  }

  @Test
  void missingFileGivesDefaults(@TempDir Path directory) {
    assertEquals(ClientPreferences.DEFAULTS, file(directory).load().toPreferences());
  }

  @Test
  void missingFieldsKeepTheirDefaultsInsteadOfFalse(@TempDir Path directory) throws IOException {
    ClientPreferences loaded = loadFrom(directory, PARTIAL_JSON);

    assertEquals(ShowMode.SHIFT, loaded.itemStorageTooltip());
    assertEquals(ClickScheme.SPLIT_INPUT, loaded.controlScheme());
    assertEquals(PickMode.SINGLE_ITEM, loaded.pickMode());
    assertEquals(ClientPreferences.DEFAULTS.itemStorageColors(), loaded.itemStorageColors());
    assertEquals(ClientPreferences.DEFAULTS.nestedItemTooltips(), loaded.nestedItemTooltips());
  }

  @Test
  void unknownValuesFallBackToDefaults(@TempDir Path directory) throws IOException {
    ClientPreferences loaded = loadFrom(directory, UNKNOWN_VALUES_JSON);

    assertEquals(ClientPreferences.DEFAULTS.singleItemMovement(), loaded.singleItemMovement());
    assertEquals(PickMode.DEFAULT, loaded.pickMode());
  }

  @Test
  void version1KeepsTheColourChoiceAndDropsTheOldFillBar(@TempDir Path directory)
      throws IOException {
    ClientPreferences loaded = loadFrom(directory, VERSION_1_JSON);

    assertFalse(loaded.itemStorageColors(), "previewColor became itemStorageColors");
    assertEquals(
        IconMarker.BAR_AND_MINIATURE, loaded.iconMarker(), "the old fill bar meant something else");
    assertEquals(PickMode.SINGLE_ITEM, loaded.pickMode());
    assertFalse(loaded.autoRefill());
  }

  /**
   * Depths saved before level 2 (loose ender chest items) existed: the saved name keeps its
   * containers, so the old level 2 reads as level 3 and the old level 3 as level 4.
   */
  @Test
  void depthsSavedBeforeLevelTwoMoveUpOneLevel(@TempDir Path directory) throws IOException {
    assertEquals(
        CraftDepth.INVENTORY_BOXES,
        loadFrom(directory, savedDepth("INVENTORY_BOXES")).craftDepth());
    assertEquals(
        CraftDepth.ENDER_CHEST_BOXES,
        loadFrom(directory, savedDepth("ENDER_CHEST_BOXES")).craftDepth());
    assertEquals(LEVEL_THREE, CraftDepth.ENDER_CHEST_BOXES.ordinal() + 1);
    assertEquals(
        CraftDepth.BUNDLES_IN_BOXES,
        loadFrom(directory, savedDepth("BUNDLES_IN_BOXES")).craftDepth());
    assertEquals(LEVEL_FOUR, CraftDepth.BUNDLES_IN_BOXES.ordinal() + 1);
  }

  private static String savedDepth(String depth) {
    return """
        {
          "schemaVersion": 3,
          "craftFromBoxes": true,
          "craftDepth": "%s"
        }
        """
        .formatted(depth);
  }

  /** Version 2 had the "+" and the bar as two switches, and a reversed wheel. */
  @Test
  void version2MergesTheIndicatorAndTheBarIntoTheIconMarker(@TempDir Path directory)
      throws IOException {
    assertEquals(
        IconMarker.BAR_AND_MINIATURE, loadFrom(directory, version2(true, true)).iconMarker());
    assertEquals(IconMarker.BAR, loadFrom(directory, version2(false, true)).iconMarker());
    assertEquals(IconMarker.NONE, loadFrom(directory, version2(false, false)).iconMarker());
    assertEquals(
        IconMarker.BAR_AND_MINIATURE, loadFrom(directory, version2(true, false)).iconMarker());
  }

  /** Version 3 stored the "+" options by name; version 4 reads them as the new markers. */
  @Test
  void version3IconMarkersMapToTheNewOnes(@TempDir Path directory) throws IOException {
    String[][] cases = {
      {"INDICATOR", "BAR_AND_MINIATURE"},
      {"FILL_BAR", "BAR"},
      {"BOTH", "BAR_AND_MINIATURE"},
      {"NONE", "NONE"},
      {"MINIATURE", "MINIATURE"}
    };
    for (String[] pair : cases) {
      ClientPreferences loaded =
          loadFrom(directory, "{\"schemaVersion\": 3, \"iconMarker\": \"" + pair[0] + "\"}");
      assertEquals(IconMarker.valueOf(pair[1]), loaded.iconMarker(), pair[0]);
    }
  }

  private static String version2(boolean indicator, boolean bar) {
    return """
        {
          "schemaVersion": 2,
          "itemStorageIndicator": %s,
          "overrideItemStorageBar": %s,
          "reverseSingleItemScrolling": true
        }
        """
        .formatted(indicator, bar);
  }

  @Test
  void maxPinnedWindowsOutOfRangeFallsBackToTheDefault(@TempDir Path directory) throws IOException {
    for (int saved : new int[] {0, 7, -3}) {
      ClientPreferences loaded =
          loadFrom(directory, "{\"schemaVersion\": 3, \"maxPinnedWindows\": " + saved + "}");
      assertEquals(ClientPreferences.DEFAULT_MAX_PINNED_WINDOWS, loaded.maxPinnedWindows());
    }
    assertEquals(
        5,
        loadFrom(directory, "{\"schemaVersion\": 3, \"maxPinnedWindows\": 5}").maxPinnedWindows());
  }

  @Test
  void savedPreferencesLoadBack(@TempDir Path directory) {
    ClientPreferences preferences =
        new ClientPreferences(
            ShowMode.KEY,
            false,
            SlotHighlight.SELECTION,
            ModifierKey.ALT,
            IconMarker.BAR,
            ModifierKey.SHIFT,
            ShowMode.NEVER,
            ClickScheme.SPLIT_INPUT,
            false,
            true,
            PickMode.SINGLE_ITEM,
            false,
            false,
            true,
            true,
            false,
            CraftDepth.BUNDLES_IN_BOXES,
            false,
            false,
            6,
            true);
    file(directory).save(ClientSettingsFile.from(preferences));

    assertEquals(preferences, file(directory).load().toPreferences());
    assertEquals(ClientSettingsFile.CURRENT_SCHEMA_VERSION, file(directory).load().schemaVersion());
  }

  @Test
  void onlyPickModeAndAutoRefillReachTheServer() {
    ClientPreferences preferences =
        ClientPreferences.DEFAULTS.withPickMode(PickMode.SINGLE_ITEM).withAutoRefill(false);

    assertEquals(PickMode.SINGLE_ITEM, preferences.toPlayer().pickMode());
    assertEquals(false, preferences.toPlayer().autoRefill());
    assertEquals(false, preferences.toPlayer().bundleInteractions());
  }
}
