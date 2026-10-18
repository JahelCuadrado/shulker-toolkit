package com.einsteinmods.shulkertoolkit.client.config;

import com.google.gson.annotations.SerializedName;

/**
 * What container items show on their icon: the fill bar ({@code override_item_storage_bar}), Easy
 * Shulker Boxes' "+" while the cursor stack fits ({@code item_storage_indicator}), both or nothing.
 * The constant names stay as saved by schema 4; the serialized alternates read schema 3's values.
 */
public enum IconMarker {
  @SerializedName(
      value = "BAR_AND_MINIATURE",
      alternate = {"INDICATOR", "BOTH"})
  BAR_AND_MINIATURE(true, true),
  @SerializedName(
      value = "BAR",
      alternate = {"FILL_BAR"})
  BAR(true, false),
  MINIATURE(false, true),
  NONE(false, false);

  private final boolean fillBar;
  private final boolean indicator;

  IconMarker(boolean fillBar, boolean indicator) {
    this.fillBar = fillBar;
    this.indicator = indicator;
  }

  public boolean showsFillBar() {
    return fillBar;
  }

  public boolean showsIndicator() {
    return indicator;
  }

  /** The marker for the two switches of schema 2 (the "+" alone keeps the bar, as schema 3). */
  public static IconMarker ofLegacy(boolean indicator, boolean fillBar) {
    if (indicator) {
      return BAR_AND_MINIATURE;
    }
    return fillBar ? BAR : NONE;
  }
}
