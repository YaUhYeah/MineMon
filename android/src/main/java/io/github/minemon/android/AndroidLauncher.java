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
        // Enable hardware acceleration
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        // Initialize logging first
        try {
            AndroidLoggerFactory.init();
            log.info("Logging initialized");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }

        // Set global exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Get root cause
            Throwable rootCause = throwable;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }

            // Log only the essential information
            log.error("[{}] {}: {}",
                thread.getName(),
                rootCause.getClass().getSimpleName(),
                rootCause.getMessage());

            finish();
        });

        super.onCreate(savedInstanceState);

        try {
            log.info("Starting AndroidLauncher onCreate");

            // Request permissions first and wait for them to be granted
            requestPermissions();

            // Wait for permissions to be granted (with timeout)
            int attempts = 0;
            while (!hasStoragePermissions() && attempts < 10) {
                try {
                    Thread.sleep(1000);
                    attempts++;
                    log.info("Waiting for storage permissions, attempt {}/10", attempts);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!hasStoragePermissions()) {
                String msg = "Storage permissions not granted after timeout";
                log.error(msg);
                throw new RuntimeException(msg);
            }

            // Ensure external storage is available and writable
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                String msg = "External storage not available";
                log.error(msg);
                throw new RuntimeException(msg);
            }

            // Test write access with retries
            boolean writeSuccess = false;
            IOException lastException = null;
            for (int i = 0; i < 3; i++) {
                File testFile = new File(externalDir, "test.tmp");
                try {
                    if (testFile.createNewFile()) {
                        testFile.delete();
                        writeSuccess = true;
                        log.info("External storage is writable");
                        break;
                    }
                } catch (IOException e) {
                    lastException = e;
                    log.warn("Write test attempt {} failed: {}", i + 1, e.getMessage());
                    try {
                        Thread.sleep(100); // Short delay before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!writeSuccess) {
                String msg = "Cannot write to external storage after retries";
                log.error(msg, lastException);
                throw new RuntimeException(msg, lastException);
            }

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);


            // Initialize Android file system and copy assets first
            AndroidInitializer initializer = new AndroidInitializer(this);
            initializer.ensureDirectories();
            initializer.copyAssets();  // Always copy assets to ensure they're up to date

            // Set Android profile
            System.setProperty("spring.profiles.active", "android");
            
            // Load native libraries
            try {
                System.loadLibrary("gdx");
                System.loadLibrary("gdx-freetype");
            } catch (UnsatisfiedLinkError e) {
                log.error("Failed to load native libraries", e);
                throw new RuntimeException("Failed to load native libraries", e);
            }

            // Initialize LibGDX first
            AndroidApplicationConfiguration config = createConfig();
            GdxGame game = new GdxGame();
            initialize(game, config);

            // Now that LibGDX is initialized, we can initialize the Android context
            try {
                AndroidGameContext.initMinimal();
                
                // Register the game instance if not already registered
                if (!AndroidGameContext.isRegistered(GdxGame.class)) {
                    AndroidGameContext.register(GdxGame.class, game);
                    log.info("Registered GdxGame instance in AndroidGameContext");
                }

                AndroidGameContext.initServices();
            } catch (Exception e) {
                log.error("Failed to initialize Android game context", e);
                throw new RuntimeException("Failed to initialize Android game context", e);
            }

            // Setup Android input
            InputService inputService = AndroidGameContext.getBean(InputService.class);
            inputService.setAndroidMode(true);

            // Initialize Android touch input
            try {
                AndroidTouchInput touchInput = AndroidGameContext.getBean(AndroidTouchInput.class);
                touchInput.initialize(AndroidUIFactory.createTouchpadStyle());
            } catch (Exception e) {
                log.warn("Failed to initialize touch input early", e);
                // Not critical, will try again in GdxGame
            }

            // Set system properties for file paths
            System.setProperty("user.home", externalDir.getAbsolutePath());

            // Initialize game
            initialize(game, config);

            // Graphics context will be initialized in the create() method
            log.info("AndroidLauncher initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize AndroidLauncher", e);
            finish();
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
