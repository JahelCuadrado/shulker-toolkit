package com.einsteinmods.shulkertoolkit.client.config;

import org.jspecify.annotations.Nullable;

/**
 * When a tooltip part is shown (Easy Shulker Boxes' {@code ItemStorageTooltip} and {@code
 * ItemHeldByCursorTooltip}): always, never, while a modifier key is held, or toggled by a key.
 */
public enum ShowMode {
  ALWAYS(ModifierKey.ALWAYS),
  NEVER(ModifierKey.NEVER),
  SHIFT(ModifierKey.SHIFT),
  CONTROL(ModifierKey.CONTROL),
  CONTROL_OR_COMMAND(ModifierKey.CONTROL_OR_COMMAND),
  ALT(ModifierKey.ALT),
  /** Toggled by its key mapping in container screens. */
  KEY(null);

  private final @Nullable ModifierKey modifier;

  ShowMode(@Nullable ModifierKey modifier) {
    this.modifier = modifier;
  }

  /** The key to hold, or null for {@link #KEY}. */
  public @Nullable ModifierKey modifier() {
    return modifier;
  }
}
