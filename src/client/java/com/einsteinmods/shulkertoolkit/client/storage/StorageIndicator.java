package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The markers of container items: the eye of a pinned window in the top right corner, and the "+"
 * while the cursor holds something (Easy Shulker Boxes' {@code item_storage_indicator}, {@code
 * ItemDecorationsHelper} and {@code ItemDecorationsType}) where vanilla draws the count, green,
 * only when the item already holds the same and more fits. Called after vanilla's decorations of
 * every item drawn in a GUI, every frame: an item outside a menu slot pays two field reads, any
 * other item without a cursor stack and a pinned window one more. The verdict is cached by contents
 * instance and cursor stack (the ender chest's, read from its live inventory, is computed: 27
 * slots, no allocation). Pinned windows draw the eyes of the items they show themselves ({@link
 * #extractEye}); the "+" stays with menu slots, where a click inserts. Render thread only.
 */
public final class StorageIndicator {
  private static final Identifier EYE = ShulkerToolkit.id("pinned_eye");
  private static final int EYE_WIDTH = 8;
  private static final int EYE_HEIGHT = 6;

  /** An item is drawn 16x16; the eye sits in its top right corner. */
  private static final int EYE_LEFT = 8;

  private static final String PLUS = "+";
  private static final int HAS_ITEM = ARGB.opaque(DyeColor.GREEN.getTextColor());
  private static final int NONE = 0;

  /** Text position of vanilla {@code GuiGraphicsExtractor#itemCount}. */
  private static final int TEXT_RIGHT = 17;

  private static final int TEXT_TOP = 9;

  /** Set-associative verdict cache, as {@link StorageFillBar}. */
  private static final int SETS = 16;

  private static final int WAYS = 4;
  private static final int SET_MASK = SETS - 1;
  private static final @Nullable Object[] CACHED_CONTENTS = new Object[SETS * WAYS];
  private static final @Nullable ItemStack[] CACHED_CARRIED = new ItemStack[SETS * WAYS];
  private static final int[] CACHED_COUNTS = new int[SETS * WAYS];
  private static final int[] CACHED_COLORS = new int[SETS * WAYS];
  private static final int[] NEXT_VICTIM = new int[SETS];

  private static @Nullable Slot slotBeingDrawn;
  private static @Nullable AbstractContainerScreen<?> screenBeingDrawn;
  private static int plusWidth = -1;

  private StorageIndicator() {}

  /** Set around vanilla {@code AbstractContainerScreen#extractSlot}; null after it. */
  public static void setSlotBeingDrawn(
      @Nullable AbstractContainerScreen<?> screen, @Nullable Slot slot) {
    screenBeingDrawn = screen;
    slotBeingDrawn = slot;
  }

  /** The markers over {@code stack} at ({@code x}, {@code y}) when it is a menu slot's item. */
  public static void extract(
      GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
    Slot slot = slotBeingDrawn;
    AbstractContainerScreen<?> screen = screenBeingDrawn;
    if (slot == null || screen == null || slot.getItem() != stack) {
      return;
    }
    if (PinnedWindows.isPinned(slot)) {
      extractEye(graphics, x, y);
    }
    ItemStack carried = screen.getMenu().getCarried();
    if (carried.isEmpty() || carried == stack) {
      return;
    }
    StorageKind kind = StorageKind.of(stack);
    LocalPlayer player = Minecraft.getInstance().player;
    if (kind == null
        || player == null
        || !StorageInput.preferences().iconMarker().showsIndicator()
        || !TransferGate.allows(player)
        || !TransferGate.allowsKind(player, kind)
        || !kind.allowModification(stack)
        || !slot.allowModification(player)
        || (screen instanceof CreativeModeInventoryScreen
            && slot.container != player.getInventory())) {
      return;
    }
    int color = color(kind, stack, carried, player);
    if (color == NONE) {
      return;
    }
    if (plusWidth < 0) {
      plusWidth = font.width(PLUS);
    }
    graphics.text(font, PLUS, x + TEXT_RIGHT - plusWidth, y + TEXT_TOP, color, true);
  }

  /** The eye of a pinned window over the item drawn at ({@code x}, {@code y}). */
  static void extractEye(GuiGraphicsExtractor graphics, int x, int y) {
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EYE, x + EYE_LEFT, y, EYE_WIDTH, EYE_HEIGHT);
  }

  public static void clear() {
    Arrays.fill(CACHED_CONTENTS, null);
    Arrays.fill(CACHED_CARRIED, null);
  }

  private static int color(StorageKind kind, ItemStack stack, ItemStack carried, Player player) {
    Object contents = kind.contents(stack);
    if (contents == null) {
      return verdict(kind, stack, carried, player);
    }
    int set = System.identityHashCode(contents) & SET_MASK;
    int first = set * WAYS;
    for (int index = first; index < first + WAYS; index++) {
      if (CACHED_CONTENTS[index] == contents
          && CACHED_CARRIED[index] == carried
          && CACHED_COUNTS[index] == carried.getCount()) {
        return CACHED_COLORS[index];
      }
    }
    int color = verdict(kind, stack, carried, player);
    int victim = first + NEXT_VICTIM[set];
    NEXT_VICTIM[set] = (NEXT_VICTIM[set] + 1) % WAYS;
    CACHED_CONTENTS[victim] = contents;
    CACHED_CARRIED[victim] = carried;
    CACHED_COUNTS[victim] = carried.getCount();
    CACHED_COLORS[victim] = color;
    return color;
  }

  /**
   * Easy Shulker Boxes' {@code ItemDecorationsType#pickType} reduced to its green case: the item
   * already holds the same and more fits (its yellow and red cases are what the fill bar shows).
   */
  private static int verdict(StorageKind kind, ItemStack stack, ItemStack carried, Player player) {
    return kind.hasAnyOf(stack, carried, player, false) && kind.canAddItem(stack, carried, player)
        ? HAS_ITEM
        : NONE;
  }
}
