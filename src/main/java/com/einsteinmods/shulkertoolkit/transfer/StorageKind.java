package com.einsteinmods.shulkertoolkit.transfer;

import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.core.StorageStacking;
import com.einsteinmods.shulkertoolkit.mixin.BundleContentsAccessor;
import com.einsteinmods.shulkertoolkit.mixin.ItemContainerContentsAccessor;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

/**
 * The container items whose contents can be seen and edited from an inventory, with Easy Shulker
 * Boxes' rules: shulker boxes ({@code ContainerStorage}, 9x3), the ender chest ({@code
 * EnderChestStorage}: the player's own ender inventory) and bundles ({@code BundleContentsStorage}:
 * a square grid, weight-limited, newest first). Side-agnostic; read on clicks and on cache misses.
 */
public enum StorageKind implements StorageStacking.Layout {
  SHULKER_BOX,
  ENDER_CHEST,
  BUNDLE;

  /** Slots of a shulker box item ({@code ShulkerBoxBlockEntity.CONTAINER_SIZE}). */
  public static final int SHULKER_SLOTS = ShulkerBoxBlockEntity.CONTAINER_SIZE;

  /** Default {@code Container#getMaxStackSize()}. */
  private static final int CONTAINER_MAX_STACK_SIZE = 99;

  /** The kind of {@code stack}, or null for every other item. Two {@code instanceof} checks. */
  public static @Nullable StorageKind of(ItemStack stack) {
    Item item = stack.getItem();
    if (item instanceof BlockItem blockItem) {
      Block block = blockItem.getBlock();
      if (block instanceof ShulkerBoxBlock) {
        return SHULKER_BOX;
      }
      return block == Blocks.ENDER_CHEST ? ENDER_CHEST : null;
    }
    return item instanceof BundleItem ? BUNDLE : null;
  }

  /**
   * Whether clicks may edit {@code stack}'s contents. A stack of several items is never edited (one
   * insert would fill every copy: a duplication), nor are shulker contents beyond 27 slots
   * (rewriting them as 27 would delete items) or bundles whose weight is invalid.
   */
  public boolean allowModification(ItemStack stack) {
    if (stack.getCount() != 1) {
      return false;
    }
    return switch (this) {
      case SHULKER_BOX -> {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        yield contents != null && contents.size() <= SHULKER_SLOTS;
      }
      case ENDER_CHEST -> true;
      case BUNDLE -> {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        yield contents != null && contents.weight().isSuccess();
      }
    };
  }

  /**
   * The immutable contents component of {@code stack} (identity changes with the contents), or null
   * for the ender chest, whose contents live in the player.
   */
  public @Nullable Object contents(ItemStack stack) {
    return switch (this) {
      case SHULKER_BOX -> stack.get(DataComponents.CONTAINER);
      case ENDER_CHEST -> null;
      case BUNDLE -> stack.get(DataComponents.BUNDLE_CONTENTS);
    };
  }

  /** Whether {@code stack} holds anything to show (a single item only, as Easy Shulker Boxes). */
  public boolean hasContents(ItemStack stack, Player player) {
    if (stack.getCount() != 1) {
      return false;
    }
    return switch (this) {
      case SHULKER_BOX -> {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        yield contents != null
            && contents != ItemContainerContents.EMPTY
            && (Object) contents instanceof ItemContainerContentsAccessor accessor
            && hasAnySlot(accessor);
      }
      case ENDER_CHEST -> !player.getEnderChestInventory().isEmpty();
      case BUNDLE -> {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        yield contents != null && !contents.isEmpty();
      }
    };
  }

  /**
   * Whether {@code other} may go in: never an empty stack, a shulker box or another refused item
   * ({@code Item#canFitInsideContainerItems}, as vanilla {@code ShulkerBoxSlot#mayPlace}).
   */
  public boolean canAccept(ItemStack stack, ItemStack other) {
    return !other.isEmpty()
        && allowModification(stack)
        && other.getItem().canFitInsideContainerItems();
  }

