package com.einsteinmods.shulkertoolkit.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Insert and extract rules of a container item for one click, independent of Minecraft types: a
 * mutable copy of its slots plus the player's selected slot, which every operation updates the way
 * Easy Shulker Boxes' {@code ItemStackingContext} does (the selection follows the slot an insert
 * ended in; extracting a whole selected stack moves it to the next stack). The same inputs give the
 * same result on the client (prediction) and the server. Work is bounded by the slot count.
 *
 * @param <K> item kind of a slot; counts are kept apart, so a key is never mutated
 */
public final class StorageStacking<K> {
  /** Grid and extraction order of one kind of container item. */
  public interface Layout {
    int columns(int size);

    int rows(int size);

    /**
     * True when extraction starts at the first slot and the selection is stored one below the slot
     * index (bundles: their editable copy starts with an extra empty slot); false when it starts at
     * the last slot (shulker boxes, the ender chest).
     */
    boolean extractsFromFront();
  }

  /**
   * Items taken out by {@link #removeOne}.
   *
   * @param key kind of the items
   * @param count how many
   * @param slot the slot they came from
   */
  public record Removal<K>(K key, int count, int slot) {}

  private final List<@Nullable K> keys;
  private final int[] counts;
  private final BiPredicate<K, K> sameKind;
  private final ToIntFunction<K> maxCount;
  private final Layout layout;
  private final boolean singleItem;
  private int selected;
  private boolean modified;

  /**
   * @param keys one key per slot, null for an empty slot (copied)
   * @param counts count of each slot, same length as {@code keys} (copied)
   * @param sameKind whether two keys stack together
   * @param maxCount largest stack of a key in this container
   * @param singleItem single item movement: every move takes at most one item
   * @param selected the player's selected slot, or {@link StorageGrid#NO_ITEM}
   */
  public StorageStacking(
      List<@Nullable K> keys,
      int[] counts,
      BiPredicate<K, K> sameKind,
      ToIntFunction<K> maxCount,
      Layout layout,
      boolean singleItem,
      int selected) {
    if (keys.size() != counts.length) {
      throw new IllegalArgumentException("keys and counts differ in length");
    }
    this.keys = new ArrayList<>(keys);
    this.counts = counts.clone();
    this.sameKind = sameKind;
    this.maxCount = maxCount;
    this.layout = layout;
    this.singleItem = singleItem;
    this.selected = selected;
  }

  public int size() {
    return counts.length;
  }

  public @Nullable K key(int slot) {
    return keys.get(slot);
  }

  public int count(int slot) {
    return counts[slot];
  }

  /** The player's selected slot after the operations so far. */
  public int selected() {
    return selected;
  }

  /** Whether any operation changed a slot. */
  public boolean modified() {
    return modified;
  }

  /** Inserts next to the selected slot first; see {@link #tryInsert(Object, int, int, int)}. */
  public int tryInsert(K incoming, int incomingCount, int acceptable) {
    return tryInsert(incoming, incomingCount, acceptable, prioritizedSlot());
  }

  /**
   * Moves up to {@code acceptable} (one with single item movement) of {@code incomingCount} items
   * in: first onto stacks of the same kind ({@code prioritizedSlot} first, then in slot order), the
   * rest into the first empty slot. The selection moves to the slot the insert ended in, or to none
   * when not everything fit.
   *
   * @return items moved in
   */
  public int tryInsert(K incoming, int incomingCount, int acceptable, int prioritizedSlot) {
    int limit = Math.min(incomingCount, acceptable);
    if (singleItem) {
      limit = Math.min(1, limit);
    }
    if (limit <= 0 || incomingCount <= 0) {
      return 0;
    }
    int remaining = limit;
    int endSlot = StorageGrid.NO_ITEM;
    for (int index = -1; index < counts.length && remaining > 0; index++) {
      int slot = index < 0 ? prioritizedSlot : index;
      if (slot < 0 || slot >= counts.length || (index >= 0 && slot == prioritizedSlot)) {
        continue;
      }
      K key = keys.get(slot);
      if (key != null && sameKind.test(key, incoming)) {
        int moved = Math.min(remaining, maxCount.applyAsInt(key) - counts[slot]);
        if (moved > 0) {
          counts[slot] += moved;
          remaining -= moved;
          modified = true;
        }
        if (remaining == 0) {
          endSlot = slot;
        }
      }
    }
    if (remaining > 0) {
      for (int slot = 0; slot < counts.length; slot++) {
        if (keys.get(slot) == null) {
          keys.set(slot, incoming);
          counts[slot] = remaining;
          remaining = 0;
          endSlot = slot;
          modified = true;
          break;
        }
      }
    }
    setPrioritizedSlot(endSlot);
    return limit - remaining;
  }

