package com.einsteinmods.shulkertoolkit.core;

import org.jspecify.annotations.Nullable;

/**
 * What a click in a pinned window does, as the vanilla container inputs it mirrors ({@code
 * ContainerInput}). Each has a stable network {@link #id()} and the buttons it accepts.
 */
public enum PinnedAction {
  /** Vanilla {@code PICKUP}: button 0 takes or places all, button 1 half or one. */
  PICKUP(0),
  /** Vanilla {@code QUICK_MOVE} on a window slot: its stack goes to the player inventory. */
  QUICK_MOVE_OUT(1),
  /** Vanilla {@code QUICK_MOVE} on a player inventory slot: its stack goes into the window. */
  QUICK_MOVE_IN(2),
  /** Vanilla {@code SWAP}: a window slot and a hotbar slot (buttons 0-8) or the offhand (40). */
  SWAP(3);

  /** Vanilla {@code AbstractContainerMenu#isContainerClickButton}: 0 primary, 1 secondary. */
  public static final int PRIMARY_BUTTON = 0;

  public static final int SECONDARY_BUTTON = 1;

  /** Hotbar slots and the offhand slot, as vanilla {@code Inventory} numbers them. */
  public static final int HOTBAR_SLOTS = 9;

  public static final int OFFHAND_BUTTON = 40;

  private final int id;

  PinnedAction(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  /** The action with {@code id}, or null for an unknown one. */
  public static @Nullable PinnedAction fromId(int id) {
    for (PinnedAction action : values()) {
      if (action.id == id) {
        return action;
      }
    }
    return null;
  }

  /** Whether {@code button} is one this action accepts. */
  public boolean accepts(int button) {
    return switch (this) {
      case PICKUP -> button == PRIMARY_BUTTON || button == SECONDARY_BUTTON;
      case QUICK_MOVE_OUT, QUICK_MOVE_IN -> button == PRIMARY_BUTTON;
      case SWAP -> (button >= 0 && button < HOTBAR_SLOTS) || button == OFFHAND_BUTTON;
    };
  }
}
