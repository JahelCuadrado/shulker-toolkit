package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftScopeTest {
  private static final int UNKNOWN_ID = 99;
  private static final int OLD_LEVEL_ONE_ID = 1;
  private static final int OLD_LEVEL_TWO_ID = 2;
  private static final int OLD_LEVEL_THREE_ID = 3;

  @Test
  void offWhenTheServerForbidsItOrThePlayerTurnedItOff() {
    for (CraftDepth depth : CraftDepth.values()) {
      assertNull(CraftScope.resolve(false, true, depth, true));
      assertNull(CraftScope.resolve(true, false, depth, true));
    }
  }

  @Test
  void eachDepthAddsItsContainers() {
    assertEquals(
        CraftScope.INVENTORY_BOXES,
        CraftScope.resolve(true, true, CraftDepth.INVENTORY_BOXES, true));
    assertEquals(
        CraftScope.WITH_ENDER_CHEST_ITEMS,
        CraftScope.resolve(true, true, CraftDepth.ENDER_CHEST_ITEMS, true));
    assertEquals(
        CraftScope.WITH_ENDER_CHEST_BOXES,
        CraftScope.resolve(true, true, CraftDepth.ENDER_CHEST_BOXES, true));
    assertEquals(
        CraftScope.WITH_ENDER_CHEST_BOXES_AND_BUNDLES,
        CraftScope.resolve(true, true, CraftDepth.BUNDLES_IN_BOXES, true));
  }

  @Test
  void withoutTheEnderChestPreviewTheEnderChestIsSkipped() {
    assertEquals(
        CraftScope.INVENTORY_BOXES,
        CraftScope.resolve(true, true, CraftDepth.ENDER_CHEST_ITEMS, false));
    assertEquals(
        CraftScope.INVENTORY_BOXES,
        CraftScope.resolve(true, true, CraftDepth.ENDER_CHEST_BOXES, false));
    assertEquals(
        CraftScope.WITH_BUNDLES,
        CraftScope.resolve(true, true, CraftDepth.BUNDLES_IN_BOXES, false));
  }

  @Test
  void levelsAreInOrderAndEachIncludesThePreviousOnes() {
    CraftDepth[] levels = {
      CraftDepth.INVENTORY_BOXES,
      CraftDepth.ENDER_CHEST_ITEMS,
      CraftDepth.ENDER_CHEST_BOXES,
      CraftDepth.BUNDLES_IN_BOXES
    };
    assertArrayEquals(levels, CraftDepth.values());
    for (int level = 0; level < levels.length; level++) {
      for (int previous = 0; previous <= level; previous++) {
        assertTrue(levels[level].includes(levels[previous]));
      }
    }
    assertFalse(CraftDepth.ENDER_CHEST_ITEMS.includes(CraftDepth.ENDER_CHEST_BOXES));
  }

  /** Ids sent before level 2 existed keep their containers (old 2 and 3 are now 3 and 4). */
  @Test
  void oldIdsKeepTheirMeaning() {
    assertEquals(CraftDepth.INVENTORY_BOXES, CraftDepth.fromId(OLD_LEVEL_ONE_ID));
    assertEquals(CraftDepth.ENDER_CHEST_BOXES, CraftDepth.fromId(OLD_LEVEL_TWO_ID));
    assertEquals(CraftDepth.BUNDLES_IN_BOXES, CraftDepth.fromId(OLD_LEVEL_THREE_ID));
  }

  @Test
  void depthsRoundTripThroughTheirIds() {
    for (CraftDepth depth : CraftDepth.values()) {
      assertEquals(depth, CraftDepth.fromId(depth.id()));
    }
    assertEquals(CraftDepth.INVENTORY_BOXES, CraftDepth.fromId(UNKNOWN_ID));
    assertEquals(CraftDepth.INVENTORY_BOXES, CraftDepth.DEFAULT);
  }
}
