package io.github.minemon.core.service.impl;

import io.github.minemon.core.service.FileAccessService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Primary
@Profile("client")
public class SpringFileAccessService implements FileAccessService {

    public SpringFileAccessService() {
    }

    @Override
    public boolean exists(String path) {
        Path p = Paths.get(path).toAbsolutePath();
        return Files.exists(p);
    }

    @Override
    public String readFile(String path) {
        Path p = Paths.get(path).toAbsolutePath();
        try {
            if (!Files.exists(p)) {
                throw new RuntimeException("File not found: " + path);
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file " + path + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void writeFile(String path, String content) {
        Path p = Paths.get(path).toAbsolutePath();
        try {
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }
}
