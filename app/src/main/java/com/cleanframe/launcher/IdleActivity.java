package com.cleanframe.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Starts art mode after the configured number of minutes without a button press, but only
 * while this screen is in front. Once another app opens, the timer is off until we're back.
 */
public abstract class IdleActivity extends Activity {
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private final Runnable goIdle = new Runnable() {
        @Override public void run() {
            // A paused screen can still get the tail of a key press (the OK that opened an
            // app arrives before the app's window takes focus), so check again before starting.
            if (!resumed || !hasWindowFocus()) {
                Log.i("CleanFrameIdle", "not in front, art mode skipped");
                return;
            }
            startActivity(new Intent(IdleActivity.this, ArtModeActivity.class));
        }
    };

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        resetIdle();
    }

    @Override protected void onPause() {
        resumed = false;
        idleHandler.removeCallbacks(goIdle);
        super.onPause();
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        // Key events can still reach this window after an app has been opened from it;
        // re-arming the timer then would start art mode over that app minutes later.
        if (resumed) resetIdle();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && resumed) resetIdle();
        else if (!hasFocus) idleHandler.removeCallbacks(goIdle);
    }

    private void resetIdle() {
        idleHandler.removeCallbacks(goIdle);
        int minutes = Prefs.idleMinutes(this);
        if (minutes > 0) idleHandler.postDelayed(goIdle, minutes * 60_000L);
    }
}
