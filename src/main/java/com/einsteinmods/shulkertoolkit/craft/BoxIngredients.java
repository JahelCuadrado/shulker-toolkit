package com.einsteinmods.shulkertoolkit.craft;

import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.pick.BoxContents;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * Crafting ingredients stored in a player's shulker boxes: the boxes in the hotbar and main
 * inventory (slots 0-35, the slots vanilla {@code Inventory#fillStackedContents} counts), then,
 * when the scope says so and an ender chest item is in those slots, the loose ender chest items and
 * the boxes in the ender chest; bundles inside those boxes too when the scope says so. Counting and
 * taking read the same items with the same rules, so the recipe book never offers more than placing
 * can take. Side-agnostic.
 *
 * <p>Only editable containers count, as clicks edit them ({@link StorageKind#allowModification}): a
 * box stacked with others, a box with more than 27 slots or a bundle stacked or with an invalid
 * weight is skipped, so taking never rewrites shared or oversized contents.
 */
final class BoxIngredients {
  private static final int SINGLE_ITEM = 1;
  private static final int UNDAMAGED = 0;

  private BoxIngredients() {}

  /**
   * Adds every usable stored item to {@code contents}, as vanilla {@code
   * StackedItemContents#accountSimpleStack} adds loose stacks. No allocation: box slots are read
   * from the immutable contents components.
   */
  static void account(Player player, CraftScope scope, StackedContents<Holder<Item>> contents) {
    NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
    boolean carriesEnderChest = false;
    for (int slot = 0; slot < items.size(); slot++) {
      ItemStack stack = items.get(slot);
      List<Optional<ItemStackTemplate>> box = editableBoxSlots(stack);
      if (box != null) {
        accountBox(box, scope.bundles(), contents);
      } else if (stack.is(Items.ENDER_CHEST)) {
        carriesEnderChest = true;
      }
    }
    if (!scope.enderChestItems() || !carriesEnderChest) {
      return;
    }
    List<ItemStack> enderItems = player.getEnderChestInventory().getItems();
    for (int slot = 0; slot < enderItems.size(); slot++) {
      ItemStack stack = enderItems.get(slot);
      // Loose items, as vanilla StackedItemContents#accountSimpleStack; a box is an item too.
      if (!stack.isEmpty() && Inventory.isUsableForCrafting(stack)) {
        contents.account(stack.typeHolder(), stack.getCount());
      }
      List<Optional<ItemStackTemplate>> box =
          scope.enderChestBoxes() ? editableBoxSlots(stack) : null;
      if (box != null) {
        accountBox(box, scope.bundles(), contents);
      }
    }
  }

  /**
   * Takes up to {@code count} of {@code item} from the first stored stack that vanilla {@code
   * Inventory#findSlotMatchingCraftingIngredient} would accept for a grid slot holding {@code
   * existing}, and returns them (empty when none): inventory boxes, then loose ender chest items,
   * then ender chest boxes. Changes the box (and the bundle in it) in place; an ender chest box is
   * also marked changed and its slot sent to the client.
   */
  static ItemStack take(
      Player player, CraftScope scope, Holder<Item> item, ItemStack existing, int count) {
    NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
    boolean carriesEnderChest = false;
    for (int slot = 0; slot < items.size(); slot++) {
      ItemStack stack = items.get(slot);
      ItemStack taken = takeFromBox(stack, scope.bundles(), item, existing, count);
      if (!taken.isEmpty()) {
        return taken;
      }
      carriesEnderChest |= stack.is(Items.ENDER_CHEST);
    }
    if (!scope.enderChestItems() || !carriesEnderChest) {
      return ItemStack.EMPTY;
    }
    SimpleContainer enderChest = player.getEnderChestInventory();
    for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
      ItemStack stack = enderChest.getItem(slot);
      if (matchesLoose(stack, item, existing)) {
        ItemStack taken = enderChest.removeItem(slot, Math.min(count, stack.getCount()));
        CraftFromBoxes.onEnderSlotChanged(player, slot);
        return taken;
      }
    }
    if (!scope.enderChestBoxes()) {
      return ItemStack.EMPTY;
    }
    for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
      ItemStack taken =
          takeFromBox(enderChest.getItem(slot), scope.bundles(), item, existing, count);
      if (!taken.isEmpty()) {
        enderChest.setChanged();
        CraftFromBoxes.onEnderSlotChanged(player, slot);
        return taken;
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

  private static void accountBox(
      List<Optional<ItemStackTemplate>> box,
      boolean bundles,
      StackedContents<Holder<Item>> contents) {
    for (int slot = 0; slot < box.size(); slot++) {
      Optional<ItemStackTemplate> content = box.get(slot);
      if (content.isEmpty()) {
        continue;
      }
      ItemStackTemplate template = content.get();
      accountItem(template, contents);
      BundleContents bundle = bundles ? editableBundle(template) : null;
      if (bundle != null) {
        List<ItemStackTemplate> bundleItems = bundle.items();
        for (int index = 0; index < bundleItems.size(); index++) {
          accountItem(bundleItems.get(index), contents);
        }
      }
    }
  }

  private static void accountItem(ItemStackTemplate item, StackedContents<Holder<Item>> contents) {
    if (isUsableForCrafting(item)) {
      contents.account(item.item(), item.count());
    }
  }

  /** Box slots first, then bundles in the box: loose box items go before bundle contents. */
  private static ItemStack takeFromBox(
      ItemStack box, boolean bundles, Holder<Item> item, ItemStack existing, int count) {
    List<Optional<ItemStackTemplate>> slots = editableBoxSlots(box);
    if (slots == null) {
      return ItemStack.EMPTY;
    }
    for (int slot = 0; slot < slots.size(); slot++) {
      Optional<ItemStackTemplate> content = slots.get(slot);
      if (content.isPresent() && matches(content.get(), item, existing)) {
        ItemStackTemplate template = content.get();
        int taken = Math.min(count, template.count());
        List<Optional<ItemStackTemplate>> updated = new ArrayList<>(slots);
        updated.set(slot, remainder(template, taken));
        BoxContents.setSlots(box, updated);
        return new ItemStack(template.item(), taken, template.components());
      }
    }
    if (!bundles) {
      return ItemStack.EMPTY;
    }
    for (int slot = 0; slot < slots.size(); slot++) {
      Optional<ItemStackTemplate> content = slots.get(slot);
      BundleContents bundle = content.isPresent() ? editableBundle(content.get()) : null;
      if (bundle == null) {
        continue;
      }
      List<ItemStackTemplate> bundleItems = bundle.items();
      for (int index = 0; index < bundleItems.size(); index++) {
        ItemStackTemplate template = bundleItems.get(index);
        if (matches(template, item, existing)) {
          int taken = Math.min(count, template.count());
          ItemStack bundleStack = content.get().create();
          bundleStack.set(DataComponents.BUNDLE_CONTENTS, without(bundleItems, index, taken));
          List<Optional<ItemStackTemplate>> updated = new ArrayList<>(slots);
          updated.set(slot, Optional.of(ItemStackTemplate.fromNonEmptyStack(bundleStack)));
          BoxContents.setSlots(box, updated);
          return new ItemStack(template.item(), taken, template.components());
        }
      }
    }
    return ItemStack.EMPTY;
  }

  /** The bundle contents with {@code taken} fewer items at {@code index}; selection reset. */
  private static BundleContents without(List<ItemStackTemplate> items, int index, int taken) {
    List<ItemStackTemplate> updated = new ArrayList<>(items);
    ItemStackTemplate template = updated.get(index);
    if (template.count() == taken) {
      updated.remove(index);
    } else {
      updated.set(index, template.withCount(template.count() - taken));
    }
    return updated.isEmpty() ? BundleContents.EMPTY : new BundleContents(List.copyOf(updated));
  }

  private static Optional<ItemStackTemplate> remainder(ItemStackTemplate template, int taken) {
    int left = template.count() - taken;
    return left == 0 ? Optional.empty() : Optional.of(template.withCount(left));
  }

  /**
   * Vanilla {@code Inventory#findSlotMatchingCraftingIngredient}: the same item, usable for
   * crafting, and the same components as what the grid slot already holds.
   */
  private static boolean matches(ItemStackTemplate stored, Holder<Item> item, ItemStack existing) {
    return stored.item().value() == item.value()
        && isUsableForCrafting(stored)
        && (existing.isEmpty()
            || (existing.getItem() == item.value()
                && existing.getComponentsPatch().equals(stored.components())));
  }

  /** Vanilla {@code Inventory#findSlotMatchingCraftingIngredient} for one loose stack. */
  private static boolean matchesLoose(ItemStack stack, Holder<Item> item, ItemStack existing) {
    return !stack.isEmpty()
        && stack.is(item)
        && Inventory.isUsableForCrafting(stack)
        && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack));
  }

  /** The slots of a single shulker box with at most 27 slots, or null. */
  private static @Nullable List<Optional<ItemStackTemplate>> editableBoxSlots(ItemStack stack) {
    List<Optional<ItemStackTemplate>> slots = BoxContents.slotsOf(stack);
    return slots != null && slots.size() <= StorageKind.SHULKER_SLOTS ? slots : null;
  }

  /** The contents of a single bundle with a valid weight, or null for every other item. */
  private static @Nullable BundleContents editableBundle(ItemStackTemplate item) {
    if (item.count() != SINGLE_ITEM) {
      return null;
    }
    BundleContents contents = item.get(DataComponents.BUNDLE_CONTENTS);
    return contents != null && contents.weight().isSuccess() ? contents : null;
  }
}
