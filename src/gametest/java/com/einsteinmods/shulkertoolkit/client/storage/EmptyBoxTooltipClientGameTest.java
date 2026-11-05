package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.config.ShulkerToolkitSettingsScreen;
import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

/**
 * The show empty boxes option on the real client: off, an empty shulker box or ender chest has no
 * contents tooltip; on, both show their empty grid at once (same stack, no other hover between); a
 * stack of two boxes still shows nothing, a stack of two ender chests shows the grid, and empty
 * bundles stay vanilla's with or without bundle interactions. {@link StorageTooltips#imageFor}
 * needs the client player and the render thread.
 *
 * <p>Then the same in the survival inventory screen with the mouse over each box, with screenshots
 * (in the run directory's {@code screenshots/}) to judge the drawn tooltip: the option in the
 * settings screen (English and German), off, on (plain, red, ender chest), a box with items and the
 * wheel moving its selection, and off again on the same hovered box. Taken at GUI scale 2 in an 854
 * x 480 window, then the start size and scale are restored.
 */
public final class EmptyBoxTooltipClientGameTest implements FabricClientGameTest {
  private static final int STACK_OF_TWO = 2;
  private static final int UNDYED_PANEL = ARGB.opaque(0x976997);
  private static final int NO_TINT = -1;
  private static final int PLAIN_BOX_SLOT = 9;
  private static final int RED_BOX_SLOT = 10;
  private static final int ENDER_CHEST_SLOT = 11;
  private static final int FULL_BOX_SLOT = 12;
  private static final int DIRT_COUNT = 13;
  private static final int STONE_COUNT = 5;
  private static final int HALF_SLOT = 8;
  private static final int WHEEL_DOWN = -1;
  private static final int TIMEOUT_TICKS = 40;
  private static final int SHOT_GUI_SCALE = 2;
  private static final int SHOT_WIDTH = 854;
  private static final int SHOT_HEIGHT = 480;
  private static final int START_GUI_SCALE = 1;
  private static final int START_WIDTH = 640;
  private static final int START_HEIGHT = 360;
  private static final String ENGLISH = "en_us";
  private static final String GERMAN = "de_de";

