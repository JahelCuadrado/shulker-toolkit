package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PickupStorageTest {
  private static final int MAX = 64;
  private static final int EMPTY = PickupStorage.EMPTY_SLOT;
  private static final ServerPolicy PICKUP_FORBIDDEN =
      new ServerPolicy(true, true, true, true, true, true, false);
  private static final PlayerPreferences OVERFLOW_ONLY =
      new PlayerPreferences(PickMode.DEFAULT, true, false, true, false);
  private static final PlayerPreferences DIRECT_ONLY =
      new PlayerPreferences(PickMode.DEFAULT, true, false, false, true);

  @Test
  void aSlotTakesUpToItsRoom() {
    assertEquals(4, PickupStorage.addable(60, MAX, 70));
    assertEquals(MAX, PickupStorage.addable(EMPTY, MAX, 70));
  }

  @Test
  void aSlotTakesNoMoreThanWhatIsLeft() {
    assertEquals(5, PickupStorage.addable(10, MAX, 5));
  }

  @Test
  void aFullOrOverfullSlotTakesNothing() {
    assertEquals(0, PickupStorage.addable(MAX, MAX, 5));
    assertEquals(0, PickupStorage.addable(MAX + 1, MAX, 5));
  }

  @Test
  void unstackableItemsTakeOneEmptySlotEach() {
    assertEquals(1, PickupStorage.addable(EMPTY, 1, 1));
    assertEquals(0, PickupStorage.addable(1, 1, 1));
  }

  @Test
  void optionsAreOffByDefault() {
    assertFalse(PickupStorage.directToBox(ServerPolicy.UNRESTRICTED, PlayerPreferences.DEFAULTS));
    assertFalse(
        PickupStorage.overflowToBoxes(ServerPolicy.UNRESTRICTED, PlayerPreferences.DEFAULTS));
  }

  @Test
  void eachOptionTurnsOnAlone() {
    assertTrue(PickupStorage.overflowToBoxes(ServerPolicy.UNRESTRICTED, OVERFLOW_ONLY));
    assertFalse(PickupStorage.directToBox(ServerPolicy.UNRESTRICTED, OVERFLOW_ONLY));
    assertTrue(PickupStorage.directToBox(ServerPolicy.UNRESTRICTED, DIRECT_ONLY));
    assertFalse(PickupStorage.overflowToBoxes(ServerPolicy.UNRESTRICTED, DIRECT_ONLY));
  }

  @Test
  void theServerPolicyForbidsBoth() {
    assertFalse(PickupStorage.overflowToBoxes(PICKUP_FORBIDDEN, OVERFLOW_ONLY));
    assertFalse(PickupStorage.directToBox(PICKUP_FORBIDDEN, DIRECT_ONLY));
  }
}
