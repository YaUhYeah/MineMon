package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.service.BackgroundService;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class ModeSelectionScreen implements Screen {
    private final AudioService audioService;
    private final ScreenManager screenManager;
    private final SettingsService settingsService;
    private final BackgroundService backgroundAnimation;

    private Stage stage;
    private Skin skin;
    private Window settingsWindow;

    @Override
    public void show() {
        audioService.playMenuMusic();
        backgroundAnimation.initialize();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        createMainMenu();
        createSettingsWindow();
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
        musicSlider.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsService.setMusicVolume(musicSlider.getValue());
            }
        });


        Label soundLabel = new Label("Sound Volume:", skin);
        Slider soundSlider = new Slider(0, 1, 0.1f, false, skin);
        soundSlider.setValue(settingsService.getSoundVolume());
        soundSlider.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                settingsService.setSoundVolume(soundSlider.getValue());
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
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        backgroundAnimation.render(false);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        settingsWindow.setPosition(
                (width - settingsWindow.getWidth()) / 2,
                (height - settingsWindow.getHeight()) / 2
        );
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        audioService.stopMenuMusic();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        backgroundAnimation.dispose();
    }
}
