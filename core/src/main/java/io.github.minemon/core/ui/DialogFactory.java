package io.github.minemon.core.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import lombok.Setter;

public class DialogFactory {
    private static final float DIALOG_PADDING = 20f;

    private final Skin skin;
    @Setter
    private Stage stage;

    public DialogFactory(Skin skin) {
        this.skin = skin;
    }

    public void showWarning(String title, String message) {
        if (stage == null) return;
        createDialog(title, message, DialogType.WARNING);
    }

    public void showError(String title, String message) {
        if (stage == null) return;
        createDialog(title, message, DialogType.ERROR);
    }

    public void showSuccess(String title, String message) {
        if (stage == null) return;
        createDialog(title, message, DialogType.SUCCESS);
    }

    public Dialog showLoading(String message) {
        if (stage == null) return null;
        Dialog dialog = new Dialog("", skin);
        dialog.pad(DIALOG_PADDING);

        Table content = new Table(skin);
        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        content.add(messageLabel).pad(10f).width(300f);
        content.row();
        content.add(new ProgressBar(0, 1, 0.01f, false, skin)).width(200f);

        dialog.getContentTable().add(content);
        dialog.setModal(true);
        dialog.show(stage);
        return dialog;
    }

    public void showConfirmation(String title, String message, Runnable onConfirm) {
        if (stage == null) return;
        Dialog dialog = new Dialog(title, skin);
        dialog.pad(DIALOG_PADDING);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        Cell<?> cell = dialog.getContentTable().add(messageLabel).pad(10f);
        cell.width(300f);

        TextButton confirmButton = new TextButton("Confirm", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onConfirm.run();
                dialog.hide();
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        Table buttonTable = new Table();
        buttonTable.add(confirmButton).pad(5).width(100f);
        buttonTable.add(cancelButton).pad(5).width(100f);

        dialog.getButtonTable().add(buttonTable);
        dialog.setModal(true);

        centerDialog(dialog);
        dialog.show(stage);
    }

    private void createDialog(String title, String message, DialogType type) {
        Dialog dialog = new Dialog(title, skin);
        dialog.pad(DIALOG_PADDING);

        Table content = new Table(skin);
        content.add(createIcon(type)).pad(10f).size(32);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.left);
        content.add(messageLabel).pad(10f).width(300f);

        dialog.getContentTable().add(content);

        TextButton okButton = new TextButton("OK", skin);
        okButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        dialog.getButtonTable().add(okButton).width(100f);
        dialog.setModal(true);

        centerDialog(dialog);
        dialog.show(stage);
    }

    private void centerDialog(Dialog dialog) {
        dialog.setPosition(
                (stage.getWidth() - dialog.getWidth()) / 2,
                (stage.getHeight() - dialog.getHeight()) / 2
        );
    }

    private Image createIcon(DialogType type) {
        String iconPath = switch (type) {
            case WARNING -> "Textures/UI/warning-icon.png";
            case ERROR -> "Textures/UI/error-icon.png";
            case SUCCESS -> "Textures/UI/success-icon.png";
        };
        return new Image(new Texture(Gdx.files.internal(iconPath)));
    }

    private enum DialogType {
        WARNING,
        ERROR,
        SUCCESS
    }
}
