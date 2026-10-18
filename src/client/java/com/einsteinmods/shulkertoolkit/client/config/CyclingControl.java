package com.einsteinmods.shulkertoolkit.client.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Row that cycles through fixed values, as a YACL enum controller: click, Enter or Space selects
 * the next value; the arrow keys move backwards and forwards. The description follows the value.
 */
final class CyclingControl<T> extends OptionControl<T> {
  private final List<T> values;
  private final Function<T, Component> valueName;
  private final Function<T, Component> valueDescription;

  CyclingControl(
      Font font,
      Component name,
      List<T> values,
      Function<T, Component> valueName,
      Function<T, Component> valueDescription,
      T initial,
      T defaultValue,
      Consumer<T> onPlayerChange) {
    super(font, name, initial, defaultValue, onPlayerChange);
    this.values = List.copyOf(values);
    this.valueName = valueName;
    this.valueDescription = valueDescription;
  }

  @Override
  public Component description() {
    return valueDescription.apply(get());
  }

  @Override
  protected Component valueText() {
    return valueName.apply(get());
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    cycle(1);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.isSelection() || event.isRight()) {
      cycle(1);
      return true;
    }
    if (event.isLeft()) {
      cycle(-1);
      return true;
    }
    return false;
  }

  private void cycle(int step) {
    int index = values.indexOf(get());
    setByPlayer(values.get(Math.floorMod(index + step, values.size())));
  }
}
