package com.einsteinmods.shulkertoolkit.transfer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.jspecify.annotations.Nullable;

/**
 * One click's working copy of a pinned window: copies of its slots and of the cursor stack, edited
 * with vanilla's click rules and written back only when the whole click is valid, so a rejected
 * click changes nothing. Shulker boxes and the ender chest are chests ({@code
 * AbstractContainerMenu#doClick} on a plain slot); bundles take what they can by weight and put it
 * first, as vanilla {@code BundleContents.Mutable#tryInsert}. Work is bounded by the window's
 * slots.
 */
final class PinnedWindowEdit {
  private final StorageKind kind;
  private final List<ItemStack> slots;
  private ItemStack carried;
  private boolean changed;

  /**
   * @param items the window's slots (copied; bundles: newest first, no empty slots)
   * @param carried the cursor stack (copied)
   */
  PinnedWindowEdit(StorageKind kind, List<ItemStack> items, ItemStack carried) {
    this.kind = kind;
    this.slots = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      slots.add(item.copy());
    }
    this.carried = carried.copy();
  }

  List<ItemStack> slots() {
    return slots;
  }

  ItemStack carried() {
    return carried;
  }

  /** Whether any slot changed. */
  boolean changed() {
    return changed;
  }

  /** The bundle contents after the edit (bundles only). */
  BundleContents bundleContents() {
    List<ItemStackTemplate> templates = new ArrayList<>(slots.size());
    for (ItemStack stack : slots) {
      if (!stack.isEmpty()) {
        templates.add(ItemStackTemplate.fromNonEmptyStack(stack));
      }
    }
    return templates.isEmpty() ? BundleContents.EMPTY : new BundleContents(templates);
  }

  /**
   * Vanilla {@code AbstractContainerMenu#doClick} with {@code PICKUP}: take all or half, place all
   * or one, merge or swap. Bundles insert the cursor stack wherever the window is clicked.
   *
   * @return false when {@code index} is not a slot of this window
   */
  boolean pickup(int index, boolean primary) {
    if (kind == StorageKind.BUNDLE) {
      return pickupBundle(index, primary);
    }
    if (index < 0 || index >= slots.size()) {
      return false;
    }
    ItemStack clicked = slots.get(index);
    if (clicked.isEmpty()) {
      if (!carried.isEmpty() && mayPlace(carried)) {
        int placed = Math.min(primary ? carried.getCount() : 1, StorageKind.maxCount(carried));
        set(index, carried.split(placed));
      }
    } else if (carried.isEmpty()) {
      int taken = primary ? clicked.getCount() : (clicked.getCount() + 1) / 2;
      carried = clicked.split(taken);
      set(index, clicked);
    } else if (mayPlace(carried)) {
      if (ItemStack.isSameItemSameComponents(clicked, carried)) {
        int room = StorageKind.maxCount(clicked) - clicked.getCount();
        int placed = Math.min(primary ? carried.getCount() : 1, room);
        if (placed > 0) {
          clicked.grow(placed);
          carried.shrink(placed);
          set(index, clicked);
        }
      } else if (carried.getCount() <= StorageKind.maxCount(carried)) {
        ItemStack previous = clicked;
        set(index, carried);
        carried = previous;
      }
    }
    return true;
  }

  /**
   * Takes up to {@code limit} items out of slot {@code index} for the player inventory.
   *
   * @return what came out (empty when nothing)
   */
  ItemStack takeOut(int index, int limit) {
    if (index < 0 || index >= slots.size() || limit <= 0) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = slots.get(index);
    ItemStack taken = stack.split(Math.min(limit, stack.getCount()));
    if (!taken.isEmpty()) {
      set(index, stack);
    }
    return taken;
  }

  /**
   * Moves as much of {@code incoming} in as fits: onto stacks of the same item first, then into
   * empty slots (vanilla {@code AbstractContainerMenu#moveItemStackTo}); bundles by weight.
   *
   * @return items moved in ({@code incoming} is not changed)
   */
  int insert(ItemStack incoming) {
    if (incoming.isEmpty()) {
      return 0;
    }
    if (kind == StorageKind.BUNDLE) {
      return insertIntoBundle(incoming.copy());
    }
    if (!mayPlace(incoming)) {
      return 0;
    }
    int remaining = incoming.getCount();
    for (int index = 0; index < slots.size() && remaining > 0; index++) {
      ItemStack stack = slots.get(index);
      if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, incoming)) {
        int moved = Math.min(remaining, StorageKind.maxCount(stack) - stack.getCount());
        if (moved > 0) {
          stack.grow(moved);
          remaining -= moved;
          changed = true;
        }
      }
    }
    for (int index = 0; index < slots.size() && remaining > 0; index++) {
      if (slots.get(index).isEmpty()) {
        int moved = Math.min(remaining, StorageKind.maxCount(incoming));
        set(index, incoming.copyWithCount(moved));
        remaining -= moved;
      }
    }
    return incoming.getCount() - remaining;
  }

  /**
   * Vanilla {@code AbstractContainerMenu#doClick} with {@code SWAP}, the window slot as the target.
   * Chests only: a bundle has no fixed slots to swap with.
   *
   * @return the new stack of the hotbar slot, or null when nothing may move
   */
  @Nullable ItemStack swap(int index, ItemStack hotbar) {
    if (kind == StorageKind.BUNDLE || index < 0 || index >= slots.size()) {
      return null;
    }
    ItemStack target = slots.get(index);
    if (hotbar.isEmpty()) {
      if (target.isEmpty()) {
        return null;
      }
      set(index, ItemStack.EMPTY);
      return target;
    }
    if (!mayPlace(hotbar) || hotbar.getCount() > StorageKind.maxCount(hotbar)) {
      return null;
    }
    set(index, hotbar.copy());
    return target;
  }

  /** Bundles: insert the cursor stack (all or one); with an empty cursor, take all or half. */
  private boolean pickupBundle(int index, boolean primary) {
    if (!carried.isEmpty()) {
      int moved = insertIntoBundle(primary ? carried.copy() : carried.copyWithCount(1));
      carried.shrink(moved);
      return true;
    }
    if (index < 0 || index >= slots.size()) {
      // The free part of the window, or a slot that just emptied: nothing to take.
      return true;
    }
    ItemStack clicked = slots.get(index);
    int taken = primary ? clicked.getCount() : (clicked.getCount() + 1) / 2;
    carried = clicked.split(taken);
    if (clicked.isEmpty()) {
      slots.remove(index);
    }
    changed = true;
    return true;
  }

  /** Vanilla {@code BundleContents.Mutable#tryInsert}: by weight, the stack moves to the front. */
  private int insertIntoBundle(ItemStack incoming) {
    BundleContents.Mutable contents = bundleContents().asMutable();
    int moved = contents.tryInsert(incoming);
    if (moved > 0) {
      slots.clear();
      for (ItemStackTemplate template : contents.toImmutable().items()) {
        slots.add(template.create());
      }
      changed = true;
    }
    return moved;
  }

  /**
   * Whether {@code stack} may go in: an ender chest takes anything, as its vanilla slots; a shulker
   * box what vanilla {@code ShulkerBoxSlot#mayPlace} takes.
   */
  private boolean mayPlace(ItemStack stack) {
    return kind == StorageKind.ENDER_CHEST || stack.getItem().canFitInsideContainerItems();
  }

  private void set(int index, ItemStack stack) {
    slots.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack);
    changed = true;
  }
}
