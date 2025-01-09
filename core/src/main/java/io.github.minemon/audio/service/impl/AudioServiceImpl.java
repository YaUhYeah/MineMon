package io.github.minemon.audio.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import io.github.minemon.audio.model.SoundEffect;
import io.github.minemon.audio.model.WeatherSoundEffect;
import io.github.minemon.audio.service.AudioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Primary
public class AudioServiceImpl implements AudioService {

    private final Map<SoundEffect, Sound> sounds = new EnumMap<>(SoundEffect.class);

    private final Map<WeatherSoundEffect, Music> weatherLoops = new EnumMap<>(WeatherSoundEffect.class);
    private final Map<WeatherSoundEffect, Sound> weatherSounds = new EnumMap<>(WeatherSoundEffect.class);

    private List<Music> menuMusicList;
    private Music currentMusic;

    private final float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float soundVolume = 1.0f;
    private boolean musicEnabled = true;
    private boolean soundEnabled = true;

    private static final float MUSIC_FADE_DURATION = 2.0f;
    private static final float FADE_OUT_DURATION = 2f;

    private boolean isFadingOutMusic = false;
    private float fadeOutMusicTimer = 0f;
    private boolean isFadingInMusic = false;
    private float fadeInMusicTimer = 0f;

    public AudioServiceImpl() {
    }

    @Override
    public void initAudio() {
        initializeAudio();
    }

    private void initializeAudio() {

        for (SoundEffect effect : SoundEffect.values()) {
            try {
                Sound sound = Gdx.audio.newSound(Gdx.files.internal("" + effect.getPath()));
                sounds.put(effect, sound);
            } catch (Exception e) {
                log.error("Failed to load sound: {}", effect.getPath(), e);
            }
        }




        for (WeatherSoundEffect wEffect : WeatherSoundEffect.values()) {
            String path = wEffect.getPath();
            if (wEffect == WeatherSoundEffect.THUNDER) {

                try {
                    Sound wSound = Gdx.audio.newSound(Gdx.files.internal(path));
                    weatherSounds.put(wEffect, wSound);
                } catch (Exception e) {
                    log.error("Failed to load weather sound: {}", path, e);
                }
            } else {

                try {
                    Music wMusic = Gdx.audio.newMusic(Gdx.files.internal(path));
                    wMusic.setLooping(true);
                    wMusic.setVolume(0f);
                    weatherLoops.put(wEffect, wMusic);
                } catch (Exception e) {
                    log.error("Failed to load weather loop: {}", path, e);
                }
            }
        }

        menuMusicList = new ArrayList<>();
        loadMenuMusic(Arrays.asList(
                "music/Menu-Music-1.mp3",
                "music/Menu-Music-2.mp3",
                "music/Menu-Music-0.mp3",
                "music/Menu-Music-3.mp3",
                "music/Menu-Music-4.mp3"
        ));
    }

