package io.github.minemon.inventory.service.impl;

import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
public class ItemPickupHandler {

    private final Random random = new Random();
    private static final float PICKUP_RANGE = 48f; // 1.5 tiles

    // Define item pools with weights
    private static final Map<String, Integer> POKEBALL_ITEMS = new HashMap<>() {{
        put("pokeball", 70);    // 70% chance
        put("potion", 20);      // 20% chance
        put("stick", 10);       // 10% chance
    }};

    @Autowired
    private ItemSpawnService itemSpawnService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private MultiplayerClient multiplayerClient;

    @Autowired
    private WorldService worldService;

    public boolean attemptPickup(PlayerData playerData, WorldObject object) {
        // Validate distance
        float dx = (playerData.getX() * 32) - (object.getTileX() * 32);
        float dy = (playerData.getY() * 32) - (object.getTileY() * 32);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > PICKUP_RANGE) {
            return false;
        }

        // Validate pickup eligibility
        if (!itemSpawnService.validatePickup(object.getId(), playerData.getUsername())) {
            return false;
        }

        // Handle pickup based on item type
        switch (object.getType()) {
            case POKEBALL:
                handlePokeballPickup(playerData.getUsername());
                break;
            // Add other item types here
            default:
                return false;
        }

        // Remove the object from world
        itemSpawnService.removeItem(object.getId());

        // Notify other players in multiplayer
        if (worldService.isMultiplayerMode() && multiplayerClient.isConnected()) {
            WorldObjectUpdate update = new WorldObjectUpdate();
            update.setObjectId(object.getId());
            update.setRemoved(true);
            multiplayerClient.sendMessage(update);
        }

        return true;
    }

    private void handlePokeballPickup(String username) {
        // Select random item based on weights
        int total = POKEBALL_ITEMS.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);

        String selectedItem = null;
        int currentTotal = 0;

        for (Map.Entry<String, Integer> entry : POKEBALL_ITEMS.entrySet()) {
            currentTotal += entry.getValue();
            if (roll < currentTotal) {
                selectedItem = entry.getKey();
                break;
            }
        }

        if (selectedItem != null) {
            inventoryService.addItem(selectedItem, 1);
            log.debug("Player {} received {} from pokeball", username, selectedItem);
        }
    }
}
