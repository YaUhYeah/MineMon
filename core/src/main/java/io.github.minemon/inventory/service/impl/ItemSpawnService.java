package io.github.minemon.inventory.service.impl;

import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ItemSpawnService {

    private final WorldService worldService;
    private final WorldObjectManager worldObjectManager;
    private final MultiplayerClient multiplayerClient;
    private final Map<String, Long> itemSpawnTimes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final float POKEBALL_SPAWN_CHANCE = 0.02f; // 2% chance per eligible tile
    private static final long ITEM_DESPAWN_TIME = 300000; // 5 minutes
    private static final int MIN_DISTANCE_BETWEEN_ITEMS = 5; // tiles

    @Autowired
    public ItemSpawnService(WorldService worldService,
                            WorldObjectManager worldObjectManager,
                            MultiplayerClient multiplayerClient) {
        this.worldService = worldService;
        this.worldObjectManager = worldObjectManager;
        this.multiplayerClient = multiplayerClient;
    }

    public void spawnItemsInChunk(int chunkX, int chunkY, int[][] tiles, Biome biome) {
        if (!biome.getAllowedTileTypes().contains(0)) {
            return;
        }

        int CHUNK_SIZE = 16;
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                if (random.nextFloat() < POKEBALL_SPAWN_CHANCE) {
                    int worldX = chunkX * CHUNK_SIZE + x;
                    int worldY = chunkY * CHUNK_SIZE + y;

                    if (canSpawnItemAt(worldX, worldY)) {
                        spawnPokeball(worldX, worldY);
                    }
                }
            }
        }
    }

    private boolean canSpawnItemAt(int tileX, int tileY) {
        // Check for nearby items
        List<WorldObject> nearbyObjects = worldService.getVisibleObjects(
            new Rectangle(
                (tileX - MIN_DISTANCE_BETWEEN_ITEMS) * 32,
                (tileY - MIN_DISTANCE_BETWEEN_ITEMS) * 32,
                (MIN_DISTANCE_BETWEEN_ITEMS * 2 + 1) * 32,
                (MIN_DISTANCE_BETWEEN_ITEMS * 2 + 1) * 32
            )
        );

        return nearbyObjects.stream()
            .noneMatch(obj -> obj.getType() == ObjectType.POKEBALL);
    }

    private void spawnPokeball(int tileX, int tileY) {
        WorldObject pokeball = new WorldObject(tileX, tileY, ObjectType.POKEBALL, true);
        worldObjectManager.addObject(pokeball);
        itemSpawnTimes.put(pokeball.getId(), System.currentTimeMillis());

        log.debug("Spawned pokeball at ({}, {})", tileX, tileY);
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void cleanupExpiredItems() {
        long now = System.currentTimeMillis();

        Set<String> expiredItems = new HashSet<>();
        itemSpawnTimes.forEach((id, spawnTime) -> {
            if (now - spawnTime > ITEM_DESPAWN_TIME) {
                expiredItems.add(id);
            }
        });

        for (String id : expiredItems) {
            worldObjectManager.removeObject(id);
            itemSpawnTimes.remove(id);
            log.debug("Removed expired item: {}", id);
        }
    }

    public boolean validatePickup(String itemId, String username) {
        // Check if item exists and hasn't expired
        Long spawnTime = itemSpawnTimes.get(itemId);
        if (spawnTime == null) {
            return false;
        }

        // Multiplayer validation if needed
        if (worldService.isMultiplayerMode() && multiplayerClient.isConnected()) {
            // Additional multiplayer validation could go here
            return true;
        }

        return true;
    }

    public void removeItem(String itemId) {
        worldObjectManager.removeObject(itemId);
        itemSpawnTimes.remove(itemId);
    }
}
