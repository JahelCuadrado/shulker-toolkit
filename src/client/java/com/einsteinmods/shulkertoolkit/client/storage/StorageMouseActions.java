package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.core.StorageStacking;
import com.einsteinmods.shulkertoolkit.network.SelectStoredItemPayload;
import com.einsteinmods.shulkertoolkit.transfer.StorageClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

/**
 * Mouse and key actions of container items in one container screen, as Easy Shulker Boxes' {@code
 * ItemStorageMouseActions}: the wheel moves the selection (horizontally, or vertically with its
 * key) and with the single item key moves one item per notch; dragging with a container item on the
 * cursor inserts from or extracts to every slot passed (highlighted, one sound on release); Enter
 * or Space opens the selected container item inside the tooltip and Backspace goes back; the arrow
 * keys move the selection. First in the screen's mouse actions, before vanilla's bundle one. Every
 * hook runs on input events, except the two draw hooks, which only draw while dragging or for the
 * cursor and nested tooltips. Only while the server allows transfer for that kind of item.
 */
public final class StorageMouseActions implements ItemSlotMouseAction {
  /** Slot index of the cursor stack (Easy Shulker Boxes' empty {@code OptionalInt}). */
  public static final int CURSOR = -1;

  /** No item opened inside a tooltip. */
  private static final int NO_NESTED_SLOT = -2;

  /**
   * Vanilla's container click buttons ({@code AbstractContainerScreen#getContainerClickButton}).
   */
  private static final int PRIMARY_CLICK = 0;

  private static final int SECONDARY_CLICK = 1;

  private static final Identifier HIGHLIGHT_BACK =
      Identifier.withDefaultNamespace("container/slot_highlight_back");
  private static final Identifier HIGHLIGHT_FRONT =
      Identifier.withDefaultNamespace("container/slot_highlight_front");
  private static final int HIGHLIGHT_INSET = 4;
  private static final int HIGHLIGHT_SIZE = 24;

  private static @Nullable StorageMouseActions current;

  private final AbstractContainerScreen<?> screen;
  private final AbstractContainerScreenAccessor access;
  private final Minecraft minecraft;
  private final ScrollWheelHandler scrollWheelHandler = new ScrollWheelHandler();
  private final List<Slot> clickedDraggingSlots = new ArrayList<>();
  private final List<Slot> allDraggingSlots = new ArrayList<>();
  private final List<NestedItem> nested = new ArrayList<>();
  private @Nullable ClickAction clickAction;
  private int nestedSlot = NO_NESTED_SLOT;

  /**
   * A container item opened inside a tooltip.
   *
   * @param item a copy, shown only
   * @param slotNum its slot in the item it was opened from
   */
  private record NestedItem(ItemStack item, int slotNum) {}

  public StorageMouseActions(
      AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor access) {
    this.screen = screen;
    this.access = access;
    this.minecraft = Minecraft.getInstance();
    current = this;
  }

  /** Whether a drag is in progress on the open screen: its clicks play no sound until release. */
  public static boolean draggingOnOpenScreen() {
    StorageMouseActions actions = current;
    return actions != null
        && actions.clickAction != null
        && Minecraft.getInstance().gui.screen() == actions.screen;
  }

  @Override
  public boolean matches(Slot slot) {
    return matches(slot.getItem());
  }

  /** A container item the server lets this player edit. */
  public boolean matches(ItemStack stack) {
    StorageKind kind = StorageKind.of(stack);
    LocalPlayer player = minecraft.player;
    return kind != null
        && player != null
        && TransferGate.allows(player)
        && TransferGate.allowsKind(player, kind);
  }

  @Override
  public boolean onMouseScrolled(double scrollX, double scrollY, int slotIndex, ItemStack stack) {
    LocalPlayer player = minecraft.player;
    if (player == null || !StorageInput.contentsShown()) {
      return false;
    }
    Slot slot = access.shulkertoolkit$hoveredSlot();
    if (slot != null
        && StorageInput.singleItemMovement()
        && editable(StorageKind.of(stack), slot, slotIndex, stack, player)) {
      int wheel = wheel(scrollX, scrollY);
      if (wheel != 0) {
        boolean left = wheel <= 0;
        access.shulkertoolkit$slotClicked(
            slot, slot.index, left ? PRIMARY_CLICK : SECONDARY_CLICK, ContainerInput.PICKUP);
      }
      return true;
    }
    if (!StorageInput.cursorTooltipShown() && slotIndex == CURSOR) {
      return false;
    }
    ItemStack storageItem = storageItem(slotIndex, stack);
    StorageKind kind = StorageKind.of(storageItem);
    if (kind == null || !editable(kind, slot, slotIndex, storageItem, player)) {
      return false;
    }
    int wheel = wheel(scrollX, scrollY);
    if (wheel != 0) {
      if (StorageInput.isHeld(StorageInput.preferences().verticalTooltipScrolling())) {
        scrollSelection(kind, slotIndex, storageItem, player, 0, -wheel);
      } else {
        scrollSelection(kind, slotIndex, storageItem, player, -wheel, 0);
      }
    }
    return true;
  }

