package io.github.minemon.world.service.impl;

import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.inventory.service.impl.ItemSpawnService;
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
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class WorldObjectManagerImpl implements WorldObjectManager {
    private static final int CHUNK_SIZE = 16;
    private static final int TILE_SIZE = 32;
    private static final float MIN_OBJECT_SPACING = 2.0f;
    private static final float TREE_SPACING = 3.0f;
    private final boolean isServer;
    private final Map<String, List<WorldObject>> objectsByChunk = new ConcurrentHashMap<>();
    private ItemSpawnService itemSpawnService;
    private WorldService worldService;

    public void setItemSpawnService(ItemSpawnService itemSpawnService) {
        this.itemSpawnService = itemSpawnService;
    }

    public void setWorldService(WorldService worldService) {
        this.worldService = worldService;
    }
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

    private boolean canPlaceObject(List<WorldObject> existingObjects, int x, int y, ObjectType type) {
        float minSpacing = type.name().contains("TREE") ? 4.0f : 2.0f;
        Rectangle newObjBounds = new Rectangle(
            x * TILE_SIZE - (type.getWidthInTiles() * TILE_SIZE / 2f),
            y * TILE_SIZE,
            type.getWidthInTiles() * TILE_SIZE,
            type.getHeightInTiles() * TILE_SIZE
        );


        newObjBounds.x -= TILE_SIZE * minSpacing;
        newObjBounds.y -= TILE_SIZE * minSpacing;
        newObjBounds.width += TILE_SIZE * minSpacing * 2;
        newObjBounds.height += TILE_SIZE * minSpacing * 2;

        for (WorldObject existing : existingObjects) {
            Rectangle existingBounds = new Rectangle(
                existing.getTileX() * TILE_SIZE - (existing.getType().getWidthInTiles() * TILE_SIZE / 2f),
                existing.getTileY() * TILE_SIZE,
                existing.getType().getWidthInTiles() * TILE_SIZE,
                existing.getType().getHeightInTiles() * TILE_SIZE
            );

            if (newObjBounds.overlaps(existingBounds)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public List<WorldObject> generateObjectsForChunk(int chunkX, int chunkY, int[][] tiles, Biome biome, long seed) {
        List<WorldObject> objects = new CopyOnWriteArrayList<>();
        if (biome == null || tiles == null) {
            return objects;
        }

        long chunkSeed = seed + chunkX * 341873128712L + chunkY * 132897987541L;
        Random random = new Random(chunkSeed);


        if (biome.getSpawnableObjects() != null) {

            List<String> sortedObjects = new ArrayList<>(biome.getSpawnableObjects());
            sortedObjects.sort((a, b) -> {
                boolean aIsTree = a.contains("TREE");
                boolean bIsTree = b.contains("TREE");
                return bIsTree ? -1 : (aIsTree ? 1 : 0);
            });

            for (String objTypeName : sortedObjects) {
                ObjectType type;
                try {
                    type = ObjectType.valueOf(objTypeName);
                    double spawnChance = biome.getSpawnChanceForObject(type);
                    int attempts = (int) (spawnChance * (CHUNK_SIZE * CHUNK_SIZE));

                    for (int i = 0; i < attempts; i++) {
                        int localX = random.nextInt(CHUNK_SIZE);
                        int localY = random.nextInt(CHUNK_SIZE);


                        if (!biome.getAllowedTileTypes().contains(tiles[localX][localY])) {
                            continue;
                        }

                        int worldX = chunkX * CHUNK_SIZE + localX;
                        int worldY = chunkY * CHUNK_SIZE + localY;

                        if (canPlaceObject(objects, worldX, worldY, type)) {
                            WorldObject obj = new WorldObject(worldX, worldY, type, type.isCollidable());
                            objects.add(obj);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid object type {} in biome {}", objTypeName, biome.getName());
                }
            }
        }
        if (!worldService.isMultiplayerMode()) {
            itemSpawnService.spawnItemsInChunk(chunkX, chunkY, tiles, biome);
        }

        return objects;
    }


    private void generateTreesWithSpacing(List<WorldObject> objects, ObjectType type,
                                          Biome biome, int[][] tiles, Random random,
                                          int chunkX, int chunkY) {
        double spawnChance = biome.getSpawnChanceForObject(type);
        int attempts = (int) (spawnChance * (CHUNK_SIZE * CHUNK_SIZE));

        for (int i = 0; i < attempts; i++) {
            int localX = random.nextInt(CHUNK_SIZE);
            int localY = random.nextInt(CHUNK_SIZE);
            int worldX = chunkX * CHUNK_SIZE + localX;
            int worldY = chunkY * CHUNK_SIZE + localY;


            if (!biome.getAllowedTileTypes().contains(tiles[localX][localY])) {
                continue;
            }


            if (hasSpaceForTree(objects, worldX, worldY)) {
                WorldObject tree = new WorldObject(worldX, worldY, type, true);
                objects.add(tree);
            }
        }
    }

    private void generateRegularObjects(List<WorldObject> objects, ObjectType type,
                                        Biome biome, int[][] tiles, Random random,
                                        int chunkX, int chunkY) {
        double spawnChance = biome.getSpawnChanceForObject(type);
        int attempts = (int) (spawnChance * (CHUNK_SIZE * CHUNK_SIZE));

        for (int i = 0; i < attempts; i++) {
            int localX = random.nextInt(CHUNK_SIZE);
            int localY = random.nextInt(CHUNK_SIZE);

            if (!biome.getAllowedTileTypes().contains(tiles[localX][localY])) {
                continue;
            }

            int worldX = chunkX * CHUNK_SIZE + localX;
            int worldY = chunkY * CHUNK_SIZE + localY;

            if (hasSpaceForObject(objects, worldX, worldY, type)) {
                WorldObject obj = new WorldObject(worldX, worldY, type, type.isCollidable());
                objects.add(obj);
            }
        }
    }

    private boolean hasSpaceForObject(List<WorldObject> objects, int x, int y, ObjectType type) {
        int minSpacing = type.name().contains("TREE") ? (int) TREE_SPACING : 2;

        for (WorldObject obj : objects) {
            int dx = Math.abs(obj.getTileX() - x);
            int dy = Math.abs(obj.getTileY() - y);

            if (dx < minSpacing && dy < minSpacing) {
                return false;
            }
        }
        return true;
    }

    private boolean isTreeType(ObjectType type) {
        return type == ObjectType.TREE_0 ||
            type == ObjectType.TREE_1 ||
            type == ObjectType.SNOW_TREE ||
            type == ObjectType.HAUNTED_TREE ||
            type == ObjectType.RUINS_TREE ||
            type == ObjectType.APRICORN_TREE ||
            type == ObjectType.RAIN_TREE ||
            type == ObjectType.CHERRY_TREE;
    }

    private boolean hasSpaceForTree(List<WorldObject> objects, int x, int y) {

        for (WorldObject obj : objects) {
            if (isTreeType(obj.getType())) {
                int dx = Math.abs(obj.getTileX() - x);
                int dy = Math.abs(obj.getTileY() - y);


                if (dx < TREE_SPACING && dy < TREE_SPACING) {
                    return false;
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
