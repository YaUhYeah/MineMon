package io.github.minemon.server.service.impl;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.minemon.NetworkProtocol;
import io.github.minemon.event.EventBus;
import io.github.minemon.player.event.PlayerJoinEvent;
import io.github.minemon.player.event.PlayerLeaveEvent;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.multiplayer.model.ChunkUpdate;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.server.service.MultiplayerServer;
import io.github.minemon.server.service.MultiplayerService;
import io.github.minemon.world.service.WorldService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.github.minemon.server.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Primary
@Service
public class MultiplayerServerImpl implements MultiplayerServer {

    private final MultiplayerService multiplayerService;
    private final EventBus eventBus;
    private final AuthService authService;
    private final Map<Integer, String> connectionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Map<ChunkKey, Long>> clientChunkCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanupExecutor =
        Executors.newSingleThreadScheduledExecutor();
    @Getter
    private final Set<ChunkKey> pendingChunkRequests = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> chunkRequestTimes = new ConcurrentHashMap<>();
    private final Map<String, Connection> activeUsers = new ConcurrentHashMap<>();
    private Server server;
    private volatile boolean running = false;
    @Autowired
    private WorldService worldService;

    public MultiplayerServerImpl(MultiplayerService multiplayerService,
                                 EventBus eventBus,
                                 AuthService authService) {
        this.multiplayerService = multiplayerService;
        this.eventBus = eventBus;
        this.authService = authService;
    }

    @Override
    public void startServer(int tcpPort, int udpPort) {
        if (running) {
            log.warn("Server already running.");
            return;
        }

        server = new Server();
        NetworkProtocol.registerClasses(server.getKryo());

        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                log.info("New connection: {}", connection.getRemoteAddressTCP());
            }

            @Override
            public void disconnected(Connection connection) {
                handleDisconnection(connection);
            }

