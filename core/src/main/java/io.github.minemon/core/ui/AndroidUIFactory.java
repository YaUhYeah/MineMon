package io.github.minemon.core.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class AndroidUIFactory {
    
    public static TouchpadStyle createTouchpadStyle() {
        Skin skin = new Skin();
        
        // Create background circle
        Pixmap bgPixmap = new Pixmap(200, 200, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(0.5f, 0.5f, 0.5f, 0.5f);
        bgPixmap.fillCircle(100, 100, 100);
        Texture bgTexture = new Texture(bgPixmap);
        bgPixmap.dispose();
        
        // Create knob
        Pixmap knobPixmap = new Pixmap(60, 60, Pixmap.Format.RGBA8888);
        knobPixmap.setColor(0.8f, 0.8f, 0.8f, 0.8f);
        knobPixmap.fillCircle(30, 30, 30);
        Texture knobTexture = new Texture(knobPixmap);
        knobPixmap.dispose();
        
        Drawable touchBackground = new TextureRegionDrawable(new TextureRegion(bgTexture));
        Drawable touchKnob = new TextureRegionDrawable(new TextureRegion(knobTexture));
        
        TouchpadStyle touchpadStyle = new TouchpadStyle();
        touchpadStyle.background = touchBackground;
        touchpadStyle.knob = touchKnob;
        
        return touchpadStyle;
    }
}