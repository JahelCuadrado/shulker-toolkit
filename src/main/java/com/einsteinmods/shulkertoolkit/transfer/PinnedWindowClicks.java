package com.einsteinmods.shulkertoolkit.transfer;

import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

/**
 * Server side of pinned window clicks ({@link PinnedWindowClickPayload}), as vanilla {@code
 * ServerGamePacketListenerImpl#handleContainerClick} applies one menu click: the server finds the
 * container item again from the path, checks every level (same item, a single one, editable, kind
 * allowed), edits copies ({@link PinnedWindowEdit}), writes the result back up the path and only
 * then commits everything at once; vanilla's menu sync then sends the changed slots and cursor. A
 * click that fails a check changes nothing, so a box that moved, was dropped or changed meanwhile
 * can never duplicate items. Server thread only; work is bounded by the path (up to 6 levels) and
 * the slots involved.
 */
public final class PinnedWindowClicks {
  private PinnedWindowClicks() {}

  /** Payload handler: only for clients with the mod, under the player's policy. */
  public static void handle(ServerPlayer player, PinnedWindowClickPayload payload) {
    if (TransferGate.clientHasMod(player)) {
      apply(player, payload, ServerPlayerSessions.policy(player.getUUID()));
    }
  }

  /**
   * Applies one click with {@code policy} (GameTests: mock players have no mod channels).
   *
   * @return whether the click passed every check (it may still have moved nothing)
   */
  public static boolean apply(
      Player player, PinnedWindowClickPayload payload, ServerPolicy policy) {
    AbstractContainerMenu menu = player.containerMenu;
    List<PinnedWindowClickPayload.Level> levels = payload.levels();
    int depth = levels.size();
    if (!policy.allowPinnedWindows()
        || menu.containerId != payload.containerId()
        || !supports(menu)
        || !PinnedPaths.isValidDepth(depth)
        || !payload.action().accepts(payload.button())) {
      return false;
    }
    int rootIndex = levels.getFirst().index();
    if (!PinnedPaths.isValidIndex(rootIndex, menu.slots.size())) {
      return false;
    }
    Slot root = menu.getSlot(rootIndex);
    if (!root.allowModification(player)) {
      return false;
    }
    Chain chain = Chain.resolve(player, root.getItem(), levels, policy);
    if (chain == null) {
      return false;
    }
    StorageKind kind = chain.kinds[depth - 1];
    ItemStack target = chain.stacks[depth - 1];
    PinnedWindowEdit edit =
        new PinnedWindowEdit(kind, kind.items(target, player), menu.getCarried());
    Commit commit = new Commit();
    if (!edit(player, menu, root, payload, edit, commit)) {
      return false;
    }
    if (edit.changed() && !writeBack(player, chain, levels, kind, target, edit, commit)) {
      return false;
    }
    commit.apply(player, menu, root, edit.carried());
    return true;
  }

  /** The menus of the screens that show pinned windows (inventory, crafting, chests, furnaces). */
  public static boolean supports(AbstractContainerMenu menu) {
    return menu instanceof InventoryMenu
        || menu instanceof CraftingMenu
        || menu instanceof ChestMenu
        || menu instanceof ShulkerBoxMenu
        || menu instanceof AbstractFurnaceMenu;
  }

  /** The click on the working copy; side effects outside it are only recorded in {@code commit}. */
  private static boolean edit(
      Player player,
      AbstractContainerMenu menu,
      Slot root,
      PinnedWindowClickPayload payload,
      PinnedWindowEdit edit,
      Commit commit) {
    int slot = payload.slot();
    // If-chains instead of enum switches: no synthetic switch-map class in the jar.
    PinnedAction action = payload.action();
    if (action == PinnedAction.PICKUP) {
      return edit.pickup(slot, payload.button() == PinnedAction.PRIMARY_BUTTON);
    }
    if (action == PinnedAction.QUICK_MOVE_OUT) {
      if (!PinnedPaths.isValidIndex(slot, edit.slots().size())) {
        return false;
      }
      int room = inventoryRoom(player, menu, root, edit.slots().get(slot));
      commit.toInventory = edit.takeOut(slot, room);
      return true;
    }
    if (action == PinnedAction.QUICK_MOVE_IN) {
      if (!PinnedPaths.isValidIndex(slot, menu.slots.size())) {
        return false;
      }
      Slot source = menu.getSlot(slot);
      if (!isPlayerInventory(player, source) || source == root || !source.mayPickup(player)) {
        return false;
      }
      commit.source = source;
      commit.sourceTaken = edit.insert(source.getItem());
      return true;
    }
    int button = payload.button();
    Inventory inventory = player.getInventory();
    if (root.container == inventory && root.getContainerSlot() == button) {
      // The box itself would swap into its own window.
      return false;
    }
    ItemStack hotbar = edit.swap(slot, inventory.getItem(button));
    commit.hotbarButton = button;
    commit.hotbarStack = hotbar;
    return hotbar != null;
  }

