package com.einsteinmods.shulkertoolkit.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The raw counts behind vanilla {@code StackedItemContents}: stored items are counted by item and
 * amount ({@code StackedContents#account}) without creating a stack for each.
 */
@Mixin(StackedItemContents.class)
public interface StackedItemContentsAccessor {
  @Accessor("raw")
  StackedContents<Holder<Item>> shulkertoolkit$raw();
}
