package io.github.minemon.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import io.github.minemon.GdxGame;
import io.github.minemon.context.AndroidGameContext;
import io.github.minemon.context.GameApplicationContext;
import io.github.minemon.core.ui.AndroidUIFactory;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputService;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;

@Slf4j
public class AndroidLauncher extends AndroidApplication {
    private GdxGame game;
    private boolean isInitialized = false;
    private AndroidInitializer initializer;
    private static final int PERMISSION_REQUEST_CODE = 123;

    private boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) and above
            if (!Environment.isExternalStorageManager()) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (Exception e) {
                    log.error("Failed to request MANAGE_EXTERNAL_STORAGE permission", e);
                }
            }
        } else {
            // Below Android 11
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            boolean needsPermission = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    needsPermission = true;
                    break;
                }
            }

            if (needsPermission) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Initialize logging first
            AndroidLoggerFactory.init();
            log.info("Starting AndroidLauncher onCreate");

            // Enable hardware acceleration
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );

            // Setup window flags
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            // Request and verify permissions
            requestPermissions();
            if (!waitForPermissions()) {
                throw new RuntimeException("Required permissions not granted");
            }

            // Initialize file system
            AndroidInitializer initializer = new AndroidInitializer(this);
            initializer.ensureDirectories();
            initializer.copyAssets();
            initializer.validateGraphicsContext();

            // Set system properties
            File externalDir = getExternalFilesDir(null);
            System.setProperty("user.home", externalDir.getAbsolutePath());
            System.setProperty("spring.profiles.active", "android");

            // Load native libraries
            try {
                System.loadLibrary("gdx");
                System.loadLibrary("gdx-freetype");
                // Try to load MediaTek-specific library if available
                try {
                    System.loadLibrary("magtsync");
                } catch (UnsatisfiedLinkError e) {
                    // Ignore if not available - it's device-specific
                    log.debug("MediaTek sync library not available - this is normal on most devices");
                }
            } catch (UnsatisfiedLinkError e) {
                log.error("Failed to load required native libraries", e);
                throw e;
            }

            // Initialize LibGDX first
            AndroidApplicationConfiguration config = createConfig();
            GdxGame game = new GdxGame();
            initialize(game, config);

            // Wait briefly for LibGDX to initialize
            Thread.sleep(500);

            // Now initialize game context
            AndroidGameContext.register(GdxGame.class, game);
            AndroidGameContext.initMinimal();
            AndroidGameContext.initServices();

            // Setup input after context is ready
            InputService inputService = AndroidGameContext.getBean(InputService.class);
            inputService.setAndroidMode(true);

            // Initialize touch input last
            initializeTouchInput();

            log.info("AndroidLauncher initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize AndroidLauncher", e);
            finish();
        }
    }

    private boolean waitForPermissions() {
        int attempts = 0;
        while (!hasStoragePermissions() && attempts < 10) {
            try {
                Thread.sleep(500);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return hasStoragePermissions();
    }

    private void initializeTouchInput() {
        try {
            AndroidTouchInput touchInput = AndroidGameContext.getBean(AndroidTouchInput.class);
            touchInput.initialize(AndroidUIFactory.createTouchpadStyle());
        } catch (Exception e) {
            log.warn("Touch input initialization deferred", e);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (graphics != null && graphics instanceof AndroidGraphics) {
            ((AndroidGraphics) graphics).onResumeGLSurfaceView();
        }
    }

    @Override
    protected void onPause() {
        if (graphics != null && graphics instanceof AndroidGraphics) {
            ((AndroidGraphics) graphics).onPauseGLSurfaceView();
        }
        super.onPause();
    }
    private AndroidApplicationConfiguration createConfig() {
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useWakelock = true;
        config.useGyroscope = false;
        config.useCompass = false;
        config.numSamples = 2;  // Enable MSAA for better rendering
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;
        config.depth = 16;
        config.stencil = 8;
        config.useGL30 = false;
        config.disableAudio = false;
        config.maxSimultaneousSounds = 16;
        config.useAccelerometer = false;
        return config;
    }

    private void setupImmersiveMode() {
        runOnUiThread(() -> {
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(flags);

            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        try {
            if (isInitialized && game != null) {
                game.dispose();
                GameApplicationContext.dispose();
            }
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isInitialized) {
            setupImmersiveMode();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                log.error("Storage permissions not granted - game may not function correctly");
            } else {
                log.info("Storage permissions granted successfully");
            }
        }
    }
}
