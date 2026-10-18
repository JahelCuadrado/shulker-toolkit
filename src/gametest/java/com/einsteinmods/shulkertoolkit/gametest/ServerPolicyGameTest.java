package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.config.ServerPolicyFile;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Policy defaults on a running server. Uses fresh random UUIDs and never calls {@link
 * ServerPlayerSessions#start}, which would clear sessions under tests running in parallel.
 */
public final class ServerPolicyGameTest {
  @GameTest
  public void defaultPolicyFileAllowsEverything(GameTestHelper helper) {
    helper.assertTrue(
        ServerPolicyFile.DEFAULTS.toPolicy().equals(ServerPolicy.UNRESTRICTED),
        "default file must allow every feature");
    helper.assertTrue(
        ServerPlayerSessions.policy(UUID.randomUUID()).equals(ServerPolicy.UNRESTRICTED),
        "the file written at server start must allow every feature");
    helper.succeed();
  }

  @GameTest
  public void sessionsKeepPreferencesUntilThePlayerLeaves(GameTestHelper helper) {
    UUID guest = UUID.randomUUID();
    UUID host = UUID.randomUUID();
    PlayerPreferences singleItem = new PlayerPreferences(PickMode.SINGLE_ITEM, false);

    helper.assertTrue(
        ServerPlayerSessions.join(guest, false).equals(ServerPolicy.UNRESTRICTED),
        "guests follow the default file");
    helper.assertTrue(
        ServerPlayerSessions.join(host, true).equals(ServerPolicy.UNRESTRICTED),
        "the host is never limited");
    helper.assertTrue(
        ServerPlayerSessions.preferences(guest).equals(PlayerPreferences.DEFAULTS),
        "preferences start at the defaults");
    ServerPlayerSessions.updatePreferences(guest, singleItem);
    helper.assertTrue(
        ServerPlayerSessions.preferences(guest).equals(singleItem), "preferences are stored");
    ServerPlayerSessions.remove(guest);
    ServerPlayerSessions.remove(host);
    helper.assertTrue(
        ServerPlayerSessions.preferences(guest).equals(PlayerPreferences.DEFAULTS),
        "a player who left is forgotten");
    helper.succeed();
  }
}
