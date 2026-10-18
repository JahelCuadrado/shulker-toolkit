package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import com.einsteinmods.shulkertoolkit.transfer.StorageClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/**
 * Server side of container item clicks ({@link StorageClicks}), in a chest menu opened by a mock
 * player (the inventory menu's crafting grid needs a connected {@code ServerPlayer}). Clicks call
 * the click rules directly (a mock player has no client channels, so the gate is closed); {@link
 * #clickWithoutTheClientModStaysVanilla} goes through the real menu click and mixin.
 */
public final class StorageClicksGameTest {
  /** Chest slots of the opened menu. */
  private static final int BOX_SLOT = 0;

  private static final int OTHER_SLOT = 1;
  private static final int FIRST_DRAG_SLOT = 2;
  private static final int DRAGGED_SLOTS = 5;
  private static final int CONTAINER_ID = 1;
  private static final int PRIMARY_BUTTON = 0;
  private static final int FULL_STACK = 64;
  private static final int DIRT_COUNT = 12;
  private static final int TORCH_COUNT = 5;
  private static final int SECOND_BOX_SLOT = 4;
  private static final int LAST_BOX_SLOT = 8;
  private static final int ENDER_SLOT = 13;
  private static final ServerPolicy TRANSFER_FORBIDDEN =
      new ServerPolicy(false, true, true, true, true, true);
  private static final ControlScheme SPLIT = new ControlScheme(false, ClickScheme.SPLIT_INPUT);
  private static final ControlScheme SINGLE_ITEM =
      new ControlScheme(true, ClickScheme.SINGLE_INPUT);

  @GameTest
  public void singleInputRightClickInsertsTheClickedStack(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    Slot stoneSlot = putInSlot(player, OTHER_SLOT, new ItemStack(Items.STONE, FULL_STACK));
    ItemStack box = new ItemStack(Items.SHULKER_BOX);

    boolean handled = boxOnCursorClicks(player, box, stoneSlot, ClickAction.SECONDARY);

    helper.assertTrue(handled, "a right click with the box on the cursor must be handled");
    helper.assertTrue(stoneSlot.getItem().isEmpty(), "the stone must leave the slot");
    helper.assertTrue(
        slotsOf(box).get(0).getCount() == FULL_STACK, "64 stone must be in the first box slot");
    helper.assertTrue(
        StorageStates.of(player).selectedItem() == 0, "the selection follows the inserted slot");
    helper.succeed();
  }

  @GameTest
  public void singleInputRightClickOnAnEmptySlotExtractsTheSelectedStack(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    ItemStack box = boxWith(dirtTorchStone());
    Slot emptySlot = player.containerMenu.getSlot(OTHER_SLOT);

    boolean first = boxOnCursorClicks(player, box, emptySlot, ClickAction.SECONDARY);
    ItemStack lastStack = emptySlot.getItem().copy();
    emptySlot.set(ItemStack.EMPTY);
    StorageStates.of(player).setSelectedItem(0);
    boolean second = boxOnCursorClicks(player, box, emptySlot, ClickAction.SECONDARY);

    helper.assertTrue(first && second, "both extractions must be handled");
    helper.assertTrue(
        lastStack.is(Items.STONE) && lastStack.getCount() == FULL_STACK,
        "without a selection the last stack comes out, was " + lastStack);
    helper.assertTrue(
        emptySlot.getItem().is(Items.DIRT) && emptySlot.getItem().getCount() == DIRT_COUNT,
        "the selected stack comes out, was " + emptySlot.getItem());
    helper.assertTrue(slotsOf(box).get(SECOND_BOX_SLOT).is(Items.TORCH), "the rest stays");
    helper.succeed();
  }

