package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Pick block from shulker boxes through vanilla's real path ({@code handlePickItemFromBlock} →
 * {@code tryPickItem}). Players get fresh UUIDs; {@code ServerPlayerSessions#start} is never
 * called.
 */
public final class PickFromShulkerGameTest {
  private static final int MAX_STACK = 64;
  private static final int BOX_STONE_FIRST_SLOT = 64;
  private static final int BOX_STONE_SECOND_SLOT = 36;
  private static final int BOX_STONE_TOTAL = BOX_STONE_FIRST_SLOT + BOX_STONE_SECOND_SLOT;
  private static final int FEW_STONE = 10;
  private static final int BOX_SLOT = 9;
  private static final int LOOSE_STONE_SLOT = 20;
  private static final int LOOSE_STONE_COUNT = 3;
  private static final int FREE_MAIN_SLOT = 30;
  private static final ServerPolicy PICK_FORBIDDEN =
      new ServerPolicy(true, false, true, true, true, true);

  @GameTest
  public void stoneOnlyInABoxGoesToTheHandAndLeavesTheBox(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);

    pickStone(helper, player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(
        held.is(Items.STONE) && held.getCount() == MAX_STACK,
        "full stack mode must put 64 stone in hand, got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE_TOTAL - MAX_STACK,
        "the box must lose exactly what was taken");
    helper.succeed();
  }

  @GameTest
  public void singleItemModeTakesExactlyOne(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper, GameType.SURVIVAL, new PlayerPreferences(PickMode.SINGLE_ITEM, true));
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);

    pickStone(helper, player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(held.is(Items.STONE) && held.getCount() == 1, "got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE_TOTAL - 1,
        "the box must lose one stone");
    helper.succeed();
  }

  @GameTest
  public void fullStackTakesWhatIsLeftWhenLessThanAStack(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.STONE, FEW_STONE));
    player.getInventory().setItem(BOX_SLOT, box);

    pickStone(helper, player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(held.is(Items.STONE) && held.getCount() == FEW_STONE, "got " + held);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == 0, "the box must be emptied of stone");
    helper.succeed();
  }

  @GameTest
  public void looseStoneIsPickedByVanillaAndTheBoxIsUntouched(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    ItemStack boxBefore = box.copy();
    player.getInventory().setItem(BOX_SLOT, box);
    player.getInventory().setItem(LOOSE_STONE_SLOT, new ItemStack(Items.STONE, LOOSE_STONE_COUNT));

    pickStone(helper, player);

    ItemStack held = player.getMainHandItem();
    helper.assertTrue(
        held.is(Items.STONE) && held.getCount() == LOOSE_STONE_COUNT,
        "vanilla must move the loose stack to the hand, got " + held);
    helper.assertTrue(ItemStack.matches(boxBefore, box), "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void creativeStaysVanilla(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.CREATIVE, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    ItemStack boxBefore = box.copy();
    player.getInventory().setItem(BOX_SLOT, box);

    pickStone(helper, player);

    helper.assertTrue(
        player.getMainHandItem().is(Items.STONE), "creative pick must still give stone");
    helper.assertTrue(ItemStack.matches(boxBefore, box), "creative must not take from the box");
    helper.succeed();
  }

  @GameTest
  public void playerWithoutTheModGetsVanilla(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL);
    player.getInventory().setItem(BOX_SLOT, stoneBox());
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    pickStone(helper, player);

    helper.assertTrue(
        ShulkerFixtures.unchanged(player, before),
        "without received preferences the pick must stay vanilla");
    helper.succeed();
  }

  @GameTest
  public void forbiddenPickStaysVanilla(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    ShulkerPick.TakeOutResult result =
        ShulkerPick.pickIfAllowed(
            player, new ItemStack(Items.STONE), PICK_FORBIDDEN, PlayerPreferences.DEFAULTS);

    helper.assertTrue(!result.succeeded(), "allowPick=false must not take anything");
    helper.assertTrue(ShulkerFixtures.unchanged(player, before), "inventory must not change");
    helper.succeed();
  }

  @GameTest
  public void fullHotbarMovesTheDisplacedStackToAFreeSlot(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    fillHotbarWithDirt(player);
    ItemStack box = stoneBox();
    player.getInventory().setItem(BOX_SLOT, box);

    pickStone(helper, player);

    Inventory inventory = player.getInventory();
    helper.assertTrue(player.getMainHandItem().is(Items.STONE), "stone must be in hand");
    helper.assertTrue(
        countEverywhere(player, Items.DIRT) == Inventory.getSelectionSize() * MAX_STACK,
        "no dirt may be lost");
    helper.assertTrue(
        inventory.getItem(BOX_SLOT + 1).is(Items.DIRT),
        "the displaced dirt goes to the first free slot");
    helper.succeed();
  }

  @GameTest
  public void fullInventoryRefusesAndLosesNothing(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    Inventory inventory = player.getInventory();
    for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
      inventory.setItem(slot, new ItemStack(Items.DIRT, MAX_STACK));
    }
    inventory.setItem(BOX_SLOT, stoneBox());
    List<ItemStack> before = ShulkerFixtures.snapshot(player);

    pickStone(helper, player);
    ShulkerPick.TakeOutResult direct =
        ShulkerPick.takeOut(player, new ItemStack(Items.STONE), PickMode.FULL_STACK);

    helper.assertTrue(
        direct.status() == ShulkerPick.Status.NO_ROOM, "a full inventory must refuse the pick");
    helper.assertTrue(ShulkerFixtures.unchanged(player, before), "nothing may move or vanish");
    helper.succeed();
  }

  @GameTest
  public void freeSlotIsFoundEvenWhenTheBoxSitsInTheTargetSlot(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    fillHotbarWithDirt(player);
    ItemStack box = stoneBox();
    player.getInventory().setItem(0, box);
    for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
      if (slot != FREE_MAIN_SLOT) {
        player.getInventory().setItem(slot, new ItemStack(Items.DIRT, MAX_STACK));
      }
    }

    ShulkerPick.TakeOutResult result =
        ShulkerPick.takeOut(player, new ItemStack(Items.STONE), PickMode.FULL_STACK);

    helper.assertTrue(result.succeeded(), "there is one free slot for the box");
    helper.assertTrue(
        result.boxSlot() == FREE_MAIN_SLOT && player.getInventory().getItem(FREE_MAIN_SLOT) == box,
        "the box moved to the free slot and the result says so");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == BOX_STONE_TOTAL - MAX_STACK,
        "the moved box lost what was taken");
    helper.succeed();
  }

  private static ItemStack stoneBox() {
    return ShulkerFixtures.box(
        new ItemStack(Items.STONE, BOX_STONE_FIRST_SLOT),
        new ItemStack(Items.STONE, BOX_STONE_SECOND_SLOT));
  }

  private static void pickStone(GameTestHelper helper, ServerPlayer player) {
    helper.setBlock(ShulkerFixtures.TARGET, Blocks.STONE);
    player.connection.handlePickItemFromBlock(
        new ServerboundPickItemFromBlockPacket(helper.absolutePos(ShulkerFixtures.TARGET), false));
  }

  private static void fillHotbarWithDirt(ServerPlayer player) {
    for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
      player.getInventory().setItem(slot, new ItemStack(Items.DIRT, MAX_STACK));
    }
  }

  private static int countEverywhere(ServerPlayer player, Item item) {
    int total = 0;
    for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
      if (stack.is(item)) {
        total += stack.getCount();
      }
    }
    return total;
  }
}
