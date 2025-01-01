package io.github.minemon.world.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WorldObjectRemoveEvent extends ApplicationEvent {
    private final String objectId;

    public WorldObjectRemoveEvent(Object source, String objectId) {
        super(source);
        this.objectId = objectId;
    }
}
