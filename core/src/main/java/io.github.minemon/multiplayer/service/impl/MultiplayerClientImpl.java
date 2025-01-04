package io.github.minemon.multiplayer.service.impl;

import com.badlogic.gdx.Gdx;
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
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.ChunkLoadingManager;
import io.github.minemon.world.service.WorldService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
public class MultiplayerClientImpl implements MultiplayerClient {
    // -----------------------
    // Constants & Collections
    // -----------------------
    private static final int MAX_CONCURRENT_REQUESTS = 8;
    private static final int BATCH_SIZE = 8;
    private static final long BATCH_DELAY = 50;
    private static final long CHUNK_REQUEST_TIMEOUT = 5000;

    private final Map<ChunkKey, ChunkBuffer> chunkBuffers = new ConcurrentHashMap<>();
    private final Map<String, PlayerSyncData> playerStates = new ConcurrentHashMap<>();
    private final Map<String, ChunkUpdate> loadedChunks = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    // This set tracks chunk requests currently in flight.
    private final Set<ChunkKey> pendingChunkRequests = ConcurrentHashMap.newKeySet();
    private final Set<String> processedLeaves = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // This set tracks chunk requests being processed in the "batch" logic.
    private final Set<ChunkKey> pendingRequests = ConcurrentHashMap.newKeySet();

    // IMPORTANT: Now store NetworkProtocol.ChunkRequest instead of the local one.
    private final Queue<NetworkProtocol.ChunkRequest> chunkQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> chunkRequestTimes = new ConcurrentHashMap<>();

    // If you also have a separate queue for normal vs. urgent, unify them if needed:
    private final Queue<NetworkProtocol.ChunkRequest> chunkRequestQueue = new ConcurrentLinkedQueue<>();

    // ------------
    // KryoNet Client
    // ------------
    private Client client;
    private boolean connected = false;

    // ------------
    // Listeners
    // ------------
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
    @Autowired
    @Lazy
    private PlayerService playerService;
    @Autowired
    @Lazy
    private ChunkLoadingManager chunkLoadingManager;

