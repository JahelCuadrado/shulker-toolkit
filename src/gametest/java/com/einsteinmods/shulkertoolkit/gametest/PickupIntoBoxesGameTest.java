package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.pick.PickupIntoBoxes;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Picked-up items into shulker boxes through vanilla's real pickup path ({@code
 * ItemEntity#playerTouch} → {@code Inventory#add}). Players get fresh UUIDs; {@code
 * ServerPlayerSessions#start} is never called.
 */
public final class PickupIntoBoxesGameTest {
  private static final int MAX_STACK = 64;
  private static final int FIRST_BOX_SLOT = 3;
  private static final int SECOND_BOX_SLOT = 9;
  private static final int THIRD_BOX_SLOT = 20;
  private static final int STONE_IN_BOX = 10;
  private static final int PICKED = 20;
  private static final int LOOSE_DIRT = 3;
  private static final int DIRT_IN_BOX = 60;
  private static final String CUSTOM_NAME = "renamed";
  private static final Vec3 DROP_POSITION = new Vec3(0.5, 1.0, 0.5);

  private static final PlayerPreferences OVERFLOW =
      new PlayerPreferences(PickMode.DEFAULT, true, false, true, false);
  private static final PlayerPreferences DIRECT =
      new PlayerPreferences(PickMode.DEFAULT, true, false, false, true);
  private static final PlayerPreferences BOTH =
      new PlayerPreferences(PickMode.DEFAULT, true, false, true, true);
  private static final ServerPolicy PICKUP_FORBIDDEN =
      new ServerPolicy(true, true, true, true, true, true, false);

