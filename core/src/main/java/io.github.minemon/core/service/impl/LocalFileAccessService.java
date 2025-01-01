package io.github.minemon.core.service.impl;

import io.github.minemon.core.service.FileAccessService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Profile("server")
public class LocalFileAccessService implements FileAccessService {

    @Override
    public boolean exists(String path) {
        Path p = Paths.get(path);
        return Files.exists(p);
    }

    @Override
    public String readFile(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                throw new RuntimeException("File not found: " + path);
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error reading file " + path + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void writeFile(String path, String content) {
        try {
            Path p = Paths.get(path);
            Files.writeString(p, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error writing file " + path + ": " + e.getMessage(), e);
        }
    }
}
