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

    private int runKey = Input.Keys.Z;

    public InputConfiguration() {

        movementKeys.put(Input.Keys.W, PlayerDirection.UP);
        movementKeys.put(Input.Keys.UP, PlayerDirection.UP);
        movementKeys.put(Input.Keys.S, PlayerDirection.DOWN);
        movementKeys.put(Input.Keys.DOWN, PlayerDirection.DOWN);
        movementKeys.put(Input.Keys.A, PlayerDirection.LEFT);
        movementKeys.put(Input.Keys.LEFT, PlayerDirection.LEFT);
        movementKeys.put(Input.Keys.D, PlayerDirection.RIGHT);
        movementKeys.put(Input.Keys.RIGHT, PlayerDirection.RIGHT);
    }

    public void updateKeyBinding(int oldKey, int newKey, PlayerDirection direction) {
        movementKeys.remove(oldKey);
        movementKeys.put(newKey, direction);
    }

    public PlayerDirection getDirectionForKey(int keyCode) {
        return movementKeys.get(keyCode);
    }
}