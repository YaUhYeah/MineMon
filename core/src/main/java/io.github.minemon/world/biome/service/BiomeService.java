package io.github.minemon.world.biome.service;

import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeTransitionResult;
import io.github.minemon.world.biome.model.BiomeType;

public interface BiomeService {
    
    BiomeTransitionResult getBiomeAt(float worldX, float worldY);

    
    Biome getBiome(BiomeType type);

    void init();
    void initWithSeed(long seed);

    
    void debugBiomeDistribution(int samples);
}
