package com.einsteinmods.shulkertoolkit.core;

/**
 * Which mouse button inserts into and which one extracts from a container item (Easy Shulker Boxes'
 * {@code control_scheme}).
 */
public enum ClickScheme {
  /** As vanilla bundles: left click inserts, right click extracts. */
  SPLIT_INPUT(0, true, false),
  /** Right click does both: on a stack it inserts, on an empty slot it extracts. */
  SINGLE_INPUT(1, false, false);

  public static final ClickScheme DEFAULT = SINGLE_INPUT;

  private final int id;
  private final boolean insertsWithPrimary;
  private final boolean extractsWithPrimary;

  ClickScheme(int id, boolean insertsWithPrimary, boolean extractsWithPrimary) {
    this.id = id;
    this.insertsWithPrimary = insertsWithPrimary;
    this.extractsWithPrimary = extractsWithPrimary;
  }

  /** Stable network id; never reuse an id for another scheme. */
  public int id() {
    return id;
  }

  /** Whether a click with the primary ({@code true}) or secondary button inserts. */
  public boolean inserts(boolean primary) {
    return primary == insertsWithPrimary;
  }

  /** Whether a click with the primary ({@code true}) or secondary button extracts. */
  public boolean extracts(boolean primary) {
    return primary == extractsWithPrimary;
  }

  /** Unknown ids (a newer client) fall back to {@link #DEFAULT}. */
  public static ClickScheme fromId(int id) {
    for (ClickScheme scheme : values()) {
      if (scheme.id == id) {
        return scheme;
      }
    }
    return DEFAULT;
  }
}
