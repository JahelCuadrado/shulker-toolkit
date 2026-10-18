package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class StorageStackingTest {
  private static final int SLOTS = 27;
  private static final int STACK = 64;
  private static final int SINGLE_STACK = 1;

  /** A shulker box: 9x3, extraction from the last slot. */
  private static final StorageStacking.Layout BOX =
      new StorageStacking.Layout() {
        @Override
        public int columns(int size) {
          return 9;
        }

        @Override
        public int rows(int size) {
          return 3;
        }

        @Override
        public boolean extractsFromFront() {
          return false;
        }
      };

  /** A bundle's editable copy: a square grid, extraction from the front. */
  private static final StorageStacking.Layout BUNDLE =
      new StorageStacking.Layout() {
        @Override
        public int columns(int size) {
          return StorageGrid.bundleColumns(size);
        }

        @Override
        public int rows(int size) {
          return StorageGrid.bundleRows(size);
        }

        @Override
        public boolean extractsFromFront() {
          return true;
        }
      };

  private static StorageStacking<String> box(
      String[] keys, int[] counts, boolean single, int selected) {
    return new StorageStacking<>(
        Arrays.asList(keys),
        counts,
        Objects::equals,
        key -> key.startsWith("sword") ? SINGLE_STACK : STACK,
        BOX,
        single,
        selected);
  }

  @Test
  void insertGoesToTheFirstEmptySlotAndSelectsIt() {
    StorageStacking<String> slots =
        box(new String[SLOTS], new int[SLOTS], false, StorageGrid.NO_ITEM);

    assertEquals(STACK, slots.tryInsert("stone", STACK, STACK));
    assertEquals("stone", slots.key(0));
    assertEquals(STACK, slots.count(0));
    assertEquals(0, slots.selected());
    assertTrue(slots.modified());
  }

  @Test
  void insertFillsStacksOfTheSameKindStartingAtTheSelection() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    keys[1] = "stone";
    counts[1] = 60;
    keys[5] = "stone";
    counts[5] = 60;
    StorageStacking<String> slots = box(keys, counts, false, 5);

    assertEquals(10, slots.tryInsert("stone", 10, 10));
    assertEquals(STACK, slots.count(5), "the selected stack fills first");
    assertEquals(STACK, slots.count(1));
    assertEquals("stone", slots.key(0));
    assertEquals(2, slots.count(0), "the rest opens the first empty slot");
    assertEquals(0, slots.selected());
  }

  @Test
  void aFullContainerTakesNothingAndDropsTheSelection() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    Arrays.fill(keys, "dirt");
    Arrays.fill(counts, STACK);
    StorageStacking<String> slots = box(keys, counts, false, 3);

    assertEquals(0, slots.tryInsert("stone", STACK, STACK));
    assertEquals(StorageGrid.NO_ITEM, slots.selected());
    assertFalse(slots.modified());
    assertFalse(slots.canAdd("stone"));
    assertTrue(box(keys, new int[SLOTS], false, 0).canAdd("dirt"));
  }

  @Test
  void acceptableAndSingleItemLimitTheInsert() {
    StorageStacking<String> limited =
        box(new String[SLOTS], new int[SLOTS], false, StorageGrid.NO_ITEM);
    StorageStacking<String> single =
        box(new String[SLOTS], new int[SLOTS], true, StorageGrid.NO_ITEM);

    assertEquals(20, limited.tryInsert("stone", STACK, 20));
    assertEquals(1, single.tryInsert("stone", STACK, STACK));
    assertEquals(0, limited.tryInsert("stone", STACK, 0));
  }

  @Test
  void extractionTakesTheLastStackWithoutASelection() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    keys[0] = "dirt";
    counts[0] = 12;
    keys[8] = "stone";
    counts[8] = STACK;
    StorageStacking<String> slots = box(keys, counts, false, StorageGrid.NO_ITEM);

    StorageStacking.Removal<String> removal = slots.removeOne(null, 0);

    assertNotNull(removal);
    assertEquals(new StorageStacking.Removal<>("stone", STACK, 8), removal);
    assertNull(slots.key(8));
    assertEquals(StorageGrid.NO_ITEM, slots.selected());
  }

  @Test
  void extractingTheSelectedStackMovesTheSelectionToThePreviousStack() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    keys[0] = "dirt";
    counts[0] = 12;
    keys[4] = "torch";
    counts[4] = 5;
    StorageStacking<String> slots = box(keys, counts, false, 4);

    StorageStacking.Removal<String> removal = slots.removeOne(null, 0);

    assertEquals(new StorageStacking.Removal<>("torch", 5, 4), removal);
    assertEquals(0, slots.selected());
  }

  @Test
  void singleItemExtractionKeepsTheSelectionUntilTheLastItem() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    keys[2] = "stone";
    counts[2] = 2;
    StorageStacking<String> slots = box(keys, counts, true, 2);

    assertEquals(new StorageStacking.Removal<>("stone", 1, 2), slots.removeOne(null, 0));
    assertEquals(2, slots.selected());
    assertEquals(1, slots.count(2));
  }

  @Test
  void extractionOntoAStackOnlyTakesTheSameKindWithRoom() {
    String[] keys = new String[SLOTS];
    int[] counts = new int[SLOTS];
    keys[0] = "stone";
    counts[0] = 10;
    keys[1] = "dirt";
    counts[1] = 10;
    StorageStacking<String> slots = box(keys, counts, true, StorageGrid.NO_ITEM);

    assertEquals(new StorageStacking.Removal<>("stone", 1, 0), slots.removeOne("stone", 5));
    assertNull(slots.removeOne("stone", STACK), "a full cursor stack takes nothing");
    assertNull(slots.removeOne("torch", 1), "no torch inside");
  }

  @Test
  void bundlesInsertAtTheFrontAndStoreTheSelectionOneLower() {
    String[] keys = {null, "dirt", "stone"};
    int[] counts = {0, 12, 5};
    StorageStacking<String> slots =
        new StorageStacking<>(
            Arrays.asList(keys), counts, Objects::equals, key -> STACK, BUNDLE, false, 1);

    StorageStacking.Removal<String> removal = slots.removeOne(null, 0);

    assertEquals(new StorageStacking.Removal<>("stone", 5, 2), removal, "selection 1 is slot 2");
    assertEquals(2, slots.tryInsert("torch", 2, 2));
    assertEquals("torch", slots.key(0));
    assertEquals(StorageGrid.NO_ITEM, slots.selected(), "slot 0 is no item of the bundle");
  }

  @Test
  void anyMatchAndIsEmptyReadTheSlots() {
    String[] keys = new String[SLOTS];
    keys[3] = "stone";
    int[] counts = new int[SLOTS];
    counts[3] = 1;

    assertTrue(box(keys, counts, false, 0).anyMatch(Objects::equals, "stone"));
    assertFalse(box(keys, counts, false, 0).anyMatch(Objects::equals, "dirt"));
    assertFalse(box(keys, counts, false, 0).isEmpty());
    assertTrue(box(new String[SLOTS], new int[SLOTS], false, 0).isEmpty());
  }

  @Test
  void keysAndCountsMustMatch() {
    assertThrows(
        IllegalArgumentException.class,
        () -> box(new String[SLOTS], new int[1], false, StorageGrid.NO_ITEM));
  }
}
