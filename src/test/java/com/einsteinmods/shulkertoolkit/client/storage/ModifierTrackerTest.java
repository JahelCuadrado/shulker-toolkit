package com.einsteinmods.shulkertoolkit.client.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

class ModifierTrackerTest {
  private static final int REPEAT = -1;

  @Test
  void nothingCountsUntilPressedInTheGame() {
    ModifierTracker tracker = new ModifierTracker();

    assertFalse(tracker.command(), "a Command the game never saw pressed is not held");
    assertFalse(tracker.shift());
    assertFalse(tracker.control());
    assertFalse(tracker.alt());
  }

  @Test
  void pressAndReleaseOfEitherSide() {
    ModifierTracker tracker = new ModifierTracker();

    tracker.onKey(InputConstants.KEY_RGUI, ModifierTracker.PRESS);
    assertTrue(tracker.command());
    tracker.onKey(InputConstants.KEY_LGUI, ModifierTracker.PRESS);
    tracker.onKey(InputConstants.KEY_RGUI, ModifierTracker.RELEASE);
    assertTrue(tracker.command(), "the left key is still down");
    tracker.onKey(InputConstants.KEY_LGUI, ModifierTracker.RELEASE);
    assertFalse(tracker.command());
  }

  @Test
  void repeatsAndOtherKeysChangeNothing() {
    ModifierTracker tracker = new ModifierTracker();

    tracker.onKey(InputConstants.KEY_LSHIFT, REPEAT);
    tracker.onKey(InputConstants.KEY_CAPSLOCK, ModifierTracker.PRESS);
    assertFalse(tracker.shift(), "a repeat is not a press; caps lock is no modifier here");
    tracker.onKey(InputConstants.KEY_LALT, ModifierTracker.PRESS);
    tracker.onKey(InputConstants.KEY_LALT, REPEAT);
    assertTrue(tracker.alt());
  }

  /** The reported case: the window loses focus with Command down and gets it back. */
  @Test
  void focusChangeForgetsHeldModifiers() {
    ModifierTracker tracker = new ModifierTracker();
    tracker.onKey(InputConstants.KEY_LGUI, ModifierTracker.PRESS);
    tracker.onKey(InputConstants.KEY_LCONTROL, ModifierTracker.PRESS);

    tracker.clear();

    assertFalse(tracker.command(), "the release happened in another app");
    assertFalse(tracker.control());
  }
}
