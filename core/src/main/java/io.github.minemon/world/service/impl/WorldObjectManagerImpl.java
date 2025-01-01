package io.github.minemon.world.service.impl;

import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class WorldObjectManagerImpl implements WorldObjectManager {

    private static final int CHUNK_SIZE = 16;
    private final boolean isServer;

    private final Map<String, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();

    @Autowired
    @Lazy  // Add this to break the cycle
    private WorldService worldService;
    @Getter
    @Setter
    private boolean singlePlayer = true;

    public WorldObjectManagerImpl() {
        this.isServer = "server".equals(System.getProperty("spring.profiles.active"));
        log.info("WorldObjectManager initialized with isServer={}", isServer);
    }

    @Override
    public void initialize() {

        log.info("WorldObjectManagerImpl initialized. singlePlayer={}", singlePlayer);
    }

    @Override
    public void loadObjectsForChunk(int chunkX, int chunkY, List<WorldObject> objects) {
        if (!isServer && worldService.isMultiplayerMode()) {
            log.debug("Client skipping local object loading in multiplayer mode");
            return;
        }
        if (objects == null) {
            objects = Collections.emptyList();
        }
        String key = chunkX + "," + chunkY;
        objectsByChunk.put(key, objects);
        log.debug("Loaded {} objects for chunk {}", objects.size(), key);
    }

    @Override
    public List<WorldObject> generateObjectsForChunk(int chunkX, int chunkY, int[][] tiles, Biome biome, long seed) {

        List<WorldObject> objects = new CopyOnWriteArrayList<>();
        if (biome == null) {
            String key = chunkX + "," + chunkY;
            objectsByChunk.put(key, objects);
            return objects;
        }

        long combinedSeed = seed * 31 + chunkX * 341873128712L + chunkY * 132897987541L;
        Random random = new Random(combinedSeed);
        int chunkSize = tiles.length;

        for (String objName : biome.getSpawnableObjects()) {
            ObjectType type;
            try {
                type = ObjectType.valueOf(objName);
            } catch (Exception e) {
                log.warn("Unknown object type: '{}' in biome '{}'", objName, biome.getType());
                continue;
            }

            double chance = biome.getSpawnChanceForObject(type);
            int attempts = (int) (chance * (chunkSize * chunkSize));

            for (int i = 0; i < attempts; i++) {
                int lx = random.nextInt(chunkSize);
                int ly = random.nextInt(chunkSize);

                int tileType = tiles[lx][ly];
                if (!biome.getAllowedTileTypes().contains(tileType)) {
                    continue;
                }

                if (canPlaceObject(objects, chunkX, chunkY, lx, ly, type)) {
                    int worldX = chunkX * CHUNK_SIZE + lx;
                    int worldY = chunkY * CHUNK_SIZE + ly;

                    WorldObject obj = new WorldObject(worldX, worldY, type, type.isCollidable());
                    objects.add(obj);
                }
            }
        }

        String key = chunkX + "," + chunkY;
        objectsByChunk.put(key, objects);
        log.info("Generated {} objects for chunk {},{} using biome '{}'", objects.size(), chunkX, chunkY, biome.getType());
        return objects;
    }

    private boolean canPlaceObject(List<WorldObject> currentObjects, int chunkX, int chunkY, int lx, int ly, ObjectType newType) {
        int worldX = chunkX * CHUNK_SIZE + lx;
        int worldY = chunkY * CHUNK_SIZE + ly;

        int minDistance = 3;
        if (newType.name().contains("TREE")) {
            for (WorldObject obj : currentObjects) {
                if (obj.getType().name().contains("TREE")) {
                    int dx = obj.getTileX() - worldX;
                    int dy = obj.getTileY() - worldY;
                    if (dx * dx + dy * dy < minDistance * minDistance) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<WorldObject> getObjectsForChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        return objectsByChunk.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public void addObject(WorldObject object) {
        int chunkX = object.getTileX() / CHUNK_SIZE;
        int chunkY = object.getTileY() / CHUNK_SIZE;
        String key = chunkX + "," + chunkY;
        objectsByChunk.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(object);
        log.debug("Added object {} to chunk {}", object.getId(), key);
    }

    @Override
    public void removeObject(String objectId) {
        for (Map.Entry<String, List<WorldObject>> entry : objectsByChunk.entrySet()) {
            List<WorldObject> objs = entry.getValue();
            boolean removed = objs.removeIf(o -> o.getId().equals(objectId));
            if (removed) {
                log.debug("Removed object {} from chunk {}", objectId, entry.getKey());
                break;
            }
        }
    }
}
