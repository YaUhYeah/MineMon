package io.github.minemon.input;

import com.badlogic.gdx.Input;
import io.github.minemon.player.model.PlayerDirection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Component
public class InputConfiguration {
    private Map<Integer, PlayerDirection> movementKeys = new HashMap<>();
    private Map<String, Integer> actionKeys = new HashMap<>();
    private int runKey = Input.Keys.Z;
    public static final int HOTBAR_START = Input.Keys.NUM_1;
    public static final int HOTBAR_END = Input.Keys.NUM_9;

    public InputConfiguration() {
        // Movement keys
        movementKeys.put(Input.Keys.W, PlayerDirection.UP);
        movementKeys.put(Input.Keys.UP, PlayerDirection.UP);
        movementKeys.put(Input.Keys.S, PlayerDirection.DOWN);
        movementKeys.put(Input.Keys.DOWN, PlayerDirection.DOWN);
        movementKeys.put(Input.Keys.A, PlayerDirection.LEFT);
        movementKeys.put(Input.Keys.LEFT, PlayerDirection.LEFT);
        movementKeys.put(Input.Keys.D, PlayerDirection.RIGHT);
        movementKeys.put(Input.Keys.RIGHT, PlayerDirection.RIGHT);

        // Action keys
        actionKeys.put("INVENTORY", Input.Keys.E);
    }

    public PlayerDirection getDirectionForKey(int keyCode) {
        return movementKeys.get(keyCode);
    }

    public void updateActionKey(String action, int keycode) {
        actionKeys.put(action, keycode);
    }

    public int getActionKey(String action) {
        return actionKeys.getOrDefault(action, Input.Keys.UNKNOWN);
    }
}
