package io.github.minemon.core.screen;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.service.BackgroundService;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class SettingsScreen implements Screen {
    private final ScreenManager screenManager;
    private final SettingsService settingsService;
    private final BackgroundService backgroundService;

    private Stage stage;
    private Skin skin;
    private Window settingsWindow;
    private boolean isGameScreen;
    @Autowired
    private AudioService audioService;

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));


        isGameScreen = screenManager.getPreviousScreen() instanceof GameScreen;

        createSettingsWindow();


        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    goBack();
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void createSettingsWindow() {
        settingsWindow = new Window("Settings", skin);
        settingsWindow.setMovable(false);
        settingsWindow.setKeepWithinStage(true);

        Table content = new Table(skin);
        content.defaults().pad(5).spaceBottom(10);


        content.add("Audio Settings").colspan(2).align(Align.left).row();
        addVolumeSlider(content, "Music Volume", settingsService.getMusicVolume(),
            settingsService::updateMusicVolume);
        addVolumeSlider(content, "Sound Volume", settingsService.getSoundVolume(),
            settingsService::updateSoundVolume);

        content.add().row();


        content.add("Video Settings").colspan(2).align(Align.left).row();
        addCheckbox(content, "VSync", settingsService.getVSync(),
            settingsService::updateVSync);
        addCheckbox(content, "Particles", settingsService.getSettings().isParticles(),
            settingsService::updateParticles);
        addCheckbox(content, "Smooth Lighting", settingsService.getSettings().isSmoothLighting(),
            settingsService::updateSmoothLighting);


        content.add("Controls").colspan(2).align(Align.left).row();
        addKeyBinding(content, "Move Up", "UP");
        addKeyBinding(content, "Move Down", "DOWN");
        addKeyBinding(content, "Move Left", "LEFT");
        addKeyBinding(content, "Move Right", "RIGHT");
        addKeyBinding(content, "Run", "RUN");
        addKeyBinding(content, "Inventory", "INVENTORY");


        TextButton doneButton = new TextButton("Done", skin);
        doneButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        content.add(doneButton).colspan(2).width(200).pad(20).row();

        settingsWindow.add(content).pad(20);
        settingsWindow.pack();


        settingsWindow.setPosition(
            (Gdx.graphics.getWidth() - settingsWindow.getWidth()) / 2,
            (Gdx.graphics.getHeight() - settingsWindow.getHeight()) / 2
        );

        stage.addActor(settingsWindow);
    }

    private void addVolumeSlider(Table table, String label, float initialValue,
                                 VolumeChangeListener listener) {
        table.add(label).left();
        Slider slider = new Slider(0, 1, 0.01f, false, skin);
        slider.setValue(initialValue);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float newValue = slider.getValue();
                
                listener.onVolumeChanged(newValue);

                
                if ("Music Volume".equals(label)) {
                    audioService.setMusicVolume(newValue);
                } else if ("Sound Volume".equals(label)) {
                    audioService.setSoundVolume(newValue);
                }
            }
        });
        table.add(slider).width(200).row();
    }


    private void addCheckbox(Table table, String label, boolean initialValue,
                             CheckboxChangeListener listener) {
        CheckBox checkbox = new CheckBox(" " + label, skin);
        checkbox.setChecked(initialValue);
        checkbox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.onCheckboxChanged(checkbox.isChecked());
            }
        });
        table.add(checkbox).colspan(2).left().row();
    }

    private void addKeyBinding(Table table, String label, String binding) {
        table.add(label).left();

        TextButton bindButton = new TextButton(
            binding.equals("INVENTORY")
                ? Input.Keys.toString(settingsService.getKeyBindings().get("INVENTORY"))
                : Input.Keys.toString(settingsService.getKeyBindings().get(binding)),
            skin
        );

        bindButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                startKeyBinding(bindButton, binding);
            }
        });

        table.add(bindButton).width(100).row();
    }

    private void startKeyBinding(TextButton button, String binding) {
        button.setText("Press any key...");
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE) {
                    if (binding.equals("INVENTORY")) {
                        settingsService.setKeyBinding("INVENTORY", keycode);
                    } else {
                        settingsService.setKeyBinding(binding, keycode);
                    }
                    button.setText(Input.Keys.toString(keycode));
                }
                stage.removeListener(this);
                return true;
            }
        });
    }

    private void goBack() {
        if (isGameScreen) {
            screenManager.goBack();
        } else {
            screenManager.showScreen(ModeSelectionScreen.class);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);


        backgroundService.render(isGameScreen);

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
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }

    private interface VolumeChangeListener {
        void onVolumeChanged(float value);
    }

    private interface CheckboxChangeListener {
        void onCheckboxChanged(boolean value);
    }
}