  /** How many of {@code other} may go in at most; 0 when {@link #canAccept} refuses. */
  public int acceptableCount(ItemStack stack, ItemStack other) {
    if (!canAccept(stack, other)) {
      return 0;
    }
    return this == BUNDLE
        ? Math.min(bundleRoomFor(stack, other), other.getCount())
        : other.getCount();
  }

  /**
   * Whether at least one of {@code other} fits. Allocation-free for the ender chest (read every
   * frame by the slot indicator, which caches the other kinds by contents).
   */
  public boolean canAddItem(ItemStack stack, ItemStack other, Player player) {
    if (!canAccept(stack, other)) {
      return false;
    }
    return switch (this) {
      case SHULKER_BOX -> view(stack, player).canAdd(other);
      case ENDER_CHEST -> {
        List<ItemStack> items = player.getEnderChestInventory().getItems();
        for (int slot = 0; slot < items.size(); slot++) {
          ItemStack item = items.get(slot);
          if (item.isEmpty()
              || (ItemStack.isSameItemSameComponents(item, other)
                  && item.getCount() < maxCount(item))) {
            yield true;
          }
        }
        yield false;
      }
      case BUNDLE -> bundleRoomFor(stack, other) > 0;
    };
  }

  /**
   * Whether the contents hold the same item as {@code other} (components too when asked).
   * Allocation-free for the ender chest, as {@link #canAddItem}.
   */
  public boolean hasAnyOf(ItemStack stack, ItemStack other, Player player, boolean sameComponents) {
    if (!canAccept(stack, other)) {
      return false;
    }
    if (this == ENDER_CHEST) {
      List<ItemStack> items = player.getEnderChestInventory().getItems();
      for (int slot = 0; slot < items.size(); slot++) {
        if (matches(items.get(slot), other, sameComponents)) {
          return true;
        }
      }
      return false;
    }
    return view(stack, player)
        .anyMatch(
            sameComponents ? ItemStack::isSameItemSameComponents : ItemStack::isSameItem, other);
  }

  /** The contents as shown and scrolled (bundles without their editing slot). */
  public StorageStacking<ItemStack> view(ItemStack stack, Player player) {
    return open(stack, player, false, false);
  }

  /**
   * A working copy of the contents for one click, with the player's selected slot.
   *
   * @param editable bundles get an extra empty first slot, where new items go (newest first)
   */
  public StorageStacking<ItemStack> open(
      ItemStack stack, Player player, boolean editable, boolean singleItem) {
    List<ItemStack> items = items(stack, player);
    int offset = editable && this == BUNDLE ? 1 : 0;
    List<@Nullable ItemStack> keys = new ArrayList<>(items.size() + offset);
    int[] counts = new int[items.size() + offset];
    if (offset == 1) {
      keys.add(null);
    }
    for (ItemStack item : items) {
      counts[keys.size()] = item.getCount();
      keys.add(item.isEmpty() ? null : item);
    }
    return new StorageStacking<>(
        keys,
        counts,
        ItemStack::isSameItemSameComponents,
        StorageKind::maxCount,
        this,
        singleItem,
        StorageStates.of(player).selectedItem());
  }

