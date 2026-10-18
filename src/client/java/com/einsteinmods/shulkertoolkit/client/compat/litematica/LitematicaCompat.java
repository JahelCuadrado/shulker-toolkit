package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Litematica support: the mod ids it needs and one warning when a hook cannot work with
 * the installed version. Nothing here loads Litematica classes.
 */
public final class LitematicaCompat {
  /** Litematica: Easy Place, schematic pick block and Auto Take Out. */
  public static final String LITEMATICA_MOD_ID = "litematica";

  /** Litematica Printer (aleksilassila's printer, published on Modrinth as litematica-printer). */
  public static final String PRINTER_MOD_ID = "litematica_printer";

  static final Logger LOGGER = LoggerFactory.getLogger("shulkertoolkit");

  private LitematicaCompat() {}

  public static boolean isInstalled() {
    return FabricLoader.getInstance().isModLoaded(LITEMATICA_MOD_ID);
  }

  /** The one warning shown when {@code feature} is off because the installed version differs. */
  static void warnUnsupported(String feature, String reason) {
    LOGGER.warn(
        "Shulker Toolkit: {} cannot take blocks out of shulker boxes with this Litematica version ({}); "
            + "the rest of the mod works.",
        feature,
        reason);
  }
}