            @Override
            public void received(Connection connection, Object object) {
                handleMessage(connection, object);
            }
        });

        try {
            server.start();
            server.bind(tcpPort, udpPort);
            running = true;
            log.info("Multiplayer server started on TCP:{} UDP:{}", tcpPort, udpPort);
        } catch (IOException e) {
            log.error("Failed to start server: {}", e.getMessage(), e);
        }
    }

    private void handleLogin(Connection connection, NetworkProtocol.LoginRequest req) {
        try {
            // Check if user is already logged in
            if (activeUsers.containsKey(req.getUsername())) {
                NetworkProtocol.LoginResponse resp = new NetworkProtocol.LoginResponse();
                resp.setSuccess(false);
                resp.setMessage("This user is already logged in");
                connection.sendTCP(resp);
                log.warn("Duplicate login attempt for user: {}", req.getUsername());
                return;
            }

            boolean authSuccess = authService.authenticate(req.getUsername(), req.getPassword());
            NetworkProtocol.LoginResponse resp = new NetworkProtocol.LoginResponse();

            if (!authSuccess) {
                resp.setSuccess(false);
                resp.setMessage("Invalid username or password.");
                connection.sendTCP(resp);
                log.info("Authentication failed for user: {}", req.getUsername());
                return;
            }

            // Store connection for this user
            activeUsers.put(req.getUsername(), connection);
            connectionUserMap.put(connection.getID(), req.getUsername());
            multiplayerService.playerConnected(req.getUsername());

            eventBus.fireEvent(new PlayerJoinEvent(req.getUsername()));
            PlayerData pd = multiplayerService.getPlayerData(req.getUsername());

            resp.setSuccess(true);
            resp.setUsername(req.getUsername());
            resp.setX((int) pd.getX());
            resp.setY((int) pd.getY());

            connection.sendTCP(resp);
            log.info("User '{}' logged in successfully from {}", req.getUsername(), connection.getRemoteAddressTCP());

            broadcastPlayerStates();
            sendInitialChunks(connection, pd);

        } catch (Exception e) {
            log.error("Error during login: {}", e.getMessage(), e);
            NetworkProtocol.LoginResponse resp = new NetworkProtocol.LoginResponse();
            resp.setSuccess(false);
            resp.setMessage("Internal server error occurred");
            connection.sendTCP(resp);
        }
    }

    public void handleDisconnection(Connection connection) {
        String username = connectionUserMap.remove(connection.getID());
        if (username != null) {
            activeUsers.remove(username); // Remove from active users
            multiplayerService.playerDisconnected(username);
            eventBus.fireEvent(new PlayerLeaveEvent(username));
            log.info("Player {} disconnected", username);
            broadcastPlayerStates();
        } else {
            log.info("Connection {} disconnected without a known user.", connection.getID());
        }
    }

    private void handleMessage(Connection connection, Object object) {
        if (object.getClass().getName().startsWith("com.esotericsoftware.kryonet.FrameworkMessage")) {
            return;
        }
        if (object instanceof NetworkProtocol.LoginRequest req) {
            handleLogin(connection, req);
        } else if (object instanceof NetworkProtocol.CreateUserRequest createReq) {
            handleCreateUser(connection, createReq);
        } else if (object instanceof NetworkProtocol.PlayerMoveRequest moveReq) {
            handlePlayerMove(connection, moveReq);
        } else if (object instanceof NetworkProtocol.ChunkRequest chunkReq) {
            handleChunkRequest(connection, chunkReq);
        } else if (object instanceof io.github.minemon.chat.model.ChatMessage chatMsg) {
            handleChatMessage(connection, chatMsg);
        } else {
            log.warn("Unknown message type received: {}", object.getClass());
        }
    }

    private void handleChatMessage(Connection connection, io.github.minemon.chat.model.ChatMessage msg) {
        String sender = connectionUserMap.get(connection.getID());
        if (sender == null) {
            log.warn("ChatMessage received from unregistered connection: {}", connection.getID());
            return;
        }
        msg.setSender(sender);

        log.info("Received ChatMessage from {}: {}", sender, msg.getContent());
        server.sendToAllExceptTCP(connection.getID(), msg);

    }

    private void handleCreateUser(Connection connection, NetworkProtocol.CreateUserRequest req) {
        NetworkProtocol.CreateUserResponse resp = new NetworkProtocol.CreateUserResponse();
        boolean success = authService.createUser(req.getUsername(), req.getPassword());
        if (success) {
            resp.setSuccess(true);
            resp.setMessage("User created successfully. You can now log in.");
        } else {
            resp.setSuccess(false);
            resp.setMessage("Username already exists or invalid input.");
        }
        connection.sendTCP(resp);
        log.info("User creation attempt for '{}': {}", req.getUsername(), success ? "SUCCESS" : "FAILURE");
    }
    private void handlePlayerMove(Connection connection, NetworkProtocol.PlayerMoveRequest moveReq) {
        String username = connectionUserMap.get(connection.getID());
        if (username == null) return;

        PlayerData pd = worldService.getPlayerData(username);
        if (pd == null) return;

        try {
            pd.setDirection(PlayerDirection.valueOf(moveReq.getDirection().toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid direction '{}'", moveReq.getDirection());
        }

        float oldX = pd.getX();
        float oldY = pd.getY();

        // Proposed new position (from the client)
        float newX = moveReq.getX();
        float newY = moveReq.getY();

        pd.setWantsToRun(moveReq.isRunning());

        float newXrounded = (float)Math.round(newX * 1000) / 1000f;
        float newYrounded = (float)Math.round(newY * 1000) / 1000f;

        boolean positionChanged =
            (Math.abs(oldX - newXrounded) > 0.001f) ||
                (Math.abs(oldY - newYrounded) > 0.001f);


        // Only set the position if it actually changed
        if (positionChanged) {
            pd.setX(newX);
            pd.setY(newY);
            pd.setMoving(true);
        } else {
            pd.setMoving(false);
        }

        // Persist updated PlayerData
        worldService.setPlayerData(pd);

        // Broadcast the updated state to everyone
        broadcastPlayerStates();
    }

    private void broadcastPlayerStates() {
        Map<String, PlayerSyncData> states = multiplayerService.getAllPlayerStates();
        NetworkProtocol.PlayerStatesUpdate update = new NetworkProtocol.PlayerStatesUpdate();
        update.setPlayers(states);
        broadcast(update);
    }

    private void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest req) {
        try {
            synchronized (this) { // Synchronize the entire chunk generation process
                // First try to get existing chunk
                ChunkUpdate chunk = multiplayerService.getChunkData(req.getChunkX(), req.getChunkY());

                if (chunk == null) {
                    // If chunk doesn't exist, explicitly generate it
                    worldService.loadChunk(new Vector2(req.getChunkX(), req.getChunkY()));

                    // Wait briefly for generation
                    Thread.sleep(50);

                    // Try to get the generated chunk again
                    chunk = multiplayerService.getChunkData(req.getChunkX(), req.getChunkY());

                    if (chunk == null) {
                        log.error("Critical: Failed to generate chunk ({}, {}) after explicit generation",
                            req.getChunkX(), req.getChunkY());
                        return;
                    }
                }

                // Create and send chunk data response
                NetworkProtocol.ChunkData cd = new NetworkProtocol.ChunkData();
                cd.setChunkX(req.getChunkX());
                cd.setChunkY(req.getChunkY());
                cd.setTiles(chunk.getTiles());
                cd.setObjects(chunk.getObjects());

                // Send using TCP for reliability
                connection.sendTCP(cd);

                log.debug("Successfully sent chunk data for ({},{}) to client {}",
                    req.getChunkX(), req.getChunkY(), connection.getID());
            }
        } catch (Exception e) {
            log.error("Error in chunk generation for ({},{}): {}",
                req.getChunkX(), req.getChunkY(), e.getMessage(), e);
        }
    }

    private void sendInitialChunks(Connection connection, PlayerData pd) {
        int px = (int) pd.getX();
        int py = (int) pd.getY();
        int radius = 2;
        int startX = px / 16 - radius;
        int endX = px / 16 + radius;
        int startY = py / 16 - radius;
        int endY = py / 16 + radius;

        for (int cx = startX; cx <= endX; cx++) {
            for (int cy = startY; cy <= endY; cy++) {
                ChunkUpdate chunk = multiplayerService.getChunkData(cx, cy);
                if (chunk == null) continue;

                NetworkProtocol.ChunkData cd = new NetworkProtocol.ChunkData();
                cd.setChunkX(cx);
                cd.setChunkY(cy);
                cd.setTiles(chunk.getTiles());
                cd.setObjects(chunk.getObjects());
                connection.sendTCP(cd);
            }
        }
    }

    @Override
    public void broadcast(Object message) {
        if (server != null && running) {
            server.sendToAllTCP(message);
        } else {
            log.warn("Cannot broadcast message, server not running.");
        }
    }

    @Override
    public synchronized void stopServer() {
        if (!running) {
            log.warn("Attempt to stop server that is not running.");
            return;
        }

        log.info("Beginning server shutdown sequence...");
        running = false;

        try {
            // 1. Notify clients of shutdown
            log.info("Notifying connected clients of shutdown...");
            NetworkProtocol.ServerShutdownNotice notice = new NetworkProtocol.ServerShutdownNotice();
            notice.setMessage("Server is shutting down...");
            broadcast(notice);

            // 2. Trigger disconnect events
            for (String username : connectionUserMap.values()) {
                eventBus.fireEvent(new PlayerLeaveEvent(username));
            }

            // 3. Save world state
            log.info("Saving world state...");
            worldService.saveWorldData();

            // 4. Wait briefly for clients to process shutdown
            Thread.sleep(1000);

            // 5. Clean up network resources
            if (server != null) {
                log.info("Stopping network server...");
                try {
                    server.close();
                } catch (Exception e) {
                    log.error("Error closing server: {}", e.getMessage());
                } finally {
                    server.stop();
                }
            }

            // 6. Clean up caches and executors
            log.info("Cleaning up resources...");
            cacheCleanupExecutor.shutdown();
            if (!cacheCleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheCleanupExecutor.shutdownNow();
            }

            // 7. Clear all maps and collections
            chunkRequestTimes.clear();
            pendingChunkRequests.clear();
            clientChunkCache.clear();
            connectionUserMap.clear();

            log.info("Server shutdown completed successfully");

        } catch (Exception e) {
            log.error("Error during server shutdown: {}", e.getMessage(), e);
        } finally {
            server = null;
        }
    }

    @Override
    public void processMessages(float delta) {
        multiplayerService.tick(delta);
        var objectUpdates = multiplayerService.getAllWorldObjectUpdates();
        if (!objectUpdates.isEmpty()) {
            NetworkProtocol.WorldObjectsUpdate wUpdate = new NetworkProtocol.WorldObjectsUpdate();
            wUpdate.setObjects(objectUpdates);
            broadcast(wUpdate);
        }
    }

    @Data
    @AllArgsConstructor
    private static class ChunkKey {
        private final int x;
        private final int y;
    }
}
