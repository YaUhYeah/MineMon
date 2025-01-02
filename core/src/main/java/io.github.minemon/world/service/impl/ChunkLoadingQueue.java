package io.github.minemon.world.service.impl;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChunkLoadingQueue {
    private static final long CHUNK_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private final Queue<ChunkRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    private final Set<String> inProgressChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Long> chunkRequestTimes = new ConcurrentHashMap<>();

    public void queueChunkRequest(int x, int y) {
        String key = getChunkKey(x, y);
        if (!inProgressChunks.contains(key)) {
            long now = System.currentTimeMillis();
            Long lastRequest = chunkRequestTimes.get(key);

            // Check if we need to retry (timeout occurred)
            if (lastRequest != null && now - lastRequest < CHUNK_REQUEST_TIMEOUT) {
                return; // Still waiting for previous request
            }

            pendingRequests.offer(new ChunkRequest(x, y));
            inProgressChunks.add(key);
            chunkRequestTimes.put(key, now);
            log.debug("Queued chunk request for {}", key);
        }
    }

    public void markChunkComplete(int x, int y) {
        String key = getChunkKey(x, y);
        inProgressChunks.remove(key);
        chunkRequestTimes.remove(key);
        log.debug("Marked chunk {} as complete", key);
    }

    public boolean isChunkInProgress(int x, int y) {
        return inProgressChunks.contains(getChunkKey(x, y));
    }

    public void cleanupStaleRequests() {
        long now = System.currentTimeMillis();
        chunkRequestTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > CHUNK_REQUEST_TIMEOUT) {
                String key = entry.getKey();
                inProgressChunks.remove(key);
                log.debug("Cleaned up stale request for chunk {}", key);
                return true;
            }
            return false;
        });
    }

    private String getChunkKey(int x, int y) {
        return x + "," + y;
    }

    @Data
    public static class ChunkRequest {
        private final int x;
        private final int y;
        private final long timestamp = System.currentTimeMillis();
    }
}
