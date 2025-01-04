package io.github.minemon.world;

import lombok.Data;

@Data
class SpawnPoint {
    private float x;
    private float y;
    private long timestamp;

    public SpawnPoint() {
        this(0, 0);
    }

    public SpawnPoint(float x, float y) {
        this.x = x;
        this.y = y;
        this.timestamp = System.currentTimeMillis();
    }
}

