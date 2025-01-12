package io.github.minemon.android;

import android.content.Context;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class AndroidInitializer {
    private final Context context;

    public AndroidInitializer(Context context) {
        this.context = context;
    }

    public void ensureDirectories() {
        try {
            // Get external storage directory
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                log.error("Failed to get external files directory");
                return;
            }

            String basePath = externalDir.getAbsolutePath();
            log.info("Using base path: {}", basePath);

            // Create required directories
            String[] dirs = {
                "save",
                "save/worlds",
                "save/players",
                "cache",
                "data",
                "temp"
            };

            for (String dir : dirs) {
                File dirFile = new File(externalDir, dir);
                if (!dirFile.exists() && !dirFile.mkdirs()) {
                    log.error("Failed to create directory: {}", dirFile.getAbsolutePath());
                } else {
                    log.info("Directory ready: {}", dirFile.getAbsolutePath());
                }
            }

            // Set system properties
            System.setProperty("java.io.tmpdir", new File(basePath, "temp").getAbsolutePath());
            System.setProperty("user.home", basePath);
            System.setProperty("user.dir", basePath);

            // Validate access
            validateDirectoryAccess(basePath);

        } catch (Exception e) {
            log.error("Failed to initialize directories", e);
        }
    }

    private void createDirectory(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("Failed to create directory: {}", dir.getAbsolutePath());
        }
    }

    private void validateDirectoryAccess(String basePath) {
        try {
            File testFile = new File(basePath, "test.tmp");
            if (testFile.createNewFile()) {
                testFile.delete();
            }
        } catch (Exception e) {
            log.error("Directory access validation failed", e);
        }
    }

    public void validateGraphicsContext() {
        try {
            if (Gdx.graphics == null) {
                log.warn("Graphics context not yet available");
                return;
            }

            // Wait for GL context to be ready
            int attempts = 0;
            while (Gdx.graphics.getGL20() == null && attempts < 10) {
                try {
                    Thread.sleep(100);
                    attempts++;
                    log.debug("Waiting for GL context, attempt {}", attempts);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Check dimensions only if we have a GL context
            if (Gdx.graphics.getGL20() != null) {
                int width = Gdx.graphics.getWidth();
                int height = Gdx.graphics.getHeight();

                if (width <= 0 || height <= 0) {
                    log.warn("Invalid frame buffer dimensions: {}x{}", width, height);
                    return;
                }

                log.info("Graphics context validated successfully: {}x{}", width, height);
            } else {
                log.warn("GL context not available after {} attempts", attempts);
            }

        } catch (Exception e) {
            log.error("Graphics context validation failed", e);
            // Don't throw, just log the error
        }
    }
}
