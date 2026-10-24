package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PinnedLayoutTest {
  private static final int CHEST_WIDTH = 176;
  private static final int CHEST_HEIGHT = 78;
  private static final int SCREEN_WIDTH = 640;
  private static final int SCREEN_HEIGHT = 360;
  private static final int SHORT_SCREEN_HEIGHT = 300;
  private static final int CONTAINER_LEFT = 232;
  private static final int CONTAINER_TOP = 97;
  private static final int CONTAINER_RIGHT = CONTAINER_LEFT + CHEST_WIDTH;
  private static final int H = PinnedLayout.HIDDEN;
  private static final int INVENTORY_HEIGHT = 166;
  private static final int HALF_SCALE = 4;

  private final int[] x = new int[PinnedPaths.MAX_DEPTH];
  private final int[] y = new int[PinnedPaths.MAX_DEPTH];

  private void place(int width, int height, boolean leftAllowed, int count, int[] heights) {
    int[] widths = {CHEST_WIDTH, CHEST_WIDTH, CHEST_WIDTH};
    PinnedLayout.place(
        width,
        height,
        CONTAINER_LEFT,
        CONTAINER_TOP,
        CONTAINER_RIGHT,
        leftAllowed,
        count,
        widths,
        heights,
        x,
        y);
  }

  @Test
  void oneWindowGoesRightOfTheContainerAtItsTop() {
    place(SCREEN_WIDTH, SCREEN_HEIGHT, true, 1, new int[] {CHEST_HEIGHT, 0, 0});

    assertEquals(CONTAINER_RIGHT + PinnedLayout.GAP, x[0]);
    assertEquals(CONTAINER_TOP, y[0]);
  }

  @Test
  void windowsStackDownwardsThenMoveToTheLeft() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    place(SCREEN_WIDTH, SHORT_SCREEN_HEIGHT, true, 3, heights);

    int right = CONTAINER_RIGHT + PinnedLayout.GAP;
    int second = CONTAINER_TOP + CHEST_HEIGHT + PinnedLayout.GAP;
    assertPrefix(new int[] {right, right, CONTAINER_LEFT - PinnedLayout.GAP - CHEST_WIDTH}, x);
    assertPrefix(new int[] {CONTAINER_TOP, second, CONTAINER_TOP}, y);
  }

  @Test
  void tallScreenFitsAllThreeOnTheRight() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    place(SCREEN_WIDTH, 480, true, 3, heights);

    int right = CONTAINER_RIGHT + PinnedLayout.GAP;
    assertPrefix(new int[] {right, right, right}, x);
  }

  @Test
  void withoutTheLeftSideASecondColumnOpensOnTheRight() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    place(1000, 270, false, 3, heights);

    int right = CONTAINER_RIGHT + PinnedLayout.GAP;
    assertPrefix(new int[] {right, right, right + CHEST_WIDTH + PinnedLayout.GAP}, x);
    assertEquals(CONTAINER_TOP, y[2]);
  }

  @Test
  void withoutRoomOrLeftSideWindowsAreHidden() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, 400};
    place(CONTAINER_RIGHT + 100, SCREEN_HEIGHT, false, 3, heights);

    assertPrefix(new int[] {H, H, H}, x);
    assertPrefix(new int[] {H, H, H}, y);
  }

  /** GUI scale 4 at 1080p: 480x270, the 176-wide inventory centred leaves 152 on each side. */
  private int placeScaled(int screenWidth, int screenHeight, int count, int[] heights) {
    int[] widths = new int[PinnedPaths.MAX_DEPTH];
    java.util.Arrays.fill(widths, CHEST_WIDTH);
    int left = (screenWidth - CHEST_WIDTH) / 2;
    int top = (screenHeight - INVENTORY_HEIGHT) / 2;
    return PinnedLayout.placeScaled(
        screenWidth,
        screenHeight,
        left,
        top,
        left + CHEST_WIDTH,
        top + INVENTORY_HEIGHT,
        true,
        false,
        count,
        widths,
        heights,
        new int[PinnedPaths.MAX_DEPTH],
        new int[PinnedPaths.MAX_DEPTH],
        x,
        y);
  }

  @Test
  void roomyScreenKeepsFullSize() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    assertEquals(PinnedLayout.FULL_SCALE, placeScaled(640, 360, 3, heights));
  }

  @Test
  void guiScaleFourShrinksToTheLargestStepThatFits() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    assertEquals(6, placeScaled(480, 270, 3, heights), "7/8 of 176 is 154, more than 152 - 4");
    int right = (480 + CHEST_WIDTH) / 2 + PinnedLayout.GAP;
    assertPrefix(new int[] {right, right, right}, x);
    int scaledHeight = PinnedLayout.scaled(CHEST_HEIGHT, 6);
    assertEquals(y[0] + scaledHeight + PinnedLayout.GAP, y[1]);
  }

  /**
   * 427 x 240 (854 x 480 at GUI scale 2, a Retina Mac's default window at automatic scale) with the
   * recipe book open: rows above and below have 33 pixels, too little at half size; 3/8 fits.
   */
  @Test
  void recipeBookAtGuiHeight240FitsAtThreeEighths() {
    int left = 202;
    int top = (240 - INVENTORY_HEIGHT) / 2;
    int[] widths = new int[PinnedPaths.MAX_DEPTH];
    java.util.Arrays.fill(widths, CHEST_WIDTH);
    int scale =
        PinnedLayout.placeScaled(
            427,
            240,
            left,
            top,
            left + CHEST_WIDTH,
            top + INVENTORY_HEIGHT,
            false,
            true,
            1,
            widths,
            sixChests(),
            new int[PinnedPaths.MAX_DEPTH],
            new int[PinnedPaths.MAX_DEPTH],
            x,
            y);
    assertEquals(PinnedLayout.MIN_SCALE, scale);
    assertTrue(x[0] != H, "the window is placed");
    assertEquals(top - PinnedLayout.GAP - PinnedLayout.scaled(CHEST_HEIGHT, scale), y[0]);
    assertTrue(y[0] >= PinnedLayout.MARGIN, "inside the screen");
  }

  @Test
  void belowTheSmallestScaleWindowsStayHidden() {
    int[] heights = {CHEST_HEIGHT, CHEST_HEIGHT, CHEST_HEIGHT};
    assertEquals(PinnedLayout.MIN_SCALE, placeScaled(CHEST_WIDTH + 100, 270, 3, heights));
    assertPrefix(new int[] {H, H, H}, x);
  }

  @Test
  void scaledSizesRoundUp() {
    assertEquals(132, PinnedLayout.scaled(CHEST_WIDTH, 6));
    assertEquals(59, PinnedLayout.scaled(CHEST_HEIGHT, 6));
    assertEquals(CHEST_WIDTH, PinnedLayout.scaled(CHEST_WIDTH, PinnedLayout.FULL_SCALE));
  }

  private static int[] sixChests() {
    int[] heights = new int[PinnedPaths.MAX_DEPTH];
    java.util.Arrays.fill(heights, CHEST_HEIGHT);
    return heights;
  }

  /** GUI scale 3 at 1080p (640x360): three on the right, three on the left, full size. */
  @Test
  void sixWindowsAtGuiScaleThreeFillBothSides() {
    assertEquals(PinnedLayout.FULL_SCALE, placeScaled(640, 360, 6, sixChests()));
    int right = (640 + CHEST_WIDTH) / 2 + PinnedLayout.GAP;
    int left = (640 - CHEST_WIDTH) / 2 - PinnedLayout.GAP - CHEST_WIDTH;
    assertPrefix(new int[] {right, right, right, left, left, left}, x);
    int top = (360 - INVENTORY_HEIGHT) / 2;
    int step = CHEST_HEIGHT + PinnedLayout.GAP;
    assertPrefix(new int[] {top, top + step, top + 2 * step, top, top + step, top + 2 * step}, y);
  }

  /** GUI scale 4 at 1080p (480x270): the same two columns at 6/8 size. */
  @Test
  void sixWindowsAtGuiScaleFourShrink() {
    assertEquals(6, placeScaled(480, 270, 6, sixChests()));
    int width = PinnedLayout.scaled(CHEST_WIDTH, 6);
    int right = (480 + CHEST_WIDTH) / 2 + PinnedLayout.GAP;
    int left = (480 - CHEST_WIDTH) / 2 - PinnedLayout.GAP - width;
    assertPrefix(new int[] {right, right, right, left, left, left}, x);
    for (int window = 0; window < PinnedPaths.MAX_DEPTH; window++) {
      assertTrue(y[window] + PinnedLayout.scaled(CHEST_HEIGHT, 6) <= 270, "window " + window);
    }
  }

  /** The first windows' positions (the arrays hold room for the maximum). */
  private static void assertPrefix(int[] expected, int[] actual) {
    assertArrayEquals(expected, java.util.Arrays.copyOf(actual, expected.length));
  }

  /** Rows above and below an inventory at {@code left}, vertically centred. */
  private int placeRows(int screenWidth, int screenHeight, int left, int count) {
    int top = (screenHeight - INVENTORY_HEIGHT) / 2;
    int[] widths = new int[PinnedPaths.MAX_DEPTH];
    java.util.Arrays.fill(widths, CHEST_WIDTH);
    return PinnedLayout.placeScaled(
        screenWidth,
        screenHeight,
        left,
        top,
        left + CHEST_WIDTH,
        top + INVENTORY_HEIGHT,
        false,
        true,
        count,
        widths,
        sixChests(),
        new int[PinnedPaths.MAX_DEPTH],
        new int[PinnedPaths.MAX_DEPTH],
        x,
        y);
  }

  @Test
  void fourToSixWindowsAtGuiScaleThreeGoAboveThenBelow() {
    int left = (640 - CHEST_WIDTH) / 2;
    int top = (360 - INVENTORY_HEIGHT) / 2;
    int above = top - PinnedLayout.GAP - CHEST_HEIGHT;
    int below = top + INVENTORY_HEIGHT + PinnedLayout.GAP;
    int rowLeft = left + CHEST_WIDTH / 2 - (3 * CHEST_WIDTH + 2 * PinnedLayout.GAP) / 2;
    int step = CHEST_WIDTH + PinnedLayout.GAP;
    for (int count = 4; count <= 6; count++) {
      assertEquals(PinnedLayout.FULL_SCALE, placeRows(640, 360, left, count), count + " windows");
      assertPrefix(new int[] {rowLeft, rowLeft + step, rowLeft + 2 * step}, x);
      assertPrefix(new int[] {above, above, above, below}, y);
    }
    int pair = left + CHEST_WIDTH / 2 - (2 * CHEST_WIDTH + PinnedLayout.GAP) / 2;
    placeRows(640, 360, left, 5);
    assertEquals(pair, x[3], "the second row of two is centred too");
  }

  @Test
  void fourToSixWindowsAtGuiScaleFourShrinkToHalf() {
    int left = (480 - CHEST_WIDTH) / 2;
    int top = (270 - INVENTORY_HEIGHT) / 2;
    for (int count = 4; count <= 6; count++) {
      assertEquals(HALF_SCALE, placeRows(480, 270, left, count), count + " windows");
      int height = PinnedLayout.scaled(CHEST_HEIGHT, HALF_SCALE);
      assertEquals(top - PinnedLayout.GAP - height, y[0]);
      assertEquals(top + INVENTORY_HEIGHT + PinnedLayout.GAP, y[3]);
      for (int window = 0; window < count; window++) {
        assertTrue(x[window] != H && y[window] >= PinnedLayout.MARGIN, "window " + window);
      }
    }
  }

  /** The recipe book pushes the inventory right; rows stay centred on the screen. */
  @Test
  void recipeBookRowsAreCentredOnTheScreen() {
    int left = 177 + (640 - CHEST_WIDTH - 200) / 2;
    int top = (360 - INVENTORY_HEIGHT) / 2;
    int above = top - PinnedLayout.GAP - CHEST_HEIGHT;
    assertEquals(PinnedLayout.FULL_SCALE, placeRows(640, 360, left, 1));
    assertEquals((640 - CHEST_WIDTH) / 2, x[0]);
    assertEquals(above, y[0]);
    placeRows(640, 360, left, 2);
    assertEquals((640 - 2 * CHEST_WIDTH - PinnedLayout.GAP) / 2, x[0]);
    int rowWidth = 3 * CHEST_WIDTH + 2 * PinnedLayout.GAP;
    assertEquals(PinnedLayout.FULL_SCALE, placeRows(640, 360, left, 6));
    int step = CHEST_WIDTH + PinnedLayout.GAP;
    int rowLeft = (640 - rowWidth) / 2;
    int below = top + INVENTORY_HEIGHT + PinnedLayout.GAP;
    assertPrefix(
        new int[] {
          rowLeft, rowLeft + step, rowLeft + 2 * step, rowLeft, rowLeft + step, rowLeft + 2 * step
        },
        x);
    assertPrefix(new int[] {above, above, above, below, below, below}, y);
  }

  /** «Always on the right»: six windows keep the column layout. */
  @Test
  void alwaysRightKeepsColumns() {
    assertEquals(PinnedLayout.FULL_SCALE, placeScaled(640, 360, 6, sixChests()));
    assertEquals((640 + CHEST_WIDTH) / 2 + PinnedLayout.GAP, x[0]);
    assertEquals((360 - INVENTORY_HEIGHT) / 2, y[0]);
  }

  /** Without the recipe book: columns, three on the right then the rest on the left. */
  @Test
  void fourToSixWindowsWithoutTheBookUseColumns() {
    for (int screen = 0; screen < 2; screen++) {
      int width = screen == 0 ? 640 : 480;
      int height = screen == 0 ? 360 : 270;
      for (int count = 4; count <= 6; count++) {
        int scale = placeScaled(width, height, count, sixChests());
        assertEquals(screen == 0 ? PinnedLayout.FULL_SCALE : 6, scale, count + " at " + width);
        int right = (width + CHEST_WIDTH) / 2 + PinnedLayout.GAP;
        int left =
            (width - CHEST_WIDTH) / 2 - PinnedLayout.GAP - PinnedLayout.scaled(CHEST_WIDTH, scale);
        for (int window = 0; window < count; window++) {
          assertEquals(window < 3 ? right : left, x[window], "window " + window);
        }
      }
    }
  }

  /** A roomy screen still puts at most three windows on the right before the left. */
  @Test
  void aTallRightSideTakesThreeWindowsAtMost() {
    assertEquals(PinnedLayout.FULL_SCALE, placeScaled(960, 540, 6, sixChests()));
    int right = (960 + CHEST_WIDTH) / 2 + PinnedLayout.GAP;
    int left = (960 - CHEST_WIDTH) / 2 - PinnedLayout.GAP - CHEST_WIDTH;
    assertPrefix(new int[] {right, right, right, left, left, left}, x);
  }
}
