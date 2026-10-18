package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PinnedPathsTest {
  @Test
  void depthIsOneToThree() {
    assertFalse(PinnedPaths.isValidDepth(0));
    assertTrue(PinnedPaths.isValidDepth(1));
    assertTrue(PinnedPaths.isValidDepth(PinnedPaths.MAX_DEPTH));
    assertFalse(PinnedPaths.isValidDepth(PinnedPaths.MAX_DEPTH + 1));
  }

  @Test
  void indexMustAddressASlot() {
    assertFalse(PinnedPaths.isValidIndex(-1, 27));
    assertTrue(PinnedPaths.isValidIndex(0, 27));
    assertTrue(PinnedPaths.isValidIndex(26, 27));
    assertFalse(PinnedPaths.isValidIndex(27, 27));
    assertFalse(PinnedPaths.isValidIndex(0, 0));
  }

  @Test
  void childIsOneLevelBelowWithTheSamePrefix() {
    int[] parent = {12, 0, 0};
    assertTrue(PinnedPaths.isChild(parent, 1, new int[] {12, 4, 0}, 2));
    assertFalse(PinnedPaths.isChild(parent, 1, new int[] {13, 4, 0}, 2));
    assertFalse(PinnedPaths.isChild(parent, 1, new int[] {12, 4, 1}, 3));
    assertFalse(PinnedPaths.isChild(parent, 1, parent, 1));
  }

  @Test
  void samePathComparesOnlyItsLevels() {
    assertTrue(PinnedPaths.samePath(new int[] {3, 5, 9}, 2, new int[] {3, 5}, 2));
    assertFalse(PinnedPaths.samePath(new int[] {3, 5}, 2, new int[] {3, 6}, 2));
    assertFalse(PinnedPaths.samePath(new int[] {3}, 1, new int[] {3, 5}, 2));
  }

  @Test
  void actionsKeepTheirIdsAndButtons() {
    for (PinnedAction action : PinnedAction.values()) {
      assertEquals(action, PinnedAction.fromId(action.id()));
    }
    assertNull(PinnedAction.fromId(PinnedAction.values().length));
    assertTrue(PinnedAction.PICKUP.accepts(PinnedAction.SECONDARY_BUTTON));
    assertFalse(PinnedAction.PICKUP.accepts(2));
    assertFalse(PinnedAction.QUICK_MOVE_OUT.accepts(PinnedAction.SECONDARY_BUTTON));
    assertTrue(PinnedAction.SWAP.accepts(8));
    assertTrue(PinnedAction.SWAP.accepts(PinnedAction.OFFHAND_BUTTON));
    assertFalse(PinnedAction.SWAP.accepts(PinnedAction.HOTBAR_SLOTS));
  }
}
