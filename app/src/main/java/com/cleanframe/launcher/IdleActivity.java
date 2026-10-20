package com.cleanframe.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Starts art mode after the configured number of minutes without a button press. */
public abstract class IdleActivity extends Activity {
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable goIdle = new Runnable() {
        @Override public void run() {
            startActivity(new Intent(IdleActivity.this, ArtModeActivity.class));
        }
    };

    @Override protected void onResume() {
        super.onResume();
        resetIdle();
    }

    @Override protected void onPause() {
        idleHandler.removeCallbacks(goIdle);
        super.onPause();
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        resetIdle();
    }

    private void resetIdle() {
        idleHandler.removeCallbacks(goIdle);
        int minutes = Prefs.idleMinutes(this);
        if (minutes > 0) idleHandler.postDelayed(goIdle, minutes * 60_000L);
    }
}
