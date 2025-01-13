package io.github.minemon.input;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.core.screen.InventoryScreen;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.inventory.service.impl.ItemPickupHandler;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Setter
@RequiredArgsConstructor
public class InputService extends InputAdapter {
    private static final float TILE_SIZE = 32f;
    private final InputConfiguration inputConfig;
    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private boolean runPressed;
    private PlayerDirection lastPressedDirection = null;
    private boolean isActive = false;
    private boolean isAndroid = false;

    public void setAndroidMode(boolean android) {
        this.isAndroid = android;
    }

    public void simulateKeyPress(PlayerDirection direction) {
        if (!isActive || !isAndroid) return;

        resetKeys();
        switch (direction) {
            case UP -> upPressed = true;
            case DOWN -> downPressed = true;
            case LEFT -> leftPressed = true;
            case RIGHT -> rightPressed = true;
        }
        lastPressedDirection = direction;
    }
    private ItemPickupHandler itemPickupHandler;
    private ChatService chatService;
    private MultiplayerClient multiplayerClient;
    private PlayerService playerService;
    private InventoryScreen inventoryScreen;
    private WorldService worldService;

    public void setItemPickupHandler(ItemPickupHandler itemPickupHandler) {
        this.itemPickupHandler = itemPickupHandler;
    }

    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void setMultiplayerClient(MultiplayerClient multiplayerClient) {
        this.multiplayerClient = multiplayerClient;
    }

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setInventoryScreen(InventoryScreen inventoryScreen) {
        this.inventoryScreen = inventoryScreen;
    }

    public void setWorldService(WorldService worldService) {
        this.worldService = worldService;
    }

    public void activate() {
        isActive = true;
        resetKeys();
    }

    public void deactivate() {
        isActive = false;
        resetKeys();
    }

    public PlayerDirection getCurrentDirection() {
        if (!isActive || (inventoryScreen != null && inventoryScreen.isVisible())) {
            return null;
        }

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
        return isActive && (inventoryScreen == null || !inventoryScreen.isVisible()) && runPressed;
    }

    public void resetKeys() {
        upPressed = false;
        downPressed = false;
        leftPressed = false;
        rightPressed = false;
        runPressed = false;
        lastPressedDirection = null;
    }
    @Override
    public boolean keyDown(int keycode) {
        if (!isActive || (chatService != null && chatService.isActive())) {
            return false;
        }
        if (keycode == inputConfig.getActionKey("PICKUP")) {
            attemptPickup();
            return true;
        }

        if (keycode == inputConfig.getActionKey("INVENTORY")) {

            inventoryScreen.toggleVisibility();

            if (inventoryScreen.isVisible()) {
                resetKeys();
            }
            return true;
        }


        if (inventoryScreen.isVisible()) {
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

    private void attemptPickup() {
        if (playerService == null || worldService == null || itemPickupHandler == null) {
            return;
        }

        PlayerData pd = playerService.getPlayerData();
        if (pd == null) return;

        float playerX = pd.getX() * TILE_SIZE;
        float playerY = pd.getY() * TILE_SIZE;

        Rectangle searchArea = new Rectangle(
            playerX - TILE_SIZE * 2,
            playerY - TILE_SIZE * 2,
            TILE_SIZE * 4,
            TILE_SIZE * 4
        );

        List<WorldObject> nearbyObjects = worldService.getVisibleObjects(searchArea);
        for (WorldObject obj : nearbyObjects) {
            if (obj.getType() == ObjectType.POKEBALL) {
                if (itemPickupHandler.attemptPickup(pd, obj)) {
                    break;
                }
            }
        }
    }
    @Override
    public boolean keyUp(int keycode) {
        if (!isActive) return false;


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
            if (playerService != null && multiplayerClient != null) {
                PlayerData pd = playerService.getPlayerData();
                if (pd != null) {
                    float x = pd.getX();
                    float y = pd.getY();
                    String dirName = pd.getDirection().name().toLowerCase();
                    multiplayerClient.sendPlayerMove(x, y, false, false, dirName);
                }
            }
        }

        return true;
    }
}
