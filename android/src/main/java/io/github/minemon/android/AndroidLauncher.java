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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AndroidLauncher extends AndroidApplication {
    private GdxGame game;
    private boolean isInitialized = false;
    private AndroidInitializer initializer;
    private static final int PERMISSION_REQUEST_CODE = 123;
    
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
            log.error("Uncaught exception in thread " + thread.getName(), throwable);
            throwable.printStackTrace();
            finish();
        });
        
        super.onCreate(savedInstanceState);

        try {
            log.info("Starting AndroidLauncher onCreate");
            
            // Request permissions first
            requestPermissions();
            
            // Ensure external storage is available
            if (getExternalFilesDir(null) == null) {
                throw new RuntimeException("External storage not available");
            }
            
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            
            AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
            config.useImmersiveMode = true;
            config.useWakelock = true;
            config.useGyroscope = false;
            config.useCompass = false;
            config.useAccelerometer = false;
            config.numSamples = 0;  
            config.r = 8;
            config.g = 8;
            config.b = 8;
            config.a = 8;
            config.depth = 16;
            config.useWakelock = true;

            
            // Initialize Android-specific context
            GameApplicationContext.initContext(true);
            
            // Initialize Android file system
            AndroidInitializer initializer = new AndroidInitializer(this);
            initializer.ensureDirectories();
            
            // Get game instance
            GdxGame game = GameApplicationContext.getBean(GdxGame.class);
            
            // Setup Android input
            InputService inputService = GameApplicationContext.getBean(InputService.class);
            inputService.setAndroidMode(true);
            
            // Initialize Android touch input
            try {
                AndroidTouchInput touchInput = GameApplicationContext.getBean(AndroidTouchInput.class);
                touchInput.initialize(AndroidUIFactory.createTouchpadStyle());
            } catch (Exception e) {
                log.warn("Failed to initialize touch input early", e);
                // Not critical, will try again in GdxGame
            }
            
            // Initialize game with proper Android paths
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) {
                throw new RuntimeException("External storage not available");
            }
            
            // Set system properties for file paths
            System.setProperty("user.home", externalDir.getAbsolutePath());
            System.setProperty("user.dir", externalDir.getAbsolutePath());
            
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
        config.numSamples = 0; 
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;
        config.depth = 16;
        config.stencil = 8;
        config.useGL30 = false; 
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
