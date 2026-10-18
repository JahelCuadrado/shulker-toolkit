package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

/**
 * A bundle's contents as Easy Shulker Boxes' {@code ClientBundleContentsTooltip}: every stack in a
 * square grid (newest at the bottom right, as vanilla's {@code ClientBundleTooltip}) on the same
 * chest panel as shulker boxes ({@link ContainerTooltip#extractPanel}), tinted leather brown, with
 * vanilla's fill bar naming the percentage under the slots. Built once per contents and tint
 * (stacks, texts and bar fill precomputed). Also draws pinned bundle windows, with the panel's
 * title strip.
 */
final class BundleGridTooltip extends CollapsibleTooltip {
  /** No stack under a point ({@link #indexAt}). */
  static final int NO_INDEX = -1;

  /** Narrowest tooltip grid: the fill bar and its percentage need two slots of width. */
  static final int TOOLTIP_MIN_COLUMNS = 2;

  private static final int SLOT = ContainerTooltip.SLOT;
  private static final int BORDER = ContainerTooltip.BORDER;
  private static final int ITEM_INSET = 1;

  /** Vanilla hovered-slot sprites, as {@link ContainerTooltip} marks the selected slot. */
  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");

  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");
  private static final int HIGHLIGHT_INSET = 3;
  private static final int HIGHLIGHT_SIZE = 24;

  /** Bar sprites and sizes of vanilla {@code ClientBundleTooltip}. */
  private static final Identifier BAR_FILL =
      Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");

  private static final Identifier BAR_FULL =
      Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
  private static final Identifier BAR_BORDER =
      Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
  private static final Component FULL = Component.translatable("item.minecraft.bundle.full");
  private static final String PERCENT = "loading.progress";
  private static final int BAR_HEIGHT = 13;
  private static final int BAR_MARGIN = 2;

  /** Room in the panel below the slots for the fill bar. */
  private static final int BAR_AREA = BAR_HEIGHT + 2 * BAR_MARGIN;

  private static final int BAR_TEXT_TOP = 3;
  private static final int BAR_BORDER_WIDTH = 2;
  private static final int PERCENT_SCALE = 100;
  private static final int WHITE = -1;

  private final BundleContents contents;
  private final ItemStack[] stacks;
  private final int selected;
  private final int columns;
  private final int rows;
  private final int color;
  private final boolean full;
  private final int barFill;
  private final Component barText;

  /**
   * @param color tint of the panel, -1 for none
   * @param minColumns narrowest grid (a pinned window also needs room for its title)
   */
  BundleGridTooltip(BundleContents contents, Fraction weight, int color, int minColumns) {
    this.contents = contents;
    List<ItemStackTemplate> items = contents.items();
    this.stacks = new ItemStack[items.size()];
    for (int index = 0; index < stacks.length; index++) {
      stacks[index] = items.get(index).create();
    }
    this.selected = contents.getSelectedItemIndex();
    this.color = color;
    this.columns = Math.max(minColumns, StorageGrid.bundleColumns(stacks.length));
    this.rows = Math.max(1, -Math.floorDiv(-stacks.length, columns));
    this.full = weight.compareTo(Fraction.ONE) >= 0;
    int fillMax = columns * SLOT - BAR_BORDER_WIDTH;
    this.barFill = Mth.clamp(Mth.mulAndTruncate(weight, fillMax), 0, fillMax);
    this.barText =
        full ? FULL : Component.translatable(PERCENT, (int) (weight.doubleValue() * PERCENT_SCALE));
  }

  /** Stacks shown, newest first (the contents order). */
  int size() {
    return stacks.length;
  }

  ItemStack stack(int index) {
    return stacks[index];
  }

  /** Whether this tooltip already shows exactly {@code candidate} with this tint. */
  boolean shows(BundleContents candidate, int newColor) {
    return candidate == contents && newColor == color;
  }

  /** Width of the panel (and so of the tooltip). */
  int panelWidth() {
    return columns * SLOT + 2 * BORDER;
  }

  /** Height of the panel with a top part of {@code topHeight}, the fill bar inside it. */
  int panelHeight(int topHeight) {
    return topHeight + rows * SLOT + BAR_AREA + BORDER;
  }

