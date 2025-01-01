package io.github.minemon.player.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.service.PlayerAnimationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
@Slf4j
public class PlayerAnimationServiceImpl implements PlayerAnimationService {

    /**
     * Increase the frame duration for walking to slow down the animation slightly.
     *
     * With 3 frames, 0.10f means ~10 FPS (1 / 0.10). Feel free to adjust as needed.
     */
    private static final float WALK_FRAME_DURATION = 0.10f;

    /**
     * Increase the frame duration for running but still keep it slightly faster than walk.
     *
     * With 3 frames, 0.06f means ~16.7 FPS (1 / 0.06). Adjust if you need a different feel.
     */
    private static final float RUN_FRAME_DURATION = 0.06f;

    private TextureRegion standingUp, standingDown, standingLeft, standingRight;
    private Animation<TextureRegion> walkUp, walkDown, walkLeft, walkRight;
    private Animation<TextureRegion> runUp, runDown, runLeft, runRight;
    private boolean initialized = false;

    public PlayerAnimationServiceImpl() {
    }

    @Override
    public TextureRegion getCurrentFrame(PlayerDirection direction, boolean moving, boolean running, float stateTime) {
        if (!moving) {
            return getStandingFrame(direction);
        }

        Animation<TextureRegion> anim = getMovementAnimation(direction, running);
        return anim.getKeyFrame(stateTime, true);
    }

    @Override
    public TextureRegion getStandingFrame(PlayerDirection direction) {
        return switch (direction) {
            case UP -> standingUp;
            case DOWN -> standingDown;
            case LEFT -> standingLeft;
            case RIGHT -> standingRight;
        };
    }

    private Animation<TextureRegion> getMovementAnimation(PlayerDirection direction, boolean running) {
        return switch (direction) {
            case UP -> running ? runUp : walkUp;
            case DOWN -> running ? runDown : walkDown;
            case LEFT -> running ? runLeft : walkLeft;
            case RIGHT -> running ? runRight : walkRight;
        };
    }

    private void loadAnimations() {
        String atlasPath = "atlas/boy-gfx-atlas.atlas";
        log.info("Loading TextureAtlas from path: {}", atlasPath);
        TextureAtlas atlas = new TextureAtlas(Gdx.files.internal(atlasPath));

        log.info("Available regions in the atlas:");
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            log.info("- {} (index: {})", region.name, region.index);
        }

        // Create the walking animations (3 frames expected, but will work with however many frames are found)
        walkUp = createLoopAnimation(atlas, "boy_walk_up", WALK_FRAME_DURATION);
        walkDown = createLoopAnimation(atlas, "boy_walk_down", WALK_FRAME_DURATION);
        walkLeft = createLoopAnimation(atlas, "boy_walk_left", WALK_FRAME_DURATION);
        walkRight = createLoopAnimation(atlas, "boy_walk_right", WALK_FRAME_DURATION);

        // Standing frames are simply the first key frame of each animation
        standingUp = walkUp.getKeyFrames()[0];
        standingDown = walkDown.getKeyFrames()[0];
        standingLeft = walkLeft.getKeyFrames()[0];
        standingRight = walkRight.getKeyFrames()[0];

        // Create the running animations (3 frames expected, but will work with however many frames are found)
        runUp = createLoopAnimation(atlas, "boy_run_up", RUN_FRAME_DURATION);
        runDown = createLoopAnimation(atlas, "boy_run_down", RUN_FRAME_DURATION);
        runLeft = createLoopAnimation(atlas, "boy_run_left", RUN_FRAME_DURATION);
        runRight = createLoopAnimation(atlas, "boy_run_right", RUN_FRAME_DURATION);
    }

    public void initAnimationsIfNeeded() {
        if (!initialized) {
            loadAnimations();
            initialized = true;
        }
    }

    private Animation<TextureRegion> createLoopAnimation(TextureAtlas atlas, String baseName, float duration) {
        Array<TextureAtlas.AtlasRegion> regions = atlas.findRegions(baseName);
        if (regions.size == 0) {
            log.error("No regions found for animation: {}", baseName);
            throw new RuntimeException("No regions found for animation: " + baseName);
        }

        // Sort frames by index to ensure correct ordering
        regions.sort(Comparator.comparingInt(a -> a.index));

        log.info("Creating animation '{}', frames: {}, frameDuration: {}",
                baseName, regions.size, duration);

        Animation<TextureRegion> anim = new Animation<>(duration, regions, Animation.PlayMode.LOOP);
        log.debug("Created loop animation: {}", baseName);
        return anim;
    }

    public void dispose() {
    }
}
