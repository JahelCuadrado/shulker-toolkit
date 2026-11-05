package com.einsteinmods.shulkertoolkit.pick;

import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.core.StorageSource;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.jspecify.annotations.Nullable;

/**
 * The items a player's {@link CraftScope} reaches, read as numbered sources ({@link StorageSource}
 * order): the shulker boxes and bundles in inventory slots 0-35, the bundles inside those boxes,
 * and, while an ender chest item is in those slots, the ender chest's loose items, its boxes and
 * bundles and the bundles inside its boxes. Crafting from boxes, the pick from boxes and
 * Litematica's material list read through this one class, so counting and taking see the same items
 * with the same rules. Reads are side-agnostic; {@link #take} changes the player's items (server).
 *
 * <p>Only editable containers count, as clicks edit them ({@link StorageKind#allowModification}): a
 * box stacked with others, a box with more than 27 slots or a bundle stacked or with an invalid
 * weight is skipped, so taking never rewrites shared or oversized contents. Bundles inside bundles
 * stay closed: they are items.
 *
 * <p>One instance per request (a pick, a recipe book refresh, a recipe click, a material count); it
 * caches the source being read, and contents components are read in place, without copies.
 */
public final class DepthStorage {
  private static final int NO_SOURCE = -1;
  private static final int SINGLE_ITEM = 1;
  private static final int LOOSE_ITEM_ENTRIES = 1;

  private final Player player;
  private final NonNullList<ItemStack> inventory;
  private final SimpleContainer enderChest;
  private final int sources;

  private int readSource = NO_SOURCE;
  private @Nullable List<Optional<ItemStackTemplate>> readBox;
  private @Nullable List<ItemStackTemplate> readBundle;
  private ItemStack readLoose = ItemStack.EMPTY;

  /** The box whose slots the bundle sources being read sit in: read once for its 27 sources. */
  private int boxRootKey = NO_SOURCE;

  private @Nullable List<Optional<ItemStackTemplate>> boxRoot;

  public DepthStorage(Player player, CraftScope scope) {
    this.player = player;
    this.inventory = player.getInventory().getNonEquipmentItems();
    this.enderChest = player.getEnderChestInventory();
    this.sources = scope.deepest(carriesEnderChest(player)).end();
  }

  /** Sources to read; source numbers run from 0 to this, exclusive. */
  public int sources() {
    return sources;
  }

  /**
   * Entries of {@code source}: the slots of a box (empty ones included), the items of a bundle, one
   * for a loose ender chest item; 0 when it holds no editable container or is empty.
   */
  public int entries(int source) {
    read(source);
    if (readBox != null) {
      return readBox.size();
    }
    if (readBundle != null) {
      return readBundle.size();
    }
    return readLoose.isEmpty() ? 0 : LOOSE_ITEM_ENTRIES;
  }

  /**
   * The item in {@code entry} of {@code source}, or null for an empty box slot: an {@link
   * ItemStackTemplate} inside a container, the live {@link ItemStack} for a loose ender chest item
   * (never change it).
   */
  public @Nullable ItemInstance entry(int source, int entry) {
    read(source);
    if (readBox != null) {
      return readBox.get(entry).orElse(null);
    }
    if (readBundle != null) {
      return readBundle.get(entry);
    }
    return readLoose;
  }

  /** Items of {@code entry} matching {@code wanted} (same item and components); 0 when none. */
  public int matchingCount(int source, int entry, WantedItem wanted) {
    ItemInstance item = entry(source, entry);
    if (item instanceof ItemStackTemplate template) {
      return wanted.matches(template) ? template.count() : 0;
    }
    return item instanceof ItemStack stack && wanted.matches(stack) ? stack.getCount() : 0;
  }

  /**
   * The inventory slot holding {@code source} (or the box it is in), or {@link PickPlanner#NO_SLOT}
   * for an ender chest source.
   */
  public int inventorySlot(int source) {
    StorageSource kind = StorageSource.of(source);
    return kind.inEnderChest() ? PickPlanner.NO_SLOT : kind.rootSlot(source);
  }

  /**
   * Removes up to {@code amount} items matching {@code wanted} from the entries of {@code source},
   * in entry order, and returns them as one stack (empty when none matched). Writes the whole chain
   * back in place: the bundle into its box slot, the box or bundle into its inventory or ender
   * chest slot. An ender chest change is marked and its slot sent to the client.
   */
  public ItemStack take(int source, WantedItem wanted, int amount) {
    read(source);
    StorageSource kind = StorageSource.of(source);
    int rootSlot = kind.rootSlot(source);
    ItemStack root = rootStack(kind, rootSlot);
    ItemStack taken;
    if (readBox != null) {
      taken = BoxContents.take(root, wanted, amount);
    } else if (readBundle != null) {
      taken = takeFromBundle(root, kind.boxSlot(source), readBundle, wanted, amount);
    } else if (!readLoose.isEmpty() && wanted.matches(readLoose)) {
      taken = enderChest.removeItem(rootSlot, Math.min(amount, readLoose.getCount()));
    } else {
      taken = ItemStack.EMPTY;
    }
    readSource = NO_SOURCE;
    boxRootKey = NO_SOURCE;
    if (!taken.isEmpty() && kind.inEnderChest()) {
      if (kind != StorageSource.ENDER_CHEST_ITEM) {
        // Changed in place; removeItem above marks it itself.
        enderChest.setChanged();
      }
      if (player instanceof ServerPlayer serverPlayer) {
        EnderChestSync.sendSlot(serverPlayer, rootSlot);
      }
    }
    return taken;
  }

