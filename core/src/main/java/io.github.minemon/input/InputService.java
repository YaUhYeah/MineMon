package io.github.minemon.input;

import com.badlogic.gdx.InputAdapter;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InputService extends InputAdapter {

    private final InputConfiguration inputConfig;

    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private boolean runPressed;

    private PlayerDirection lastPressedDirection = null;
    @Autowired
    @Lazy
    private ChatService chatService;
    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;
    @Autowired
    @Lazy
    private PlayerService playerService;

    /**
     * Returns the current direction, favoring the last pressed direction if multiple are pressed.
     */
    public PlayerDirection getCurrentDirection() {
        if (lastPressedDirection != null) {
            switch (lastPressedDirection) {
                case UP -> {
                    if (upPressed) return PlayerDirection.UP;
                }
                case DOWN -> {
                    if (downPressed) return PlayerDirection.DOWN;
                }
                case LEFT -> {
                    if (leftPressed) return PlayerDirection.LEFT;
                }
                case RIGHT -> {
                    if (rightPressed) return PlayerDirection.RIGHT;
                }
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
        if (chatService.isActive()) {
            return false;
        }
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
            lastPressedDirection = null;
        }

        if (keycode == inputConfig.getRunKey()) {
            runPressed = false;
        }

        if (!upPressed && !downPressed && !leftPressed && !rightPressed) {
            PlayerData pd = playerService.getPlayerData();
            float x = pd.getX();
            float y = pd.getY();
            String dirName = pd.getDirection().name().toLowerCase();

            // Send "stop" => running = false, moving = false
            multiplayerClient.sendPlayerMove(x, y, false, false, dirName);
        }

        return true;
    }

}
