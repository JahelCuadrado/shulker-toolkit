package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.pick.PickupIntoBoxes;
import com.einsteinmods.shulkertoolkit.pick.ShulkerPick;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * The ender chest behaves as cloud storage: its contents are not stored in the item, and on the
 * server a slot of the ender menu is one of those contents, so a click must never write the ender
 * inventory from a stale copy. These clicks work in every slot, the ender menu included. Clicks go
 * through {@link StorageClicks#decide} with the gate open and then through the real vanilla menu
 * when the rules leave them to it, as the mixin does. Fake players have a session but no mod
 * channels, so {@code clicked} is pure vanilla.
 */
public final class EnderChestSafetyGameTest {
  private static final int CONTAINER_ID = 1;

  /** Three-row chest menus: 27 chest slots, then main inventory (9-35), then hotbar (0-8). */
  private static final int CHEST_SLOTS = 27;

  private static final int MAIN_MENU_SLOT = CHEST_SLOTS;
  private static final int FURNACE_SLOTS = 3;
  private static final int FURNACE_RESULT_SLOT = 2;
  private static final int FURNACE_DATA = 4;
  private static final int MAIN_INVENTORY_SLOT = 9;
  private static final int ENDER_ITEM_SLOT = 3;
  private static final int DIAMOND_SLOT = 5;
  private static final int NEXT_SLOT = 6;
  private static final int DIAMONDS = 5;
  private static final int STONE_COUNT = 20;
  private static final int DIRT_COUNT = 13;
  private static final int FULL_STACK = 64;
  private static final int STACKED_CHESTS = 3;
  private static final int STACK_OF_FOUR = 4;
  private static final int SINGLE_ITEM_SCHEME = 2;
  private static final int STEPS = 6000;
  private static final long SEED = 20261102L;
  private static final int OPERATIONS = 4;
  private static final int NONE = -1;

  /** Every item the random sequences move. */
  private static final List<Item> COUNTED =
      List.of(
          Items.ENDER_CHEST,
          Items.DIRT,
          Items.STONE,
          Items.DIAMOND,
          Items.SHULKER_BOX,
          Items.BUNDLE);

  private static final List<ControlScheme> SCHEMES =
      List.of(
          ControlScheme.DEFAULT,
          new ControlScheme(false, ClickScheme.SPLIT_INPUT),
          new ControlScheme(true, ClickScheme.SINGLE_INPUT));

  private static final PlayerPreferences INTERACTIONS_ON =
      new PlayerPreferences(
          PickMode.DEFAULT, true, true, true, false, false, true, CraftDepth.DEFAULT);

  /** The only item, so also the selected one: it comes out of itself, a plain pickup. */
  @GameTest
  public void rightClickOnTheEnderChestInTheEnderInventoryIsAPickup(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    int owned = ShulkerFixtures.countOwned(player, Items.ENDER_CHEST);
    helper.assertTrue(owned == 1, "one ender chest must stay one, there are " + owned);
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "it goes to the cursor");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT).isEmpty(), "and leaves the slot");
    finish(helper, player);
  }

  @GameTest
  public void rightClickWithTheSelectionOnItselfIsAPickup(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    StorageStates.of(player).setSelectedItem(ENDER_ITEM_SLOT);

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    int owned = ShulkerFixtures.countOwned(player, Items.ENDER_CHEST);
    helper.assertTrue(owned == 1, "one ender chest must stay one, there are " + owned);
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "it goes to the cursor");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT).isEmpty(), "the slot empties");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS,
        "the diamonds stay");
    finish(helper, player);
  }

  /** Its menu already shows the contents, so no click reaches them through the item. */
  @GameTest
  public void rightClickInTheEnderMenuNeverExtracts(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    StorageStates.of(player).setSelectedItem(DIAMOND_SLOT);

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "the ender chest is picked up");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT).isEmpty(), "its slot empties");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS,
        "the diamonds stay");
    finish(helper, player);
  }

  /** A stack on the cursor merges onto the single clicked chest: the slot must keep the sum. */
  @GameTest
  public void cursorStackInsertedIntoAStoredEnderChestKeepsItsCount(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR));

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    int owned = ShulkerFixtures.countOwned(player, Items.ENDER_CHEST);
    helper.assertTrue(owned == STACK_OF_FOUR + 1, "5 ender chests must stay 5, there are " + owned);
    finish(helper, player);
  }

  @GameTest
  public void cursorEnderChestIsPlacedInTheEnderMenu(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT).is(Items.ENDER_CHEST),
        "the ender chest lands in the clicked slot");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS,
        "the diamonds stay");
    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the cursor empties");
    finish(helper, player);
  }

  /** Vanilla swaps them: nothing goes in through the item. */
  @GameTest
  public void cursorEnderChestSwapsWithAnEnderSlot(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, FULL_STACK));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, DIAMOND_SLOT, ClickAction.SECONDARY);

    int diamonds = ShulkerFixtures.countOwned(player, Items.DIAMOND);
    helper.assertTrue(diamonds == FULL_STACK, "64 diamonds must stay 64, there are " + diamonds);
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).is(Items.ENDER_CHEST), "they swap");
    finish(helper, player);
  }

  /** Only part of the stack fits, so its slot keeps the rest: a stale copy would restore it all. */
  @GameTest
  public void partlyInsertingFromAnEnderSlotNeverCopiesIt(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    SimpleContainer ender = player.getEnderChestInventory();
    for (int slot = 0; slot < ender.getContainerSize(); slot++) {
      ender.setItem(slot, new ItemStack(Items.DIRT, FULL_STACK));
    }
    ender.setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, FULL_STACK - DIAMONDS));
    ender.setItem(NEXT_SLOT, new ItemStack(Items.DIAMOND, FULL_STACK - DIAMONDS));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, DIAMOND_SLOT, ClickAction.SECONDARY);

    int diamonds = ShulkerFixtures.countOwned(player, Items.DIAMOND);
    int expected = 2 * (FULL_STACK - DIAMONDS);
    helper.assertTrue(diamonds == expected, expected + " diamonds became " + diamonds);
    finish(helper, player);
  }

  /** One item at a time, with the clicked slot itself as the one the item comes from. */
  @GameTest
  public void singleItemMovementOnAnEnderSlotConservesIt(GameTestHelper helper) {
    for (int count : new int[] {1, DIAMONDS}) {
      for (ClickAction action : ClickAction.values()) {
        ServerPlayer player = playerWithEnderMenuOpen(helper);
        StorageStates.of(player).setControlScheme(SCHEMES.get(SINGLE_ITEM_SCHEME));
        player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, count));
        player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

        click(player, DIAMOND_SLOT, action);

        int diamonds = ShulkerFixtures.countOwned(player, Items.DIAMOND);
        helper.assertTrue(diamonds == count, action + " turned " + count + " into " + diamonds);
        ServerPlayerSessions.remove(player.getUUID());
        StorageStates.forget(player.getUUID());
      }
    }
    helper.succeed();
  }

  /** A slot that refuses what comes out sends it back into the chest, not over a stale copy. */
  @GameTest
  public void extractionARefusingSlotSendsBackIsNotLost(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    player.containerMenu =
        new FurnaceMenu(
            CONTAINER_ID,
            player.getInventory(),
            new SimpleContainer(FURNACE_SLOTS),
            new SimpleContainerData(FURNACE_DATA));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, FURNACE_RESULT_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getEnderChestInventory().countItem(Items.DIAMOND) == DIAMONDS,
        "the diamonds stay in the chest");
    helper.assertTrue(
        player.containerMenu.getSlot(FURNACE_RESULT_SLOT).getItem().isEmpty(), "nothing goes in");
    finish(helper, player);
  }

  /** The real ender inventory takes shulker boxes, so the item does too, contents intact. */
  @GameTest
  public void shulkerBoxGoesInAndOutWithItsContents(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    open(player, new SimpleContainer(CHEST_SLOTS));
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT));
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, box);
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getInventory().getItem(MAIN_INVENTORY_SLOT).isEmpty(), "the box leaves the slot");
    helper.assertTrue(
        player.getEnderChestInventory().countItem(Items.SHULKER_BOX) == 1, "and is in the chest");

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    ItemStack out = player.getInventory().getItem(MAIN_INVENTORY_SLOT);
    helper.assertTrue(out.is(Items.SHULKER_BOX), "the box comes back, was " + out);
    helper.assertTrue(
        ShulkerFixtures.countInBox(out, Items.DIRT) == DIRT_COUNT, "with its contents");
    finish(helper, player);
  }

  /** Every path asks these: drag, wheel, the "+" indicator. Boxes and bundles keep refusing. */
  @GameTest
  public void onlyTheEnderChestAcceptsShulkerBoxes(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
    ItemStack box = new ItemStack(Items.SHULKER_BOX);

    helper.assertTrue(StorageKind.ENDER_CHEST.canAccept(enderChest, box), "the ender chest does");
    helper.assertTrue(
        StorageKind.ENDER_CHEST.canAddItem(enderChest, box, player), "and has room for it");
    helper.assertFalse(
        StorageKind.SHULKER_BOX.canAccept(new ItemStack(Items.SHULKER_BOX), box), "a box does not");
    helper.assertFalse(
        StorageKind.BUNDLE.canAccept(new ItemStack(Items.BUNDLE), box), "nor does a bundle");
    finish(helper, player);
  }

  /** Windows edit a working copy: a shulker box goes in by click and by Shift-click. */
  @GameTest
  public void shulkerBoxesGoIntoAnEnderWindow(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getInventory().setItem(MAIN_INVENTORY_SLOT + 1, new ItemStack(Items.SHULKER_BOX));
    player.containerMenu.setCarried(new ItemStack(Items.SHULKER_BOX));
    List<PinnedWindowClickPayload.Level> window = List.of(level(MAIN_MENU_SLOT, Items.ENDER_CHEST));

    boolean clicked =
        windowClick(player, window, PinnedAction.PICKUP, 0, PinnedAction.PRIMARY_BUTTON);
    boolean shifted =
        windowClick(player, window, PinnedAction.QUICK_MOVE_IN, MAIN_MENU_SLOT + 1, 0);

    helper.assertTrue(clicked && shifted, "the clicks pass");
    helper.assertTrue(
        player.getEnderChestInventory().countItem(Items.SHULKER_BOX) == 2, "both boxes went in");
    finish(helper, player);
  }

  /** A chest's slots work like any other, for the carried and the clicked ender chest. */
  @GameTest
  public void chestSlotsEditTheEnderChest(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    SimpleContainer chest = new SimpleContainer(CHEST_SLOTS);
    open(player, chest);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    chest.setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    chest.setItem(NEXT_SLOT, new ItemStack(Items.STONE, STONE_COUNT));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, NEXT_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(chest.getItem(NEXT_SLOT).isEmpty(), "the stone leaves the chest");
    helper.assertTrue(
        player.getEnderChestInventory().countItem(Items.STONE) == STONE_COUNT,
        "into the ender one");
    player.containerMenu.setCarried(ItemStack.EMPTY);
    StorageStates.of(player).setSelectedItem(DIAMOND_SLOT);

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == DIAMONDS, "the diamonds are on the cursor");
    helper.assertTrue(chest.getItem(ENDER_ITEM_SLOT).is(Items.ENDER_CHEST), "the item stays");
    finish(helper, player);
  }

  @GameTest
  public void inventorySlotsStayVanillaWithTheEnderMenuOpen(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, STONE_COUNT));

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(player.getEnderChestInventory().isEmpty(), "nothing goes in");
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "vanilla swaps them");
    finish(helper, player);
  }

  /** The held item decides: locked by its menu, it stays vanilla instead of going into the box. */
  @GameTest
  public void heldEnderChestNeverGoesIntoTheClickedBox(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player
        .getInventory()
        .setItem(MAIN_INVENTORY_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST));

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getInventory().getItem(MAIN_INVENTORY_SLOT).is(Items.ENDER_CHEST), "vanilla swaps");
    ItemStack box = player.containerMenu.getCarried();
    helper.assertTrue(
        box.is(Items.SHULKER_BOX) && ShulkerFixtures.countInBox(box, Items.ENDER_CHEST) == 0,
        "the ender chest did not go into the box");
    finish(helper, player);
  }

  /** And a held box takes the clicked ender chest in. */
  @GameTest
  public void heldBoxTakesTheClickedEnderChest(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    SimpleContainer chest = new SimpleContainer(CHEST_SLOTS);
    open(player, chest);
    chest.setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.containerMenu.setCarried(new ItemStack(Items.SHULKER_BOX));

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(chest.getItem(ENDER_ITEM_SLOT).isEmpty(), "the ender chest leaves the slot");
    helper.assertTrue(
        ShulkerFixtures.countInBox(player.containerMenu.getCarried(), Items.ENDER_CHEST) == 1,
        "into the held box");
    finish(helper, player);
  }

  /** Only the ender chest is locked by its menu: a box stored in it keeps its clicks. */
  @GameTest
  public void boxesKeepTheirClicksInTheEnderMenu(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player
        .getEnderChestInventory()
        .setItem(ENDER_ITEM_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    player.containerMenu.setCarried(new ItemStack(Items.DIRT, DIRT_COUNT));

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the dirt goes in");
    helper.assertTrue(
        ShulkerFixtures.countInBox(
                player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT), Items.DIRT)
            == 2 * DIRT_COUNT,
        "all of it");
    finish(helper, player);
  }

  /** The same checks a mouse drag makes before it clicks a slot, then the clicks it sends. */
  @GameTest
  public void dragsWorkOverTheEnderMenu(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
    player.containerMenu.setCarried(enderChest);
    Slot filledEnderSlot = player.containerMenu.getSlot(DIAMOND_SLOT);

    helper.assertTrue(
        StorageClicks.insertsFrom(StorageKind.ENDER_CHEST, enderChest, filledEnderSlot, player),
        "a filled ender slot is a drag insertion target");
    for (int slot = 0; slot < CHEST_SLOTS; slot++) {
      if (StorageClicks.extractsTo(
              StorageKind.ENDER_CHEST, enderChest, player.containerMenu.getSlot(slot), player)
          || StorageClicks.insertsFrom(
              StorageKind.ENDER_CHEST, enderChest, player.containerMenu.getSlot(slot), player)) {
        click(player, slot, ClickAction.SECONDARY);
      }
    }

    int diamonds = ShulkerFixtures.countOwned(player, Items.DIAMOND);
    helper.assertTrue(diamonds == DIAMONDS, "5 diamonds must stay 5, there are " + diamonds);
    finish(helper, player);
  }

  /** An ender window on the ender chest stored in the ender inventory can take itself out. */
  @GameTest
  public void windowOnAnEnderChestStoredInTheEnderInventory(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));

    boolean tookDiamonds =
        windowClick(
            player,
            List.of(level(ENDER_ITEM_SLOT, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            DIAMOND_SLOT,
            PinnedAction.PRIMARY_BUTTON);
    boolean putDiamondsBack =
        windowClick(
            player,
            List.of(level(ENDER_ITEM_SLOT, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            NEXT_SLOT,
            PinnedAction.PRIMARY_BUTTON);
    boolean tookItself =
        windowClick(
            player,
            List.of(level(ENDER_ITEM_SLOT, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            ENDER_ITEM_SLOT,
            PinnedAction.PRIMARY_BUTTON);
    boolean windowGone =
        !windowClick(
            player,
            List.of(level(ENDER_ITEM_SLOT, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            NEXT_SLOT,
            PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(tookDiamonds && putDiamondsBack && tookItself, "the clicks pass");
    helper.assertTrue(windowGone, "with the root gone the path no longer resolves");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.ENDER_CHEST) == 1, "still one ender chest");
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "it is on the cursor");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(NEXT_SLOT).getCount() == DIAMONDS,
        "the diamonds stayed in the chest");
    finish(helper, player);
  }

  /** The same contents open in a window and in the menu: each click reads the live chest. */
  @GameTest
  public void sameContentsInAWindowAndInTheMenu(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    List<PinnedWindowClickPayload.Level> window = List.of(level(MAIN_MENU_SLOT, Items.ENDER_CHEST));

    click(player, DIAMOND_SLOT, ClickAction.PRIMARY);
    boolean placed =
        windowClick(player, window, PinnedAction.PICKUP, NEXT_SLOT, PinnedAction.PRIMARY_BUTTON);
    click(player, NEXT_SLOT, ClickAction.PRIMARY);
    boolean swappedNothing =
        windowClick(player, window, PinnedAction.PICKUP, NEXT_SLOT, PinnedAction.SECONDARY_BUTTON);

    helper.assertTrue(placed && swappedNothing, "the clicks pass");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.DIAMOND) == DIAMONDS, "still 5 diamonds");
    finish(helper, player);
  }

  /** Ender chest, an ender chest stored in it, a box in that one: the climb stops at the chest. */
  @GameTest
  public void nestedChainThroughTwoEnderChests(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player
        .getEnderChestInventory()
        .setItem(DIAMOND_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    boolean clicked =
        windowClick(
            player,
            List.of(
                level(MAIN_MENU_SLOT, Items.ENDER_CHEST),
                level(ENDER_ITEM_SLOT, Items.ENDER_CHEST),
                level(DIAMOND_SLOT, Items.SHULKER_BOX)),
            PinnedAction.PICKUP,
            0,
            PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(clicked, "the click passes");
    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == DIRT_COUNT, "the dirt is on the cursor");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.DIRT) == DIRT_COUNT, "and nowhere else");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_ITEM_SLOT).is(Items.ENDER_CHEST),
        "the ender chest in the chain is untouched");
    finish(helper, player);
  }

  /** Option off (the default): the ender chest item is a plain block on every click. */
  @GameTest
  public void enderChestsStayVanillaWithTheOptionOff(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    StorageStates.of(player).setControlScheme(ControlScheme.DEFAULT);
    open(player, new SimpleContainer(CHEST_SLOTS));
    player.getEnderChestInventory().clearContent();
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, STONE_COUNT));

    helper.assertFalse(
        TransferGate.allowsKind(player, StorageKind.ENDER_CHEST), "the gate stays closed");
    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(player.getEnderChestInventory().isEmpty(), "nothing goes in");
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.ENDER_CHEST), "vanilla swaps the cursor");
    helper.assertTrue(
        player.getInventory().getItem(MAIN_INVENTORY_SLOT).getCount() == STONE_COUNT,
        "with the slot");
    finish(helper, player);
  }

  /** Option on: the same click goes through the box controls. */
  @GameTest
  public void enderChestsUseTheBoxControlsWithTheOptionOn(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    open(player, new SimpleContainer(CHEST_SLOTS));
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, STONE_COUNT));

    helper.assertTrue(TransferGate.allowsKind(player, StorageKind.ENDER_CHEST), "the gate opens");
    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getEnderChestInventory().countItem(Items.STONE) == STONE_COUNT, "the stone goes in");
    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "and leaves the cursor");
    finish(helper, player);
  }

  /** Windows are opened on purpose with middle click: they work whatever the option. */
  @GameTest
  public void windowsWorkWithTheOptionOff(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    open(player, new SimpleContainer(CHEST_SLOTS));
    player.getEnderChestInventory().clearContent();
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));

    boolean clicked =
        windowClick(
            player,
            List.of(level(MAIN_MENU_SLOT, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            DIAMOND_SLOT,
            PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(clicked, "the click passes");
    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == DIAMONDS, "the diamonds are on the cursor");
    finish(helper, player);
  }

  /** Once the wheel chose something inside, a right click takes it out instead of splitting. */
  @GameTest
  public void rightClickOnAStackWithASelectionTakesItOut(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    SimpleContainer chest = new SimpleContainer(CHEST_SLOTS);
    open(player, chest);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    chest.setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR));
    StorageStates.of(player).setSelectedItem(DIAMOND_SLOT);

    click(player, ENDER_ITEM_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.DIAMOND)
            && player.containerMenu.getCarried().getCount() == DIAMONDS,
        "the diamonds come out");
    helper.assertTrue(
        chest.getItem(ENDER_ITEM_SLOT).getCount() == STACK_OF_FOUR, "the stack stays whole");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).isEmpty(), "the chest gave them");
    finish(helper, player);
  }

  /**
   * Without a selection a stack keeps vanilla's right click: half of it goes to the cursor, nothing
   * is edited.
   */
  @GameTest
  public void rightClickOnAStackOfEnderChestsSplitsIt(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    ItemStack stack = new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, stack);

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(player.containerMenu.getCarried().getCount() == 2, "half goes to the cursor");
    helper.assertTrue(stack.getCount() == 2, "half stays");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS,
        "the ender inventory is not edited");
    finish(helper, player);
  }

  @GameTest
  public void cursorStackOfEnderChestsStaysVanilla(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR));

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);

    helper.assertTrue(
        player.getInventory().getItem(MAIN_INVENTORY_SLOT).getCount() == 1, "vanilla places one");
    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == STACK_OF_FOUR - 1, "the rest stays");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS,
        "the ender inventory is not edited");
    finish(helper, player);
  }

  /** Contents live in the player, so every item of a stack shows the same ones. */
  @GameTest
  public void aStackOfEnderChestsShowsItsContents(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    ItemStack stack = new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR);

    helper.assertTrue(StorageKind.ENDER_CHEST.viewable(stack), "a stack is viewable");
    helper.assertFalse(StorageKind.ENDER_CHEST.allowModification(stack), "but not clickable");
    helper.assertFalse(StorageKind.ENDER_CHEST.hasContents(stack, player), "empty chest");
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND));
    helper.assertTrue(StorageKind.ENDER_CHEST.hasContents(stack, player), "it holds a diamond");
    finish(helper, player);
  }

  /** A window on a stack edits the player's ender chest, never the stack. */
  @GameTest
  public void windowOnAStackNeverDuplicates(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    ItemStack stack = new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, stack);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    List<PinnedWindowClickPayload.Level> window = List.of(level(MAIN_MENU_SLOT, Items.ENDER_CHEST));

    boolean took =
        windowClick(player, window, PinnedAction.PICKUP, DIAMOND_SLOT, PinnedAction.PRIMARY_BUTTON);
    boolean placed =
        windowClick(player, window, PinnedAction.PICKUP, NEXT_SLOT, PinnedAction.SECONDARY_BUTTON);

    helper.assertTrue(took && placed, "the clicks pass");
    helper.assertTrue(stack.getCount() == STACK_OF_FOUR, "the stack does not change");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.DIAMOND) == DIAMONDS, "still 5 diamonds");
    finish(helper, player);
  }

  /** Taking a part leaves the window on the rest; taking all of it closes the path. */
  @GameTest
  public void takingTheStackAwayEndsItsWindow(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    ItemStack stack = new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR);
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, stack);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    List<PinnedWindowClickPayload.Level> window = List.of(level(MAIN_MENU_SLOT, Items.ENDER_CHEST));

    click(player, MAIN_MENU_SLOT, ClickAction.SECONDARY);
    boolean partStaysOpen =
        windowClick(player, window, PinnedAction.QUICK_MOVE_OUT, DIAMOND_SLOT, 0);
    click(player, MAIN_MENU_SLOT, ClickAction.PRIMARY);
    click(player, MAIN_MENU_SLOT, ClickAction.PRIMARY);
    boolean gone =
        !windowClick(player, window, PinnedAction.PICKUP, 0, PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(partStaysOpen, "the rest of the stack keeps its window");
    helper.assertTrue(gone, "without the stack the click is refused");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.ENDER_CHEST) == STACK_OF_FOUR,
        "the stack only moved");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.DIAMOND) == DIAMONDS, "still 5 diamonds");
    finish(helper, player);
  }

  /** A stack inside a box: the climb stops at the ender chest, so the box is not rewritten. */
  @GameTest
  public void windowOnAStackInsideABox(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR));
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, box);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    ItemContainerContents before = box.get(DataComponents.CONTAINER);

    boolean clicked =
        windowClick(
            player,
            List.of(level(MAIN_MENU_SLOT, Items.SHULKER_BOX), level(0, Items.ENDER_CHEST)),
            PinnedAction.PICKUP,
            DIAMOND_SLOT,
            PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(clicked, "the click passes");
    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == DIAMONDS, "the diamonds are on the cursor");
    helper.assertTrue(box.get(DataComponents.CONTAINER) == before, "the box is not rewritten");
    finish(helper, player);
  }

  /** Pick, refill and pickup read the inventory's boxes only: the ender chest never moves. */
  @GameTest
  public void pickRefillAndPickupLeaveTheEnderChestAlone(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    open(player, new SimpleContainer(CHEST_SLOTS));
    PlayerPreferences preferences = new PlayerPreferences(PickMode.DEFAULT, true, true, true, true);
    player.getEnderChestInventory().setItem(DIAMOND_SLOT, new ItemStack(Items.DIRT, DIRT_COUNT));
    player.getInventory().setItem(MAIN_INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
    player
        .getInventory()
        .setItem(
            MAIN_INVENTORY_SLOT + 1,
            ShulkerFixtures.box(
                new ItemStack(Items.DIRT, DIRT_COUNT),
                new ItemStack(Items.STONE, STONE_COUNT),
                new ItemStack(Items.ENDER_CHEST)));
    int dirtBefore = ShulkerFixtures.countOwned(player, Items.DIRT);

    ShulkerPick.TakeOutResult pick =
        ShulkerPick.pickIfAllowed(
            player, new ItemStack(Items.ENDER_CHEST), ServerPolicy.UNRESTRICTED, preferences);
    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    ItemStack used = new ItemStack(Items.STONE);
    used.shrink(1);
    boolean refilled =
        ShulkerPick.refillIfAllowed(
            player, InteractionHand.OFF_HAND, used, ServerPolicy.UNRESTRICTED, preferences);
    ItemStack picked = new ItemStack(Items.DIRT, DIRT_COUNT);
    int stored = PickupIntoBoxes.storeFirst(player, picked, ServerPolicy.UNRESTRICTED, preferences);

    helper.assertTrue(pick.succeeded() && refilled, "pick and refill work");
    helper.assertTrue(stored == 0 || picked.isEmpty(), "pickup stores in the box or leaves it");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.ENDER_CHEST) == 2, "both ender chests stay");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.STONE) == STONE_COUNT, "the stone stays");
    helper.assertTrue(
        ShulkerFixtures.countOwned(player, Items.DIRT) + picked.getCount()
            == dirtBefore + DIRT_COUNT,
        "the dirt is the same plus what was picked up");
    helper.assertTrue(
        player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIRT_COUNT,
        "the ender chest did not change");
    finish(helper, player);
  }

  @GameTest
  public void randomClicksInTheEnderMenuConserveEveryItem(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    stock(player);
    randomClicks(helper, player, null);
    finish(helper, player);
  }

  @GameTest
  public void randomClicksInAChestConserveEveryItem(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    SimpleContainer chest = new SimpleContainer(CHEST_SLOTS);
    open(player, chest);
    stock(player);
    chest.setItem(0, new ItemStack(Items.ENDER_CHEST));
    chest.setItem(1, ShulkerFixtures.box(new ItemStack(Items.STONE, STONE_COUNT)));
    randomClicks(helper, player, chest);
    finish(helper, player);
  }

  /** Nearly full: a click that takes or extracts has few places to go. */
  @GameTest
  public void randomClicksInAFullEnderMenuConserveEveryItem(GameTestHelper helper) {
    ServerPlayer player = playerWithEnderMenuOpen(helper);
    stock(player);
    SimpleContainer ender = player.getEnderChestInventory();
    for (int slot = 0; slot < ender.getContainerSize() - 1; slot++) {
      if (ender.getItem(slot).isEmpty()) {
        ender.setItem(slot, new ItemStack(Items.STONE, slot + 1));
      }
    }
    randomClicks(helper, player, null);
    finish(helper, player);
  }

  /** A furnace's result slot refuses items, so what leaves it must fit where it goes. */
  @GameTest
  public void randomClicksInAFurnaceConserveEveryItem(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    SimpleContainer furnace = new SimpleContainer(FURNACE_SLOTS);
    player.containerMenu =
        new FurnaceMenu(
            CONTAINER_ID, player.getInventory(), furnace, new SimpleContainerData(FURNACE_DATA));
    stock(player);
    furnace.setItem(FURNACE_RESULT_SLOT, new ItemStack(Items.STONE, FULL_STACK));
    randomClicks(helper, player, furnace);
    finish(helper, player);
  }

  /** A seeded mix of menu clicks, window clicks at any depth and selection changes. */
  static void randomClicks(GameTestHelper helper, ServerPlayer player, @Nullable Container chest) {
    Random random = new Random(SEED);
    int[] expected = counts(player, chest);
    for (int step = 0; step < STEPS; step++) {
      String operation = randomOperation(random, player);
      int[] now = counts(player, chest);
      for (int index = 0; index < now.length; index++) {
        if (now[index] != expected[index]) {
          helper.assertTrue(
              false,
              COUNTED.get(index)
                  + " went from "
                  + expected[index]
                  + " to "
                  + now[index]
                  + " at step "
                  + step
                  + ": "
                  + operation);
        }
      }
    }
  }

  private static String randomOperation(Random random, ServerPlayer player) {
    AbstractContainerMenu menu = player.containerMenu;
    int operation = random.nextInt(OPERATIONS);
    if (operation == 0) {
      int selected = random.nextInt(CHEST_SLOTS) - 1;
      StorageStates.of(player).setSelectedItem(selected);
      return "select " + selected;
    }
    if (operation == 1) {
      List<PinnedWindowClickPayload.Level> path = randomPath(random, player);
      if (path != null) {
        return randomWindowClick(random, player, path);
      }
    }
    ControlScheme scheme = SCHEMES.get(random.nextInt(SCHEMES.size()));
    StorageStates.of(player).setControlScheme(scheme);
    int slot = random.nextInt(menu.slots.size());
    ClickAction action = random.nextBoolean() ? ClickAction.PRIMARY : ClickAction.SECONDARY;
    String description =
        action + " " + scheme + " on " + menu.getSlot(slot).getItem() + " in slot " + slot;
    click(player, slot, action);
    return description + " with cursor " + menu.getCarried();
  }

  private static String randomWindowClick(
      Random random, ServerPlayer player, List<PinnedWindowClickPayload.Level> path) {
    PinnedAction action = PinnedAction.values()[random.nextInt(PinnedAction.values().length)];
    int slot = random.nextInt(CHEST_SLOTS);
    int button = PinnedAction.PRIMARY_BUTTON;
    if (action == PinnedAction.PICKUP) {
      button = random.nextBoolean() ? PinnedAction.PRIMARY_BUTTON : PinnedAction.SECONDARY_BUTTON;
    } else if (action == PinnedAction.QUICK_MOVE_IN) {
      slot = random.nextInt(player.containerMenu.slots.size());
    } else if (action == PinnedAction.SWAP) {
      button = random.nextInt(PinnedAction.HOTBAR_SLOTS);
    }
    boolean applied = windowClick(player, path, action, slot, button);
    return action + " window " + path + " slot " + slot + " button " + button + " " + applied;
  }

  /** A path to a random container item, from a random menu slot, or null when there is none. */
  private static @Nullable List<PinnedWindowClickPayload.Level> randomPath(
      Random random, ServerPlayer player) {
    int root = random.nextInt(player.containerMenu.slots.size());
    ItemStack stack = player.containerMenu.getSlot(root).getItem();
    StorageKind kind = StorageKind.of(stack);
    if (kind == null) {
      return null;
    }
    List<PinnedWindowClickPayload.Level> path = new ArrayList<>();
    path.add(level(root, stack.getItem()));
    while (path.size() < PinnedPaths.MAX_DEPTH && random.nextBoolean()) {
      List<ItemStack> items = kind.items(stack, player);
      if (items.isEmpty()) {
        break;
      }
      int index = random.nextInt(items.size());
      StorageKind inside = StorageKind.of(items.get(index));
      if (inside == null) {
        break;
      }
      stack = items.get(index);
      kind = inside;
      path.add(level(index, stack.getItem()));
    }
    return path;
  }

  private static int[] counts(ServerPlayer player, @Nullable Container chest) {
    int[] counts = new int[COUNTED.size()];
    for (int index = 0; index < counts.length; index++) {
      Item item = COUNTED.get(index);
      counts[index] =
          ShulkerFixtures.countOwned(player, item)
              + (chest == null ? 0 : ShulkerFixtures.countDeep(chest, item));
    }
    return counts;
  }

  private static void stock(ServerPlayer player) {
    Inventory inventory = player.getInventory();
    inventory.setItem(0, new ItemStack(Items.ENDER_CHEST));
    inventory.setItem(1, new ItemStack(Items.ENDER_CHEST, STACKED_CHESTS));
    inventory.setItem(2, new ItemStack(Items.DIRT, FULL_STACK));
    inventory.setItem(
        MAIN_INVENTORY_SLOT,
        ShulkerFixtures.box(
            new ItemStack(Items.DIRT, DIRT_COUNT),
            new ItemStack(Items.STONE, STONE_COUNT),
            new ItemStack(Items.ENDER_CHEST)));
    inventory.setItem(MAIN_INVENTORY_SLOT + 1, new ItemStack(Items.SHULKER_BOX));
    inventory.setItem(MAIN_INVENTORY_SLOT + 2, bundleWith(new ItemStack(Items.DIRT, DIRT_COUNT)));
    SimpleContainer ender = player.getEnderChestInventory();
    ender.setItem(0, new ItemStack(Items.ENDER_CHEST));
    ender.setItem(1, ShulkerFixtures.box(new ItemStack(Items.STONE, FULL_STACK)));
    ender.setItem(2, new ItemStack(Items.ENDER_CHEST, STACKED_CHESTS));
    ender.setItem(ENDER_ITEM_SLOT, bundleWith(new ItemStack(Items.DIAMOND, DIAMONDS)));
    ender.setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
    ender.setItem(NEXT_SLOT, new ItemStack(Items.BUNDLE));
  }

  private static ItemStack bundleWith(ItemStack stack) {
    ItemStack bundle = new ItemStack(Items.BUNDLE);
    bundle.set(
        DataComponents.BUNDLE_CONTENTS,
        new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(stack))));
    return bundle;
  }

  private static ServerPlayer player(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.player(helper, GameType.SURVIVAL, INTERACTIONS_ON);
    player.getEnderChestInventory().clearContent();
    StorageStates.of(player).setControlScheme(ControlScheme.DEFAULT);
    StorageStates.of(player).setSelectedItem(StorageGrid.NO_ITEM);
    return player;
  }

  private static ServerPlayer playerWithEnderMenuOpen(GameTestHelper helper) {
    ServerPlayer player = player(helper);
    open(player, player.getEnderChestInventory());
    return player;
  }

  private static void open(ServerPlayer player, Container container) {
    player.containerMenu = ChestMenu.threeRows(CONTAINER_ID, player.getInventory(), container);
  }

  /** One menu click as the mixin runs it for a client with the mod. */
  private static void click(ServerPlayer player, int menuSlot, ClickAction action) {
    AbstractContainerMenu menu = player.containerMenu;
    Slot slot = menu.getSlot(menuSlot);
    ItemStack clicked = slot.getItem();
    ItemStack carried = menu.getCarried();
    TriState decision =
        StorageClicks.decide(
            menu,
            player,
            action,
            slot,
            clicked,
            carried,
            StorageKind.of(carried),
            StorageKind.of(clicked),
            true);
    if (decision != TriState.TRUE) {
      int button =
          action == ClickAction.PRIMARY
              ? PinnedAction.PRIMARY_BUTTON
              : PinnedAction.SECONDARY_BUTTON;
      menu.clicked(menuSlot, button, ContainerInput.PICKUP, player);
    }
  }

  private static boolean windowClick(
      ServerPlayer player,
      List<PinnedWindowClickPayload.Level> path,
      PinnedAction action,
      int slot,
      int button) {
    return PinnedWindowClicks.apply(
        player,
        new PinnedWindowClickPayload(CONTAINER_ID, path, action, slot, button),
        ServerPolicy.UNRESTRICTED);
  }

  private static PinnedWindowClickPayload.Level level(int index, Item item) {
    return new PinnedWindowClickPayload.Level(index, item);
  }

  private static void finish(GameTestHelper helper, ServerPlayer player) {
    ServerPlayerSessions.remove(player.getUUID());
    StorageStates.forget(player.getUUID());
    helper.succeed();
  }
}
