package io.github.minemon.core.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GameSettings {
    private float musicVolume = 0.7f;
    private float soundVolume = 1.0f;
    private boolean vSync = true;
    private int renderDistance = 8;
    private boolean particles = true;
    private boolean smoothLighting = true;
}