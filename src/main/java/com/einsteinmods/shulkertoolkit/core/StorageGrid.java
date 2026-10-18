package com.einsteinmods.shulkertoolkit.core;

import java.util.function.IntPredicate;

/**
 * Grid geometry, selection scrolling and fill bar of container items (shulker boxes, the ender
 * chest, bundles), as Easy Shulker Boxes lays them out. Pure; runs on scroll, key and click events
 * and on cache misses, never per frame.
 */
public final class StorageGrid {
  /** No selected item, as vanilla {@code BundleContents.NO_SELECTED_ITEM_INDEX}. */
  public static final int NO_ITEM = -1;

  /** Columns of a shulker box and an ender chest. */
  public static final int CONTAINER_COLUMNS = 9;

  /** Rows of a shulker box, and of an ender chest whose size is not a multiple of 9. */
  public static final int CONTAINER_ROWS = 3;

  /** Width of a full item bar, as vanilla {@code GuiGraphicsExtractor#itemBar} draws it. */
  public static final int FULL_BAR_WIDTH = 13;

  /** Steps of a partial bar, as vanilla {@code BundleItem#getBarWidth} ({@code 1 + f * 12}). */
  private static final int PARTIAL_BAR_STEPS = 12;

  private StorageGrid() {}

  /** Rows of an ender chest grid of {@code size} slots, 9 per row. */
  public static int enderRows(int size) {
    return size % CONTAINER_COLUMNS == 0 ? size / CONTAINER_COLUMNS : CONTAINER_ROWS;
  }

  /** Columns of a bundle grid: the smallest square that holds {@code itemCount} stacks. */
  public static int bundleColumns(int itemCount) {
    return Math.max(1, (int) Math.ceil(Math.sqrt(itemCount)));
  }

  /** Rows of a bundle grid of {@code itemCount} stacks. */
  public static int bundleRows(int itemCount) {
    return -Math.floorDiv(-itemCount, bundleColumns(itemCount));
  }

  /**
   * The next non-empty slot from {@code selected} in direction ({@code scrollX}, {@code scrollY}):
   * horizontal steps wrap to the next or previous row, vertical steps to the next or previous
   * column; only the sign of a step counts and a horizontal step wins. {@link #NO_ITEM} when no
   * slot in the grid holds an item.
   *
   * @param selected current selection or {@link #NO_ITEM}, which starts before the first slot
   * @param size slots that exist; grid cells beyond it are skipped
   * @param hasItem whether a slot (below {@code size}) holds an item
   */
  public static int scroll(
      int selected,
      int columns,
      int rows,
      int size,
      IntPredicate hasItem,
      int scrollX,
      int scrollY) {
    if (columns <= 0 || rows <= 0) {
      return NO_ITEM;
    }
    int cells = columns * rows;
    int current = selected;
    for (int step = 0; step < cells; step++) {
      int x = current % columns;
      int y = current / columns;
      if (scrollX == 0) {
        y += Integer.signum(scrollY);
        if (y < 0) {
          y = rows - 1;
          x--;
        } else if (y >= rows) {
          y = 0;
          x++;
        }
        x = Math.floorMod(x, columns);
      } else {
        x += Integer.signum(scrollX);
        if (x < 0) {
          x = columns - 1;
          y--;
        } else if (x >= columns) {
          x = 0;
          y++;
        }
        y = Math.floorMod(y, rows);
      }
      current = y * columns + x;
      if (current >= 0 && current < size && hasItem.test(current)) {
        return current;
      }
    }
    return NO_ITEM;
  }

  /**
   * Fill bar width of a container item, 0 for none: one more step per filled slot fraction, as Easy
   * Shulker Boxes' {@code VisualItemStorage#getBarWidth} ({@code 1 + filled / size * 12}).
   */
  public static int barWidth(int filledSlots, int size) {
    if (filledSlots <= 0 || size <= 0) {
      return 0;
    }
    return Math.min(1 + filledSlots * PARTIAL_BAR_STEPS / size, FULL_BAR_WIDTH);
  }
}
