package com.einsteinmods.shulkertoolkit.pick;

import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PickPlanner;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.server.PlayerSession;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Takes items out of the shulker boxes in a player's hotbar and main inventory (slots 0-35, in slot
 * order): pick block when the item is in no loose stack, and auto refill when the held stack runs
 * out. Server thread only. Cost only on a pick or when a held stack runs out: at most 36 slots × 27
 * box slots read, no stack created per slot.
 */
public final class ShulkerPick {
  /** Finding one matching item is enough to know a box holds it. */
  private static final int ONE_ITEM = 1;

  private ShulkerPick() {}

  /** Outcome of {@link #takeOut}. */
  public enum Status {
    /** Items moved to the hotbar slot, which is now selected. */
    TAKEN,
    /** No shulker box in the inventory holds the item; nothing changed. */
    NOT_IN_ANY_BOX,
    /** The hotbar is full and so is the inventory; nothing changed, so nothing is lost. */
    NO_ROOM,
    /** A loose stack already holds the item (a repeated request); nothing changed. */
    IN_INVENTORY
  }

  /**
   * What {@link #takeOut} did.
   *
   * @param status the outcome
   * @param boxSlot inventory slot of the box taken from, after the move, or {@link
   *     PickPlanner#NO_SLOT}
   * @param hotbarSlot hotbar slot now holding the items, or {@link PickPlanner#NO_SLOT}
   * @param amount items taken; 0 unless {@link Status#TAKEN}
   */
  public record TakeOutResult(Status status, int boxSlot, int hotbarSlot, int amount) {
    static final TakeOutResult NOT_IN_ANY_BOX =
        new TakeOutResult(Status.NOT_IN_ANY_BOX, PickPlanner.NO_SLOT, PickPlanner.NO_SLOT, 0);
    static final TakeOutResult NO_ROOM =
        new TakeOutResult(Status.NO_ROOM, PickPlanner.NO_SLOT, PickPlanner.NO_SLOT, 0);
    static final TakeOutResult IN_INVENTORY =
        new TakeOutResult(Status.IN_INVENTORY, PickPlanner.NO_SLOT, PickPlanner.NO_SLOT, 0);

    public boolean succeeded() {
      return status == Status.TAKEN;
    }
  }

  /**
   * Hook for vanilla {@code ServerGamePacketListenerImpl#tryPickItem} when {@code
   * Inventory#findSlotMatchingItem} found no loose stack: {@link #pickIfAllowed} with the player's
   * session. Players whose client lacks the mod get vanilla.
   */
  public static void onPickNotInInventory(ServerPlayer player, ItemStack picked) {
    PlayerSession session = ServerPlayerSessions.modClientSession(player.getUUID());
    if (session != null) {
      pickIfAllowed(player, picked, session.policy(), session.preferences());
    }
  }

  /**
   * Pulls {@code picked} out of a shulker box when {@code policy} allows picks, in the player's
   * {@link PickMode}. Creative (infinite materials) stays vanilla.
   */
  public static TakeOutResult pickIfAllowed(
      ServerPlayer player, ItemStack picked, ServerPolicy policy, PlayerPreferences preferences) {
    if (player.hasInfiniteMaterials() || !policy.allowPick()) {
      return TakeOutResult.NOT_IN_ANY_BOX;
    }
    return takeOut(player, picked, preferences.pickMode());
  }

  /**
   * Handles a {@code TakeOutPayload}: the same rules and sync as a pick block that found the item
   * only in a box (vanilla {@code ServerGamePacketListenerImpl#tryPickItem}: disabled items are
   * ignored, then {@code ClientboundSetHeldSlotPacket} and {@code inventoryMenu.broadcastChanges()}
   * on success). Nothing happens when a loose stack already holds the item, so a request repeated
   * before the client saw the first one never takes out twice.
   */
  public static TakeOutResult takeOutOnRequest(
      ServerPlayer player, ItemStack wanted, ServerPolicy policy, PlayerPreferences preferences) {
    if (wanted.isEmpty() || !wanted.isItemEnabled(player.level().enabledFeatures())) {
      return TakeOutResult.NOT_IN_ANY_BOX;
    }
    Inventory inventory = player.getInventory();
    if (inventory.findSlotMatchingItem(wanted) != PickPlanner.NO_SLOT) {
      return TakeOutResult.IN_INVENTORY;
    }
    TakeOutResult result = pickIfAllowed(player, wanted, policy, preferences);
    if (result.succeeded()) {
      player.connection.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
      player.inventoryMenu.broadcastChanges();
    }
    return result;
  }

