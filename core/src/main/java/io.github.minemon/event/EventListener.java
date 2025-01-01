package io.github.minemon.event;

public interface EventListener<E extends Event> {
    void onEvent(E event);
}
