package com.einsteinmods.shulkertoolkit.transfer;

import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.ServerPolicyPayload;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a click may move items into or out of a container item. The server decides: its policy
 * for the player must allow transfer (and the ender chest preview, for the ender chest) and the
 * player's client must have the mod (a vanilla client keeps vanilla clicks). The client predicts
 * only after a server with the mod allowed it, so on a server without the mod every click stays
 * vanilla.
 */
public final class TransferGate {
  /** Set by the client entrypoint; a dedicated server never predicts. */
  private static BooleanSupplier clientAllows = () -> false;

  private static BooleanSupplier clientAllowsEnder = () -> false;
  private static BooleanSupplier clientBundles = () -> false;

  private TransferGate() {}

  public static void installClientCheck(
      BooleanSupplier transfer, BooleanSupplier enderChest, BooleanSupplier bundles) {
    clientAllows = transfer;
    clientAllowsEnder = enderChest;
    clientBundles = bundles;
  }

  /** Called on clicks only: one side check, then one map lookup and one channel lookup. */
  public static boolean allows(Player player) {
    if (player.level().isClientSide()) {
      return clientAllows.getAsBoolean();
    }
    return player instanceof ServerPlayer serverPlayer
        && serverAllows(policyOf(serverPlayer), clientHasMod(serverPlayer));
  }

  /**
   * Whether {@code kind} may be edited by {@code player}, once {@link #allows} said yes: the ender
   * chest needs its preview allowed, bundles the player's bundle interactions option (else they
   * stay vanilla).
   */
  public static boolean allowsKind(Player player, StorageKind kind) {
    boolean client = player.level().isClientSide();
    if (kind == StorageKind.BUNDLE) {
      return client
          ? clientBundles.getAsBoolean()
          : ServerPlayerSessions.preferences(player.getUUID()).bundleInteractions();
    }
    if (kind != StorageKind.ENDER_CHEST) {
      return true;
    }
    return client
        ? clientAllowsEnder.getAsBoolean()
        : player instanceof ServerPlayer serverPlayer && policyOf(serverPlayer).allowEnderPreview();
  }

  /** The server's rule, without the player lookups. */
  public static boolean serverAllows(ServerPolicy policy, boolean clientHasMod) {
    return clientHasMod && policy.allowTransfer();
  }

  /** Whether {@code player}'s client has the mod (registered the mod's channels). */
  public static boolean clientHasMod(ServerPlayer player) {
    return player.connection != null
        && ServerPlayNetworking.canSend(player, ServerPolicyPayload.TYPE);
  }

  private static ServerPolicy policyOf(ServerPlayer player) {
    return ServerPlayerSessions.policy(player.getUUID());
  }
}
