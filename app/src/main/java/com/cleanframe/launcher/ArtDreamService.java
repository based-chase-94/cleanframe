package com.cleanframe.launcher;

import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The same art as a system screensaver, so the TV shows paintings even when it idles inside
 * another app. Fire OS has no picker for this; the install script selects it over adb.
 */
public class ArtDreamService extends DreamService {
    private ArtController controller;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        FrameLayout root = new FrameLayout(this);
        ArtCanvasView canvas = new ArtCanvasView(this);
        root.addView(canvas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        PlaqueView plaque = new PlaqueView(this);
        FrameLayout.LayoutParams plaqueLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        plaqueLp.setMargins(0, 0, Ui.dp(this, 40), Ui.dp(this, 32));
        root.addView(plaque, plaqueLp);
        setContentView(root);
        controller = new ArtController(this, canvas, plaque, null);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (controller != null) controller.start();
    }

    @Override public void onDreamingStopped() {
        if (controller != null) controller.stop();
        super.onDreamingStopped();
    }
}
