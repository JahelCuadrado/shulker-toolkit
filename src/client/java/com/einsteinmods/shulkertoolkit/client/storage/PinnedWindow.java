package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.core.PinnedLayout;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

/**
 * One pinned window: where its container item is (a path from a menu slot, see {@link
 * PinnedPaths}), what it showed there, and how it is drawn and clicked. Shulker boxes and the ender
 * chest draw as a chest with a title, cut from the same texture and tint as their tooltip panel
 * ({@link ContainerTooltip#extractPanel}); bundles draw their tooltip grid on the same panel
 * ({@link BundleGridTooltip#extractPanel}). Contents are copied only when their (immutable)
 * component changes, so a frame in steady state allocates nothing. Render and client thread only.
 */
final class PinnedWindow {
  static final int NO_CELL = -1;

  /** Vanilla {@code AbstractContainerScreen} title position and colour. */
  private static final int TITLE_X = 8;

  private static final int TITLE_Y = 6;
  private static final int TITLE_COLOR = 0xFF404040;
  private static final int WHITE = -1;
  private static final int NO_TINT = -1;

  /** Narrowest bundle window: room for a short title. */
  private static final int BUNDLE_MIN_COLUMNS = 4;

  /** Item inset in a slot. */
  private static final int ITEM_INSET = 1;

  /** Vanilla hovered-slot sprites ({@code AbstractContainerScreen}). */
  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");

  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");
  private static final int HIGHLIGHT_INSET = 4;
  private static final int HIGHLIGHT_SIZE = 24;

  /** One level of the path: the item found there this frame and a copy of its slots. */
  private static final class Level {
    private final StorageKind kind;
    private ItemStack stack = ItemStack.EMPTY;
    private @Nullable Object contents;
    private List<ItemStack> items = List.of();

    private Level(StorageKind kind) {
      this.kind = kind;
    }
  }

  private final int[] path;
  private final Item[] items;
  private final int depth;
  private final Level[] levels;
  private final StorageKind kind;
  private @Nullable BundleGridTooltip bundle;
  private @Nullable Component titleSource;
  private FormattedCharSequence title = FormattedCharSequence.EMPTY;
  private int x;
  private int y;
  private boolean visible;
  private float scale = 1.0F;

  /**
   * @param path menu slot, then the slot inside each item (copied)
   * @param stacks the item found at each level when pinned
   */
  PinnedWindow(int[] path, ItemStack[] stacks, int depth) {
    this.path = path.clone();
    this.depth = depth;
    this.items = new Item[depth];
    this.levels = new Level[depth];
    StorageKind last = StorageKind.SHULKER_BOX;
    for (int level = 0; level < depth; level++) {
      items[level] = stacks[level].getItem();
      StorageKind levelKind = StorageKind.of(stacks[level]);
      last = levelKind == null ? StorageKind.SHULKER_BOX : levelKind;
      levels[level] = new Level(last);
    }
    this.kind = last;
  }

  int[] path() {
    return path;
  }

  /** Moves the window's root to {@code menuSlot}: the same inventory slot in another menu. */
  void reroot(int menuSlot) {
    path[0] = menuSlot;
  }

  int depth() {
    return depth;
  }

  StorageKind kind() {
    return kind;
  }

  /** The window's container item as found this frame. */
  ItemStack stack() {
    return levels[depth - 1].stack;
  }

  /** The item found at path level {@code level} this frame. */
  ItemStack stackAtLevel(int level) {
    return levels[level].stack;
  }

  /**
   * Whether a Shift-click from the inventory would move some of {@code other} in, by the server's
   * rules ({@code PinnedWindowEdit}): an ender chest takes anything that fits its slots.
   */
  boolean accepts(ItemStack other, Player player) {
    if (kind != StorageKind.ENDER_CHEST) {
      return kind.canAddItem(stack(), other, player);
    }
    List<ItemStack> slots = slots();
    for (int slot = 0; slot < slots.size(); slot++) {
      ItemStack item = slots.get(slot);
      if (item.isEmpty()
          || (ItemStack.isSameItemSameComponents(item, other)
              && item.getCount() < StorageKind.maxCount(item))) {
        return true;
      }
    }
    return false;
  }

  boolean visible() {
    return visible;
  }

  /**
   * Puts the window's top left corner at ({@code screenX}, {@code screenY}), drawn at {@code scale}
   * eighths of its size. The window keeps its own unscaled coordinates; drawing scales them ({@link
   * #beginDraw}) and mouse positions are divided back ({@link #contains}).
   */
  void place(int screenX, int screenY, int scale) {
    visible = screenX != PinnedLayout.HIDDEN;
    this.scale = (float) scale / PinnedLayout.FULL_SCALE;
    x = Math.round(screenX / this.scale);
    y = Math.round(screenY / this.scale);
  }

