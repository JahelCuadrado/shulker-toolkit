package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.config.ClientPreferences;
import com.einsteinmods.shulkertoolkit.client.config.SlotHighlight;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

/**
 * Contents tooltips of container items, from {@code ItemStackMixin} on {@code
 * ItemStack#getTooltipImage} (asked several times per frame while an item is hovered, and for every
 * tooltip). Any other item pays two {@code instanceof} checks. Tooltips are cached by contents
 * instance (immutable components; a hovered stack keeps its instance) plus selection and style, in
 * a few entries so the cursor's and the hovered item's tooltips can alternate within a frame
 * without rebuilding: steady state costs a few comparisons and no allocation. The ender chest
 * tooltip draws the client's live ender inventory, synced by the server. Render thread only.
 */
public final class StorageTooltips {
  /** Ender chest panel colour of Easy Shulker Boxes' {@code EnderChestStorage}. */
  private static final int ENDER_CHEST_COLOR = ARGB.opaque(0x2A6255);

  /**
   * Trial look: the ender chest panel shows vanilla's static End portal starfield behind its
   * translucent slots ({@link ContainerTooltip#extractPanel}). False restores the plain panel.
   */
  private static final boolean ENDER_STARFIELD = true;

  /** Bundle panel colour: the leather of vanilla's {@code textures/item/bundle.png}. */
  private static final int BUNDLE_LEATHER_COLOR = ARGB.opaque(0xCD7B46);

  private static final int NO_TINT = -1;

  /** Entries per kind: hovered stack, cursor stack and a nested one fit without eviction. */
  private static final int ENTRIES = 4;

  private static final @Nullable ShulkerEntry[] SHULKER_ENTRIES = new ShulkerEntry[ENTRIES];
  private static int nextShulker;

  private static final @Nullable BundleGridTooltip[] BUNDLE_TOOLTIPS =
      new BundleGridTooltip[ENTRIES];
  private static int nextBundle;

  private static @Nullable ContainerTooltip enderTooltip;

  private StorageTooltips() {}

  /** A shulker box's contents, its slot stacks and the tooltip that shows them. */
  private record ShulkerEntry(
      ItemContainerContents contents, List<ItemStack> items, ContainerTooltip tooltip) {}

  /** The contents tooltip of {@code stack}, or null to keep vanilla's. */
  public static @Nullable CollapsibleTooltip imageFor(ItemStack stack) {
    StorageKind kind = StorageKind.of(stack);
    if (kind == null
        || (kind == StorageKind.BUNDLE && !StorageInput.preferences().bundleInteractions())) {
      return null;
    }
    Minecraft minecraft = Minecraft.getInstance();
    LocalPlayer player = minecraft.player;
    if (player == null || !minecraft.isSameThread() || !kind.hasContents(stack, player)) {
      return null;
    }
    ClientPreferences preferences = StorageInput.preferences();
    int selected = StorageStates.of(player).selectedItem();
    if (kind == StorageKind.SHULKER_BOX) {
      return shulkerImage(stack, preferences, selected);
    }
    return kind == StorageKind.ENDER_CHEST
        ? enderImage(player.getEnderChestInventory(), preferences, selected)
        : bundleImage(stack);
  }

  /**
   * Whether vanilla's text list of a shulker box's contents is dropped: always for shulker boxes,
   * whose contents show in the grid (Easy Shulker Boxes' {@code ClientEventHandler#onItemTooltip}).
   * Pure, so safe off the render thread (creative search trees build tooltips there).
   */
  public static boolean replacesContainerLines(ItemStack stack) {
    return StorageKind.of(stack) == StorageKind.SHULKER_BOX;
  }

  public static void clear() {
    Arrays.fill(SHULKER_ENTRIES, null);
    Arrays.fill(BUNDLE_TOOLTIPS, null);
    enderTooltip = null;
  }

