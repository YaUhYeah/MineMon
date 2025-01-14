package io.github.minemon.android;

import android.content.Context;
import com.badlogic.gdx.Gdx;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;

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
                String msg = "Failed to get external files directory";
                log.error(msg);
                throw new RuntimeException(msg);
            }

            String basePath = externalDir.getAbsolutePath();
            log.info("Using base path: {}", basePath);

            // Create required directories with proper permissions
            String[] dirs = {
                "save",
                "save/worlds",
                "save/players",
                "cache",
                "data",
                "temp",
                "config"  // Add config directory
            };

            for (String dir : dirs) {
                File dirFile = new File(externalDir, dir);
                if (!dirFile.exists()) {
                    if (!dirFile.mkdirs()) {
                        String msg = "Failed to create directory: " + dirFile.getAbsolutePath();
                        log.error(msg);
                        throw new RuntimeException(msg);
                    }
                }

                // Ensure directory is writable
                if (!dirFile.canWrite()) {
                    String msg = "Directory not writable: " + dirFile.getAbsolutePath();
                    log.error(msg);
                    throw new RuntimeException(msg);
                }

                // Test write access with retries
                boolean writeSuccess = false;
                IOException lastException = null;
                for (int i = 0; i < 3; i++) {
                    File testFile = new File(dirFile, ".test");
                    try {
                        if (testFile.createNewFile()) {
                            testFile.delete();
                            writeSuccess = true;
                            log.info("Directory ready and writable: {}", dirFile.getAbsolutePath());
                            break;
                        }
                    } catch (IOException e) {
                        lastException = e;
                        log.warn("Write test attempt {} failed for directory: {}", i + 1, dirFile.getAbsolutePath());
                        try {
                            Thread.sleep(100); // Short delay before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                if (!writeSuccess) {
                    String msg = "Cannot write to directory after retries: " + dirFile.getAbsolutePath();
                    log.error(msg, lastException);
                    throw new RuntimeException(msg, lastException);
                }
            }

            // Set system properties
            System.setProperty("java.io.tmpdir", new File(basePath, "temp").getAbsolutePath());
            System.setProperty("user.home", basePath);

            // Don't set user.dir as it's read-only on Android

            log.info("All directories created and validated successfully");

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

    public void copyAssets() {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                log.error("External storage not available");
                return;
            }

            // Create config directory
            File configDir = new File(externalDir, "config");
            if (!configDir.exists() && !configDir.mkdirs()) {
                log.error("Failed to create config directory");
                throw new RuntimeException("Failed to create config directory");
            }

            // Copy biomes.json
            File biomesFile = new File(configDir, "biomes.json");
            if (!biomesFile.exists()) {
                try {
                    java.io.InputStream in = context.getAssets().open("config/biomes.json");
                    java.io.OutputStream out = new java.io.FileOutputStream(biomesFile);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    out.flush();
                    out.close();
                    log.info("Successfully copied biomes.json to {}", biomesFile.getAbsolutePath());
                } catch (IOException e) {
                    log.error("Failed to copy biomes.json", e);
                    throw e;
                }
            }

            // Copy all necessary config files
            String[] configFiles = {
                "config/tiles.json",
                "config/biomes.json",
                "data/biomes.json",
                "Skins/uiskin.json",
                "atlas/items-gfx-atlas.atlas",
                "atlas/tiles-gfx-atlas.atlas",
                "atlas/ui-gfx-atlas.atlas",
                "shaders/menu_background.vert",
                "shaders/menu_background.frag"
            };

            for (String configFile : configFiles) {
                File destFile = new File(externalDir, configFile);
                if (!destFile.exists()) {
                    File parentDir = destFile.getParentFile();
                    if (!parentDir.exists() && !parentDir.mkdirs()) {
                        log.error("Failed to create directory: {}", parentDir.getAbsolutePath());
                        throw new RuntimeException("Failed to create directory: " + parentDir.getAbsolutePath());
                    }
                    try {
                        java.io.InputStream in = context.getAssets().open(configFile);
                        java.io.OutputStream out = new java.io.FileOutputStream(destFile);
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        in.close();
                        out.flush();
                        out.close();
                        log.info("Successfully copied {} to {}", configFile, destFile.getAbsolutePath());
                    } catch (IOException e) {
                        log.error("Failed to copy {}", configFile, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to copy assets", e);
            throw new RuntimeException("Failed to copy assets", e);
        }
    }

    public void copyAssetsIfNeeded() {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                log.error("External storage not available");
                return;
            }

            // Create config directory
            File configDir = new File(externalDir, "config");
            if (!configDir.exists() && !configDir.mkdirs()) {
                log.error("Failed to create config directory");
                return;
            }

            // Copy biomes.json
            File biomesFile = new File(configDir, "biomes.json");
            if (!biomesFile.exists()) {
                try {
                    java.io.InputStream in = context.getAssets().open("config/biomes.json");
                    java.io.OutputStream out = new java.io.FileOutputStream(biomesFile);
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();
                    out.close();
                    log.info("Successfully copied biomes.json to {}", biomesFile.getAbsolutePath());
                } catch (IOException e) {
                    log.error("Failed to copy biomes.json", e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to copy assets", e);
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
