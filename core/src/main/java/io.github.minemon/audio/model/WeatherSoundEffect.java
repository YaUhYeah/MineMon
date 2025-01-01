package io.github.minemon.audio.model;

import lombok.Getter;

@Getter
public enum WeatherSoundEffect {
    LIGHT_RAIN("sounds/weather/rain.ogg"),
    THUNDER("sounds/weather/thunder.ogg"),
    WIND("sounds/weather/wind.ogg"),
    SAND_WIND("sounds/weather/sandwind.ogg");

    private final String path;

    WeatherSoundEffect(String path) {
        this.path = path;
    }

}
