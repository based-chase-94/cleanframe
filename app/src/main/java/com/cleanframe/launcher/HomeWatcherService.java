package com.cleanframe.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always routes Home to the Amazon launcher and won't let another app be the default.
 *
 * The main route is catching the Home key itself: the press never reaches the system, so
 * CleanFrame opens straight away. Watching for the Amazon home screen is only a fallback,
 * because once the system has handled a Home press Android holds back activity starts from
 * background apps for about five seconds, and the redirect arrives late or not at all.
 * Holding Home opens Fire TV settings, since the Amazon long-press menu can't be reached.
 */
public class HomeWatcherService extends AccessibilityService {
    private static final String[] AMAZON_LAUNCHERS = {"com.amazon.tv.launcher", "com.amazon.firetv.launcher"};
    private static final String[] FIRE_SETTINGS = {"com.amazon.tv.settings.v2", "com.amazon.tv.settings"};
    private long lastLaunch;
    private boolean homeLongPressed;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        applyKeyFilter();
    }

    private static HomeWatcherService running;

    static boolean isRunning() { return running != null; }

    /** Settings calls this after the key-intercept option changes. */
    static void refresh() {
        if (running != null) running.applyKeyFilter();
    }

    @Override public void onCreate() {
        super.onCreate();
        running = this;
    }

    @Override public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
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
        // Remembered so Settings can show which Amazon screen appeared, for troubleshooting.
        Prefs.setLastAmazonScreen(this, className);
        android.util.Log.i("CleanFrameHome", "Amazon launcher window: " + className);
        if (!Prefs.homeCapture(this)) return;
        if (isHomeScreen(className) || Prefs.homeCaptureAllScreens(this)) goHome();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (!Prefs.homeCapture(this) || !Prefs.homeKeyIntercept(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                homeLongPressed = false;
            } else if (!homeLongPressed && (event.isLongPress() || event.getRepeatCount() >= 5)) {
                homeLongPressed = true;
                openFireSettings();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && !homeLongPressed) {
            goHome();
        }
        return true;
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

    @Override public void onInterrupt() {}

    private static boolean isAmazonLauncher(String pkg) {
        for (String p : AMAZON_LAUNCHERS) if (p.equals(pkg)) return true;
        return false;
    }

    /**
     * The Amazon home activity's class name has changed between Fire OS releases
     * (HomeActivity, HomeActivity_vNext, ...). Toasts and dialogs from the launcher are ignored.
     */
    private static boolean isHomeScreen(String className) {
        if (className.startsWith("android.")) return false;
        return className.contains("HomeActivity") || className.endsWith(".MainActivity")
                || className.endsWith("LauncherActivity");
    }

    private void goHome() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 700) return;
        lastLaunch = now;
        android.util.Log.i("CleanFrameHome", "opening CleanFrame");
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }
}
