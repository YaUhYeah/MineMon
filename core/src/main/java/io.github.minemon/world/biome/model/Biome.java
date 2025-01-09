package io.github.minemon.world.biome.model;

import io.github.minemon.world.model.ObjectType;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class Biome {
    private final String name;

    private final BiomeType type;

    private final List<Integer> allowedTileTypes;

    private final Map<Integer, Double> tileDistribution;

    private final List<String> spawnableObjects;

    private final Map<String, Double> spawnChances;

    public Biome(String name,
                 BiomeType type,
                 List<Integer> allowedTileTypes,
                 Map<Integer, Double> tileDistribution,
                 List<String> spawnableObjects,
                 Map<String, Double> spawnChances) {
        this.name = name;
        this.type = type;
        this.allowedTileTypes = allowedTileTypes;
        this.tileDistribution = tileDistribution;
        this.spawnableObjects = spawnableObjects;
        this.spawnChances = spawnChances;
    }

    public double getSpawnChanceForObject(ObjectType objType) {
        return spawnChances.getOrDefault(objType.name(), 0.0);
    }
}
