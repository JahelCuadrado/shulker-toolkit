package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Auto refill through vanilla's real placement path ({@code ServerPlayerGameMode#useItemOn} →
 * {@code BlockItem#place}). Players get fresh UUIDs; {@code ServerPlayerSessions#start} is never
 * called.
 */
public final class AutoRefillGameTest {
  private static final int MAX_STACK = 64;
  private static final int BOX_STONE_SECOND_SLOT = 36;
  private static final int BOX_STONE = MAX_STACK + BOX_STONE_SECOND_SLOT;
  private static final int HELD_STONE = 2;
  private static final int BOX_SLOT = 9;
  private static final double TOP_FACE_OFFSET = 0.5;
  private static final int STEAKS_IN_BOX = 20;
  private static final byte FINISH_USING_ITEM_EVENT = 9;
  private static final ServerPolicy REFILL_FORBIDDEN =
      new ServerPolicy(true, true, false, true, true, true);

  @GameTest
  public void placingTheLastBlockRefillsTheHand(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));

    placeHeldBlock(helper, player);

    helper.assertBlockPresent(Blocks.STONE, ShulkerFixtures.TARGET.above());
    ItemStack held = player.getMainHandItem();
    helper.assertTrue(
        held.is(Items.STONE) && held.getCount() == MAX_STACK,
        "the hand must be refilled with a full stack, got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE - MAX_STACK,
        "the box must lose exactly the refill");
    helper.succeed();
  }

  @GameTest
  public void refillIgnoresSingleItemPickMode(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper, GameType.SURVIVAL, new PlayerPreferences(PickMode.SINGLE_ITEM, true));
    player.getInventory().setItem(BOX_SLOT, stoneBox());
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));

    placeHeldBlock(helper, player);

    helper.assertTrue(
        player.getMainHandItem().getCount() == MAX_STACK, "refill always takes a full stack");
    helper.succeed();
  }

  @GameTest
  public void refillOffLeavesTheHandEmpty(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper, GameType.SURVIVAL, new PlayerPreferences(PickMode.FULL_STACK, false));
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));

    placeHeldBlock(helper, player);

    helper.assertBlockPresent(Blocks.STONE, ShulkerFixtures.TARGET.above());
    helper.assertTrue(player.getMainHandItem().isEmpty(), "auto refill off: the hand stays empty");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE, "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void playerWithoutTheModIsNotRefilled(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));

    placeHeldBlock(helper, player);

    helper.assertTrue(player.getMainHandItem().isEmpty(), "no mod client: the hand stays empty");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE, "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void eatingTheLastSteakRefillsTheHand(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.COOKED_BEEF, STEAKS_IN_BOX));
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));

    finishUsingHeldItem(player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(
        held.is(Items.COOKED_BEEF) && held.getCount() == STEAKS_IN_BOX,
        "the hand must be refilled with the box's steaks, got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.COOKED_BEEF) == 0, "the box gave its steaks");
    helper.succeed();
  }

  @GameTest
  public void drinkingTheLastPotionRefillsThatExactPotion(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack swiftness = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
    ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
    ItemStack box = ShulkerFixtures.box(healing.copy(), swiftness.copy());
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, swiftness.copy());

    finishUsingHeldItem(player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(
        ItemStack.isSameItemSameComponents(held, swiftness),
        "the hand must hold a swiftness potion again, got " + held);
    helper.assertTrue(
        player.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)),
        "the empty bottle goes to the inventory");
    ItemContainerContents left =
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    helper.assertTrue(
        left.nonEmptyItemCopyStream()
                .allMatch(stack -> ItemStack.isSameItemSameComponents(stack, healing))
            && left.nonEmptyItemCopyStream().count() == 1,
        "only the swiftness potion leaves the box");
    helper.succeed();
  }

  @GameTest
  public void refillOffLeavesTheEatenSteakGone(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper, GameType.SURVIVAL, new PlayerPreferences(PickMode.FULL_STACK, false));
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.COOKED_BEEF, STEAKS_IN_BOX));
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));

    finishUsingHeldItem(player);

    helper.assertTrue(player.getMainHandItem().isEmpty(), "auto refill off: the hand stays empty");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.COOKED_BEEF) == STEAKS_IN_BOX,
        "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void placingWithStoneLeftDoesNotRefill(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, HELD_STONE));

    placeHeldBlock(helper, player);

    helper.assertTrue(player.getMainHandItem().getCount() == HELD_STONE - 1, "one stone placed");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE, "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void forbiddenRefillLeavesTheHandEmpty(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    ItemStack used = new ItemStack(Items.STONE);
    player.setItemInHand(InteractionHand.MAIN_HAND, used);
    used.shrink(1);

    boolean refilled =
        ShulkerPick.refillIfAllowed(
            player, InteractionHand.MAIN_HAND, used, REFILL_FORBIDDEN, PlayerPreferences.DEFAULTS);

    helper.assertTrue(!refilled && player.getMainHandItem().isEmpty(), "allowRefill=false");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE, "the box must not change");
    helper.succeed();
  }

  /**
   * Starts using the held item and finishes it at once through vanilla: entity event 9 is what
   * {@code Player#handleEntityEvent} turns into {@code completeUsingItem}, the call {@code
   * LivingEntity#updateUsingItem} makes when the use time runs out (fake players do not tick).
   */
  private static void finishUsingHeldItem(ServerPlayer player) {
    player.getFoodData().setFoodLevel(0);
    player.startUsingItem(InteractionHand.MAIN_HAND);
    player.handleEntityEvent(FINISH_USING_ITEM_EVENT);
  }

  private static ItemStack stoneBox() {
    return ShulkerFixtures.box(
        new ItemStack(Items.STONE, MAX_STACK), new ItemStack(Items.STONE, BOX_STONE_SECOND_SLOT));
  }

  private static void placeHeldBlock(GameTestHelper helper, ServerPlayer player) {
    helper.setBlock(ShulkerFixtures.TARGET, Blocks.STONE);
    helper.setBlock(ShulkerFixtures.TARGET.above(), Blocks.AIR);
    BlockPos target = helper.absolutePos(ShulkerFixtures.TARGET);
    BlockHitResult hit =
        new BlockHitResult(
            Vec3.atCenterOf(target).add(0.0, TOP_FACE_OFFSET, 0.0), Direction.UP, target, false);
    ItemStack held = player.getMainHandItem();
    player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND, hit);
  }
}
