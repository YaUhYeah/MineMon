package io.github.minemon.server.world;

import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;
import io.github.minemon.world.biome.service.impl.BiomeServiceImpl;
import jakarta.annotation.PostConstruct;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
public class ServerBiomeServiceImpl extends BiomeServiceImpl {
    @Autowired
    private BiomeConfigurationLoader configurationLoader;

    public ServerBiomeServiceImpl(BiomeConfigurationLoader configurationLoader) {
        super(configurationLoader);
    }

    @PostConstruct
    public void validateServerBiomes() {
        Map<BiomeType, Biome> loadedBiomes = configurationLoader.loadBiomes("config/biomes.json");

        if (loadedBiomes.isEmpty()) {
            throw new IllegalStateException("No biomes loaded for server");
        }

        // Validate each biome has required data
        loadedBiomes.forEach((type, biome) -> {
            if (biome.getAllowedTileTypes() == null || biome.getAllowedTileTypes().isEmpty()) {
                throw new IllegalStateException("Biome " + type + " has no allowed tile types");
            }
            if (biome.getTileDistribution() == null || biome.getTileDistribution().isEmpty()) {
                throw new IllegalStateException("Biome " + type + " has no tile distribution");
            }
        });
    }
}
