package com.einsteinmods.shulkertoolkit.client.config;

/**
 * A key that must be held for an action (Easy Shulker Boxes' {@code SimpleKeyType}). Pure: {@code
 * client/storage/InputConditions} reads the keyboard.
 */
public enum ModifierKey {
  ALWAYS,
  NEVER,
  SHIFT,
  CONTROL,
  /** Command on macOS, Control elsewhere. */
  CONTROL_OR_COMMAND,
  ALT
}
