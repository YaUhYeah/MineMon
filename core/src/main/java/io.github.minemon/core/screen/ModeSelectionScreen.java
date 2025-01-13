package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.service.BackgroundService;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.world.service.WorldService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ModeSelectionScreen implements Screen {
    private final AudioService audioService;
    private final ScreenManager screenManager;
    private final SettingsService settingsService;
    private final BackgroundService backgroundAnimation;
    @Setter
    private MultiplayerClient multiplayerClient;
    @Autowired
    @Lazy
    @Setter
    private WorldService worldService;
    private Stage stage;
    private Skin skin;
    private Window settingsWindow;
    private boolean initialized = false;

    @Override
    public void show() {
        try {
            log.info("ModeSelectionScreen show() called");

            ensureInitialized();


            if (stage != null) {
                Gdx.input.setInputProcessor(stage);
                log.info("Stage set as input processor");
            }


            backgroundAnimation.initialize();


            Gdx.app.postRunnable(() -> {
                try {
                    audioService.playMenuMusic();
                } catch (Exception e) {
                    log.error("Failed to play menu music", e);
                }
            });

        } catch (Exception e) {
            log.error("Error in show(): {}", e.getMessage(), e);
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            try {
                log.info("Starting ModeSelectionScreen initialization");


                String skinPath = "Skins/uiskin.json";
                if (!Gdx.files.internal(skinPath).exists()) {
                    log.error("UI skin not found at: {}", skinPath);
                    throw new RuntimeException("Required UI skin missing: " + skinPath);
                }


                ScreenViewport viewport = new ScreenViewport();
                stage = new Stage(viewport);


                try {
                    skin = new Skin(Gdx.files.internal(skinPath));
                    log.info("UI skin loaded successfully");
                } catch (Exception e) {
                    log.error("Failed to load UI skin", e);
                    throw e;
                }

                createMainMenu();
                createSettingsWindow();

                initialized = true;
                log.info("ModeSelectionScreen initialized successfully");

            } catch (Exception e) {
                log.error("Failed to initialize ModeSelectionScreen: {}", e.getMessage(), e);
                throw new RuntimeException("Screen initialization failed", e);
            }
        }
    }

    @Override
    public void render(float delta) {
        try {
            if (!initialized) {
                ensureInitialized();
            }

            Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            backgroundAnimation.render(false);
            if (stage != null) {
                stage.act(delta);
                stage.draw();
            }

        } catch (Exception e) {
            log.error("Error in render(): {}", e.getMessage(), e);
        }
    }

    @Override
    public void hide() {
        audioService.stopMenuMusic();
        Gdx.input.setInputProcessor(null);
        cleanup();
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null);
        cleanup();
        if (backgroundAnimation != null) {
            backgroundAnimation.dispose();
        }
    }

    private void createMainMenu() {
        Table mainTable = new Table();
        mainTable.setFillParent(true);


        Label titleLabel = new Label("MineMon", skin);
        titleLabel.setFontScale(2.0f);


        Label versionLabel = new Label("Version 1.0", skin);


        TextButton singlePlayerButton = createStyledButton("Single Player");
        TextButton multiplayerButton = createStyledButton("Multiplayer");
        TextButton settingsButton = createStyledButton("Settings");
        TextButton exitButton = createStyledButton("Exit Game");


        Label motdLabel = new Label("Welcome to MineMon - Catch them all in an open world!", skin);
        motdLabel.setWrap(true);


        mainTable.add(titleLabel).pad(50).row();
        mainTable.add(versionLabel).padBottom(20).row();
        mainTable.add(motdLabel).width(400).pad(20).row();
        mainTable.add(singlePlayerButton).width(300).pad(10).row();
        mainTable.add(multiplayerButton).width(300).pad(10).row();
        mainTable.add(settingsButton).width(300).pad(10).row();
        mainTable.add(exitButton).width(300).pad(10).row();

        singlePlayerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (multiplayerClient.isConnected()) {
                    multiplayerClient.disconnect();
                }
                worldService.handleDisconnect();
                worldService.setMultiplayerMode(false);

                screenManager.showScreen(WorldSelectionScreen.class);
            }
        });

        multiplayerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screenManager.showScreen(LoginScreen.class);
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleSettingsWindow();
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        stage.addActor(mainTable);
    }

    private TextButton createStyledButton(String text) {
        TextButton button = new TextButton(text, skin);
        button.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                button.setColor(1, 1, 0.8f, 1);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                button.setColor(1, 1, 1, 1);
            }
        });
        return button;
    }

    private void createSettingsWindow() {
        settingsWindow = new Window("Settings", skin);
        settingsWindow.setVisible(false);
        settingsWindow.setModal(true);


        Label musicLabel = new Label("Music Volume:", skin);
        Slider musicSlider = new Slider(0, 1, 0.1f, false, skin);
        musicSlider.setValue(settingsService.getMusicVolume());
        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                float newVolume = musicSlider.getValue();

                settingsService.updateMusicVolume(newVolume);

                audioService.setMusicVolume(newVolume);
            }
        });


        Label soundLabel = new Label("Sound Volume:", skin);
        Slider soundSlider = new Slider(0, 1, 0.1f, false, skin);
        soundSlider.setValue(settingsService.getSoundVolume());
        soundSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float newVolume = soundSlider.getValue();
                settingsService.updateSoundVolume(newVolume);
                audioService.setSoundVolume(newVolume);
            }
        });


        CheckBox vsyncCheck = new CheckBox(" VSync", skin);
        vsyncCheck.setChecked(settingsService.getVSync());
        vsyncCheck.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsService.setVSync(vsyncCheck.isChecked());
                Gdx.graphics.setVSync(vsyncCheck.isChecked());
            }
        });


        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsWindow.setVisible(false);
            }
        });


        Table settingsTable = new Table();
        settingsTable.add(musicLabel).pad(10);
        settingsTable.add(musicSlider).width(200).pad(10).row();
        settingsTable.add(soundLabel).pad(10);
        settingsTable.add(soundSlider).width(200).pad(10).row();
        settingsTable.add(vsyncCheck).colspan(2).pad(10).row();
        settingsTable.add(closeButton).colspan(2).pad(10);

        settingsWindow.add(settingsTable);
        settingsWindow.pack();
        settingsWindow.setPosition(
            (stage.getWidth() - settingsWindow.getWidth()) / 2,
            (stage.getHeight() - settingsWindow.getHeight()) / 2
        );

        stage.addActor(settingsWindow);
    }

    private void toggleSettingsWindow() {
        settingsWindow.setVisible(!settingsWindow.isVisible());
    }



    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
            if (settingsWindow != null) {
                settingsWindow.setPosition(
                    (width - settingsWindow.getWidth()) / 2,
                    (height - settingsWindow.getHeight()) / 2
                );
            }
        }
    }

    private void cleanup() {
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
        initialized = false;
        settingsWindow = null;
    }



    @Override
    public void pause() {
    }

    @Override
    public void resume() {
        initialized = false;
    }

}
