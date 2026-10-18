package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.mixin.ItemContainerContentsAccessor;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

/**
 * The fill bar of shulker box items while {@code override_item_storage_bar} is on (client only; any
 * server): one step per filled slot, as Easy Shulker Boxes' {@code VisualItemStorage#getBarWidth}.
 * Bundles keep vanilla's bar (the same weight bar) and the ender chest none. Called from {@code
 * GuiGraphicsExtractorMixin} for every item drawn in a GUI, every frame. A shulker box pays one
 * identity hash and at most 8 reference comparisons: widths are cached by {@link
 * ItemContainerContents} instance (immutable, kept by a stack until its contents change), so only
 * the first frame of new contents walks its slots. Render thread only.
 */
public final class StorageFillBar {
  /** Colours of vanilla {@code BundleItem#getBarColor}. */
  private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F);

  private static final int FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);

  /** Geometry of vanilla {@code GuiGraphicsExtractor#itemBar}. */
  private static final int BAR_LEFT = 2;

  private static final int BAR_TOP = 13;
  private static final int BACKGROUND_HEIGHT = 2;
  private static final int BAR_HEIGHT = 1;
  private static final int BACKGROUND_COLOR = 0xFF000000;

  /**
   * Set-associative cache: {@code SETS} sets of {@code WAYS} entries, replaced round-robin. A
   * screen shows at most ~90 boxes (double chest + inventory); a set only thrashes when more than
   * {@code WAYS} visible boxes hash into it.
   */
  private static final int SETS = 32;

  private static final int WAYS = 8;
  private static final int SET_MASK = SETS - 1;

  private static final @Nullable ItemContainerContents[] CACHED_CONTENTS =
      new ItemContainerContents[SETS * WAYS];
  private static final int[] CACHED_WIDTHS = new int[SETS * WAYS];
  private static final int[] NEXT_VICTIM = new int[SETS];

  private StorageFillBar() {}

  /**
   * Draws the bar of {@code stack} when it is a single shulker box with items and the option is on.
   *
   * @return true when drawn (vanilla's bar must be skipped)
   */
  public static boolean draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
    if (StorageKind.of(stack) != StorageKind.SHULKER_BOX
        || stack.getCount() != 1
        || !StorageInput.preferences().iconMarker().showsFillBar()) {
      return false;
    }
    ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
    if (contents == null || contents.size() == 0) {
      return false;
    }
    int width = width(contents);
    if (width == 0) {
      return false;
    }
    int left = x + BAR_LEFT;
    int top = y + BAR_TOP;
    graphics.fill(
        RenderPipelines.GUI,
        left,
        top,
        left + StorageGrid.FULL_BAR_WIDTH,
        top + BACKGROUND_HEIGHT,
        BACKGROUND_COLOR);
    graphics.fill(
        RenderPipelines.GUI,
        left,
        top,
        left + width,
        top + BAR_HEIGHT,
        width == StorageGrid.FULL_BAR_WIDTH ? FULL_BAR_COLOR : BAR_COLOR);
    return true;
  }

  /** Bar width of {@code contents}, 0 for none. Cached; a miss replaces the probed entry. */
  public static int width(ItemContainerContents contents) {
    int set = System.identityHashCode(contents) & SET_MASK;
    int first = set * WAYS;
    for (int index = first; index < first + WAYS; index++) {
      if (CACHED_CONTENTS[index] == contents) {
        return CACHED_WIDTHS[index];
      }
    }
    int width = computeWidth(contents);
    int victim = first + NEXT_VICTIM[set];
    NEXT_VICTIM[set] = (NEXT_VICTIM[set] + 1) % WAYS;
    CACHED_CONTENTS[victim] = contents;
    CACHED_WIDTHS[victim] = width;
    return width;
  }

  public static void clear() {
    Arrays.fill(CACHED_CONTENTS, null);
  }

  private static int computeWidth(ItemContainerContents contents) {
    if (!((Object) contents instanceof ItemContainerContentsAccessor accessor)) {
      return 0;
    }
    List<Optional<ItemStackTemplate>> slots = accessor.shulkertoolkit$slots();
    int filled = 0;
    for (Optional<ItemStackTemplate> slot : slots) {
      if (slot.isPresent()) {
        filled++;
      }
    }
    return StorageGrid.barWidth(filled, StorageKind.SHULKER_SLOTS);
  }
}