  /**
   * Builds the edited item and every item above it, from the target up to the menu slot. An ender
   * chest stops the climb: its contents live in the player, so the items above it do not change.
   *
   * @return false when a bundle above would exceed its weight (bundle in bundle)
   */
  private static boolean writeBack(
      Player player,
      Chain chain,
      List<PinnedWindowClickPayload.Level> levels,
      StorageKind kind,
      ItemStack target,
      PinnedWindowEdit edit,
      Commit commit) {
    if (kind == StorageKind.ENDER_CHEST) {
      commit.enderSlots = edit.slots();
      return true;
    }
    ItemStack child = target.copy();
    if (kind == StorageKind.SHULKER_BOX) {
      child.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(edit.slots()));
    } else {
      child.set(DataComponents.BUNDLE_CONTENTS, edit.bundleContents());
    }
    for (int level = levels.size() - 2; level >= 0; level--) {
      int index = levels.get(level + 1).index();
      List<ItemStack> siblings = chain.items.get(level);
      StorageKind parentKind = chain.kinds[level];
      if (parentKind == StorageKind.ENDER_CHEST) {
        commit.enderSlot = index;
        commit.enderStack = child;
        return true;
      }
      ItemStack parent = chain.stacks[level].copy();
      if (parentKind == StorageKind.SHULKER_BOX) {
        List<ItemStack> items = new ArrayList<>(siblings);
        items.set(index, child);
        parent.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
      } else {
        BundleContents contents = replaced(siblings, index, child);
        if (!withinWeight(contents)) {
          return false;
        }
        parent.set(DataComponents.BUNDLE_CONTENTS, contents);
      }
      child = parent;
    }
    commit.rootStack = child;
    return true;
  }

  private static BundleContents replaced(List<ItemStack> items, int index, ItemStack child) {
    List<ItemStackTemplate> templates = new ArrayList<>(items.size());
    for (int slot = 0; slot < items.size(); slot++) {
      ItemStack stack = slot == index ? child : items.get(slot);
      if (!stack.isEmpty()) {
        templates.add(ItemStackTemplate.fromNonEmptyStack(stack));
      }
    }
    return new BundleContents(templates);
  }

  private static boolean withinWeight(BundleContents contents) {
    Fraction weight = contents.weight().result().orElse(null);
    return weight != null && weight.compareTo(Fraction.ONE) <= 0;
  }

  /**
   * How many of {@code stack} the player inventory takes, as {@link #moveToInventory} puts them
   * (the box's own slot excluded).
   */
  private static int inventoryRoom(
      Player player, AbstractContainerMenu menu, Slot root, ItemStack stack) {
    if (stack.isEmpty()) {
      return 0;
    }
    int room = 0;
    for (int index = menu.slots.size() - 1; index >= 0 && room < stack.getCount(); index--) {
      Slot slot = menu.getSlot(index);
      if (slot != root && isPlayerInventory(player, slot) && slot.mayPlace(stack)) {
        ItemStack current = slot.getItem();
        if (current.isEmpty()) {
          room += slot.getMaxStackSize(stack);
        } else if (ItemStack.isSameItemSameComponents(current, stack)) {
          room += Math.max(0, slot.getMaxStackSize(stack) - current.getCount());
        }
      }
    }
    return Math.min(room, stack.getCount());
  }

  /**
   * Puts {@code stack} in the player inventory as vanilla {@code ChestMenu#quickMoveStack} moves a
   * chest stack: onto stacks of the same item, then into empty slots, both from the last slot.
   */
  private static void moveToInventory(
      Player player, AbstractContainerMenu menu, Slot root, ItemStack stack) {
    for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
      boolean merge = pass == 0;
      for (int index = menu.slots.size() - 1; index >= 0 && !stack.isEmpty(); index--) {
        Slot slot = menu.getSlot(index);
        if (slot != root
            && isPlayerInventory(player, slot)
            && slot.getItem().isEmpty() != merge
            && (!merge || ItemStack.isSameItemSameComponents(slot.getItem(), stack))) {
          slot.safeInsert(stack);
        }
      }
    }
  }

  /** A hotbar or main inventory slot (not armour, offhand or crafting). */
  private static boolean isPlayerInventory(Player player, Slot slot) {
    return slot.container == player.getInventory()
        && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
  }

  /**
   * The checked path: each level's stack and kind, and the slots of every level above the target.
   */
  private static final class Chain {
    private final ItemStack[] stacks;
    private final StorageKind[] kinds;
    private final List<List<ItemStack>> items;

    private Chain(ItemStack[] stacks, StorageKind[] kinds, List<List<ItemStack>> items) {
      this.stacks = stacks;
      this.kinds = kinds;
      this.items = items;
    }

    static @Nullable Chain resolve(
        Player player,
        ItemStack rootStack,
        List<PinnedWindowClickPayload.Level> levels,
        ServerPolicy policy) {
      int depth = levels.size();
      ItemStack[] stacks = new ItemStack[depth];
      StorageKind[] kinds = new StorageKind[depth];
      List<List<ItemStack>> items = new ArrayList<>(depth);
      ItemStack stack = rootStack;
      for (int level = 0; level < depth; level++) {
        if (level > 0) {
          List<ItemStack> parentItems = kinds[level - 1].items(stacks[level - 1], player);
          items.add(parentItems);
          int index = levels.get(level).index();
          if (!PinnedPaths.isValidIndex(index, parentItems.size())) {
            return null;
          }
          stack = parentItems.get(index);
        }
        StorageKind kind = StorageKind.of(stack);
        if (kind == null
            || stack.getItem() != levels.get(level).item()
            || !kind.allowModification(stack)
            || (kind == StorageKind.ENDER_CHEST && !policy.allowEnderPreview())) {
          return null;
        }
        stacks[level] = stack;
        kinds[level] = kind;
      }
      return new Chain(stacks, kinds, items);
    }
  }

  /** Everything a click changes outside its working copy, applied only once it is valid. */
  private static final class Commit {
    private @Nullable ItemStack rootStack;
    private @Nullable List<ItemStack> enderSlots;
    private int enderSlot = -1;
    private @Nullable ItemStack enderStack;
    private @Nullable ItemStack toInventory;
    private @Nullable Slot source;
    private int sourceTaken;
    private int hotbarButton = -1;
    private @Nullable ItemStack hotbarStack;

    void apply(Player player, AbstractContainerMenu menu, Slot root, ItemStack carried) {
      SimpleContainer enderChest = player.getEnderChestInventory();
      boolean enderChanged = false;
      if (enderSlots != null) {
        for (int slot = 0; slot < enderSlots.size(); slot++) {
          if (!ItemStack.matches(enderChest.getItem(slot), enderSlots.get(slot))) {
            enderChest.setItem(slot, enderSlots.get(slot));
            enderChanged = true;
          }
        }
      }
      if (enderStack != null) {
        enderChest.setItem(enderSlot, enderStack);
        enderChanged = true;
      }
      if (rootStack != null) {
        root.set(rootStack);
      }
      menu.setCarried(carried);
      if (toInventory != null && !toInventory.isEmpty()) {
        moveToInventory(player, menu, root, toInventory);
      }
      if (source != null && sourceTaken > 0) {
        source.remove(sourceTaken);
        source.setChanged();
      }
      if (hotbarStack != null) {
        player.getInventory().setItem(hotbarButton, hotbarStack);
      }
      menu.broadcastChanges();
      if (enderChanged && player instanceof ServerPlayer serverPlayer) {
        EnderChestSync.sendContents(serverPlayer);
      }
    }
  }
}
