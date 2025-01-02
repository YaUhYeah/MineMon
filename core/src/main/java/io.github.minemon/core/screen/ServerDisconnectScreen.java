package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.UiService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServerDisconnectScreen implements Screen {
    private final ScreenManager screenManager;
    private final UiService uiService;
    private Stage stage;
    @Setter
    private String disconnectReason;
    private Skin skin;

    @Autowired
    public ServerDisconnectScreen(ScreenManager screenManager, UiService uiService) {
        this.screenManager = screenManager;
        this.uiService = uiService;
    }


    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = uiService.getSkin();

        Table mainTable = new Table(skin);
        mainTable.setFillParent(true);
        stage.addActor(mainTable);

        // Set dark semi-transparent background
        mainTable.setBackground(skin.newDrawable("white", 0.2f, 0.2f, 0.2f, 0.9f));

        // Title
        Label titleLabel = new Label(getDisconnectTitle(), skin);
        titleLabel.setAlignment(Align.center);
        mainTable.add(titleLabel).pad(40).row();

        // Message
        Label messageLabel = new Label(getDisconnectMessage(), skin);
        messageLabel.setAlignment(Align.center);
        messageLabel.setWrap(true);
        mainTable.add(messageLabel).width(400).pad(20).row();

        // Back button
        TextButton backButton = new TextButton("Back to Menu", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screenManager.showScreen(ModeSelectionScreen.class);
            }
        });
        mainTable.add(backButton).pad(40).width(200).height(50).row();

        // ESC key handler
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    screenManager.showScreen(ModeSelectionScreen.class);
                    return true;
                }
                return false;
            }
        });

        Gdx.input.setInputProcessor(stage);
    }

    private void goToModeSelection() {
        screenManager.showScreen(ModeSelectionScreen.class);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    private String getDisconnectTitle() {
        if (disconnectReason == null) {
            return "Disconnected from Server";
        }
        return switch (disconnectReason) {
            case "QUIT" -> "Left Game";
            case "KICKED" -> "Kicked from Server";
            case "SERVER_CLOSED" -> "Server Closed";
            case "TIMEOUT" -> "Connection Timed Out";
            default -> "Disconnected from Server";
        };
    }

    private String getDisconnectMessage() {
        if (disconnectReason == null) {
            return "You have been disconnected from the server.\nPlease try connecting again later.";
        }
        return switch (disconnectReason) {
            case "QUIT" -> "Thanks for playing!\nYou can rejoin the server at any time.";
            case "KICKED" -> "You were kicked from the server.\nPlease contact the server administrator.";
            case "SERVER_CLOSED" -> "The server has been shut down.\nPlease try again later.";
            case "TIMEOUT" -> "Lost connection to the server.\nPlease check your internet connection.";
            default -> "Connection to the server was lost.\nPlease try connecting again later.";
        };
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
    }
}
