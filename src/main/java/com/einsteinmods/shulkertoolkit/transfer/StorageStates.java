package com.einsteinmods.shulkertoolkit.transfer;

import com.einsteinmods.shulkertoolkit.core.ControlScheme;
import com.einsteinmods.shulkertoolkit.core.StorageGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Per player and per side: the slot selected in container items and the player's control scheme, as
 * Easy Shulker Boxes' {@code selected_item} and {@code control_scheme} player attachments. One
 * selection serves every container item (stopping hovering one resets it). The client keeps its own
 * player's, the server one per connected player; both are changed by the same click code, so the
 * prediction never diverges. Each side's state is touched by one thread only.
 */
public final class StorageStates {
  private static final State CLIENT = new State();
  private static final Map<UUID, State> SERVER = new HashMap<>();

  private StorageStates() {}

  /** One player's state on one side. */
  public static final class State {
    private int selectedItem = StorageGrid.NO_ITEM;
    private ControlScheme controlScheme = ControlScheme.DEFAULT;

    public int selectedItem() {
      return selectedItem;
    }

    public void setSelectedItem(int selectedItem) {
      this.selectedItem = selectedItem;
    }

    public ControlScheme controlScheme() {
      return controlScheme;
    }

    public void setControlScheme(ControlScheme controlScheme) {
      this.controlScheme = controlScheme;
    }

    private void reset() {
      selectedItem = StorageGrid.NO_ITEM;
      controlScheme = ControlScheme.DEFAULT;
    }
  }

  /** {@code player}'s state on its side; one map lookup on the server. */
  public static State of(Player player) {
    return player.level().isClientSide()
        ? CLIENT
        : SERVER.computeIfAbsent(player.getUUID(), id -> new State());
  }

  /** Forgets a player who left the server. */
  public static void forget(UUID playerId) {
    SERVER.remove(playerId);
  }

  /** Resets the client player's state on disconnect. */
  public static void clearClient() {
    CLIENT.reset();
  }
}
