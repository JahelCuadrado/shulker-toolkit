package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.network.EnderContentsPayload;
import com.einsteinmods.shulkertoolkit.network.SelectStoredItemPayload;
import com.einsteinmods.shulkertoolkit.network.ShulkerToolkitNetworking;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Server side of the ender chest sync and of the client's selection and control scheme packets. */
public final class EnderChestSyncGameTest {
  private static final int FIRST_SLOT = 0;
  private static final int MIDDLE_SLOT = 13;
  private static final int LAST_SLOT = 26;
  private static final int DIAMOND_COUNT = 5;
  private static final int TORCH_COUNT = 64;
  private static final int SELECTED_SLOT = 4;

  @GameTest
  public void contentsHoldCopiesOfEveryEnderSlot(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.getEnderChestInventory().clearContent();
    player
        .getEnderChestInventory()
        .setItem(FIRST_SLOT, new ItemStack(Items.DIAMOND, DIAMOND_COUNT));
    player.getEnderChestInventory().setItem(MIDDLE_SLOT, new ItemStack(Items.TORCH, TORCH_COUNT));
    player.getEnderChestInventory().setItem(LAST_SLOT, new ItemStack(Items.SHULKER_BOX));

    EnderContentsPayload payload = EnderChestSync.contentsOf(player);

    List<ItemStack> items = payload.items();
    helper.assertTrue(
        items.size() == player.getEnderChestInventory().getContainerSize(), "every slot is sent");
    for (int slot = 0; slot < items.size(); slot++) {
      ItemStack expected = player.getEnderChestInventory().getItem(slot);
      helper.assertTrue(
          ItemStack.matches(expected, items.get(slot)),
          "slot " + slot + " should be " + expected + ", was " + items.get(slot));
      helper.assertTrue(
          expected.isEmpty() || expected != items.get(slot),
          "the payload must not share stacks with the server (singleplayer passes it as is)");
    }
    helper.succeed();
  }

  /** A fake player has no mod channels: nothing may be sent to it. */
  @GameTest
  public void playersWithoutTheModReceiveNothing(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL);
    player.getEnderChestInventory().setItem(FIRST_SLOT, new ItemStack(Items.DIAMOND));

    EnderChestSync.sendContents(player);
    EnderChestSync.onMenuOpened(player, player.inventoryMenu);

    helper.succeed();
  }

  @GameTest
  public void selectionFromAClientWithoutTheModIsIgnored(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    player.containerMenu.setCarried(ShulkerFixtures.box(new ItemStack(Items.DIRT)));
    StorageStates.of(player).setSelectedItem(StorageGrid.NO_ITEM);

    ShulkerToolkitNetworking.selectStoredItem(
        player, new SelectStoredItemPayload(SelectStoredItemPayload.CARRIED, SELECTED_SLOT, false));

    helper.assertTrue(
        StorageStates.of(player).selectedItem() == StorageGrid.NO_ITEM,
        "the transfer gate (no mod channels) must drop the selection");
    StorageStates.forget(player.getUUID());
    helper.succeed();
  }

  @GameTest
  public void controlSchemeIsStoredWithSingleItemMovementAllowed(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ControlScheme scheme = new ControlScheme(true, ClickScheme.SPLIT_INPUT);

    ShulkerToolkitNetworking.setControlScheme(player, scheme);

    helper.assertTrue(
        StorageStates.of(player).controlScheme().equals(scheme),
        "the default policy allows single item movement");
    StorageStates.forget(player.getUUID());
    helper.succeed();
  }
}
