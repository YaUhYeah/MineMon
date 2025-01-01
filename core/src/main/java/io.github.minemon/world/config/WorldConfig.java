package io.github.minemon.world.config;

import lombok.Getter;

@Getter
public class WorldConfig {
    private final long seed;
    private final int chunkSize = 16;
    private final int tileSize = 32;

    public WorldConfig(long seed) {
        this.seed = seed;
    }

}
