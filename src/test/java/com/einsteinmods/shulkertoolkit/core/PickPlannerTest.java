package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PickPlannerTest {
  private static final int INVENTORY_SLOTS = 36;
  private static final int BOX_SLOTS = 27;
  private static final int MAX_STACK = 64;
  private static final int SMALL_STACK = 16;
  private static final int FIRST_BOX = 4;
  private static final int SECOND_BOX = 20;
  private static final int TARGET_HOTBAR_SLOT = 3;
  private static final int FREE_SLOT = 12;

  /** An inventory of {@link #INVENTORY_SLOTS} slots; {@code boxes[slot]} is null when no box. */
  private record FakeView(int[][] boxes) implements PickPlanner.BoxView {
    static FakeView empty() {
      return new FakeView(new int[INVENTORY_SLOTS][]);
    }

    FakeView withBox(int inventorySlot, int... matchingPerBoxSlot) {
      int[] box = new int[BOX_SLOTS];
      System.arraycopy(matchingPerBoxSlot, 0, box, 0, matchingPerBoxSlot.length);
      boxes[inventorySlot] = box;
      return this;
    }

    @Override
    public int inventorySlots() {
      return boxes.length;
    }

    @Override
    public int boxSlots(int inventorySlot) {
      int[] box = boxes[inventorySlot];
      return box == null ? 0 : box.length;
    }

    @Override
    public int matchingCount(int inventorySlot, int boxSlot) {
      return boxes[inventorySlot][boxSlot];
    }
  }

  @Test
  void singleItemWantsOneAndFullStackWantsTheMaxStackSize() {
    assertEquals(1, PickPlanner.wantedAmount(PickMode.SINGLE_ITEM, MAX_STACK));
    assertEquals(MAX_STACK, PickPlanner.wantedAmount(PickMode.FULL_STACK, MAX_STACK));
    assertEquals(SMALL_STACK, PickPlanner.wantedAmount(PickMode.FULL_STACK, SMALL_STACK));
  }

  @Test
  void noBoxOrNoMatchFindsNothing() {
    assertNull(PickPlanner.findExtraction(FakeView.empty(), MAX_STACK));
    assertNull(PickPlanner.findExtraction(FakeView.empty().withBox(FIRST_BOX, 0, 0), MAX_STACK));
  }

  @Test
  void firstBoxInInventoryOrderWins() {
    FakeView view = FakeView.empty().withBox(FIRST_BOX, 0, 5).withBox(SECOND_BOX, MAX_STACK);
    assertEquals(
        new PickPlanner.Extraction(FIRST_BOX, 5), PickPlanner.findExtraction(view, MAX_STACK));
  }

  @Test
  void fullStackSumsMatchingSlotsOfOneBoxUpToTheWantedAmount() {
    FakeView view = FakeView.empty().withBox(FIRST_BOX, 40, 0, 40, 40);
    assertEquals(
        new PickPlanner.Extraction(FIRST_BOX, MAX_STACK),
        PickPlanner.findExtraction(view, MAX_STACK));
  }

  @Test
  void singleItemTakesExactlyOne() {
    FakeView view = FakeView.empty().withBox(FIRST_BOX, MAX_STACK);
    assertEquals(new PickPlanner.Extraction(FIRST_BOX, 1), PickPlanner.findExtraction(view, 1));
  }

  @Test
  void zeroWantedFindsNothing() {
    assertNull(PickPlanner.findExtraction(FakeView.empty().withBox(FIRST_BOX, MAX_STACK), 0));
  }

  @Test
  void emptyTargetTakesThePickedStackWithoutMovingAnything() {
    assertEquals(
        new PickPlanner.Placement(TARGET_HOTBAR_SLOT, PickPlanner.NO_SLOT),
        PickPlanner.placement(TARGET_HOTBAR_SLOT, true, PickPlanner.NO_SLOT));
  }

  @Test
  void occupiedTargetMovesItsStackToTheFreeSlot() {
    assertEquals(
        new PickPlanner.Placement(TARGET_HOTBAR_SLOT, FREE_SLOT),
        PickPlanner.placement(TARGET_HOTBAR_SLOT, false, FREE_SLOT));
  }

  @Test
  void occupiedTargetWithoutFreeSlotIsRefused() {
    assertNull(PickPlanner.placement(TARGET_HOTBAR_SLOT, false, PickPlanner.NO_SLOT));
    assertNull(PickPlanner.placement(TARGET_HOTBAR_SLOT, false, TARGET_HOTBAR_SLOT));
  }

  @Test
  void takeFromSlotNeverExceedsWhatIsAvailableOrDue() {
    assertEquals(5, PickPlanner.takeFromSlot(5, MAX_STACK));
    assertEquals(MAX_STACK, PickPlanner.takeFromSlot(MAX_STACK, MAX_STACK));
    assertEquals(0, PickPlanner.takeFromSlot(MAX_STACK, 0));
  }
}
