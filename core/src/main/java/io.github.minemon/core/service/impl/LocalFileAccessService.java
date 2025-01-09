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

    @Override
    public void writeFile(String path, String content) {
        try {
            if (Gdx.files == null) {
                throw new IllegalStateException("LibGDX not initialized");
            }

            
            FileHandle fileHandle = Gdx.files.local(path);
            fileHandle.writeString(content, false);
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
}
