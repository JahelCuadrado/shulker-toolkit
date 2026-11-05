package com.einsteinmods.shulkertoolkit.client.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Tells the ender chest's own menu on the client, whose {@code ChestMenu} has a container of its
 * own: only the title vanilla gives it says what it shows. The server knows for certain ({@code
 * TransferGate}); a screen titled otherwise at most shows a view the server's click then refuses. A
 * few comparisons per call.
 */
public final class EnderMenuScreen {
  /** Vanilla {@code EnderChestBlock#CONTAINER_TITLE}. */
  private static final String TITLE_KEY = "container.enderchest";

  private EnderMenuScreen() {}

  /** Whether the open screen shows the ender chest's own menu. */
  public static boolean isOpen() {
    return shows(Minecraft.getInstance().gui.screen());
  }

  static boolean shows(Screen screen) {
    return screen instanceof ContainerScreen
        && screen.getTitle().getContents() instanceof TranslatableContents title
        && TITLE_KEY.equals(title.getKey());
  }
}
