package io.github.minemon.core.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import io.github.minemon.core.ui.DialogFactory;
import io.github.minemon.core.ui.StyleFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Getter
public class UiService {
    private static final String UI_SKIN_PATH = "Skins/uiskin.json";
    private Skin skin;
    private DialogFactory dialogFactory;
    private StyleFactory styleFactory;

    public void initialize() {
        if (skin != null) {
            return;
        }

        try {
            FileHandle skinFile = Gdx.files.internal(UI_SKIN_PATH);
            if (!skinFile.exists()) {
                log.error("Could not find skin file: {}", UI_SKIN_PATH);
                createDefaultSkin();
                return;
            }

            skin = new Skin(skinFile);
            createFactories();

        } catch (Exception e) {
            log.error("Failed to initialize UI skin: {}", e.getMessage());
            createDefaultSkin();
        }
    }

    private void createFactories() {
        if (skin != null) {
            dialogFactory = new DialogFactory(skin);
            styleFactory = new StyleFactory();
        }
    }

    private void createDefaultSkin() {
        log.info("Creating default skin");
        try {
            
            FileHandle internal = Gdx.files.internal("default.fnt");
            FileHandle bitmap = Gdx.files.internal("default.png");

            if (!internal.exists() || !bitmap.exists()) {
                log.error("Default font files not found!");
                return;
            }

            skin = new Skin();
            BitmapFont defaultFont = new BitmapFont(internal, bitmap, false);
            skin.add("default", defaultFont);

            
            TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
            textButtonStyle.font = defaultFont;
            skin.add("default", textButtonStyle);

            Label.LabelStyle labelStyle = new Label.LabelStyle();
            labelStyle.font = defaultFont;
            skin.add("default", labelStyle);

            createFactories();
            log.info("Default skin created successfully");

        } catch (Exception e) {
            log.error("Failed to create default skin", e);
            if (skin != null) {
                skin.dispose();
                skin = null;
            }
        }
    }

    public void dispose() {
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
    }
}
