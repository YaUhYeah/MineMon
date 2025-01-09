package io.github.minemon.multiplayer.service;

import io.github.minemon.multiplayer.model.PlayerSyncData;

import java.util.Map;

public interface MultiplayerClient {
    interface LoginResponseListener {
        void onLoginResponse(boolean success, String message, String username, int startX, int startY);
    }
    boolean isPendingChunkRequest(int chunkX, int chunkY);
    Map<String, PlayerSyncData> getPlayerStates();

    interface CreateUserResponseListener {
        void onCreateUserResponse(boolean success, String message);
    }

    void clearPendingChunkRequests();

    void setLoginResponseListener(LoginResponseListener listener);
    void setCreateUserResponseListener(CreateUserResponseListener listener);
    void connect(String serverIP, int tcpPort, int udpPort);
    void login(String username, String password);
    void createUser(String username, String password);
    void disconnect();
    boolean isConnected();
    void sendPlayerMove(float x, float y, boolean running, boolean moving, String direction);
    void requestChunk(int chunkX, int chunkY);
    void update(float delta);
    void sendMessage(Object msg);

    
    void setPendingLoginRequest(Runnable action);
    void setPendingCreateUserRequest(Runnable action);
}
