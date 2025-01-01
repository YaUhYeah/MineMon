package io.github.minemon.core.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.minemon.core.ui.DialogFactory;
import io.github.minemon.core.ui.StyleFactory;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
public class UiService {
    private static final String UI_SKIN_PATH = "Skins/uiskin.json";
    private Skin skin;
    private DialogFactory dialogFactory;
    private StyleFactory styleFactory;

    public void initialize() {
        if (skin != null) {
            return;
        }

        skin = new Skin(Gdx.files.internal(UI_SKIN_PATH));
        dialogFactory = new DialogFactory(skin);
        styleFactory = new StyleFactory();
    }

    public void dispose() {
        if (skin != null) {
            skin.dispose();
            skin = null;
        }
    }
}
