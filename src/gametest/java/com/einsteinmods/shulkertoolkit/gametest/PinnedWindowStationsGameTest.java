package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Pinned window clicks in the work station menus (stonecutter, loom, cartography table, smithing
 * table, grindstone, anvil), each as vanilla builds it and as a subclass (Handy Tables' portable
 * stations): items move between the window, the cursor and the inventory only, a station's result
 * slot can never be a window's slot, and the item count never changes.
 */
public final class PinnedWindowStationsGameTest {
  private static final int CONTAINER_ID = 1;
  private static final int BOX_INVENTORY_SLOT = 9;
  private static final int OTHER_INVENTORY_SLOT = 10;
  private static final int FIRST_SLOT = 0;
  private static final int SECOND_SLOT = 1;
  private static final int DIRT_COUNT = 13;
  private static final int STONE_COUNT = 20;
  private static final String NEW_NAME = "Renamed";

  /** {@code LoomMenu} has no constant for it: banner, dye, pattern, then the result. */
  private static final int LOOM_RESULT_SLOT = 3;

  /** One station menu: how to build it and which of its slots is the result. */
  private record Station(String name, MenuFactory factory, int resultSlot) {}

  @FunctionalInterface
  private interface MenuFactory {
    AbstractContainerMenu create(int containerId, Inventory inventory);
  }

  private static final List<Station> STATIONS =
      List.of(
          new Station("stonecutter", StonecutterMenu::new, StonecutterMenu.RESULT_SLOT),
          new Station("loom", LoomMenu::new, LOOM_RESULT_SLOT),
          new Station(
              "cartography table", CartographyTableMenu::new, CartographyTableMenu.RESULT_SLOT),
          new Station("smithing table", SmithingMenu::new, SmithingMenu.RESULT_SLOT),
          new Station("grindstone", GrindstoneMenu::new, GrindstoneMenu.RESULT_SLOT),
          new Station("anvil", AnvilMenu::new, AnvilMenu.RESULT_SLOT),
          new Station(
              "stonecutter subclass",
              (id, inventory) -> new StonecutterMenu(id, inventory) {},
              StonecutterMenu.RESULT_SLOT),
          new Station(
              "loom subclass", (id, inventory) -> new LoomMenu(id, inventory) {}, LOOM_RESULT_SLOT),
          new Station(
              "cartography table subclass",
              (id, inventory) -> new CartographyTableMenu(id, inventory) {},
              CartographyTableMenu.RESULT_SLOT),
          new Station(
              "smithing table subclass",
              (id, inventory) -> new SmithingMenu(id, inventory) {},
              SmithingMenu.RESULT_SLOT),
          new Station(
              "grindstone subclass",
              (id, inventory) -> new GrindstoneMenu(id, inventory) {},
              GrindstoneMenu.RESULT_SLOT),
          new Station(
              "anvil subclass",
              (id, inventory) -> new AnvilMenu(id, inventory) {},
              AnvilMenu.RESULT_SLOT));

  @GameTest
  public void loomResultSlotIndexIsTheOneVanillaUses(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    LoomMenu loom = new LoomMenu(CONTAINER_ID, player.getInventory());
    helper.assertTrue(
        loom.getResultSlot().index == LOOM_RESULT_SLOT, "the loom's result slot is slot 3");
    helper.succeed();
  }

