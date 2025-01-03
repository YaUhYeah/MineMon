package io.github.minemon.world.model;

import com.badlogic.gdx.math.Rectangle;

import lombok.Data;

import java.util.UUID;


@Data
public class WorldObject {

    private String id;

    private int tileX;
    private int tileY;

    private ObjectType type;

    private float spawnTime;
    private boolean collidable;


    private float timeSinceVisible;

    public WorldObject() {

    }

    public WorldObject(int tileX, int tileY, ObjectType type, boolean collidable) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.type = type;
        this.collidable = collidable;
        this.id = UUID.randomUUID().toString();
        this.spawnTime = type.isPermanent() ? 0f : (System.currentTimeMillis() / 1000f);
        this.timeSinceVisible = 0f;
    }



    public float getFadeAlpha() {
        return Math.min(timeSinceVisible, 1f);
    }




    public Rectangle getCollisionBox() {
        if (!collidable) {
            return null;
        }

        float pixelX = tileX * 32;
        float pixelY = tileY * 32;

        if (isTreeType(this.type)) {
            float baseX = pixelX - 32;
            return new Rectangle(baseX, pixelY, 64, 64);
        } else {
            // Other objects use their normal dimensions
            return new Rectangle(
                pixelX,
                pixelY,
                this.type.getWidthInTiles() * 32,
                this.type.getHeightInTiles() * 32
            );
        }
    }



    private boolean isTreeType(ObjectType t) {
        return t == ObjectType.TREE_0 ||
                t == ObjectType.TREE_1 ||
                t == ObjectType.SNOW_TREE ||
                t == ObjectType.HAUNTED_TREE ||
                t == ObjectType.RUINS_TREE ||
                t == ObjectType.APRICORN_TREE ||
                t == ObjectType.RAIN_TREE ||
                t == ObjectType.CHERRY_TREE;
    }
}
