package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.craft.CraftFromBoxes;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;

/**
 * The recipe book placing the crafting table recipe (four planks in the 2x2 inventory grid) through
 * vanilla {@code AbstractCraftingMenu#handlePlacement}, as {@code
 * ServerGamePacketListenerImpl#handlePlaceRecipe} calls it, with the planks only inside containers.
 * Players get fresh UUIDs; {@code ServerPlayerSessions#start} is never called.
 */
public final class CraftFromBoxesGameTest {
  private static final ResourceKey<Recipe<?>> CRAFTING_TABLE =
      ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("crafting_table"));
  private static final int STORED_PLANKS = 64;
  private static final int PLANKS_PER_SLOT = 1;
  private static final int MAX_PLANKS_PER_SLOT = 16;
  private static final int GRID_SLOTS = 4;
  private static final int BUNDLE_PLANKS = GRID_SLOTS;
  private static final int BOX_SLOT = 9;
  private static final int ENDER_ITEM_SLOT = 10;
  private static final int ENDER_BOX_SLOT = 0;
  private static final int LOOSE_ENDER_SLOT = 1;
  private static final boolean SINGLE_CRAFT = false;
  private static final boolean MAX_CRAFT = true;
  private static final ServerPolicy CRAFT_FORBIDDEN =
      new ServerPolicy(true, true, true, true, true, true, true, false);

  @GameTest
  public void planksOnlyInABoxArePlacedInTheGrid(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.INVENTORY_BOXES);
    ItemStack box = planksBox();
    player.getInventory().setItem(BOX_SLOT, box);

    RecipeBookMenu.PostPlaceAction action = place(helper, player, SINGLE_CRAFT);

    helper.assertTrue(action == RecipeBookMenu.PostPlaceAction.NOTHING, "the recipe is craftable");
    assertGridHolds(helper, player, PLANKS_PER_SLOT);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.OAK_PLANKS) == STORED_PLANKS - GRID_SLOTS,
        "the box must lose exactly the planks in the grid");
    helper.succeed();
  }

  @GameTest
  public void maxCraftEmptiesTheBoxIntoTheGrid(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.INVENTORY_BOXES);
    ItemStack box = planksBox();
    player.getInventory().setItem(BOX_SLOT, box);

    place(helper, player, MAX_CRAFT);

    assertGridHolds(helper, player, MAX_PLANKS_PER_SLOT);
    helper.assertTrue(
        box.get(DataComponents.CONTAINER) == ItemContainerContents.EMPTY,
        "an emptied box gets vanilla's shared empty contents");
    helper.succeed();
  }

  @GameTest
  public void depthOneIgnoresTheEnderChest(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.INVENTORY_BOXES);
    ItemStack enderBox = enderSetup(player, true);

    assertVanilla(helper, player);
    helper.assertTrue(
        ShulkerFixtures.countInBox(enderBox, Items.OAK_PLANKS) == STORED_PLANKS,
        "the ender chest box is untouched");
    helper.succeed();
  }

  @GameTest
  public void depthThreeWithoutAnEnderChestItemIgnoresTheEnderChest(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.ENDER_CHEST_BOXES);
    ItemStack enderBox = enderSetup(player, false);

    assertVanilla(helper, player);
    helper.assertTrue(
        ShulkerFixtures.countInBox(enderBox, Items.OAK_PLANKS) == STORED_PLANKS,
        "the ender chest box is untouched");
    helper.succeed();
  }

  @GameTest
  public void depthThreeWithAnEnderChestItemUsesEnderChestBoxes(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.ENDER_CHEST_BOXES);
    ItemStack enderBox = enderSetup(player, true);

    place(helper, player, SINGLE_CRAFT);

    assertGridHolds(helper, player, PLANKS_PER_SLOT);
    helper.assertTrue(
        ShulkerFixtures.countInBox(
                player.getEnderChestInventory().getItem(ENDER_BOX_SLOT), Items.OAK_PLANKS)
            == STORED_PLANKS - GRID_SLOTS,
        "the ender chest box must lose exactly the planks in the grid");
    helper.assertTrue(
        enderBox == player.getEnderChestInventory().getItem(ENDER_BOX_SLOT),
        "the ender chest box is changed in place");
    helper.succeed();
  }

  @GameTest
  public void depthTwoUsesLooseEnderChestItemsButNotItsBoxes(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.ENDER_CHEST_ITEMS);
    player.getInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    player
        .getEnderChestInventory()
        .setItem(LOOSE_ENDER_SLOT, new ItemStack(Items.OAK_PLANKS, GRID_SLOTS));
    ItemStack enderBox = planksBox();
    player.getEnderChestInventory().setItem(ENDER_BOX_SLOT, enderBox);

    place(helper, player, MAX_CRAFT);

    assertGridHolds(helper, player, PLANKS_PER_SLOT);
    helper.assertTrue(
        player.getEnderChestInventory().getItem(LOOSE_ENDER_SLOT).isEmpty(),
        "the loose ender chest planks went to the grid");
    helper.assertTrue(
        ShulkerFixtures.countInBox(enderBox, Items.OAK_PLANKS) == STORED_PLANKS,
        "boxes inside the ender chest are not read at level 2");
    helper.succeed();
  }

  @GameTest
  public void depthTwoWithoutAnEnderChestItemIgnoresLooseEnderChestItems(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.ENDER_CHEST_ITEMS);
    player
        .getEnderChestInventory()
        .setItem(LOOSE_ENDER_SLOT, new ItemStack(Items.OAK_PLANKS, GRID_SLOTS));

    assertVanilla(helper, player);
    helper.assertTrue(
        player.getEnderChestInventory().getItem(LOOSE_ENDER_SLOT).getCount() == GRID_SLOTS,
        "the ender chest is untouched");
    helper.succeed();
  }

  @GameTest
  public void depthThreeIgnoresBundles(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.ENDER_CHEST_BOXES);
    player.getInventory().setItem(BOX_SLOT, ShulkerFixtures.box(planksBundle()));

    assertVanilla(helper, player);
    helper.succeed();
  }

  @GameTest
  public void depthFourUsesABundleInABox(GameTestHelper helper) {
    ServerPlayer player = player(helper, CraftDepth.BUNDLES_IN_BOXES);
    ItemStack box = ShulkerFixtures.box(planksBundle());
    player.getInventory().setItem(BOX_SLOT, box);

    place(helper, player, SINGLE_CRAFT);

    assertGridHolds(helper, player, PLANKS_PER_SLOT);
    ItemStack bundle =
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
            .nonEmptyItemCopyStream()
            .findFirst()
            .orElse(ItemStack.EMPTY);
    helper.assertTrue(bundle.is(Items.BUNDLE), "the bundle stays in the box");
    helper.assertTrue(
        bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty(),
        "the bundle must lose exactly the planks in the grid");
    helper.succeed();
  }

  @GameTest
  public void optionOffIsVanilla(GameTestHelper helper) {
    ServerPlayer player =
        ShulkerFixtures.player(
            helper,
            GameType.SURVIVAL,
            new PlayerPreferences(
                PickMode.DEFAULT, true, false, false, false, false, CraftDepth.DEFAULT));
    ItemStack box = planksBox();
    player.getInventory().setItem(BOX_SLOT, box);

    assertVanilla(helper, player);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.OAK_PLANKS) == STORED_PLANKS, "box untouched");
    helper.succeed();
  }

  @GameTest
  public void playerWithoutTheModIsVanilla(GameTestHelper helper) {
    ServerPlayer player = ShulkerFixtures.playerWithoutMod(helper, GameType.SURVIVAL);
    ItemStack box = planksBox();
    player.getInventory().setItem(BOX_SLOT, box);

    assertVanilla(helper, player);
    helper.assertTrue(
        ShulkerFixtures.countInBox(box, Items.OAK_PLANKS) == STORED_PLANKS, "box untouched");
    helper.succeed();
  }

  /** The server's rule; sessions take the file policy, which tests must not change. */
  @GameTest
  public void forbiddenByPolicyIsVanilla(GameTestHelper helper) {
    helper.assertTrue(
        CraftFromBoxes.serverScope(CRAFT_FORBIDDEN, PlayerPreferences.DEFAULTS) == null,
        "a server that forbids it turns it off");
    helper.assertTrue(
        CraftFromBoxes.serverScope(ServerPolicy.UNRESTRICTED, PlayerPreferences.DEFAULTS) != null,
        "allowed by default");
    helper.succeed();
  }

  private static ServerPlayer player(GameTestHelper helper, CraftDepth depth) {
    return ShulkerFixtures.player(
        helper,
        GameType.SURVIVAL,
        new PlayerPreferences(PickMode.DEFAULT, true, false, false, false, true, depth));
  }

  private static ItemStack planksBox() {
    return ShulkerFixtures.box(new ItemStack(Items.OAK_PLANKS, STORED_PLANKS));
  }

  private static ItemStack planksBundle() {
    ItemStack bundle = new ItemStack(Items.BUNDLE);
    bundle.set(
        DataComponents.BUNDLE_CONTENTS,
        new BundleContents(
            List.of(
                ItemStackTemplate.fromNonEmptyStack(
                    new ItemStack(Items.OAK_PLANKS, BUNDLE_PLANKS)))));
    return bundle;
  }

  /** A planks box in the ender chest, and optionally an ender chest item in the inventory. */
  private static ItemStack enderSetup(ServerPlayer player, boolean carryEnderChest) {
    ItemStack enderBox = planksBox();
    player.getEnderChestInventory().setItem(ENDER_BOX_SLOT, enderBox);
    if (carryEnderChest) {
      player.getInventory().setItem(ENDER_ITEM_SLOT, new ItemStack(Items.ENDER_CHEST));
    }
    return enderBox;
  }

  private static RecipeBookMenu.PostPlaceAction place(
      GameTestHelper helper, ServerPlayer player, boolean useMaxItems) {
    RecipeHolder<?> recipe =
        helper
            .getLevel()
            .recipeAccess()
            .byKey(CRAFTING_TABLE)
            .orElseThrow(
                () ->
                    helper.assertionException(Component.literal("crafting table recipe missing")));
    return player.inventoryMenu.handlePlacement(
        useMaxItems, player.isCreative(), recipe, helper.getLevel(), player.getInventory());
  }

  /** Vanilla with no loose planks: a ghost recipe and an empty grid. */
  private static void assertVanilla(GameTestHelper helper, ServerPlayer player) {
    helper.assertTrue(
        place(helper, player, SINGLE_CRAFT) == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE,
        "without usable planks vanilla shows a ghost recipe");
    assertGridHolds(helper, player, 0);
  }

  private static void assertGridHolds(GameTestHelper helper, ServerPlayer player, int planks) {
    List<Slot> grid = player.inventoryMenu.getInputGridSlots();
    for (Slot slot : grid) {
      ItemStack stack = slot.getItem();
      helper.assertTrue(
          planks == 0 ? stack.isEmpty() : stack.is(Items.OAK_PLANKS) && stack.getCount() == planks,
          "grid slot " + slot.index + " must hold " + planks + " planks, got " + stack);
    }
  }
}