  /**
   * Whether a shulker box among {@code items} holds {@code wanted} (same item and components, as
   * {@link #takeOut} matches). Read-only, so the client can check before sending a request.
   */
  public static boolean anyBoxHolds(NonNullList<ItemStack> items, ItemStack wanted) {
    return !wanted.isEmpty()
        && PickPlanner.findExtraction(new InventoryBoxes(items, WantedItem.of(wanted)), ONE_ITEM)
            != null;
  }

  /**
   * Moves {@code wanted} (one item, or up to a full stack) from the first shulker box holding it
   * into a hotbar slot and selects it, placed the way vanilla {@code Inventory#addAndPickItem}
   * picks: the {@code Inventory#getSuitableHotbarSlot} slot, its previous stack moved to a free
   * slot; refused when there is none, so no item is ever lost or duplicated. No policy or game mode
   * checks: callers decide. The caller syncs the client as vanilla {@code tryPickItem} does
   * (selected-slot packet, then {@code inventoryMenu.broadcastChanges()}).
   */
  public static TakeOutResult takeOut(ServerPlayer player, ItemStack wanted, PickMode mode) {
    if (wanted.isEmpty()) {
      return TakeOutResult.NOT_IN_ANY_BOX;
    }
    WantedItem item = WantedItem.of(wanted);
    Inventory inventory = player.getInventory();
    NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
    PickPlanner.Extraction extraction =
        PickPlanner.findExtraction(
            new InventoryBoxes(items, item), PickPlanner.wantedAmount(mode, item.maxStackSize()));
    if (extraction == null) {
      return TakeOutResult.NOT_IN_ANY_BOX;
    }
    int target = inventory.getSuitableHotbarSlot();
    PickPlanner.Placement placement =
        PickPlanner.placement(target, items.get(target).isEmpty(), inventory.getFreeSlot());
    if (placement == null) {
      return TakeOutResult.NO_ROOM;
    }
    int boxSlot = extraction.inventorySlot();
    ItemStack taken = BoxContents.take(items.get(boxSlot), item, extraction.amount());
    if (placement.displacedTo() != PickPlanner.NO_SLOT) {
      items.set(placement.displacedTo(), items.get(target));
      if (boxSlot == target) {
        boxSlot = placement.displacedTo();
      }
    }
    items.set(target, taken);
    inventory.setSelectedSlot(target);
    return new TakeOutResult(Status.TAKEN, boxSlot, target, taken.getCount());
  }

  /**
   * Hook for the end of vanilla {@code ServerPlayerGameMode#useItemOn} and {@code #useItem}:
   * refills the hand from the first shulker box holding the same item when the used stack ran out
   * during that call and left the hand empty. Cheapest checks first: a use that leaves items costs
   * two comparisons.
   *
   * @param countBefore the used stack's count when the call started
   */
  public static void afterItemUse(
      ServerPlayer player, InteractionHand hand, ItemStack used, int countBefore) {
    if (countBefore <= 0
        || !used.isEmpty()
        || !player.getItemInHand(hand).isEmpty()
        || player.hasInfiniteMaterials()) {
      return;
    }
    refillFromSession(player, hand, used);
  }

