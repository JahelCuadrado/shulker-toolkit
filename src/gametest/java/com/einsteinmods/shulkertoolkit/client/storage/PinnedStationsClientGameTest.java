package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.core.PinnedLayout;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The real client in survival: in each work station screen (placed block) middle-clicking a shulker
 * box pins its window beside the screen, clicks in the window take and place items on the server,
 * and a second middle click unpins it. In the anvil, keys stay with the name field while it takes
 * them. Also the crafting screen opened the way Handy Tables opens its portable table (a {@code
 * CraftingMenu} subclass, straight from the inventory screen without a close packet), with and
 * without the recipe book, at GUI height 240 (the default 854 x 480 window, also a Retina Mac's at
 * automatic GUI scale) and at 360.
 *
 * <p>Runs muted, at 30 FPS, in a 640 x 360 window at GUI scale 1 (see {@code build.gradle}).
 */
public final class PinnedStationsClientGameTest implements FabricClientGameTest {
  private static final int BOX_INVENTORY_SLOT = 9;
  private static final int DIRT_COUNT = 13;
  private static final int FIRST_CELL = 0;
  private static final int SECOND_CELL = 1;
  private static final int HALF_SLOT = 8;
  private static final int SERVER_TIMEOUT_TICKS = 40;

  /** Longer than {@code PinnedWindows#SWITCH_MILLIS} (one second). */
  private static final int SWITCH_WAIT_TICKS = 30;

  private static final int SMALL_GUI_SCALE = 2;
  private static final int SMALL_WIDTH = 854;
  private static final int SMALL_HEIGHT = 480;
  private static final int START_GUI_SCALE = 1;
  private static final int START_WIDTH = 640;
  private static final int START_HEIGHT = 360;

  private record Station(Block block, Class<? extends Screen> screen) {}

  private static final List<Station> STATIONS =
      List.of(
          new Station(Blocks.STONECUTTER, StonecutterScreen.class),
          new Station(Blocks.LOOM, LoomScreen.class),
          new Station(Blocks.CARTOGRAPHY_TABLE, CartographyTableScreen.class),
          new Station(Blocks.SMITHING_TABLE, SmithingScreen.class),
          new Station(Blocks.GRINDSTONE, GrindstoneScreen.class),
          new Station(Blocks.ANVIL, AnvilScreen.class));

