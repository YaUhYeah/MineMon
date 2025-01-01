package io.github.minemon.weather.service;

import io.github.minemon.audio.model.WeatherSoundEffect;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.weather.model.WeatherType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class WeatherAudioService {

    private static final float THUNDER_MIN_INTERVAL = 5f;
    private static final float THUNDER_MAX_INTERVAL = 15f;

    private float thunderTimer;
    private float nextThunderTime;
    private boolean isThundering;
    @Getter
    private float lightningAlpha;

    @Setter
    private AudioService audioService;

    public WeatherAudioService() {
        resetThunderTimer();
    }

    private void resetThunderTimer() {
        nextThunderTime = randomRange();
        thunderTimer = 0;
    }

    
    public void update(float delta, WeatherType currentWeather, float intensity) {
        updateThunderAndLightning(delta, currentWeather, intensity);
        updateWeatherSounds(currentWeather, intensity);
    }

    private void updateThunderAndLightning(float delta, WeatherType currentWeather, float intensity) {
        if (currentWeather == WeatherType.THUNDERSTORM) {
            thunderTimer += delta;


            if (isThundering) {
                lightningAlpha = Math.max(0, lightningAlpha - delta * 2f);
                if (lightningAlpha <= 0) {
                    isThundering = false;
                }
            }


            if (thunderTimer >= nextThunderTime) {
                triggerThunderAndLightning(intensity);
                resetThunderTimer();
            }
        } else {
            lightningAlpha = 0;
            isThundering = false;
            resetThunderTimer();
        }
    }

    private void triggerThunderAndLightning(float intensity) {
        isThundering = true;
        lightningAlpha = 0.7f * intensity;


        float volume = 0.5f + (intensity * 0.5f);
        float pitch = 0.9f + (randomFloat() * 0.2f);
        if (audioService != null && audioService.isSoundEnabled()) {
            audioService.playWeatherSound(WeatherSoundEffect.THUNDER, volume, pitch);
        } else {
            log.debug("AudioService not available or sound disabled; skipping thunder sound.");
        }
    }

    private void updateWeatherSounds(WeatherType currentWeather, float intensity) {
        if (audioService == null || !audioService.isSoundEnabled()) {
            return;
        }


        switch (currentWeather) {
            case RAIN -> {

                audioService.updateWeatherLoop(WeatherSoundEffect.LIGHT_RAIN, intensity * 0.6f);
                audioService.stopWeatherLoop(WeatherSoundEffect.WIND);
                audioService.stopWeatherLoop(WeatherSoundEffect.SAND_WIND);
            }
            case HEAVY_RAIN, THUNDERSTORM -> {
                audioService.stopWeatherLoop(WeatherSoundEffect.LIGHT_RAIN);
                audioService.stopWeatherLoop(WeatherSoundEffect.WIND);
                audioService.stopWeatherLoop(WeatherSoundEffect.SAND_WIND);
            }
            case SNOW, BLIZZARD -> {

                audioService.updateWeatherLoop(WeatherSoundEffect.WIND, intensity * 0.4f);
                audioService.stopWeatherLoop(WeatherSoundEffect.LIGHT_RAIN);
                audioService.stopWeatherLoop(WeatherSoundEffect.SAND_WIND);
            }
            case SANDSTORM -> {

                audioService.updateWeatherLoop(WeatherSoundEffect.SAND_WIND, intensity * 0.7f);
                audioService.stopWeatherLoop(WeatherSoundEffect.LIGHT_RAIN);
                audioService.stopWeatherLoop(WeatherSoundEffect.WIND);
            }
            default -> {

                audioService.stopWeatherLoop(WeatherSoundEffect.LIGHT_RAIN);
                audioService.stopWeatherLoop(WeatherSoundEffect.WIND);
                audioService.stopWeatherLoop(WeatherSoundEffect.SAND_WIND);
            }
        }
    }

    private float randomFloat() {
        return (float) Math.random();
    }

    private float randomRange() {
        return WeatherAudioService.THUNDER_MIN_INTERVAL + randomFloat() * (WeatherAudioService.THUNDER_MAX_INTERVAL - WeatherAudioService.THUNDER_MIN_INTERVAL);
    }
}
