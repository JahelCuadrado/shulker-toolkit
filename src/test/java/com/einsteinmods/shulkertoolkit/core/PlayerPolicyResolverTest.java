package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerPolicyResolverTest {
  private static final ServerPolicy NOTHING_ALLOWED =
      new ServerPolicy(false, false, false, false, false, false);

  @Test
  void singleplayerOrLanHostIsNeverLimited() {
    assertEquals(ServerPolicy.UNRESTRICTED, PlayerPolicyResolver.forPlayer(true, NOTHING_ALLOWED));
  }

  @Test
  void everyoneElseFollowsTheFile() {
    assertEquals(NOTHING_ALLOWED, PlayerPolicyResolver.forPlayer(false, NOTHING_ALLOWED));
  }

  @Test
  void unrestrictedAllowsEveryFeature() {
    assertEquals(new ServerPolicy(true, true, true, true, true, true), ServerPolicy.UNRESTRICTED);
  }
}