    @Autowired
    public MultiplayerClientImpl(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------
    //    1) Public method to request a chunk with NetworkProtocol.ChunkRequest
    // ---------------------------------------------------------------

    @Override
    public void requestChunk(int chunkX, int chunkY) {
        if (!isConnected()) {
            log.warn("Cannot request chunk - not connected to server");
            return;
        }

        NetworkProtocol.ChunkRequest req = new NetworkProtocol.ChunkRequest();
        req.setChunkX(chunkX);
        req.setChunkY(chunkY);
        req.setTimestamp(System.currentTimeMillis());

        client.sendTCP(req);
        log.debug("Sent chunk request to server for ({},{})", chunkX, chunkY);
    }
    @Override
    public boolean isPendingChunkRequest(int chunkX, int chunkY) {
        return pendingChunkRequests.contains(new ChunkKey(chunkX, chunkY));
    }

    // ---------------------------------------------------------------
    //    2) Moves the next batch of queued requests into flight
    // ---------------------------------------------------------------
    private void processPendingChunks() {
        if (pendingRequests.size() >= BATCH_SIZE) {
            return;
        }

        int space = BATCH_SIZE - pendingRequests.size();
        for (int i = 0; i < space; i++) {
            NetworkProtocol.ChunkRequest request = chunkQueue.poll();
            if (request == null) break; // no more to process

            ChunkKey key = new ChunkKey(request.getChunkX(), request.getChunkY());
            if (!pendingRequests.contains(key)) {
                sendChunkRequest(request);
                pendingRequests.add(key);
            }
        }
    }

    // ---------------------------------------------------------------
    //    3) Actually sends the chunk request to the server
    // ---------------------------------------------------------------
    private void sendChunkRequest(NetworkProtocol.ChunkRequest request) {
        // Optionally update the timestamp
        request.setTimestamp(System.currentTimeMillis());

        client.sendTCP(request);
        log.debug("Sent chunk request for ({},{})", request.getChunkX(), request.getChunkY());
    }

    // ---------------------------------------------------------------
    //    4) For stale or additional chunk requests, we do the same
    // ---------------------------------------------------------------
    private void handleChunkRequest(int chunkX, int chunkY) {
        ChunkKey key = new ChunkKey(chunkX, chunkY);
        if (!pendingChunkRequests.contains(key)) {
            if (pendingChunkRequests.size() < MAX_CONCURRENT_REQUESTS) {
                // Rebuild the request from the actual protocol class
                NetworkProtocol.ChunkRequest req = new NetworkProtocol.ChunkRequest();
                req.setChunkX(chunkX);
                req.setChunkY(chunkY);
                req.setTimestamp(System.currentTimeMillis());

                sendChunkRequest(req);
                pendingChunkRequests.add(key);
                chunkRequestTimes.put(key.toString(), System.currentTimeMillis());
            } else {
                // Defer if too many requests are in flight:
                NetworkProtocol.ChunkRequest laterReq = new NetworkProtocol.ChunkRequest();
                laterReq.setChunkX(chunkX);
                laterReq.setChunkY(chunkY);
                laterReq.setTimestamp(System.currentTimeMillis());

                chunkRequestQueue.offer(laterReq);
            }
        }
    }

    // ---------------------------------------------------------------
    //    5) Connect, login, creation, etc.
    // ---------------------------------------------------------------
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

                if (!worldService.isMultiplayerMode()) {
                    return;
                }
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
                loginResponseListener.onLoginResponse(false, "Not connected to server.", "", 0, 0);
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
                createUserResponseListener.onCreateUserResponse(false, "Not connected to server.");
            }
            return;
        }
        NetworkProtocol.CreateUserRequest req = new NetworkProtocol.CreateUserRequest();
        req.setUsername(username);
        req.setPassword(password);
        client.sendTCP(req);
        log.info("Sent CreateUserRequest for user: {}", username);
    }

    // ---------------------------------------------------------------
    //    6) Clean up chunk requests, handle partial or full data
    // ---------------------------------------------------------------
    private void handleChunkData(NetworkProtocol.ChunkData chunkData) {
        ChunkKey key = new ChunkKey(chunkData.getChunkX(), chunkData.getChunkY());

        log.debug("Received chunk data for ({},{})", chunkData.getChunkX(), chunkData.getChunkY());

        // Update the world data
        worldService.loadOrReplaceChunkData(
            chunkData.getChunkX(),
            chunkData.getChunkY(),
            chunkData.getTiles(),
            chunkData.getObjects()
        );

        // Mark chunk as complete in loading manager
        chunkLoadingManager.markChunkComplete(chunkData.getChunkX(), chunkData.getChunkY());
    }
    private void processChunkQueue() {
        while (pendingChunkRequests.size() < MAX_CONCURRENT_REQUESTS && !chunkRequestQueue.isEmpty()) {
            NetworkProtocol.ChunkRequest request = chunkRequestQueue.poll();
            if (request != null) {
                handleChunkRequest(request.getChunkX(), request.getChunkY());
            }
        }
    }

    @Override
    public void clearPendingChunkRequests() {
        pendingChunkRequests.clear();
        chunkBuffers.clear();
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
                    resp.getMessage() != null ? resp.getMessage()
                        : (resp.isSuccess() ? "Success" : "Failed"),
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
                    createResp.getMessage() != null ? createResp.getMessage()
                        : (createResp.isSuccess() ? "Account created." : "Failed to create account.")
                );
            }
        } else if (object instanceof NetworkProtocol.PlayerStatesUpdate pUpdate) {
            handlePlayerStatesUpdate(pUpdate);
        } else if (object instanceof NetworkProtocol.ChunkData cData) {
            handleChunkData(cData);
            chunkLoadingManager.markChunkComplete(cData.getChunkX(), cData.getChunkY());
        } else if (object instanceof NetworkProtocol.ChunkRequestAck ack) {
            String key = ack.getChunkX() + "," + ack.getChunkY();
            pendingChunkRequests.remove(key);
        } else if (object instanceof NetworkProtocol.WorldObjectsUpdate wObjects) {
            handleWorldObjectsUpdate(wObjects);
        } else if (object instanceof ChatMessage chatMsg) {
            log.info("Received ChatMessage from {}: {}", chatMsg.getSender(), chatMsg.getContent());
            eventPublisher.publishEvent(new ChatMessageReceivedEvent(this, chatMsg));
        } else if (object instanceof NetworkProtocol.ServerShutdownNotice notice) {
            if (screenManager != null) {
                Gdx.app.postRunnable(() -> {
                    disconnect();
                    screenManager.showScreen(ServerDisconnectScreen.class);
                });
            }
        } else {
            log.warn("Unknown message type received: {}", object.getClass().getName());
        }
    }

    private void handlePlayerStatesUpdate(NetworkProtocol.PlayerStatesUpdate pUpdate) {
        // Compare with previous state to detect joins/leaves
        Set<String> previousPlayers = new HashSet<>(playerStates.keySet());
        Set<String> currentPlayers = new HashSet<>(pUpdate.getPlayers().keySet());

        // Find disconnected players
        Set<String> leaves = new HashSet<>(previousPlayers);
        leaves.removeAll(currentPlayers);

        // Handle player leaves
        for (String username : leaves) {
            if (!processedLeaves.contains(username)) {
                handlePlayerLeave(username);
                processedLeaves.add(username);
            }
        }

        // Clear processed leaves for players that rejoin
        processedLeaves.removeIf(currentPlayers::contains);

        // Find new players
        Set<String> joins = new HashSet<>(currentPlayers);
        joins.removeAll(previousPlayers);

        // Handle new joins
        for (String username : joins) {
            handlePlayerJoin(username);
        }

        // Update all player states
        updatePlayerStates(pUpdate.getPlayers());

        log.debug("Updated player states. Total players: {}", playerStates.size());
    }

    private void handleWorldObjectsUpdate(NetworkProtocol.WorldObjectsUpdate wObjects) {
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
    }

    private void handlePlayerLeave(String username) {
        playerStates.remove(username);

        // Send single leave message
        ChatMessage leaveMsg = new ChatMessage();
        leaveMsg.setSender("System");
        leaveMsg.setContent(username + " left the game");
        leaveMsg.setTimestamp(System.currentTimeMillis());
        leaveMsg.setType(ChatMessage.Type.SYSTEM);
        chatService.handleIncomingMessage(leaveMsg);
    }

    private void handlePlayerJoin(String username) {
        ChatMessage joinMsg = new ChatMessage();
        joinMsg.setSender("System");
        joinMsg.setContent(username + " joined the game");
        joinMsg.setTimestamp(System.currentTimeMillis());
        joinMsg.setType(ChatMessage.Type.SYSTEM);
        chatService.handleIncomingMessage(joinMsg);
    }

    private void updatePlayerStates(Map<String, PlayerSyncData> newStates) {
        String localUsername = playerService.getPlayerData().getUsername();

        for (Map.Entry<String, PlayerSyncData> entry : newStates.entrySet()) {
            String username = entry.getKey();
            PlayerSyncData newState = entry.getValue();
            PlayerSyncData oldState = playerStates.get(username);

            // Skip local player's movement state
            if (username.equals(localUsername)) {
                playerStates.put(username, newState);
                continue;
            }

            // For remote players, detect actual movement
            if (oldState != null) {
                float dx = Math.abs(oldState.getX() - newState.getX());
                float dy = Math.abs(oldState.getY() - newState.getY());
                boolean actuallyMoving = dx > 0.001f || dy > 0.001f;

                if (actuallyMoving) {
                    newState.setMoving(true);
                    if (oldState.isMoving()) {
                        newState.setAnimationTime(oldState.getAnimationTime());
                    }
                } else {
                    newState.setMoving(newState.isMoving());
                    if (newState.isMoving()) {
                        newState.setAnimationTime(oldState.getAnimationTime());
                    } else {
                        newState.setAnimationTime(0f);
                    }
                }
            }
            playerStates.put(username, newState);
        }
    }

    // ---------------------------------------------------------------
    //    7) Stale request cleanup
    // ---------------------------------------------------------------
    private void cleanupStaleRequests() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = chunkRequestTimes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > CHUNK_REQUEST_TIMEOUT) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                ChunkKey key = new ChunkKey(x, y);
                pendingChunkRequests.remove(key);
                it.remove();

                // Requeue failed request
                NetworkProtocol.ChunkRequest retry = new NetworkProtocol.ChunkRequest();
                retry.setChunkX(x);
                retry.setChunkY(y);
                retry.setTimestamp(now);

                chunkRequestQueue.offer(retry);
            }
        }
    }

    // ---------------------------------------------------------------
    //    8) Helper for verifying server connection
    // ---------------------------------------------------------------
    private boolean checkServerConnection() {
        if (!connected || client == null) {
            log.debug("Lost connection to server");
            Gdx.app.postRunnable(() -> {
                disconnect();
                screenManager.showScreen(ServerDisconnectScreen.class);
            });
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------
    //    9) Required MultiplayerClient interface methods
    // ---------------------------------------------------------------
    @Override
    public void disconnect() {
        if (connected) {
            log.info("Disconnecting from server...");
            connected = false;
            clearPendingChunkRequests();
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("Error closing client: {}", e.getMessage());
                }
                client.stop();
                client = null;
            }
            // Clear all state on disconnect
            playerStates.clear();
            processedLeaves.clear();
        }
    }

    @Override
    public boolean isConnected() {
        return connected && worldService.isMultiplayerMode();
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
        if (!worldService.isMultiplayerMode()) {
            return;
        }
        if (!checkServerConnection()) {
            return;
        }

        // Process chunk queue
        processChunkQueue();

        // Clean up stale requests
        cleanupStaleRequests();

        String localUsername = playerService.getPlayerData().getUsername();
        for (Map.Entry<String, PlayerSyncData> entry : playerStates.entrySet()) {
            String username = entry.getKey();
            PlayerSyncData psd = entry.getValue();

            // Skip local player animation updates
            if (username.equals(localUsername)) {
                continue;
            }
            // Update animation time only if the player is moving
            if (psd.isMoving()) {
                psd.setAnimationTime(psd.getAnimationTime() + delta);
            }
            // Track previous state
            psd.setWasMoving(psd.isMoving());
            psd.setLastDirection(psd.getDirection());
        }
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

    // ---------------------------------------------------------------
    //    10) Internal support classes
    // ---------------------------------------------------------------
    @Data
    static class ChunkKey {
        private final int x;
        private final int y;
    }

    @Data
    static class ChunkBuffer {
        private final int[][] tiles;
        private final List<WorldObject> objects = new ArrayList<>();
        private final int totalParts;
        private int receivedParts = 0;
    }
}
