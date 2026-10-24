package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/**
 * Server side of pinned window clicks ({@link PinnedWindowClicks}), in a chest menu opened by a
 * mock player: the box sits in the first main inventory slot. Clicks call {@link
 * PinnedWindowClicks#apply} with a policy (mock players have no mod channels); {@link
 * #clientWithoutTheModIsRejected} goes through the payload handler.
 */
public final class PinnedWindowClicksGameTest {
  private static final int CONTAINER_ID = 1;

  /** Chest menu slots: 27 chest slots, then main inventory (inventory 9-35), then hotbar (0-8). */
  private static final int BOX_MENU_SLOT = 27;

  private static final int BOX_INVENTORY_SLOT = 9;
  private static final int OTHER_MENU_SLOT = 28;
  private static final int OTHER_INVENTORY_SLOT = 10;
  private static final int HOTBAR_MENU_SLOT = 54;
  private static final int FIRST_SLOT = 0;
  private static final int SECOND_SLOT = 1;
  private static final int ENDER_BOX_SLOT = 3;
  private static final int DIRT_COUNT = 13;
  private static final int DIRT_HALF = 7;
  private static final int FULL_STACK = 64;
  private static final int STONE_COUNT = 20;
  private static final int TABLE_COUNT = 3;
  private static final int NO_SLOT = -1;
  private static final ServerPolicy PINNING_FORBIDDEN =
      new ServerPolicy(true, true, true, true, true, true, true, true, false);

  @GameTest
  public void pickupTakesTheWholeStack(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    helper.assertTrue(click(player, PinnedAction.PICKUP, FIRST_SLOT, 0), "the click must pass");

    helper.assertTrue(player.containerMenu.getCarried().getCount() == DIRT_COUNT, "13 dirt taken");
    helper.assertTrue(ShulkerFixtures.countInBox(box(player), Items.DIRT) == 0, "the box is empty");
    helper.succeed();
  }