  @Override
  public void runTest(ClientGameTestContext context) {
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getConnection().waitForChunksRender();
      context.runOnClient(EmptyBoxTooltipClientGameTest::checkEmptyBoxes);
      checkInInventory(context, world.getServer());
    }
  }

  private static void checkInInventory(ClientGameTestContext context, TestServerContext server) {
    ClientShulkerState state = ShulkerToolkitClient.state();
    ClientPreferences original = context.computeOnClient(mc -> state.preferences());
    ClientPreferences withEnderChest = interactions(original, false, true);
    server.runOnServer(
        minecraftServer -> {
          ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
          player.setGameMode(GameType.SURVIVAL);
          player.getInventory().clearContent();
          player.getInventory().setItem(PLAIN_BOX_SLOT, new ItemStack(Items.SHULKER_BOX));
          player.getInventory().setItem(RED_BOX_SLOT, new ItemStack(Items.DYED_SHULKER_BOX.red()));
          player.getInventory().setItem(ENDER_CHEST_SLOT, new ItemStack(Items.ENDER_CHEST));
          ItemStack full = new ItemStack(Items.SHULKER_BOX);
          full.set(
              DataComponents.CONTAINER,
              ItemContainerContents.fromItems(
                  List.of(
                      new ItemStack(Items.DIRT, DIRT_COUNT),
                      new ItemStack(Items.STONE, STONE_COUNT))));
          player.getInventory().setItem(FULL_BOX_SLOT, full);
        });
    context.waitFor(
        mc -> mc.player.getInventory().getItem(FULL_BOX_SLOT).is(Items.SHULKER_BOX), TIMEOUT_TICKS);
    context.getInput().resizeWindow(SHOT_WIDTH, SHOT_HEIGHT);
    setGuiScale(context, SHOT_GUI_SCALE);
    try {
      settingsScreen(context, state, ENGLISH);
      settingsScreen(context, state, GERMAN);
      setLanguage(context, ENGLISH);

      context.getInput().pressKey(options -> options.keyInventory);
      context.waitForScreen(InventoryScreen.class);

      setShowEmptyBoxes(context, state, withEnderChest, false);
      hover(context, PLAIN_BOX_SLOT);
      context.takeScreenshot("empty-box-off-plain");

      setShowEmptyBoxes(context, state, withEnderChest, true);
      context.waitTick();
      context.takeScreenshot("empty-box-on-plain");
      hover(context, RED_BOX_SLOT);
      context.takeScreenshot("empty-box-on-red");
      hover(context, ENDER_CHEST_SLOT);
      context.takeScreenshot("empty-box-on-ender-chest");

      hover(context, FULL_BOX_SLOT);
      context.takeScreenshot("full-box-before-wheel");
      int before = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
      context.getInput().scroll(WHEEL_DOWN);
      context.waitTick();
      int after = context.computeOnClient(mc -> StorageStates.of(mc.player).selectedItem());
      expect(after != before, "the wheel moved the selection (" + before + " -> " + after + ")");
      context.takeScreenshot("full-box-after-wheel");

      hover(context, PLAIN_BOX_SLOT);
      context.takeScreenshot("empty-box-on-plain-again");
      setShowEmptyBoxes(context, state, withEnderChest, false);
      context.waitTick();
      expect(
          context.computeOnClient(mc -> hoveredSlot(mc) == inventorySlot(mc, PLAIN_BOX_SLOT)),
          "still hovering the plain box");
      context.takeScreenshot("empty-box-off-again-same-hover");

      context.runOnClient(mc -> mc.player.closeContainer());
      context.waitForScreen(null);
    } finally {
      context.runOnClient(mc -> state.update(original));
      setGuiScale(context, START_GUI_SCALE);
      context.getInput().resizeWindow(START_WIDTH, START_HEIGHT);
      context.waitTick();
    }
  }

  /**
   * The settings screen as opened from its key, in {@code language}, with the option at its
   * default.
   */
  private static void settingsScreen(
      ClientGameTestContext context, ClientShulkerState state, String language) {
    setLanguage(context, language);
    context.runOnClient(
        mc ->
            state.update(
                state
                    .preferences()
                    .withShowEmptyBoxes(ClientPreferences.DEFAULTS.showEmptyBoxes())));
    expect(!ClientPreferences.DEFAULTS.showEmptyBoxes(), "show empty boxes is off by default");
    context.runOnClient(
        mc ->
            mc.gui.setScreen(
                new ShulkerToolkitSettingsScreen(null, state, ShulkerToolkitClient.keyMappings())));
    context.waitForScreen(ShulkerToolkitSettingsScreen.class);
    context.waitTick();
    context.takeScreenshot("settings-" + language);
    context.runOnClient(mc -> mc.gui.setScreen(null));
    context.waitForScreen(null);
  }

  private static void setShowEmptyBoxes(
      ClientGameTestContext context,
      ClientShulkerState state,
      ClientPreferences original,
      boolean show) {
    context.runOnClient(mc -> state.update(original.withShowEmptyBoxes(show)));
  }

  /** Only the language file reload, not every resource pack. */
  private static void setLanguage(ClientGameTestContext context, String language) {
    context.runOnClient(
        mc -> {
          mc.options.languageCode = language;
          mc.getLanguageManager().setSelected(language);
          mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        });
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

  private static void hover(ClientGameTestContext context, int inventorySlot) {
    double[] at = context.computeOnClient(mc -> slotCentre(mc, inventorySlot));
    context.getInput().setCursorPos(at[0], at[1]);
    context.waitTick();
    expect(
        context.computeOnClient(mc -> hoveredSlot(mc) == inventorySlot(mc, inventorySlot)),
        "hovering inventory slot " + inventorySlot);
  }

  private static double[] slotCentre(Minecraft minecraft, int inventorySlot) {
    AbstractContainerScreenAccessor screen = accessor(minecraft);
    Slot slot = inventorySlot(minecraft, inventorySlot);
    Window window = minecraft.getWindow();
    double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
    return new double[] {
      (screen.shulkertoolkit$leftPos() + slot.x + HALF_SLOT) * scale,
      (screen.shulkertoolkit$topPos() + slot.y + HALF_SLOT) * scale
    };
  }

  private static Slot hoveredSlot(Minecraft minecraft) {
    return accessor(minecraft).shulkertoolkit$hoveredSlot();
  }

  private static AbstractContainerScreenAccessor accessor(Minecraft minecraft) {
    if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)
        || !((Object) screen instanceof AbstractContainerScreenAccessor access)) {
      throw new AssertionError("no container screen");
    }
    return access;
  }

  private static Slot inventorySlot(Minecraft minecraft, int inventorySlot) {
    if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
      throw new AssertionError("no container screen");
    }
    for (Slot slot : screen.getMenu().slots) {
      if (slot.container == minecraft.player.getInventory()
          && slot.getContainerSlot() == inventorySlot) {
        return slot;
      }
    }
    throw new AssertionError("inventory slot " + inventorySlot + " is not in the menu");
  }

  private static void checkEmptyBoxes(Minecraft minecraft) {
    ClientShulkerState state = StorageInput.state();
    if (state == null) {
      throw new AssertionError("client state not installed");
    }
    expect(
        minecraft.player != null && minecraft.player.getEnderChestInventory().isEmpty(),
        "empty ender chest");
    ClientPreferences original = state.preferences();
    ItemStack shulkerBox = new ItemStack(Items.SHULKER_BOX);
    ItemStack redShulkerBox = new ItemStack(Items.DYED_SHULKER_BOX.red());
    ItemStack enderChest = new ItemStack(Items.ENDER_CHEST);
    ItemStack bundle = new ItemStack(Items.BUNDLE);
    try {
      ClientPreferences hidden = interactions(original, false, true).withShowEmptyBoxes(false);
      state.update(hidden);
      expect(StorageTooltips.imageFor(shulkerBox) == null, "off: empty shulker box");
      expect(StorageTooltips.imageFor(enderChest) == null, "off: empty ender chest");

      state.update(withoutColors(hidden));
      expect(
          StorageTooltips.panelColor(StorageKind.SHULKER_BOX, shulkerBox) == NO_TINT,
          "colours off: no tint");

      ClientPreferences vanillaEnderChest =
          interactions(original, false, false).withShowEmptyBoxes(true);
      state.update(vanillaEnderChest);
      expect(StorageTooltips.imageFor(enderChest) == null, "ender chest interactions off");
      expect(StorageTooltips.imageFor(shulkerBox) != null, "but the box still shows");

      ClientPreferences shown = interactions(original, false, true).withShowEmptyBoxes(true);
      state.update(shown);
      expect(StorageTooltips.imageFor(shulkerBox) != null, "on: empty shulker box");
      expect(StorageTooltips.imageFor(redShulkerBox) != null, "on: empty red shulker box");
      expect(
          StorageTooltips.panelColor(StorageKind.SHULKER_BOX, shulkerBox) == UNDYED_PANEL,
          "the undyed box shows its own colour");
      expect(StorageTooltips.imageFor(enderChest) != null, "on: empty ender chest");
      expect(
          StorageTooltips.imageFor(shulkerBox.copyWithCount(STACK_OF_TWO)) == null,
          "on: two shulker boxes");
      expect(
          StorageTooltips.imageFor(enderChest.copyWithCount(STACK_OF_TWO)) != null,
          "on: two ender chests");
      expect(StorageTooltips.imageFor(bundle) == null, "on: empty bundle");
      state.update(interactions(shown, true, true));
      expect(StorageTooltips.imageFor(bundle) == null, "on: empty bundle, bundle interactions");

      state.update(hidden);
      expect(StorageTooltips.imageFor(shulkerBox) == null, "off again: empty shulker box");
    } finally {
      state.update(original);
    }
  }

  /** {@code preferences} with the box colours off. */
  private static ClientPreferences withoutColors(ClientPreferences preferences) {
    return new ClientPreferences(
        preferences.itemStorageTooltip(),
        false,
        preferences.itemStorageHighlightSprite(),
        preferences.verticalTooltipScrolling(),
        preferences.iconMarker(),
        preferences.singleItemMovement(),
        preferences.itemHeldByCursorTooltip(),
        preferences.controlScheme(),
        preferences.nestedItemTooltips(),
        preferences.bundleInteractions(),
        preferences.enderChestInteractions(),
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

  /** {@code preferences} with the two interaction options as given. */
  private static ClientPreferences interactions(
      ClientPreferences preferences, boolean bundles, boolean enderChests) {
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
        bundles,
        enderChests,
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

  private static void expect(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
