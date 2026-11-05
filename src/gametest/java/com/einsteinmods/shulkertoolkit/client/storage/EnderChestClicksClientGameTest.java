package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/**
 * The ender chest on the real client and integrated server, where the mixin's gate is open and the
 * client predicts on its own chest menu, which the server corrects: in the ender menu a stored
 * ender chest extracts or takes itself, a cursor ender chest extracts into an empty slot and a
 * shulker box goes in and out, and both sides agree after each click; a stack shows its tooltip,
 * moves its selection with the wheel, pins a window that works, splits with a right click and loses
 * its window with the stack; with the option off clicks and tooltips are vanilla but windows still
 * open.
 *
 * <p>Runs muted, at 30 FPS, in a 640 x 360 window at GUI scale 1 (see {@code build.gradle}).
 */
public final class EnderChestClicksClientGameTest implements FabricClientGameTest {
  private static final int ENDER_ITEM_SLOT = 3;
  private static final int DIAMOND_SLOT = 5;
  private static final int EMPTY_SLOT = 6;
  private static final int DIAMONDS = 5;
  private static final int INVENTORY_SLOT = 9;
  private static final int SPARE_SLOT = 20;
  private static final int STACK_OF_FOUR = 4;
  private static final int STONE_COUNT = 20;
  private static final int HALF_SLOT = 8;
  private static final int WHEEL_DOWN = -1;
  private static final int SERVER_TIMEOUT_TICKS = 40;
  private static final int BUTTON_OVERLAP = 4;
  private static final int BUTTON_WIDTH = 16;