    private void loadMenuMusic(List<String> paths) {
        for (String path : paths) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal( path));
                music.setVolume(musicVolume * masterVolume);
                menuMusicList.add(music);
            } catch (Exception e) {
                log.error("Failed to load menu music: {}", path, e);
            }
        }
    }
    @Override
    public void playMenuMusic() {
        if (!musicEnabled || menuMusicList == null || menuMusicList.isEmpty()) {
            return;
        }

        try {
            stopCurrentMusic(); 

            int index = MathUtils.random(menuMusicList.size() - 1);
            currentMusic = menuMusicList.get(index);

            if (currentMusic != null) {
                currentMusic.setVolume(0f);
                currentMusic.setLooping(false);
                currentMusic.play();

                
                isFadingInMusic = true;
                fadeInMusicTimer = MUSIC_FADE_DURATION;

                setMusicCompletionListenerForMenu();
                log.info("Started playing menu music track {}", index);
            }
        } catch (Exception e) {
            log.error("Failed to play menu music", e);
        }
    }

    private void stopAllSounds() {
        
        for (Sound sound : sounds.values()) {
            if (sound != null) {
                sound.stop();
                sound.dispose();
            }
        }
        sounds.clear();

        
        for (Sound sound : weatherSounds.values()) {
            if (sound != null) {
                sound.stop();
                sound.dispose();
            }
        }
        weatherSounds.clear();

        
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }

        if (menuMusicList != null) {
            for (Music music : menuMusicList) {
                if (music != null) {
                    music.stop();
                    music.dispose();
                }
            }
            menuMusicList.clear();
        }
    }
    private void setMusicCompletionListenerForMenu() {
        if (currentMusic != null) {
            currentMusic.setOnCompletionListener(music -> playMenuMusic());
        }
    }

    @Override
    public void stopMenuMusic() {
        if (currentMusic != null && currentMusic.isPlaying()) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = MUSIC_FADE_DURATION;
        }
    }

    @Override
    public void fadeOutMenuMusic() {
        if (currentMusic != null && currentMusic.isPlaying()) {
            isFadingOutMusic = true;
            fadeOutMusicTimer = FADE_OUT_DURATION;
        }
    }

    @Override
    public void update(float delta) {
        if (isFadingInMusic && currentMusic != null) {
            fadeInMusicTimer -= delta;
            float progress = 1 - Math.max(0, fadeInMusicTimer / MUSIC_FADE_DURATION);
            float volume = progress * musicVolume * masterVolume;
            currentMusic.setVolume(volume);

            if (fadeInMusicTimer <= 0) {
                isFadingInMusic = false;
                currentMusic.setVolume(musicVolume * masterVolume);
            }
        }

        if (isFadingOutMusic && currentMusic != null) {
            fadeOutMusicTimer -= delta;
            float volume = Math.max(0, (fadeOutMusicTimer / MUSIC_FADE_DURATION) * musicVolume * masterVolume);
            currentMusic.setVolume(volume);

            if (fadeOutMusicTimer <= 0) {
                currentMusic.stop();
                isFadingOutMusic = false;
                currentMusic = null;
            }
        }
    }

    @Override
    public void playSound(SoundEffect effect) {
        if (!soundEnabled) return;
        Sound sound = sounds.get(effect);
        if (sound != null) {
            sound.play(soundVolume * masterVolume);
        }
    }

    @Override
    public void playWeatherSound(WeatherSoundEffect effect, float volume, float pitch) {
        if (!soundEnabled) return;

        Sound wSound = weatherSounds.get(effect);
        if (wSound != null) {
            long id = wSound.play(volume * soundVolume * masterVolume);
            wSound.setPitch(id, pitch);
        } else {
            log.debug("Weather sound {} not found or not loaded as Sound.", effect);
        }
    }

    @Override
    public void updateWeatherLoop(WeatherSoundEffect effect, float volume) {
        if (!soundEnabled) return;

        Music loop = weatherLoops.get(effect);
        if (loop != null) {
            if (!loop.isPlaying()) {
                loop.play();
            }
            float finalVolume = volume * soundVolume * masterVolume;
            loop.setVolume(finalVolume);

            if (finalVolume <= 0.01f) {
                loop.pause();
            }
        } else {
            log.debug("Weather loop {} not found or not loaded as Music.", effect);
        }
    }

    @Override
    public void stopWeatherLoop(WeatherSoundEffect effect) {
        Music loop = weatherLoops.get(effect);
        if (loop != null && loop.isPlaying()) {
            loop.stop();
        }
    }

    @Override
    public float getMusicVolume() {
        return musicVolume;
    }

    @Override
    public void setMusicVolume(float musicVolume) {
        this.musicVolume = musicVolume;
        if (currentMusic != null) {
            currentMusic.setVolume(musicVolume * masterVolume);
        }
    }

    @Override
    public float getSoundVolume() {
        return soundVolume;
    }

    @Override
    public void setSoundVolume(float soundVolume) {
        this.soundVolume = soundVolume;

        for (Music loop : weatherLoops.values()) {
            if (loop.isPlaying()) {



                float currentVol = loop.getVolume() / (masterVolume);
                loop.setVolume(currentVol * soundVolume);
            }
        }
    }

    @Override
    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    @Override
    public void setMusicEnabled(boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
        if (currentMusic != null) {
            if (musicEnabled) {
                if (!currentMusic.isPlaying()) {
                    currentMusic.play();
                }
                currentMusic.setVolume(musicVolume * masterVolume);
            } else {
                currentMusic.pause();
            }
        }
    }

    @Override
    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    @Override
    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;

        if (!soundEnabled) {
            for (Music loop : weatherLoops.values()) {
                if (loop.isPlaying()) {
                    loop.pause();
                }
            }
        } else {


        }
    }

    private void stopCurrentMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic = null;
        }
    }

    @Override
    public void dispose() {
        stopAllSounds();
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();

        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }

        if (menuMusicList != null) {
            for (Music music : menuMusicList) {
                music.dispose();
            }
            menuMusicList.clear();
        }


        for (Sound wSound : weatherSounds.values()) {
            wSound.dispose();
        }
        weatherSounds.clear();

        for (Music loop : weatherLoops.values()) {
            loop.dispose();
        }
        weatherLoops.clear();
    }
}
