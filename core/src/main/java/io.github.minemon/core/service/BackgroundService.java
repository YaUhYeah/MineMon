package io.github.minemon.core.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BackgroundService {
    private SpriteBatch batch;
    private TextureRegion backgroundTexture;
    private ShaderProgram shader;
    private float time;
    private Matrix4 transform;
    private boolean useShader = true;

    public void initialize() {
        batch = new SpriteBatch();
        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(false);
        transform = new Matrix4();


        try {
            shader = new ShaderProgram(
                    Gdx.files.internal("shaders/menu_background.vert"),
                    Gdx.files.internal("shaders/menu_background.frag")
            );

            if (!shader.isCompiled()) {
                log.error("Shader compilation failed: {}", shader.getLog());
                useShader = false;
            }
        } catch (Exception e) {
            log.error("Failed to load shader", e);
            useShader = false;
        }


        try {
            Texture bgTexture = new Texture(Gdx.files.internal("assets/Textures/UI/ethereal.png"));
            backgroundTexture = new TextureRegion(bgTexture);
        } catch (Exception e) {
            log.error("Failed to load background texture", e);
        }
    }

    public void update(float delta) {
        time += delta * 0.02f;
        if (useShader) {
            transform.setToRotation(0, 1, 0, time * 15);
        }
    }

    public void render(boolean transparent) {
        if (backgroundTexture == null) return;

        batch.begin();
        if (transparent) {
            batch.setColor(1, 1, 1, 0.5f);
        }

        batch.draw(backgroundTexture,
                0, 0,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight()
        );

        if (transparent) {
            batch.setColor(Color.WHITE);
        }
        batch.end();
    }

    public void dispose() {
        if (batch != null) batch.dispose();
        if (shader != null) shader.dispose();
        if (backgroundTexture != null) backgroundTexture.getTexture().dispose();
    }
}