  @Override
  public void runTest(ClientGameTestContext context) {
    ClientShulkerState state = ShulkerToolkitClient.state();
    ClientPreferences original = context.computeOnClient(mc -> state.preferences());
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getConnection().waitForChunksRender();
      TestServerContext server = world.getServer();
      server.runOnServer(minecraftServer -> player(minecraftServer).setGameMode(GameType.SURVIVAL));
      context.runOnClient(mc -> state.update(enderChestInteractions(original, true)));
      context.waitTicks(2);

      storedEnderChestIsPickedUp(context, server);
      storedEnderChestKeepsItsContents(context, server);
      cursorEnderChestIsPlaced(context, server);
      middleClickInTheEnderMenuShowsANotice(context, server);
      shulkerBoxGoesInAndOut(context, server);
      stackIsViewableAndItsWindowTakesOver(context, server);
      windowBoxKeepsItsRightClick(context, server);
      windowsClearButtonsBesideTheMenu(context, server);
      secondEnderWindowShowsANotice(context, server);

      context.runOnClient(mc -> state.update(enderChestInteractions(original, false)));
      context.waitTicks(2);
      optionOffIsVanillaButWindowsWork(context, server);
    } finally {
      context.runOnClient(mc -> state.update(original));
    }
  }

  private static void storedEnderChestIsPickedUp(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer ->
            player(minecraftServer)
                .getEnderChestInventory()
                .setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST)));
    openEnderMenu(context, server);

    click(context, ENDER_ITEM_SLOT, InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);

    expect(
        server.computeOnServer(
            minecraftServer -> owned(player(minecraftServer), Items.ENDER_CHEST) == 1),
        "the stored ender chest stays one on the server");
    expect(
        context.computeOnClient(
            mc ->
                menuSlot(mc, ENDER_ITEM_SLOT).getItem().isEmpty()
                    && carried(mc).is(Items.ENDER_CHEST)),
        "the client agrees: empty slot, ender chest on the cursor");
    close(context, server);
  }

  private static void cursorEnderChestIsPlaced(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer ->
            player(minecraftServer)
                .getEnderChestInventory()
                .setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS)));
    openEnderMenu(context, server);
    pickUpEnderChest(context, server);

    click(context, EMPTY_SLOT, InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer ->
            player(minecraftServer)
                .getEnderChestInventory()
                .getItem(EMPTY_SLOT)
                .is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);

    expect(
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return player.getEnderChestInventory().getItem(DIAMOND_SLOT).getCount() == DIAMONDS
                  && player.containerMenu.getCarried().isEmpty();
            }),
        "the ender chest was placed and the diamonds stayed");
    expect(
        context.computeOnClient(
            mc ->
                menuSlot(mc, EMPTY_SLOT).getItem().is(Items.ENDER_CHEST) && carried(mc).isEmpty()),
        "the client agrees");
    close(context, server);
  }

  private static void storedEnderChestKeepsItsContents(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player
              .getEnderChestInventory()
              .setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
          player
              .getEnderChestInventory()
              .setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
        });
    openEnderMenu(context, server);

    click(context, ENDER_ITEM_SLOT, InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);

    expect(
        context.computeOnClient(
            mc ->
                menuSlot(mc, ENDER_ITEM_SLOT).getItem().isEmpty()
                    && menuSlot(mc, DIAMOND_SLOT).getItem().getCount() == DIAMONDS
                    && carried(mc).is(Items.ENDER_CHEST)),
        "the ender chest is on the cursor, the diamonds stay, the client agrees");
    expect(
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return owned(player, Items.ENDER_CHEST) == 1
                  && owned(player, Items.DIAMOND) == DIAMONDS;
            }),
        "nothing was duplicated or lost");
    close(context, server);
  }

  private static void middleClickInTheEnderMenuShowsANotice(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player
              .getEnderChestInventory()
              .setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
          player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
        });
    openEnderMenu(context, server);
    hoverInventorySlot(context);
    expect(
        context.computeOnClient(mc -> StorageTooltips.imageFor(inventoryStack(mc)) == null),
        "no contents tooltip in the ender menu");

    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    expect(context.computeOnClient(mc -> PinnedWindows.isNoticeShown()), "the notice shows");
    context.waitTick();
    context.takeScreenshot("ender-chest-already-open");
    expect(
        !context.computeOnClient(EnderChestClicksClientGameTest::stackPinned), "no window opens");
    close(context, server);
    expect(!context.computeOnClient(mc -> PinnedWindows.isNoticeShown()), "and goes with the menu");
  }

  private static void shulkerBoxGoesInAndOut(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ItemStack box = new ItemStack(Items.SHULKER_BOX);
          box.set(
              DataComponents.CONTAINER,
              ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, STONE_COUNT))));
          player(minecraftServer).getInventory().setItem(INVENTORY_SLOT, box);
        });
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    pickUpEnderChest(context, server);
    int menuSlot = context.computeOnClient(mc -> inventorySlot(mc).index);

    click(context, menuSlot, InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer ->
            player(minecraftServer).getEnderChestInventory().countItem(Items.SHULKER_BOX) == 1,
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);
    expect(context.computeOnClient(mc -> inventoryStack(mc).isEmpty()), "the box left its slot");

    click(context, menuSlot, InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer ->
            player(minecraftServer).getInventory().getItem(INVENTORY_SLOT).is(Items.SHULKER_BOX),
        SERVER_TIMEOUT_TICKS);
    expect(
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return player.getEnderChestInventory().isEmpty()
                  && player
                          .getInventory()
                          .getItem(INVENTORY_SLOT)
                          .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                          .nonEmptyItemCopyStream()
                          .mapToInt(ItemStack::getCount)
                          .sum()
                      == STONE_COUNT;
            }),
        "the box came back with its contents");
    close(context, server);
  }

  private static void stackIsViewableAndItsWindowTakesOver(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player.getEnderChestInventory().setItem(0, new ItemStack(Items.STONE, STONE_COUNT));
          player
              .getEnderChestInventory()
              .setItem(DIAMOND_SLOT, new ItemStack(Items.DIAMOND, DIAMONDS));
          player
              .getInventory()
              .setItem(INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST, STACK_OF_FOUR));
          EnderChestSync.sendContents(player);
        });
    context.waitFor(
        mc -> mc.player.getEnderChestInventory().getItem(DIAMOND_SLOT).is(Items.DIAMOND),
        SERVER_TIMEOUT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    hoverInventorySlot(context);

    expect(
        context.computeOnClient(mc -> StorageTooltips.imageFor(inventoryStack(mc)) != null),
        "a stack shows the grid");
    int before = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
    context.getInput().scroll(WHEEL_DOWN);
    context.waitTick();
    int after = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
    expect(after != before, "the wheel moves the selection (" + before + " -> " + after + ")");

    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();
    expect(
        context.computeOnClient(EnderChestClicksClientGameTest::stackPinned), "middle click pins");
    expect(
        context.computeOnClient(mc -> inventoryStack(mc).getTooltipImage().isEmpty()),
        "no contents tooltip while its window is open");
    int beforeWindow = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
    context.getInput().scroll(WHEEL_DOWN);
    context.waitTick();
    int afterWindow = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
    expect(afterWindow != beforeWindow, "the wheel still moves the selection, in the window");
    context.takeScreenshot("window-driven-selection");

    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer -> !player(minecraftServer).containerMenu.getCarried().isEmpty(),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);
    expect(
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return !player.containerMenu.getCarried().is(Items.ENDER_CHEST)
                  && player.getInventory().getItem(INVENTORY_SLOT).getCount() == STACK_OF_FOUR;
            }),
        "with a selection, a right click takes it out and the stack stays whole");
    expect(
        context.computeOnClient(EnderChestClicksClientGameTest::stackPinned), "the window stays");
    expect(
        server.computeOnServer(
            minecraftServer -> {
              ServerPlayer player = player(minecraftServer);
              return owned(player, Items.ENDER_CHEST) == STACK_OF_FOUR
                  && owned(player, Items.DIAMOND) == DIAMONDS
                  && owned(player, Items.STONE) == STONE_COUNT;
            }),
        "nothing was duplicated or lost");
    close(context, server);
  }

  private static void windowBoxKeepsItsRightClick(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          ItemStack box = new ItemStack(Items.SHULKER_BOX);
          box.set(
              DataComponents.CONTAINER,
              ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE, STONE_COUNT))));
          player.getInventory().setItem(INVENTORY_SLOT, box);
          player.getInventory().setItem(SPARE_SLOT, box.copy());
        });
    context.waitFor(
        mc -> mc.player.getInventory().getItem(SPARE_SLOT).is(Items.SHULKER_BOX),
        SERVER_TIMEOUT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    hoverInventorySlot(context);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();

    expect(context.computeOnClient(mc -> PinnedWindows.locks(inventoryStack(mc))), "this box is");
    expect(
        !context.computeOnClient(
            mc -> PinnedWindows.locks(mc.player.getInventory().getItem(SPARE_SLOT))),
        "the other one is not");
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.STONE),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);
    expect(
        context.computeOnClient(EnderChestClicksClientGameTest::stackPinned),
        "a right click takes the stone out and the window stays");
    close(context, server);
  }

  /** Every ender chest shows the same contents: a second window would repeat the first. */
  private static void secondEnderWindowShowsANotice(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
          player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
          player.getInventory().setItem(SPARE_SLOT, new ItemStack(Items.ENDER_CHEST));
          EnderChestSync.sendContents(player);
        });
    context.waitFor(
        mc -> mc.player.getInventory().getItem(SPARE_SLOT).is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    hoverInventorySlot(context);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();
    expect(context.computeOnClient(EnderChestClicksClientGameTest::stackPinned), "the first opens");

    int spareMenuSlot =
        context.computeOnClient(
            mc ->
                PinnedWindowClicks.inventorySlot(mc.player, screen(mc).getMenu(), SPARE_SLOT)
                    .index);
    click(context, spareMenuSlot, InputConstants.MOUSE_BUTTON_MIDDLE);
    expect(context.computeOnClient(mc -> PinnedWindows.isNoticeShown()), "the second one tells");
    expect(
        !context.computeOnClient(mc -> PinnedWindows.isPinned(menuSlot(mc, spareMenuSlot))),
        "and does not open");

    hoverInventorySlot(context);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();
    expect(
        !context.computeOnClient(EnderChestClicksClientGameTest::stackPinned),
        "middle click on the first one still closes it");
    close(context, server);
  }

  /** As Inventory Management's sort buttons: straddling the menu's right edge. */
  private static void windowsClearButtonsBesideTheMenu(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer ->
            player(minecraftServer)
                .getInventory()
                .setItem(INVENTORY_SLOT, new ItemStack(Items.SHULKER_BOX)));
    context.waitFor(
        mc -> mc.player.getInventory().getItem(INVENTORY_SLOT).is(Items.SHULKER_BOX),
        SERVER_TIMEOUT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    int buttonRight =
        context.computeOnClient(
            mc -> {
              AbstractContainerScreenAccessor access = accessor(mc);
              Button button =
                  Button.builder(Component.empty(), pressed -> {})
                      .bounds(
                          access.shulkertoolkit$leftPos()
                              + access.shulkertoolkit$imageWidth()
                              - BUTTON_OVERLAP,
                          access.shulkertoolkit$topPos(),
                          BUTTON_WIDTH,
                          BUTTON_WIDTH)
                      .build();
              Screens.getWidgets(screen(mc)).add(button);
              return button.getRight();
            });
    hoverInventorySlot(context);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();

    expect(context.computeOnClient(EnderChestClicksClientGameTest::stackPinned), "a window opens");
    expect(
        context.computeOnClient(mc -> PinnedWindows.firstWindowX()) > buttonRight,
        "the window goes past the button");
    close(context, server);
  }

  private static void optionOffIsVanillaButWindowsWork(
      ClientGameTestContext context, TestServerContext server) {
    reset(context, server);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
          player.getInventory().setItem(INVENTORY_SLOT, new ItemStack(Items.ENDER_CHEST));
          EnderChestSync.sendContents(player);
        });
    context.waitFor(
        mc -> mc.player.getEnderChestInventory().getItem(0).is(Items.DIAMOND),
        SERVER_TIMEOUT_TICKS);
    context.getInput().pressKey(options -> options.keyInventory);
    context.waitForScreen(InventoryScreen.class);
    hoverInventorySlot(context);
    expect(
        context.computeOnClient(mc -> StorageTooltips.imageFor(inventoryStack(mc)) == null),
        "option off: vanilla tooltip");

    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    expect(
        server.computeOnServer(
            minecraftServer ->
                player(minecraftServer).getEnderChestInventory().getItem(0).getCount() == DIAMONDS),
        "option off: a right click picks the item up, nothing comes out of the chest");
    context.waitTicks(2);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().isEmpty(),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);

    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
    context.waitTick();
    expect(
        context.computeOnClient(EnderChestClicksClientGameTest::stackPinned),
        "option off: middle click still pins");
    close(context, server);
  }

  private static ClientPreferences enderChestInteractions(
      ClientPreferences preferences, boolean enabled) {
    return new ClientPreferences(
        preferences.itemStorageTooltip(),
        preferences.itemStorageColors(),
        preferences.itemStorageHighlightSprite(),
        preferences.verticalTooltipScrolling(),
        preferences.iconMarker(),
        preferences.singleItemMovement(),
        preferences.itemHeldByCursorTooltip(),
        preferences.controlScheme(),
        preferences.nestedItemTooltips(),
        preferences.bundleInteractions(),
        enabled,
        preferences.pickMode(),
        preferences.autoRefill(),
        preferences.litematicaAutoTakeOut(),
        preferences.pickupOverflowToBoxes(),
        preferences.pickupDirectToBox(),
        preferences.craftFromBoxes(),
        preferences.craftDepth(),
        preferences.pinnedWindows(),
        preferences.pinnedWindowSounds(),
        preferences.maxPinnedWindows(),
        preferences.showEmptyBoxes());
  }

  private static void reset(ClientGameTestContext context, TestServerContext server) {
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player.getInventory().clearContent();
          player.getEnderChestInventory().clearContent();
          player.containerMenu.setCarried(ItemStack.EMPTY);
          StorageStates.of(player).setSelectedItem(StorageGrid.NO_ITEM);
        });
    context.runOnClient(mc -> StorageStates.of(mc.player).setSelectedItem(StorageGrid.NO_ITEM));
  }

  /**
   * Takes an ender chest with a real click, as a player does: until a press reaches vanilla, a new
   * screen skips the next release ({@code skipNextRelease}), and the drag hook keeps the press with
   * a container item on the cursor, so a cursor stack set by the server would lose its first click.
   */
  private static void pickUpEnderChest(ClientGameTestContext context, TestServerContext server) {
    server.runOnServer(
        minecraftServer ->
            player(minecraftServer)
                .getInventory()
                .setItem(SPARE_SLOT, new ItemStack(Items.ENDER_CHEST)));
    context.waitFor(
        mc -> mc.player.getInventory().getItem(SPARE_SLOT).is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    int menuSlot =
        context.computeOnClient(
            mc ->
                PinnedWindowClicks.inventorySlot(mc.player, screen(mc).getMenu(), SPARE_SLOT)
                    .index);
    click(context, menuSlot, InputConstants.MOUSE_BUTTON_LEFT);
    server.waitFor(
        minecraftServer -> player(minecraftServer).containerMenu.getCarried().is(Items.ENDER_CHEST),
        SERVER_TIMEOUT_TICKS);
    context.waitTicks(2);
  }

  private static void openEnderMenu(ClientGameTestContext context, TestServerContext server) {
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          player.openMenu(
              new SimpleMenuProvider(
                  (containerId, inventory, menuPlayer) ->
                      ChestMenu.threeRows(
                          containerId, inventory, menuPlayer.getEnderChestInventory()),
                  Component.translatable("container.enderchest")));
        });
    context.waitForScreen(ContainerScreen.class);
    context.waitTicks(2);
  }

  /** Closes the screen and waits for the server's menu to close (see the connection map). */
  private static void close(ClientGameTestContext context, TestServerContext server) {
    server.runOnServer(
        minecraftServer -> player(minecraftServer).containerMenu.setCarried(ItemStack.EMPTY));
    context.runOnClient(mc -> mc.player.closeContainer());
    context.waitForScreen(null);
    server.waitFor(
        minecraftServer -> {
          ServerPlayer player = player(minecraftServer);
          return player.containerMenu == player.inventoryMenu;
        },
        SERVER_TIMEOUT_TICKS);
  }

  private static void click(ClientGameTestContext context, int menuSlot, int button) {
    double[] at = context.computeOnClient(mc -> slotCentre(mc, menuSlot(mc, menuSlot)));
    context.getInput().setCursorPos(at[0], at[1]);
    context.waitTick();
    context.getInput().pressMouse(button);
    context.waitTick();
  }

  private static void hoverInventorySlot(ClientGameTestContext context) {
    double[] at = context.computeOnClient(mc -> slotCentre(mc, inventorySlot(mc)));
    context.getInput().setCursorPos(at[0], at[1]);
    context.waitTick();
  }

  private static boolean stackPinned(Minecraft minecraft) {
    return PinnedWindows.isPinned(inventorySlot(minecraft));
  }

  private static ItemStack inventoryStack(Minecraft minecraft) {
    return minecraft.player.getInventory().getItem(INVENTORY_SLOT);
  }

  private static ItemStack carried(Minecraft minecraft) {
    return screen(minecraft).getMenu().getCarried();
  }

  private static double[] slotCentre(Minecraft minecraft, Slot slot) {
    AbstractContainerScreenAccessor access = accessor(minecraft);
    Window window = minecraft.getWindow();
    double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
    return new double[] {
      (access.shulkertoolkit$leftPos() + slot.x + HALF_SLOT) * scale,
      (access.shulkertoolkit$topPos() + slot.y + HALF_SLOT) * scale
    };
  }

  private static Slot menuSlot(Minecraft minecraft, int index) {
    return screen(minecraft).getMenu().getSlot(index);
  }

  private static Slot inventorySlot(Minecraft minecraft) {
    for (Slot slot : screen(minecraft).getMenu().slots) {
      if (slot.container == minecraft.player.getInventory()
          && slot.getContainerSlot() == INVENTORY_SLOT) {
        return slot;
      }
    }
    throw new AssertionError("inventory slot " + INVENTORY_SLOT + " is not in the menu");
  }

  private static AbstractContainerScreen<?> screen(Minecraft minecraft) {
    if (minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen) {
      return screen;
    }
    throw new AssertionError("no container screen");
  }

  private static AbstractContainerScreenAccessor accessor(Minecraft minecraft) {
    if ((Object) screen(minecraft) instanceof AbstractContainerScreenAccessor access) {
      return access;
    }
    throw new AssertionError("screen accessor not applied");
  }

  private static int owned(ServerPlayer player, Item item) {
    return player.containerMenu.getCarried().is(item)
        ? player.containerMenu.getCarried().getCount() + stored(player, item)
        : stored(player, item);
  }

  private static int stored(ServerPlayer player, Item item) {
    return player.getInventory().countItem(item) + player.getEnderChestInventory().countItem(item);
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
