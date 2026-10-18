package com.einsteinmods.shulkertoolkit.pick;

import com.einsteinmods.shulkertoolkit.mixin.ItemStackAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The item to take out of shulker boxes. Matches box slots with vanilla's pick rule, {@code
 * ItemStack#isSameItemSameComponents} as {@code Inventory#findSlotMatchingItem} uses it: same item
 * and the same components (a renamed or enchanted copy is a different item).
 *
 * @param item the item type
 * @param components the component changes against the item's defaults
 * @param maxStackSize the most a full stack holds
 */
public record WantedItem(Holder<Item> item, DataComponentPatch components, int maxStackSize) {
  private static final int UNSTACKABLE = 1;

  /** The item of a non-empty stack, such as the stack vanilla picks from a block. */
  public static WantedItem of(ItemStack stack) {
    return new WantedItem(stack.typeHolder(), stack.getComponentsPatch(), stack.getMaxStackSize());
  }

  /**
   * What a stack that just ran out (count 0) was, or null for the shared empty stack. Vanilla
   * getters report air once the count is 0, so this reads the stack's fields.
   */
  public static @Nullable WantedItem ofRunOut(ItemStack stack) {
    if (!((Object) stack instanceof ItemStackAccessor accessor)) {
      throw new IllegalStateException("ItemStackAccessor mixin not applied");
    }
    Holder<Item> item = accessor.shulkertoolkit$rawItem();
    if (item == null || item.value() == Items.AIR) {
      return null;
    }
    PatchedDataComponentMap components = accessor.shulkertoolkit$rawComponents();
    return new WantedItem(
        item,
        components.asPatch(),
        components.getOrDefault(DataComponents.MAX_STACK_SIZE, UNSTACKABLE));
  }

  /** Vanilla {@code ItemStack#isSameItemSameComponents}, without creating a stack per slot. */
  public boolean matches(ItemStackTemplate slot) {
    return slot.item().value() == item.value() && slot.components().equals(components);
  }

  /** A new stack of this item. */
  public ItemStack createStack(int count) {
    return new ItemStack(item, count, components);
  }
}
