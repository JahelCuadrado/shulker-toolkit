package com.einsteinmods.shulkertoolkit.core;

/**
 * The places a depth level reads, numbered as one list of sources so every feature searches them in
 * the same order: shallow places first, each kind in slot order. A source is one container (a
 * shulker box or a bundle) or one loose ender chest item. Each level reads a prefix of the list
 * ({@link CraftScope#deepest}), so a search is one bounded loop over source numbers.
 */
public enum StorageSource {
  /** The shulker box or bundle in an inventory slot (hotbar and main inventory). */
  INVENTORY_CONTAINER(Slots.INVENTORY, false, false),
  /** A bundle in a slot of a shulker box in the inventory. */
  BUNDLE_IN_INVENTORY_BOX(Slots.INVENTORY, true, false),
  /** A loose item in the ender chest. */
  ENDER_CHEST_ITEM(Slots.ENDER_CHEST, false, true),
  /** The shulker box or bundle in an ender chest slot. */
  ENDER_CHEST_CONTAINER(Slots.ENDER_CHEST, false, true),
  /** A bundle in a slot of a shulker box in the ender chest. */
  BUNDLE_IN_ENDER_CHEST_BOX(Slots.ENDER_CHEST, true, true);

  /** No box slot: the source sits directly in its inventory or ender chest slot. */
  public static final int NO_BOX_SLOT = -1;

  private static final StorageSource[] IN_ORDER = values();

  /** Slot counts of the vanilla containers the sources sit in. */
  public static final class Slots {
    /** Hotbar and main inventory ({@code Inventory#getNonEquipmentItems}). */
    public static final int INVENTORY = 36;

    /** {@code PlayerEnderChestContainer}. */
    public static final int ENDER_CHEST = 27;

    /** {@code ShulkerBoxBlockEntity#CONTAINER_SIZE}. */
    public static final int SHULKER_BOX = 27;

    private Slots() {}
  }

  static {
    int start = 0;
    for (StorageSource source : IN_ORDER) {
      source.start = start;
      start += source.size;
    }
  }

  private final boolean inBoxSlot;
  private final boolean inEnderChest;
  private final int size;
  private int start;

  StorageSource(int roots, boolean inBoxSlot, boolean inEnderChest) {
    this.inBoxSlot = inBoxSlot;
    this.inEnderChest = inEnderChest;
    this.size = inBoxSlot ? roots * Slots.SHULKER_BOX : roots;
  }

  /** The first source number of this kind. */
  public int start() {
    return start;
  }

  /** One past the last source number of this kind. */
  public int end() {
    return start + size;
  }

  /** Whether the source sits in the ender chest (slot numbers are ender chest slots). */
  public boolean inEnderChest() {
    return inEnderChest;
  }

  /** The inventory or ender chest slot holding {@code source} (or the box it is in). */
  public int rootSlot(int source) {
    int offset = source - start;
    return inBoxSlot ? offset / Slots.SHULKER_BOX : offset;
  }

  /** The box slot holding {@code source}, or {@link #NO_BOX_SLOT}. */
  public int boxSlot(int source) {
    return inBoxSlot ? (source - start) % Slots.SHULKER_BOX : NO_BOX_SLOT;
  }

  /** The kind of a source number below {@code BUNDLE_IN_ENDER_CHEST_BOX.end()}. */
  public static StorageSource of(int source) {
    for (StorageSource kind : IN_ORDER) {
      if (source < kind.end()) {
        return kind;
      }
    }
    throw new IllegalArgumentException("no storage source " + source);
  }
}
