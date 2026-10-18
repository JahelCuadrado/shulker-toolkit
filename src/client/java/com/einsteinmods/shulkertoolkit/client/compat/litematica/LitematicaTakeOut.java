package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Entry points for the Litematica and printer mixins. Uses only vanilla types, so no Litematica API
 * change can break it; the mixins check their targets at startup.
 */
public final class LitematicaTakeOut {
  private static @Nullable TakeOutRequester requester;

  private LitematicaTakeOut() {}

  /**
   * Litematica {@code InventoryUtils#schematicWorldPickBlock} (Easy Place, both its legacy and
   * rewritten paths, and pick block on a schematic block) found no loose stack in survival.
   *
   * @return true when Shulker Toolkit asked the server for the item, so Litematica must not move a
   *     shulker box to the hand ({@code pickBlockShulkers}) meanwhile
   */
  public static boolean onSchematicPickWithoutLooseStack(ItemStack wanted, Minecraft minecraft) {
    LocalPlayer player = minecraft.player;
    return player != null && requester().request(player, wanted);
  }

  /**
   * Printer {@code Guide#getRequiredItem} found none of {@code requiredItems} loose, so the printer
   * skips the position: request the first one a box holds; a later tick prints it.
   */
  public static void onPrinterItemsMissing(LocalPlayer player, List<ItemStack> requiredItems) {
    TakeOutRequester active = requester();
    for (int index = 0; index < requiredItems.size(); index++) {
      if (active.request(player, requiredItems.get(index))) {
        return;
      }
    }
  }

  static TakeOutRequester requester() {
    TakeOutRequester active = requester;
    if (active == null) {
      active = new TakeOutRequester(ShulkerToolkitClient.state());
      requester = active;
    }
    return active;
  }
}
