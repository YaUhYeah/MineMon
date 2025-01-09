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
            
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                log.error("Failed to get external files directory");
                return;
            }

            String basePath = externalDir.getAbsolutePath();

            
            createDirectory(new File(basePath, "save"));
            createDirectory(new File(basePath, "cache"));
            createDirectory(new File(basePath, "data"));

            
            System.setProperty("java.io.tmpdir", new File(basePath, "temp").getAbsolutePath());

            
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
                throw new RuntimeException("Graphics context is null");
            }

            if (Gdx.graphics.getGL20() == null) {
                throw new RuntimeException("GL20 context is null");
            }

            
            int width = Gdx.graphics.getWidth();
            int height = Gdx.graphics.getHeight();

            if (width <= 0 || height <= 0) {
                throw new RuntimeException("Invalid frame buffer dimensions: " + width + "x" + height);
            }

            log.info("Graphics context validated successfully: {}x{}", width, height);

        } catch (Exception e) {
            log.error("Graphics context validation failed", e);
            throw e;
        }
    }
}
