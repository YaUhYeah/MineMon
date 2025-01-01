package io.github.minemon.world.service;

import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;

public interface WorldGenerator {
    void setSeedAndBiomes(long seed, java.util.Map<BiomeType, Biome> biomes);
    int[][] generateChunk(int chunkX, int chunkY);
    Biome getBiomeForChunk(int chunkX, int chunkY);
}