  @GameTest
  public void singleInputLeftClickStaysVanilla(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    Slot stoneSlot = putInSlot(player, OTHER_SLOT, new ItemStack(Items.STONE, FULL_STACK));
    ItemStack box = new ItemStack(Items.SHULKER_BOX);

    boolean handled = boxOnCursorClicks(player, box, stoneSlot, ClickAction.PRIMARY);
    Slot boxSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.SHULKER_BOX));
    boolean handledOnBox =
        stackOnBoxClicks(player, boxSlot, new ItemStack(Items.DIRT), ClickAction.PRIMARY);

    helper.assertFalse(handled, "a left click with the box on the cursor must stay vanilla");
    helper.assertFalse(handledOnBox, "a left click with a stack on a box must stay vanilla");
    helper.assertTrue(stoneSlot.getItem().getCount() == FULL_STACK, "nothing may move");
    helper.succeed();
  }

  @GameTest
  public void splitInputInsertsWithLeftAndExtractsWithRight(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, SPLIT);
    Slot boxSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.SHULKER_BOX));
    ItemStack cursor = new ItemStack(Items.STONE, FULL_STACK);

    boolean inserted = stackOnBoxClicks(player, boxSlot, cursor, ClickAction.PRIMARY);
    ItemStack extracted = extractOntoCursor(player, boxSlot);

    helper.assertTrue(inserted, "a left click with a stack on the box inserts it");
    helper.assertTrue(cursor.isEmpty(), "the whole stack must leave the cursor");
    helper.assertTrue(
        extracted.is(Items.STONE) && extracted.getCount() == FULL_STACK,
        "a right click with an empty cursor extracts the stack, was " + extracted);
    helper.succeed();
  }

  @GameTest
  public void insertMergesIntoStacksOfTheSameKindFirst(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    List<ItemStack> initial = emptySlots();
    initial.set(0, new ItemStack(Items.DIRT, DIRT_COUNT));
    initial.set(SECOND_BOX_SLOT, new ItemStack(Items.STONE, FULL_STACK - TORCH_COUNT));
    Slot boxSlot = putInSlot(player, BOX_SLOT, boxWith(initial));

    stackOnBoxClicks(
        player, boxSlot, new ItemStack(Items.STONE, FULL_STACK), ClickAction.SECONDARY);

    List<ItemStack> slots = slotsOf(boxSlot.getItem());
    helper.assertTrue(
        slots.get(SECOND_BOX_SLOT).getCount() == FULL_STACK, "the partial stack fills first");
    helper.assertTrue(
        slots.get(1).is(Items.STONE) && slots.get(1).getCount() == FULL_STACK - TORCH_COUNT,
        "the rest goes to the first empty slot, was " + slots.get(1));
    helper.succeed();
  }

  @GameTest
  public void fullBoxConsumesTheClickAndKeepsTheCursor(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    List<ItemStack> initial = emptySlots();
    for (int slot = 0; slot < StorageKind.SHULKER_SLOTS; slot++) {
      initial.set(slot, new ItemStack(Items.DIRT, FULL_STACK));
    }
    ItemStack box = boxWith(initial);
    ItemContainerContents before = box.get(DataComponents.CONTAINER);
    Slot boxSlot = putInSlot(player, BOX_SLOT, box);
    ItemStack cursor = new ItemStack(Items.STONE, FULL_STACK);

    boolean handled = stackOnBoxClicks(player, boxSlot, cursor, ClickAction.SECONDARY);

    helper.assertTrue(handled, "a full box still consumes the click, like a full bundle");
    helper.assertTrue(cursor.getCount() == FULL_STACK, "the cursor must stay intact");
    helper.assertTrue(
        before.equals(boxSlot.getItem().get(DataComponents.CONTAINER)), "contents unchanged");
    helper.succeed();
  }

  @GameTest
  public void shulkerBoxesNeverGoInside(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    Slot boxSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.SHULKER_BOX));
    ItemStack cursor = new ItemStack(Items.DYED_SHULKER_BOX.red());

    stackOnBoxClicks(player, boxSlot, cursor, ClickAction.SECONDARY);

    helper.assertTrue(cursor.getCount() == 1, "the cursor box must stay");
    helper.assertTrue(
        boxSlot.getItem().get(DataComponents.CONTAINER) == ItemContainerContents.EMPTY,
        "nothing may enter the box");
    helper.succeed();
  }

  /** The server side of a drag: the client sends one right click per slot it passes. */
  @GameTest
  public void draggingTheBoxOverDirtInsertsEveryStack(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    ItemStack box = new ItemStack(Items.SHULKER_BOX);
    int dragged = 0;
    for (int index = 0; index < DRAGGED_SLOTS; index++) {
      Slot slot = putInSlot(player, FIRST_DRAG_SLOT + index, new ItemStack(Items.DIRT, DIRT_COUNT));
      if (StorageClicks.insertsFrom(StorageKind.SHULKER_BOX, box, slot, player)) {
        boxOnCursorClicks(player, box, slot, ClickAction.SECONDARY);
        dragged++;
      }
    }
    Slot empty = player.containerMenu.getSlot(FIRST_DRAG_SLOT + DRAGGED_SLOTS);

    helper.assertTrue(dragged == DRAGGED_SLOTS, "every dirt slot must take part in the drag");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.DIRT) == DRAGGED_SLOTS * DIRT_COUNT,
        "all the dirt must be in the box");
    helper.assertTrue(
        StorageClicks.extractsTo(StorageKind.SHULKER_BOX, box, empty, player),
        "an empty slot is a drag extraction target");
    helper.succeed();
  }

  @GameTest
  public void singleItemMovementMovesOneItemEachWay(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, SINGLE_ITEM);
    Slot stoneSlot = putInSlot(player, OTHER_SLOT, new ItemStack(Items.STONE, FULL_STACK));
    ItemStack box = new ItemStack(Items.SHULKER_BOX);

    boolean inserted = boxOnCursorClicks(player, box, stoneSlot, ClickAction.PRIMARY);
    boxOnCursorClicks(player, box, stoneSlot, ClickAction.PRIMARY);
    boolean extracted = boxOnCursorClicks(player, box, stoneSlot, ClickAction.SECONDARY);

    helper.assertTrue(inserted && extracted, "single item clicks must be handled");
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.STONE) == 1, "two in, one out leaves one inside");
    helper.assertTrue(
        stoneSlot.getItem().getCount() == FULL_STACK - 1, "the slot must lose exactly one");
    helper.succeed();
  }

  @GameTest
  public void enderChestItemEditsTheEnderInventory(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    player.getEnderChestInventory().clearContent();
    player.getEnderChestInventory().setItem(ENDER_SLOT, new ItemStack(Items.DIAMOND, TORCH_COUNT));
    Slot enderSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.ENDER_CHEST));
    ItemStack cursor = new ItemStack(Items.TORCH, FULL_STACK);

    stackOnBoxClicks(player, enderSlot, cursor, ClickAction.SECONDARY);
    ItemStack extracted = extractOntoCursor(player, enderSlot);

    helper.assertTrue(cursor.isEmpty(), "the torches must go into the ender chest");
    helper.assertTrue(
        extracted.is(Items.TORCH),
        "the selection followed the insert, so the torches come back, was " + extracted);
    helper.assertTrue(
        player.getEnderChestInventory().getItem(ENDER_SLOT).is(Items.DIAMOND), "the diamonds stay");
    helper.assertTrue(
        StorageKind.ENDER_CHEST.hasContents(enderSlot.getItem(), player),
        "the ender chest item shows the player's ender inventory");
    helper.succeed();
  }

  @GameTest
  public void bundleInsertsNewestFirstAndExtractsIt(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    Slot bundleSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.BUNDLE));

    stackOnBoxClicks(
        player, bundleSlot, new ItemStack(Items.DIRT, DIRT_COUNT), ClickAction.SECONDARY);
    ItemStack cursor = new ItemStack(Items.STONE, FULL_STACK);
    stackOnBoxClicks(player, bundleSlot, cursor, ClickAction.SECONDARY);
    BundleContents contents = bundleSlot.getItem().get(DataComponents.BUNDLE_CONTENTS);
    ItemStack extracted = extractOntoCursor(player, bundleSlot);

    helper.assertTrue(contents != null && contents.size() == 2, "dirt and stone must be inside");
    helper.assertTrue(
        cursor.getCount() == FULL_STACK - (FULL_STACK - DIRT_COUNT),
        "only what the weight allows goes in, cursor was " + cursor);
    helper.assertTrue(
        contents.items().get(0).is(Items.STONE), "the newest stack is the first, as vanilla");
    helper.assertTrue(extracted.is(Items.STONE), "the newest stack comes out first");
    helper.succeed();
  }

  /** Bundle interactions off (the default): the rules leave bundles to vanilla. */
  @GameTest
  public void bundlesStayVanillaWithTheOptionOff(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(helper, GameType.SURVIVAL, PlayerPreferences.DEFAULTS);
    openChest(player);
    Slot bundleSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.BUNDLE));
    ItemStack cursor = new ItemStack(Items.STONE, FULL_STACK);
    player.containerMenu.setCarried(cursor);

    TriState decision = decideFor(player, bundleSlot, cursor, ClickAction.SECONDARY);
    player.containerMenu.clicked(BOX_SLOT, PRIMARY_BUTTON, ContainerInput.PICKUP, player);

    helper.assertTrue(decision == TriState.DEFAULT, "with the option off vanilla decides");
    BundleContents contents = bundleSlot.getItem().get(DataComponents.BUNDLE_CONTENTS);
    helper.assertTrue(
        contents != null && contents.size() == 1 && contents.items().get(0).is(Items.STONE),
        "vanilla's left click puts the stone into the bundle");
    ServerPlayerSessions.remove(player.getUUID());
    helper.succeed();
  }

  /** Bundle interactions on: a right click with stone inserts it, as with a shulker box. */
  @GameTest
  public void bundlesUseTheBoxControlsWithTheOptionOn(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper, GameType.SURVIVAL, new PlayerPreferences(PickMode.DEFAULT, true, true));
    openChest(player);
    StorageStates.of(player).setControlScheme(ControlScheme.DEFAULT);
    Slot bundleSlot = putInSlot(player, BOX_SLOT, new ItemStack(Items.BUNDLE));
    ItemStack cursor = new ItemStack(Items.STONE, FULL_STACK);
    player.containerMenu.setCarried(cursor);

    TriState decision = decideFor(player, bundleSlot, cursor, ClickAction.SECONDARY);

    helper.assertTrue(decision == TriState.TRUE, "the click is handled by the box controls");
    helper.assertTrue(cursor.isEmpty(), "the stone went into the bundle");
    ServerPlayerSessions.remove(player.getUUID());
    StorageStates.forget(player.getUUID());
    helper.succeed();
  }

  @GameTest
  public void forbiddenTransferAndVanillaClientsCloseTheGate(GameTestHelper helper) {
    helper.assertFalse(
        TransferGate.serverAllows(TRANSFER_FORBIDDEN, true), "allowTransfer=false closes it");
    helper.assertFalse(
        TransferGate.serverAllows(ServerPolicy.UNRESTRICTED, false),
        "a client without the mod keeps vanilla clicks");
    helper.assertTrue(
        TransferGate.serverAllows(ServerPolicy.UNRESTRICTED, true), "otherwise it is open");
    helper.succeed();
  }

  @GameTest
  public void stacksOfSeveralBoxesAreNeverEdited(GameTestHelper helper) {
    ItemStack boxes = new ItemStack(Items.SHULKER_BOX);
    boxes.set(DataComponents.MAX_STACK_SIZE, 2);
    boxes.setCount(2);
    ItemStack bundles = new ItemStack(Items.BUNDLE);
    bundles.set(DataComponents.MAX_STACK_SIZE, 2);
    bundles.setCount(2);

    helper.assertFalse(
        StorageKind.SHULKER_BOX.allowModification(boxes),
        "inserting into two boxes at once would duplicate items");
    helper.assertFalse(StorageKind.BUNDLE.allowModification(bundles), "the same for bundles");
    helper.succeed();
  }

  /** Through the real menu click and mixin: a mock player has no mod channels, so vanilla swaps. */
  @GameTest
  public void clickWithoutTheClientModStaysVanilla(GameTestHelper helper) {
    Player player = playerWithChestOpen(helper, ControlScheme.DEFAULT);
    putInSlot(player, BOX_SLOT, new ItemStack(Items.SHULKER_BOX));
    player.containerMenu.setCarried(new ItemStack(Items.STONE, FULL_STACK));

    player.containerMenu.clicked(BOX_SLOT, PRIMARY_BUTTON, ContainerInput.PICKUP, player);

    helper.assertTrue(
        player.containerMenu.getSlot(BOX_SLOT).getItem().is(Items.STONE),
        "vanilla swaps the stone into the slot");
    helper.assertTrue(
        player.containerMenu.getCarried().is(Items.SHULKER_BOX), "and the box onto the cursor");
    helper.succeed();
  }

  private static Player playerWithChestOpen(GameTestHelper helper, ControlScheme scheme) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.containerMenu =
        ChestMenu.threeRows(
            CONTAINER_ID, player.getInventory(), new SimpleContainer(StorageKind.SHULKER_SLOTS));
    StorageStates.of(player).setControlScheme(scheme);
    StorageStates.of(player).setSelectedItem(StorageGrid.NO_ITEM);
    return player;
  }

  private static void openChest(Player player) {
    player.containerMenu =
        ChestMenu.threeRows(
            CONTAINER_ID, player.getInventory(), new SimpleContainer(StorageKind.SHULKER_SLOTS));
  }

  /** The click rules with the transfer gate open (a fake player has no mod channels). */
  private static TriState decideFor(
      Player player, Slot slot, ItemStack carried, ClickAction action) {
    ItemStack clicked = slot.getItem();
    return StorageClicks.decide(
        player.containerMenu,
        player,
        action,
        slot,
        clicked,
        carried,
        StorageKind.of(carried),
        StorageKind.of(clicked),
        true);
  }

  private static boolean boxOnCursorClicks(
      Player player, ItemStack box, Slot slot, ClickAction action) {
    player.containerMenu.setCarried(box);
    StorageKind kind = StorageKind.of(box);
    return kind != null && StorageClicks.stackedOnOther(kind, box, slot, action, player);
  }

  private static boolean stackOnBoxClicks(
      Player player, Slot boxSlot, ItemStack cursor, ClickAction action) {
    player.containerMenu.setCarried(cursor);
    StorageKind kind = StorageKind.of(boxSlot.getItem());
    return kind != null
        && StorageClicks.otherStackedOnMe(
            kind,
            boxSlot.getItem(),
            cursor,
            boxSlot,
            action,
            player,
            carriedAccess(player.containerMenu));
  }

  private static ItemStack extractOntoCursor(Player player, Slot boxSlot) {
    stackOnBoxClicks(player, boxSlot, ItemStack.EMPTY, ClickAction.SECONDARY);
    return player.containerMenu.getCarried();
  }

  private static SlotAccess carriedAccess(AbstractContainerMenu menu) {
    return SlotAccess.of(menu::getCarried, menu::setCarried);
  }

  private static Slot putInSlot(Player player, int menuSlot, ItemStack stack) {
    Slot slot = player.containerMenu.getSlot(menuSlot);
    slot.set(stack);
    return slot;
  }

  private static List<ItemStack> dirtTorchStone() {
    List<ItemStack> slots = emptySlots();
    slots.set(0, new ItemStack(Items.DIRT, DIRT_COUNT));
    slots.set(SECOND_BOX_SLOT, new ItemStack(Items.TORCH, TORCH_COUNT));
    slots.set(LAST_BOX_SLOT, new ItemStack(Items.STONE, FULL_STACK));
    return slots;
  }

  private static List<ItemStack> emptySlots() {
    return new ArrayList<>(Collections.nCopies(StorageKind.SHULKER_SLOTS, ItemStack.EMPTY));
  }

  private static ItemStack boxWith(List<ItemStack> slots) {
    ItemStack box = new ItemStack(Items.SHULKER_BOX);
    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
    return box;
  }

  private static List<ItemStack> slotsOf(ItemStack box) {
    NonNullList<ItemStack> slots = NonNullList.withSize(StorageKind.SHULKER_SLOTS, ItemStack.EMPTY);
    box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
    return slots;
  }
}