  @GameTest
  public void takeAndPlaceBetweenWindowAndCursor(GameTestHelper helper) {
    for (Station station : STATIONS) {
      Player player = playerIn(helper, station);
      player
          .getInventory()
          .setItem(BOX_INVENTORY_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
      int boxSlot = menuSlotOf(player, BOX_INVENTORY_SLOT);

      helper.assertTrue(
          click(player, boxSlot, PinnedAction.PICKUP, FIRST_SLOT, PinnedAction.PRIMARY_BUTTON),
          station.name() + ": the take must pass");
      helper.assertTrue(
          player.containerMenu.getCarried().getCount() == DIRT_COUNT,
          station.name() + ": 13 dirt on the cursor");
      helper.assertTrue(
          click(player, boxSlot, PinnedAction.PICKUP, SECOND_SLOT, PinnedAction.PRIMARY_BUTTON),
          station.name() + ": the place must pass");

      helper.assertTrue(
          player.containerMenu.getCarried().isEmpty(), station.name() + ": the cursor is empty");
      helper.assertTrue(
          ShulkerFixtures.countInBox(box(player), Items.DIRT) == DIRT_COUNT,
          station.name() + ": the dirt is back in the box");
      expectStationSlotsEmpty(helper, station, player);
    }
    helper.succeed();
  }

  @GameTest
  public void shiftClicksMoveBetweenWindowAndInventoryOnly(GameTestHelper helper) {
    for (Station station : STATIONS) {
      Player player = playerIn(helper, station);
      player
          .getInventory()
          .setItem(BOX_INVENTORY_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
      player.getInventory().setItem(OTHER_INVENTORY_SLOT, new ItemStack(Items.STONE, STONE_COUNT));
      int boxSlot = menuSlotOf(player, BOX_INVENTORY_SLOT);

      helper.assertTrue(
          click(
              player,
              boxSlot,
              PinnedAction.QUICK_MOVE_OUT,
              FIRST_SLOT,
              PinnedAction.PRIMARY_BUTTON),
          station.name() + ": the move out must pass");
      helper.assertTrue(
          click(
              player,
              boxSlot,
              PinnedAction.QUICK_MOVE_IN,
              menuSlotOf(player, OTHER_INVENTORY_SLOT),
              PinnedAction.PRIMARY_BUTTON),
          station.name() + ": the move in must pass");

      helper.assertTrue(
          player.getInventory().countItem(Items.DIRT) == DIRT_COUNT,
          station.name() + ": the dirt went to the inventory");
      helper.assertTrue(
          ShulkerFixtures.countInBox(box(player), Items.STONE) == STONE_COUNT,
          station.name() + ": the stone went into the box");
      helper.assertTrue(
          player.getInventory().getItem(OTHER_INVENTORY_SLOT).isEmpty(),
          station.name() + ": the stone left its slot");
      expectStationSlotsEmpty(helper, station, player);
    }
    helper.succeed();
  }

  /**
   * A box in a result slot (as a renamed copy in the anvil's) is no window's slot: it refuses
   * items, so {@code Slot#allowModification} is false; nothing moves and the copy is unchanged.
   */
  @GameTest
  public void resultSlotsAreNeverAWindow(GameTestHelper helper) {
    for (Station station : STATIONS) {
      Player player = playerIn(helper, station);
      ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT));
      Slot result = player.containerMenu.getSlot(station.resultSlot());
      // Straight into the container: the menus' own result logic would clear a box at once.
      result.container.setItem(result.getContainerSlot(), box.copy());
      helper.assertTrue(
          ItemStack.matches(result.getItem(), box), station.name() + ": the box is in the result");
      helper.assertFalse(
          result.allowModification(player), station.name() + ": the result slot refuses items");

      for (PinnedAction action : PinnedAction.values()) {
        int slot =
            action == PinnedAction.QUICK_MOVE_IN
                ? menuSlotOf(player, OTHER_INVENTORY_SLOT)
                : FIRST_SLOT;
        helper.assertFalse(
            click(player, station.resultSlot(), action, slot, PinnedAction.PRIMARY_BUTTON),
            station.name() + ": " + action + " in the result slot must be refused");
      }

      helper.assertTrue(
          player.containerMenu.getCarried().isEmpty(), station.name() + ": nothing taken");
      helper.assertTrue(
          ItemStack.matches(result.getItem(), box), station.name() + ": the result is unchanged");
      helper.assertTrue(
          player.getInventory().countItem(Items.DIRT) == 0,
          station.name() + ": no dirt in the inventory");
    }
    helper.succeed();
  }

