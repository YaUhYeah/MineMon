package io.github.minemon.world.biome.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class BiomeTransitionResult {
    private final Biome primaryBiome;
    private final Biome secondaryBiome;
    
    private final float transitionFactor;
}
