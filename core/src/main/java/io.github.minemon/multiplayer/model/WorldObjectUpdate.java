package io.github.minemon.multiplayer.model;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class WorldObjectUpdate {
    private String objectId;
    private String type;
    private int tileX;
    private int tileY;
    private boolean removed;
}
