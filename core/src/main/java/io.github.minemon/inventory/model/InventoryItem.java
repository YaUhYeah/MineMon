package io.github.minemon.inventory.model;

import lombok.Data;

@Data
public class InventoryItem {
    private String itemId;
    private String name;
    private int maxStackSize;
    private boolean isStackable;
    private int maxDurability;

    public InventoryItem(String itemId, String name) {
        this.itemId = itemId;
        this.name = name;
    }

    public InventoryItem setTool(int durability, int maxDurability) {
        this.isStackable = false;
        this.maxDurability = maxDurability;
        this.maxStackSize = 1;
        return this;
    }
}