  /**
   * Hook for vanilla {@code LivingEntity#completeUsingItem} (eating, drinking and other items used
   * over time), after the result went to the hand: refills it when the used stack ran out. The hand
   * may then hold what the item turned into ({@code UseRemainder}: glass bottle, bowl, bucket);
   * that remainder goes to the inventory as vanilla {@code ItemUtils#createFilledResult} does.
   *
   * @param countBefore the used stack's count before {@code ItemStack#finishUsingItem}
   */
  public static void afterUseFinished(
      ServerPlayer player, InteractionHand hand, ItemStack used, int countBefore) {
    if (countBefore <= 0 || !used.isEmpty() || player.hasInfiniteMaterials()) {
      return;
    }
    refillFromSession(player, hand, used);
  }

  /**
   * Refills {@code hand} with what the run-out stack {@code used} was, when {@code policy} allows
   * refills and the player turned auto refill on. Whatever the hand holds (a use remainder) is
   * moved to the inventory, or dropped when it is full (vanilla {@code
   * Inventory#placeItemBackInInventory}); without a box holding the item nothing changes.
   *
   * @return whether anything was taken
   */
  public static boolean refillIfAllowed(
      ServerPlayer player,
      InteractionHand hand,
      ItemStack used,
      ServerPolicy policy,
      PlayerPreferences preferences) {
    if (!policy.allowRefill() || !preferences.autoRefill()) {
      return false;
    }
    WantedItem item = WantedItem.ofRunOut(used);
    return item != null && refill(player, hand, item);
  }

  private static void refillFromSession(ServerPlayer player, InteractionHand hand, ItemStack used) {
    PlayerSession session = ServerPlayerSessions.modClientSession(player.getUUID());
    if (session != null) {
      refillIfAllowed(player, hand, used, session.policy(), session.preferences());
    }
  }

  /**
   * Puts up to a full stack of {@code item} from the first shulker box holding it into {@code
   * hand}, moves what the hand held (empty or a use remainder) to the inventory, and syncs the
   * inventory at once so the client's next use sees it.
   *
   * @return whether anything was taken
   */
  private static boolean refill(ServerPlayer player, InteractionHand hand, WantedItem item) {
    NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
    PickPlanner.Extraction extraction =
        PickPlanner.findExtraction(new InventoryBoxes(items, item), item.maxStackSize());
    if (extraction == null) {
      return false;
    }
    ItemStack remainder = player.getItemInHand(hand);
    ItemStack taken =
        BoxContents.take(items.get(extraction.inventorySlot()), item, extraction.amount());
    player.setItemInHand(hand, taken);
    if (!remainder.isEmpty()) {
      player.getInventory().placeItemBackInInventory(remainder, Prediction.SERVER_ONLY);
    }
    player.containerMenu.broadcastChanges();
    return true;
  }

  /** The inventory's shulker boxes as the planner reads them; caches the box being read. */
  private static final class InventoryBoxes implements PickPlanner.BoxView {
    private final NonNullList<ItemStack> items;
    private final WantedItem wanted;
    private int readSlot = PickPlanner.NO_SLOT;
    private @Nullable List<Optional<ItemStackTemplate>> readBox;

    InventoryBoxes(NonNullList<ItemStack> items, WantedItem wanted) {
      this.items = items;
      this.wanted = wanted;
    }

    @Override
    public int inventorySlots() {
      return items.size();
    }

    @Override
    public int boxSlots(int inventorySlot) {
      List<Optional<ItemStackTemplate>> box = read(inventorySlot);
      return box == null ? 0 : box.size();
    }

    @Override
    public int matchingCount(int inventorySlot, int boxSlot) {
      List<Optional<ItemStackTemplate>> box = read(inventorySlot);
      if (box == null) {
        return 0;
      }
      Optional<ItemStackTemplate> content = box.get(boxSlot);
      return content.isPresent() && wanted.matches(content.get()) ? content.get().count() : 0;
    }

    private @Nullable List<Optional<ItemStackTemplate>> read(int inventorySlot) {
      if (inventorySlot != readSlot) {
        readSlot = inventorySlot;
        readBox = BoxContents.slotsOf(items.get(inventorySlot));
      }
      return readBox;
    }
  }
}
