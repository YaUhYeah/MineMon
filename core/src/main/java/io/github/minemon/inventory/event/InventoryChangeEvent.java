package io.github.minemon.inventory.event;

import io.github.minemon.event.Event;
import io.github.minemon.inventory.model.InventorySlot;
import lombok.Getter;

@Getter
public class InventoryChangeEvent implements Event {
    private final String username;
    private final int slotIndex;
    private final String itemId;
    private final int count;
    private final ChangeType type;

    public InventoryChangeEvent(String username, int slotIndex, String itemId, int count, ChangeType type) {
        this.username = username;
        this.slotIndex = slotIndex;
        this.itemId = itemId;
        this.count = count;
        this.type = type;
    }

    public enum ChangeType {
        ITEM_ADDED,
        ITEM_REMOVED,
        ITEM_MOVED,
        SLOT_CLEARED,
        INVENTORY_LOADED
    }
}

