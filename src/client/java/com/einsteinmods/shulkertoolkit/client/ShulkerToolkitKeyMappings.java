package com.einsteinmods.shulkertoolkit.client;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.config.ShulkerToolkitSettingsScreen;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Mod key mappings, all unbound by default so they never clash with vanilla or other mods. */
public final class ShulkerToolkitKeyMappings {
  private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(ShulkerToolkit.id("main"));

  private static final String PICK_MODE_MESSAGE = "message.shulkertoolkit.pick_mode";
  private static final String PICK_MODE_VALUE_PREFIX = "config.shulkertoolkit.pick_mode.";
  private static final String AUTO_REFILL_MESSAGE = "message.shulkertoolkit.auto_refill";

  private final KeyMapping openSettings = register("key.shulkertoolkit.open_settings");
  private final KeyMapping togglePickMode = register("key.shulkertoolkit.toggle_pick_mode");
  private final KeyMapping toggleAutoRefill = register("key.shulkertoolkit.toggle_auto_refill");
  private final KeyMapping toggleContents =
      register("key.shulkertoolkit.toggle_item_storage_tooltip");
  private final KeyMapping toggleCursorTooltip =
      register("key.shulkertoolkit.toggle_item_held_by_cursor_tooltip");

  private final ClientShulkerState state;

  public ShulkerToolkitKeyMappings(ClientShulkerState state) {
    this.state = state;
  }

  /** Mappings shown in the settings screen, in display order. */
  public List<KeyMapping> configurable() {
    return List.of(
        openSettings, togglePickMode, toggleAutoRefill, toggleContents, toggleCursorTooltip);
  }

  /** Toggles contents tooltips in container screens when they are set to the key. */
  public KeyMapping toggleContents() {
    return toggleContents;
  }

  /** Toggles the cursor item's tooltip in container screens when it is set to the key. */
  public KeyMapping toggleCursorTooltip() {
    return toggleCursorTooltip;
  }

  /** Per client tick: one click counter read per mapping while its key is not pressed. */
  public void onClientTick(Minecraft minecraft) {
    while (openSettings.consumeClick()) {
      // Clicks only count without a screen open; the check keeps a burst from stacking screens.
      if (minecraft.gui.screen() == null) {
        minecraft.gui.setScreen(new ShulkerToolkitSettingsScreen(null, state, this));
      }
    }
    while (togglePickMode.consumeClick()) {
      ClientPreferences preferences = state.preferences();
      PickMode next = preferences.pickMode().next();
      state.update(preferences.withPickMode(next));
      showOverlay(
          minecraft,
          Component.translatable(
              PICK_MODE_MESSAGE,
              Component.translatable(
                  PICK_MODE_VALUE_PREFIX + next.name().toLowerCase(Locale.ROOT))));
    }
    while (toggleAutoRefill.consumeClick()) {
      ClientPreferences preferences = state.preferences();
      boolean autoRefill = !preferences.autoRefill();
      state.update(preferences.withAutoRefill(autoRefill));
      showOverlay(
          minecraft,
          Component.translatable(AUTO_REFILL_MESSAGE, CommonComponents.optionStatus(autoRefill)));
    }
  }

  private static KeyMapping register(String name) {
    return KeyMappingHelper.registerKeyMapping(
        new KeyMapping(name, InputConstants.UNKNOWN.getValue(), CATEGORY));
  }

  private static void showOverlay(Minecraft minecraft, Component message) {
    LocalPlayer player = minecraft.player;
    if (player != null) {
      player.sendOverlayMessage(message);
    }
  }
}
