package io.github.minemon.inventory.event;

import io.github.minemon.event.Event;
import lombok.Getter;

@Getter
public class InventorySyncEvent implements Event {
    private final String username;
    private final String serializedInventory;

    public InventorySyncEvent(String username, String serializedInventory) {
        this.username = username;
        this.serializedInventory = serializedInventory;
    }
}
