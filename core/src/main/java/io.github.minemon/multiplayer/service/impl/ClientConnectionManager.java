package io.github.minemon.multiplayer.service.impl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
@Slf4j
public class ClientConnectionManager {
    private static final String INSTANCE_FILE = "instance.lock";
    @Getter
    private final String instanceId;
    private FileChannel lockChannel;
    private FileLock lock;

    public ClientConnectionManager() {
        this.instanceId = UUID.randomUUID().toString();
    }

    public boolean acquireInstanceLock() {
        releaseInstanceLock(); // Always release any existing lock first

        try {
            File lockFile = new File(INSTANCE_FILE);

            // Delete the file if it exists but can't be opened
            if (lockFile.exists() && !lockFile.canWrite()) {
                Files.deleteIfExists(lockFile.toPath());
            }

            lockChannel = FileChannel.open(lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE);

            // Try to get exclusive lock
            lock = lockChannel.tryLock();

            if (lock != null) {
                // Write instance ID
                ByteBuffer buffer = ByteBuffer.wrap(instanceId.getBytes());
                lockChannel.truncate(0);
                lockChannel.write(buffer);
                lockChannel.force(true);
                return true;
            }

            return false;
        } catch (IOException e) {
            log.error("Failed to acquire instance lock: {}", e.getMessage());
            releaseInstanceLock();
            return false;
        }
    }

    public void releaseInstanceLock() {
        try {
            if (lock != null) {
                lock.release();
                lock = null;
            }
            if (lockChannel != null) {
                lockChannel.close();
                lockChannel = null;
            }
            // Try to delete the lock file
            Files.deleteIfExists(Path.of(INSTANCE_FILE));
        } catch (IOException e) {
            log.error("Error releasing instance lock: {}", e.getMessage());
        }
    }

}
