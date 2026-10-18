package com.einsteinmods.shulkertoolkit.client.storage;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A contents tooltip that is both the {@link TooltipComponent} returned by {@code
 * ItemStack#getTooltipImage} and its own client component (Fabric {@code
 * ClientTooltipComponentCallback} returns it as is), so a cached instance is drawn every frame
 * without allocation by the mod. While the {@code item_storage_tooltip} condition is not met it
 * shows only the "Hold Shift to reveal contents" line, as Easy Shulker Boxes' {@code
 * CollapsibleClientTooltipComponent}. Render thread only.
 */
public abstract class CollapsibleTooltip implements TooltipComponent, ClientTooltipComponent {
  /** Offsets of vanilla {@code ClientBundleTooltip#extractSelectedItemTooltip}. */
  private static final int NAME_CENTER_OFFSET = 12;

  private static final int NAME_ABOVE = 15;

  private Optional<TooltipComponent> image = Optional.empty();
  private @Nullable ItemStack namedStack;
  private List<ClientTooltipComponent> nameLines = List.of();
  private int nameWidth;
  private @Nullable Identifier nameStyle;

  /** This tooltip as {@code getTooltipImage} returns it, without a new {@link Optional}. */
  public final Optional<TooltipComponent> image() {
    if (image.isEmpty()) {
      image = Optional.of(this);
    }
    return image;
  }

  abstract int contentWidth(Font font);

  abstract int contentHeight(Font font);

  abstract void extractContents(Font font, int x, int y, int width, GuiGraphicsExtractor graphics);

  @Override
  public final int getHeight(Font font) {
    return StorageInput.contentsShown() ? contentHeight(font) : StorageInput.revealHintHeight(font);
  }

  @Override
  public final int getWidth(Font font) {
    return StorageInput.contentsShown() ? contentWidth(font) : StorageInput.revealHintWidth(font);
  }

  /** Shown over the cursor stack too, as vanilla bundles ({@code ClientBundleTooltip}). */
  @Override
  public final boolean showTooltipWithItemInHand() {
    return StorageInput.contentsShown();
  }

  @Override
  public final void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
    if (!StorageInput.contentsShown()) {
      Component hint = StorageInput.revealHint(font);
      if (hint != null) {
        graphics.text(font, hint, x, y, -1);
      }
    }
  }

  @Override
  public final void extractImage(
      Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
    if (StorageInput.contentsShown()) {
      extractContents(font, x, y, width, graphics);
    }
  }

  /**
   * The selected item's name in a small tooltip above, as vanilla {@code
   * ClientBundleTooltip#extractSelectedItemTooltip}; the line is rebuilt only when the selected
   * stack changes.
   */
  final void extractSelectedName(
      Font font, GuiGraphicsExtractor graphics, int x, int y, int width, ItemStack selected) {
    if (selected.isEmpty()) {
      return;
    }
    if (selected != namedStack) {
      namedStack = selected;
      Component name = selected.getStyledHoverName();
      nameLines = List.of(ClientTooltipComponent.create(name.getVisualOrderText()));
      nameWidth = font.width(name.getVisualOrderText());
      nameStyle = selected.get(DataComponents.TOOLTIP_STYLE);
    }
    int center = x + width / 2 - NAME_CENTER_OFFSET;
    graphics.tooltip(
        font,
        nameLines,
        center - nameWidth / 2,
        y - NAME_ABOVE,
        DefaultTooltipPositioner.INSTANCE,
        nameStyle,
        false);
  }
}
