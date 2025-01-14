package io.github.minemon.core.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.minemon.core.service.FileAccessService;
import org.springframework.stereotype.Service;

@Service
public class LocalFileAccessService implements FileAccessService {


    private FileHandle getFileHandle(String path) {
        if (Gdx.files == null) {
            // Return a temporary file handle for initialization
            return new FileHandle(path);
        }

        if (isAndroid()) {
            // First try internal assets
            if (path.startsWith("config/") || path.startsWith("data/")) {
                FileHandle internal = Gdx.files.internal(path);
                if (internal.exists()) {
                    return internal;
                }
            }
            // Then try external files directory
            return Gdx.files.external(path);
        } else {
            return Gdx.files.local(path);
        }
    }

    @Override
    public void writeFile(String path, String content) {
        try {
            FileHandle fileHandle = getFileHandle(path);
            // Ensure parent directory exists
            if (!fileHandle.parent().exists()) {fileHandle.parent().mkdirs();

            }

            // Try to write with retries
            Exception lastException = null;
            for (int i = 0; i < 3; i++) {
                try {
                    fileHandle.writeString(content, false);
                    return;
                } catch (Exception e) {
                    lastException = e;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            throw new RuntimeException("Failed to write file after retries: " + path, lastException);
        } catch (Exception e) {
            throw new RuntimeException("Error writing file " + path + ": " + e.getMessage(), e);
        }
    }

    private boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void ensureDirectoryExists(String path) {
        if (Gdx.files == null) {
            // Skip directory creation during initialization
            return;
        }

        FileHandle dir = getFileHandle(path);
        if (!dir.exists()) {
           dir.mkdirs();
        }

        // Verify directory is writable
        FileHandle testFile = dir.child(".test");
        try {
            testFile.writeString("test", false);
            testFile.delete();
        } catch (Exception e) {
            throw new RuntimeException("Directory exists but is not writable: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    public String getBasePath() {
        if (Gdx.files == null) {
            // Return current directory during initialization
            return ".";
        }

        if (isAndroid()) {
            // On Android, use the external files directory path
            return Gdx.files.external("").path();
        } else {
            // On desktop, use the working directory
            return Gdx.files.local("").file().getAbsolutePath();
        }
    }

    @Override
    public boolean exists(String path) {
        if (Gdx.files == null) {
            // Return false during initialization
            return false;
        }
        return getFileHandle(path).exists();
    }

    @Override
    public String readFile(String path) {
        try {
            if (Gdx.files == null) {
                // Return empty string during initialization
                return "";
            }

            FileHandle fileHandle = getFileHandle(path);
            if (!fileHandle.exists()) {
                throw new RuntimeException("File not found: " + path);
            }

            return fileHandle.readString();
        } catch (Exception e) {
            throw new RuntimeException("Error reading file " + path + ": " + e.getMessage(), e);
        }
    }
}
