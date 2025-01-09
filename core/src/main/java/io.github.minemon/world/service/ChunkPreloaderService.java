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
    
    private static final int VISIBLE_RADIUS = 3;
    private static final int PRELOAD_RADIUS = 5;
    private static final int URGENT_RADIUS = 2;
    private static final long CHUNK_REQUEST_TIMEOUT = 2000; 
    private static final long URGENT_REQUEST_TIMEOUT = 1000; 

    private final WorldService worldService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<Vector2, Long> chunkRequestTimes = new ConcurrentHashMap<>();
    private final Set<Vector2> preloadedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Vector2> failedRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Queue<Vector2> urgentChunkQueue = new LinkedList<>();
    @Autowired
    @Lazy
    private ChunkLoadingManager chunkLoadingManager;
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
                    Thread.sleep(100); 
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

    private void requestChunk(Vector2 chunkPos, boolean urgent) {
        if (worldService.isMultiplayerMode() && client != null && client.isConnected()) {
            chunkLoadingManager.queueChunkRequest((int) chunkPos.x, (int) chunkPos.y, urgent);
        } else {
            
            worldService.loadChunk(chunkPos);
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
