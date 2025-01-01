package io.github.minemon.chat.event;

import io.github.minemon.chat.model.ChatMessage;

public interface ChatListener {
    void onNewMessage(ChatMessage msg);
}
