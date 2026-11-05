package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.GameType;

/**
 * The depth level drives the pick from boxes and take-out requests ({@code
 * ShulkerPick#pickIfAllowed} and {@code #takeOutOnRequest}, what middle click and Litematica
 * reach): each level takes from the places it adds and leaves the deeper ones alone, writing every
 * container chain back without losing or copying an item. Auto refill keeps reading only the
 * inventory's shulker boxes. Players get fresh UUIDs; {@code ServerPlayerSessions#start} is never
 * called.
 */
public final class PickDepthGameTest {
  private static final int BUNDLE_STONE = 40;
  private static final int SOME_STONE = 10;
  private static final int CONTAINER_SLOT = 9;
  private static final int ENDER_ITEM_SLOT = 10;
  private static final int ENDER_SLOT = 4;
  private static final ServerPolicy NO_ENDER_PREVIEW =
      new ServerPolicy(true, true, true, false, true, true, true, true, true);

  @GameTest
  public void levelOneTakesFromABundleInTheInventory(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.SHALLOW);
    ItemStack bundle = stoneBundle();
    player.getInventory().setItem(CONTAINER_SLOT, bundle);
    int before = owned(player);

    ShulkerPick.TakeOutResult result =
        pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.SHALLOW));

    assertTaken(helper, player, result, BUNDLE_STONE, before);
    helper.assertTrue(
        bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
            == BundleContents.EMPTY,
        "the emptied bundle gets vanilla's shared empty contents");
    helper.assertTrue(result.boxSlot() == CONTAINER_SLOT, "got " + result);
    helper.succeed();
  }

  @GameTest
  public void levelOneLeavesBundlesInBoxesAndTheEnderChest(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.SHALLOW);
    player.getInventory().setItem(CONTAINER_SLOT, ShulkerFixtures.box(stoneBundle()));
    carryEnderChest(player);
    player.getEnderChestInventory().setItem(ENDER_SLOT, new ItemStack(Items.STONE, SOME_STONE));
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    ShulkerPick.TakeOutResult result =
        pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.SHALLOW));

    helper.assertFalse(result.succeeded(), "got " + result);
    helper.assertTrue(ShulkerFixtures.unchanged(player, before), "nothing may move");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_SLOT).getCount() == SOME_STONE,
        "the ender chest is untouched");
    helper.succeed();
  }

  @GameTest
  public void levelTwoTakesFromABundleInABox(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.DEEP);
    ItemStack box = ShulkerFixtures.box(stoneBundle());
    player.getInventory().setItem(CONTAINER_SLOT, box);
    int before = owned(player);

    ShulkerPick.TakeOutResult result =
        pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.DEEP));

    assertTaken(helper, player, result, BUNDLE_STONE, before);
    helper.assertTrue(
        player.getInventory().getItem(CONTAINER_SLOT) == box
            && ShulkerFixtures.countDeep(box, Items.BUNDLE) == 1,
        "the bundle stays in the box, which stays in its slot");
    helper.succeed();
  }

  @GameTest
  public void levelTwoTakesLooseEnderChestItems(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.DEEP);
    carryEnderChest(player);
    player.getEnderChestInventory().setItem(ENDER_SLOT, new ItemStack(Items.STONE, SOME_STONE));
    int before = owned(player);

    ShulkerPick.TakeOutResult result =
        pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.DEEP));

    assertTaken(helper, player, result, SOME_STONE, before);
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_SLOT).isEmpty(), "the ender slot is empty");
    helper.succeed();
  }

  @GameTest
  public void levelTwoTakesFromABoxAndABundleInTheEnderChest(GameTestHelper helper) {
    for (ItemStack container :
        List.of(ShulkerFixtures.box(new ItemStack(Items.STONE, SOME_STONE)), stoneBundle())) {
      ServerPlayer player = player(helper, CraftDepth.DEEP);
      carryEnderChest(player);
      player.getEnderChestInventory().setItem(ENDER_SLOT, container);
      int stored = ShulkerFixtures.countDeep(container, Items.STONE);
      int before = owned(player);

      ShulkerPick.TakeOutResult result =
          pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.DEEP));

      assertTaken(helper, player, result, stored, before);
      helper.assertTrue(
          player.getEnderChestInventory().getItem(ENDER_SLOT) == container
              && ShulkerFixtures.countDeep(container, Items.STONE) == 0,
          "the ender chest container is emptied in place");
    }
    helper.succeed();
  }

  @GameTest
  public void levelTwoNeedsAnEnderChestItemAndTheEnderPreview(GameTestHelper helper) {
    ServerPlayer withoutItem = player(helper, CraftDepth.DEEP);
    withoutItem
        .getEnderChestInventory()
        .setItem(ENDER_SLOT, new ItemStack(Items.STONE, SOME_STONE));
    ServerPlayer withoutPreview = player(helper, CraftDepth.DEEP);
    carryEnderChest(withoutPreview);
    withoutPreview
        .getEnderChestInventory()
        .setItem(ENDER_SLOT, new ItemStack(Items.STONE, SOME_STONE));

    helper.assertFalse(
        pick(withoutItem, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.DEEP)).succeeded(),
        "no ender item");
    helper.assertFalse(
        pick(withoutPreview, NO_ENDER_PREVIEW, preferences(CraftDepth.DEEP)).succeeded(),
        "no ender preview");
    helper.assertTrue(
        withoutItem.getEnderChestInventory().getItem(ENDER_SLOT).getCount() == SOME_STONE
            && withoutPreview.getEnderChestInventory().getItem(ENDER_SLOT).getCount() == SOME_STONE,
        "both ender chests are untouched");
    helper.succeed();
  }

  @GameTest
  public void levelTwoLeavesBundlesInEnderChestBoxes(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.DEEP);
    carryEnderChest(player);
    ItemStack enderBox = ShulkerFixtures.box(stoneBundle());
    player.getEnderChestInventory().setItem(ENDER_SLOT, enderBox);

    helper.assertFalse(
        pick(player, ServerPolicy.UNRESTRICTED, preferences(CraftDepth.DEEP)).succeeded(),
        "level 2 stops");
    helper.assertTrue(
        ShulkerFixtures.countDeep(enderBox, Items.STONE) == BUNDLE_STONE, "box untouched");
    helper.succeed();
  }

  /**
   * The deepest chain: a bundle in a shulker box in the ender chest. The bundle loses exactly what
   * went to the hand, stays in its box slot, and the box stays the same stack in its ender slot.
   */
  @GameTest
  public void levelThreeTakesFromABundleInAnEnderChestBoxWithoutLossOrCopies(
      GameTestHelper helper) {
    for (PickMode mode : PickMode.values()) {
      PlayerPreferences preferences =
          new PlayerPreferences(mode, true, false, false, false, true, CraftDepth.DEEPEST);
      ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, preferences);
      carryEnderChest(player);
      ItemStack enderBox = ShulkerFixtures.box(new ItemStack(Items.DIRT), stoneBundle());
      player.getEnderChestInventory().setItem(ENDER_SLOT, enderBox);
      int before = owned(player);
      int expected = mode == PickMode.SINGLE_ITEM ? 1 : BUNDLE_STONE;

      ShulkerPick.TakeOutResult result = pick(player, ServerPolicy.UNRESTRICTED, preferences);

      assertTaken(helper, player, result, expected, before);
      helper.assertTrue(
          player.getEnderChestInventory().getItem(ENDER_SLOT) == enderBox,
          "the box stays the same stack in its ender slot");
      helper.assertTrue(
          ShulkerFixtures.countDeep(enderBox, Items.STONE) == BUNDLE_STONE - expected
              && ShulkerFixtures.countDeep(enderBox, Items.BUNDLE) == 1
              && ShulkerFixtures.countDeep(enderBox, Items.DIRT) == 1,
          "the bundle stays in the box and loses exactly what was taken");
      helper.assertTrue(result.boxSlot() == PickPlanner.NO_SLOT, "got " + result);
    }
    helper.succeed();
  }

  /** Litematica's requests reach the same depth. */
  @GameTest
  public void takeOutRequestsFollowTheDepth(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.DEEPEST);
    carryEnderChest(player);
    player.getEnderChestInventory().setItem(ENDER_SLOT, ShulkerFixtures.box(stoneBundle()));
    int before = owned(player);

    ShulkerPick.TakeOutResult result =
        ShulkerPick.takeOutOnRequest(
            player,
            new ItemStack(Items.STONE),
            ServerPolicy.UNRESTRICTED,
            preferences(CraftDepth.DEEPEST));

    assertTaken(helper, player, result, BUNDLE_STONE, before);
    helper.succeed();
  }

  /** Auto refill was not part of the depth: it still reads only the inventory's shulker boxes. */
  @GameTest
  public void autoRefillIgnoresTheDepth(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.DEEPEST);
    player.getInventory().setItem(CONTAINER_SLOT, stoneBundle());
    ItemStack used = new ItemStack(Items.STONE);
    used.shrink(1);

    boolean refilled =
        ShulkerPick.refillIfAllowed(
            player,
            InteractionHand.MAIN_HAND,
            used,
            ServerPolicy.UNRESTRICTED,
            preferences(CraftDepth.DEEPEST));

    helper.assertFalse(refilled, "a bundle is not a refill source");
    helper.assertTrue(
        ShulkerFixtures.countDeep(player.getInventory().getItem(CONTAINER_SLOT), Items.STONE)
            == BUNDLE_STONE,
        "the bundle is untouched");
    helper.succeed();
  }

  private static PlayerPreferences preferences(CraftDepth depth) {
    return new PlayerPreferences(PickMode.FULL_STACK, true, false, false, false, true, depth);
  }

  private static ServerPlayer player(GameTestHelper helper, CraftDepth depth) {
    return ShulkerFixtures.player(helper, GameType.SURVIVAL, preferences(depth));
  }

  private static ShulkerPick.TakeOutResult pick(
      ServerPlayer player, ServerPolicy policy, PlayerPreferences preferences) {
    return ShulkerPick.pickIfAllowed(player, new ItemStack(Items.STONE), policy, preferences);
  }

  private static void carryEnderChest(ServerPlayer player) {
    player.getInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
  }

  private static ItemStack stoneBundle() {
    ItemStack bundle = new ItemStack(Items.BUNDLE);
    bundle.set(
        DataComponents.BUNDLE_CONTENTS,
        new BundleContents(
            List.of(
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STONE, BUNDLE_STONE)))));
    return bundle;
  }

  private static int owned(ServerPlayer player) {
    return ShulkerFixtures.countOwned(player, Items.STONE);
  }

  private static void assertTaken(
      GameTestHelper helper,
      ServerPlayer player,
      ShulkerPick.TakeOutResult result,
      int amount,
      int ownedBefore) {
    ItemStack held = player.getMainHandItem();
    helper.assertTrue(result.succeeded() && result.amount() == amount, "got " + result);
    helper.assertTrue(held.is(Items.STONE) && held.getCount() == amount, "got " + held);
    helper.assertTrue(
        owned(player) == ownedBefore,
        "stone owned went from " + ownedBefore + " to " + owned(player));
  }
}
