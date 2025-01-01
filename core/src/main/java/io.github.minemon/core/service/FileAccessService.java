package io.github.minemon.core.service;

public interface FileAccessService {
    boolean exists(String path);
    String readFile(String path);
    void writeFile(String path, String content);
}
