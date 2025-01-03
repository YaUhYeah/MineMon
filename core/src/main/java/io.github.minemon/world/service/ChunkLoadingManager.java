package io.github.minemon.world.service;

import io.github.minemon.multiplayer.service.MultiplayerClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

@Service
public class ChunkLoadingManager {
    private static final int MAX_CONCURRENT_REQUESTS = 4;
    private static final int REQUEST_TIMEOUT_MS = 5000;

    private final Map<ChunkKey, Long> pendingRequests = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ChunkRequest> requestQueue = new PriorityBlockingQueue<>();

    @Autowired
    private MultiplayerClient multiplayerClient;

    public void queueChunkRequest(int x, int y, boolean highPriority) {
        ChunkKey key = new ChunkKey(x, y);

        // Don't queue if already pending
        if (pendingRequests.containsKey(key)) {
            return;
        }

        // Check if we can make new requests
        if (pendingRequests.size() >= MAX_CONCURRENT_REQUESTS) {
            // Only add high priority requests when at limit
            if (highPriority) {
                requestQueue.offer(new ChunkRequest(x, y, highPriority ? 0 : 1));
            }
            return;
        }

        // Add to pending and send request
        pendingRequests.put(key, System.currentTimeMillis());
        multiplayerClient.requestChunk(x, y);
    }

    public void markChunkComplete(int x, int y) {
        ChunkKey key = new ChunkKey(x, y);
        pendingRequests.remove(key);

        // Process next request if available
        if (!requestQueue.isEmpty() && pendingRequests.size() < MAX_CONCURRENT_REQUESTS) {
            ChunkRequest next = requestQueue.poll();
            if (next != null) {
                queueChunkRequest(next.x, next.y, next.priority == 0);
            }
        }
    }

    public boolean isChunkInProgress(int x, int y) {
        return pendingRequests.containsKey(new ChunkKey(x, y));
    }

    public void update() {
        long now = System.currentTimeMillis();

        // Clean up timed out requests
        pendingRequests.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > REQUEST_TIMEOUT_MS) {
                ChunkKey key = entry.getKey();
                // Re-queue failed requests with lower priority
                requestQueue.offer(new ChunkRequest(key.x, key.y, 2));
                return true;
            }
            return false;
        });
    }

    @Data
    @AllArgsConstructor
    private static class ChunkKey {
        private final int x;
        private final int y;
    }

    @Data
    @AllArgsConstructor
    private static class ChunkRequest implements Comparable<ChunkRequest> {
        private final int x;
        private final int y;
        private final int priority; // 0 = high, 1 = normal, 2 = retry

        @Override
        public int compareTo(ChunkRequest o) {
            return Integer.compare(this.priority, o.priority);
        }
    }
}
