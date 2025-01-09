package io.github.minemon.inventory.service;

import io.github.minemon.inventory.model.InventoryItem;
import io.github.minemon.inventory.model.InventorySlot;

import java.util.List;
import java.util.Map;

public interface InventoryService {
    Map<String, InventoryItem> getItemRegistry();
    void addItem(String itemId, int count);
    void registerItem(InventoryItem item);
    boolean removeItem(String itemId, int count);
    List<InventorySlot> getInventory();
    String serializeInventory();
    void deserializeInventory(String data);
}
