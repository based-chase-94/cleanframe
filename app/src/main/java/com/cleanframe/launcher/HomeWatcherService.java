package com.cleanframe.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always routes Home to the Amazon launcher and won't let another app be the default.
 *
 * Two routes, whichever this Fire OS build allows:
 * <ol>
 * <li>Catch the Home key itself, so the press never reaches the system. Holding Home opens
 *     Fire TV settings, since the Amazon long-press menu can't be reached that way.</li>
 * <li>Watch for the Amazon home screen. After the system handles a Home press, Android holds
 *     back activity starts from background apps for about five seconds. So the service covers
 *     the Amazon screen at once with an overlay showing the current artwork (accessibility
 *     overlays need no activity start), then keeps asking for CleanFrame until it is in front.</li>
 * </ol>
 */
public class HomeWatcherService extends AccessibilityService {
    private static final String TAG = "CleanFrameHome";
    private static final String[] AMAZON_LAUNCHERS = {"com.amazon.tv.launcher", "com.amazon.firetv.launcher"};
    private static final String[] FIRE_SETTINGS = {"com.amazon.tv.settings.v2", "com.amazon.tv.settings"};
    private static final long RETRY_MS = 1000;
    private static final long GIVE_UP_MS = 9000;

    private static HomeWatcherService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeLongPressed;
    private long redirectStarted;
    private View cover;

    private final Runnable retry = new Runnable() {
        @Override public void run() {
            if (HomeActivity.isInFront()) {
                Log.i(TAG, "CleanFrame is in front after " + (SystemClock.elapsedRealtime() - redirectStarted) + " ms");
                finishRedirect();
                return;
            }
            if (SystemClock.elapsedRealtime() - redirectStarted > GIVE_UP_MS) {
                Log.w(TAG, "gave up waiting for CleanFrame");
                finishRedirect();
                return;
            }
            startHome();
            handler.postDelayed(this, RETRY_MS);
        }
    };

    static boolean isRunning() { return running != null; }

    /** Settings calls this after the key-intercept option changes. */
    static void refresh() {
        if (running != null) running.applyKeyFilter();
    }

    /** HomeActivity calls this when it comes to the front, so the cover can go. */
    static void onHomeShown() {
        if (running != null && running.redirectStarted != 0) running.finishRedirect();
    }

    @Override public void onCreate() {
        super.onCreate();
        running = this;
    }

    @Override public void onDestroy() {
        removeCover();
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        applyKeyFilter();
        Log.i(TAG, "service connected, key filter " + Prefs.homeKeyIntercept(this));
    }

    private void applyKeyFilter() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if (Prefs.homeKeyIntercept(this)) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || !isAmazonLauncher(pkg.toString())) return;
        String className = cls == null ? "" : cls.toString();
        Log.i(TAG, "Amazon launcher window: " + className);
        // Remembered so Settings can show which Amazon screen appeared, for troubleshooting.
        Prefs.setLastAmazonScreen(this, className);
        if (!Prefs.homeCapture(this)) return;
        if (isHomeScreen(className) || Prefs.homeCaptureAllScreens(this)) redirect(true);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Log.i(TAG, "key " + KeyEvent.keyCodeToString(event.getKeyCode()));
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        Prefs.setHomeKeySeen(this);
        if (!Prefs.homeCapture(this) || !Prefs.homeKeyIntercept(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                homeLongPressed = false;
            } else if (!homeLongPressed && (event.isLongPress() || event.getRepeatCount() >= 5)) {
                homeLongPressed = true;
                openFireSettings();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && !homeLongPressed) {
            redirect(false);
        }
        return true;
    }

    @Override public void onInterrupt() {}

    private static boolean isAmazonLauncher(String pkg) {
        for (String p : AMAZON_LAUNCHERS) if (p.equals(pkg)) return true;
        return false;
    }

    /**
     * The Amazon home activity's class name has changed between Fire OS releases
     * (HomeActivity, HomeActivity_vNext on Fire OS 7.7, ...). Toasts and dialogs are ignored.
     */
    private static boolean isHomeScreen(String className) {
        if (className.startsWith("android.")) return false;
        return className.contains("HomeActivity") || className.endsWith(".MainActivity")
                || className.endsWith("LauncherActivity");
    }

    private void redirect(boolean coverAmazon) {
        if (redirectStarted != 0) return;  // Already on it.
        redirectStarted = SystemClock.elapsedRealtime();
        Log.i(TAG, "opening CleanFrame" + (coverAmazon ? " over Amazon home" : ""));
        if (coverAmazon) showCover();
        startHome();
        handler.postDelayed(retry, RETRY_MS);
    }

    private void finishRedirect() {
        handler.removeCallbacks(retry);
        redirectStarted = 0;
        // Give the home screen a moment to draw before uncovering it.
        handler.postDelayed(new Runnable() {
            @Override public void run() { removeCover(); }
        }, 250);
    }

    private void startHome() {
        try {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "start failed", e);
        }
    }

    /**
     * A full-screen overlay with the current artwork, shown over the Amazon home screen until
     * CleanFrame arrives. It takes key focus so presses don't land on the hidden Amazon screen;
     * Back removes it as an escape hatch.
     */
    private void showCover() {
        if (cover != null) return;
        final ArtCanvasView art = new ArtCanvasView(this);
        art.setFocusable(true);
        art.setFocusableInTouchMode(true);
        art.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (keyCode == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(retry);
                    redirectStarted = 0;
                    removeCover();
                }
                return true;
            }
        });
        ArtRepository repo = ArtRepository.get(this);
        ArtRepository.Artwork a = repo.find(Prefs.homeArtId(this));
        if (a != null && Prefs.homeBackgroundArt(this)) {
            repo.loadBitmap(a, new ArtRepository.BitmapCallback() {
                @Override public void onBitmap(Bitmap b) {
                    if (b != null && cover == art) art.setArtwork(b, false);
                }
            });
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(art, lp);
            cover = art;
            art.requestFocus();
        } catch (Exception e) {
            Log.w(TAG, "could not show cover", e);
        }
    }

    private void removeCover() {
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (Exception ignored) {
            // Already gone.
        }
        cover = null;
    }

    private void openFireSettings() {
        for (String pkg : FIRE_SETTINGS) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
        }
        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
