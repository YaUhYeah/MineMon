package io.github.minemon.inventory.event;

import io.github.minemon.event.Event;
import io.github.minemon.inventory.model.InventorySlot;
import lombok.Getter;

@Getter
public class ItemUsedEvent implements Event {
    private final String username;
    private final String itemId;
    private final int slotIndex;
    private final InventorySlot slot;

    public ItemUsedEvent(String username, String itemId, int slotIndex, InventorySlot slot) {
        this.username = username;
        this.itemId = itemId;
        this.slotIndex = slotIndex;
        this.slot = slot;
    }
}
