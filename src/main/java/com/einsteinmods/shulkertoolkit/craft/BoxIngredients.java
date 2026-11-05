package com.einsteinmods.shulkertoolkit.craft;

import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.pick.DepthStorage;
import com.einsteinmods.shulkertoolkit.pick.WantedItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Crafting ingredients stored where the player's depth level reaches ({@link DepthStorage}: shulker
 * boxes and bundles in the inventory, then, by level, bundles in those boxes and the ender chest).
 * Counting and taking read the same items with the same rules, so the recipe book never offers more
 * than placing can take. Side-agnostic.
 */
final class BoxIngredients {
  private static final int UNDAMAGED = 0;

  private BoxIngredients() {}

  /**
   * Adds every usable stored item to {@code contents}, as vanilla {@code
   * StackedItemContents#accountSimpleStack} adds loose stacks. Allocates only the reader: entries
   * are read from the immutable contents components.
   */
  static void account(Player player, CraftScope scope, StackedContents<Holder<Item>> contents) {
    DepthStorage storage = new DepthStorage(player, scope);
    for (int source = 0; source < storage.sources(); source++) {
      int entries = storage.entries(source);
      for (int entry = 0; entry < entries; entry++) {
        ItemInstance item = storage.entry(source, entry);
        if (item != null && isUsableForCrafting(item)) {
          contents.account(item.typeHolder(), item.count());
        }
      }
    }
  }

  /**
   * Takes up to {@code count} of {@code item} from the first stored stack that vanilla {@code
   * Inventory#findSlotMatchingCraftingIngredient} would accept for a grid slot holding {@code
   * existing}, and returns them (empty when none), searching in {@link DepthStorage} order. Takes
   * only stacks of that same item and components, so the result stacks with {@code existing}.
   */
  static ItemStack take(
      Player player, CraftScope scope, Holder<Item> item, ItemStack existing, int count) {
    DepthStorage storage = new DepthStorage(player, scope);
    for (int source = 0; source < storage.sources(); source++) {
      int entries = storage.entries(source);
      for (int entry = 0; entry < entries; entry++) {
        ItemInstance stored = storage.entry(source, entry);
        if (stored != null && matches(stored, item, existing)) {
          return storage.take(source, wantedFor(stored), count);
        }
      }
    }
    return ItemStack.EMPTY;
  }

  /**
   * Vanilla {@code Inventory#isUsableForCrafting} (not damaged, not enchanted, no custom name) read
   * from an item without creating a stack.
   */
  static boolean isUsableForCrafting(ItemInstance item) {
    Integer damage = item.get(DataComponents.DAMAGE);
    boolean damaged =
        damage != null
            && damage > UNDAMAGED
            && item.get(DataComponents.MAX_DAMAGE) != null
            && item.get(DataComponents.UNBREAKABLE) == null;
    return !damaged
        && item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
        && item.get(DataComponents.CUSTOM_NAME) == null;
  }

  /**
   * Vanilla {@code Inventory#findSlotMatchingCraftingIngredient}: the same item, usable for
   * crafting, and the same components as what the grid slot already holds.
   */
  private static boolean matches(ItemInstance stored, Holder<Item> item, ItemStack existing) {
    if (stored.typeHolder().value() != item.value() || !isUsableForCrafting(stored)) {
      return false;
    }
    if (existing.isEmpty()) {
      return true;
    }
    if (stored instanceof ItemStack stack) {
      return ItemStack.isSameItemSameComponents(existing, stack);
    }
    return existing.getItem() == item.value()
        && stored instanceof ItemStackTemplate template
        && existing.getComponentsPatch().equals(template.components());
  }

  /** {@link DepthStorage#entry} gives templates inside containers and loose ender chest stacks. */
  private static WantedItem wantedFor(ItemInstance stored) {
    if (stored instanceof ItemStackTemplate template) {
      return WantedItem.of(template);
    }
    if (stored instanceof ItemStack stack) {
      return WantedItem.of(stack);
    }
    throw new IllegalStateException("unexpected stored item " + stored);
  }
}
