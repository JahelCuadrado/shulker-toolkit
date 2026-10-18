package com.einsteinmods.shulkertoolkit.mixin;

import com.mojang.serialization.DataResult;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Weight of one item in a bundle: private vanilla {@code BundleContents#getWeight}. */
@Mixin(BundleContents.class)
public interface BundleContentsAccessor {
  @Invoker("getWeight")
  static DataResult<Fraction> shulkertoolkit$weightOf(ItemInstance item) {
    throw new AssertionError("mixin not applied");
  }
}
