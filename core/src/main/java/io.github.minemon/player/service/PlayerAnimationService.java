package io.github.minemon.player.service;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.minemon.player.model.PlayerDirection;

public interface PlayerAnimationService {
    TextureRegion getCurrentFrame(PlayerDirection direction, boolean moving, boolean running, float stateTime);
    TextureRegion getStandingFrame(PlayerDirection direction);
    void initAnimationsIfNeeded();
}
