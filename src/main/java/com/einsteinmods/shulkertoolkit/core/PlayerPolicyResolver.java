package com.einsteinmods.shulkertoolkit.core;

/** Picks the policy that applies to one player. Pure and side-agnostic. */
public final class PlayerPolicyResolver {
  private PlayerPolicyResolver() {}

  /**
   * The singleplayer/LAN host is never limited; everyone else (LAN guests, dedicated server
   * players) follows the server policy file.
   */
  public static ServerPolicy forPlayer(boolean singleplayerOwner, ServerPolicy filePolicy) {
    return singleplayerOwner ? ServerPolicy.UNRESTRICTED : filePolicy;
  }
}
