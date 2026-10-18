package com.einsteinmods.shulkertoolkit.core;

/** How much pick-block takes out of a shulker box in the inventory. */
public enum PickMode {
  SINGLE_ITEM(0),
  FULL_STACK(1);

  public static final PickMode DEFAULT = FULL_STACK;

  private final int id;

  PickMode(int id) {
    this.id = id;
  }

  /** Stable network id; never reuse an id for another mode. */
  public int id() {
    return id;
  }

  /** The mode after this one, wrapping around: what the toggle key switches to. */
  public PickMode next() {
    PickMode[] modes = values();
    return modes[(ordinal() + 1) % modes.length];
  }

  /** Unknown ids (a newer client) fall back to {@link #DEFAULT}. */
  public static PickMode fromId(int id) {
    for (PickMode mode : values()) {
      if (mode.id == id) {
        return mode;
      }
    }
    return DEFAULT;
  }
}
