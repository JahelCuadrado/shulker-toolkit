package com.einsteinmods.shulkertoolkit;

import com.einsteinmods.shulkertoolkit.config.JsonConfigFile;
import com.einsteinmods.shulkertoolkit.config.ServerPolicyFile;
import com.einsteinmods.shulkertoolkit.core.ServerPolicy;
import com.einsteinmods.shulkertoolkit.network.ShulkerToolkitNetworking;
import com.einsteinmods.shulkertoolkit.server.ServerPlayerSessions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Common (client + server) entrypoint. */
public final class ShulkerToolkit implements ModInitializer {
  public static final String MOD_ID = "shulkertoolkit";

  @Override
  public void onInitialize() {
    ShulkerToolkitNetworking.register();
    ServerLifecycleEvents.SERVER_STARTING.register(
        server -> ServerPlayerSessions.start(loadFilePolicy()));
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerPlayerSessions.stop());
  }

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }

  /**
   * Every server (dedicated or integrated) reads its policy file; the singleplayer/LAN host is
   * exempted per player in {@link ServerPlayerSessions#join}.
   */
  private static ServerPolicy loadFilePolicy() {
    return new JsonConfigFile<>(
            FabricLoader.getInstance().getConfigDir().resolve(ServerPolicyFile.FILE_NAME),
            ServerPolicyFile.class,
            ServerPolicyFile.DEFAULTS)
        .load()
        .toPolicy();
  }
}
