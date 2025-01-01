package io.github.minemon.event;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventBus {
    private final Map<Class<? extends Event>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    public <E extends Event> void registerListener(Class<E> eventClass, EventListener<E> listener) {
        listeners.computeIfAbsent(eventClass, k -> Collections.synchronizedList(new ArrayList<>())).add(listener);
    }

    public <E extends Event> void unregisterListener(Class<E> eventClass, EventListener<E> listener) {
        List<EventListener<?>> list = listeners.get(eventClass);
        if (list != null) {
            list.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void fireEvent(E event) {
        Class<? extends Event> eventClass = event.getClass();
        List<EventListener<?>> list = listeners.get(eventClass);
        if (list != null) {
            for (EventListener<?> l : list) {
                ((EventListener<E>) l).onEvent(event);
            }
        }
    }
}
