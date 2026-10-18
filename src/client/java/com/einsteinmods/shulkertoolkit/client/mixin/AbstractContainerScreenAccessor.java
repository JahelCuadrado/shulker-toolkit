package com.einsteinmods.shulkertoolkit.client.mixin;

import java.util.List;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * What the container item mouse actions need from a container screen (Easy Shulker Boxes widens the
 * same members with a class tweaker).
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
  @Accessor("itemSlotMouseActions")
  List<ItemSlotMouseAction> shulkertoolkit$itemSlotMouseActions();

  @Accessor("hoveredSlot")
  @Nullable Slot shulkertoolkit$hoveredSlot();

  @Accessor("leftPos")
  int shulkertoolkit$leftPos();

  @Accessor("topPos")
  int shulkertoolkit$topPos();

  @Accessor("imageWidth")
  int shulkertoolkit$imageWidth();

  @Accessor("imageHeight")
  int shulkertoolkit$imageHeight();

  @Invoker("getHoveredSlot")
  @Nullable Slot shulkertoolkit$slotAt(double x, double y);

  @Invoker("isHovering")
  boolean shulkertoolkit$isHovering(Slot slot, double x, double y);

  @Invoker("slotClicked")
  void shulkertoolkit$slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput input);

  @Invoker("getTooltipFromContainerItem")
  List<Component> shulkertoolkit$tooltipLines(ItemStack stack);
}