  /**
   * The contents index of the stack drawn at ({@code mouseX}, {@code mouseY}) when the slots start
   * at ({@code gridLeft}, {@code gridTop}), or {@link #NO_INDEX}; the inverse of {@link
   * #extractPanel}'s drawing order.
   */
  int indexAt(int gridLeft, int gridTop, double mouseX, double mouseY) {
    double fromRight = gridLeft + columns * SLOT - mouseX;
    double fromBottom = gridTop + rows * SLOT - mouseY;
    if (fromRight <= 0 || fromBottom <= 0 || mouseX < gridLeft || mouseY < gridTop) {
      return NO_INDEX;
    }
    int column = (int) Math.ceil(fromRight / SLOT);
    int row = (int) Math.ceil(fromBottom / SLOT);
    int slotNumber = (row - 1) * columns + column;
    return slotNumber <= stacks.length ? stacks.length - slotNumber : NO_INDEX;
  }

  /** Left edge of the slot of contents index {@code index}, for slots starting at gridLeft. */
  int cellLeft(int gridLeft, int index) {
    int slotNumber = stacks.length - index;
    return gridLeft + columns * SLOT - ((slotNumber - 1) % columns + 1) * SLOT;
  }

  /** Top edge of the slot of contents index {@code index}, for slots starting at gridTop. */
  int cellTop(int gridTop, int index) {
    int slotNumber = stacks.length - index;
    return gridTop + rows * SLOT - ((slotNumber - 1) / columns + 1) * SLOT;
  }

  @Override
  int contentWidth(Font font) {
    return panelWidth();
  }

  @Override
  int contentHeight(Font font) {
    return panelHeight(BORDER);
  }

  @Override
  void extractContents(Font font, int x, int y, int width, GuiGraphicsExtractor graphics) {
    int left = x + (width - panelWidth()) / 2;
    extractPanel(graphics, font, left, y, BORDER, selected);
    if (selected >= 0 && selected < stacks.length) {
      extractSelectedName(font, graphics, x, y, width, stacks[selected]);
    }
  }

  /**
   * The panel at ({@code left}, {@code top}) with a top part of {@code topHeight} ({@link
   * ContainerTooltip#BORDER} or the titled {@link ContainerTooltip#TITLED_TOP}), the stacks (newest
   * at the bottom right, filling leftwards then upwards, as vanilla), {@code highlighted} marked,
   * and the fill bar under the slots.
   */
  void extractPanel(
      GuiGraphicsExtractor graphics, Font font, int left, int top, int topHeight, int highlighted) {
    ContainerTooltip.extractPanel(graphics, left, top, columns, rows, color, topHeight, BAR_AREA);
    int gridLeft = left + BORDER;
    int gridTop = top + topHeight;
    boolean marked = highlighted >= 0 && highlighted < stacks.length;
    if (marked) {
      extractHighlight(graphics, HIGHLIGHT_BACK, gridLeft, gridTop, highlighted);
    }
    for (int index = 0; index < stacks.length; index++) {
      int itemX = cellLeft(gridLeft, index) + ITEM_INSET;
      int itemY = cellTop(gridTop, index) + ITEM_INSET;
      graphics.item(stacks[index], itemX, itemY, index);
      graphics.itemDecorations(font, stacks[index], itemX, itemY);
    }
    if (marked) {
      extractHighlight(graphics, HIGHLIGHT_FRONT, gridLeft, gridTop, highlighted);
    }
    int barTop = gridTop + rows * SLOT + BAR_MARGIN;
    int barWidth = columns * SLOT;
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        full ? BAR_FULL : BAR_FILL,
        gridLeft + 1,
        barTop,
        barFill,
        BAR_HEIGHT);
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED, BAR_BORDER, gridLeft, barTop, barWidth, BAR_HEIGHT);
    graphics.centeredText(font, barText, gridLeft + barWidth / 2, barTop + BAR_TEXT_TOP, WHITE);
  }

  private void extractHighlight(
      GuiGraphicsExtractor graphics, Identifier sprite, int gridLeft, int gridTop, int index) {
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        sprite,
        cellLeft(gridLeft, index) - HIGHLIGHT_INSET,
        cellTop(gridTop, index) - HIGHLIGHT_INSET,
        HIGHLIGHT_SIZE,
        HIGHLIGHT_SIZE);
  }
}
