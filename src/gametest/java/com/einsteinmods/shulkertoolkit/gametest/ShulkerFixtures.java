package com.einsteinmods.shulkertoolkit.gametest;

import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Players, boxes and inventory snapshots shared by the pick and refill GameTests. */
final class ShulkerFixtures {
  /** Where test players stand: within block interaction range of {@link #TARGET}. */
  static final Vec3 PLAYER_POSITION = new Vec3(0.5, 1.0, 0.5);

  /** The block players pick or place on. */
  static final BlockPos TARGET = new BlockPos(2, 1, 2);

  private static final String PLAYER_NAME = "shulkertk-test";

  private ShulkerFixtures() {}

  /**
   * A player whose client has the mod: its session holds {@code preferences} as if its {@code
   * PlayerPreferencesPayload} had arrived. See {@link #playerWithoutMod}.
   */
  static ServerPlayer player(
      GameTestHelper helper, GameType gameType, PlayerPreferences preferences) {
    ServerPlayer player = playerWithoutMod(helper, gameType);
    ServerPlayerSessions.join(player.getUUID(), false);
    ServerPlayerSessions.updatePreferences(player.getUUID(), preferences);
    return player;
  }

  /**
   * A Fabric fake player (it has a packet listener, so vanilla pick handling runs) with a fresh
   * UUID, in {@code gameType}, with an empty inventory, never in the player list and without a mod
   * client (no preferences received).
   */
  static ServerPlayer playerWithoutMod(GameTestHelper helper, GameType gameType) {
    ServerPlayer player =
        FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), PLAYER_NAME));
    player.setGameMode(gameType);
    player.setPos(helper.absoluteVec(PLAYER_POSITION));
    player.getInventory().clearContent();
    player.getInventory().setSelectedSlot(0);
    return player;
  }

  /** An undyed shulker box holding {@code stacks} from its first slot on. */
  static ItemStack box(ItemStack... stacks) {
    ItemStack box = new ItemStack(Items.SHULKER_BOX);
    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(stacks)));
    return box;
  }

  /** Items of {@code item} inside the box. */
  static int countInBox(ItemStack box, Item item) {
    ItemContainerContents contents =
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
    return contents
        .nonEmptyItemCopyStream()
        .filter(stack -> stack.is(item))
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  /** Copies of the 36 hotbar and main inventory slots. */
  static List<ItemStack> snapshot(ServerPlayer player) {
    List<ItemStack> copies = new ArrayList<>();
    for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
      copies.add(stack.copy());
    }
    return copies;
  }

  /** Whether every inventory slot still holds what {@code before} recorded. */
  static boolean unchanged(ServerPlayer player, List<ItemStack> before) {
    List<ItemStack> now = player.getInventory().getNonEquipmentItems();
    for (int slot = 0; slot < now.size(); slot++) {
      if (!ItemStack.matches(before.get(slot), now.get(slot))) {
        return false;
      }
    }
    return true;
  }
}
