package io.github.minemon.chat.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    private String sender;
    private String content;
    private long timestamp;
    private Type type;
    public enum Type {
        NORMAL, SYSTEM
    }
}
