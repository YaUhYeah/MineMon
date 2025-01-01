package io.github.minemon.world.service;

import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.model.WorldObject;

import java.util.List;

public interface WorldObjectManager {
    void initialize();
    List<WorldObject> generateObjectsForChunk(int chunkX, int chunkY, int[][] tiles, Biome biome, long seed);
    List<WorldObject> getObjectsForChunk(int chunkX, int chunkY);
    void addObject(WorldObject object);
    void removeObject(String objectId);
    void loadObjectsForChunk(int chunkX, int chunkY, List<WorldObject> objects);
}
