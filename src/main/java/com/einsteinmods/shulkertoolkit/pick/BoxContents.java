package com.einsteinmods.shulkertoolkit.pick;

import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.core.PickupStorage;
import com.einsteinmods.shulkertoolkit.mixin.ItemContainerContentsAccessor;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

/**
 * Reads and changes the contents of a shulker box item ({@link DataComponents#CONTAINER}, what
 * vanilla {@code ShulkerBoxBlockEntity} loads when the box is placed). Contents are immutable: a
 * change sets new contents on the box stack.
 */
public final class BoxContents {
  private static final int SINGLE_BOX = 1;

  private BoxContents() {}

  /**
   * The slots of a shulker box item, or null when {@code stack} is not a single shulker box with
   * contents. Boxes stacked by other mods are skipped: their stacks share one contents component.
   */
  public static @Nullable List<Optional<ItemStackTemplate>> slotsOf(ItemStack stack) {
    if (stack.getCount() != SINGLE_BOX
        || !(stack.getItem() instanceof BlockItem blockItem)
        || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
      return null;
    }
    ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
    return contents == null ? null : accessor(contents).shulkertoolkit$slots();
  }

  /**
   * Removes up to {@code amount} items matching {@code wanted} from the box, in slot order, and
   * returns them as one stack. Empty when nothing matched.
   */
  static ItemStack take(ItemStack box, WantedItem wanted, int amount) {
    List<Optional<ItemStackTemplate>> slots = slotsOf(box);
    if (slots == null) {
      return ItemStack.EMPTY;
    }
    List<Optional<ItemStackTemplate>> updated = new ArrayList<>(slots);
    int remaining = amount;
    for (int slot = 0; slot < updated.size() && remaining > 0; slot++) {
      Optional<ItemStackTemplate> content = updated.get(slot);
      if (content.isEmpty() || !wanted.matches(content.get())) {
        continue;
      }
      ItemStackTemplate template = content.get();
      int taken = PickPlanner.takeFromSlot(template.count(), remaining);
      remaining -= taken;
      int left = template.count() - taken;
      updated.set(slot, left == 0 ? Optional.empty() : Optional.of(template.withCount(left)));
    }
    int takenTotal = amount - remaining;
    if (takenTotal == 0) {
      return ItemStack.EMPTY;
    }
    setSlots(box, updated);
    return wanted.createStack(takenTotal);
  }

  /**
   * Sets new contents on a shulker box item, in the same shape as vanilla {@code
   * ItemContainerContents#fromItems}: no trailing empty slots, and the shared EMPTY instance for an
   * empty box, so equal contents stay equal. Trims {@code updated} in place.
   */
  public static void setSlots(ItemStack box, List<Optional<ItemStackTemplate>> updated) {
    while (!updated.isEmpty() && updated.getLast().isEmpty()) {
      updated.removeLast();
    }
    box.set(
        DataComponents.CONTAINER,
        updated.isEmpty()
            ? ItemContainerContents.EMPTY
            : ItemContainerContentsAccessor.shulkertoolkit$create(updated));
  }

  /**
   * What {@link #roomFor} returns for a box that does not hold the item but has an empty slot. Such
   * a box takes a whole picked-up stack: an item entity never holds more than one stack.
   */
  static final int NOT_HOLDING_WITH_EMPTY_SLOT = -1;

