package com.einsteinmods.shulkertoolkit.craft;

import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.mixin.StackedItemContentsAccessor;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.server.PlayerSession;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The recipe book of the crafting table and the inventory ({@link AbstractCraftingMenu}) uses
 * ingredients stored in shulker boxes. Counting hooks vanilla {@code
 * AbstractCraftingMenu#fillCraftSlotsStackedContents}, which both sides call right after {@code
 * Inventory#fillStackedContents}: the client's {@code RecipeBookComponent} when it refreshes which
 * recipes are craftable (on open and on the first tick after the inventory changed, never per
 * frame) and the server's {@code ServerPlaceRecipe#placeRecipe} on a recipe click. Placing hooks
 * {@code ServerPlaceRecipe#moveItemToGrid} only where vanilla finds no loose stack. Furnaces and
 * other recipe book menus never reach either hook.
 *
 * <p>Cost (measured 2026-10-17, dev-only GameTest harness, M-series Mac, JDK 25; 20,000 refreshes,
 * 1,000 recipe clicks): a book refresh is 0.4 µs in vanilla or with the option off. With 9 full
 * shulker boxes in the inventory and 27 loose stacks in the ender chest: 3.7 µs at level 1, 4.3 µs
 * at level 2, 4.5-4.7 µs at levels 3-4. With 9 boxes of bundles in the inventory and 27 boxes of
 * bundles in the ender chest (4,860 stored stacks): 2.9 / 3.3 / 11.6 / 60.7 µs at levels 1-4. A
 * crafting table recipe click takes 60-90 µs, and 431 µs in that bundle case at level 4, mostly
 * vanilla's recipe solve over every counted item type. Paid only on those events, by the player who
 * opened the book or clicked.
 *
 * <p>The server decides: the player's client must have the mod (it sent its preferences), the
 * policy must allow it and the player must have the option on. The client counts only after a
 * server with the mod allowed it, so elsewhere the book stays vanilla.
 */
public final class CraftFromBoxes {
  /** {@link #moveFromBoxes} result that leaves the move to vanilla. */
  public static final int NOT_HANDLED = Integer.MIN_VALUE;

  /** Vanilla {@code Inventory#findSlotMatchingCraftingIngredient} result for "none". */
  private static final int NO_SLOT = -1;

  /** Set by the client entrypoint; a dedicated server never counts for a client player. */
  private static Supplier<@Nullable CraftScope> clientScope = () -> null;

  private CraftFromBoxes() {}

  public static void installClientCheck(Supplier<@Nullable CraftScope> scope) {
    clientScope = scope;
  }

  /**
   * Hook for the end of {@code AbstractCraftingMenu#fillCraftSlotsStackedContents}: adds the stored
   * ingredients. With the feature off it costs one side check and one supplier call (client) or one
   * map lookup (server).
   */
  public static void accountBoxes(Player player, StackedItemContents contents) {
    CraftScope scope = scopeFor(player);
    if (scope != null) {
      BoxIngredients.account(player, scope, accessor(contents).shulkertoolkit$raw());
    }
  }

  /**
   * Hook for the start of {@code ServerPlaceRecipe#moveItemToGrid}: when no loose stack holds the
   * ingredient, takes it from a box and puts it in {@code target} as vanilla does with a loose
   * stack (set when empty, grown otherwise).
   *
   * @return what vanilla would return (the count still to move), or {@link #NOT_HANDLED} to run
   *     vanilla, which also covers "nothing found"
   */
  public static int moveFromBoxes(Inventory inventory, Slot target, Holder<Item> item, int count) {
    Player player = inventory.player;
    if (!(player.containerMenu instanceof AbstractCraftingMenu)) {
      return NOT_HANDLED;
    }
    CraftScope scope = scopeFor(player);
    ItemStack existing = target.getItem();
    if (scope == null || inventory.findSlotMatchingCraftingIngredient(item, existing) != NO_SLOT) {
      return NOT_HANDLED;
    }
    ItemStack taken = BoxIngredients.take(player, scope, item, existing, count);
    if (taken.isEmpty()) {
      return NOT_HANDLED;
    }
    if (existing.isEmpty()) {
      target.set(taken);
    } else {
      existing.grow(taken.getCount());
    }
    return count - taken.getCount();
  }

  /** The server's rule for one player, without the session lookup. */
  public static @Nullable CraftScope serverScope(
      ServerPolicy policy, PlayerPreferences preferences) {
    return CraftScope.resolve(
        policy.allowCraftFromBoxes(),
        preferences.craftFromBoxes(),
        preferences.craftDepth(),
        policy.allowEnderPreview());
  }

  /** An ender chest box changed outside an ender chest menu: keep the client's copy current. */
  static void onEnderSlotChanged(Player player, int slot) {
    if (player instanceof ServerPlayer serverPlayer) {
      EnderChestSync.sendSlot(serverPlayer, slot);
    }
  }

  private static @Nullable CraftScope scopeFor(Player player) {
    if (player.level().isClientSide()) {
      return clientScope.get();
    }
    PlayerSession session = ServerPlayerSessions.modClientSession(player.getUUID());
    return session == null ? null : serverScope(session.policy(), session.preferences());
  }

  private static StackedItemContentsAccessor accessor(StackedItemContents contents) {
    if ((Object) contents instanceof StackedItemContentsAccessor accessor) {
      return accessor;
    }
    throw new IllegalStateException("StackedItemContentsAccessor mixin not applied");
  }
}
