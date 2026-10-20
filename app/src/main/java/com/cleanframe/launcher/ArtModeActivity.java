package com.cleanframe.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Frame-style art mode. Keeps the screen on and changes the artwork on a timer.
 * Left/Right step through works, Up/Down show the label, OK or Back return home.
 */
public class ArtModeActivity extends Activity {
    private ArtController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sleep = new Runnable() {
        @Override public void run() {
            // Let the TV's own sleep timer take over from here.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        ArtCanvasView canvas = new ArtCanvasView(this);
        root.addView(canvas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        PlaqueView plaque = new PlaqueView(this);
        FrameLayout.LayoutParams plaqueLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        plaqueLp.setMargins(0, 0, Ui.dp(this, 40), Ui.dp(this, 32));
        root.addView(plaque, plaqueLp);

        TextView status = Ui.text(this, "Collecting artwork from museum collections…\nThis needs an internet connection the first time.",
                18, Ui.LIGHT, Color.argb(200, 255, 255, 255));
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        setContentView(root);
        controller = new ArtController(this, canvas, plaque, status);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        controller.start();
        handler.removeCallbacks(sleep);
        int hours = Prefs.artSleepHours(this);
        if (hours > 0) handler.postDelayed(sleep, hours * 3_600_000L);
    }

    @Override protected void onPause() {
        controller.stop();
        handler.removeCallbacks(sleep);
        super.onPause();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Any press means someone is watching again.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                controller.next();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                controller.previous();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                controller.revealPlaque();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
