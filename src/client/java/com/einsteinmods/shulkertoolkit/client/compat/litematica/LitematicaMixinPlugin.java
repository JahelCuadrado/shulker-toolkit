package com.einsteinmods.shulkertoolkit.client.compat.litematica;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Applies each mixin of {@code shulkertoolkit.litematica.mixins.json} only when its mod is
 * installed and, for hooks into Litematica or the printer, only when the target method still has
 * the shape the hook relies on (checked on the class bytes when Mixin prepares, at startup, without
 * loading the class). A hook that does not fit logs one warning and stays off; the mixins also use
 * {@code require = 0} and the config is not required, so version drift never crashes the game.
 */
public final class LitematicaMixinPlugin implements IMixinConfigPlugin {
  private static final String MIXIN_PACKAGE =
      "com.einsteinmods.shulkertoolkit.client.compat.litematica.mixin.";

  private static final String CONFIG_BOOLEAN = "fi/dy/masa/malilib/config/options/ConfigBoolean";
  private static final String GET_BOOLEAN_VALUE = "getBooleanValue";
  private static final String BOOLEAN_GETTER = "()Z";

  /**
   * One method a hook injects into and the single call inside it the injection relies on (or, for a
   * hook at the head, a call that shows the method still works the way the hook assumes).
   */
  private record Shape(
      String method,
      String methodDescriptor,
      String callOwner,
      String callName,
      String callDescriptor) {
    /** Null when {@code target} has the method with exactly one such call, otherwise why not. */
    @Nullable String mismatch(ClassNode target) {
      for (MethodNode candidate : target.methods) {
        if (candidate.name.equals(method) && candidate.desc.equals(methodDescriptor)) {
          int calls = countCalls(candidate);
          return calls == 1 ? null : method + " calls " + callName + " " + calls + " times";
        }
      }
      return "no " + method + methodDescriptor;
    }

    private int countCalls(MethodNode candidate) {
      int calls = 0;
      for (AbstractInsnNode instruction : candidate.instructions) {
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals(callOwner)
            && call.name.equals(callName)
            && call.desc.equals(callDescriptor)) {
          calls++;
        }
      }
      return calls;
    }
  }

  /** One hook: the mod it needs and, for mod targets, the shape of each method it injects into. */
  private enum Hook {
    EASY_PLACE(
        "InventoryUtilsMixin",
        LitematicaCompat.LITEMATICA_MOD_ID,
        "Easy Place",
        new Shape(
            "schematicWorldPickBlock",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V",
            CONFIG_BOOLEAN,
            GET_BOOLEAN_VALUE,
            BOOLEAN_GETTER)),
    PRINTER(
        "GuideMixin",
        LitematicaCompat.PRINTER_MOD_ID,
        "Litematica Printer",
        new Shape(
            "getRequiredItem",
            "(Lnet/minecraft/client/player/LocalPlayer;)Ljava/util/Optional;",
            "me/aleksilassila/litematica/printer/guides/Guide",
            "getSlotWithItem",
            "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;)I")),
    MATERIAL_LIST(
        "MaterialListUtilsMixin",
        LitematicaCompat.LITEMATICA_MOD_ID,
        "Material list count",
        // The count keys items as Litematica does: one ItemType per loose stack.
        new Shape(
            "getInventoryItemCounts",
            "(Lnet/minecraft/world/Container;)Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;",
            "fi/dy/masa/malilib/util/data/ItemType",
            "<init>",
            "(Lnet/minecraft/world/item/ItemStack;ZZ)V"),
        new Shape(
            "updateAvailableCounts",
            "(Ljava/util/List;Lnet/minecraft/world/entity/player/Player;)V",
            CONFIG_BOOLEAN,
            GET_BOOLEAN_VALUE,
            BOOLEAN_GETTER),
        new Shape(
            "createMaterialListFromItems",
            "(Ljava/util/Map;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;",
            CONFIG_BOOLEAN,
            GET_BOOLEAN_VALUE,
            BOOLEAN_GETTER)),
    /** Vanilla target (pinned Minecraft version): only needs Litematica installed. */
    AUTO_TAKE_OUT("MinecraftMixin", LitematicaCompat.LITEMATICA_MOD_ID, "Auto Take Out");

    private final String mixinName;
    private final String modId;
    private final String feature;
    private final List<Shape> shapes;

    Hook(String mixinName, String modId, String feature, Shape... shapes) {
      this.mixinName = mixinName;
      this.modId = modId;
      this.feature = feature;
      this.shapes = List.of(shapes);
    }

    static @Nullable Hook forMixin(String mixinClassName) {
      for (Hook hook : values()) {
        if (mixinClassName.equals(MIXIN_PACKAGE + hook.mixinName)) {
          return hook;
        }
      }
      return null;
    }

    /** Null when every method fits, otherwise why not. */
    @Nullable String mismatch(ClassNode target) {
      for (Shape shape : shapes) {
        String mismatch = shape.mismatch(target);
        if (mismatch != null) {
          return mismatch;
        }
      }
      return null;
    }
  }

  @Override
  public void onLoad(String mixinPackage) {
    // Nothing to set up: every decision is per mixin in shouldApplyMixin.
  }

  @Override
  public @Nullable String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    Hook hook = Hook.forMixin(mixinClassName);
    if (hook == null || !FabricLoader.getInstance().isModLoaded(hook.modId)) {
      return false;
    }
    if (hook.shapes.isEmpty()) {
      return true;
    }
    String mismatch;
    try {
      mismatch =
          hook.mismatch(
              MixinService.getService().getBytecodeProvider().getClassNode(targetClassName));
    } catch (ClassNotFoundException | IOException exception) {
      mismatch = "no " + targetClassName;
    }
    if (mismatch != null) {
      LitematicaCompat.warnUnsupported(hook.feature, mismatch);
      return false;
    }
    return true;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    // Targets are fixed by the mixin annotations.
  }

  @Override
  public @Nullable List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    // No bytecode changes besides the mixins themselves.
  }

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    // No bytecode changes besides the mixins themselves.
  }
}
