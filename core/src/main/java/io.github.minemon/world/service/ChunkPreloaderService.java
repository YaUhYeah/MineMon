package io.github.minemon.world.service;

import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChunkPreloaderService {
    // Significantly increased radius for better coverage
    private static final int VISIBLE_RADIUS = 3;
    private static final int PRELOAD_RADIUS = 5;
    private static final int URGENT_RADIUS = 2;
    private static final long CHUNK_REQUEST_TIMEOUT = 2000; // 2 seconds timeout
    private static final long URGENT_REQUEST_TIMEOUT = 1000; // 1 second for urgent chunks

    private final WorldService worldService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<Vector2, Long> chunkRequestTimes = new ConcurrentHashMap<>();
    private final Set<Vector2> preloadedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Vector2> failedRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Queue<Vector2> urgentChunkQueue = new LinkedList<>();

    @Autowired
    @Lazy
    private MultiplayerClient client;

    public ChunkPreloaderService(WorldService worldService) {
        this.worldService = worldService;
        startUrgentChunkProcessor();
    }

    private void startUrgentChunkProcessor() {
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processUrgentChunks();
                    Thread.sleep(100); // Check urgent queue every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void processUrgentChunks() {
        Vector2 urgentChunk;
        while ((urgentChunk = urgentChunkQueue.poll()) != null) {
            if (!worldService.isChunkLoaded(urgentChunk)) {
                requestChunk(urgentChunk, true);
            }
        }
    }

    public void preloadAround(float playerX, float playerY) {
        int tileX = (int) (playerX / 32);
        int tileY = (int) (playerY / 32);
        int playerChunkX = tileX / 16;
        int playerChunkY = tileY / 16;

        executor.submit(() -> {
            try {
                // First handle urgent chunks (immediately visible)
                for (int dx = -URGENT_RADIUS; dx <= URGENT_RADIUS; dx++) {
                    for (int dy = -URGENT_RADIUS; dy <= URGENT_RADIUS; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                        if (!worldService.isChunkLoaded(chunkPos)) {
                            urgentChunkQueue.offer(chunkPos);
                        }
                    }
                }

                // Then handle visible chunks
                Set<Vector2> visibleChunks = new HashSet<>();
                for (int dx = -VISIBLE_RADIUS; dx <= VISIBLE_RADIUS; dx++) {
                    for (int dy = -VISIBLE_RADIUS; dy <= VISIBLE_RADIUS; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                        visibleChunks.add(chunkPos);
                        if (!worldService.isChunkLoaded(chunkPos)) {
                            requestChunk(chunkPos, false);
                        }
                    }
                }

                // Finally handle preload chunks
                for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
                    for (int dy = -PRELOAD_RADIUS; dy <= PRELOAD_RADIUS; dy++) {
                        Vector2 chunkPos = new Vector2(playerChunkX + dx, playerChunkY + dy);
                        if (!visibleChunks.contains(chunkPos) && !worldService.isChunkLoaded(chunkPos)) {
                            requestChunk(chunkPos, false);
                        }
                    }
                }

                // Retry failed requests
                retryFailedRequests();

            } catch (Exception e) {
                log.error("Error during chunk preloading: {}", e.getMessage(), e);
            }
        });
    }

    private void requestChunk(Vector2 chunkPos, boolean urgent) {
        long now = System.currentTimeMillis();
        Long lastRequest = chunkRequestTimes.get(chunkPos);
        long timeout = urgent ? URGENT_REQUEST_TIMEOUT : CHUNK_REQUEST_TIMEOUT;

        if (lastRequest != null && now - lastRequest < timeout) {
            return;
        }

        if (worldService.isMultiplayerMode() && client != null && client.isConnected()) {
            if (!client.isPendingChunkRequest((int)chunkPos.x, (int)chunkPos.y)) {
                client.requestChunk((int)chunkPos.x, (int)chunkPos.y);
                chunkRequestTimes.put(chunkPos, now);
                if (urgent) {
                    failedRequests.add(chunkPos);
                }
            }
        } else {
            worldService.loadChunk(chunkPos);
            preloadedChunks.add(chunkPos);
        }
    }

    private void retryFailedRequests() {
        long now = System.currentTimeMillis();
        Iterator<Vector2> iterator = failedRequests.iterator();
        while (iterator.hasNext()) {
            Vector2 failedChunk = iterator.next();
            if (worldService.isChunkLoaded(failedChunk)) {
                iterator.remove();
                continue;
            }

            Long lastAttempt = chunkRequestTimes.get(failedChunk);
            if (lastAttempt == null || now - lastAttempt >= CHUNK_REQUEST_TIMEOUT) {
                requestChunk(failedChunk, true);
            }
        }
    }

    public void dispose() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        preloadedChunks.clear();
        chunkRequestTimes.clear();
        failedRequests.clear();
        urgentChunkQueue.clear();
    }
}
