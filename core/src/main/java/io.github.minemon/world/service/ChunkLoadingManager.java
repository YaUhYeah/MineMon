package io.github.minemon.world.service;

import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ChunkLoadingManager {
    private static final int MAX_CONCURRENT_REQUESTS = 12;
    private static final long REQUEST_TIMEOUT = 3000;
    private static final int IMMEDIATE_RADIUS = 4;  
    private static final int ACTIVE_RADIUS = 6;     
    private static final int PRELOAD_RADIUS = 8;    
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_RETRIES = 3;
    private final PriorityBlockingQueue<ChunkRequest> requestQueue = new PriorityBlockingQueue<>();
    private final Map<Vector2, ChunkRequestInfo> activeRequests = new ConcurrentHashMap<>();
    private final Set<Vector2> failedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Vector2, Integer> retryCount = new ConcurrentHashMap<>();
    private final Map<Vector2, Long> recentlyLoaded = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupService = Executors.newSingleThreadScheduledExecutor();
    private final Object queueLock = new Object();
    private Vector2 lastPlayerChunk = new Vector2(Integer.MAX_VALUE, Integer.MAX_VALUE);
    @Autowired
    @Lazy
    private WorldService worldService;
    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;

    public ChunkLoadingManager() {
        cleanupService.scheduleWithFixedDelay(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public void update() {
        if (!multiplayerClient.isConnected()) {
            return;
        }

        long now = System.currentTimeMillis();

        
        handleTimeouts(now);

        
        processQueue();
    }

    public void queueChunkRequest(int x, int y, boolean highPriority) {
        Vector2 pos = new Vector2(x, y);
        if (worldService.isMultiplayerMode()) {
            
            if (worldService.isChunkLoaded(pos) || activeRequests.containsKey(pos)) {
                return;
            }

            
            retryCount.remove(pos);
            failedChunks.remove(pos);

            requestQueue.offer(new ChunkRequest(
                x, y,
                highPriority ? 0 : 1,
                System.currentTimeMillis(),
                0
            ));
        }
    }

    public void markChunkComplete(int x, int y) {
        Vector2 pos = new Vector2(x, y);
        activeRequests.remove(pos);
        failedChunks.remove(pos);
        retryCount.remove(pos);
        processQueue(); 
    }

    private synchronized void processQueue() {
        while (activeRequests.size() < MAX_CONCURRENT_REQUESTS && !requestQueue.isEmpty()) {
            ChunkRequest request = requestQueue.poll();
            if (request == null) break;

            Vector2 pos = new Vector2(request.x, request.y);

            
            if (worldService.isChunkLoaded(pos)) continue;

            activeRequests.put(pos, new ChunkRequestInfo(
                System.currentTimeMillis(),
                request.priority
            ));

            multiplayerClient.requestChunk(request.x, request.y);
            log.debug("Sent chunk request for ({},{})", request.x, request.y);
        }
    }

    public void preloadChunksAroundPosition(float tileX, float tileY) {
        int centerX = (int) Math.floor(tileX / CHUNK_SIZE);
        int centerY = (int) Math.floor(tileY / CHUNK_SIZE);

        Vector2 currentChunk = new Vector2(centerX, centerY);
        if (currentChunk.equals(lastPlayerChunk)) {
            return;
        }
        lastPlayerChunk.set(currentChunk);

        
        int[][] priorities = calculatePriorities();

        synchronized (queueLock) {
            requestQueue.clear(); 

            
            for (int radius = 0; radius <= PRELOAD_RADIUS; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        if (Math.abs(dx) == radius || Math.abs(dy) == radius) {
                            int priority = priorities[dx + PRELOAD_RADIUS][dy + PRELOAD_RADIUS];
                            queueChunkRequest(centerX + dx, centerY + dy, priority == 0);
                        }
                    }
                }
            }
        }
    }

    private int[][] calculatePriorities() {
        int size = PRELOAD_RADIUS * 2 + 1;
        int[][] priorities = new int[size][size];

        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dy = -PRELOAD_RADIUS; dy <= PRELOAD_RADIUS; dy++) {
                double distance = Math.sqrt(dx * dx + dy * dy);
                int priority;

                if (distance <= IMMEDIATE_RADIUS) {
                    priority = 0;  
                } else if (distance <= ACTIVE_RADIUS) {
                    priority = 1;  
                } else {
                    priority = 2;  
                }

                priorities[dx + PRELOAD_RADIUS][dy + PRELOAD_RADIUS] = priority;
            }
        }

        return priorities;
    }


    private void handleTimeouts(long now) {
        List<Vector2> timedOut = new ArrayList<>();

        activeRequests.forEach((pos, info) -> {
            if (now - info.getStartTime() > REQUEST_TIMEOUT) {
                int currentRetries = retryCount.getOrDefault(pos, 0);

                if (currentRetries < MAX_RETRIES) {
                    
                    int newPriority = Math.max(0, info.getPriority() - 1);
                    retryCount.put(pos, currentRetries + 1);

                    requestQueue.offer(new ChunkRequest(
                        (int) pos.x, (int) pos.y,
                        newPriority,
                        now,
                        currentRetries + 1
                    ));

                    log.debug("Retrying chunk {},{} (attempt {})", pos.x, pos.y, currentRetries + 1);
                } else {
                    failedChunks.add(pos);
                    log.warn("Chunk {},{} failed after {} retries", pos.x, pos.y, MAX_RETRIES);
                }

                timedOut.add(pos);
            }
        });

        
        timedOut.forEach(activeRequests::remove);
    }

    private void cleanup() {
        recentlyLoaded.entrySet().removeIf(entry ->
            System.currentTimeMillis() - entry.getValue() > 30000
        );

        retryCount.entrySet().removeIf(entry ->
            !activeRequests.containsKey(entry.getKey())
        );
    }

    private boolean isChunkLoaded(Vector2 pos) {
        if (worldService.isChunkLoaded(pos)) {
            return true;
        }
        Long loadTime = recentlyLoaded.get(pos);
        return loadTime != null && System.currentTimeMillis() - loadTime < 30000;
    }

    public boolean isChunkInProgress(int x, int y) {
        Vector2 pos = new Vector2(x, y);
        return activeRequests.containsKey(pos);
    }

    public void dispose() {
        cleanupService.shutdown();
        try {
            cleanupService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            cleanupService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        requestQueue.clear();
        activeRequests.clear();
        recentlyLoaded.clear();
        failedChunks.clear();
    }

    @Data
    private static class ChunkRequest implements Comparable<ChunkRequest> {
        private final int x;
        private final int y;
        private final int priority;
        private final long timestamp;
        private final int retries;

        @Override
        public int compareTo(ChunkRequest o) {
            
            int priorityCompare = Integer.compare(this.priority, o.priority);
            if (priorityCompare != 0) return priorityCompare;

            
            int retryCompare = Integer.compare(o.retries, this.retries);
            if (retryCompare != 0) return retryCompare;

            
            return Long.compare(this.timestamp, o.timestamp);
        }
    }

    @Data
    private static class ChunkRequestInfo {
        private final long startTime;
        private final int priority;
    }
}
