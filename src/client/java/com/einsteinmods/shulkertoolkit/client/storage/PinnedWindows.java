package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.ShulkerToolkit;
import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.client.mixin.AbstractRecipeBookScreenAccessor;
import com.einsteinmods.shulkertoolkit.core.PinnedAction;
import com.einsteinmods.shulkertoolkit.core.PinnedLayout;
import com.einsteinmods.shulkertoolkit.core.PinnedPaths;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.core.StorageStacking;
import com.einsteinmods.shulkertoolkit.network.PinnedWindowClickPayload;
import com.einsteinmods.shulkertoolkit.transfer.PinnedWindowClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageKind;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

/**
 * Pinned windows of the open container screen: middle click (Control + middle click in creative,
 * where middle click clones) on a shulker box, ender chest or bundle pins its window next to the
 * screen, up to the player's maximum ({@link PinnedPaths#MAX_DEPTH} at most) at once, also from
 * inside another window; middle click on it again or on the window title unpins it, closing the
 * screen removes them all (a switch to another supported screen keeps the ones whose box is in the
 * player's inventory). An item with its window open shows no tooltip view: its selection and right
 * clicks work through the window (every ender chest, with an ender window: they all show the same
 * contents), and those contents open once: a second ender window, or one over the ender chest's own
 * menu, shows a short notice instead, a frame check while none shows. Clicks in a window,
 * Shift-clicks from the inventory into the last window used and number keys over a window slot each
 * send one {@link PinnedWindowClickPayload}; the server applies them and vanilla's menu sync shows
 * the result (no prediction). Every hook costs one check while nothing is pinned; with windows, a
 * frame resolves each window (a few comparisons per level) and draws it without allocating. Render
 * and client thread only.
 */
public final class PinnedWindows {

  /** Vanilla open sounds: {@code ShulkerBoxBlockEntity}, {@code EnderChestBlockEntity}. */
  private static final float OPEN_VOLUME = 0.5F;

  private static final float OPEN_PITCH = 0.9F;
  private static final float OPEN_PITCH_RANGE = 0.1F;

  /** Vanilla {@code BundleItem#playRemoveOneSound}. */
  private static final float BUNDLE_VOLUME = 0.8F;

  private static final float BUNDLE_PITCH = 0.8F;
  private static final float BUNDLE_PITCH_RANGE = 0.4F;

  private static final int NONE = -1;

  /** How far past the container's right edge a widget still counts as beside it. */
  private static final int BUTTON_REACH = 32;

  private static final Component ALREADY_OPEN =
      Component.translatable("tooltip." + ShulkerToolkit.MOD_ID + ".ender_chest_already_open");

  private static final long NOTICE_MILLIS = 2000L;
  private static final int NOTICE_COLOR = 0xFFFFFFFF;
  private static final int NOTICE_GAP = 12;
  private static final int NOTICE_MARGIN = 2;

  /**
   * How long pins wait for the next screen after one is removed without the player closing it: a
   * server switching menus closes the old one first (a close packet, the world shown for a moment)
   * and opens the next one in the same tick, as Handy Tables going from a chest to a station.
   */
  private static final long SWITCH_MILLIS = 1000L;

  private static final PinnedWindow[] WINDOWS = new PinnedWindow[PinnedPaths.MAX_DEPTH];
  private static final int[] WIDTHS = new int[PinnedPaths.MAX_DEPTH];
  private static final int[] HEIGHTS = new int[PinnedPaths.MAX_DEPTH];
  private static final int[] SCALED_WIDTHS = new int[PinnedPaths.MAX_DEPTH];
  private static final int[] SCALED_HEIGHTS = new int[PinnedPaths.MAX_DEPTH];
  private static final int[] LAID_X = new int[PinnedPaths.MAX_DEPTH];
  private static final int[] LAID_Y = new int[PinnedPaths.MAX_DEPTH];

  private static int count;
  private static @Nullable AbstractContainerScreen<?> screen;
  private static @Nullable AbstractContainerScreenAccessor access;
  private static int focused = NONE;
  private static int hoveredWindow = NONE;
  private static int hoveredCell = PinnedWindow.NO_CELL;