  /**
   * The real case behind a result slot holding a box: a box renamed in the anvil. Emptying the box
   * in the input slot through its window rebuilds the result from it (vanilla {@code
   * ItemCombinerMenu#slotsChanged}), so the renamed copy can never hand the dirt out a second time.
   */
  @GameTest
  public void anvilResultFollowsTheEditedInputBox(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    AnvilMenu anvil = new AnvilMenu(CONTAINER_ID, player.getInventory());
    player.containerMenu = anvil;
    anvil
        .getSlot(AnvilMenu.INPUT_SLOT)
        .set(ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    anvil.setItemName(NEW_NAME);
    ItemStack renamed = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
    helper.assertTrue(
        ShulkerFixtures.countInBox(renamed, Items.DIRT) == DIRT_COUNT,
        "the renamed copy holds the dirt before the edit");

    helper.assertTrue(
        PinnedWindowClicks.apply(
            player,
            payload(
                AnvilMenu.INPUT_SLOT,
                Items.SHULKER_BOX,
                PinnedAction.QUICK_MOVE_OUT,
                FIRST_SLOT,
                PinnedAction.PRIMARY_BUTTON),
            ServerPolicy.UNRESTRICTED),
        "the move out of the input box must pass");

    helper.assertTrue(
        player.getInventory().countItem(Items.DIRT) == DIRT_COUNT, "the dirt is in the inventory");
    helper.assertTrue(
        ShulkerFixtures.countInBox(anvil.getSlot(AnvilMenu.INPUT_SLOT).getItem(), Items.DIRT) == 0,
        "the input box is empty");
    helper.assertTrue(
        ShulkerFixtures.countInBox(anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem(), Items.DIRT) == 0,
        "the renamed copy is empty too");
    helper.succeed();
  }

  /** The stonecutter's input takes any item, so a box there opens a window like any other. */
  @GameTest
  public void boxInTheStonecutterInputIsAWindow(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    StonecutterMenu stonecutter = new StonecutterMenu(CONTAINER_ID, player.getInventory());
    player.containerMenu = stonecutter;
    stonecutter
        .getSlot(StonecutterMenu.INPUT_SLOT)
        .set(ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    helper.assertTrue(
        PinnedWindowClicks.apply(
            player,
            payload(
                StonecutterMenu.INPUT_SLOT,
                Items.SHULKER_BOX,
                PinnedAction.PICKUP,
                FIRST_SLOT,
                PinnedAction.PRIMARY_BUTTON),
            ServerPolicy.UNRESTRICTED),
        "the take must pass");

    helper.assertTrue(player.containerMenu.getCarried().getCount() == DIRT_COUNT, "dirt taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(
                stonecutter.getSlot(StonecutterMenu.INPUT_SLOT).getItem(), Items.DIRT)
            == 0,
        "the box in the stonecutter is empty");
    helper.assertTrue(
        stonecutter.getSlot(StonecutterMenu.RESULT_SLOT).getItem().isEmpty(), "no result");
    helper.succeed();
  }

  private static Player playerIn(GameTestHelper helper, Station station) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.containerMenu = station.factory().create(CONTAINER_ID, player.getInventory());
    return player;
  }

  /** The station's own slots (inputs and result) stay empty: window clicks never reach them. */
  private static void expectStationSlotsEmpty(
      GameTestHelper helper, Station station, Player player) {
    for (int index = 0; index <= station.resultSlot(); index++) {
      helper.assertTrue(
          player.containerMenu.getSlot(index).getItem().isEmpty(),
          station.name() + ": station slot " + index + " must stay empty");
    }
  }

  private static int menuSlotOf(Player player, int inventorySlot) {
    for (Slot slot : player.containerMenu.slots) {
      if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
        return slot.index;
      }
    }
    throw new AssertionError("inventory slot " + inventorySlot + " is not in the menu");
  }

  private static ItemStack box(Player player) {
    return player.getInventory().getItem(BOX_INVENTORY_SLOT);
  }

  private static boolean click(
      Player player, int menuSlot, PinnedAction action, int slot, int button) {
    Item item = player.containerMenu.getSlot(menuSlot).getItem().getItem();
    return PinnedWindowClicks.apply(
        player, payload(menuSlot, item, action, slot, button), ServerPolicy.UNRESTRICTED);
  }

  private static PinnedWindowClickPayload payload(
      int menuSlot, Item item, PinnedAction action, int slot, int button) {
    return new PinnedWindowClickPayload(
        CONTAINER_ID,
        List.of(new PinnedWindowClickPayload.Level(menuSlot, item)),
        action,
        slot,
        button);
  }
}
