package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.config.SlotHighlight;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * The contents grid of a shulker box or the ender chest, as Easy Shulker Boxes' {@code
 * ClientItemContentsTooltip}: a container panel of {@code columns x rows} slots tinted with the box
 * colour, centred in the tooltip, the items with their vanilla decorations, the selected slot
 * highlighted and its name in a small tooltip above.
 *
 * <p>The panel is cut at draw time from the slot area of vanilla's {@code
 * textures/gui/container/shulker_box.png} (so resource packs restyle it): its top edge, the 3x9
 * slot block and its bottom edge, tiled for other sizes. Nine textured quads for a 9x3 grid.
 */
final class ContainerTooltip extends CollapsibleTooltip {
  private static final Identifier PANEL_TEXTURE =
      Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

  /** Vanilla {@code AbstractEndPortalRenderer#END_PORTAL_LOCATION}, drawn static and flat. */
  private static final Identifier STARFIELD_TEXTURE =
      Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png");

  /** Opacity of the ender chest panel over the starfield, and its inset from the panel edge. */
  private static final int STARFIELD_PANEL_ALPHA = 0x80;

  private static final int STARFIELD_INSET = 2;

  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");
  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");

  /** Vanilla hotbar frame, the closest vanilla sprite to Easy Shulker Boxes' slot selection. */
  private static final Identifier SELECTION_FRAME =
      Identifier.withDefaultNamespace("hud/hotbar_selection");

  private static final int SELECTION_FRAME_HEIGHT = 23;
  private static final int TEXTURE_SIZE = 256;

  /** Panel edge width, and the top edge height of a panel without a title. */
  static final int BORDER = 7;

  /** Top edge plus title strip of vanilla's chest texture: where its slots start. */
  static final int TITLED_TOP = 17;

  static final int SLOT = 18;

  /** Plain rows of the title strip (below the top edge), tiled as filler. */
  private static final int FILLER_HEIGHT = TITLED_TOP - BORDER;

  /** Where the panel parts sit in the texture: left/right edge, top/bottom edge, slot block. */
  private static final int RIGHT_EDGE_U = 169;

  private static final int SLOTS_V = TITLED_TOP;
  private static final int BOTTOM_EDGE_V = 159;
  private static final int TEXTURE_COLUMNS = 9;
  private static final int TEXTURE_ROWS = 3;

  /** Item inset in a slot, and the 24x24 highlight sprites around it. */
  private static final int ITEM_INSET = 1;

  private static final int HIGHLIGHT_INSET = 3;
  private static final int HIGHLIGHT_SIZE = 24;

  private final List<ItemStack> items;
  private final int columns;
  private final int rows;
  private final int color;
  private final int selected;
  private final SlotHighlight highlight;

  /**
   * @param items every slot's stack; the ender chest's live list (drawn as it is each frame)
   * @param color tint of the panel, -1 for none
   * @param selected highlighted slot, or {@code StorageGrid.NO_ITEM}
   */
  ContainerTooltip(
      List<ItemStack> items,
      int columns,
      int rows,
      int color,
      int selected,
      SlotHighlight highlight) {
    this.items = items;
    this.columns = columns;
    this.rows = rows;
    this.color = color;
    this.selected = selected;
    this.highlight = highlight;
  }

  /** Whether this tooltip already shows {@code candidate} this way. */
  boolean shows(List<ItemStack> candidate, int newColor, int newSelected, SlotHighlight sprite) {
    return candidate == items
        && newColor == color
        && newSelected == selected
        && sprite == highlight;
  }

  @Override
  int contentWidth(Font font) {
    return columns * SLOT + 2 * BORDER;
  }

  @Override
  int contentHeight(Font font) {
    return rows * SLOT + 2 * BORDER;
  }

  @Override
  void extractContents(Font font, int x, int y, int width, GuiGraphicsExtractor graphics) {
    int left = x + (width - contentWidth(font)) / 2;
    extractPanel(graphics, left, y);
    int cells = columns * rows;
    for (int cell = 0; cell < cells; cell++) {
      if (cell != selected) {
        extractSlot(font, graphics, left, y, cell);
      }
    }
    if (selected >= 0 && selected < cells) {
      int slotX = left + BORDER + (selected % columns) * SLOT;
      int slotY = y + BORDER + (selected / columns) * SLOT;
      if (highlight == SlotHighlight.HIGHLIGHT) {
        extractHighlight(graphics, HIGHLIGHT_BACK, slotX, slotY, HIGHLIGHT_SIZE);
      }
      extractSlot(font, graphics, left, y, selected);
      if (highlight == SlotHighlight.HIGHLIGHT) {
        extractHighlight(graphics, HIGHLIGHT_FRONT, slotX, slotY, HIGHLIGHT_SIZE);
      } else {
        extractHighlight(graphics, SELECTION_FRAME, slotX, slotY, SELECTION_FRAME_HEIGHT);
      }
    }
    if (selected >= 0 && selected < items.size()) {
      extractSelectedName(font, graphics, x, y, width, items.get(selected));
    }
  }

