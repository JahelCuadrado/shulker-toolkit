package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftScopeTest {
  private static final int UNKNOWN_ID = 99;
  private static final int OLD_INVENTORY_BOXES_ID = 1;
  private static final int OLD_ENDER_CHEST_BOXES_ID = 2;
  private static final int OLD_BUNDLES_IN_BOXES_ID = 3;
  private static final int OLD_ENDER_CHEST_ITEMS_ID = 4;
  private static final boolean CARRIED = true;
  private static final boolean NOT_CARRIED = false;

  @Test
  void offWhenTheServerForbidsItOrThePlayerTurnedItOff() {
    for (CraftDepth depth : CraftDepth.values()) {
      assertNull(CraftScope.resolve(false, true, depth, true));
      assertNull(CraftScope.resolve(true, false, depth, true));
    }
  }

  @Test
  void eachDepthAddsItsContainers() {
    assertEquals(CraftScope.INVENTORY, CraftScope.resolve(true, true, CraftDepth.SHALLOW, true));
    assertEquals(
        CraftScope.WITH_ENDER_CHEST, CraftScope.resolve(true, true, CraftDepth.DEEP, true));
    assertEquals(
        CraftScope.WITH_ENDER_CHEST_BUNDLES_IN_BOXES,
        CraftScope.resolve(true, true, CraftDepth.DEEPEST, true));
  }

  @Test
  void withoutTheEnderChestPreviewTheEnderChestIsSkipped() {
    assertEquals(CraftScope.INVENTORY, CraftScope.resolve(true, true, CraftDepth.SHALLOW, false));
    assertEquals(
        CraftScope.WITH_BUNDLES_IN_BOXES, CraftScope.resolve(true, true, CraftDepth.DEEP, false));
    assertEquals(
        CraftScope.WITH_BUNDLES_IN_BOXES,
        CraftScope.resolve(true, true, CraftDepth.DEEPEST, false));
  }

  /** Level 1: inventory containers; level 2: one level further in; level 3: the deepest. */
  @Test
  void eachScopeReadsItsSources() {
    assertEquals(StorageSource.INVENTORY_CONTAINER, CraftScope.INVENTORY.deepest(CARRIED));
    assertEquals(
        StorageSource.BUNDLE_IN_INVENTORY_BOX, CraftScope.WITH_BUNDLES_IN_BOXES.deepest(CARRIED));
    assertEquals(StorageSource.ENDER_CHEST_CONTAINER, CraftScope.WITH_ENDER_CHEST.deepest(CARRIED));
    assertEquals(
        StorageSource.BUNDLE_IN_ENDER_CHEST_BOX,
        CraftScope.WITH_ENDER_CHEST_BUNDLES_IN_BOXES.deepest(CARRIED));
  }

  @Test
  void theEnderChestNeedsAnEnderChestItem() {
    for (CraftScope scope : CraftScope.values()) {
      assertFalse(scope.deepest(NOT_CARRIED).inEnderChest(), scope.name());
    }
    assertFalse(CraftScope.INVENTORY.readsEnderChest());
    assertFalse(CraftScope.WITH_BUNDLES_IN_BOXES.readsEnderChest());
    assertTrue(CraftScope.WITH_ENDER_CHEST.readsEnderChest());
    assertTrue(CraftScope.WITH_ENDER_CHEST_BUNDLES_IN_BOXES.readsEnderChest());
  }

  @Test
  void levelsAreInOrderAndEachIncludesThePreviousOnes() {
    CraftDepth[] levels = {CraftDepth.SHALLOW, CraftDepth.DEEP, CraftDepth.DEEPEST};
    assertArrayEquals(levels, CraftDepth.values());
    for (int level = 0; level < levels.length; level++) {
      for (int previous = 0; previous <= level; previous++) {
        assertTrue(levels[level].includes(levels[previous]));
      }
    }
    assertFalse(CraftDepth.DEEP.includes(CraftDepth.DEEPEST));
  }

  /** An older client sends the four earlier ids: each reads as the level holding its containers. */
  @Test
  void oldIdsReadAsTheirNewLevel() {
    assertEquals(CraftDepth.SHALLOW, CraftDepth.fromId(OLD_INVENTORY_BOXES_ID));
    assertEquals(CraftDepth.DEEP, CraftDepth.fromId(OLD_ENDER_CHEST_ITEMS_ID));
    assertEquals(CraftDepth.DEEP, CraftDepth.fromId(OLD_ENDER_CHEST_BOXES_ID));
    assertEquals(CraftDepth.DEEPEST, CraftDepth.fromId(OLD_BUNDLES_IN_BOXES_ID));
  }

  @Test
  void newIdsAreNotTheOldOnes() {
    for (CraftDepth depth : CraftDepth.values()) {
      assertTrue(depth.id() > OLD_ENDER_CHEST_ITEMS_ID, depth.name());
    }
  }

  @Test
  void depthsRoundTripThroughTheirIds() {
    for (CraftDepth depth : CraftDepth.values()) {
      assertEquals(depth, CraftDepth.fromId(depth.id()));
    }
    assertEquals(CraftDepth.SHALLOW, CraftDepth.fromId(UNKNOWN_ID));
    assertEquals(CraftDepth.SHALLOW, CraftDepth.DEFAULT);
  }
}
