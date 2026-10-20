package com.cleanframe.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
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
import android.widget.FrameLayout;

/**
 * Fire OS always routes Home to the Amazon launcher and won't let another app be the default.
 *
 * Two routes, whichever this Fire OS build allows:
 * <ol>
 * <li>Catch the Home key itself, so the press never reaches the system. Holding Home opens
 *     Fire TV settings, since the Amazon long-press menu can't be reached that way.
 *     (Fire OS 7.7 on the TCL 65S450F does not pass Home to accessibility services.)</li>
 * <li>Watch for the Amazon home screen. After the system handles a Home press, Android queues
 *     activity starts from background apps for five seconds. So the service covers the Amazon
 *     screen at once with a working copy of the CleanFrame home screen (accessibility overlays
 *     need no activity start). Choosing an app there queues its launch, which runs when the
 *     five seconds are up; the cover goes away once the real home screen is in front.</li>
 * </ol>
 */
public class HomeWatcherService extends AccessibilityService {
    private static final String TAG = "CleanFrameHome";
    private static final String[] AMAZON_LAUNCHERS = {"com.amazon.tv.launcher", "com.amazon.firetv.launcher"};
    private static final String[] FIRE_SETTINGS = {"com.amazon.tv.settings.v2", "com.amazon.tv.settings"};
    /** One more start if the queued one hasn't landed a little after Android's 5 s hold. */
    private static final long RETRY_MS = 6500;
    private static final long GIVE_UP_MS = 12000;

    private static HomeWatcherService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeLongPressed;
    private long redirectStarted;
    /** Amazon home windows are ignored until then, after an app was chosen on the cover. */
    private long quietUntil;
    private View cover;

    private final Runnable coverTimeout = new Runnable() {
        @Override public void run() {
            Log.w(TAG, "cover timed out");
            cancelRedirect();
            removeCover();
        }
    };

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
        if (running != null && (running.redirectStarted != 0 || running.cover != null)) running.finishRedirect();
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
        // This service keeps CleanFrame running, so the bookmarks page stays reachable.
        BookmarkServer.start(this);
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
        if (pkg == null) return;
        String className = cls == null ? "" : cls.toString();
        if (cover != null && isOtherScreen(pkg.toString(), className)) {
            // Whatever was queued from the cover has arrived.
            Log.i(TAG, "in front: " + pkg + "/" + className);
            finishRedirect();
            return;
        }
        if (!isAmazonLauncher(pkg.toString())) return;
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

    /** An app's screen, or one of CleanFrame's own; not the Amazon launcher, nor the cover itself. */
    private boolean isOtherScreen(String pkg, String className) {
        if (isAmazonLauncher(pkg)) return false;
        if (pkg.equals(getPackageName())) return className.startsWith(pkg + ".") && className.endsWith("Activity");
        return !pkg.equals("android") && !pkg.equals("com.android.systemui");
    }

    private void redirect(boolean coverAmazon) {
        if (redirectStarted != 0) return;  // Already on it.
        if (SystemClock.elapsedRealtime() < quietUntil) return;
        redirectStarted = SystemClock.elapsedRealtime();
        Log.i(TAG, "opening CleanFrame" + (coverAmazon ? " over Amazon home" : ""));
        if (coverAmazon) showCover();
        startHome();
        handler.postDelayed(retry, RETRY_MS);
    }

    /** Stops the retries, e.g. after something was chosen on the cover. */
    private void cancelRedirect() {
        handler.removeCallbacks(retry);
        redirectStarted = 0;
    }

    /** Something was chosen on the cover: let its queued launch land, then uncover. */
    private void chosenOnCover() {
        cancelRedirect();
        quietUntil = SystemClock.elapsedRealtime() + 7000;
    }

    private void finishRedirect() {
        handler.removeCallbacks(retry);
        handler.removeCallbacks(coverTimeout);
        redirectStarted = 0;
        fadeOutCover();
    }

    private void startHome() {
        try {
            Intent i = new Intent(this, HomeActivity.class);
            // No window animation: the cover already looks like home, so the swap should be a cut.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "start failed", e);
        }
    }

    /**
     * A full-screen overlay copy of the home screen, shown over the Amazon home screen until
     * CleanFrame arrives. It takes key focus so presses don't land on the hidden Amazon screen.
     * Back removes it as an escape hatch.
     */
    private void showCover() {
        if (cover != null) return;
        final Context ctx = this;
        final HomeScreen screen = new HomeScreen(ctx, new HomeScreen.Actions() {
            @Override public void onApp(AppCatalog.AppEntry app) {
                // Queued by Android behind the home screen, so the app ends up in front.
                chosenOnCover();
                AppCatalog.launch(ctx, app.pkg, app.label);
            }
            @Override public void onForYou() { open(ForYouActivity.class); }
            @Override public void onArtMode() { open(ArtModeActivity.class); }
            @Override public void onAllApps() { open(AllAppsActivity.class); }
            @Override public void onSettings() { open(SettingsActivity.class); }
            @Override public void onAddApps() { open(HomeEditorActivity.class); }
        });
        FrameLayout root = new FrameLayout(ctx) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) {
                        cancelRedirect();
                        removeCover();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        root.addView(screen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        screen.bindApps(HomeScreen.homeApps(ctx), null);

        ArtRepository repo = ArtRepository.get(this);
        // The cover is the first thing a Home press shows, so it picks the new painting.
        final ArtRepository.Artwork a = HomeScreen.homeArtwork(this, repo, true);
        if (a != null && Prefs.homeBackgroundArt(this)) {
            repo.loadBitmap(a, new ArtRepository.BitmapCallback() {
                @Override public void onBitmap(Bitmap b) {
                    if (b != null && cover != null) screen.showArtwork(a, b, false);
                }
            });
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(root, lp);
            cover = root;
            screen.focusFirst();
            handler.postDelayed(coverTimeout, GIVE_UP_MS);
        } catch (Exception e) {
            Log.w(TAG, "could not show cover", e);
        }
    }

    /** Starts one of CleanFrame's screens from the cover; it is queued like the home screen. */
    private void open(Class<?> activity) {
        chosenOnCover();
        startActivity(new Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Fades the cover out, so the hand-over to the real home screen has no hard cut. */
    private void fadeOutCover() {
        final View v = cover;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() {
                if (cover == v) removeCover();
            }
        }).start();
    }

    private void removeCover() {
        handler.removeCallbacks(coverTimeout);
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