  /** Writes a working copy back (contents only when changed) and stores its selection. */
  public void save(ItemStack stack, Player player, StorageStacking<ItemStack> slots) {
    StorageStates.of(player).setSelectedItem(slots.selected());
    if (!slots.modified()) {
      return;
    }
    switch (this) {
      case SHULKER_BOX ->
          stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacksOf(slots)));
      case ENDER_CHEST -> {
        SimpleContainer container = player.getEnderChestInventory();
        List<ItemStack> stacks = stacksOf(slots);
        for (int slot = 0; slot < stacks.size(); slot++) {
          if (!ItemStack.matches(container.getItem(slot), stacks.get(slot))) {
            container.setItem(slot, stacks.get(slot));
          }
        }
      }
      case BUNDLE -> {
        List<ItemStackTemplate> templates = new ArrayList<>(slots.size());
        for (int slot = 0; slot < slots.size(); slot++) {
          ItemStack key = slots.key(slot);
          if (key != null) {
            templates.add(ItemStackTemplate.fromNonEmptyStack(key, slots.count(slot)));
          }
        }
        stack.set(
            DataComponents.BUNDLE_CONTENTS,
            templates.isEmpty() ? BundleContents.EMPTY : new BundleContents(templates));
      }
    }
  }

  /**
   * Sets the selected slot. Containers ignore a reset caused by a click ({@code slotClicked});
   * bundles also store it in their contents, as vanilla {@code BundleItem#toggleSelectedItem}.
   */
  public void toggleSelectedItem(
      ItemStack stack, Player player, int selectedItem, boolean slotClicked) {
    if (this == BUNDLE) {
      StorageStates.of(player).setSelectedItem(selectedItem);
      BundleItem.toggleSelectedItem(stack, selectedItem);
    } else if (!slotClicked) {
      StorageStates.of(player).setSelectedItem(selectedItem);
    }
  }

  /** Every slot's stack (empty ones too); live for the ender chest, copies otherwise. */
  public List<ItemStack> items(ItemStack stack, Player player) {
    return switch (this) {
      case SHULKER_BOX -> {
        NonNullList<ItemStack> slots = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(slots);
        yield slots;
      }
      case ENDER_CHEST -> player.getEnderChestInventory().getItems();
      case BUNDLE -> {
        BundleContents contents =
            stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        List<ItemStack> items = new ArrayList<>(contents.size());
        for (ItemStackTemplate template : contents.items()) {
          items.add(template.create());
        }
        yield items;
      }
    };
  }

  @Override
  public int columns(int size) {
    return switch (this) {
      case SHULKER_BOX, ENDER_CHEST -> StorageGrid.CONTAINER_COLUMNS;
      case BUNDLE -> StorageGrid.bundleColumns(size);
    };
  }

  @Override
  public int rows(int size) {
    return switch (this) {
      case SHULKER_BOX -> StorageGrid.CONTAINER_ROWS;
      case ENDER_CHEST -> StorageGrid.enderRows(size);
      case BUNDLE -> StorageGrid.bundleRows(size);
    };
  }

  @Override
  public boolean extractsFromFront() {
    return this == BUNDLE;
  }

  /**
   * Items of {@code other} that still fit in a bundle by weight, as vanilla {@code
   * BundleContents.Mutable#getMaxAmountToAdd}; 0 for an item without a weight.
   */
  private static int bundleRoomFor(ItemStack bundle, ItemStack other) {
    BundleContents contents =
        bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    DataResult<Fraction> itemWeight = BundleContentsAccessor.shulkertoolkit$weightOf(other);
    if (itemWeight.isError()) {
      return 0;
    }
    Fraction remaining = Fraction.ONE.subtract(contents.weight().result().orElse(Fraction.ONE));
    return Math.max(remaining.divideBy(itemWeight.getOrThrow()).intValue(), 0);
  }

  /** Largest stack in a container slot: {@code Container#getMaxStackSize(ItemStack)}. */
  public static int maxCount(ItemStack stack) {
    return Math.min(CONTAINER_MAX_STACK_SIZE, stack.getMaxStackSize());
  }

  private static boolean matches(ItemStack item, ItemStack other, boolean sameComponents) {
    return sameComponents
        ? ItemStack.isSameItemSameComponents(item, other)
        : ItemStack.isSameItem(item, other);
  }

  /** Whether any slot holds an item, without creating a stack or a stream. */
  private static boolean hasAnySlot(ItemContainerContentsAccessor contents) {
    List<Optional<ItemStackTemplate>> slots = contents.shulkertoolkit$slots();
    for (int slot = 0; slot < slots.size(); slot++) {
      if (slots.get(slot).isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static List<ItemStack> stacksOf(StorageStacking<ItemStack> slots) {
    List<ItemStack> stacks = new ArrayList<>(slots.size());
    for (int slot = 0; slot < slots.size(); slot++) {
      ItemStack key = slots.key(slot);
      stacks.add(key == null ? ItemStack.EMPTY : key.copyWithCount(slots.count(slot)));
    }
    return stacks;
  }
}
