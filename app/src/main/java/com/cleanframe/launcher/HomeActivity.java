package com.cleanframe.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;

/** The home screen. Home only launches; arranging lives in Settings. */
public class HomeActivity extends IdleActivity implements ArtRepository.Listener, HomeScreen.Actions {
    private static boolean inFront;

    private HomeScreen screen;
    private String focusedPkg;
    private ArtRepository repo;
    /** Set when Home brought this screen up, so the next resume shows a new painting. */
    private boolean homePressed = true;

    /** True while the home screen is the resumed activity. */
    static boolean isInFront() { return inFront; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = ArtRepository.get(this);
        screen = new HomeScreen(this, this);
        setContentView(screen);
    }

    @Override protected void onResume() {
        super.onResume();
        inFront = true;
        repo.addListener(this);
        repo.ensureStocked();
        screen.bindApps(HomeScreen.homeApps(this), focusedPkg);
        refreshBackground(false, homePressed);
        homePressed = false;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) HomeWatcherService.onHomeShown();
    }

    @Override public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // Only uncover once this window is really on screen, or the Amazon screen flashes through.
        getWindow().getDecorView().post(new Runnable() {
            @Override public void run() { HomeWatcherService.onHomeShown(); }
        });
    }

    @Override protected void onPause() {
        inFront = false;
        repo.removeListener(this);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home jumps back to the first app.
        focusedPkg = null;
        homePressed = true;
        screen.focusFirst();
    }

    @Override public void onBackPressed() {
        // Home is the bottom of the stack; Back does nothing here.
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            onSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onArtworkAdded() {
        if (!screen.background.hasArtwork()) refreshBackground(true, false);
    }

    @Override public void onApp(AppCatalog.AppEntry app) {
        focusedPkg = app.pkg;
        AppCatalog.launch(this, app.pkg, app.label);
    }

    @Override public void onArtMode() { startActivity(new Intent(this, ArtModeActivity.class)); }
    @Override public void onAllApps() { startActivity(new Intent(this, AllAppsActivity.class)); }
    @Override public void onSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
    @Override public void onAddApps() { startActivity(new Intent(this, HomeEditorActivity.class)); }

    private void refreshBackground(final boolean animate, boolean pressed) {
        if (!Prefs.homeBackgroundArt(this)) {
            screen.clearArtwork();
            return;
        }
        final ArtRepository.Artwork shown = HomeScreen.homeArtwork(this, repo, pressed);
        if (shown == null || screen.isShowing(shown)) return;
        Bitmap ready = HomeScreen.decoded(shown);
        if (ready != null) {
            screen.showArtwork(shown, ready, animate);
            return;
        }
        repo.loadBitmap(shown, new ArtRepository.BitmapCallback() {
            @Override public void onBitmap(Bitmap b) {
                if (b != null) screen.showArtwork(shown, b, animate || screen.background.hasArtwork());
            }
        });
    }
}