  /**
   * Takes the stack (one item with single item movement) a click extracts: the selected slot when
   * it holds something {@code other} can join, else the first such slot in extraction order.
   *
   * @param other the stack the items join (the cursor, or the clicked slot), or null for none
   * @param otherCount its count; it must stay below its max stack size to join
   * @return what came out, or null when nothing can
   */
  public @Nullable Removal<K> removeOne(@Nullable K other, int otherCount) {
    int slot = slotToRemoveFrom(other, otherCount);
    if (slot == StorageGrid.NO_ITEM) {
      return null;
    }
    K key = keys.get(slot);
    if (key == null) {
      return null;
    }
    int count = removeLimit(counts[slot]);
    counts[slot] -= count;
    if (counts[slot] <= 0) {
      counts[slot] = 0;
      keys.set(slot, null);
    }
    modified = true;
    return new Removal<>(key, count, slot);
  }

  /** Whether any slot is empty or holds a stack of the same kind that is not full. */
  public boolean canAdd(K incoming) {
    for (int slot = 0; slot < counts.length; slot++) {
      K key = keys.get(slot);
      if (key == null
          || (sameKind.test(key, incoming) && counts[slot] < maxCount.applyAsInt(key))) {
        return true;
      }
    }
    return false;
  }

  /** Whether any slot holds a stack {@code test} accepts. */
  public boolean anyMatch(BiPredicate<K, K> test, K other) {
    for (K key : keys) {
      if (key != null && test.test(key, other)) {
        return true;
      }
    }
    return false;
  }

  /** Whether every slot is empty. */
  public boolean isEmpty() {
    for (K key : keys) {
      if (key != null) {
        return false;
      }
    }
    return true;
  }

  private int slotToRemoveFrom(@Nullable K other, int otherCount) {
    int prioritized = prioritizedSlot();
    if (prioritized >= 0 && prioritized < counts.length) {
      K key = keys.get(prioritized);
      if (key != null && canJoin(other, otherCount, key)) {
        if (counts[prioritized] <= removeLimit(counts[prioritized])) {
          int step = layout.extractsFromFront() ? 1 : -1;
          setPrioritizedSlot(
              StorageGrid.scroll(
                  selected,
                  layout.columns(counts.length),
                  layout.rows(counts.length),
                  counts.length,
                  slot -> keys.get(slot) != null,
                  step,
                  0));
        }
        return prioritized;
      }
    }
    for (int index = 0; index < counts.length; index++) {
      int slot = layout.extractsFromFront() ? index : counts.length - 1 - index;
      K key = keys.get(slot);
      if (key != null && canJoin(other, otherCount, key)) {
        setPrioritizedSlot(counts[slot] <= removeLimit(counts[slot]) ? StorageGrid.NO_ITEM : slot);
        return slot;
      }
    }
    return StorageGrid.NO_ITEM;
  }

  private boolean canJoin(@Nullable K other, int otherCount, K key) {
    return other == null || (sameKind.test(other, key) && otherCount < maxCount.applyAsInt(key));
  }

  private int removeLimit(int count) {
    return singleItem ? Math.min(1, count) : count;
  }

  /** The selection as a slot index of this copy (bundles store it one lower). */
  private int prioritizedSlot() {
    return selected != StorageGrid.NO_ITEM && layout.extractsFromFront() ? selected + 1 : selected;
  }

  private void setPrioritizedSlot(int slot) {
    selected = slot != StorageGrid.NO_ITEM && layout.extractsFromFront() ? slot - 1 : slot;
  }
}