  @Override
  public void onStopHovering(Slot hoveredSlot) {
    if (!StorageInput.cursorTooltipShown()) {
      clearNested(hoveredSlot.getItem());
      toggleSelection(hoveredSlot.getItem(), hoveredSlot.index, StorageGrid.NO_ITEM, false);
    }
  }

  @Override
  public void onSlotClicked(Slot slot, ContainerInput containerInput) {
    clearNested(slot.getItem());
    if (containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.SWAP) {
      toggleSelection(slot.getItem(), slot.index, StorageGrid.NO_ITEM, true);
    }
  }

  /** Mouse press; true consumes it (vanilla does not see it). */
  public boolean onMouseClicked(MouseButtonEvent event, int slotIndex, ItemStack stack) {
    if (blocksMouse(slotIndex)) {
      return true;
    }
    LocalPlayer player = minecraft.player;
    ClientShulkerState state = StorageInput.state();
    if (player == null || state == null || !state.mouseDraggingAllowed() || slotIndex != CURSOR) {
      return false;
    }
    clearDragging();
    StorageKind kind = StorageKind.of(stack);
    if (kind != null && kind.allowModification(stack)) {
      Slot slot = access.shulkertoolkit$slotAt(event.x(), event.y());
      if (slot != null) {
        clickAction = dragAction(event.button(), slot, kind, stack, player);
        return clickAction != null;
      }
    }
    return false;
  }

  /** Mouse release: ends a drag with one insert or remove sound. */
  public boolean onMouseReleased(MouseButtonEvent event, int slotIndex, ItemStack stack) {
    if (blocksMouse(slotIndex)) {
      return true;
    }
    LocalPlayer player = minecraft.player;
    ClientShulkerState state = StorageInput.state();
    if (player == null || state == null || !state.mouseDraggingAllowed() || slotIndex != CURSOR) {
      return false;
    }
    ClickAction lastAction = clickedDraggingSlots.isEmpty() ? null : clickAction;
    boolean handled = allDraggingSlots.size() > 1 || !clickedDraggingSlots.isEmpty();
    clearDragging();
    if (lastAction != null && StorageKind.of(stack) != null) {
      StorageClicks.playSound(player, lastAction == ClickAction.SECONDARY);
    }
    return handled;
  }

  /** Mouse drag: clicks each new slot passed that the drag's action applies to. */
  public boolean onMouseDragged(MouseButtonEvent event, int slotIndex, ItemStack stack) {
    if (blocksMouse(slotIndex)) {
      return true;
    }
    LocalPlayer player = minecraft.player;
    ClientShulkerState state = StorageInput.state();
    if (player == null
        || state == null
        || !state.mouseDraggingAllowed()
        || slotIndex != CURSOR
        || clickAction == null) {
      return false;
    }
    StorageKind kind = StorageKind.of(stack);
    if (kind == null || !kind.allowModification(stack)) {
      clearDragging();
      return false;
    }
    Slot slot = access.shulkertoolkit$slotAt(event.x(), event.y());
    if (slot == null || !screen.getMenu().canDragTo(slot) || allDraggingSlots.contains(slot)) {
      return false;
    }
    if (dragApplies(clickAction, slot, kind, stack, player)) {
      clickedDraggingSlots.add(slot);
      access.shulkertoolkit$slotClicked(
          slot,
          slot.index,
          event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? SECONDARY_CLICK : PRIMARY_CLICK,
          ContainerInput.PICKUP);
    }
    allDraggingSlots.add(slot);
    return true;
  }

