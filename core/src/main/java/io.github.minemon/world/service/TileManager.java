package io.github.minemon.world.service;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public interface TileManager {
    void initIfNeeded();
    TextureRegion getRegionForTile(int tileId);
    boolean isPassable(int tileId);
    String getTileName(int tileId);
}