  @GameTest
  public void overflowGoesToTheBoxHoldingTheItem(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, OVERFLOW);
    ItemStack dirtBox = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    ItemStack stoneBox = ShulkerFixtures.box(new ItemStack(Items.STONE, STONE_IN_BOX));
    fillInventory(player);
    player.getInventory().setItem(FIRST_BOX_SLOT, dirtBox);
    player.getInventory().setItem(SECOND_BOX_SLOT, stoneBox);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.STONE, PICKED));

    helper.assertTrue(dropped.isRemoved(), "the whole stack must be taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(stoneBox, Items.STONE) == STONE_IN_BOX + PICKED,
        "the stone must go to the box already holding stone");
    helper.assertTrue(
        ShulkerFixtures.countInBox(dirtBox, Items.STONE) == 0, "the first box must not change");
    helper.succeed();
  }

  @GameTest
  public void overflowWithoutAMatchingBoxUsesTheFirstWithRoom(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, OVERFLOW);
    ItemStack fullBox = fullBoxOf(Items.DIRT);
    ItemStack firstWithRoom = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    ItemStack emptyBox = ShulkerFixtures.box();
    fillInventory(player);
    player.getInventory().setItem(FIRST_BOX_SLOT, fullBox);
    player.getInventory().setItem(SECOND_BOX_SLOT, firstWithRoom);
    player.getInventory().setItem(THIRD_BOX_SLOT, emptyBox);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.STONE, PICKED));

    helper.assertTrue(dropped.isRemoved(), "the whole stack must be taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(firstWithRoom, Items.STONE) == PICKED,
        "the stone must go to the first box with room");
    helper.assertTrue(
        ShulkerFixtures.countInBox(emptyBox, Items.STONE) == 0, "later boxes must not change");
    helper.succeed();
  }

  @GameTest
  public void overflowLeavesWhatDoesNotFitOnTheGround(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, OVERFLOW);
    ItemStack box = boxWithRoomFor(Items.STONE, STONE_IN_BOX);
    fillInventory(player);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.STONE, PICKED));

    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == MAX_STACK, "the box takes what fits");
    helper.assertTrue(
        !dropped.isRemoved() && dropped.getItem().getCount() == PICKED - STONE_IN_BOX,
        "the rest must stay on the ground, got " + dropped.getItem());
    helper.succeed();
  }

  @GameTest
  public void directGoesToTheBoxEvenWithRoom(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, DIRECT);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.DIRT, PICKED));

    helper.assertTrue(dropped.isRemoved(), "the whole stack must be taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.DIRT) == 1 + PICKED, "the dirt must go to the box");
    helper.assertTrue(player.getInventory().countItem(Items.DIRT) == 0, "no dirt may land loose");
    helper.succeed();
  }

  @GameTest
  public void directMergesWithACarriedLooseStack(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, DIRECT);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    player.getInventory().setItem(0, new ItemStack(Items.DIRT, LOOSE_DIRT));
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    pickUp(helper, player, new ItemStack(Items.DIRT, PICKED));

    helper.assertTrue(
        player.getInventory().getItem(0).getCount() == LOOSE_DIRT + PICKED,
        "the dirt must join the loose stack");
    helper.assertTrue(ShulkerFixtures.countInBox(box, Items.DIRT) == 1, "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void directSendsWhatTheBoxCannotTakeToTheInventory(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, DIRECT);
    ItemStack box = boxWithRoomFor(Items.DIRT, MAX_STACK - DIRT_IN_BOX);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.DIRT, PICKED));

    helper.assertTrue(dropped.isRemoved(), "the whole stack must be taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.DIRT) == MAX_STACK, "the box must be filled");
    helper.assertTrue(
        player.getInventory().countItem(Items.DIRT) == PICKED - (MAX_STACK - DIRT_IN_BOX),
        "the rest must go to the inventory");
    helper.succeed();
  }

  @GameTest
  public void directNeedsTheSameComponents(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, DIRECT);
    ItemStack renamed = new ItemStack(Items.DIRT);
    renamed.set(DataComponents.CUSTOM_NAME, Component.literal(CUSTOM_NAME));
    ItemStack box = ShulkerFixtures.box(renamed);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    pickUp(helper, player, new ItemStack(Items.DIRT, PICKED));

    helper.assertTrue(
        player.getInventory().countItem(Items.DIRT) == PICKED,
        "plain dirt is not the renamed dirt in the box: vanilla pickup");
    helper.succeed();
  }

  @GameTest
  public void bothOptionsOffIsVanilla(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    assertVanillaFullInventory(helper, player);
    assertVanillaRoomInInventory(
        helper, ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS));
    helper.succeed();
  }

  @GameTest
  public void playerWithoutTheModIsVanilla(GameTestHelper helper) {
    assertVanillaFullInventory(helper, ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL));
    assertVanillaRoomInInventory(
        helper, ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL));
    helper.succeed();
  }

  @GameTest
  public void policyOffIsVanilla(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, BOTH);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.STONE));
    fillInventory(player);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);
    ItemStack picked = new ItemStack(Items.STONE, PICKED);

    int storedFirst = PickupIntoBoxes.storeFirst(player, picked, PICKUP_FORBIDDEN, BOTH);
    boolean taken = PickupIntoBoxes.storeOverflow(player, picked, PICKUP_FORBIDDEN, BOTH);

    helper.assertTrue(storedFirst == 0 && !taken, "a forbidding server stores nothing");
    helper.assertTrue(picked.getCount() == PICKED, "the picked stack must not change");
    helper.assertTrue(ShulkerFixtures.countInBox(box, Items.STONE) == 1, "the box must not change");
    helper.succeed();
  }

  @GameTest
  public void aShulkerBoxIsNeverStoredInABox(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, BOTH);
    ItemStack box = ShulkerFixtures.box();
    fillInventory(player);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    ItemEntity dropped = pickUp(helper, player, ShulkerFixtures.box());

    helper.assertTrue(!dropped.isRemoved(), "the shulker box must stay on the ground");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.SHULKER_BOX) == 0, "nothing may go in the box");
    helper.succeed();
  }

  /** Full inventory and a box with stone: the stone stays on the ground. */
  private static void assertVanillaFullInventory(GameTestHelper helper, ServerPlayer player) {
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.STONE));
    fillInventory(player);
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    ItemEntity dropped = pickUp(helper, player, new ItemStack(Items.STONE, PICKED));

    helper.assertTrue(
        !dropped.isRemoved() && dropped.getItem().getCount() == PICKED,
        "vanilla: a full inventory takes nothing");
    helper.assertTrue(ShulkerFixtures.countInBox(box, Items.STONE) == 1, "the box must not change");
  }

  /** Room in the inventory and a box with dirt: the dirt lands loose. */
  private static void assertVanillaRoomInInventory(GameTestHelper helper, ServerPlayer player) {
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    player.getInventory().setItem(SECOND_BOX_SLOT, box);

    pickUp(helper, player, new ItemStack(Items.DIRT, PICKED));

    helper.assertTrue(
        player.getInventory().countItem(Items.DIRT) == PICKED, "vanilla: the dirt lands loose");
    helper.assertTrue(ShulkerFixtures.countInBox(box, Items.DIRT) == 1, "the box must not change");
  }

  /** An item entity of {@code stack} that may be picked up at once, touched by {@code player}. */
  private static ItemEntity pickUp(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
    Vec3 position = helper.absoluteVec(DROP_POSITION);
    ItemEntity entity =
        new ItemEntity(helper.getLevel(), position.x, position.y, position.z, stack);
    entity.setNoPickUpDelay();
    entity.playerTouch(player);
    return entity;
  }

  /** Every hotbar and main inventory slot holds a full stack of cobblestone. */
  private static void fillInventory(ServerPlayer player) {
    Inventory inventory = player.getInventory();
    for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
      inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, MAX_STACK));
    }
  }

  /** A box with every slot full of {@code item}. */
  private static ItemStack fullBoxOf(Item item) {
    List<ItemStack> stacks = new ArrayList<>();
    for (int slot = 0; slot < StorageKind.SHULKER_SLOTS; slot++) {
      stacks.add(new ItemStack(item, MAX_STACK));
    }
    return ShulkerFixtures.box(stacks.toArray(ItemStack[]::new));
  }

  /** A box whose only room is {@code room} more of {@code item} in its first slot. */
  private static ItemStack boxWithRoomFor(Item item, int room) {
    List<ItemStack> stacks = new ArrayList<>();
    stacks.add(new ItemStack(item, MAX_STACK - room));
    for (int slot = 1; slot < StorageKind.SHULKER_SLOTS; slot++) {
      stacks.add(new ItemStack(Items.COBBLESTONE, MAX_STACK));
    }
    return ShulkerFixtures.box(stacks.toArray(ItemStack[]::new));
  }
}