  /** The cell hovered last frame, whose item's selection the wheel moved. */
  private static int lastWindow = NONE;

  private static int lastCell = PinnedWindow.NO_CELL;
  private static final ScrollWheelHandler WHEEL = new ScrollWheelHandler();
  private static boolean pressConsumed;
  private static boolean layoutDirty;
  private static int laidLeft;
  private static int laidTop;
  private static int laidWidth;
  private static int laidHeight;
  private static boolean laidRecipeBook;

  /** While pins wait for the next screen: the inventory index of each window's root, and until. */
  private static final int[] PENDING_ROOTS = new int[PinnedPaths.MAX_DEPTH];

  private static boolean pending;
  private static long pendingUntil;

  private static boolean noticeShown;
  private static long noticeStart;

  private PinnedWindows() {}

  /**
   * The screens with pinned windows: inventory, crafting table, chests, shulker boxes, furnaces and
   * the work stations (stonecutter, loom, cartography table, smithing table, grindstone, anvil).
   * All of them draw everything inside their image (176 x 166, no recipe book but the crafting
   * one), so windows go beside it as with a chest. Keep in step with {@code
   * PinnedWindowClicks#supports}.
   */
  public static boolean supports(Screen candidate) {
    return candidate instanceof InventoryScreen
        || candidate instanceof CraftingScreen
        || candidate instanceof ContainerScreen
        || candidate instanceof ShulkerBoxScreen
        || candidate instanceof AbstractFurnaceScreen
        || candidate instanceof StonecutterScreen
        || candidate instanceof LoomScreen
        || candidate instanceof CartographyTableScreen
        || candidate instanceof SmithingScreen
        || candidate instanceof GrindstoneScreen
        || candidate instanceof AnvilScreen;
  }

  /**
   * Hooks a supported screen after each init. A resize keeps its windows; another screen takes the
   * windows of the one just left when it came straight from it ({@link #keepForNextScreen}), else
   * starts empty. Registered before the container item actions, so a window click never reaches
   * them.
   */
  public static void install(
      AbstractContainerScreen<?> containerScreen, AbstractContainerScreenAccessor screenAccess) {
    if (screen != containerScreen) {
      if (pending && Util.getMillis() <= pendingUntil) {
        pending = false;
        carryOver(containerScreen);
      } else {
        clear();
      }
    }
    screen = containerScreen;
    access = screenAccess;
    layoutDirty = true;
    ScreenMouseEvents.allowMouseClick(containerScreen)
        .register((current, event) -> !onMouseClicked(event));
    ScreenMouseEvents.allowMouseRelease(containerScreen)
        .register((current, event) -> !onMouseReleased());
    ScreenMouseEvents.allowMouseDrag(containerScreen)
        .register((current, event, dragX, dragY) -> !pressConsumed);
    ScreenMouseEvents.allowMouseScroll(containerScreen)
        .register(
            (current, mouseX, mouseY, scrollX, scrollY) -> !onMouseScrolled(scrollX, scrollY));
    ScreenKeyboardEvents.allowKeyPress(containerScreen)
        .register((current, event) -> !onKeyPressed(event));
    ScreenEvents.afterForeground(containerScreen)
        .register(
            (current, graphics, mouseX, mouseY, partialTick) -> extract(graphics, mouseX, mouseY));
    ScreenEvents.remove(containerScreen)
        .register(
            current -> {
              if (current == screen) {
                keepForNextScreen();
              }
            });
  }

  /** Unpins every window: the player closed the container ({@code LocalPlayerMixin}). */
  public static void unpinAll() {
    clear();
  }

  /** Another screen than a supported container screen opened: pins left behind are dropped. */
  public static void forgetPending() {
    if (pending) {
      clear();
    }
  }

