package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class StorageGridTest {
  private static final int COLUMNS = 9;
  private static final int ROWS = 3;
  private static final int SIZE = COLUMNS * ROWS;
  private static final IntPredicate EVERY_SLOT = slot -> true;

  private static int scroll(int selected, IntPredicate hasItem, int scrollX, int scrollY) {
    return StorageGrid.scroll(selected, COLUMNS, ROWS, SIZE, hasItem, scrollX, scrollY);
  }

  @Test
  void horizontalScrollStartsAtEitherEndWithoutASelection() {
    assertEquals(0, scroll(StorageGrid.NO_ITEM, EVERY_SLOT, 1, 0));
    assertEquals(SIZE - 1, scroll(StorageGrid.NO_ITEM, EVERY_SLOT, -1, 0));
  }

  @Test
  void horizontalScrollWrapsToTheNextRowAndAroundTheGrid() {
    assertEquals(COLUMNS, scroll(COLUMNS - 1, EVERY_SLOT, 1, 0));
    assertEquals(0, scroll(SIZE - 1, EVERY_SLOT, 1, 0));
    assertEquals(SIZE - 1, scroll(0, EVERY_SLOT, -1, 0));
  }

  @Test
  void verticalScrollWrapsToTheNextColumn() {
    assertEquals(COLUMNS, scroll(0, EVERY_SLOT, 0, 1));
    assertEquals(1, scroll(2 * COLUMNS, EVERY_SLOT, 0, 1));
    assertEquals(2 * COLUMNS + COLUMNS - 1, scroll(0, EVERY_SLOT, 0, -1));
  }

  /** Shift + wheel in a full box: down one row in the same column, then back up. */
  @Test
  void verticalScrollJumpsARowInAFullBox() {
    assertEquals(4 + COLUMNS, scroll(4, EVERY_SLOT, 0, 1));
    assertEquals(4, scroll(4 + COLUMNS, EVERY_SLOT, 0, -1));
  }

  @Test
  void emptySlotsAreSkippedAndAnEmptyGridSelectsNothing() {
    assertEquals(SIZE - 1, scroll(0, slot -> slot == 0 || slot == SIZE - 1, 1, 0));
    assertEquals(0, scroll(0, slot -> slot == 0, 1, 0));
    assertEquals(StorageGrid.NO_ITEM, scroll(0, slot -> false, 1, 0));
    assertEquals(StorageGrid.NO_ITEM, StorageGrid.scroll(0, 0, 0, 0, EVERY_SLOT, 1, 0));
  }

  @Test
  void horizontalStepWinsAndOnlyTheSignCounts() {
    assertEquals(1, scroll(0, EVERY_SLOT, 5, 3));
  }

  @Test
  void bundleGridIsTheSmallestSquare() {
    assertEquals(1, StorageGrid.bundleColumns(0));
    assertEquals(0, StorageGrid.bundleRows(0));
    assertEquals(1, StorageGrid.bundleColumns(1));
    assertEquals(2, StorageGrid.bundleColumns(2));
    assertEquals(1, StorageGrid.bundleRows(2));
    assertEquals(3, StorageGrid.bundleColumns(5));
    assertEquals(2, StorageGrid.bundleRows(5));
    assertEquals(8, StorageGrid.bundleColumns(64));
    assertEquals(8, StorageGrid.bundleRows(64));
  }

  @Test
  void enderChestRowsFollowItsSize() {
    assertEquals(3, StorageGrid.enderRows(SIZE));
    assertEquals(6, StorageGrid.enderRows(2 * SIZE));
    assertEquals(3, StorageGrid.enderRows(SIZE + 1));
  }

  @Test
  void barCountsFilledSlotsAndIsFullOnlyWhenAllAre() {
    assertEquals(0, StorageGrid.barWidth(0, SIZE));
    assertEquals(1, StorageGrid.barWidth(1, SIZE));
    assertEquals(1 + 13 * 12 / SIZE, StorageGrid.barWidth(13, SIZE));
    assertEquals(StorageGrid.FULL_BAR_WIDTH - 1, StorageGrid.barWidth(SIZE - 1, SIZE));
    assertEquals(StorageGrid.FULL_BAR_WIDTH, StorageGrid.barWidth(SIZE, SIZE));
  }
}
