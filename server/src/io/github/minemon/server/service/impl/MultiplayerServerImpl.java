package io.github.minemon.server.service.impl;

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
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldService;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.github.minemon.server.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Primary
@Service
public class MultiplayerServerImpl implements MultiplayerServer {

    private static final int MAX_CONCURRENT_CHUNK_REQUESTS = 4;
    private static final long CHUNK_REQUEST_TIMEOUT = 5000; 
    private static final long CHUNK_SEND_DELAY = 50L; 
    private static final int MAX_CONCURRENT_CHUNK_GEN = 8;
    private final MultiplayerService multiplayerService;
    private final EventBus eventBus;
    private final AuthService authService;
    private final Map<Integer, String> connectionUserMap = new ConcurrentHashMap<>();
    private final Map<String, Connection> activeUsers = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<NetworkProtocol.ChunkRequest> chunkRequestQueue = new PriorityBlockingQueue<>();
    private final ExecutorService chunkExecutor;
    private final Map<String, Map<ChunkKey, Long>> clientChunkCache = new ConcurrentHashMap<>();
    private final ExecutorService chunkGenExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_CHUNK_GEN);
    private final Map<ChunkKey, Set<Connection>> pendingChunkRequests = new ConcurrentHashMap<>();
    private final Object chunkLock = new Object();
    @Getter
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
        this.chunkExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_CHUNK_REQUESTS);
    }

    @Override
    public void startServer(int tcpPort, int udpPort) {
        if (running) {
            log.warn("Server already running.");
            return;
        }


        server = new Server(1024 * 1024, 1024 * 1024);
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
            activeUsers.remove(username); 
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
            log.info("Server received ChunkRequest for {},{}", chunkReq.getChunkX(), chunkReq.getChunkY());
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
            return;
        }

        float oldX = pd.getX();
        float oldY = pd.getY();

        float newX = moveReq.getX();
        float newY = moveReq.getY();

        pd.setWantsToRun(moveReq.isRunning());

        float newXrounded = (float) Math.round(newX * 1000) / 1000f;
        float newYrounded = (float) Math.round(newY * 1000) / 1000f;

        boolean positionChanged =
            (Math.abs(oldX - newXrounded) > 0.001f) ||
                (Math.abs(oldY - newYrounded) > 0.001f);

        pd.setMoving(positionChanged);

        if (positionChanged) {
            pd.setX(newX);
            pd.setY(newY);
        }

        worldService.setPlayerData(pd);
        broadcastPlayerStates();
    }

    private void broadcastPlayerStates() {
        Map<String, PlayerSyncData> states = multiplayerService.getAllPlayerStates();
        NetworkProtocol.PlayerStatesUpdate update = new NetworkProtocol.PlayerStatesUpdate();
        update.setPlayers(states);
        broadcast(update);
    }

    private void handleChunkRequest(Connection connection, NetworkProtocol.ChunkRequest req) {
        ChunkKey key = new ChunkKey(req.getChunkX(), req.getChunkY());

        synchronized (chunkLock) {
            
            pendingChunkRequests.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(connection);

            
            if (pendingChunkRequests.get(key).size() == 1) {
                chunkGenExecutor.submit(() -> {
                    try {
                        
                        ChunkData chunk = worldService.loadOrGenerateChunk(req.getChunkX(), req.getChunkY());
                        if (chunk == null) {
                            log.error("Failed to generate chunk {},{}", req.getChunkX(), req.getChunkY());
                            return;
                        }

                        
                        synchronized (chunkLock) {
                            Set<Connection> waitingConnections = pendingChunkRequests.get(key);
                            if (waitingConnections != null) {
                                NetworkProtocol.ChunkData response = new NetworkProtocol.ChunkData();
                                response.setChunkX(chunk.getChunkX());
                                response.setChunkY(chunk.getChunkY());
                                response.setTiles(chunk.getTiles());
                                response.setObjects(chunk.getObjects());

                                for (Connection conn : waitingConnections) {
                                    conn.sendTCP(response);

                                    
                                    NetworkProtocol.ChunkRequestAck ack = new NetworkProtocol.ChunkRequestAck();
                                    ack.setChunkX(req.getChunkX());
                                    ack.setChunkY(req.getChunkY());
                                    conn.sendTCP(ack);
                                }
                                pendingChunkRequests.remove(key);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing chunk request {},{}: {}",
                            req.getChunkX(), req.getChunkY(), e.getMessage(), e);
                    }
                });
            }
        }
    }

    @PreDestroy
    public void destroy() {
        chunkGenExecutor.shutdown();
        try {
            if (!chunkGenExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                chunkGenExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkGenExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        stopServer();
    }

    private boolean isLargeChunk(ChunkData chunk) {
        
        int estimatedSize = estimateChunkSize(chunk);
        return false; 
    }

    private int estimateChunkSize(ChunkData chunk) {
        
        int size = 16; 

        
        if (chunk.getTiles() != null) {
            size += 16 * 16 * 4; 
        }

        
        if (chunk.getObjects() != null) {
            for (WorldObject obj : chunk.getObjects()) {
                
                size += 32; 

                
                if (obj.getId() != null) {
                    size += 36; 
                }

                
                if (obj.getType() != null) {
                    size += obj.getType().name().length() * 2; 
                }
            }
        }

        return size;
    }

    private void sendChunkInParts(Connection connection, ChunkData chunk) {
        
        NetworkProtocol.ChunkData tilesPacket = new NetworkProtocol.ChunkData();
        tilesPacket.setChunkX(chunk.getChunkX());
        tilesPacket.setChunkY(chunk.getChunkY());
        tilesPacket.setTiles(chunk.getTiles());
        tilesPacket.setPartial(true);
        tilesPacket.setPartNumber(0);
        tilesPacket.setTotalParts(2);
        connection.sendTCP(tilesPacket);

        
        try {
            Thread.sleep(CHUNK_SEND_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        
        NetworkProtocol.ChunkData objectsPacket = new NetworkProtocol.ChunkData();
        objectsPacket.setChunkX(chunk.getChunkX());
        objectsPacket.setChunkY(chunk.getChunkY());
        objectsPacket.setObjects(chunk.getObjects());
        objectsPacket.setPartial(true);
        objectsPacket.setPartNumber(1);
        objectsPacket.setTotalParts(2);
        connection.sendTCP(objectsPacket);
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
            return;
        }

        running = false;
        log.info("Shutting down server...");

        try {
            
            NetworkProtocol.ServerShutdownNotice notice = new NetworkProtocol.ServerShutdownNotice();
            notice.setMessage("Server is shutting down...");
            notice.setReason(NetworkProtocol.ServerShutdownNotice.ShutdownReason.NORMAL_SHUTDOWN);
            broadcast(notice);

            
            Thread.sleep(1000);

            
            if (server != null) {
                for (Connection conn : server.getConnections()) {
                    conn.close();
                }
                server.stop();
                server.close();
            }

            
            chunkExecutor.shutdown();
            if (!chunkExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                chunkExecutor.shutdownNow();
            }

            
            pendingChunkRequests.clear();
            chunkRequestQueue.clear();
            connectionUserMap.clear();
            activeUsers.clear();

            log.info("Server shutdown complete");

        } catch (Exception e) {
            log.error("Error during server shutdown: {}", e.getMessage());
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
