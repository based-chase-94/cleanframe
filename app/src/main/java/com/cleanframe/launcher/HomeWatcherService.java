package com.cleanframe.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always routes Home to the Amazon launcher and won't let another app be the default.
 * This service watches for the Amazon home screen appearing and immediately opens CleanFrame
 * over it. Optionally it can also swallow the Home key itself, which avoids the brief flash of
 * the Amazon screen but disables the long-press Home menu.
 */
public class HomeWatcherService extends AccessibilityService {
    private static final String[] AMAZON_LAUNCHERS = {"com.amazon.tv.launcher", "com.amazon.firetv.launcher"};
    private long lastLaunch;

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
        if (!Prefs.homeCapture(this)) return;
        if (isHomeScreen(className) || Prefs.homeCaptureAllScreens(this)) goHome();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (!Prefs.homeCapture(this) || !Prefs.homeKeyIntercept(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) goHome();
        return true;
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
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }
}
