package io.github.minemon.inventory.config;

import io.github.minemon.inventory.model.InventoryItem;
import lombok.Getter;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class ItemRegistry {
    @Getter
    private static final List<InventoryItem> defaultItems = new ArrayList<>();

    static {
        
        defaultItems.add(new InventoryItem("wooden_axe", "Wooden Axe").setTool(100,100));

        
        defaultItems.add(new InventoryItem("stick", "Stick"));
        defaultItems.add(new InventoryItem("pokeball", "Poké Ball"));
        defaultItems.add(new InventoryItem("potion", "Potion"));

        
        defaultItems.add(new InventoryItem("dirt", "Dirt"));
        defaultItems.add(new InventoryItem("grass", "Grass"));
        defaultItems.add(new InventoryItem("stone", "Stone"));
        defaultItems.add(new InventoryItem("wood", "Wood"));
    }
}
