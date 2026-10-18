package com.einsteinmods.shulkertoolkit.server;

import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;

/**
 * What the server knows about one connected player.
 *
 * @param policy features this player may use
 * @param preferences the player's last announced preferences (defaults until the first payload)
 * @param modClient whether the player's client has the mod: true once it sent its preferences
 */
public record PlayerSession(ServerPolicy policy, PlayerPreferences preferences, boolean modClient) {
  public PlayerSession withPreferences(PlayerPreferences newPreferences) {
    return new PlayerSession(policy, newPreferences, true);
  }
}
