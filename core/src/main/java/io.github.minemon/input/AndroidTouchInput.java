package io.github.minemon.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import io.github.minemon.player.model.PlayerDirection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AndroidTouchInput extends InputAdapter {
    private static final float DEADZONE = 0.2f;
    private static final float VIRTUAL_PAD_SIZE = 200f;
    
    private final InputService inputService;
    private Touchpad touchpad;
    private Stage stage;
    @Getter
    private boolean enabled = false;
    private Vector3 touchPoint = new Vector3();
    private Vector2 lastKnownDirection = new Vector2();

    public void initialize(TouchpadStyle touchpadStyle) {
        touchpad = new Touchpad(10, touchpadStyle);
        touchpad.setBounds(50, 50, VIRTUAL_PAD_SIZE, VIRTUAL_PAD_SIZE);
        
        stage = new Stage();
        stage.addActor(touchpad);
        
        Gdx.input.setInputProcessor(stage);
        enabled = true;
    }

    public void update() {
        if (!enabled || stage == null) return;
        
        stage.act(Gdx.graphics.getDeltaTime());
        
        float knobX = touchpad.getKnobPercentX();
        float knobY = touchpad.getKnobPercentY();
        
        if (Math.abs(knobX) < DEADZONE && Math.abs(knobY) < DEADZONE) {
            inputService.resetKeys();
            return;
        }

        lastKnownDirection.set(knobX, knobY);
        
        // Determine primary direction
        if (Math.abs(knobX) > Math.abs(knobY)) {
            if (knobX > 0) {
                inputService.simulateKeyPress(PlayerDirection.RIGHT);
            } else {
                inputService.simulateKeyPress(PlayerDirection.LEFT);
            }
        } else {
            if (knobY > 0) {
                inputService.simulateKeyPress(PlayerDirection.UP);
            } else {
                inputService.simulateKeyPress(PlayerDirection.DOWN);
            }
        }
    }

    public void render() {
        if (enabled && stage != null) {
            stage.draw();
        }
    }

    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
    }
}