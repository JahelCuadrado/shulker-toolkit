package com.einsteinmods.shulkertoolkit.client.storage;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Modifier keys pressed inside the game window since it last gained or lost focus. The polled key
 * state ({@code InputConstants#isKeyDown}) can report a modifier as held that was never pressed in
 * the game (a shortcut handled by the system, a release while the window was unfocused), so the mod
 * counts a modifier only when both agree: it was pressed here (this tracker) and is still down (the
 * polled state, which also covers releases vanilla drops while a world loads, {@code
 * SDLEventHandler#flushInputEvents}). Pure bit arithmetic: no allocation, a few ns per check.
 * Client thread only.
 */
public final class ModifierTracker {
  /** Key event actions of {@code SDLEventHandler#handleKeyEvent} (a repeat is -1). */
  static final int RELEASE = 0;

  static final int PRESS = 1;

  private static final int LEFT_SHIFT = 1;
  private static final int RIGHT_SHIFT = 1 << 1;
  private static final int LEFT_CONTROL = 1 << 2;
  private static final int RIGHT_CONTROL = 1 << 3;
  private static final int LEFT_ALT = 1 << 4;
  private static final int RIGHT_ALT = 1 << 5;
  private static final int LEFT_COMMAND = 1 << 6;
  private static final int RIGHT_COMMAND = 1 << 7;
  private static final int NONE = 0;

  private int pressed;

  /** A key event delivered to the game ({@code KeyboardHandler#keyPress}). */
  public void onKey(int scancode, int action) {
    int bit = bitOf(scancode);
    if (bit == NONE) {
      return;
    }
    if (action == PRESS) {
      pressed |= bit;
    } else if (action == RELEASE) {
      pressed &= ~bit;
    }
  }

  /** The window gained or lost focus: presses seen before no longer count. */
  public void clear() {
    pressed = NONE;
  }

  public boolean shift() {
    return (pressed & (LEFT_SHIFT | RIGHT_SHIFT)) != NONE;
  }

  public boolean control() {
    return (pressed & (LEFT_CONTROL | RIGHT_CONTROL)) != NONE;
  }

  public boolean alt() {
    return (pressed & (LEFT_ALT | RIGHT_ALT)) != NONE;
  }

  /** Command on macOS (the GUI keys). */
  public boolean command() {
    return (pressed & (LEFT_COMMAND | RIGHT_COMMAND)) != NONE;
  }

  private static int bitOf(int scancode) {
    return switch (scancode) {
      case InputConstants.KEY_LSHIFT -> LEFT_SHIFT;
      case InputConstants.KEY_RSHIFT -> RIGHT_SHIFT;
      case InputConstants.KEY_LCONTROL -> LEFT_CONTROL;
      case InputConstants.KEY_RCONTROL -> RIGHT_CONTROL;
      case InputConstants.KEY_LALT -> LEFT_ALT;
      case InputConstants.KEY_RALT -> RIGHT_ALT;
      case InputConstants.KEY_LGUI -> LEFT_COMMAND;
      case InputConstants.KEY_RGUI -> RIGHT_COMMAND;
      default -> NONE;
    };
  }
}
