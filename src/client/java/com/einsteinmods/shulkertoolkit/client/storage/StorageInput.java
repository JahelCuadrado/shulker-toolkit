package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.config.ModifierKey;
import com.einsteinmods.shulkertoolkit.client.config.ShowMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Keyboard conditions of the container item options (Easy Shulker Boxes' {@code KeyType}s): which
 * modifier keys are held, the two key-toggled states and the "Hold Shift to reveal contents" line.
 * Key state reads are array lookups; the line is built once per mode, key and language. Render and
 * client thread only.
 */
public final class StorageInput {
  private static final String TOOLTIP_PREFIX = "tooltip." + ShulkerToolkit.MOD_ID + ".";
  private static final String REVEAL_CONTENTS = TOOLTIP_PREFIX + "reveal_contents";
  private static final Component HOLD = Component.translatable(TOOLTIP_PREFIX + "hold");
  private static final Component TOGGLE = Component.translatable(TOOLTIP_PREFIX + "toggle");
  private static final Component SHIFT = Component.translatable(TOOLTIP_PREFIX + "shift");
  private static final Component CONTROL = Component.translatable(TOOLTIP_PREFIX + "control");
  private static final Component COMMAND = Component.translatable(TOOLTIP_PREFIX + "command");
  private static final Component ALT = Component.translatable(TOOLTIP_PREFIX + "alt");

  /** Height of the reveal line: one text line plus a gap, as Easy Shulker Boxes. */
  private static final int HINT_HEIGHT_PADDING = 1;

  private static final ModifierTracker MODIFIERS = new ModifierTracker();

  private static @Nullable ClientShulkerState state;
  private static @Nullable KeyMapping contentsToggleKey;
  private static @Nullable KeyMapping cursorToggleKey;
  private static boolean contentsToggled;
  private static boolean cursorToggled;

  private static @Nullable ShowMode hintMode;
  private static InputConstants.@Nullable Key hintKey;
  private static @Nullable Language hintLanguage;
  private static @Nullable Component hint;
  private static int hintWidth;

  private StorageInput() {}

  public static void install(
      ClientShulkerState clientState, KeyMapping contentsToggle, KeyMapping cursorToggle) {
    state = clientState;
    contentsToggleKey = contentsToggle;
    cursorToggleKey = cursorToggle;
  }

  public static ClientPreferences preferences() {
    return state == null ? ClientPreferences.DEFAULTS : state.preferences();
  }

  /** The client state, once installed. */
  public static @Nullable ClientShulkerState state() {
    return state;
  }

  /** Modifier presses seen by the game, fed by the client mixins. */
  public static ModifierTracker modifiers() {
    return MODIFIERS;
  }

  /**
   * Whether {@code key} is held now ({@code SimpleKeyType#isUsed}): pressed inside the game since
   * the window last changed focus, and still down.
   */
  public static boolean isHeld(ModifierKey key) {
    // If-chains instead of an enum switch: no synthetic switch-map class in the jar.
    Minecraft minecraft = Minecraft.getInstance();
    if (key == ModifierKey.SHIFT) {
      return MODIFIERS.shift() && minecraft.hasShiftDown();
    }
    if (key == ModifierKey.CONTROL_OR_COMMAND && InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
      return MODIFIERS.command()
          && (InputConstants.isKeyDown(InputConstants.KEY_LGUI)
              || InputConstants.isKeyDown(InputConstants.KEY_RGUI));
    }
    if (key == ModifierKey.CONTROL || key == ModifierKey.CONTROL_OR_COMMAND) {
      return MODIFIERS.control() && minecraft.hasControlDown();
    }
    if (key == ModifierKey.ALT) {
      return MODIFIERS.alt() && minecraft.hasAltDown();
    }
    return key == ModifierKey.ALWAYS;
  }

  /** Whether contents tooltips show their grid ({@code item_storage_tooltip}). */
  public static boolean contentsShown() {
    return isUsed(preferences().itemStorageTooltip(), contentsToggled);
  }

  /** Whether the container item on the cursor shows its tooltip ({@code item_held_by_cursor}). */
  public static boolean cursorTooltipShown() {
    return isUsed(preferences().itemHeldByCursorTooltip(), cursorToggled);
  }

  /** Single item movement: its key is held and the server allows it. */
  public static boolean singleItemMovement() {
    return state != null
        && state.singleItemMovementAllowed()
        && isHeld(preferences().singleItemMovement());
  }

  /**
   * Whether a text field of {@code screen} takes the keys now (the anvil's name with an item in the
   * anvil): vanilla {@code AnvilScreen#keyPressed} then keeps every key for it ({@code
   * EditBox#canConsumeInput}), so the mod's keys must leave them too. One call and a type check.
   */
  public static boolean isTyping(Screen screen) {
    return screen.getFocused() instanceof EditBox field && field.canConsumeInput();
  }

  /**
   * Flips a key-toggled state when {@code event}'s key is one of the two toggle keys, whatever the
   * mode (Easy Shulker Boxes' {@code BackedKeyType#onKeyPressed}).
   *
   * @return true when the key was a toggle key (the press is consumed)
   */
  public static boolean toggle(KeyEvent event) {
    if (cursorToggleKey != null && cursorToggleKey.matches(event)) {
      cursorToggled = !cursorToggled;
      return true;
    }
    if (contentsToggleKey != null && contentsToggleKey.matches(event)) {
      contentsToggled = !contentsToggled;
      return true;
    }
    return false;
  }

  /**
   * The line shown instead of a hidden grid ("Hold Shift to reveal contents"), or null for none
   * ({@link ShowMode#NEVER}, or when the grid shows).
   */
  public static @Nullable Component revealHint(Font font) {
    ShowMode mode = preferences().itemStorageTooltip();
    InputConstants.Key key =
        contentsToggleKey == null
            ? InputConstants.UNKNOWN
            : KeyMappingHelper.getBoundKeyOf(contentsToggleKey);
    Language language = Language.getInstance();
    if (mode != hintMode || key != hintKey || language != hintLanguage) {
      hintMode = mode;
      hintKey = key;
      hintLanguage = language;
      hint = buildHint(mode);
      hintWidth = hint == null ? 0 : font.width(hint);
    }
    return hint;
  }

  /** Width of {@link #revealHint}, measured once per hint. */
  public static int revealHintWidth(Font font) {
    return revealHint(font) == null ? 0 : hintWidth;
  }

  /** Height of {@link #revealHint}, 0 without one. */
  public static int revealHintHeight(Font font) {
    return revealHint(font) == null ? 0 : font.lineHeight + HINT_HEIGHT_PADDING;
  }

  private static boolean isUsed(ShowMode mode, boolean toggled) {
    ModifierKey modifier = mode.modifier();
    return modifier == null ? toggled : isHeld(modifier);
  }

  private static @Nullable Component buildHint(ShowMode mode) {
    Component keyName = null;
    if (mode == ShowMode.SHIFT) {
      keyName = SHIFT;
    } else if (mode == ShowMode.CONTROL) {
      keyName = CONTROL;
    } else if (mode == ShowMode.CONTROL_OR_COMMAND) {
      keyName = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? COMMAND : CONTROL;
    } else if (mode == ShowMode.ALT) {
      keyName = ALT;
    } else if (mode == ShowMode.KEY && contentsToggleKey != null) {
      keyName = contentsToggleKey.getTranslatedKeyMessage();
    }
    if (keyName == null) {
      return null;
    }
    return Component.translatable(
            REVEAL_CONTENTS,
            mode == ShowMode.KEY ? TOGGLE : HOLD,
            keyName.copy().withStyle(ChatFormatting.LIGHT_PURPLE))
        .withStyle(ChatFormatting.GRAY);
  }
}