  @Override
  public void runTest(ClientGameTestContext context) {
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getConnection().waitForChunksRender();
      TestServerContext server = world.getServer();
      server.runOnServer(
          minecraftServer -> {
            ServerPlayer player = player(minecraftServer);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
          });

      for (Station station : STATIONS) {
        giveBox(server);
        openBlock(context, server, station);
        pinUseAndUnpin(context, server, station.screen().getSimpleName());
        close(context, server);
      }

      giveBox(server);
      anvilNameKeepsItsKeys(context, server);
      close(context, server);

      craftingLikeHandyTables(context, server);

      pinsFollowMenuSwitches(context, server);
    }
  }

  /**
   * Pins follow the server switching menus: with a close packet first (vanilla {@code
   * ServerPlayer#openMenu} over an open menu, Handy Tables from a chest) and without one (Handy
   * Tables from station to station). The player closing the screen unpins them; so does a server
   * close that no screen follows.
   */
  private static void pinsFollowMenuSwitches(
      ClientGameTestContext context, TestServerContext server) {
    giveBox(server);
    openBlock(context, server, new Station(Blocks.CRAFTING_TABLE, CraftingScreen.class));
    middleClickBoxSlot(context);
    expect(context.computeOnClient(PinnedStationsClientGameTest::boxPinned), "crafting: pinned");

    openBlock(context, server, new Station(Blocks.STONECUTTER, StonecutterScreen.class));
    expect(
        context.computeOnClient(PinnedStationsClientGameTest::boxPinned),
        "the pin follows a switch with a close packet");
    takeAndPutBack(context, server, "after the switch");

    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          BlockPos pos = placeBeside(player, Blocks.LOOM);
          player.doCloseContainer();
          player.openMenu(player.level().getBlockState(pos).getMenuProvider(player.level(), pos));
        });
    context.waitForScreen(LoomScreen.class);
    context.waitTick();
    expect(
        context.computeOnClient(PinnedStationsClientGameTest::boxPinned),
        "the pin follows a switch without a close packet");

    close(context, server);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    expect(
        !context.computeOnClient(PinnedStationsClientGameTest::boxPinned),
        "closing the screen unpins");

    middleClickBoxSlot(context);
    expect(context.computeOnClient(PinnedStationsClientGameTest::boxPinned), "inventory: pinned");
    server.runOnServer(minecraftServer -> player(minecraftServer).closeContainer());
    context.waitForScreen(null);
    context.waitTicks(SWITCH_WAIT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    expect(
        !context.computeOnClient(PinnedStationsClientGameTest::boxPinned),
        "a server close with no screen after it unpins");
    close(context, server);
  }

  private static void takeAndPutBack(
      ClientGameTestContext context, TestServerContext server, String name) {
    clickCell(context, FIRST_CELL);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.DIRT),
        SERVER_TIMEOUT_TICKS);
    clickCell(context, SECOND_CELL);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().isEmpty(),
        SERVER_TIMEOUT_TICKS);
    expect(
        server.computeOnServer(
            minecraftServer -> boxSlots(player(minecraftServer)).get(SECOND_CELL).is(Items.DIRT)),
        name + ": the window moves items");
  }

  private static void pinUseAndUnpin(
      ClientGameTestContext context, TestServerContext server, String name) {
    middleClickBoxSlot(context);
    expect(context.computeOnClient(PinnedStationsClientGameTest::boxPinned), name + ": pinned");

    clickCell(context, FIRST_CELL);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.DIRT),
        SERVER_TIMEOUT_TICKS);
    clickCell(context, SECOND_CELL);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().isEmpty(),
        SERVER_TIMEOUT_TICKS);
    boolean moved =
        server.computeOnServer(
            minecraftServer -> {
              List<ItemStack> slots = boxSlots(player(minecraftServer));
              return slots.get(FIRST_CELL).isEmpty()
                  && slots.get(SECOND_CELL).is(Items.DIRT)
                  && slots.get(SECOND_CELL).getCount() == DIRT_COUNT;
            });
    expect(moved, name + ": the dirt moved from the first cell to the second");

    middleClickBoxSlot(context);
    expect(!context.computeOnClient(PinnedStationsClientGameTest::boxPinned), name + ": unpinned");
  }

  /**
   * With an item in the anvil the name field takes the keys: Left moves its cursor (not the box's
   * selection) and a number key over a window cell does not swap it with the hotbar.
   */
  private static void anvilNameKeepsItsKeys(
      ClientGameTestContext context, TestServerContext server) {
    openBlock(context, server, new Station(Blocks.ANVIL, AnvilScreen.class));
    middleClickBoxSlot(context);
    expect(context.computeOnClient(PinnedStationsClientGameTest::boxPinned), "anvil: pinned");
    server.runOnServer(
        minecraftServer ->
            player(minecraftServer)
                .containerMenu
                .getSlot(AnvilMenu.INPUT_SLOT)
                .set(new ItemStack(Items.STICK)));
    context.waitFor(
        mc -> mc.gui.screen() instanceof AnvilScreen screen && StorageInput.isTyping(screen),
        SERVER_TIMEOUT_TICKS);

    hoverBoxSlot(context);
    int before = context.computeOnClient(PinnedStationsClientGameTest::nameCursor);
    context.getInput().pressKey(InputConstants.KEY_LEFT);
    context.waitTick();
    int after = context.computeOnClient(PinnedStationsClientGameTest::nameCursor);
    expect(
        after == before - 1, "anvil: Left moved the name cursor (" + before + " -> " + after + ")");

    hoverCell(context, FIRST_CELL);
    context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
    context.waitTicks(2);
    boolean kept =
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return player.getInventory().getItem(0).isEmpty()
                  && boxSlots(player).getFirst().getCount() == DIRT_COUNT;
            });
    expect(kept, "anvil: the number key typed instead of swapping");
  }

  /**
   * The crafting screen as Handy Tables opens its portable table pins at GUI height 360 and 240,
   * with or without the recipe book. At 240 with the book open the window goes above the screen at
   * the smallest scale ({@code PinnedLayout#MIN_SCALE}); at half size it found no room there and
   * the middle click showed nothing.
   */
  private static void craftingLikeHandyTables(
      ClientGameTestContext context, TestServerContext server) {
    giveBox(server);
    expectCraftingPin(context, server, true, "GUI 360, recipe book open");
    expectCraftingPin(context, server, false, "GUI 360, recipe book closed");

    // Larger window first: the GUI scale option refuses a scale the window is too small for.
    context.getInput().resizeWindow(SMALL_WIDTH, SMALL_HEIGHT);
    setGuiScale(context, SMALL_GUI_SCALE);
    expectCraftingPin(context, server, true, "GUI 240, recipe book open");
    expectCraftingPin(context, server, false, "GUI 240, recipe book closed");

    setGuiScale(context, START_GUI_SCALE);
    context.getInput().resizeWindow(START_WIDTH, START_HEIGHT);
    context.waitTick();
  }

  private static void setGuiScale(ClientGameTestContext context, int scale) {
    context.runOnClient(
        mc -> {
          mc.options.guiScale().set(scale);
          mc.resizeGui();
        });
    context.waitTick();
    expect(
        context.computeOnClient(mc -> mc.getWindow().getGuiScale()) == scale,
        "GUI scale " + scale + " applied");
  }

  private static void expectCraftingPin(
      ClientGameTestContext context, TestServerContext server, boolean recipeBook, String name) {
    context.runOnClient(
        mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, recipeBook));
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          BlockPos pos = placeBeside(player, Blocks.CRAFTING_TABLE);
          ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
          // Handy Tables' StationRequests#open: the inventory menu closes without a close packet.
          player.doCloseContainer();
          player.openMenu(
              new SimpleMenuProvider(
                  (containerId, inventory, opener) ->
                      new CraftingMenu(containerId, inventory, access) {},
                  Component.translatable("container.crafting")));
        });
    context.waitForScreen(CraftingScreen.class);
    context.waitTick();
    middleClickBoxSlot(context);
    expect(context.computeOnClient(PinnedStationsClientGameTest::boxPinned), name + ": pinned");
    middleClickBoxSlot(context);
    close(context, server);
  }

  private static void giveBox(TestServerContext server) {
    server.runOnServer(
        minecraftServer -> {
          ItemStack box = new ItemStack(Items.SHULKER_BOX);
          box.set(
              DataComponents.CONTAINER,
              ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT, DIRT_COUNT))));
          ServerPlayer player = player(minecraftServer);
          player.getInventory().clearContent();
          player.getInventory().setItem(BOX_INVENTORY_SLOT, box);
        });
  }

  private static void openBlock(
      ClientGameTestContext context, TestServerContext server, Station station) {
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          BlockPos pos = placeBeside(player, station.block());
          ServerLevel level = player.level();
          player.openMenu(level.getBlockState(pos).getMenuProvider(level, pos));
        });
    context.waitForScreen(station.screen());
    context.waitTick();
  }

  private static BlockPos placeBeside(ServerPlayer player, Block block) {
    BlockPos pos = player.blockPosition().east();
    ServerLevel level = player.level();
    level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos, block.defaultBlockState());
    return pos;
  }

  /**
   * Closes the screen and waits for the server to close the menu too: vanilla {@code
   * ServerGamePacketListenerImpl#handleContainerClose} ignores the container id, so a close
   * arriving after the next menu opened would close that one instead.
   */
  private static void close(ClientGameTestContext context, TestServerContext server) {
    context.runOnClient(mc -> mc.player.closeContainer());
    context.waitForScreen(null);
    server.waitFor(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          return player.containerMenu == player.inventoryMenu;
        },
        SERVER_TIMEOUT_TICKS);
  }

  private static void middleClickBoxSlot(ClientGameTestContext context) {
    hoverBoxSlot(context);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();
  }

  private static void hoverBoxSlot(ClientGameTestContext context) {
    double[] at = context.computeOnClient(PinnedStationsClientGameTest::boxSlotCentre);
    context.getInput().setCursorPos(at[0], at[1]);
    context.waitTick();
  }

  private static void clickCell(ClientGameTestContext context, int cell) {
    hoverCell(context, cell);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTick();
  }

  /** The window is the first one, at full size to the right of the screen, top-aligned with it. */
  private static void hoverCell(ClientGameTestContext context, int cell) {
    double[] at = context.computeOnClient(mc -> cellCentre(mc, cell));
    context.getInput().setCursorPos(at[0], at[1]);
    context.waitTick();
  }

  private static boolean boxPinned(Minecraft minecraft) {
    return PinnedWindows.isPinned(boxSlot(minecraft));
  }

  private static int nameCursor(Minecraft minecraft) {
    if (!(minecraft.gui.screen() instanceof AnvilScreen screen)
        || !(screen.getFocused() instanceof EditBox name)) {
      throw new AssertionError("no anvil name field");
    }
    return name.getCursorPosition();
  }

  private static double[] boxSlotCentre(Minecraft minecraft) {
    AbstractContainerScreenAccessor screen = accessor(minecraft);
    Slot slot = boxSlot(minecraft);
    return toWindow(
        minecraft,
        screen.shulkertoolkit$leftPos() + slot.x + HALF_SLOT,
        screen.shulkertoolkit$topPos() + slot.y + HALF_SLOT);
  }

  private static double[] cellCentre(Minecraft minecraft, int cell) {
    AbstractContainerScreenAccessor screen = accessor(minecraft);
    int left =
        screen.shulkertoolkit$leftPos() + screen.shulkertoolkit$imageWidth() + PinnedLayout.GAP;
    int top = Math.max(PinnedLayout.MARGIN, screen.shulkertoolkit$topPos());
    return toWindow(
        minecraft,
        left + ContainerTooltip.BORDER + cell * ContainerTooltip.SLOT + ContainerTooltip.SLOT / 2,
        top + ContainerTooltip.TITLED_TOP + ContainerTooltip.SLOT / 2);
  }

  private static double[] toWindow(Minecraft minecraft, int guiX, int guiY) {
    Window window = minecraft.getWindow();
    double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
    return new double[] {guiX * scale, guiY * scale};
  }

  private static AbstractContainerScreenAccessor accessor(Minecraft minecraft) {
    if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)
        || !((Object) screen instanceof AbstractContainerScreenAccessor access)) {
      throw new AssertionError("no container screen");
    }
    return access;
  }

  private static Slot boxSlot(Minecraft minecraft) {
    if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
      throw new AssertionError("no container screen");
    }
    for (Slot slot : screen.getMenu().slots) {
      if (slot.container == minecraft.player.getInventory()
          && slot.getContainerSlot() == BOX_INVENTORY_SLOT) {
        return slot;
      }
    }
    throw new AssertionError("the box's slot is not in the menu");
  }

  private static List<ItemStack> boxSlots(ServerPlayer player) {
    NonNullList<ItemStack> slots = NonNullList.withSize(StorageKind.SHULKER_SLOTS, ItemStack.EMPTY);
    player
        .getInventory()
        .getItem(BOX_INVENTORY_SLOT)
        .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
        .copyInto(slots);
    return slots;
  }

  private static ServerPlayer player(MinecraftServer minecraftServer) {
    return minecraftServer.getPlayerList().getPlayers().getFirst();
  }

  private static void expect(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