  /**
   * How many of {@code picked} fit in the box, read without creating anything (it runs every tick
   * while a full inventory touches an item): the room when the box holds the item, {@link
   * #NOT_HOLDING_WITH_EMPTY_SLOT} when it does not but has an empty slot, otherwise 0. Also 0 for a
   * box that {@link #slotsOf} skips or contents beyond 27 slots (rewriting them as 27 would delete
   * items). The caller checks the item may go in a box ({@code Item#canFitInsideContainerItems}).
   *
   * @param components {@code picked}'s component patch, read once per pickup
   * @param maxCount the most one box slot holds ({@link StorageKind#maxCount})
   */
  static int roomFor(ItemStack box, ItemStack picked, DataComponentPatch components, int maxCount) {
    List<Optional<ItemStackTemplate>> slots = editableSlotsOf(box);
    if (slots == null) {
      return 0;
    }
    boolean holding = false;
    int room = 0;
    for (int slot = 0; slot < StorageKind.SHULKER_SLOTS; slot++) {
      Optional<ItemStackTemplate> content =
          slot < slots.size() ? slots.get(slot) : Optional.empty();
      if (content.isEmpty()) {
        room += maxCount;
      } else if (matches(content.get(), picked, components)) {
        holding = true;
        room += PickupStorage.addable(content.get().count(), maxCount, maxCount);
      }
    }
    if (holding) {
      return room;
    }
    return room > 0 ? NOT_HOLDING_WITH_EMPTY_SLOT : 0;
  }

  /**
   * Moves as much of {@code picked} as fits into a box {@link #roomFor} found room in, as vanilla
   * {@code SimpleContainer#addItem}: slots already holding the item first, then empty slots, each
   * in slot order. Shrinks {@code picked} by what was stored. Creates the new contents, so only
   * called when something goes in.
   *
   * @return items stored
   */
  static int store(ItemStack box, ItemStack picked, DataComponentPatch components, int maxCount) {
    List<Optional<ItemStackTemplate>> slots = editableSlotsOf(box);
    if (slots == null) {
      return 0;
    }
    List<Optional<ItemStackTemplate>> updated = new ArrayList<>(StorageKind.SHULKER_SLOTS);
    updated.addAll(slots);
    while (updated.size() < StorageKind.SHULKER_SLOTS) {
      updated.add(Optional.empty());
    }
    int remaining = picked.getCount();
    for (int slot = 0; slot < updated.size() && remaining > 0; slot++) {
      Optional<ItemStackTemplate> content = updated.get(slot);
      if (content.isPresent() && matches(content.get(), picked, components)) {
        int added = PickupStorage.addable(content.get().count(), maxCount, remaining);
        if (added > 0) {
          updated.set(slot, Optional.of(content.get().withCount(content.get().count() + added)));
          remaining -= added;
        }
      }
    }
    for (int slot = 0; slot < updated.size() && remaining > 0; slot++) {
      if (updated.get(slot).isEmpty()) {
        int added = PickupStorage.addable(PickupStorage.EMPTY_SLOT, maxCount, remaining);
        updated.set(slot, Optional.of(ItemStackTemplate.fromNonEmptyStack(picked, added)));
        remaining -= added;
      }
    }
    int stored = picked.getCount() - remaining;
    if (stored == 0) {
      return 0;
    }
    // Same shape as vanilla ItemContainerContents#fromItems (see take); never empty here.
    while (updated.getLast().isEmpty()) {
      updated.removeLast();
    }
    box.set(DataComponents.CONTAINER, ItemContainerContentsAccessor.shulkertoolkit$create(updated));
    picked.shrink(stored);
    return stored;
  }

  /** {@link #slotsOf}, or null for contents beyond 27 slots, which are never rewritten. */
  private static @Nullable List<Optional<ItemStackTemplate>> editableSlotsOf(ItemStack box) {
    List<Optional<ItemStackTemplate>> slots = slotsOf(box);
    return slots == null || slots.size() > StorageKind.SHULKER_SLOTS ? null : slots;
  }

  /**
   * Vanilla {@code ItemStack#isSameItemSameComponents} between a box slot and {@code picked},
   * without creating a stack: the item first, the components only for the same item (the shared
   * empty patch by identity).
   */
  private static boolean matches(
      ItemStackTemplate slot, ItemStack picked, DataComponentPatch components) {
    if (slot.item().value() != picked.getItem()) {
      return false;
    }
    DataComponentPatch slotComponents = slot.components();
    return slotComponents == components || slotComponents.equals(components);
  }

  private static ItemContainerContentsAccessor accessor(ItemContainerContents contents) {
    if ((Object) contents instanceof ItemContainerContentsAccessor accessor) {
      return accessor;
    }
    throw new IllegalStateException("ItemContainerContentsAccessor mixin not applied");
  }
}
