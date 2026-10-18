package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PickModeTest {
  private static final int UNKNOWN_ID = 99;

  @Test
  void everyModeRoundTripsThroughItsId() {
    for (PickMode mode : PickMode.values()) {
      assertEquals(mode, PickMode.fromId(mode.id()));
    }
  }

  @Test
  void nextCyclesThroughEveryMode() {
    assertEquals(PickMode.FULL_STACK, PickMode.SINGLE_ITEM.next());
    assertEquals(PickMode.SINGLE_ITEM, PickMode.FULL_STACK.next());
  }

  @Test
  void unknownIdIsTheDefaultFullStack() {
    assertEquals(PickMode.FULL_STACK, PickMode.fromId(UNKNOWN_ID));
    assertEquals(PickMode.FULL_STACK, PickMode.DEFAULT);
  }
}