  /**
   * The screen is going away without the player closing it (a menu switch): its windows wait for
   * the next supported screen, by the inventory slot of their root. A window whose root is not a
   * player inventory slot (a box in a chest) cannot follow and is dropped.
   */
  private static void keepForNextScreen() {
    AbstractContainerScreen<?> current = screen;
    LocalPlayer player = Minecraft.getInstance().player;
    if (current == null || player == null || count == 0) {
      clear();
      return;
    }
    AbstractContainerMenu menu = current.getMenu();
    for (int index = count - 1; index >= 0; index--) {
      int root = WINDOWS[index].path()[0];
      Slot slot = root < menu.slots.size() ? menu.getSlot(root) : null;
      if (slot == null || slot.container != player.getInventory()) {
        remove(index);
      }
    }
    for (int index = 0; index < count; index++) {
      PENDING_ROOTS[index] = menu.getSlot(WINDOWS[index].path()[0]).getContainerSlot();
    }
    pending = count > 0;
    pendingUntil = Util.getMillis() + SWITCH_MILLIS;
    screen = null;
    access = null;
    noticeShown = false;
    focused = NONE;
    hoveredWindow = NONE;
    hoveredCell = PinnedWindow.NO_CELL;
    pressConsumed = false;
  }

  /**
   * Puts the waiting windows on the slots of {@code next}'s menu for the same inventory slots. An
   * ender window does not enter the ender menu, which shows the same contents.
   */
  private static void carryOver(AbstractContainerScreen<?> next) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) {
      return;
    }
    AbstractContainerMenu menu = next.getMenu();
    boolean enderMenu = EnderMenuScreen.shows(next);
    for (int index = 0; index < count; index++) {
      Slot slot = PinnedWindowClicks.inventorySlot(player, menu, PENDING_ROOTS[index]);
      if (slot == null || (enderMenu && WINDOWS[index].kind() == StorageKind.ENDER_CHEST)) {
        PENDING_ROOTS[index] = NONE;
      } else {
        WINDOWS[index].reroot(slot.index);
      }
    }
    for (int index = count - 1; index >= 0; index--) {
      if (PENDING_ROOTS[index] == NONE) {
        remove(index);
      }
    }
  }

  /** Whether {@code slot}'s item has its own window pinned. One field read while none is. */
  static boolean isPinned(Slot slot) {
    for (int index = 0; index < count; index++) {
      PinnedWindow window = WINDOWS[index];
      if (window.depth() == 1 && window.path()[0] == slot.index) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether {@code stack}'s contents are already open in a window: {@code stack} itself or, for an
   * ender chest, any ender window. One field read while none is pinned. Asked from {@code
   * ItemStack#getTooltipImage}, also off the render thread (creative search trees), where no window
   * is read.
   */
  public static boolean locks(ItemStack stack) {
    if (count == 0 || !Minecraft.getInstance().isSameThread()) {
      return false;
    }
    boolean enderChest = StorageKind.of(stack) == StorageKind.ENDER_CHEST;
    for (int index = 0; index < count; index++) {
      PinnedWindow window = WINDOWS[index];
      if (window.stack() == stack || (enderChest && window.kind() == StorageKind.ENDER_CHEST)) {
        return true;
      }
    }
    return false;
  }

  /** Where the first window went on the screen. */
  static int firstWindowX() {
    return LAID_X[0];
  }

  /** Whether the notice shows: a middle click found the ender menu already open. */
  static boolean isNoticeShown() {
    return noticeShown;
  }

  private static boolean enabled() {
    ClientShulkerState state = StorageInput.state();
    return state != null
        && state.pinnedWindowsAllowed()
        && StorageInput.preferences().pinnedWindows();
  }

  /** After the screen's slots (and recipe book), before the cursor item and tooltips. */
  private static void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    if (noticeShown) {
      extractNotice(graphics);
    }
    if (count == 0) {
      return;
    }
    AbstractContainerScreen<?> current = screen;
    LocalPlayer player = Minecraft.getInstance().player;
    if (current == null || player == null || !enabled()) {
      while (count > 0) {
        remove(count - 1);
      }
      return;
    }
    AbstractContainerMenu menu = current.getMenu();
    for (int index = count - 1; index >= 0; index--) {
      if (!WINDOWS[index].resolve(menu, player)) {
        remove(index);
      }
    }
    if (count == 0) {
      return;
    }
    Font font = current.getFont();
    layout(current, font);
    hoveredWindow = NONE;
    hoveredCell = PinnedWindow.NO_CELL;
    AbstractContainerScreenAccessor screenAccess = access;
    Slot hoveredSlot = screenAccess == null ? null : screenAccess.shulkertoolkit$hoveredSlot();
    ItemStack hoveredStack = hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem();
    int selected = StorageStates.of(player).selectedItem();
    for (int index = 0; index < count; index++) {
      if (WINDOWS[index].contains(font, mouseX, mouseY)) {
        hoveredWindow = index;
        hoveredCell = WINDOWS[index].cellAt(font, mouseX, mouseY);
        focused = index;
      }
    }
    if (hoveredWindow != lastWindow || hoveredCell != lastCell) {
      // Leaving a cell drops the selection made in its item, as leaving a menu slot does.
      if (lastCell != PinnedWindow.NO_CELL) {
        StorageStates.of(player).setSelectedItem(StorageGrid.NO_ITEM);
        selected = StorageGrid.NO_ITEM;
      }
      lastWindow = hoveredWindow;
      lastCell = hoveredCell;
    }
    int hoveredChild = childAt(hoveredWindow, hoveredCell);
    for (int index = 0; index < count; index++) {
      PinnedWindow window = WINDOWS[index];
      if (window.visible()) {
        window.beginDraw(graphics);
        int marked;
        if (index == hoveredWindow) {
          marked = hoveredCell;
        } else if (index == hoveredChild) {
          marked = selected >= 0 ? selected : PinnedWindow.NO_CELL;
        } else {
          marked = drivenCell(window, hoveredStack, selected);
        }
        window.extract(graphics, font, marked);
        extractChildEyes(graphics, window);
        window.endDraw(graphics);
      }
    }
    if (hoveredCell != PinnedWindow.NO_CELL && menu.getCarried().isEmpty()) {
      ItemStack stack = WINDOWS[hoveredWindow].stackAt(hoveredCell);
      if (!stack.isEmpty()) {
        // Vanilla's hovered-slot tooltip (AbstractContainerScreen#extractTooltip) builds the same;
        // no contents view when the item's own window is open.
        graphics.setTooltipForNextFrame(
            font,
            Screen.getTooltipFromItem(Minecraft.getInstance(), stack),
            hoveredChild == NONE ? stack.getTooltipImage() : Optional.empty(),
            mouseX,
            mouseY,
            stack.get(DataComponents.TOOLTIP_STYLE));
      }
    }
  }

  /** Above the menu, so it reads over the screen's dimmed background. */
  private static void extractNotice(GuiGraphicsExtractor graphics) {
    AbstractContainerScreen<?> current = screen;
    AbstractContainerScreenAccessor screenAccess = access;
    if (current == null
        || screenAccess == null
        || Util.getMillis() - noticeStart >= NOTICE_MILLIS) {
      noticeShown = false;
      return;
    }
    int y = Math.max(NOTICE_MARGIN, screenAccess.shulkertoolkit$topPos() - NOTICE_GAP);
    graphics.centeredText(current.getFont(), ALREADY_OPEN, current.width / 2, y, NOTICE_COLOR);
  }

  /**
   * The cell marked in {@code window} while the mouse is on a menu slot holding its item (any ender
   * chest, for an ender window): the item's selection, which the wheel moves there instead of in a
   * tooltip.
   */
  private static int drivenCell(PinnedWindow window, ItemStack hovered, int selected) {
    boolean drives =
        window.stack() == hovered
            || (window.kind() == StorageKind.ENDER_CHEST
                && StorageKind.of(hovered) == StorageKind.ENDER_CHEST);
    return drives && selected >= 0 ? selected : PinnedWindow.NO_CELL;
  }

  /** The window pinned on the item in {@code cell} of window {@code parentIndex}, or none. */
  private static int childAt(int parentIndex, int cell) {
    if (parentIndex == NONE || cell == PinnedWindow.NO_CELL) {
      return NONE;
    }
    PinnedWindow parent = WINDOWS[parentIndex];
    for (int index = 0; index < count; index++) {
      PinnedWindow child = WINDOWS[index];
      if (PinnedPaths.isChild(parent.path(), parent.depth(), child.path(), child.depth())
          && child.path()[parent.depth()] == cell) {
        return index;
      }
    }
    return NONE;
  }

  /** Eyes over the boxes inside {@code window} whose own windows are pinned. */
  private static void extractChildEyes(GuiGraphicsExtractor graphics, PinnedWindow window) {
    for (int index = 0; index < count; index++) {
      PinnedWindow child = WINDOWS[index];
      if (PinnedPaths.isChild(window.path(), window.depth(), child.path(), child.depth())) {
        int cell = child.path()[window.depth()];
        StorageIndicator.extractEye(graphics, window.itemX(cell), window.itemY(cell));
      }
    }
  }

  /** Places the windows again when one changed size or the container moved (recipe book). */
  private static void layout(AbstractContainerScreen<?> current, Font font) {
    AbstractContainerScreenAccessor screenAccess = access;
    if (screenAccess == null) {
      return;
    }
    int left = screenAccess.shulkertoolkit$leftPos();
    int top = screenAccess.shulkertoolkit$topPos();
    boolean recipeBook =
        (Object) current instanceof AbstractRecipeBookScreenAccessor book
            && book.shulkertoolkit$recipeBookComponent().isVisible();
    boolean changed =
        layoutDirty
            || left != laidLeft
            || top != laidTop
            || current.width != laidWidth
            || current.height != laidHeight
            || recipeBook != laidRecipeBook;
    for (int index = 0; index < count; index++) {
      int width = WINDOWS[index].width(font);
      int height = WINDOWS[index].height(font);
      changed |= width != WIDTHS[index] || height != HEIGHTS[index];
      WIDTHS[index] = width;
      HEIGHTS[index] = height;
    }
    if (!changed) {
      return;
    }
    int right = left + screenAccess.shulkertoolkit$imageWidth();
    int bottom = top + screenAccess.shulkertoolkit$imageHeight();
    // Beside the container, unless the recipe book takes its left: then above and below it.
    int scale =
        PinnedLayout.placeScaled(
            current.width,
            current.height,
            left,
            top,
            clearOfButtons(current, right, top, bottom),
            bottom,
            recipeBook,
            count,
            WIDTHS,
            HEIGHTS,
            SCALED_WIDTHS,
            SCALED_HEIGHTS,
            LAID_X,
            LAID_Y);
    for (int index = 0; index < count; index++) {
      WINDOWS[index].place(LAID_X[index], LAID_Y[index], scale);
    }
    layoutDirty = false;
    laidLeft = left;
    laidTop = top;
    laidWidth = current.width;
    laidHeight = current.height;
    laidRecipeBook = recipeBook;
  }

  /**
   * The container's right edge past any button other mods hang on it, as Inventory Management's
   * sort buttons (where they go is the player's setting, so they are read, not assumed). A widget
   * further out is not beside the container. One pass over the widgets, on layout changes only.
   */
  private static int clearOfButtons(
      AbstractContainerScreen<?> current, int right, int top, int bottom) {
    int edge = right;
    for (GuiEventListener child : current.children()) {
      if (child instanceof AbstractWidget widget
          && widget.visible
          && widget.getRight() > right
          && widget.getX() <= right + BUTTON_REACH
          && widget.getBottom() > top
          && widget.getY() < bottom) {
        edge = Math.max(edge, widget.getRight());
      }
    }
    return edge;
  }

  private static boolean onMouseClicked(MouseButtonEvent event) {
    AbstractContainerScreen<?> current = screen;
    AbstractContainerScreenAccessor screenAccess = access;
    LocalPlayer player = Minecraft.getInstance().player;
    if (current == null || screenAccess == null || player == null || !enabled()) {
      return false;
    }
    Font font = current.getFont();
    double mouseX = event.x();
    double mouseY = event.y();
    int button = event.button();
    int windowIndex = windowAt(font, mouseX, mouseY);
    if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
      // Creative: a plain middle click keeps vanilla's clone.
      if (player.hasInfiniteMaterials()
          && !event.hasControlDown()
          && !event.hasControlDownWithQuirk()) {
        return false;
      }
      if (windowIndex != NONE) {
        middleClickWindow(current, font, WINDOWS[windowIndex], mouseX, mouseY, player);
        pressConsumed = true;
        return true;
      }
      Slot slot = screenAccess.shulkertoolkit$slotAt(mouseX, mouseY);
      if (slot != null
          && slot.hasItem()
          && slot.allowModification(player)
          && togglePin(
              current, new int[] {slot.index}, new ItemStack[] {slot.getItem()}, 1, player)) {
        pressConsumed = true;
        return true;
      }
      return false;
    }
    boolean containerButton =
        button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT;
    if (windowIndex != NONE) {
      focused = windowIndex;
      if (containerButton) {
        clickWindow(current, font, WINDOWS[windowIndex], mouseX, mouseY, button, event, player);
      }
      pressConsumed = true;
      return true;
    }
    if (containerButton
        && event.hasShiftDown()
        && focused != NONE
        && current.getMenu().getCarried().isEmpty()) {
      Slot slot = screenAccess.shulkertoolkit$slotAt(mouseX, mouseY);
      if (slot != null && moveIn(current, WINDOWS[focused], slot, player)) {
        pressConsumed = true;
        return true;
      }
    }
    return false;
  }

  /**
   * The wheel over a shulker box or bundle in a window slot moves the selection inside it, shown in
   * its tooltip, as over a menu slot ({@code StorageMouseActions#onMouseScrolled}); a right click
   * then takes that stack. Not an ender chest, whose contents are not in the window.
   *
   * @return true when the wheel was used
   */
  private static boolean onMouseScrolled(double scrollX, double scrollY) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (count == 0
        || player == null
        || hoveredWindow == NONE
        || hoveredCell == PinnedWindow.NO_CELL
        || !StorageInput.contentsShown()) {
      return false;
    }
    ItemStack stack = WINDOWS[hoveredWindow].stackAt(hoveredCell);
    StorageKind kind = StorageKind.of(stack);
    if (kind == null
        || kind == StorageKind.ENDER_CHEST
        || !kind.viewable(stack)
        || !kind.hasContents(stack, player)
        || !TransferGate.allowsKind(player, kind)) {
      return false;
    }
    Vector2i wheelXY = WHEEL.onMouseScroll(scrollX, scrollY);
    int wheel = wheelXY.y == 0 ? -wheelXY.x : wheelXY.y;
    if (wheel == 0) {
      return true;
    }
    boolean vertical = StorageInput.isHeld(StorageInput.preferences().verticalTooltipScrolling());
    StorageStacking<ItemStack> view = kind.view(stack, player);
    int previous = StorageStates.of(player).selectedItem();
    int updated =
        StorageGrid.scroll(
            previous,
            kind.columns(view.size()),
            kind.rows(view.size()),
            view.size(),
            slot -> view.key(slot) != null,
            vertical ? 0 : -wheel,
            vertical ? -wheel : 0);
    if (updated != previous) {
      kind.toggleSelectedItem(stack, player, updated, false);
    }
    return true;
  }

  /** A release after a press the windows took: vanilla would drop the cursor stack outside. */
  private static boolean onMouseReleased() {
    if (!pressConsumed) {
      return false;
    }
    pressConsumed = false;
    return true;
  }

  /**
   * Number keys and the offhand key over a window slot swap with it, as over a vanilla slot; not
   * while a text field takes the keys (the anvil's name), as vanilla {@code
   * AnvilScreen#keyPressed}.
   */
  private static boolean onKeyPressed(KeyEvent event) {
    AbstractContainerScreen<?> current = screen;
    if (count == 0
        || current == null
        || hoveredCell == PinnedWindow.NO_CELL
        || hoveredWindow == NONE
        || !current.getMenu().getCarried().isEmpty()
        || StorageInput.isTyping(current)) {
      return false;
    }
    PinnedWindow window = WINDOWS[hoveredWindow];
    if (window.kind() == StorageKind.BUNDLE) {
      return false;
    }
    Minecraft minecraft = Minecraft.getInstance();
    int button = NONE;
    if (minecraft.options.keySwapOffhand.matches(event)) {
      button = PinnedAction.OFFHAND_BUTTON;
    }
    for (int slot = 0; slot < PinnedAction.HOTBAR_SLOTS && button == NONE; slot++) {
      if (minecraft.options.keyHotbarSlots[slot].matches(event)) {
        button = slot;
      }
    }
    LocalPlayer player = minecraft.player;
    if (button == NONE || player == null) {
      return false;
    }
    // The menu's slot rules, as the server checks them: a refused swap is not sent.
    if (PinnedWindowClicks.swapAllowed(
        player,
        PinnedWindowClicks.inventorySlot(player, current.getMenu(), button),
        player.getInventory().getItem(button),
        window.stackAt(hoveredCell))) {
      send(current, window, PinnedAction.SWAP, hoveredCell, button, player);
    }
    return true;
  }

  private static void middleClickWindow(
      AbstractContainerScreen<?> current,
      Font font,
      PinnedWindow window,
      double mouseX,
      double mouseY,
      LocalPlayer player) {
    if (window.onTitle(font, mouseX, mouseY)) {
      remove(indexOf(window));
      return;
    }
    int cell = window.cellAt(font, mouseX, mouseY);
    int depth = window.depth();
    if (cell == PinnedWindow.NO_CELL || depth >= PinnedPaths.MAX_DEPTH) {
      return;
    }
    int[] path = Arrays.copyOf(window.path(), depth + 1);
    path[depth] = cell;
    ItemStack[] stacks = new ItemStack[depth + 1];
    for (int level = 0; level < depth; level++) {
      stacks[level] = window.stackAtLevel(level);
    }
    stacks[depth] = window.stackAt(cell);
    togglePin(current, path, stacks, depth + 1, player);
  }

  /**
   * Unpins the window of the item at {@code path} or pins a new one (not past the limit, nor when
   * it fits nowhere on the screen).
   *
   * @return false when the item has no window (vanilla keeps the click)
   */
  private static boolean togglePin(
      AbstractContainerScreen<?> current,
      int[] path,
      ItemStack[] stacks,
      int depth,
      LocalPlayer player) {
    ItemStack stack = stacks[depth - 1];
    StorageKind kind = StorageKind.of(stack);
    ClientShulkerState state = StorageInput.state();
    if (kind == null
        || state == null
        || !kind.viewable(stack)
        || (kind == StorageKind.ENDER_CHEST && !state.enderPreviewAllowed())) {
      return false;
    }
    for (int index = 0; index < count; index++) {
      if (PinnedPaths.samePath(WINDOWS[index].path(), WINDOWS[index].depth(), path, depth)) {
        remove(index);
        return true;
      }
    }
    if (kind == StorageKind.ENDER_CHEST && (EnderMenuScreen.shows(current) || locks(stack))) {
      noticeShown = true;
      noticeStart = Util.getMillis();
      return true;
    }
    if (count >= StorageInput.preferences().maxPinnedWindows()) {
      return true;
    }
    PinnedWindow window = new PinnedWindow(path, stacks, depth);
    if (!window.resolve(current.getMenu(), player)) {
      return false;
    }
    WINDOWS[count] = window;
    count++;
    layoutDirty = true;
    layout(current, current.getFont());
    if (!window.visible()) {
      remove(count - 1);
      return true;
    }
    focused = count - 1;
    playOpenSound(kind, player);
    return true;
  }

  private static void clickWindow(
      AbstractContainerScreen<?> current,
      Font font,
      PinnedWindow window,
      double mouseX,
      double mouseY,
      int button,
      MouseButtonEvent event,
      LocalPlayer player) {
    if (window.onTitle(font, mouseX, mouseY)) {
      return;
    }
    int cell = window.cellAt(font, mouseX, mouseY);
    boolean hasItem = cell != PinnedWindow.NO_CELL && !window.stackAt(cell).isEmpty();
    if (event.hasShiftDown()) {
      if (hasItem) {
        send(
            current,
            window,
            PinnedAction.QUICK_MOVE_OUT,
            cell,
            PinnedAction.PRIMARY_BUTTON,
            player);
      }
      return;
    }
    boolean carrying = !current.getMenu().getCarried().isEmpty();
    // Chests need a slot; a bundle takes the cursor stack anywhere in its window.
    if (cell == PinnedWindow.NO_CELL && (window.kind() != StorageKind.BUNDLE || !carrying)) {
      return;
    }
    int buttonNum =
        button == InputConstants.MOUSE_BUTTON_LEFT
            ? PinnedAction.PRIMARY_BUTTON
            : PinnedAction.SECONDARY_BUTTON;
    send(current, window, PinnedAction.PICKUP, cell, buttonNum, player);
  }

  /** Shift-click on an inventory stack: into the last window used, when some of it fits. */
  private static boolean moveIn(
      AbstractContainerScreen<?> current, PinnedWindow window, Slot slot, LocalPlayer player) {
    if (slot.container != player.getInventory()
        || slot.getContainerSlot() >= Inventory.INVENTORY_SIZE
        || !slot.hasItem()
        || slot.index == window.path()[0]
        || !window.accepts(slot.getItem(), player)) {
      return false;
    }
    send(
        current,
        window,
        PinnedAction.QUICK_MOVE_IN,
        slot.index,
        PinnedAction.PRIMARY_BUTTON,
        player);
    return true;
  }

  private static void send(
      AbstractContainerScreen<?> current,
      PinnedWindow window,
      PinnedAction action,
      int slot,
      int button,
      LocalPlayer player) {
    StorageScreenHooks.sendControlScheme(player);
    if (ClientPlayNetworking.canSend(PinnedWindowClickPayload.TYPE)) {
      ClientPlayNetworking.send(
          new PinnedWindowClickPayload(
              current.getMenu().containerId,
              window.payloadLevels(),
              action,
              slot,
              button,
              StorageStates.of(player).selectedItem()));
    }
  }

  /** The opening sound of the window's container, heard by this player only. */
  private static void playOpenSound(StorageKind kind, LocalPlayer player) {
    if (!StorageInput.preferences().pinnedWindowSounds()) {
      return;
    }
    float random = player.getRandom().nextFloat();
    SimpleSoundInstance sound;
    if (kind == StorageKind.BUNDLE) {
      sound =
          SimpleSoundInstance.forUI(
              SoundEvents.BUNDLE_REMOVE_ONE,
              BUNDLE_PITCH + random * BUNDLE_PITCH_RANGE,
              BUNDLE_VOLUME);
    } else {
      SoundEvent event =
          kind == StorageKind.ENDER_CHEST
              ? SoundEvents.ENDER_CHEST_OPEN
              : SoundEvents.SHULKER_BOX_OPEN;
      sound = SimpleSoundInstance.forUI(event, OPEN_PITCH + random * OPEN_PITCH_RANGE, OPEN_VOLUME);
    }
    Minecraft.getInstance().getSoundManager().play(sound);
  }

  private static int windowAt(Font font, double mouseX, double mouseY) {
    for (int index = 0; index < count; index++) {
      if (WINDOWS[index].contains(font, mouseX, mouseY)) {
        return index;
      }
    }
    return NONE;
  }

  private static int indexOf(PinnedWindow window) {
    for (int index = 0; index < count; index++) {
      if (WINDOWS[index] == window) {
        return index;
      }
    }
    return NONE;
  }

  private static void remove(int index) {
    if (index < 0 || index >= count) {
      return;
    }
    System.arraycopy(WINDOWS, index + 1, WINDOWS, index, count - index - 1);
    count--;
    WINDOWS[count] = null;
    if (focused == index) {
      focused = NONE;
    } else if (focused > index) {
      focused--;
    }
    hoveredWindow = NONE;
    hoveredCell = PinnedWindow.NO_CELL;
    lastWindow = NONE;
    lastCell = PinnedWindow.NO_CELL;
    layoutDirty = true;
  }

  private static void clear() {
    pending = false;
    lastWindow = NONE;
    lastCell = PinnedWindow.NO_CELL;
    noticeShown = false;
    Arrays.fill(WINDOWS, null);
    count = 0;
    focused = NONE;
    hoveredWindow = NONE;
    hoveredCell = PinnedWindow.NO_CELL;
    pressConsumed = false;
    screen = null;
    access = null;
  }
}
