package com.einsteinmods.shulkertoolkit.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einsteinmods.shulkertoolkit.core.ClickScheme;
import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.CraftDepth;
import com.einsteinmods.shulkertoolkit.core.PickMode;
import com.einsteinmods.shulkertoolkit.core.PlayerPreferences;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PayloadCodecTest {
  private static final int UNKNOWN_PICK_MODE_ID = 99;
  private static final int SELECTED_MENU_SLOT = 40;
  private static final int SELECTED_BOX_SLOT = 26;

  /** Item stack codecs need the vanilla registries. */
  @BeforeAll
  static void bootstrap() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  private static RegistryFriendlyByteBuf buffer() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
  }

  private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
    RegistryFriendlyByteBuf buffer = buffer();
    codec.encode(buffer, payload);
    return codec.decode(buffer);
  }

  @Test
  void serverPolicyKeepsEveryFlag() {
    for (ServerPolicy policy :
        List.of(
            ServerPolicy.UNRESTRICTED,
            new ServerPolicy(false, true, false, true, false, true),
            new ServerPolicy(true, false, true, false, true, false),
            new ServerPolicy(true, true, true, true, true, true, false),
            new ServerPolicy(true, true, true, true, true, true, true, false),
            new ServerPolicy(true, true, true, true, true, true, true, true, false))) {
      assertEquals(
          policy, roundTrip(ServerPolicyPayload.CODEC, new ServerPolicyPayload(policy)).policy());
    }
  }

  @Test
  void playerPreferencesKeepEveryPickMode() {
    for (PickMode mode : PickMode.values()) {
      PlayerPreferences preferences = new PlayerPreferences(mode, false, true);

      assertEquals(
          preferences,
          roundTrip(PlayerPreferencesPayload.CODEC, new PlayerPreferencesPayload(preferences))
              .preferences());
    }
  }

  @Test
  void playerPreferencesKeepBothPickupOptions() {
    for (PlayerPreferences preferences :
        List.of(
            new PlayerPreferences(PickMode.DEFAULT, true, false, true, false),
            new PlayerPreferences(PickMode.DEFAULT, true, false, false, true))) {
      assertEquals(
          preferences,
          roundTrip(PlayerPreferencesPayload.CODEC, new PlayerPreferencesPayload(preferences))
              .preferences());
    }
  }

  @Test
  void playerPreferencesKeepEveryCraftDepth() {
    for (CraftDepth depth : CraftDepth.values()) {
      PlayerPreferences preferences =
          new PlayerPreferences(PickMode.DEFAULT, true, false, false, false, false, depth);

      assertEquals(
          preferences,
          roundTrip(PlayerPreferencesPayload.CODEC, new PlayerPreferencesPayload(preferences))
              .preferences());
    }
  }

  @Test
  void unknownPickModeAndCraftDepthIdsDecodeAsDefault() {
    RegistryFriendlyByteBuf buffer = buffer();
    buffer.writeByte(UNKNOWN_PICK_MODE_ID);
    buffer.writeBoolean(true);
    buffer.writeBoolean(true);
    buffer.writeBoolean(false);
    buffer.writeBoolean(false);
    buffer.writeBoolean(true);
    buffer.writeByte(UNKNOWN_PICK_MODE_ID);

    assertEquals(
        new PlayerPreferences(PickMode.DEFAULT, true, true, false, false, true, CraftDepth.DEFAULT),
        PlayerPreferencesPayload.CODEC.decode(buffer).preferences());
  }

  @Test
  void storedItemSelectionKeepsEveryField() {
    for (SelectStoredItemPayload payload :
        List.of(
            new SelectStoredItemPayload(SELECTED_MENU_SLOT, SELECTED_BOX_SLOT, false),
            new SelectStoredItemPayload(
                SelectStoredItemPayload.CARRIED, StorageGrid.NO_ITEM, true))) {
      assertEquals(payload, roundTrip(SelectStoredItemPayload.CODEC, payload));
    }
  }

  @Test
  void controlSchemeKeepsEveryValue() {
    for (ClickScheme scheme : ClickScheme.values()) {
      for (boolean single : new boolean[] {false, true}) {
        ControlScheme controlScheme = new ControlScheme(single, scheme);

        assertEquals(
            controlScheme,
            roundTrip(ControlSchemePayload.CODEC, new ControlSchemePayload(controlScheme))
                .controlScheme());
      }
    }
  }

  @Test
  void enderContentsAndSlotsKeepEmptySlots() {
    List<ItemStack> empty = List.of(ItemStack.EMPTY, ItemStack.EMPTY);

    assertEquals(
        empty.size(),
        roundTrip(EnderContentsPayload.CODEC, new EnderContentsPayload(empty)).items().size());
    EnderSlotPayload slot =
        roundTrip(EnderSlotPayload.CODEC, new EnderSlotPayload(SELECTED_BOX_SLOT, ItemStack.EMPTY));
    assertEquals(SELECTED_BOX_SLOT, slot.slot());
    assertTrue(slot.item().isEmpty());
  }
}