  /** Key press: opens or closes a nested item, or moves the selection with the arrow keys. */
  public boolean onKeyPressed(KeyEvent event, int slotIndex, ItemStack stack) {
    LocalPlayer player = minecraft.player;
    if (player == null || !StorageInput.contentsShown()) {
      return false;
    }
    if (!StorageInput.cursorTooltipShown() && slotIndex == CURSOR) {
      return false;
    }
    ItemStack storageItem = storageItem(slotIndex, stack);
    StorageKind kind = StorageKind.of(storageItem);
    if (kind == null
        || !editable(kind, access.shulkertoolkit$hoveredSlot(), slotIndex, storageItem, player)) {
      return false;
    }
    if (StorageInput.preferences().nestedItemTooltips()) {
      if (event.isSelection()) {
        openNested(kind, slotIndex, storageItem, player);
        return true;
      }
      if (event.key() == InputConstants.KEY_BACKSPACE) {
        closeNested(slotIndex, stack, player);
        return true;
      }
    }
    int scrollX = (event.isRight() ? 1 : 0) - (event.isLeft() ? 1 : 0);
    int scrollY = (event.isDown() ? 1 : 0) - (event.isUp() ? 1 : 0);
    if (scrollX != 0 || scrollY != 0) {
      scrollSelection(kind, slotIndex, storageItem, player, scrollX, scrollY);
      return true;
    }
    return false;
  }

