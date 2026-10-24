package com.einsteinmods.shulkertoolkit.client.storage;

import com.einsteinmods.shulkertoolkit.client.mixin.AbstractContainerScreenAccessor;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.network.ControlSchemePayload;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Wires {@link StorageMouseActions} into container screens through Fabric's screen events, as Easy
 * Shulker Boxes' {@code ItemSlotMouseActionHandler}: the cursor stack is offered every input first,
 * then the hovered slot's stack; a consumed input never reaches vanilla. Not in the creative
 * screen, whose slot indexes are not the inventory menu's (its inventory tab wraps them). Also
 * sends the control scheme before clicks. Events fire on input only; the two draw events cost two
 * list checks per frame when nothing is dragged or shown.
 */
public final class StorageScreenHooks {
  private static ControlScheme lastSent = ControlScheme.DEFAULT;

  private StorageScreenHooks() {}

  public static void register() {
    ScreenEvents.AFTER_INIT.register(
        (minecraft, screen, width, height) -> {
          if (!PinnedWindows.supports(screen)) {
            // Pins waiting for the next screen do not survive any other screen.
            PinnedWindows.forgetPending();
          }
          if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
          }
          // A text field taking the keys (the anvil's name) keeps them, as in vanilla.
          ScreenKeyboardEvents.allowKeyPress(screen)
              .register(
                  (current, event) ->
                      StorageInput.isTyping(current) || !StorageInput.toggle(event));
          if (!(screen instanceof CreativeModeInventoryScreen)
              && (Object) screen instanceof AbstractContainerScreenAccessor access) {
            if (PinnedWindows.supports(screen)) {
              PinnedWindows.install(containerScreen, access);
            }
            install(screen, containerScreen, access);
          }
        });
  }

  /**
   * Sends the control scheme when it differs from the last one sent (Easy Shulker Boxes' {@code
   * ensureHasSentContainerClientInput}), and keeps it for the click prediction. Called before every
   * container click; allocates only on a change.
   */
  public static void sendControlScheme(LocalPlayer player) {
    boolean single = StorageInput.singleItemMovement();
    ControlScheme last = lastSent;
    if (last.moveSingleItem() == single
        && last.clickScheme() == StorageInput.preferences().controlScheme()) {
      return;
    }
    ControlScheme scheme = new ControlScheme(single, StorageInput.preferences().controlScheme());
    lastSent = scheme;
    StorageStates.of(player).setControlScheme(scheme);
    if (ClientPlayNetworking.canSend(ControlSchemePayload.TYPE)) {
      ClientPlayNetworking.send(new ControlSchemePayload(scheme));
    }
  }

  /** On disconnect: the next server starts from the default scheme. */
  public static void reset() {
    lastSent = ControlScheme.DEFAULT;
  }

  private static void install(
      Screen screen,
      AbstractContainerScreen<?> containerScreen,
      AbstractContainerScreenAccessor access) {
    StorageMouseActions actions = new StorageMouseActions(containerScreen, access);
    access.shulkertoolkit$itemSlotMouseActions().addFirst(actions);
    ScreenMouseEvents.allowMouseClick(screen)
        .register(
            (current, event) ->
                !offer(
                    containerScreen,
                    access,
                    actions,
                    (slot, stack) -> actions.onMouseClicked(event, slot, stack)));
    ScreenMouseEvents.allowMouseRelease(screen)
        .register(
            (current, event) ->
                !offer(
                    containerScreen,
                    access,
                    actions,
                    (slot, stack) -> actions.onMouseReleased(event, slot, stack)));
    ScreenMouseEvents.allowMouseDrag(screen)
        .register(
            (current, event, dragX, dragY) ->
                !offer(
                    containerScreen,
                    access,
                    actions,
                    (slot, stack) -> actions.onMouseDragged(event, slot, stack)));
    ScreenMouseEvents.allowMouseScroll(screen)
        .register(
            (current, mouseX, mouseY, scrollX, scrollY) -> {
              ItemStack carried = containerScreen.getMenu().getCarried();
              if (!carried.isEmpty() && actions.matches(carried)) {
                actions.onMouseScrolled(scrollX, scrollY, StorageMouseActions.CURSOR, carried);
                return false;
              }
              return true;
            });
    ScreenKeyboardEvents.allowKeyPress(screen)
        .register(
            (current, event) ->
                StorageInput.isTyping(current)
                    || !offer(
                        containerScreen,
                        access,
                        actions,
                        (slot, stack) -> actions.onKeyPressed(event, slot, stack)));
    ScreenEvents.afterBackground(screen)
        .register(
            (current, graphics, mouseX, mouseY, partialTick) ->
                actions.onExtractBackground(graphics, mouseX, mouseY));
    ScreenEvents.afterForeground(screen)
        .register(
            (current, graphics, mouseX, mouseY, partialTick) ->
                actions.onExtractForeground(graphics, mouseX, mouseY));
  }

  /** One input offered to a container item's actions. */
  @FunctionalInterface
  private interface SlotInput {
    boolean consumes(int slotIndex, ItemStack stack);
  }

  /** Offers an input to the cursor stack first, then to the hovered slot's stack. */
  private static boolean offer(
      AbstractContainerScreen<?> screen,
      AbstractContainerScreenAccessor access,
      StorageMouseActions actions,
      SlotInput input) {
    ItemStack carried = screen.getMenu().getCarried();
    if (!carried.isEmpty()
        && actions.matches(carried)
        && input.consumes(StorageMouseActions.CURSOR, carried)) {
      return true;
    }
    Slot slot = access.shulkertoolkit$hoveredSlot();
    return slot != null
        && slot.hasItem()
        && actions.matches(slot)
        && input.consumes(slot.index, slot.getItem());
  }
}
