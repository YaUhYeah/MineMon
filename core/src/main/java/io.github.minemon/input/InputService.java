package io.github.minemon.input;

import com.badlogic.gdx.InputAdapter;
import io.github.minemon.player.model.PlayerDirection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InputService extends InputAdapter {

    private final InputConfiguration inputConfig;

    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private boolean runPressed;

    private PlayerDirection lastPressedDirection = null;

    /**
     * Returns the current direction, favoring the last pressed direction if multiple are pressed.
     */
    public PlayerDirection getCurrentDirection() {
        if (lastPressedDirection != null) {
            switch (lastPressedDirection) {
                case UP -> { if (upPressed) return PlayerDirection.UP; }
                case DOWN -> { if (downPressed) return PlayerDirection.DOWN; }
                case LEFT -> { if (leftPressed) return PlayerDirection.LEFT; }
                case RIGHT -> { if (rightPressed) return PlayerDirection.RIGHT; }
            }
        }
        if (upPressed) return PlayerDirection.UP;
        if (downPressed) return PlayerDirection.DOWN;
        if (leftPressed) return PlayerDirection.LEFT;
        if (rightPressed) return PlayerDirection.RIGHT;
        return null;
    }

    public boolean isRunning() {
        return runPressed;
    }

    @Override
    public boolean keyDown(int keycode) {
        PlayerDirection dir = inputConfig.getDirectionForKey(keycode);
        if (dir != null) {
            switch (dir) {
                case UP -> upPressed = true;
                case DOWN -> downPressed = true;
                case LEFT -> leftPressed = true;
                case RIGHT -> rightPressed = true;
            }
            lastPressedDirection = dir;
            return true;
        }

        if (keycode == inputConfig.getRunKey()) {
            runPressed = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        PlayerDirection dir = inputConfig.getDirectionForKey(keycode);
        if (dir != null) {
            switch (dir) {
                case UP -> upPressed = false;
                case DOWN -> downPressed = false;
                case LEFT -> leftPressed = false;
                case RIGHT -> rightPressed = false;
            }
            if (dir == lastPressedDirection) {
                lastPressedDirection = null;

                if (upPressed) lastPressedDirection = PlayerDirection.UP;
                else if (downPressed) lastPressedDirection = PlayerDirection.DOWN;
                else if (leftPressed) lastPressedDirection = PlayerDirection.LEFT;
                else if (rightPressed) lastPressedDirection = PlayerDirection.RIGHT;
            }
            return true;
        }

        if (keycode == inputConfig.getRunKey()) {
            runPressed = false;
            return true;
        }

        return false;
    }
}