  /**
   * Whether an ender chest item is in inventory slots 0-35 (a stack too: its contents live in the
   * player); the ender chest is read only then.
   */
  public static boolean carriesEnderChest(Player player) {
    NonNullList<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
    for (int slot = 0; slot < inventory.size(); slot++) {
      if (inventory.get(slot).is(Items.ENDER_CHEST)) {
        return true;
      }
    }
    return false;
  }

  private ItemStack rootStack(StorageSource kind, int rootSlot) {
    if (kind.inEnderChest()) {
      return rootSlot < enderChest.getContainerSize()
          ? enderChest.getItem(rootSlot)
          : ItemStack.EMPTY;
    }
    return rootSlot < inventory.size() ? inventory.get(rootSlot) : ItemStack.EMPTY;
  }

  private void read(int source) {
    if (source == readSource) {
      return;
    }
    readSource = source;
    readBox = null;
    readBundle = null;
    readLoose = ItemStack.EMPTY;
    StorageSource kind = StorageSource.of(source);
    int rootSlot = kind.rootSlot(source);
    int boxSlot = kind.boxSlot(source);
    if (kind == StorageSource.ENDER_CHEST_ITEM) {
      readLoose = rootStack(kind, rootSlot);
    } else if (boxSlot == StorageSource.NO_BOX_SLOT) {
      ItemStack root = rootStack(kind, rootSlot);
      readBox = editableBoxSlots(root);
      readBundle = readBox == null ? editableBundle(root) : null;
    } else {
      int rootKey = kind.start() + rootSlot;
      if (rootKey != boxRootKey) {
        boxRootKey = rootKey;
        boxRoot = editableBoxSlots(rootStack(kind, rootSlot));
      }
      List<Optional<ItemStackTemplate>> box = boxRoot;
      if (box != null && boxSlot < box.size() && box.get(boxSlot).isPresent()) {
        readBundle = editableBundle(box.get(boxSlot).get());
      }
    }
  }

  /**
   * Takes from a bundle in an inventory or ender chest slot ({@code boxSlot} is {@link
   * StorageSource#NO_BOX_SLOT}) or in a slot of the box there.
   */
  private static ItemStack takeFromBundle(
      ItemStack root, int boxSlot, List<ItemStackTemplate> items, WantedItem wanted, int amount) {
    List<ItemStackTemplate> updated = new ArrayList<>(items.size());
    int remaining = amount;
    for (int index = 0; index < items.size(); index++) {
      ItemStackTemplate item = items.get(index);
      int taken = remaining > 0 && wanted.matches(item) ? Math.min(remaining, item.count()) : 0;
      remaining -= taken;
      if (taken < item.count()) {
        updated.add(taken == 0 ? item : item.withCount(item.count() - taken));
      }
    }
    int takenTotal = amount - remaining;
    if (takenTotal == 0) {
      return ItemStack.EMPTY;
    }
    // Vanilla's shape for an emptied bundle; the selection resets with the new contents.
    BundleContents contents =
        updated.isEmpty() ? BundleContents.EMPTY : new BundleContents(List.copyOf(updated));
    if (boxSlot == StorageSource.NO_BOX_SLOT) {
      root.set(DataComponents.BUNDLE_CONTENTS, contents);
    } else {
      List<Optional<ItemStackTemplate>> slots = BoxContents.slotsOf(root);
      if (slots == null) {
        return ItemStack.EMPTY;
      }
      ItemStack bundle = slots.get(boxSlot).orElseThrow().create();
      bundle.set(DataComponents.BUNDLE_CONTENTS, contents);
      List<Optional<ItemStackTemplate>> updatedSlots = new ArrayList<>(slots);
      updatedSlots.set(boxSlot, Optional.of(ItemStackTemplate.fromNonEmptyStack(bundle)));
      BoxContents.setSlots(root, updatedSlots);
    }
    return wanted.createStack(takenTotal);
  }

  /** The slots of a single shulker box with at most 27 slots, or null. */
  private static @Nullable List<Optional<ItemStackTemplate>> editableBoxSlots(ItemStack stack) {
    List<Optional<ItemStackTemplate>> slots = BoxContents.slotsOf(stack);
    return slots != null && slots.size() <= StorageKind.SHULKER_SLOTS ? slots : null;
  }

  /** The items of a single bundle with a valid weight, or null for every other item. */
  private static @Nullable List<ItemStackTemplate> editableBundle(ItemInstance item) {
    if (item.count() != SINGLE_ITEM) {
      return null;
    }
    BundleContents contents = item.get(DataComponents.BUNDLE_CONTENTS);
    return contents != null && contents.weight().isSuccess() ? contents.items() : null;
  }
}
