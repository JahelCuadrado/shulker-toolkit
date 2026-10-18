package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.TakeOutPayload;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The server side of {@code TakeOutPayload} (what Litematica Easy Place, the printer and Auto Take
 * Out send): {@code ShulkerPick#takeOutOnRequest}. The client hooks into Litematica cannot run in a
 * server GameTest.
 */
public final class TakeOutRequestGameTest {
  private static final int MAX_STACK = 64;
  private static final int BOX_STONE = 100;
  private static final int BOX_SLOT = 9;
  private static final ServerPolicy PICK_FORBIDDEN =
      new ServerPolicy(true, false, true, true, true, true);
  private static final PlayerPreferences SINGLE_ITEM =
      new PlayerPreferences(PickMode.SINGLE_ITEM, true);

  @GameTest
  public void requestTakesAFullStackAndSelectsIt(GameTestHelper helper) {
    ServerPlayer player = playerWithStoneBox(helper, GameType.SURVIVAL);

    ShulkerPick.TakeOutResult result = request(player, PlayerPreferences.DEFAULTS);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(result.status() == ShulkerPick.Status.TAKEN, "got " + result);
    helper.assertTrue(held.is(Items.STONE) && held.getCount() == MAX_STACK, "got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.STONE) == BOX_STONE - MAX_STACK,
        "the box must lose exactly what was taken");
    helper.succeed();
  }

  @GameTest
  public void requestFollowsThePlayersPickMode(GameTestHelper helper) {
    ServerPlayer player = playerWithStoneBox(helper, GameType.SURVIVAL);

    request(player, SINGLE_ITEM);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(held.is(Items.STONE) && held.getCount() == 1, "got " + held);
    helper.succeed();
  }

  @GameTest
  public void repeatedRequestDoesNotTakeTwice(GameTestHelper helper) {
    ServerPlayer player = playerWithStoneBox(helper, GameType.SURVIVAL);
    request(player, PlayerPreferences.DEFAULTS);
    List<ItemStack> afterFirst = ShulkerFixtures.snapshot(player);

    ShulkerPick.TakeOutResult second = request(player, PlayerPreferences.DEFAULTS);

    helper.assertTrue(second.status() == ShulkerPick.Status.IN_INVENTORY, "got " + second);
    helper.assertTrue(
        ShulkerFixtures.unchanged(player, afterFirst), "a loose stack must stop a second take");
    helper.succeed();
  }

  @GameTest
  public void forbiddenPickTakesNothing(GameTestHelper helper) {
    ServerPlayer player = playerWithStoneBox(helper, GameType.SURVIVAL);
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    ShulkerPick.TakeOutResult result =
        ShulkerPick.takeOutOnRequest(
            player, new ItemStack(Items.STONE), PICK_FORBIDDEN, PlayerPreferences.DEFAULTS);

    helper.assertFalse(result.succeeded(), "got " + result);
    helper.assertTrue(ShulkerFixtures.unchanged(player, before), "nothing may move");
    helper.succeed();
  }

  @GameTest
  public void creativeTakesNothing(GameTestHelper helper) {
    ServerPlayer player = playerWithStoneBox(helper, GameType.CREATIVE);
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    ShulkerPick.TakeOutResult result = request(player, PlayerPreferences.DEFAULTS);

    helper.assertFalse(result.succeeded(), "got " + result);
    helper.assertTrue(ShulkerFixtures.unchanged(player, before), "nothing may move");
    helper.succeed();
  }

  @GameTest
  public void payloadKeepsItemAndComponents(GameTestHelper helper) {
    ItemStack wanted = new ItemStack(Items.STONE);
    RegistryFriendlyByteBuf buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

    TakeOutPayload.CODEC.encode(buffer, new TakeOutPayload(wanted));
    ItemStack decoded = TakeOutPayload.CODEC.decode(buffer).wanted();

    helper.assertTrue(ItemStack.isSameItemSameComponents(wanted, decoded), "got " + decoded);
    helper.succeed();
  }

  private static ServerPlayer playerWithStoneBox(GameTestHelper helper, GameType gameType) {
    ServerPlayer player = ShulkerFixtures.player(helper, gameType, PlayerPreferences.DEFAULTS);
    player
        .getInventory()
        .setItem(
            BOX_SLOT,
            ShulkerFixtures.box(
                new ItemStack(Items.STONE, MAX_STACK),
                new ItemStack(Items.STONE, BOX_STONE - MAX_STACK)));
    return player;
  }

  private static ShulkerPick.TakeOutResult request(
      ServerPlayer player, PlayerPreferences preferences) {
    return ShulkerPick.takeOutOnRequest(
        player, new ItemStack(Items.STONE), ServerPolicy.UNRESTRICTED, preferences);
  }

  private static ItemStack box(ServerPlayer player) {
    return player.getInventory().getItem(BOX_SLOT);
  }
}
