package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClickSchemeTest {
  private static final int UNKNOWN_ID = 99;

  @Test
  void singleInputDoesEverythingWithTheSecondaryButton() {
    assertTrue(ClickScheme.SINGLE_INPUT.inserts(false));
    assertTrue(ClickScheme.SINGLE_INPUT.extracts(false));
    assertFalse(ClickScheme.SINGLE_INPUT.inserts(true));
    assertFalse(ClickScheme.SINGLE_INPUT.extracts(true));
  }

  @Test
  void splitInputMirrorsVanillaBundles() {
    assertTrue(ClickScheme.SPLIT_INPUT.inserts(true));
    assertTrue(ClickScheme.SPLIT_INPUT.extracts(false));
    assertFalse(ClickScheme.SPLIT_INPUT.inserts(false));
    assertFalse(ClickScheme.SPLIT_INPUT.extracts(true));
  }

  @Test
  void singleItemMovementForcesTheSplitScheme() {
    assertEquals(ClickScheme.SINGLE_INPUT, ControlScheme.DEFAULT.effectiveScheme());
    assertEquals(
        ClickScheme.SPLIT_INPUT,
        new ControlScheme(true, ClickScheme.SINGLE_INPUT).effectiveScheme());
  }

  @Test
  void idsRoundTripAndUnknownIdsFallBack() {
    for (ClickScheme scheme : ClickScheme.values()) {
      assertEquals(scheme, ClickScheme.fromId(scheme.id()));
    }
    assertEquals(ClickScheme.DEFAULT, ClickScheme.fromId(UNKNOWN_ID));
  }
}