  @GameTest
  public void rightClickTakesHalfThenPlacesOne(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    click(player, PinnedAction.PICKUP, FIRST_SLOT, PinnedAction.SECONDARY_BUTTON);
    helper.assertTrue(player.containerMenu.getCarried().getCount() == DIRT_HALF, "half rounds up");
    click(player, PinnedAction.PICKUP, SECOND_SLOT, PinnedAction.SECONDARY_BUTTON);

    helper.assertTrue(player.containerMenu.getCarried().getCount() == DIRT_HALF - 1, "one placed");
    helper.assertTrue(
        slotsOf(box(player)).get(SECOND_SLOT).getCount() == 1, "the second slot holds one");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.DIRT) == DIRT_COUNT - DIRT_HALF + 1,
        "7 in the box");
    helper.succeed();
  }

  @GameTest
  public void placePutsTheCursorStackInAnEmptySlot(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.SHULKER_BOX));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, STONE_COUNT));

    click(player, PinnedAction.PICKUP, SECOND_SLOT, PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the cursor is empty");
    helper.assertTrue(
        slotsOf(box(player)).get(SECOND_SLOT).getCount() == STONE_COUNT, "20 stone in slot 1");
    helper.succeed();
  }

  @GameTest
  public void shulkerBoxesDoNotGoIntoShulkerBoxes(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.SHULKER_BOX));
    player.containerMenu.setCarried(new ItemStack(Items.DYED_SHULKER_BOX.red()));

    click(player, PinnedAction.PICKUP, FIRST_SLOT, PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.DYED_SHULKER_BOX.red()),
        "the box stays on the cursor");
    helper.succeed();
  }

  @GameTest
  public void shiftClickMovesAStackToTheInventory(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    click(player, PinnedAction.QUICK_MOVE_OUT, FIRST_SLOT, PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(ShulkerFixtures.countInBox(box(player), Items.DIRT) == 0, "the box is empty");
    helper.assertTrue(
        player.getInventory().countItem(Items.DIRT) == DIRT_COUNT, "the dirt is in the inventory");
    helper.succeed();
  }

  @GameTest
  public void shiftClickFromTheInventoryFillsTheBox(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.STONE, FULL_STACK - 4)));
    player.getInventory().setItem(OTHER_INVENTORY_SLOT, new ItemStack(Items.STONE, STONE_COUNT));

    click(player, PinnedAction.QUICK_MOVE_IN, OTHER_MENU_SLOT, PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.STONE) == FULL_STACK - 4 + STONE_COUNT,
        "all 20 stone moved in");
    helper.assertTrue(
        slotsOf(box(player)).get(FIRST_SLOT).getCount() == FULL_STACK, "the stack filled first");
    helper.assertTrue(
        player.getInventory().getItem(OTHER_INVENTORY_SLOT).isEmpty(), "the source slot is empty");
    helper.succeed();
  }

  /**
   * A slot that never gives its last item away (as Handy Tables' locked station slot): the box must
   * not get the item while the slot keeps it.
   */
  @GameTest
  public void shiftClickFromASlotThatKeepsItsLastItemIsRefused(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.SHULKER_BOX));
    ItemStack tables = new ItemStack(Items.CRAFTING_TABLE, TABLE_COUNT);
    player.getInventory().setItem(OTHER_INVENTORY_SLOT, tables.copy());
    player.containerMenu.slots.set(
        OTHER_MENU_SLOT, new KeepsLastItemSlot(player.containerMenu.getSlot(OTHER_MENU_SLOT)));

    helper.assertFalse(
        click(player, PinnedAction.QUICK_MOVE_IN, OTHER_MENU_SLOT, PinnedAction.PRIMARY_BUTTON),
        "the slot gives less than the box would take");

    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.CRAFTING_TABLE) == 0, "the box is empty");
    helper.assertTrue(
        ItemStack.matches(player.getInventory().getItem(OTHER_INVENTORY_SLOT), tables),
        "the slot keeps all its tables");
    helper.succeed();
  }

  /** A hotbar slot that refuses to give its item (a locked slot): the swap is refused. */
  @GameTest
  public void swapRespectsAHotbarSlotThatRefusesPickup(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
    player.getInventory().setItem(0, table.copy());
    player.containerMenu.slots.set(
        HOTBAR_MENU_SLOT, new RefusingSlot(player.containerMenu.getSlot(HOTBAR_MENU_SLOT), true));

    helper.assertFalse(click(player, PinnedAction.SWAP, FIRST_SLOT, 0), "the slot keeps its table");
    helper.assertFalse(
        PinnedWindowClicks.swapAllowed(
            player,
            PinnedWindowClicks.inventorySlot(player, player.containerMenu, 0),
            table,
            ItemStack.EMPTY),
        "the client check refuses it too");

    helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), table), "table kept");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.DIRT) == DIRT_COUNT, "dirt kept");
    helper.succeed();
  }

  /** A hotbar slot that refuses the window's item: the swap is refused. */
  @GameTest
  public void swapRespectsAHotbarSlotThatRefusesTheItem(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    player.containerMenu.slots.set(
        HOTBAR_MENU_SLOT, new RefusingSlot(player.containerMenu.getSlot(HOTBAR_MENU_SLOT), false));

    helper.assertFalse(click(player, PinnedAction.SWAP, FIRST_SLOT, 0), "dirt may not go there");

    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "the hotbar slot stays empty");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.DIRT) == DIRT_COUNT, "dirt kept");
    helper.succeed();
  }

  /** A hotbar slot that keeps its last item (Handy Tables' lone station): no copy in the box. */
  @GameTest
  public void swapFromASlotThatKeepsItsLastItemIsRefused(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.SHULKER_BOX));
    ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
    player.getInventory().setItem(0, table.copy());
    player.containerMenu.slots.set(
        HOTBAR_MENU_SLOT, new KeepsLastItemSlot(player.containerMenu.getSlot(HOTBAR_MENU_SLOT)));

    helper.assertFalse(
        click(player, PinnedAction.SWAP, FIRST_SLOT, 0), "the slot gives less than the swap");

    helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), table), "table kept");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box(player), Items.CRAFTING_TABLE) == 0, "no table in the box");
    helper.succeed();
  }

  @GameTest
  public void theBoxCannotMoveOrSwapIntoItsOwnWindow(GameTestHelper helper) {
    Player player = playerWithBox(helper, ItemStack.EMPTY);
    ItemStack box = ShulkerFixtures.box(new ItemStack(Items.DIRT));
    player.getInventory().setItem(0, box);

    helper.assertFalse(
        PinnedWindowClicks.apply(
            player,
            payload(HOTBAR_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.SWAP, FIRST_SLOT, 0),
            ServerPolicy.UNRESTRICTED),
        "the box must not swap with a slot of its own window");
    helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), box), "box unchanged");

    ItemStack bundle = new ItemStack(Items.BUNDLE);
    bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    player.getInventory().setItem(0, bundle);
    helper.assertFalse(
        PinnedWindowClicks.apply(
            player,
            payload(
                HOTBAR_MENU_SLOT, Items.BUNDLE, PinnedAction.QUICK_MOVE_IN, HOTBAR_MENU_SLOT, 0),
            ServerPolicy.UNRESTRICTED),
        "a bundle must not move into its own window");
    helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), bundle), "same bundle");
    helper.succeed();
  }

  @GameTest
  public void shulkerBoxInsideTheEnderChest(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().clearContent();
    player
        .getEnderChestInventory()
        .setItem(ENDER_BOX_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    PinnedWindowClickPayload pickup =
        new PinnedWindowClickPayload(
            CONTAINER_ID,
            List.of(
                new PinnedWindowClickPayload.Level(BOX_MENU_SLOT, Items.ENDER_CHEST),
                new PinnedWindowClickPayload.Level(ENDER_BOX_SLOT, Items.SHULKER_BOX)),
            PinnedAction.PICKUP,
            FIRST_SLOT,
            PinnedAction.PRIMARY_BUTTON);

    helper.assertTrue(
        PinnedWindowClicks.apply(player, pickup, ServerPolicy.UNRESTRICTED), "the click passes");

    helper.assertTrue(player.containerMenu.getCarried().getCount() == DIRT_COUNT, "dirt taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(
                player.getEnderChestInventory().getItem(ENDER_BOX_SLOT), Items.DIRT)
            == 0,
        "the box in the ender chest is empty");
    helper.assertTrue(box(player).is(Items.ENDER_CHEST), "the ender chest item is untouched");
    helper.succeed();
  }

  @GameTest
  public void enderChestNeedsItsPreviewAllowed(GameTestHelper helper) {
    Player player = playerWithBox(helper, new ItemStack(Items.ENDER_CHEST));
    player.getEnderChestInventory().clearContent();
    player.getEnderChestInventory().setItem(FIRST_SLOT, new ItemStack(Items.DIRT));
    ServerPolicy noEnder = new ServerPolicy(true, true, true, false, true, true);

    helper.assertFalse(
        PinnedWindowClicks.apply(
            player,
            payload(BOX_MENU_SLOT, Items.ENDER_CHEST, PinnedAction.PICKUP, FIRST_SLOT, 0),
            noEnder),
        "the ender chest is closed without its preview");
    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing taken");
    helper.succeed();
  }

  @GameTest
  public void bundleInsideAShulkerBoxTakesTheCursorStack(GameTestHelper helper) {
    ItemStack bundle = new ItemStack(Items.BUNDLE);
    bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    Player player = playerWithBox(helper, ShulkerFixtures.box(bundle));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, FULL_STACK));

    helper.assertTrue(
        PinnedWindowClicks.apply(
            player,
            nested(Items.SHULKER_BOX, FIRST_SLOT, Items.BUNDLE, PinnedAction.PICKUP, NO_SLOT),
            ServerPolicy.UNRESTRICTED),
        "the click passes");

    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "all 64 stone went in");
    ItemStack stored = slotsOf(box(player)).get(FIRST_SLOT);
    BundleContents contents = stored.get(DataComponents.BUNDLE_CONTENTS);
    helper.assertTrue(
        contents != null && contents.items().getFirst().count() == FULL_STACK,
        "the bundle in the box holds the stone");
    helper.succeed();
  }

  @GameTest
  public void bundleInsideAFullBundleCannotGrow(GameTestHelper helper) {
    ItemStack inner = new ItemStack(Items.BUNDLE);
    inner.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    ItemStack outer = new ItemStack(Items.BUNDLE);
    outer.set(
        DataComponents.BUNDLE_CONTENTS,
        new BundleContents(
            List.of(
                ItemStackTemplate.fromNonEmptyStack(inner),
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STONE, 56)))));
    Player player = playerWithBox(helper, outer);
    player.containerMenu.setCarried(new ItemStack(Items.DIRT, FULL_STACK));

    helper.assertFalse(
        PinnedWindowClicks.apply(
            player,
            nested(Items.BUNDLE, FIRST_SLOT, Items.BUNDLE, PinnedAction.PICKUP, NO_SLOT),
            ServerPolicy.UNRESTRICTED),
        "the outer bundle would weigh more than one");
    helper.assertTrue(
        player.containerMenu.getCarried().getCount() == FULL_STACK, "the cursor keeps its dirt");
    helper.assertTrue(ItemStack.matches(box(player), outer), "the bundles are unchanged");
    helper.succeed();
  }

  @GameTest
  public void movedBoxIsRejected(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));
    PinnedWindowClickPayload pickup =
        payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.PICKUP, FIRST_SLOT, 0);
    ItemStack moved = box(player);
    player.getInventory().setItem(BOX_INVENTORY_SLOT, ItemStack.EMPTY);
    player.getInventory().setItem(OTHER_INVENTORY_SLOT, moved);

    helper.assertFalse(
        PinnedWindowClicks.apply(player, pickup, ServerPolicy.UNRESTRICTED), "nothing is there");
    player.getInventory().setItem(BOX_INVENTORY_SLOT, new ItemStack(Items.DYED_SHULKER_BOX.red()));
    helper.assertFalse(
        PinnedWindowClicks.apply(player, pickup, ServerPolicy.UNRESTRICTED),
        "another box is there");
    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing taken");
    helper.assertTrue(ShulkerFixtures.countInBox(moved, Items.DIRT) == DIRT_COUNT, "dirt kept");
    helper.succeed();
  }

  @GameTest
  public void policyOffIsRejected(GameTestHelper helper) {
    Player player =
        playerWithBox(helper, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    helper.assertFalse(
        PinnedWindowClicks.apply(
            player,
            payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.PICKUP, FIRST_SLOT, 0),
            PINNING_FORBIDDEN),
        "the server forbids pinned windows");
    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing taken");
    helper.succeed();
  }

  /** A fake player has no mod channels, as a vanilla client. */
  @GameTest
  public void clientWithoutTheModIsRejected(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    openChest(player);
    player
        .getInventory()
        .setItem(BOX_INVENTORY_SLOT, ShulkerFixtures.box(new ItemStack(Items.DIRT, DIRT_COUNT)));

    PinnedWindowClicks.handle(
        player, payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.PICKUP, FIRST_SLOT, 0));

    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing taken");
    helper.assertTrue(
        ShulkerFixtures.countInBox(player.getInventory().getItem(BOX_INVENTORY_SLOT), Items.DIRT)
            == DIRT_COUNT,
        "the box keeps its dirt");
    helper.succeed();
  }

  @GameTest
  public void aSequenceOfClicksNeverDuplicates(GameTestHelper helper) {
    Player player =
        playerWithBox(
            helper,
            ShulkerFixtures.box(
                new ItemStack(Items.DIRT, DIRT_COUNT), new ItemStack(Items.DIRT, FULL_STACK)));
    player.getInventory().setItem(OTHER_INVENTORY_SLOT, new ItemStack(Items.DIRT, STONE_COUNT));
    int total = DIRT_COUNT + FULL_STACK + STONE_COUNT;
    List<PinnedWindowClickPayload> clicks = new ArrayList<>();
    for (int slot = 0; slot < 4; slot++) {
      clicks.add(payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.PICKUP, slot, slot % 2));
      clicks.add(payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.QUICK_MOVE_OUT, slot, 0));
      clicks.add(
          payload(
              BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.QUICK_MOVE_IN, OTHER_MENU_SLOT, 0));
      clicks.add(payload(BOX_MENU_SLOT, Items.SHULKER_BOX, PinnedAction.SWAP, slot, slot));
    }

    for (PinnedWindowClickPayload click : clicks) {
      PinnedWindowClicks.apply(player, click, ServerPolicy.UNRESTRICTED);
      int now =
          player.getInventory().countItem(Items.DIRT)
              + ShulkerFixtures.countInBox(box(player), Items.DIRT)
              + (player.containerMenu.getCarried().is(Items.DIRT)
                  ? player.containerMenu.getCarried().getCount()
                  : 0);
      helper.assertTrue(now == total, "dirt must stay " + total + " after " + click + ": " + now);
    }
    helper.succeed();
  }

  @GameTest
  public void payloadRoundTrips(GameTestHelper helper) {
    PinnedWindowClickPayload sent =
        nested(
            Items.ENDER_CHEST, ENDER_BOX_SLOT, Items.DYED_SHULKER_BOX.blue(), PinnedAction.SWAP, 4);
    RegistryFriendlyByteBuf buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    PinnedWindowClickPayload.CODEC.encode(buffer, sent);

    helper.assertTrue(sent.equals(PinnedWindowClickPayload.CODEC.decode(buffer)), "same payload");
    helper.succeed();
  }

  /** Takes stop one short of the whole stack, as Handy Tables' {@code LockedStationSlot}. */
  private static final class KeepsLastItemSlot extends Slot {
    KeepsLastItemSlot(Slot original) {
      super(original.container, original.getContainerSlot(), original.x, original.y);
      this.index = original.index;
    }

    @Override
    public ItemStack remove(int amount) {
      return super.remove(Math.min(amount, getItem().getCount() - 1));
    }
  }

  /** Refuses taking its item ({@code mayPickup}) or placing any item ({@code mayPlace}). */
  private static final class RefusingSlot extends Slot {
    private final boolean refusesPickup;

    RefusingSlot(Slot original, boolean refusesPickup) {
      super(original.container, original.getContainerSlot(), original.x, original.y);
      this.index = original.index;
      this.refusesPickup = refusesPickup;
    }

    @Override
    public boolean mayPickup(Player player) {
      return !refusesPickup;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
      return refusesPickup;
    }
  }

  private static Player playerWithBox(GameTestHelper helper, ItemStack box) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    openChest(player);
    player.getInventory().setItem(BOX_INVENTORY_SLOT, box);
    return player;
  }

  private static void openChest(Player player) {
    player.containerMenu =
        ChestMenu.threeRows(
            CONTAINER_ID, player.getInventory(), new SimpleContainer(StorageKind.SHULKER_SLOTS));
  }

  private static ItemStack box(Player player) {
    return player.getInventory().getItem(BOX_INVENTORY_SLOT);
  }

  private static List<ItemStack> slotsOf(ItemStack box) {
    NonNullList<ItemStack> slots = NonNullList.withSize(StorageKind.SHULKER_SLOTS, ItemStack.EMPTY);
    box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
    return slots;
  }

  private static boolean click(Player player, PinnedAction action, int slot, int button) {
    return PinnedWindowClicks.apply(
        player,
        payload(BOX_MENU_SLOT, box(player).getItem(), action, slot, button),
        ServerPolicy.UNRESTRICTED);
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

  private static PinnedWindowClickPayload nested(
      Item root, int index, Item inner, PinnedAction action, int slot) {
    return new PinnedWindowClickPayload(
        CONTAINER_ID,
        List.of(
            new PinnedWindowClickPayload.Level(BOX_MENU_SLOT, root),
            new PinnedWindowClickPayload.Level(index, inner)),
        action,
        slot,
        action == PinnedAction.SWAP ? 4 : PinnedAction.PRIMARY_BUTTON);
  }
}
