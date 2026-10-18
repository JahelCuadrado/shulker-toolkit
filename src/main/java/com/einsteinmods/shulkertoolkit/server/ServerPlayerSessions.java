package com.einsteinmods.shulkertoolkit.server;

import com.einsteinmods.shulkertoolkit.core.PlayerPolicyResolver;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Policy and preferences of every connected player, keyed by UUID and removed on disconnect. A map
 * (not a player attachment): it is only touched on join, on a preferences payload and on rare
 * requests, and it needs no registration or persistence. Accessed only from the server thread
 * (connection events and payload handlers).
 */
public final class ServerPlayerSessions {
  private static final Map<UUID, PlayerSession> SESSIONS = new HashMap<>();
  private static ServerPolicy filePolicy = ServerPolicy.UNRESTRICTED;

  private ServerPlayerSessions() {}

  public static void start(ServerPolicy serverFilePolicy) {
    SESSIONS.clear();
    filePolicy = serverFilePolicy;
  }

  public static void stop() {
    SESSIONS.clear();
  }

  /** Records a joining player with default preferences and returns their policy. */
  public static ServerPolicy join(UUID playerId, boolean singleplayerOwner) {
    ServerPolicy policy = PlayerPolicyResolver.forPlayer(singleplayerOwner, filePolicy);
    SESSIONS.put(playerId, new PlayerSession(policy, PlayerPreferences.DEFAULTS, false));
    return policy;
  }

  public static void updatePreferences(UUID playerId, PlayerPreferences preferences) {
    SESSIONS.computeIfPresent(playerId, (id, session) -> session.withPreferences(preferences));
  }

  public static void remove(UUID playerId) {
    SESSIONS.remove(playerId);
  }

  /** The player's policy; the file policy for players that have not joined (never more). */
  public static ServerPolicy policy(UUID playerId) {
    PlayerSession session = SESSIONS.get(playerId);
    return session == null ? filePolicy : session.policy();
  }

  /**
   * The session of a player whose client has the mod (it sent its preferences), or null. Gate for
   * server-side features that act on their own (pick from boxes, auto refill): players without the
   * mod get pure vanilla. One map lookup.
   */
  public static @Nullable PlayerSession modClientSession(UUID playerId) {
    PlayerSession session = SESSIONS.get(playerId);
    return session != null && session.modClient() ? session : null;
  }

  /** The player's preferences; defaults for players that have not sent any. */
  public static PlayerPreferences preferences(UUID playerId) {
    PlayerSession session = SESSIONS.get(playerId);
    return session == null ? PlayerPreferences.DEFAULTS : session.preferences();
  }
}
