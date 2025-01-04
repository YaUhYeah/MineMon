package io.github.minemon.inventory.model;

import lombok.Data;
import java.util.UUID;

@Data
public class InventorySlot {
    private String itemId;
    private int count;
    private int durability;
    private int maxDurability;
    private UUID uuid = UUID.randomUUID();

    public InventorySlot copy() {
        InventorySlot copy = new InventorySlot();
        copy.itemId = this.itemId;
        copy.count = this.count;
        copy.durability = this.durability;
        copy.maxDurability = this.maxDurability;
        copy.uuid = UUID.randomUUID(); // Generate new UUID for copy
        return copy;
    }

    public void copyFrom(InventorySlot other) {
        if (other == null) {
            this.itemId = null;
            this.count = 0;
            this.durability = 0;
            this.maxDurability = 0;
            this.uuid = UUID.randomUUID();
            return;
        }

        this.itemId = other.itemId;
        this.count = other.count;
        this.durability = other.durability;
        this.maxDurability = other.maxDurability;
        this.uuid = UUID.randomUUID(); // Generate new UUID when copying
    }

    public void clear() {
        this.itemId = null;
        this.count = 0;
        this.durability = 0;
        this.maxDurability = 0;
        this.uuid = UUID.randomUUID();
    }
}
