package com.einsteinmods.shulkertoolkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageSourceTest {
  private static final int INVENTORY = StorageSource.Slots.INVENTORY;
  private static final int ENDER_CHEST = StorageSource.Slots.ENDER_CHEST;
  private static final int BOX = StorageSource.Slots.SHULKER_BOX;
  private static final int LAST_SLOT = BOX - 1;
  private static final int SOME_ROOT = 5;
  private static final int SOME_BOX_SLOT = 7;

  @Test
  void sourcesAreOneListShallowFirst() {
    StorageSource[] kinds = StorageSource.values();
    assertEquals(0, kinds[0].start());
    for (int index = 1; index < kinds.length; index++) {
      assertEquals(kinds[index - 1].end(), kinds[index].start(), kinds[index].name());
    }
    assertEquals(INVENTORY, StorageSource.INVENTORY_CONTAINER.end());
    assertEquals(
        INVENTORY + INVENTORY * BOX + ENDER_CHEST + ENDER_CHEST + ENDER_CHEST * BOX,
        StorageSource.BUNDLE_IN_ENDER_CHEST_BOX.end());
  }

  @Test
  void everySourceNumberDecodesToItsKindRootAndBoxSlot() {
    for (StorageSource kind : StorageSource.values()) {
      for (int source = kind.start(); source < kind.end(); source++) {
        assertEquals(kind, StorageSource.of(source));
        int boxSlot = kind.boxSlot(source);
        assertTrue(boxSlot == StorageSource.NO_BOX_SLOT || boxSlot <= LAST_SLOT);
        assertTrue(kind.rootSlot(source) < (kind.inEnderChest() ? ENDER_CHEST : INVENTORY));
      }
    }
  }

  @Test
  void boxSlotsOfOneRootAreConsecutive() {
    StorageSource kind = StorageSource.BUNDLE_IN_ENDER_CHEST_BOX;
    int source = kind.start() + SOME_ROOT * BOX + SOME_BOX_SLOT;

    assertEquals(SOME_ROOT, kind.rootSlot(source));
    assertEquals(SOME_BOX_SLOT, kind.boxSlot(source));
    assertEquals(
        SOME_ROOT,
        StorageSource.ENDER_CHEST_ITEM.rootSlot(
            StorageSource.ENDER_CHEST_ITEM.start() + SOME_ROOT));
    assertEquals(
        StorageSource.NO_BOX_SLOT,
        StorageSource.INVENTORY_CONTAINER.boxSlot(StorageSource.INVENTORY_CONTAINER.start()));
  }

  @Test
  void onlyEnderChestKindsAreInTheEnderChest() {
    assertFalse(StorageSource.INVENTORY_CONTAINER.inEnderChest());
    assertFalse(StorageSource.BUNDLE_IN_INVENTORY_BOX.inEnderChest());
    assertTrue(StorageSource.ENDER_CHEST_ITEM.inEnderChest());
    assertTrue(StorageSource.ENDER_CHEST_CONTAINER.inEnderChest());
    assertTrue(StorageSource.BUNDLE_IN_ENDER_CHEST_BOX.inEnderChest());
  }

  @Test
  void numbersPastTheListAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> StorageSource.of(StorageSource.BUNDLE_IN_ENDER_CHEST_BOX.end()));
  }
}
