package io.github.minemon.core.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.minemon.core.service.FileAccessService;
import org.springframework.stereotype.Service;

@Service
public class LocalFileAccessService implements FileAccessService {

    @Override
    public boolean exists(String path) {
        if (Gdx.files == null) {
            throw new IllegalStateException("LibGDX not initialized");
        }
        return Gdx.files.internal(path).exists();
    }

    @Override
    public String readFile(String path) {
        try {
            if (Gdx.files == null) {
                throw new IllegalStateException("LibGDX not initialized");
            }

            FileHandle fileHandle = Gdx.files.internal(path);
            if (!fileHandle.exists()) {
                throw new RuntimeException("File not found: " + path);
            }

            return fileHandle.readString();
        } catch (Exception e) {
            throw new RuntimeException("Error reading file " + path + ": " + e.getMessage(), e);
        }
    }

    private FileHandle getFileHandle(String path) {
        if (Gdx.files == null) {
            throw new IllegalStateException("LibGDX not initialized");
        }
        
        if (isAndroid()) {
            // On Android, use external files directory
            return Gdx.files.external("Android/data/io.github.minemon/files/" + path);
        } else {
            return Gdx.files.local(path);
        }
    }

    @Override
    public void writeFile(String path, String content) {
        try {
            FileHandle fileHandle = getFileHandle(path);
            // Ensure parent directory exists
            if (!fileHandle.parent().exists()) {
                boolean created = fileHandle.parent().mkdirs();
                if (!created) {
                    throw new RuntimeException("Failed to create parent directory for: " + path);
                }
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
            throw new IllegalStateException("LibGDX not initialized");
        }
        
        FileHandle dir = getFileHandle(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create directory: " + path);
            }
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
            throw new IllegalStateException("LibGDX not initialized");
        }
        
        if (isAndroid()) {
            // On Android, use the external files directory path
            return Gdx.files.external("Android/data/io.github.minemon/files").path();
        } else {
            // On desktop, use the working directory
            return Gdx.files.local("").file().getAbsolutePath();
        }
    }

    @Override
    public boolean exists(String path) {
        if (Gdx.files == null) {
            throw new IllegalStateException("LibGDX not initialized");
        }
        return getFileHandle(path).exists();
    }

    @Override
    public String readFile(String path) {
        try {
            if (Gdx.files == null) {
                throw new IllegalStateException("LibGDX not initialized");
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