  /**
   * After the screen background: dragged slots' back highlight and the tooltip of the cursor or
   * nested container item, set before vanilla's hovered-slot tooltip so it wins.
   */
  public void onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    extractDragHighlights(graphics, mouseX, mouseY, HIGHLIGHT_BACK);
    LocalPlayer player = minecraft.player;
    ItemStack stack = tooltipItem();
    StorageKind kind = StorageKind.of(stack);
    if (player != null
        && kind != null
        && kind.allowModification(stack)
        && kind.hasContents(stack, player)) {
      graphics.setTooltipForNextFrame(
          screen.getFont(),
          access.shulkertoolkit$tooltipLines(stack),
          stack.getTooltipImage(),
          mouseX,
          mouseY,
          stack.get(DataComponents.TOOLTIP_STYLE));
    }
  }

  /** After the slots: dragged slots' front highlight. */
  public void onExtractForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    extractDragHighlights(graphics, mouseX, mouseY, HIGHLIGHT_FRONT);
  }

  private void extractDragHighlights(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, Identifier sprite) {
    if (clickedDraggingSlots.isEmpty()) {
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(access.shulkertoolkit$leftPos(), access.shulkertoolkit$topPos());
    for (Slot slot : clickedDraggingSlots) {
      if (slot.isHighlightable()
          && (clickedDraggingSlots.size() > 1
              || !access.shulkertoolkit$isHovering(slot, mouseX, mouseY))) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            sprite,
            slot.x - HIGHLIGHT_INSET,
            slot.y - HIGHLIGHT_INSET,
            HIGHLIGHT_SIZE,
            HIGHLIGHT_SIZE);
      }
    }
    graphics.pose().popMatrix();
  }

  /** The cursor item while its tooltip key is held, or the opened nested item. */
  private ItemStack tooltipItem() {
    boolean cursorShown = StorageInput.cursorTooltipShown();
    if (nested.isEmpty() || (nestedSlot == CURSOR && !cursorShown)) {
      return cursorShown ? screen.getMenu().getCarried() : ItemStack.EMPTY;
    }
    return nested.getLast().item();
  }

  /** While an item is opened in a tooltip, its slot takes no mouse input. */
  private boolean blocksMouse(int slotIndex) {
    return !nested.isEmpty()
        && slotIndex == nestedSlot
        && (slotIndex != CURSOR || StorageInput.cursorTooltipShown());
  }

  private void clearDragging() {
    clickAction = null;
    clickedDraggingSlots.clear();
    allDraggingSlots.clear();
  }

  /** What a press starts: the scheme's button, or for a single right button the slot's need. */
  private @Nullable ClickAction dragAction(
      int button, Slot slot, StorageKind kind, ItemStack stack, LocalPlayer player) {
    ClickScheme scheme =
        StorageInput.singleItemMovement()
            ? ClickScheme.SPLIT_INPUT
            : StorageInput.preferences().controlScheme();
    if (scheme == ClickScheme.SPLIT_INPUT) {
      return switch (button) {
        case InputConstants.MOUSE_BUTTON_LEFT -> ClickAction.PRIMARY;
        case InputConstants.MOUSE_BUTTON_RIGHT -> ClickAction.SECONDARY;
        default -> null;
      };
    }
    if (button != InputConstants.MOUSE_BUTTON_RIGHT) {
      return null;
    }
    if (StorageClicks.extractsTo(kind, stack, slot, player)) {
      return ClickAction.SECONDARY;
    }
    return StorageClicks.insertsFrom(kind, stack, slot, player) ? ClickAction.PRIMARY : null;
  }

  private static boolean dragApplies(
      ClickAction action, Slot slot, StorageKind kind, ItemStack stack, LocalPlayer player) {
    if (action == ClickAction.PRIMARY) {
      return StorageClicks.insertsFrom(kind, stack, slot, player);
    }
    return StorageClicks.extractsTo(kind, stack, slot, player)
        || (StorageInput.singleItemMovement()
            && kind.hasAnyOf(stack, slot.getItem(), player, true));
  }

  /** Easy Shulker Boxes' {@code allowModification(holder, slot, slotIndex, itemStack)}. */
  private boolean editable(
      @Nullable StorageKind kind,
      @Nullable Slot slot,
      int slotIndex,
      ItemStack stack,
      LocalPlayer player) {
    if (kind == null || !kind.allowModification(stack) || !kind.hasContents(stack, player)) {
      return false;
    }
    if (slotIndex == CURSOR || !nested.isEmpty()) {
      return true;
    }
    return slot != null
        && slot.index == slotIndex
        && slot.getItem() == stack
        && slot.allowModification(player);
  }

  private int wheel(double scrollX, double scrollY) {
    Vector2i wheelXY = scrollWheelHandler.onMouseScroll(scrollX, scrollY);
    return wheelXY.y == 0 ? -wheelXY.x : wheelXY.y;
  }

  private void scrollSelection(
      StorageKind kind,
      int slotIndex,
      ItemStack stack,
      LocalPlayer player,
      int scrollX,
      int scrollY) {
    StorageStacking<ItemStack> view = kind.view(stack, player);
    int previous = StorageClicks.selectedItem(player);
    int updated =
        StorageGrid.scroll(
            previous,
            kind.columns(view.size()),
            kind.rows(view.size()),
            view.size(),
            slot -> view.key(slot) != null,
            scrollX,
            scrollY);
    if (updated != previous) {
      toggleSelection(stack, slotIndex, updated, false);
    }
  }

  private void openNested(StorageKind kind, int slotIndex, ItemStack stack, LocalPlayer player) {
    int selected = StorageClicks.selectedItem(player);
    List<ItemStack> items = kind.items(stack, player);
    ItemStack item =
        selected >= 0 && selected < items.size() ? items.get(selected) : ItemStack.EMPTY;
    StorageKind itemKind = StorageKind.of(item);
    if (itemKind == null) {
      return;
    }
    if (slotIndex != nestedSlot) {
      nested.clear();
      nestedSlot = slotIndex;
    }
    itemKind.toggleSelectedItem(stack, player, StorageGrid.NO_ITEM, false);
    nested.add(new NestedItem(item.copy(), selected));
  }

  private void closeNested(int slotIndex, ItemStack original, LocalPlayer player) {
    if (nested.isEmpty()) {
      return;
    }
    int slotNum = nested.removeLast().slotNum();
    ItemStack item = storageItem(slotIndex, original);
    StorageKind kind = StorageKind.of(item);
    if (kind != null) {
      kind.toggleSelectedItem(item, player, slotNum, false);
    }
    if (nested.isEmpty()) {
      nestedSlot = NO_NESTED_SLOT;
    }
  }

  /** Leaving a slot or clicking it closes every nested item, restoring the outer selection. */
  private void clearNested(ItemStack stack) {
    LocalPlayer player = minecraft.player;
    if (!nested.isEmpty() && player != null) {
      StorageKind kind = StorageKind.of(stack);
      if (kind != null) {
        kind.toggleSelectedItem(stack, player, nested.getFirst().slotNum(), false);
      }
      nested.clear();
    }
    nestedSlot = NO_NESTED_SLOT;
  }

  private ItemStack storageItem(int slotIndex, ItemStack original) {
    return !nested.isEmpty() && slotIndex == nestedSlot ? nested.getLast().item() : original;
  }

  /** Sets the selection locally and, at the top level, on the server. */
  private void toggleSelection(ItemStack stack, int slotIndex, int selected, boolean slotClicked) {
    LocalPlayer player = minecraft.player;
    StorageKind kind = StorageKind.of(stack);
    if (player == null || kind == null) {
      return;
    }
    kind.toggleSelectedItem(stack, player, selected, slotClicked);
    if (nested.isEmpty() && ClientPlayNetworking.canSend(SelectStoredItemPayload.TYPE)) {
      ClientPlayNetworking.send(
          new SelectStoredItemPayload(
              slotIndex == CURSOR ? SelectStoredItemPayload.CARRIED : slotIndex,
              selected,
              slotClicked));
    }
  }
}
