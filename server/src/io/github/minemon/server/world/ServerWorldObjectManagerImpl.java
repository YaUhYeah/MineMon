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
        int chunkSize = tiles.length;

        if (biome != null && biome.getSpawnableObjects().contains("TREE_0")) {
            Random random = new Random((chunkX * 341L + chunkY * 773L) ^ seed);
            int spacing = 4;

            for (int lx = 0; lx < chunkSize; lx += spacing) {
                for (int ly = 0; ly < chunkSize; ly += spacing) {
                    int tileId = tiles[lx][ly];

                    if (biome.getAllowedTileTypes().contains(tileId) && random.nextFloat() < 0.1f) {
                        int worldX = chunkX * chunkSize + lx;
                        int worldY = chunkY * chunkSize + ly;
                        if (noTreeNearby(objects, worldX, worldY, spacing)) {
                            WorldObject tree = new WorldObject(worldX, worldY, ObjectType.TREE_0, ObjectType.TREE_0.isCollidable());
                            objects.add(tree);
                        }
                    }
                }
            }
        }

        objectsByChunk.put(chunkX + "," + chunkY, objects);
        log.info("Generated {} objects for chunk {},{} on server.", objects.size(), chunkX, chunkY);
        return objects;
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
