package com.einsteinmods.shulkertoolkit.core;

/**
 * Where pinned windows go around a container screen. Columns ({@link #place}): up to {@link
 * #ROW_WINDOWS} to the right of the container, top-aligned with it and filled downwards, then up to
 * as many to its left (when allowed; otherwise, or once both are full, further columns outwards).
 * Rows ({@link #placeRows}): up to {@link #ROW_WINDOWS} side by side above the container, the next
 * ones below it, each row centred on the screen. A window that fits nowhere is {@link #HIDDEN}.
 * Pure and allocation-free; runs when a window is pinned or resized or the screen changes, never
 * per frame in steady state.
 */
public final class PinnedLayout {
  /** Position of a window that does not fit on the screen. */
  public static final int HIDDEN = Integer.MIN_VALUE;

  /** Space between windows and between a window and the container. */
  public static final int GAP = 2;

  /** Space kept free at the screen edges. */
  public static final int MARGIN = 2;

  /** Windows in one row above or below the container, or in one column beside it. */
  public static final int ROW_WINDOWS = 3;

  /** Window scales are eighths: full size, then smaller steps down to {@link #MIN_SCALE}. */
  public static final int FULL_SCALE = 8;

  /**
   * The smallest window: 3/8, slots of 6.75 GUI pixels with 6-pixel items. Small GUIs come with
   * large GUI scales (427 x 240 is GUI scale 2 at 854 x 480, 4 on a Retina Mac's default window),
   * so items stay 12 to 24 screen pixels, still recognisable; at 2/8 they would be 4 GUI pixels. It
   * is also what fits there beside the recipe book: 240 high leaves 33 pixels above and below the
   * screen, a 78-high shulker window fits at 3/8 (30) but not at 4/8 (39), and slots of 8 pixels
   * (35 high) would not fit either.
   */
  public static final int MIN_SCALE = 3;

  private PinnedLayout() {}

  /**
   * Places the windows at the largest scale (in eighths, {@link #FULL_SCALE} down to {@link
   * #MIN_SCALE}) at which every one of them fits, in rows ({@link #placeRows}) or columns ({@link
   * #place}); when none does, at the smallest, where some stay {@link #HIDDEN}. Positions are on
   * the screen; sizes are unscaled.
   *
   * @param rows rows above and below the container instead of columns beside it
   * @param scaledWidths scratch space for the scaled widths (as long as {@code widths})
   * @param scaledHeights scratch space for the scaled heights
   * @return the chosen scale in eighths
   */
  public static int placeScaled(
      int screenWidth,
      int screenHeight,
      int containerLeft,
      int containerTop,
      int containerRight,
      int containerBottom,
      boolean leftAllowed,
      boolean rows,
      int count,
      int[] widths,
      int[] heights,
      int[] scaledWidths,
      int[] scaledHeights,
      int[] outX,
      int[] outY) {
    for (int scale = FULL_SCALE; ; scale--) {
      for (int window = 0; window < count; window++) {
        scaledWidths[window] = scaled(widths[window], scale);
        scaledHeights[window] = scaled(heights[window], scale);
      }
      if (rows) {
        placeRows(
            screenWidth,
            screenHeight,
            containerTop,
            containerBottom,
            count,
            scaledWidths,
            scaledHeights,
            outX,
            outY);
      } else {
        place(
            screenWidth,
            screenHeight,
            containerLeft,
            containerTop,
            containerRight,
            leftAllowed,
            count,
            scaledWidths,
            scaledHeights,
            outX,
            outY);
      }
      if (scale == MIN_SCALE || allPlaced(count, outX)) {
        return scale;
      }
    }
  }

  /**
   * Places up to {@link #ROW_WINDOWS} windows side by side above the container (their bottoms on
   * its top edge), the next ones below it (their tops on its bottom edge); each row is centred on
   * the screen (the recipe book shifts the container off centre). A row that does not fit is {@link
   * #HIDDEN}.
   */
  public static void placeRows(
      int screenWidth,
      int screenHeight,
      int containerTop,
      int containerBottom,
      int count,
      int[] widths,
      int[] heights,
      int[] outX,
      int[] outY) {
    for (int first = 0; first < count; first += ROW_WINDOWS) {
      int last = Math.min(count, first + ROW_WINDOWS);
      boolean above = first == 0;
      int rowWidth = GAP * (last - first - 1);
      int rowHeight = 0;
      for (int window = first; window < last; window++) {
        rowWidth += widths[window];
        rowHeight = Math.max(rowHeight, heights[window]);
      }
      boolean fits =
          rowWidth <= screenWidth - 2 * MARGIN
              && (above
                  ? containerTop - GAP - rowHeight >= MARGIN
                  : containerBottom + GAP + rowHeight <= screenHeight - MARGIN);
      int x = (screenWidth - rowWidth) / 2;
      for (int window = first; window < last; window++) {
        outX[window] = fits ? x : HIDDEN;
        outY[window] =
            !fits ? HIDDEN : above ? containerTop - GAP - heights[window] : containerBottom + GAP;
        x += widths[window] + GAP;
      }
    }
  }

  /** {@code size} at {@code scale} eighths, rounded up so a scaled window never overlaps. */
  public static int scaled(int size, int scale) {
    return (size * scale + FULL_SCALE - 1) / FULL_SCALE;
  }

  private static boolean allPlaced(int count, int[] outX) {
    for (int window = 0; window < count; window++) {
      if (outX[window] == HIDDEN) {
        return false;
      }
    }
    return true;
  }

  /**
   * Places {@code count} windows of {@code widths} x {@code heights}, writing their top left
   * corners to {@code outX} and {@code outY} ({@link #HIDDEN} for both when one does not fit).
   *
   * @param leftAllowed false while something else uses the left of the container (the recipe book)
   */
  public static void place(
      int screenWidth,
      int screenHeight,
      int containerLeft,
      int containerTop,
      int containerRight,
      boolean leftAllowed,
      int count,
      int[] widths,
      int[] heights,
      int[] outX,
      int[] outY) {
    int top = Math.max(MARGIN, containerTop);
    int bottom = screenHeight - MARGIN;
    boolean right = true;
    // Right side: left edge of the current column; left side: its right edge.
    int edge = containerRight + GAP;
    int y = top;
    int columnWidth = 0;
    int inColumn = 0;
    for (int window = 0; window < count; window++) {
      outX[window] = HIDDEN;
      outY[window] = HIDDEN;
      int width = widths[window];
      int height = heights[window];
      if (top + height > bottom) {
        continue;
      }
      while (true) {
        if (inColumn > 0 && (inColumn == ROW_WINDOWS || y + height > bottom)) {
          // A full column: the left side next, or another column further out.
          if (right && leftAllowed) {
            right = false;
            edge = containerLeft - GAP;
          } else {
            edge = right ? edge + columnWidth + GAP : edge - columnWidth - GAP;
          }
          y = top;
          columnWidth = 0;
          inColumn = 0;
        }
        boolean fits = right ? edge + width <= screenWidth - MARGIN : edge - width >= MARGIN;
        if (fits) {
          outX[window] = right ? edge : edge - width;
          outY[window] = y;
          y += height + GAP;
          columnWidth = Math.max(columnWidth, width);
          inColumn++;
          break;
        }
        if (!right || !leftAllowed) {
          break;
        }
        right = false;
        edge = containerLeft - GAP;
        y = top;
        columnWidth = 0;
        inColumn = 0;
      }
    }
  }
}
