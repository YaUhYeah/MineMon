package io.github.minemon.core.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.Gdx;
import org.springframework.stereotype.Component;

@Component
public class StyleFactory {
    private final Color TITLE_COLOR = new Color(1f, 0.95f, 0.8f, 1f);

    public Label createTitleLabel(String text, Skin skin) {
        Label label = new Label(text, skin);
        Label.LabelStyle style = new Label.LabelStyle(label.getStyle());
        style.fontColor = TITLE_COLOR;
        label.setStyle(style);
        float TITLE_SCALE = 1.8f;
        label.setFontScale(TITLE_SCALE);
        return label;
    }

    public TextButton createGameButton(String text, Skin skin) {
        TextButton button = new TextButton(text, skin);
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(button.getStyle());
        style.up = createButtonBackground();
        style.down = createButtonBackground();
        style.over = createButtonBackground();
        button.setStyle(style);
        return button;
    }

    public Drawable createPanelBackground() {
        return new TextureRegionDrawable(new Texture(Gdx.files.internal("assets/Textures/UI/window.png")));
    }

    private Drawable createButtonBackground() {
        Texture buttonTexture = new Texture(Gdx.files.internal("assets/Textures/UI/hotbar_bg.png"));
        TextureRegionDrawable drawable = new TextureRegionDrawable(buttonTexture);
        drawable.setMinWidth(150);
        drawable.setMinHeight(50);
        return drawable;
    }
}
