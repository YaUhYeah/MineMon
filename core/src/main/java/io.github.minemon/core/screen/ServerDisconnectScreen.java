package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.UiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServerDisconnectScreen implements Screen {
    private final ScreenManager screenManager;
    private final UiService uiService;
    private Stage stage;
    private Skin skin;

    @Autowired
    public ServerDisconnectScreen(ScreenManager screenManager, UiService uiService) {
        this.screenManager = screenManager;
        this.uiService = uiService;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = uiService.getSkin();

        Table mainTable = new Table(skin);
        mainTable.setFillParent(true);
        stage.addActor(mainTable);

        // Add panel background
        mainTable.setBackground(skin.newDrawable("white", 0.2f, 0.2f, 0.2f, 0.9f));

        // Title
        Label titleLabel = new Label("Server Disconnected", skin, "title");
        titleLabel.setAlignment(Align.center);
        mainTable.add(titleLabel).pad(40).row();

        // Message
        Label messageLabel = new Label("The server has stopped or you were disconnected.\nPlease try connecting again later.", skin);
        messageLabel.setAlignment(Align.center);
        messageLabel.setWrap(true);
        mainTable.add(messageLabel).width(400).pad(20).row();

        // Back button
        TextButton backButton = new TextButton("Back to Login", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screenManager.showScreen(LoginScreen.class);
            }
        });
        mainTable.add(backButton).pad(40).width(200).height(50).row();
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
