package com.einsteinmods.shulkertoolkit.core;

/**
 * How one player's clicks move items in container items. The client sends it before a click when it
 * changed; the server and the client's prediction read the same value.
 *
 * @param moveSingleItem the single item movement key is held (and the server allows it): clicks
 *     move one item and always use {@link ClickScheme#SPLIT_INPUT}
 * @param clickScheme the player's chosen buttons
 */
public record ControlScheme(boolean moveSingleItem, ClickScheme clickScheme) {
  public static final ControlScheme DEFAULT = new ControlScheme(false, ClickScheme.DEFAULT);

  /** The buttons in effect: single item movement forces {@link ClickScheme#SPLIT_INPUT}. */
  public ClickScheme effectiveScheme() {
    return moveSingleItem ? ClickScheme.SPLIT_INPUT : clickScheme;
  }
}
