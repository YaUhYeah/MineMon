package io.github.minemon.android;

import android.util.Log;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import org.slf4j.LoggerFactory;

public class AndroidLoggerFactory {
    private static final String TAG = "MineMon";
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        try {
            // Get the logger context
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.reset();

            // Create and configure the Logcat appender
            LogcatAppender logcatAppender = new LogcatAppender();
            logcatAppender.setContext(context);
            logcatAppender.setName("logcat");

            // Create and configure the encoder
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("[%thread] %msg%n");
            encoder.start();

            // Configure and start the appender
            logcatAppender.setEncoder(encoder);
            logcatAppender.start();

            // Add appender to root logger
            ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(logcatAppender);
            rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);

            initialized = true;
            Log.i(TAG, "Android logging initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize logging: " + e.getMessage(), e);
            e.printStackTrace();
        }
    }
}