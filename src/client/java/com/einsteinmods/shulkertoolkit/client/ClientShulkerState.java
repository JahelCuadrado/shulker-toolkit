package com.einsteinmods.shulkertoolkit.client;

import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.config.ClientSettingsFile;
import com.einsteinmods.shulkertoolkit.config.JsonConfigFile;
import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.PlayerPreferencesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

/**
 * The player's saved preferences plus the policy of the current server. Server-backed features are
 * off until a server with the mod sends its policy, so servers without the mod never receive the
 * mod's packets. Client thread only.
 */
public final class ClientShulkerState {
  private final JsonConfigFile<ClientSettingsFile> file;
  private ClientPreferences preferences;
  private @Nullable ServerPolicy serverPolicy;

  public ClientShulkerState(JsonConfigFile<ClientSettingsFile> file) {
    this.file = file;
    this.preferences = file.load().toPreferences();
  }

  public ClientPreferences preferences() {
    return preferences;
  }

  /** Persists new preferences and, when connected to a server with the mod, sends its part. */
  public void update(ClientPreferences newPreferences) {
    preferences = newPreferences;
    file.save(ClientSettingsFile.from(preferences));
    sendPreferences();
  }

  public void onServerPolicy(ServerPolicy policy) {
    serverPolicy = policy;
    sendPreferences();
  }

  public void onDisconnect() {
    serverPolicy = null;
  }

  /** Policy of the current server, or null when not connected or the server has no mod. */
  public @Nullable ServerPolicy serverPolicy() {
    return serverPolicy;
  }

  /** Container item clicks: predicted only once a server with the mod allowed them. */
  public boolean transferAllowed() {
    return serverPolicy != null && serverPolicy.allowTransfer();
  }

  /** Whether the server takes items out of shulker boxes for this player's picks. */
  public boolean pickAllowed() {
    return serverPolicy != null && serverPolicy.allowPick();
  }

  public boolean enderPreviewAllowed() {
    return serverPolicy != null && serverPolicy.allowEnderPreview();
  }

  /** Dragging a container item over slots (needs transfer allowed too). */
  public boolean mouseDraggingAllowed() {
    return serverPolicy != null && serverPolicy.allowMouseDragging();
  }

  /** Moving one item per click or notch while its key is held. */
  public boolean singleItemMovementAllowed() {
    return serverPolicy != null && serverPolicy.allowSingleItemMovement();
  }

  /**
   * Where the recipe book takes ingredients from, or null when the server lacks the mod, forbids
   * it, or the player turned it off.
   */
  public @Nullable CraftScope craftScope() {
    return serverPolicy == null
        ? null
        : CraftScope.resolve(
            serverPolicy.allowCraftFromBoxes(),
            preferences.craftFromBoxes(),
            preferences.craftDepth(),
            serverPolicy.allowEnderPreview());
  }

  /** Pinned windows: the server checks each of their clicks. */
  public boolean pinnedWindowsAllowed() {
    return serverPolicy != null && serverPolicy.allowPinnedWindows();
  }

  private void sendPreferences() {
    if (serverPolicy != null && ClientPlayNetworking.canSend(PlayerPreferencesPayload.TYPE)) {
      ClientPlayNetworking.send(new PlayerPreferencesPayload(preferences.toPlayer()));
    }
  }
}
