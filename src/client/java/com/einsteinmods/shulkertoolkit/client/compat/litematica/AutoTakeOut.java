package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import com.einsteinmods.shulkertoolkit.client.ClientShulkerState;
import com.einsteinmods.shulkertoolkit.client.ShulkerToolkitClient;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Auto Take Out: a right-click (vanilla {@code Minecraft#startUseItem}) on the spot of a schematic
 * block that is not placed yet, without Easy Place, takes the block the schematic needs there out
 * of a shulker box. The spot is where the click would place a block ({@code
 * BlockPlaceContext#getClickedPos}); the block is the one Litematica's material list counts ({@code
 * MaterialCache#getRequiredBuildItemForState}).
 *
 * <p>Runs once per use-key press (vanilla repeats it every 4 ticks while held), cheapest checks
 * first; a hand holding something other than a block or nothing returns at once. When a block is
 * held, the click is cancelled so the wrong block is not placed; the next click (or the next repeat
 * while held) places the one taken out. Linked against Litematica's API: if a newer version changes
 * it, the feature turns off with one warning instead of crashing.
 */
public final class AutoTakeOut {
  private static final String FEATURE = "Auto Take Out";

  private static boolean unsupported;

  private AutoTakeOut() {}

  /**
   * @return true to skip this use of the key (a block is held and its request was sent)
   */
  public static boolean onUseKey() {
    ClientShulkerState state = ShulkerToolkitClient.state();
    if (unsupported || !state.preferences().litematicaAutoTakeOut() || !state.pickAllowed()) {
      return false;
    }
    try {
      return requestForSchematicSpot(Minecraft.getInstance());
    } catch (LinkageError error) {
      unsupported = true;
      LitematicaCompat.warnUnsupported(FEATURE, error.toString());
      return false;
    }
  }

  private static boolean requestForSchematicSpot(Minecraft minecraft) {
    LocalPlayer player = minecraft.player;
    ClientLevel level = minecraft.level;
    if (player == null
        || level == null
        || player.hasInfiniteMaterials()
        || !(minecraft.hitResult instanceof BlockHitResult hit)
        || hit.getType() != HitResult.Type.BLOCK) {
      return false;
    }
    ItemStack held = player.getMainHandItem();
    boolean holdsBlock = held.getItem() instanceof BlockItem;
    if ((!holdsBlock && !held.isEmpty()) || easyPlaceActive()) {
      return false;
    }
    // Opening a container keeps working: vanilla uses it before placing unless sneaking.
    if (!player.isSecondaryUseActive()
        && level.getBlockState(hit.getBlockPos()).getMenuProvider(level, hit.getBlockPos())
            != null) {
      return false;
    }
    WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
    if (schematic == null) {
      return false;
    }
    BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit);
    BlockPos spot = context.getClickedPos();
    if (!context.canPlace() || !DataManager.getRenderLayerRange().isPositionWithinRange(spot)) {
      return false;
    }
    BlockState needed = schematic.getBlockState(spot);
    if (needed.isAir() || needed == level.getBlockState(spot)) {
      return false;
    }
    ItemStack required =
        MaterialCache.getInstance().getRequiredBuildItemForState(needed, schematic, spot);
    if (required.isEmpty()
        || ItemStack.isSameItem(held, required)
        || ItemStack.isSameItem(player.getOffhandItem(), required)) {
      return false;
    }
    return LitematicaTakeOut.requester().request(player, required) && holdsBlock;
  }

  /** Litematica's own rule: Easy Place mode on and its use key ({@code easyPlaceUseKey}) held. */
  private static boolean easyPlaceActive() {
    return Configs.Generic.EASY_PLACE_MODE.getBooleanValue()
        && Hotkeys.EASY_PLACE_ACTIVATION.getKeybind().isKeybindHeld();
  }
}
