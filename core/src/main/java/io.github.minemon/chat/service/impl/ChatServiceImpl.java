package io.github.minemon.chat.service.impl;

import io.github.minemon.chat.event.ChatListener;
import io.github.minemon.chat.event.ChatMessageReceivedEvent;
import io.github.minemon.chat.model.ChatMessage;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.chat.service.CommandService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.service.PlayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final PlayerService playerService;
    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;
    private final CommandService commandService;
    private final ConcurrentLinkedQueue<ChatMessage> messages = new ConcurrentLinkedQueue<>();
    private final List<String> messageHistory = new ArrayList<>();
    private boolean isActive;
    private int messageHistoryIndex = -1;
    private String currentInputBeforeHistory = "";

    private final List<ChatListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    public ChatServiceImpl(PlayerService playerService, MultiplayerClient multiplayerClient, CommandService commandService) {
        this.playerService = playerService;
        this.multiplayerClient = multiplayerClient;
        this.commandService = commandService;
    }

    @EventListener 
    public void onChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessage chatMessage = event.getChatMessage();
        handleIncomingMessage(chatMessage);
    }

    @Override
    public List<ChatMessage> pollMessages() {
        List<ChatMessage> newMessages = new ArrayList<>();
        ChatMessage msg;
        while ((msg = messages.poll()) != null) {
            newMessages.add(msg);
        }
        return newMessages;
    }

    @Override
    public void sendMessage(String content) {
        if (content.isEmpty()) return;

        if (messageHistory.isEmpty() || !content.equals(messageHistory.get(messageHistory.size() - 1))) {
            messageHistory.add(content);
            messageHistoryIndex = messageHistory.size();
        }

        boolean isMultiplayer = multiplayerClient.isConnected();

        if (content.startsWith("/")) {
            String[] parts = content.substring(1).split(" ", 2);
            String commandName = parts[0].toLowerCase();
            String args = (parts.length > 1) ? parts[1] : "";
            if (!commandService.executeCommand(commandName, args, playerService, this, multiplayerClient)) {
                addSystemMessage("Unknown command: " + commandName);
            }
            return;
        }

        String username = playerService.getPlayerData().getUsername();
        if (username == null || username.isEmpty()) {
            username = "Player";
        }

        ChatMessage msg = new ChatMessage(username, content, System.currentTimeMillis(), ChatMessage.Type.NORMAL);
        handleIncomingMessage(msg);

        if (isMultiplayer) {
            multiplayerClient.sendMessage(msg);
        }
    }

    @Override
    public void addSystemMessage(String message) {
        ChatMessage sysMsg = new ChatMessage("System", message, System.currentTimeMillis(), ChatMessage.Type.SYSTEM);
        handleIncomingMessage(sysMsg);
    }

    @Override
    public void handleIncomingMessage(ChatMessage message) {
        messages.add(message);
        log.info("Received chat message from: {} content: {}", message.getSender(), message.getContent());

        for (ChatListener listener : listeners) {
            listener.onNewMessage(message);
        }
    }

    @Override
    public void activateChat() {
        isActive = true;
        messageHistoryIndex = messageHistory.size();
        currentInputBeforeHistory = "";
    }

    @Override
    public void deactivateChat() {
        isActive = false;
        messageHistoryIndex = messageHistory.size();
        currentInputBeforeHistory = "";
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public Queue<ChatMessage> getMessages() {
        return messages;
    }

    @Override
    public String getPreviousHistoryMessage(String currentText) {
        if (messageHistoryIndex == messageHistory.size()) {
            currentInputBeforeHistory = currentText;
        }

        if (messageHistory.isEmpty()) {
            return currentText;
        }

        if (messageHistoryIndex > 0) {
            messageHistoryIndex--;
            return messageHistory.get(messageHistoryIndex);
        } else {
            return messageHistory.get(0);
        }
    }

    @Override
    public String getNextHistoryMessage() {
        if (messageHistory.isEmpty()) {
            return "";
        }

        if (messageHistoryIndex < messageHistory.size()) {
            messageHistoryIndex++;
        }

        if (messageHistoryIndex == messageHistory.size()) {
            return currentInputBeforeHistory;
        } else {
            return messageHistory.get(messageHistoryIndex);
        }
    }
    public void addListener(ChatListener listener) {
        listeners.add(listener);
    }


    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }
}