  /** Scales the pose for this window; pair with {@link #endDraw}. No allocation. */
  void beginDraw(GuiGraphicsExtractor graphics) {
    graphics.pose().pushMatrix();
    graphics.pose().scale(scale, scale);
  }

  void endDraw(GuiGraphicsExtractor graphics) {
    graphics.pose().popMatrix();
  }

  /**
   * Finds the container item again, level by level; false when it is gone (moved, dropped, more
   * than one, or no longer editable), and the window closes. A few comparisons per level; copies a
   * level's slots only when its contents changed.
   */
  boolean resolve(AbstractContainerMenu menu, Player player) {
    if (path[0] >= menu.slots.size()) {
      return false;
    }
    ItemStack stack = menu.getSlot(path[0]).getItem();
    for (int level = 0; level < depth; level++) {
      if (level > 0) {
        List<ItemStack> parent = levels[level - 1].items;
        if (path[level] >= parent.size()) {
          return false;
        }
        stack = parent.get(path[level]);
      }
      Level current = levels[level];
      if (stack.getItem() != items[level]
          || !current.kind.allowModification(stack)
          || !refresh(current, stack, player, level == depth - 1)) {
        return false;
      }
    }
    return true;
  }

  int width(Font font) {
    if (kind != StorageKind.BUNDLE) {
      return StorageGrid.CONTAINER_COLUMNS * ContainerTooltip.SLOT + 2 * ContainerTooltip.BORDER;
    }
    BundleGridTooltip grid = bundle;
    return grid == null ? 0 : grid.panelWidth();
  }

  int height(Font font) {
    if (kind != StorageKind.BUNDLE) {
      return ContainerTooltip.TITLED_TOP + rows() * ContainerTooltip.SLOT + ContainerTooltip.BORDER;
    }
    BundleGridTooltip grid = bundle;
    return grid == null ? 0 : grid.panelHeight(ContainerTooltip.TITLED_TOP);
  }

  boolean contains(Font font, double mouseX, double mouseY) {
    return containsLocal(font, mouseX / scale, mouseY / scale);
  }

  private boolean containsLocal(Font font, double mouseX, double mouseY) {
    return visible
        && mouseX >= x
        && mouseY >= y
        && mouseX < x + width(font)
        && mouseY < y + height(font);
  }

  /** Whether the point is on the title, above the slots. */
  boolean onTitle(Font font, double mouseX, double mouseY) {
    return containsLocal(font, mouseX / scale, mouseY / scale) && mouseY / scale < slotsTop();
  }

  /** The slot at the point, or {@link #NO_CELL}. */
  int cellAt(Font font, double screenX, double screenY) {
    double mouseX = screenX / scale;
    double mouseY = screenY / scale;
    if (!containsLocal(font, mouseX, mouseY)) {
      return NO_CELL;
    }
    if (kind == StorageKind.BUNDLE) {
      BundleGridTooltip grid = bundle;
      return grid == null
          ? NO_CELL
          : grid.indexAt(x + ContainerTooltip.BORDER, slotsTop(), mouseX, mouseY);
    }
    int left = x + ContainerTooltip.BORDER;
    int top = slotsTop();
    if (mouseX < left || mouseY < top) {
      return NO_CELL;
    }
    int column = (int) ((mouseX - left) / ContainerTooltip.SLOT);
    int row = (int) ((mouseY - top) / ContainerTooltip.SLOT);
    int cell = row * StorageGrid.CONTAINER_COLUMNS + column;
    return column < StorageGrid.CONTAINER_COLUMNS && row < rows() && cell < slots().size()
        ? cell
        : NO_CELL;
  }

  /** The stack in slot {@code cell} (a valid one from {@link #cellAt}). */
  ItemStack stackAt(int cell) {
    BundleGridTooltip grid = bundle;
    if (kind == StorageKind.BUNDLE) {
      return grid == null || cell < 0 || cell >= grid.size() ? ItemStack.EMPTY : grid.stack(cell);
    }
    List<ItemStack> slots = slots();
    return cell >= 0 && cell < slots.size() ? slots.get(cell) : ItemStack.EMPTY;
  }

  /** The path as the click payload sends it, with the items this window shows. */
  List<PinnedWindowClickPayload.Level> payloadLevels() {
    List<PinnedWindowClickPayload.Level> payloadLevels = new ArrayList<>(depth);
    for (int level = 0; level < depth; level++) {
      payloadLevels.add(new PinnedWindowClickPayload.Level(path[level], items[level]));
    }
    return List.copyOf(payloadLevels);
  }

