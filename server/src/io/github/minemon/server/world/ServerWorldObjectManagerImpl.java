package io.github.minemon.server.world;

import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldObjectManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
@Service
@Primary
@Slf4j
public class ServerWorldObjectManagerImpl implements WorldObjectManager {

    private final Map<String, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        log.info("ServerWorldObjectManagerImpl initialized (no-op).");
    }

    @Override
    public List<WorldObject> getObjectsForChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return objectsByChunk.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public List<WorldObject> generateObjectsForChunk(int chunkX, int chunkY, int[][] tiles, Biome biome, long seed) {
        List<WorldObject> objects = new CopyOnWriteArrayList<>();
        if (biome == null || tiles == null) {
            log.warn("Cannot generate objects - missing biome or tiles for chunk {},{}", chunkX, chunkY);
            return objects;
        }

        // Generate a chunk-specific seed
        long chunkSeed = seed + chunkX * 341873128712L + chunkY * 132897987541L;
        Random random = new Random(chunkSeed);

        // Get chunk size from tiles array
        int chunkSize = tiles.length;

        // Calculate object placement
        for (String objTypeName : biome.getSpawnableObjects()) {
            ObjectType type;
            try {
                type = ObjectType.valueOf(objTypeName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid object type {} in biome {}", objTypeName, biome.getName());
                continue;
            }

            double spawnChance = biome.getSpawnChanceForObject(type);
            int maxAttempts = (int)(spawnChance * (chunkSize * chunkSize));

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int localX = random.nextInt(chunkSize);
                int localY = random.nextInt(chunkSize);

                // Check if tile type is valid for this object
                int tileId = tiles[localX][localY];
                if (!biome.getAllowedTileTypes().contains(tileId)) {
                    continue;
                }

                // Convert to world coordinates
                int worldX = chunkX * chunkSize + localX;
                int worldY = chunkY * chunkSize + localY;

                // Check spacing requirements
                if (canPlaceObject(objects, worldX, worldY, type)) {
                    WorldObject obj = new WorldObject(worldX, worldY, type, type.isCollidable());
                    objects.add(obj);
                    log.debug("Added {} at {},{} in chunk {},{}",
                        type, worldX, worldY, chunkX, chunkY);
                }
            }
        }

        log.info("Generated {} objects for chunk {},{}", objects.size(), chunkX, chunkY);
        return objects;
    }

    private boolean canPlaceObject(List<WorldObject> existingObjects, int x, int y, ObjectType type) {
        // Define minimum spacing based on object type
        int minSpacing = type.name().contains("TREE") ? 3 : 2;

        for (WorldObject existing : existingObjects) {
            int dx = existing.getTileX() - x;
            int dy = existing.getTileY() - y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < minSpacing) {
                return false;
            }
        }
        return true;
    }

    private boolean noTreeNearby(List<WorldObject> objects, int x, int y, int minDistance) {
        for (WorldObject obj : objects) {
            if (obj.getType().name().startsWith("TREE")) {
                int dx = obj.getTileX() - x;
                int dy = obj.getTileY() - y;
                if (dx * dx + dy * dy < minDistance * minDistance) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void loadObjectsForChunk(int chunkX, int chunkY, List<WorldObject> objects) {
        String key = chunkX + "," + chunkY;
        objectsByChunk.put(key, objects);
    }

    @Override
    public void addObject(WorldObject object) {
        int chunkX = object.getTileX() / 16;
        int chunkY = object.getTileY() / 16;
        String key = chunkX + "," + chunkY;
        objectsByChunk.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(object);
    }

    @Override
    public void removeObject(String objectId) {
        for (List<WorldObject> objs : objectsByChunk.values()) {
            objs.removeIf(o -> o.getId().equals(objectId));
        }
    }
}
