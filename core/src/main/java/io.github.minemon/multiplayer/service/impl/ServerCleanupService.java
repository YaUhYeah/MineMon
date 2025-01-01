package io.github.minemon.multiplayer.service.impl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ServerCleanupService {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    public void cleanupExecutor(ExecutorService executor, String executorName) {
        log.info("Shutting down {} executor service...", executorName);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("{} executor did not terminate in {} seconds, forcing shutdown...",
                        executorName, SHUTDOWN_TIMEOUT_SECONDS);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("{} executor shutdown interrupted", executorName);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void preDestroy() {
        log.info("ServerCleanupService shutting down...");
    }
}