  /** Draws the window; {@code hoveredCell} gets vanilla's hovered-slot highlight. */
  void extract(GuiGraphicsExtractor graphics, Font font, int hoveredCell) {
    int tint = StorageTooltips.panelColor(kind, stack());
    BundleGridTooltip grid = bundle;
    if (kind == StorageKind.BUNDLE) {
      if (grid != null) {
        grid.extractPanel(graphics, font, x, y, ContainerTooltip.TITLED_TOP, hoveredCell);
      }
    } else {
      ContainerTooltip.extractPanel(
          graphics, x, y, StorageGrid.CONTAINER_COLUMNS, rows(), tint, ContainerTooltip.TITLED_TOP);
    }
    // Vanilla's dark title is unreadable on a tinted panel.
    boolean tinted = tint != NO_TINT;
    graphics.text(
        font, title(font), x + TITLE_X, y + TITLE_Y, tinted ? WHITE : TITLE_COLOR, tinted);
    if (kind == StorageKind.BUNDLE) {
      return;
    }
    if (hoveredCell != NO_CELL) {
      extractHighlight(graphics, HIGHLIGHT_BACK, hoveredCell);
    }
    List<ItemStack> slots = slots();
    int cells = Math.min(slots.size(), StorageGrid.CONTAINER_COLUMNS * rows());
    for (int cell = 0; cell < cells; cell++) {
      ItemStack item = slots.get(cell);
      if (!item.isEmpty()) {
        int itemX = itemX(cell);
        int itemY = itemY(cell);
        graphics.item(item, itemX, itemY, cell);
        graphics.itemDecorations(font, item, itemX, itemY);
      }
    }
    if (hoveredCell != NO_CELL) {
      extractHighlight(graphics, HIGHLIGHT_FRONT, hoveredCell);
    }
  }

  /** Item position of slot {@code cell}, where its eye marker goes. */
  int itemX(int cell) {
    if (kind == StorageKind.BUNDLE) {
      BundleGridTooltip grid = bundle;
      return grid == null ? x : grid.cellLeft(x + ContainerTooltip.BORDER, cell) + ITEM_INSET;
    }
    return x
        + ContainerTooltip.BORDER
        + (cell % StorageGrid.CONTAINER_COLUMNS) * ContainerTooltip.SLOT
        + ITEM_INSET;
  }

  int itemY(int cell) {
    if (kind == StorageKind.BUNDLE) {
      BundleGridTooltip grid = bundle;
      return grid == null ? y : grid.cellTop(slotsTop(), cell) + ITEM_INSET;
    }
    return slotsTop() + (cell / StorageGrid.CONTAINER_COLUMNS) * ContainerTooltip.SLOT + ITEM_INSET;
  }

  private void extractHighlight(GuiGraphicsExtractor graphics, Identifier sprite, int cell) {
    int slotX =
        x
            + ContainerTooltip.BORDER
            + (cell % StorageGrid.CONTAINER_COLUMNS) * ContainerTooltip.SLOT;
    int slotY = slotsTop() + (cell / StorageGrid.CONTAINER_COLUMNS) * ContainerTooltip.SLOT;
    graphics.blitSprite(
        RenderPipelines.GUI_TEXTURED,
        sprite,
        slotX + ITEM_INSET - HIGHLIGHT_INSET,
        slotY + ITEM_INSET - HIGHLIGHT_INSET,
        HIGHLIGHT_SIZE,
        HIGHLIGHT_SIZE);
  }

  /** The title (the item's name, cut to the window), rebuilt only when the name changes. */
  private FormattedCharSequence title(Font font) {
    Component name = stack().getHoverName();
    if (name != titleSource) {
      titleSource = name;
      title =
          Language.getInstance()
              .getVisualOrder(font.substrByWidth(name, width(font) - 2 * TITLE_X));
    }
    return title;
  }

  private int slotsTop() {
    return y + ContainerTooltip.TITLED_TOP;
  }

  private int rows() {
    return kind.rows(slots().size());
  }

  private List<ItemStack> slots() {
    return levels[depth - 1].items;
  }

  /** Copies a level's slots when its contents changed; the ender chest reads the live chest. */
  private boolean refresh(Level level, ItemStack stack, Player player, boolean target) {
    level.stack = stack;
    if (level.kind == StorageKind.ENDER_CHEST) {
      level.items = player.getEnderChestInventory().getItems();
      return true;
    }
    Object contents = level.kind.contents(stack);
    if (contents != level.contents) {
      level.contents = contents;
      level.items = level.kind.items(stack, player);
    }
    return !target || level.kind != StorageKind.BUNDLE || refreshBundle(stack);
  }

  /** Rebuilds the bundle grid when its contents or tint changed. */
  private boolean refreshBundle(ItemStack stack) {
    BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
    if (bundleContents == null) {
      return false;
    }
    int tint = StorageTooltips.panelColor(StorageKind.BUNDLE, stack);
    BundleGridTooltip grid = bundle;
    if (grid != null && grid.shows(bundleContents, tint)) {
      return true;
    }
    Fraction weight = bundleContents.weight().result().orElse(null);
    if (weight == null) {
      return false;
    }
    bundle = new BundleGridTooltip(bundleContents, weight, tint, BUNDLE_MIN_COLUMNS);
    return true;
  }
}
