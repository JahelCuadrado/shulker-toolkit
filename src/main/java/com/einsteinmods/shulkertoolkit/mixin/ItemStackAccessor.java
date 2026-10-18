package com.einsteinmods.shulkertoolkit.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Item and components of a stack that just ran out: vanilla {@code ItemStack#typeHolder} and {@code
 * #getComponentsPatch} report air and no components once the count is 0, but the fields still hold
 * what the stack was. Used for auto refill.
 */
@Mixin(ItemStack.class)
public interface ItemStackAccessor {
  /** Null only for the shared {@code ItemStack.EMPTY}. */
  @Accessor("item")
  @Nullable Holder<Item> shulkertoolkit$rawItem();

  @Accessor("components")
  PatchedDataComponentMap shulkertoolkit$rawComponents();
}
