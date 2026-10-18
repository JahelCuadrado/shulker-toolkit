package com.einsteinmods.shulkertoolkit.config;

import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import org.jspecify.annotations.Nullable;

/**
 * On-disk shape of {@code config/shulkertoolkit-server.json} (every server; never applied to the
 * singleplayer/LAN host). A missing flag (Gson reads it as null) means allowed, so a file written
 * by an older version keeps new features on.
 */
public record ServerPolicyFile(
    int schemaVersion,
    @Nullable Boolean allowTransfer,
    @Nullable Boolean allowPick,
    @Nullable Boolean allowRefill,
    @Nullable Boolean allowEnderPreview,
    @Nullable Boolean allowMouseDragging,
    @Nullable Boolean allowSingleItemMovement,
    @Nullable Boolean allowPickupIntoBoxes,
    @Nullable Boolean allowCraftFromBoxes,
    @Nullable Boolean allowPinnedWindows) {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final String FILE_NAME = "shulkertoolkit-server.json";

  public static final ServerPolicyFile DEFAULTS = from(ServerPolicy.UNRESTRICTED);

  public static ServerPolicyFile from(ServerPolicy policy) {
    return new ServerPolicyFile(
        CURRENT_SCHEMA_VERSION,
        policy.allowTransfer(),
        policy.allowPick(),
        policy.allowRefill(),
        policy.allowEnderPreview(),
        policy.allowMouseDragging(),
        policy.allowSingleItemMovement(),
        policy.allowPickupIntoBoxes(),
        policy.allowCraftFromBoxes(),
        policy.allowPinnedWindows());
  }

  public ServerPolicy toPolicy() {
    return new ServerPolicy(
        allowedUnlessFalse(allowTransfer),
        allowedUnlessFalse(allowPick),
        allowedUnlessFalse(allowRefill),
        allowedUnlessFalse(allowEnderPreview),
        allowedUnlessFalse(allowMouseDragging),
        allowedUnlessFalse(allowSingleItemMovement),
        allowedUnlessFalse(allowPickupIntoBoxes),
        allowedUnlessFalse(allowCraftFromBoxes),
        allowedUnlessFalse(allowPinnedWindows));
  }

  private static boolean allowedUnlessFalse(@Nullable Boolean flag) {
    return flag == null || flag;
  }
}
