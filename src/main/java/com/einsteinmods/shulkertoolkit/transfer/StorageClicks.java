package com.einsteinmods.shulkertoolkit.transfer;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import com.einsteinmods.shulkertoolkit.core.StorageStacking;
import com.einsteinmods.shulkertoolkit.server.EnderChestSync;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Inventory clicks with container items, called from {@code AbstractContainerMenuMixin} on both
 * sides (the client predicts, the server decides; both run this code on the same inputs). Mirrors
 * Easy Shulker Boxes' {@code ContainerClickInputHandler} and {@code
 * ContainerItemStorage#overrideStackedOnOther}/{@code #overrideOtherStackedOnMe}: with {@link
 * ClickScheme#SINGLE_INPUT} a right click inserts the stack it lands on and extracts the selected
 * stack onto an empty slot or the cursor; {@link ClickScheme#SPLIT_INPUT} inserts with a left
 * click, as vanilla bundles. Single item movement moves one item per click. Other clicks stay
 * vanilla. Work per click is bounded by the container's slots.
 */
public final class StorageClicks {
  /** Volumes and pitches of vanilla {@code BundleItem#playInsertSound} and siblings. */
  private static final float SOUND_VOLUME = 0.8F;

  private static final float SOUND_BASE_PITCH = 0.8F;
  private static final float SOUND_PITCH_RANGE = 0.4F;
  private static final float FAIL_SOUND_VOLUME = 1.0F;
  private static final float FAIL_SOUND_PITCH = 1.0F;

  /** Set by the client: true while a mouse drag plays one sound on release instead. */
  private static BooleanSupplier clientSoundsMuted = () -> false;

  private StorageClicks() {}

  public static void installClientSoundMute(BooleanSupplier muted) {
    clientSoundsMuted = muted;
  }

  /**
   * Vanilla {@code AbstractContainerMenu#tryItemClickBehaviourOverride}: the carried container item
   * decides first, then the clicked one. {@link TriState#DEFAULT} leaves the click (and vanilla
   * bundle behaviour) to vanilla; otherwise the value is what vanilla must return.
   */
  public static TriState onItemClicked(
      AbstractContainerMenu menu,
      Player player,
      ClickAction action,
      Slot slot,
      ItemStack clicked,
      ItemStack carried) {
    StorageKind carriedKind = StorageKind.of(carried);
    StorageKind clickedKind = StorageKind.of(clicked);
    if (carriedKind == null && clickedKind == null) {
      return TriState.DEFAULT;
    }
    return decide(
        menu,
        player,
        action,
        slot,
        clicked,
        carried,
        carriedKind,
        clickedKind,
        TransferGate.allows(player));
  }

  /**
   * {@link #onItemClicked} with the transfer gate's answer given (GameTests: mock players have no
   * mod channels).
   */
  public static TriState decide(
      AbstractContainerMenu menu,
      Player player,
      ClickAction action,
      Slot slot,
      ItemStack clicked,
      ItemStack carried,
      @Nullable StorageKind carriedKind,
      @Nullable StorageKind clickedKind,
      boolean transferAllowed) {
    if (!transferAllowed) {
      return TriState.DEFAULT;
    }
    if (editable(carriedKind, carried, player, menu)) {
      return TriState.of(stackedOnOther(carriedKind, carried, slot, action, player));
    }
    if (editable(clickedKind, clicked, player, menu)) {
      SlotAccess carriedAccess = SlotAccess.of(menu::getCarried, menu::setCarried);
      return TriState.of(
          otherStackedOnMe(clickedKind, clicked, carried, slot, action, player, carriedAccess));
    }
    return TriState.DEFAULT;
  }

  /**
   * The container item {@code self} on the cursor clicked onto {@code slot}. Easy Shulker Boxes'
   * {@code ContainerItemStorage#overrideStackedOnOther}.
   *
   * @return true when handled (vanilla must skip the click)
   */
  public static boolean stackedOnOther(
      StorageKind kind, ItemStack self, Slot slot, ClickAction action, Player player) {
    ControlScheme control = StorageStates.of(player).controlScheme();
    boolean single = control.moveSingleItem();
    ClickScheme scheme = control.effectiveScheme();
    boolean primary = action == ClickAction.PRIMARY;
    ItemStack other = slot.getItem();
    if (scheme.extracts(primary) && (other.isEmpty() || single)) {
      StorageStacking<ItemStack> slots = kind.open(self, player, true, single);
      StorageStacking.Removal<ItemStack> removal =
          slots.removeOne(other.isEmpty() ? null : other, other.getCount());
      if (removal != null) {
        ItemStack remainder = slot.safeInsert(removal.key().copyWithCount(removal.count()));
        if (!remainder.isEmpty()) {
          // What the slot refused goes back where it came from; it fitted a moment ago.
          slots.tryInsert(
              remainder.copy(), remainder.getCount(), remainder.getCount(), removal.slot());
        }
        if (!single) {
          playRemoveSound(player);
        }
      }
      kind.save(self, player, slots);
      broadcastChanges(kind, player);
      return true;
    }
    if (scheme.inserts(primary) && (!other.isEmpty() || single)) {
      ItemStack taken = slot.safeTake(other.getCount(), other.getCount(), player);
      int moved = 0;
      if (!taken.isEmpty()) {
        StorageStacking<ItemStack> slots = kind.open(self, player, true, single);
        moved = slots.tryInsert(taken.copy(), taken.getCount(), kind.acceptableCount(self, taken));
        taken.shrink(moved);
        kind.save(self, player, slots);
      }
      if (!single) {
        playInsertOutcome(player, moved);
      }
      slot.safeInsert(taken);
      broadcastChanges(kind, player);
      return true;
    }
    return false;
  }

  /**
   * {@code carried} on the cursor clicked onto the container item {@code self} in {@code slot}.
   * Easy Shulker Boxes' {@code ContainerItemStorage#overrideOtherStackedOnMe}.
   *
   * @return true when handled (vanilla must skip the click)
   */
  public static boolean otherStackedOnMe(
      StorageKind kind,
      ItemStack self,
      ItemStack carried,
      Slot slot,
      ClickAction action,
      Player player,
      SlotAccess carriedAccess) {
    ControlScheme control = StorageStates.of(player).controlScheme();
    boolean single = control.moveSingleItem();
    ClickScheme scheme = control.effectiveScheme();
    boolean primary = action == ClickAction.PRIMARY;
    if (primary && carried.isEmpty()) {
      if (single) {
        return true;
      }
      return keepVanilla(kind, self, slot, player);
    }
    if (scheme.extracts(primary) && (carried.isEmpty() || single)) {
      if (slot.allowModification(player)) {
        StorageStacking<ItemStack> slots = kind.open(self, player, true, single);
        StorageStacking.Removal<ItemStack> removal =
            slots.removeOne(carried.isEmpty() ? null : carried, carried.getCount());
        if (removal != null) {
          if (carried.isEmpty()) {
            carriedAccess.set(removal.key().copyWithCount(removal.count()));
          } else {
            carried.grow(removal.count());
          }
          if (!single) {
            playRemoveSound(player);
          }
        }
        kind.save(self, player, slots);
      }
      slot.set(self);
      broadcastChanges(kind, player);
      return true;
    }
    if (scheme.inserts(primary) && !carried.isEmpty()) {
      if (slot.allowModification(player)) {
        StorageStacking<ItemStack> slots = kind.open(self, player, true, single);
        int moved =
            slots.tryInsert(
                carried.copy(), carried.getCount(), kind.acceptableCount(self, carried));
        carried.shrink(moved);
        kind.save(self, player, slots);
        if (!single) {
          playInsertOutcome(player, moved);
        }
      }
      slot.set(self);
      broadcastChanges(kind, player);
      return true;
    }
    return keepVanilla(kind, self, slot, player);
  }

  /**
   * Whether a click with {@code self} on the cursor would insert what {@code slot} holds (Easy
   * Shulker Boxes' {@code matchesPrimaryClickAction}): also what a mouse drag inserts from.
   */
  public static boolean insertsFrom(StorageKind kind, ItemStack self, Slot slot, Player player) {
    return slot.hasItem() && kind.canAddItem(self, slot.getItem(), player);
  }

  /**
   * Whether a click with {@code self} on the cursor would extract into {@code slot} (Easy Shulker
   * Boxes' {@code matchesSecondaryClickAction}): also what a mouse drag extracts to.
   */
  public static boolean extractsTo(StorageKind kind, ItemStack self, Slot slot, Player player) {
    return !slot.hasItem() && !kind.view(self, player).isEmpty();
  }

  /** The selected slot of {@code player}'s container items on this side. */
  public static int selectedItem(Player player) {
    return StorageStates.of(player).selectedItem();
  }

  /** Clears the selection on a plain pickup, as vanilla bundles, and lets vanilla click. */
  private static boolean keepVanilla(StorageKind kind, ItemStack self, Slot slot, Player player) {
    kind.toggleSelectedItem(self, player, StorageGrid.NO_ITEM, true);
    slot.set(self);
    return false;
  }

  private static boolean editable(
      @Nullable StorageKind kind, ItemStack stack, Player player, AbstractContainerMenu menu) {
    return kind != null
        && kind.allowModification(stack)
        && TransferGate.allowsKind(player, kind)
        // The creative inventory tab clicks the inventory menu on the client only; the server
        // would never see an ender chest change made there.
        && (kind != StorageKind.ENDER_CHEST || menu == player.containerMenu);
  }

  /** As vanilla {@code BundleItem#broadcastChangesOnContainerMenu}; the ender chest syncs. */
  private static void broadcastChanges(StorageKind kind, Player player) {
    if (kind == StorageKind.ENDER_CHEST) {
      if (player instanceof ServerPlayer serverPlayer) {
        EnderChestSync.sendContents(serverPlayer);
      }
      return;
    }
    AbstractContainerMenu menu = player.containerMenu;
    if (menu != null) {
      menu.slotsChanged(player.getInventory());
    }
  }

  private static void playInsertOutcome(Player player, int moved) {
    if (moved > 0) {
      playSound(player, false);
    } else if (!soundsMuted(player)) {
      player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, FAIL_SOUND_VOLUME, FAIL_SOUND_PITCH);
    }
  }

  private static void playRemoveSound(Player player) {
    playSound(player, true);
  }

  /** Vanilla {@code BundleItem#playInsertSound} / {@code #playRemoveOneSound}. */
  public static void playSound(Player player, boolean removed) {
    if (!soundsMuted(player)) {
      player.playSound(
          removed ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.BUNDLE_INSERT,
          SOUND_VOLUME,
          SOUND_BASE_PITCH + player.level().getRandom().nextFloat() * SOUND_PITCH_RANGE);
    }
  }

  private static boolean soundsMuted(Player player) {
    return player.level().isClientSide() && clientSoundsMuted.getAsBoolean();
  }
}
