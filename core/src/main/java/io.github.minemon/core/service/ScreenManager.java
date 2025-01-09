package io.github.minemon.core.service;

import com.badlogic.gdx.Screen;

public interface ScreenManager {
    void showScreen(Class<? extends Screen> screenClass);
    void goBack();
    Screen getPreviousScreen();
    void disposeScreen(Class<? extends Screen> screenClass);
    <T extends Screen> T getScreen(Class<T> screenClass);
}
