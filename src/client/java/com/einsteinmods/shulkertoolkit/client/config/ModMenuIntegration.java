package com.einsteinmods.shulkertoolkit.client.config;

import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu "Configure" button; only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent ->
        new ShulkerToolkitSettingsScreen(
            parent, ShulkerToolkitClient.state(), ShulkerToolkitClient.keyMappings());
  }
}