  private void extractSlot(Font font, GuiGraphicsExtractor graphics, int left, int top, int cell) {
    if (cell >= items.size()) {
      return;
    }
    ItemStack stack = items.get(cell);
    int itemX = left + BORDER + (cell % columns) * SLOT + ITEM_INSET;
    int itemY = top + BORDER + (cell / columns) * SLOT + ITEM_INSET;
    graphics.item(stack, itemX, itemY, cell);
    graphics.itemDecorations(font, stack, itemX, itemY);
  }

  private static void extractHighlight(
      GuiGraphicsExtractor graphics, Identifier sprite, int slotX, int slotY, int height) {
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        sprite,
        slotX - HIGHLIGHT_INSET,
        slotY - HIGHLIGHT_INSET,
        HIGHLIGHT_SIZE,
        height);
  }

  /** Top edge, slot rows in blocks of up to 3, bottom edge; each split in left, middle, right. */
  private void extractPanel(GuiGraphicsExtractor graphics, int left, int top) {
    extractPanel(graphics, left, top, columns, rows, color, BORDER);
  }

  /**
   * A container panel of {@code columns x rows} slots at ({@code left}, {@code top}), tinted with
   * {@code color} (-1 for none). Its top part is the texture's first {@code topHeight} rows: the
   * top edge ({@link #BORDER}) or the edge with the title strip of a chest ({@link #TITLED_TOP}).
   * Shared with pinned windows.
   */
  static void extractPanel(
      GuiGraphicsExtractor graphics,
      int left,
      int top,
      int columns,
      int rows,
      int color,
      int topHeight) {
    extractPanel(graphics, left, top, columns, rows, color, topHeight, 0);
  }

  /**
   * {@link #extractPanel(GuiGraphicsExtractor, int, int, int, int, int, int)} with a plain strip of
   * {@code fillerHeight} between the slots and the bottom edge (a bundle's fill bar goes there),
   * tiled from the texture's title strip.
   */
  static void extractPanel(
      GuiGraphicsExtractor graphics,
      int left,
      int top,
      int columns,
      int rows,
      int color,
      int topHeight,
      int fillerHeight) {
    int panelColor = color;
    if (StorageTooltips.starfield(color)) {
      int width = columns * SLOT + 2 * BORDER - 2 * STARFIELD_INSET;
      int height = topHeight + rows * SLOT + fillerHeight + BORDER - 2 * STARFIELD_INSET;
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          STARFIELD_TEXTURE,
          left + STARFIELD_INSET,
          top + STARFIELD_INSET,
          0,
          0,
          width,
          height,
          TEXTURE_SIZE,
          TEXTURE_SIZE);
      panelColor = ARGB.color(STARFIELD_PANEL_ALPHA, color);
    }
    extractPanelStrips(graphics, left, top, columns, rows, panelColor, topHeight, fillerHeight);
  }

  private static void extractPanelStrips(
      GuiGraphicsExtractor graphics,
      int left,
      int top,
      int columns,
      int rows,
      int color,
      int topHeight,
      int fillerHeight) {
    extractStrip(graphics, left, top, 0, topHeight, columns, color);
    for (int row = 0; row < rows; row += TEXTURE_ROWS) {
      int blockRows = Math.min(TEXTURE_ROWS, rows - row);
      extractStrip(
          graphics, left, top + topHeight + row * SLOT, SLOTS_V, blockRows * SLOT, columns, color);
    }
    int fillerTop = top + topHeight + rows * SLOT;
    for (int filled = 0; filled < fillerHeight; filled += FILLER_HEIGHT) {
      int height = Math.min(FILLER_HEIGHT, fillerHeight - filled);
      extractStrip(graphics, left, fillerTop + filled, BORDER, height, columns, color);
    }
    extractStrip(graphics, left, fillerTop + fillerHeight, BOTTOM_EDGE_V, BORDER, columns, color);
  }

  private static void extractStrip(
      GuiGraphicsExtractor graphics, int left, int y, int v, int height, int columns, int color) {
    blit(graphics, left, y, 0, v, BORDER, height, color);
    for (int column = 0; column < columns; column += TEXTURE_COLUMNS) {
      int blockColumns = Math.min(TEXTURE_COLUMNS, columns - column);
      blit(
          graphics,
          left + BORDER + column * SLOT,
          y,
          BORDER,
          v,
          blockColumns * SLOT,
          height,
          color);
    }
    blit(graphics, left + BORDER + columns * SLOT, y, RIGHT_EDGE_U, v, BORDER, height, color);
  }

  private static void blit(
      GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h, int color) {
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        PANEL_TEXTURE,
        x,
        y,
        u,
        v,
        w,
        h,
        TEXTURE_SIZE,
        TEXTURE_SIZE,
        color);
  }
}
