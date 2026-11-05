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
            false,
            PickMode.FULL_STACK,
            true,
            true,
            false,
            false,
            true,
            CraftDepth.SHALLOW,
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
  void filesWithoutShowEmptyBoxesKeepEmptyBoxesHidden(@TempDir Path directory) throws IOException {
    assertFalse(loadFrom(directory, PARTIAL_JSON).showEmptyBoxes());
    assertFalse(loadFrom(directory, VERSION_1_JSON).showEmptyBoxes());
  }

  @Test
  void showEmptyBoxesStaysOnTheClient() {
    assertEquals(
        ClientPreferences.DEFAULTS.toPlayer(),
        ClientPreferences.DEFAULTS.withShowEmptyBoxes(true).toPlayer());
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

  /** The four earlier levels, saved by name: each reads as the level now holding its containers. */
  @Test
  void depthsSavedWithFourLevelsReadAsTheirNewLevel(@TempDir Path directory) throws IOException {
    assertEquals(
        CraftDepth.SHALLOW, loadFrom(directory, savedDepth("INVENTORY_BOXES")).craftDepth());
    assertEquals(
        CraftDepth.DEEP, loadFrom(directory, savedDepth("ENDER_CHEST_ITEMS")).craftDepth());
    assertEquals(
        CraftDepth.DEEP, loadFrom(directory, savedDepth("ENDER_CHEST_BOXES")).craftDepth());
    assertEquals(
        CraftDepth.DEEPEST, loadFrom(directory, savedDepth("BUNDLES_IN_BOXES")).craftDepth());
    assertEquals(CraftDepth.DEFAULT, loadFrom(directory, savedDepth("NO_SUCH_LEVEL")).craftDepth());
  }

  @Test
  void newDepthsAreSavedByName(@TempDir Path directory) throws IOException {
    for (CraftDepth depth : CraftDepth.values()) {
      assertEquals(depth, loadFrom(directory, savedDepth(depth.name())).craftDepth());
    }
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

  /** The removed «always on the right» option: an old file still loads, its field ignored. */
  @Test
  void removedAlwaysRightFieldIsIgnored(@TempDir Path directory) throws IOException {
    ClientPreferences loaded =
        loadFrom(
            directory,
            """
            {
              "schemaVersion": 4,
              "pinnedWindowsAlwaysRight": true,
              "maxPinnedWindows": 5
            }
            """);

    assertEquals(5, loaded.maxPinnedWindows());
    assertEquals(ClientPreferences.DEFAULTS.showEmptyBoxes(), loaded.showEmptyBoxes());
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
            true,
            PickMode.SINGLE_ITEM,
            false,
            false,
            true,
            true,
            false,
            CraftDepth.DEEPEST,
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
    assertEquals(false, preferences.toPlayer().enderChestInteractions());
  }
}
