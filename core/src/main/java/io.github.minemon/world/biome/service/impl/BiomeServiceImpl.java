package io.github.minemon.world.biome.service.impl;

import io.github.minemon.utils.OpenSimplex2;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeTransitionResult;
import io.github.minemon.world.biome.model.BiomeType;
import io.github.minemon.world.biome.service.BiomeService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
public class BiomeServiceImpl implements BiomeService {


    private static final float TEMPERATURE_SCALE = 0.00005f;
    private static final float MOISTURE_SCALE = 0.00005f;
    private static final float WARP_SCALE = 0.00001f;
    private static final float WARP_STRENGTH = 30f;
    private static final double TRANSITION_BASE = 0.15;
    private final BiomeConfigurationLoader configurationLoader;
    private long baseSeed;
    private long temperatureSeed;
    private long moistureSeed;
    private long warpSeed;
    private Map<BiomeType, Biome> biomes = new HashMap<>();

    public BiomeServiceImpl(BiomeConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    public void initWithSeed(long seed) {
        this.baseSeed = seed;
        this.temperatureSeed = seed + 1000;
        this.moistureSeed = seed + 2000;
        this.warpSeed = seed + 4000;
    }


    @PostConstruct
    @Override
    public void init() {
        this.biomes = configurationLoader.loadBiomes("config/biomes.json");
        if (biomes.isEmpty()) {
            log.warn("No biomes loaded - using defaults.");
        } else {
            log.info("Loaded {} biomes.", biomes.size());
        }
    }

    @Override
    public BiomeTransitionResult getBiomeAt(float worldX, float worldY) {
        float[] warped = domainWarp(worldX, worldY);
        double temperature = getNoiseValue(warped[0], warped[1], temperatureSeed, TEMPERATURE_SCALE);
        double moisture = getNoiseValue(warped[0], warped[1], moistureSeed, MOISTURE_SCALE);


        BiomeType primaryType = determineBiomeType(temperature, moisture);


        double edgeNoise = getNoiseValue(worldX, worldY, warpSeed, TEMPERATURE_SCALE * 2);
        double transitionThreshold = TRANSITION_BASE + edgeNoise * 0.05;

        if (shouldBlendBiomes(temperature, moisture, transitionThreshold)) {

            float[] offsetWarp = domainWarp(worldX + 64, worldY + 64);
            double temp2 = getNoiseValue(offsetWarp[0], offsetWarp[1], temperatureSeed, TEMPERATURE_SCALE);
            double moist2 = getNoiseValue(offsetWarp[0], offsetWarp[1], moistureSeed, MOISTURE_SCALE);
            BiomeType secondaryType = determineBiomeType(temp2, moist2);

            if (primaryType != secondaryType && areCompatibleBiomes(primaryType, secondaryType)) {
                float transitionFactor = computeTransitionFactor(temperature, moisture, transitionThreshold);
                return new BiomeTransitionResult(getBiome(primaryType), getBiome(secondaryType), transitionFactor);
            }
        }


        return new BiomeTransitionResult(getBiome(primaryType), null, 1.0f);
    }


    private float computeTransitionFactor(double temperature, double moisture, double threshold) {

        double tempDelta = Math.abs(temperature - 0.5);
        double moistDelta = Math.abs(moisture - 0.5);
        double maxDelta = Math.max(tempDelta, moistDelta);

        double factor = maxDelta / threshold;
        return (float) Math.min(Math.max(factor, 0.0), 1.0);
    }


    private boolean shouldBlendBiomes(double temperature, double moisture, double transitionThreshold) {
        return (Math.abs(temperature - 0.5) < transitionThreshold ||
                Math.abs(moisture - 0.5) < transitionThreshold);
    }


    private BiomeType determineBiomeType(double temperature, double moisture) {

        if (temperature < 0.35) {
            return (moisture > 0.65) ? BiomeType.DESERT : BiomeType.PLAINS;
        } else if (temperature > 0.65) {
            return (moisture < 0.35) ? BiomeType.DESERT : BiomeType.PLAINS;
        } else {
            return BiomeType.PLAINS;
        }
    }


    private float[] domainWarp(float x, float y) {
        float[] warped = new float[]{x, y};
        float amplitude = WARP_STRENGTH;
        float frequency = WARP_SCALE;

        for (int i = 0; i < 3; i++) {
            float warpX = (float) OpenSimplex2.noise2(warpSeed + i, warped[0] * frequency, warped[1] * frequency) * amplitude;
            float warpY = (float) OpenSimplex2.noise2(warpSeed + i + 1000, warped[0] * frequency, warped[1] * frequency) * amplitude;

            warped[0] += warpX;
            warped[1] += warpY;

            amplitude *= 0.5f;
            frequency *= 1.8f;
        }
        return warped;
    }


    private double getNoiseValue(float x, float y, long seed, float scale) {
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0;


        for (int i = 0; i < 3; i++) {
            double n = OpenSimplex2.noise2(seed + i, x * scale * frequency, y * scale * frequency);
            value += amplitude * n;
            maxAmplitude += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }

        value = (value / maxAmplitude + 1) / 2;
        return Math.max(0.0, Math.min(1.0, Math.pow(value, 1.1)));
    }


    private boolean areCompatibleBiomes(BiomeType a, BiomeType b) {
        if (a == b) return true;

        if (a == BiomeType.PLAINS && b == BiomeType.FOREST) return true;

        return true;
    }

    @Override
    public Biome getBiome(BiomeType type) {
        Biome biome = biomes.get(type);
        if (biome == null) {
            log.error("Missing biome type: {}. Falling back to PLAINS.", type);
            return biomes.getOrDefault(BiomeType.PLAINS, new Biome("Plains", BiomeType.PLAINS, null, null, null, null));
        }
        return biome;
    }

    @Override
    public void debugBiomeDistribution(int samples) {

        Map<BiomeType, Integer> distribution = new HashMap<>();
        for (int i = 0; i < samples; i++) {
            float x = (float) Math.random() * 1000;
            float y = (float) Math.random() * 1000;
            BiomeTransitionResult result = getBiomeAt(x, y);
            BiomeType type = result.getPrimaryBiome().getType();
            distribution.merge(type, 1, Integer::sum);
        }

        log.info("=== Biome Distribution ({} samples) ===", samples);
        for (Map.Entry<BiomeType, Integer> entry : distribution.entrySet()) {
            double perc = (entry.getValue() * 100.0) / samples;
            log.info("{}: {} ({}%)", entry.getKey(), entry.getValue(), perc);
        }
        log.info("======================================");
    }
}
