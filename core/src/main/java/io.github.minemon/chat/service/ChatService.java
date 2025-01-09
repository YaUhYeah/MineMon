package io.github.minemon.chat.service;

import io.github.minemon.chat.model.ChatMessage;
import io.github.minemon.chat.event.ChatListener;

import java.util.List;
import java.util.Queue;

public interface ChatService {
    void sendMessage(String content);
    void addSystemMessage(String message);
    void handleIncomingMessage(ChatMessage message);
    void activateChat();
    void deactivateChat();
    boolean isActive();
    List<ChatMessage> pollMessages();
    Queue<ChatMessage> getMessages();

    String getPreviousHistoryMessage(String currentText);
    String getNextHistoryMessage();

    
    void addListener(ChatListener listener);
    void removeListener(ChatListener listener);
}