  private static @Nullable CollapsibleTooltip shulkerImage(
      ItemStack stack, ClientPreferences preferences, int selected) {
    ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
    if (contents == null || !shows(stack, DataComponents.CONTAINER)) {
      return null;
    }
    int color = preferences.itemStorageColors() ? shulkerColor(stack) : NO_TINT;
    SlotHighlight highlight = preferences.itemStorageHighlightSprite();
    for (int index = 0; index < ENTRIES; index++) {
      ShulkerEntry entry = SHULKER_ENTRIES[index];
      if (entry != null && entry.contents() == contents) {
        if (!entry.tooltip().shows(entry.items(), color, selected, highlight)) {
          entry =
              new ShulkerEntry(
                  contents,
                  entry.items(),
                  shulkerTooltip(entry.items(), color, selected, highlight));
          SHULKER_ENTRIES[index] = entry;
        }
        return entry.tooltip();
      }
    }
    NonNullList<ItemStack> items = NonNullList.withSize(StorageKind.SHULKER_SLOTS, ItemStack.EMPTY);
    contents.copyInto(items);
    ShulkerEntry entry =
        new ShulkerEntry(contents, items, shulkerTooltip(items, color, selected, highlight));
    SHULKER_ENTRIES[nextShulker] = entry;
    nextShulker = (nextShulker + 1) % ENTRIES;
    return entry.tooltip();
  }

  private static CollapsibleTooltip enderImage(
      SimpleContainer enderChest, ClientPreferences preferences, int selected) {
    List<ItemStack> items = enderChest.getItems();
    int color = preferences.itemStorageColors() ? ENDER_CHEST_COLOR : NO_TINT;
    SlotHighlight highlight = preferences.itemStorageHighlightSprite();
    ContainerTooltip tooltip = enderTooltip;
    if (tooltip == null || !tooltip.shows(items, color, selected, highlight)) {
      tooltip =
          new ContainerTooltip(
              items,
              StorageGrid.CONTAINER_COLUMNS,
              StorageGrid.enderRows(items.size()),
              color,
              selected,
              highlight);
      enderTooltip = tooltip;
    }
    return tooltip;
  }

  private static @Nullable CollapsibleTooltip bundleImage(ItemStack stack) {
    BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
    if (contents == null || !shows(stack, DataComponents.BUNDLE_CONTENTS)) {
      return null;
    }
    int color = panelColor(StorageKind.BUNDLE, stack);
    for (BundleGridTooltip tooltip : BUNDLE_TOOLTIPS) {
      if (tooltip != null && tooltip.shows(contents, color)) {
        return tooltip;
      }
    }
    Fraction weight = contents.weight().result().orElse(null);
    if (weight == null) {
      return null;
    }
    BundleGridTooltip tooltip =
        new BundleGridTooltip(contents, weight, color, BundleGridTooltip.TOOLTIP_MIN_COLUMNS);
    BUNDLE_TOOLTIPS[nextBundle] = tooltip;
    nextBundle = (nextBundle + 1) % ENTRIES;
    return tooltip;
  }

  private static ContainerTooltip shulkerTooltip(
      List<ItemStack> items, int color, int selected, SlotHighlight highlight) {
    return new ContainerTooltip(
        items,
        StorageGrid.CONTAINER_COLUMNS,
        StorageGrid.CONTAINER_ROWS,
        color,
        selected,
        highlight);
  }

  /** The panel tint of a container item's grid, as its tooltip draws it (-1: none). */
  static int panelColor(StorageKind kind, ItemStack stack) {
    if (!StorageInput.preferences().itemStorageColors()) {
      return NO_TINT;
    }
    if (kind == StorageKind.BUNDLE) {
      return BUNDLE_LEATHER_COLOR;
    }
    return kind == StorageKind.ENDER_CHEST ? ENDER_CHEST_COLOR : shulkerColor(stack);
  }

  /** Whether a panel of {@code color} is the ender chest's and gets the starfield. */
  static boolean starfield(int color) {
    return ENDER_STARFIELD && color == ENDER_CHEST_COLOR;
  }

  /** Dyed boxes take their dye's colour; the undyed box is not tinted, as Easy Shulker Boxes. */
  private static int shulkerColor(ItemStack stack) {
    if (stack.getItem() instanceof BlockItem blockItem
        && blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBox) {
      DyeColor dye = shulkerBox.getColor();
      return dye == null ? NO_TINT : ARGB.opaque(dye.getTextureDiffuseColor());
    }
    return NO_TINT;
  }

  /** Map makers can hide contents with {@code tooltip_display}, as vanilla bundles honour. */
  private static boolean shows(ItemStack stack, DataComponentType<?> type) {
    return stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT).shows(type);
  }
}
