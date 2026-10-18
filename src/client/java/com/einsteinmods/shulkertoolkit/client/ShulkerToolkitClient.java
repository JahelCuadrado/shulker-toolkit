package com.einsteinmods.shulkertoolkit.client;

import com.einsteinmods.shulkertoolkit.client.config.ClientSettingsFile;
import com.einsteinmods.shulkertoolkit.client.storage.CollapsibleTooltip;
import com.einsteinmods.shulkertoolkit.client.storage.StorageFillBar;
import com.einsteinmods.shulkertoolkit.client.storage.StorageIndicator;
import com.einsteinmods.shulkertoolkit.client.storage.StorageInput;
import com.einsteinmods.shulkertoolkit.client.storage.StorageMouseActions;
import com.einsteinmods.shulkertoolkit.client.storage.StorageScreenHooks;
import com.einsteinmods.shulkertoolkit.client.storage.StorageTooltips;
import com.einsteinmods.shulkertoolkit.config.JsonConfigFile;
import com.einsteinmods.shulkertoolkit.craft.CraftFromBoxes;
import com.einsteinmods.shulkertoolkit.network.EnderContentsPayload;
import com.einsteinmods.shulkertoolkit.network.EnderSlotPayload;
import com.einsteinmods.shulkertoolkit.network.ServerPolicyPayload;
import com.einsteinmods.shulkertoolkit.transfer.StorageClicks;
import com.einsteinmods.shulkertoolkit.transfer.StorageStates;
import com.einsteinmods.shulkertoolkit.transfer.TransferGate;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Client-only entrypoint. */
public final class ShulkerToolkitClient implements ClientModInitializer {
  private static ClientShulkerState state;
  private static ShulkerToolkitKeyMappings keyMappings;

  /** Shared with the settings screen (Mod Menu entrypoint has no constructor arguments). */
  public static ClientShulkerState state() {
    return state;
  }

  /** Shared with the settings screen, which rebinds these mappings. */
  public static ShulkerToolkitKeyMappings keyMappings() {
    return keyMappings;
  }

  @Override
  public void onInitializeClient() {
    state =
        new ClientShulkerState(
            new JsonConfigFile<>(
                FabricLoader.getInstance().getConfigDir().resolve(ClientSettingsFile.FILE_NAME),
                ClientSettingsFile.class,
                ClientSettingsFile.DEFAULTS));
    keyMappings = new ShulkerToolkitKeyMappings(state);
    StorageInput.install(state, keyMappings.toggleContents(), keyMappings.toggleCursorTooltip());
    TransferGate.installClientCheck(
        state::transferAllowed,
        state::enderPreviewAllowed,
        () -> state.preferences().bundleInteractions());
    CraftFromBoxes.installClientCheck(state::craftScope);
    StorageClicks.installClientSoundMute(StorageMouseActions::draggingOnOpenScreen);
    StorageScreenHooks.register();

    // Contents tooltips are their own client components: no allocation when vanilla converts them.
    ClientTooltipComponentCallback.EVENT.register(
        data -> data instanceof CollapsibleTooltip tooltip ? tooltip : null);

    ClientPlayNetworking.registerGlobalReceiver(
        ServerPolicyPayload.TYPE, (payload, context) -> state.onServerPolicy(payload.policy()));
    ClientPlayNetworking.registerGlobalReceiver(
        EnderContentsPayload.TYPE,
        (payload, context) -> setEnderContents(context.player(), payload.items()));
    ClientPlayNetworking.registerGlobalReceiver(
        EnderSlotPayload.TYPE,
        (payload, context) -> {
          SimpleContainer enderChest = context.player().getEnderChestInventory();
          if (payload.slot() >= 0 && payload.slot() < enderChest.getContainerSize()) {
            enderChest.setItem(payload.slot(), payload.item());
            markInventoryChanged(context.player());
          }
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          state.onDisconnect();
          StorageStates.clearClient();
          StorageScreenHooks.reset();
          StorageTooltips.clear();
          StorageFillBar.clear();
          StorageIndicator.clear();
        });

    ClientTickEvents.END_CLIENT_TICK.register(keyMappings::onClientTick);
  }

  /** As Easy Shulker Boxes' {@code EnderChestSyncHandler#setEnderChestContent}. */
  private static void setEnderContents(Player player, List<ItemStack> items) {
    SimpleContainer enderChest = player.getEnderChestInventory();
    int size = Math.min(items.size(), enderChest.getContainerSize());
    for (int slot = 0; slot < size; slot++) {
      enderChest.setItem(slot, items.get(slot));
    }
    markInventoryChanged(player);
  }

  /**
   * An open recipe book recounts what is craftable on the next tick after the inventory changed
   * ({@code RecipeBookComponent#tick} compares {@code Inventory#getTimesChanged}); ender chest
   * contents count when crafting from boxes reaches the ender chest. One increment per payload.
   */
  private static void markInventoryChanged(Player player) {
    player.getInventory().setChanged();
  }
}
