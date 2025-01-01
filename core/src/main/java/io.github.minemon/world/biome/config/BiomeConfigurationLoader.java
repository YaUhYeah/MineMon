package io.github.minemon.world.biome.config;

import com.badlogic.gdx.utils.Json;
import io.github.minemon.core.service.FileAccessService;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BiomeConfigurationLoader {
    private static final Logger logger = LoggerFactory.getLogger(BiomeConfigurationLoader.class);

    public static class BiomeDefinition {
        public String name;
        public String type;
        public ArrayList<Integer> allowedTileTypes = new ArrayList<>();
        public HashMap<String, Double> tileDistribution = new HashMap<>();
        public ArrayList<String> spawnableObjects = new ArrayList<>();
        public HashMap<String, Double> spawnChances = new HashMap<>();
    }

    public static class BiomeRoot {
        public ArrayList<BiomeDefinition> biomes = new ArrayList<>();
    }

    private final FileAccessService fileAccessService;

    public BiomeConfigurationLoader(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }

    public Map<BiomeType, Biome> loadBiomes(String configFilePath) {
        if (!fileAccessService.exists(configFilePath)) {
            logger.error("Biome config file not found: {}", configFilePath);
            return Collections.emptyMap();
        }

        String jsonContent;
        try {
            jsonContent = fileAccessService.readFile(configFilePath);
        } catch (RuntimeException e) {
            logger.error("Failed to read biome config: {}", e.getMessage());
            return Collections.emptyMap();
        }

        Json json = new Json();
        json.setIgnoreUnknownFields(true);

        BiomeRoot root = json.fromJson(BiomeRoot.class, jsonContent);
        if (root == null || root.biomes.isEmpty()) {
            logger.warn("No biomes found in file: {}", configFilePath);
            return Collections.emptyMap();
        }

        Map<BiomeType, Biome> biomeMap = new HashMap<>();
        for (BiomeDefinition def : root.biomes) {
            BiomeType type;
            try {
                type = BiomeType.valueOf(def.type.toUpperCase());
            } catch (IllegalArgumentException ex) {
                logger.error("Unknown biome type '{}' in config. Skipping...", def.type);
                continue;
            }

            Map<Integer, Double> distribution = new HashMap<>();
            for (Map.Entry<String, Double> entry : def.tileDistribution.entrySet()) {
                try {
                    distribution.put(Integer.valueOf(entry.getKey()), entry.getValue());
                } catch (NumberFormatException ex) {
                    logger.error("Invalid tile type '{}' in distribution for biome '{}'. Skipping this entry.", entry.getKey(), def.name);
                }
            }

            Biome biome = new Biome(
                    def.name,
                    type,
                    def.allowedTileTypes,
                    distribution,
                    def.spawnableObjects,
                    def.spawnChances
            );
            biomeMap.put(type, biome);
        }

        logger.info("Loaded {} biomes from {}", biomeMap.size(), configFilePath);
        return biomeMap;
    }
}
