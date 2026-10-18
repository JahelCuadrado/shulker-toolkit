package com.einsteinmods.shulkertoolkit.mixin;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reads box slots without creating an {@code ItemStack} per slot (vanilla only offers {@code
 * copyInto} and streams) and builds new contents from slot templates (vanilla {@code fromItems}
 * needs a stack per slot).
 */
@Mixin(ItemContainerContents.class)
public interface ItemContainerContentsAccessor {
  @Accessor("items")
  List<Optional<ItemStackTemplate>> shulkertoolkit$slots();

  /** The private constructor; the list must not end in empty slots (as {@code fromItems}). */
  @Invoker("<init>")
  static ItemContainerContents shulkertoolkit$create(List<Optional<ItemStackTemplate>> slots) {
    throw new AssertionError("mixin not applied");
  }
}
