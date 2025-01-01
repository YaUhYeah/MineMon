package io.github.minemon.core.ui;

import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;

public interface DialogCloseListener extends EventListener {
    void onClose();

    @Override
    default boolean handle(Event event) {
        return false;
    }
}
