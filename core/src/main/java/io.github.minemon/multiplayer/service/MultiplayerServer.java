package io.github.minemon.multiplayer.service;

public interface MultiplayerServer {
    void startServer(int tcpPort, int udpPort);
    void stopServer();
    void broadcast(Object message);
    void processMessages(float delta);
}
