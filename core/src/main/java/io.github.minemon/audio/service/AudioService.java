package io.github.minemon.audio.service;

import io.github.minemon.audio.model.SoundEffect;
import io.github.minemon.audio.model.WeatherSoundEffect;

public interface AudioService {

    void initAudio();

    void playMenuMusic();
    void stopMenuMusic();
    void fadeOutMenuMusic();
    void update(float delta);

    void playSound(SoundEffect effect);


    
    void playWeatherSound(WeatherSoundEffect effect, float volume, float pitch);

    
    void updateWeatherLoop(WeatherSoundEffect effect, float volume);

    
    void stopWeatherLoop(WeatherSoundEffect effect);

    float getMusicVolume();
    void setMusicVolume(float musicVolume);
    float getSoundVolume();
    void setSoundVolume(float soundVolume);
    boolean isMusicEnabled();
    void setMusicEnabled(boolean musicEnabled);
    boolean isSoundEnabled();
    void setSoundEnabled(boolean soundEnabled);

    void dispose();
}
