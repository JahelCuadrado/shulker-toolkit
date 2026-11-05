package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import com.einsteinmods.shulkertoolkit.core.CraftScope;
import com.einsteinmods.shulkertoolkit.pick.DepthStorage;
import fi.dy.masa.malilib.util.data.ItemType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

/**
 * Litematica's material list counts as available what the player's depth level can take out ({@link
 * DepthStorage}), instead of everything inside every shulker box and bundle. Litematica 0.29.1
 * {@code MaterialListUtils#getInventoryItemCounts} counts loose stacks by item ({@code new
 * ItemType(stack, true, false)}: components ignored) and, for a shulker box or bundle holding
 * items, its contents at any nesting instead of the item itself. Here loose stacks count the same
 * way; stored items count only where the depth level reaches, only in containers the mod edits, and
 * only without component changes, because a take-out request asks for the item the schematic needs
 * ({@code MaterialCache#getRequiredBuildItemForState}, a plain item) with its exact components: a
 * renamed copy is never taken, so it is never promised.
 *
 * <p>Only while a server with the mod allows picks, in survival, for the client player's own
 * inventory; otherwise Litematica counts as before. Litematica's own ender chest count ({@code
 * materialListCountEnderCache}) is skipped while the depth level already counts the ender chest, so
 * nothing counts twice.
 *
 * <p>Cost: Litematica calls this when the material list screen opens and every 2 seconds while its
 * info HUD shows the list ({@code MaterialListHudRenderer#render}), never per frame. One call reads
 * 41 inventory slots and at most 1,791 sources (level 3); it allocates the result map, one {@code
 * ItemType} per loose slot as Litematica does, and one stack and {@code ItemType} per distinct
 * stored item (stored entries are summed per item first), nothing per stored stack. The read of the
 * stored items measured 0.9 / 5.6 / 8.6 µs at levels 1-3 with 9 full boxes and 27 loose ender chest
 * stacks, and 1.0 / 18.4 / 49.6 µs with 4,860 stacks in bundles in boxes (2026-11-04, dev-only
 * harness, M-series Mac).
 */
public final class MaterialListCounts {
  private MaterialListCounts() {}

  /**
   * The available counts for {@code container}, or null to leave the count to Litematica.
   *
   * @param container what Litematica counts: the player's inventory or its ender chest cache
   */
  public static @Nullable Object2IntOpenHashMap<ItemType> countFor(Container container) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null || container != player.getInventory()) {
      return null;
    }
    CraftScope scope = scopeFor(player);
    return scope == null ? null : count(player.getInventory(), new DepthStorage(player, scope));
  }

  /** Whether {@link #countFor} already counts the ender chest for the client player. */
  public static boolean countsEnderChest() {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) {
      return false;
    }
    CraftScope scope = scopeFor(player);
    return scope != null && scope.readsEnderChest() && DepthStorage.carriesEnderChest(player);
  }

  private static @Nullable CraftScope scopeFor(LocalPlayer player) {
    return player.hasInfiniteMaterials() ? null : ShulkerToolkitClient.state().pickScope();
  }

  private static Object2IntOpenHashMap<ItemType> count(Inventory inventory, DepthStorage storage) {
    Object2IntOpenHashMap<ItemType> counts = new Object2IntOpenHashMap<>();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (!stack.isEmpty() && !holdsItems(stack)) {
        counts.addTo(new ItemType(stack, true, false), stack.getCount());
      }
    }
    Reference2IntOpenHashMap<Item> stored = new Reference2IntOpenHashMap<>();
    for (int source = 0; source < storage.sources(); source++) {
      int entries = storage.entries(source);
      for (int entry = 0; entry < entries; entry++) {
        ItemInstance item = storage.entry(source, entry);
        if (item != null && hasDefaultComponents(item)) {
          stored.addTo(item.typeHolder().value(), item.count());
        }
      }
    }
    for (Reference2IntMap.Entry<Item> entry : Reference2IntMaps.fastIterable(stored)) {
      counts.addTo(new ItemType(new ItemStack(entry.getKey()), false, false), entry.getIntValue());
    }
    return counts;
  }

  /** What a take-out request for a plain item can take: no component changes. */
  private static boolean hasDefaultComponents(ItemInstance item) {
    if (item instanceof ItemStackTemplate template) {
      return template.components().isEmpty();
    }
    return item instanceof ItemStack stack && stack.getComponentsPatch().isEmpty();
  }

  /** Litematica's rule for a container item it counts by contents instead of as an item. */
  private static boolean holdsItems(ItemStack stack) {
    if (stack.getItem() instanceof BlockItem blockItem
        && blockItem.getBlock() instanceof ShulkerBoxBlock) {
      ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
      return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }
    BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
    return stack.getItem() instanceof BundleItem && bundle != null && !bundle.isEmpty();
  }
}
