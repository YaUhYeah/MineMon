package io.github.minemon.multiplayer.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import io.github.minemon.NetworkProtocol;
import io.github.minemon.chat.event.ChatMessageReceivedEvent;
import io.github.minemon.chat.model.ChatMessage;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.core.screen.ServerDisconnectScreen;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.multiplayer.model.ChunkUpdate;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MultiplayerClientImpl implements MultiplayerClient {
    private final Map<String, PlayerSyncData> playerStates = new ConcurrentHashMap<>();
    private final Map<String, ChunkUpdate> loadedChunks = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private Client client;
    private boolean connected = false;
    private LoginResponseListener loginResponseListener;
    private CreateUserResponseListener createUserResponseListener;
    private Runnable pendingCreateUserRequest = null;
    private Runnable pendingLoginRequest = null;
    @Autowired
    @Lazy
    private ScreenManager screenManager;
    @Autowired
    @Lazy
    private WorldService worldService;
    @Autowired
    @Lazy
    private ChatService chatService;

    @Data
    @AllArgsConstructor
    private static class ChunkKey {
        private final int x;
        private final int y;
    }

    // Add this to class fields
    private final Set<ChunkKey> pendingChunkRequests = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isPendingChunkRequest(int chunkX, int chunkY) {
        return pendingChunkRequests.contains(new ChunkKey(chunkX, chunkY));
    }

    @Override
    public void requestChunk(int chunkX, int chunkY) {
        if (!connected) return;

        ChunkKey key = new ChunkKey(chunkX, chunkY);
        if (pendingChunkRequests.add(key)) {
            NetworkProtocol.ChunkRequest req = new NetworkProtocol.ChunkRequest();
            req.setChunkX(chunkX);
            req.setChunkY(chunkY);
            req.setTimestamp(System.currentTimeMillis());
            client.sendTCP(req);
            log.debug("Sent chunk request for ({},{})", chunkX, chunkY);
        } else {
            log.trace("Chunk request for ({},{}) already pending", chunkX, chunkY);
        }
    }

    @Autowired
    public MultiplayerClientImpl(ApplicationEventPublisher eventPublisher) { // Modify constructor
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Map<String, PlayerSyncData> getPlayerStates() {
        return playerStates;
    }

    @Override
    public void setLoginResponseListener(LoginResponseListener listener) {
        this.loginResponseListener = listener;
    }

    @Override
    public void setCreateUserResponseListener(CreateUserResponseListener listener) {
        this.createUserResponseListener = listener;
    }

    @Override
    public void connect(String serverIP, int tcpPort, int udpPort) {
        if (connected) {
            log.warn("Already connected to a server.");
            return;
        }
        client = new Client();
        NetworkProtocol.registerClasses(client.getKryo());

        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                log.info("Connected to server: {}", connection.getRemoteAddressTCP());
                connected = true;
                if (pendingLoginRequest != null) {
                    pendingLoginRequest.run();
                    pendingLoginRequest = null;
                }
                if (pendingCreateUserRequest != null) {
                    pendingCreateUserRequest.run();
                    pendingCreateUserRequest = null;
                }
            }

            @Override
            public void disconnected(Connection connection) {
                log.info("Disconnected from server: {}", connection.getRemoteAddressTCP());
                connected = false;

                playerStates.clear();
                loadedChunks.clear();

                Gdx.app.postRunnable(() -> {
                    if (loginResponseListener != null) {
                        loginResponseListener.onLoginResponse(false, "Lost connection to server.", "", 0, 0);
                    }
                    if (createUserResponseListener != null) {
                        createUserResponseListener.onCreateUserResponse(false, "Disconnected before completion.");
                    }
                });
            }


            @Override
            public void received(Connection connection, Object object) {
                handleMessage(object);
            }
        });

        try {
            client.start();
            client.connect(5000, serverIP, tcpPort, udpPort);
            log.info("Client attempting to connect to {}:{} (TCP) and {} (UDP)", serverIP, tcpPort, udpPort);
        } catch (IOException e) {
            log.error("Failed to connect to server: {}", e.getMessage(), e);
            if (loginResponseListener != null) {
                loginResponseListener.onLoginResponse(false,
                        "Connection failed: " + e.getMessage(), "", 0, 0);
            }
            if (createUserResponseListener != null) {
                createUserResponseListener.onCreateUserResponse(false,
                        "Connection failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void login(String username, String password) {
        if (!connected) {
            log.warn("Not connected to server. Cannot send login request.");
            if (loginResponseListener != null) {
                loginResponseListener.onLoginResponse(false,
                        "Not connected to server.", "", 0, 0);
            }
            return;
        }
        NetworkProtocol.LoginRequest req = new NetworkProtocol.LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setTimestamp(System.currentTimeMillis());
        client.sendTCP(req);
        log.info("Sent LoginRequest for user: {}", username);
    }

    @Override
    public void createUser(String username, String password) {
        if (!connected) {
            log.warn("Not connected to server. Cannot send create user request.");
            if (createUserResponseListener != null) {
                createUserResponseListener.onCreateUserResponse(false,
                        "Not connected to server.");
            }
            return;
        }
        NetworkProtocol.CreateUserRequest req = new NetworkProtocol.CreateUserRequest();
        req.setUsername(username);
        req.setPassword(password);
        client.sendTCP(req);
        log.info("Sent CreateUserRequest for user: {}", username);
    }


    private void handleMessage(Object object) {
        if (object.getClass().getName().startsWith("com.esotericsoftware.kryonet.FrameworkMessage")) {
            return;
        }

        if (object instanceof NetworkProtocol.LoginResponse resp) {
            log.info("Received LoginResponse: success={}, message={}", resp.isSuccess(), resp.getMessage());
            if (loginResponseListener != null) {
                loginResponseListener.onLoginResponse(
                        resp.isSuccess(),
                        resp.getMessage() != null ? resp.getMessage() : (resp.isSuccess() ? "Success" : "Failed"),
                        resp.getUsername(),
                        resp.getX(),
                        resp.getY()
                );
            }
        } else if (object instanceof NetworkProtocol.CreateUserResponse createResp) {
            log.info("Received CreateUserResponse: success={}, message={}", createResp.isSuccess(), createResp.getMessage());
            if (createUserResponseListener != null) {
                createUserResponseListener.onCreateUserResponse(
                        createResp.isSuccess(),
                        createResp.getMessage() != null ? createResp.getMessage() : (createResp.isSuccess() ? "Account created." : "Failed to create account.")
                );
            }
        }    if (object instanceof NetworkProtocol.PlayerStatesUpdate pUpdate) {
            // Compare with previous state to detect joins/leaves
            Set<String> previousPlayers = new HashSet<>(playerStates.keySet());
            Set<String> currentPlayers = new HashSet<>(pUpdate.getPlayers().keySet());

            // Find new players (joins)
            Set<String> joins = new HashSet<>(currentPlayers);
            joins.removeAll(previousPlayers);

            // Find players who left
            Set<String> leaves = new HashSet<>(previousPlayers);
            leaves.removeAll(currentPlayers);

            // Handle joins
            for (String username : joins) {
                ChatMessage joinMsg = new ChatMessage();
                joinMsg.setSender("System");
                joinMsg.setContent(username + " joined the game");
                joinMsg.setTimestamp(System.currentTimeMillis());
                joinMsg.setType(ChatMessage.Type.SYSTEM);
                chatService.handleIncomingMessage(joinMsg);
            }

            // Handle leaves
            for (String username : leaves) {
                ChatMessage leaveMsg = new ChatMessage();
                leaveMsg.setSender("System");
                leaveMsg.setContent(username + " left the game");
                leaveMsg.setTimestamp(System.currentTimeMillis());
                leaveMsg.setType(ChatMessage.Type.SYSTEM);
                chatService.handleIncomingMessage(leaveMsg);
            }
            for (Map.Entry<String, PlayerSyncData> entry : pUpdate.getPlayers().entrySet()) {
                String username = entry.getKey();
                PlayerSyncData newState = entry.getValue();
                PlayerSyncData oldState = playerStates.get(username);

                // If we have an old state, compare positions
                if (oldState != null) {
                    float dx = Math.abs(oldState.getX() - newState.getX());
                    float dy = Math.abs(oldState.getY() - newState.getY());
                    boolean posChanged = dx > 0.001f || dy > 0.001f;

                    log.debug("Player {}: pos change={}, old=({},{}), new=({},{}), moving={}",
                        username, posChanged, oldState.getX(), oldState.getY(),
                        newState.getX(), newState.getY(), newState.isMoving());

                    // Update movement based on actual position change
                    newState.setMoving(posChanged);

                    // Reset animation if we stopped moving
                    if (!posChanged && newState.isMoving()) {
                        newState.setMoving(false);
                        newState.setAnimationTime(0f);
                    }
                }

                // Update the states map with the new state
                playerStates.put(username, newState);
            }

            // Broadcast updated player list for UI
            log.debug("Total players in update: {}", pUpdate.getPlayers().size());
            playerStates.clear();
            playerStates.putAll(pUpdate.getPlayers());

        } else if (object instanceof NetworkProtocol.ChunkData chunkData) {
            ChunkKey key = new ChunkKey(chunkData.getChunkX(), chunkData.getChunkY());
            if (pendingChunkRequests.remove(key)) {
                log.debug("Received chunk data for ({},{})", chunkData.getChunkX(), chunkData.getChunkY());
                // Update world service with chunk data
                Gdx.app.postRunnable(() -> {
                    worldService.loadOrReplaceChunkData(
                            chunkData.getChunkX(),
                            chunkData.getChunkY(),
                            chunkData.getTiles(),
                            chunkData.getObjects()
                    );
                });
            } else {
                log.warn("Received unrequested chunk data for ({},{})",
                        chunkData.getChunkX(), chunkData.getChunkY());
            }
        }else if (object instanceof NetworkProtocol.WorldObjectsUpdate wObjects) {
            wObjects.getObjects().forEach(update -> {
                String key = (update.getTileX() / 16) + "," + (update.getTileY() / 16);
                ChunkUpdate cu = loadedChunks.get(key);
                if (cu != null) {
                    if (update.isRemoved()) {
                        cu.getObjects().removeIf(o -> o.getId().equals(update.getObjectId()));
                    } else {
                        boolean found = false;
                        for (WorldObject wo : cu.getObjects()) {
                            if (wo.getId().equals(update.getObjectId())) {
                                wo.setTileX(update.getTileX());
                                wo.setTileY(update.getTileY());
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            ObjectType objType = ObjectType.valueOf(update.getType());
                            WorldObject newObj = new WorldObject(
                                    update.getTileX(),
                                    update.getTileY(),
                                    objType,
                                    objType.isCollidable()
                            );
                            cu.getObjects().add(newObj);
                        }
                    }
                }
                // Update the local world service with the object changes
                Gdx.app.postRunnable(() -> {
                    worldService.updateWorldObjectState(update);
                });
            });
        } else if (object instanceof io.github.minemon.chat.model.ChatMessage chatMsg) {
            log.info("Received ChatMessage from {}: {}", chatMsg.getSender(), chatMsg.getContent());
            eventPublisher.publishEvent(new ChatMessageReceivedEvent(this, chatMsg));
        }else if (object instanceof NetworkProtocol.ServerShutdownNotice notice) {
            if (screenManager != null) {
                Gdx.app.postRunnable(() -> {
                    disconnect();  // Make sure we disconnect
                    screenManager.showScreen(ServerDisconnectScreen.class);
                });
            }
        } else {
            log.warn("Unknown message type received: {}", object.getClass().getName());
        }
    }

    private boolean checkServerConnection() {
        if (!connected || client == null) {
            log.debug("Lost connection to server");
            Gdx.app.postRunnable(() -> {
                disconnect();  // Clean disconnect
                screenManager.showScreen(ServerDisconnectScreen.class);
            });
            return false;
        }
        return true;
    }


    @Override
    public void disconnect() {
        if (connected) {
            log.info("Disconnecting from server...");
            connected = false;
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("Error closing client: {}", e.getMessage());
                }
                client.stop();
                client = null;
            }
            playerStates.clear();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void sendPlayerMove(float x, float y, boolean running, boolean moving, String direction) {
        if (!connected) return;
        NetworkProtocol.PlayerMoveRequest req = new NetworkProtocol.PlayerMoveRequest();
        req.setX(x);
        req.setY(y);
        req.setRunning(running);
        req.setMoving(moving);
        req.setDirection(direction);
        client.sendTCP(req);
    }

    @Override
    public void update(float delta) {
        // First check connection
        if (!checkServerConnection()) {
            return;
        }

        // Update player states
        for (PlayerSyncData psd : playerStates.values()) {
            boolean directionChanged = !psd.getDirection().equals(psd.getLastDirection());
            boolean movementChanged = (psd.isMoving() != psd.isWasMoving());

            if (directionChanged || movementChanged) {
                psd.setAnimationTime(0f);
            } else if (psd.isMoving()) {
                psd.setAnimationTime(psd.getAnimationTime() + delta);
            }

            psd.setWasMoving(psd.isMoving());
            psd.setLastDirection(psd.getDirection());
        }
    }
    @Override
    public void sendMessage(Object msg) {
        if (!connected) return;
        client.sendTCP(msg);
    }

    @Override
    public void setPendingLoginRequest(Runnable action) {
        this.pendingLoginRequest = action;
    }

    @Override
    public void setPendingCreateUserRequest(Runnable action) {
        this.pendingCreateUserRequest = action;
    }
}
