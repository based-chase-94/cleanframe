package com.cleanframe.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/**
 * The home screen: artwork behind a short list of big app names, clock top-left, and
 * art mode / all apps / settings top-right. Home only launches; arranging lives in Settings.
 */
public class HomeActivity extends IdleActivity implements ArtRepository.Listener {
    private ArtCanvasView background;
    private TextView credit;
    private LinearLayout list;
    private ImageView artButton;
    private ImageView appsButton;
    private ImageView settingsButton;
    private String focusedPkg;
    private ArtRepository repo;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = ArtRepository.get(this);

        FrameLayout root = new FrameLayout(this);
        background = new ArtCanvasView(this);
        root.addView(background, match());

        // Darken the left side and the top so white type stays readable on any painting.
        View sideScrim = new View(this);
        sideScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(170, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(sideScrim, match());
        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(topScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 140)));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(40);
        clock.setTypeface(Ui.REGULAR);
        clock.setTextColor(Color.WHITE);
        Ui.shadow(clock);
        FrameLayout.LayoutParams clockLp = wrap(Gravity.TOP | Gravity.START);
        clockLp.setMargins(Ui.dp(this, 64), Ui.dp(this, 40), 0, 0);
        root.addView(clock, clockLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        artButton = Ui.iconButton(this, R.drawable.ic_art, "Art mode");
        appsButton = Ui.iconButton(this, R.drawable.ic_apps, "All apps");
        settingsButton = Ui.iconButton(this, R.drawable.ic_settings, "Settings");
        int size = Ui.dp(this, 52);
        for (ImageView b : new ImageView[]{artButton, appsButton, settingsButton}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = Ui.dp(this, 10);
            controls.addView(b, lp);
        }
        artButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, ArtModeActivity.class)); }
        });
        appsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, AllAppsActivity.class)); }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, SettingsActivity.class)); }
        });
        FrameLayout.LayoutParams controlsLp = wrap(Gravity.TOP | Gravity.END);
        controlsLp.setMargins(0, Ui.dp(this, 32), Ui.dp(this, 56), 0);
        root.addView(controls, controlsLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 48));
        scroll.setFillViewport(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollLp.setMargins(Ui.dp(this, 48), Ui.dp(this, 120), 0, 0);
        root.addView(scroll, scrollLp);

        credit = Ui.text(this, "", 12, Ui.REGULAR, Color.argb(170, 255, 255, 255));
        Ui.shadow(credit);
        credit.setGravity(Gravity.END);
        credit.setMaxWidth(Ui.dp(this, 420));
        FrameLayout.LayoutParams creditLp = wrap(Gravity.BOTTOM | Gravity.END);
        creditLp.setMargins(0, 0, Ui.dp(this, 56), Ui.dp(this, 28));
        root.addView(credit, creditLp);

        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        repo.addListener(this);
        repo.ensureStocked();
        buildList();
        refreshBackground(false);
    }

    @Override protected void onPause() {
        repo.removeListener(this);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already home jumps back to the first app.
        focusedPkg = null;
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    @Override public void onBackPressed() {
        // Home is the bottom of the stack; Back does nothing here.
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onArtworkAdded() {
        if (!background.hasArtwork()) refreshBackground(true);
    }

    private void buildList() {
        List<AppCatalog.AppEntry> apps = AppCatalog.load(this);
        Map<String, AppCatalog.AppEntry> byPkg = AppCatalog.index(apps);
        if (!Prefs.homeInitialized(this)) Prefs.setHomeOrder(this, AppCatalog.defaultHome(apps));

        list.removeAllViews();
        View focusTarget = null;
        for (String pkg : Prefs.homeOrder(this)) {
            AppCatalog.AppEntry app = byPkg.get(pkg);
            if (app == null) continue;  // Uninstalled; keep its slot in case it comes back.
            View row = appRow(app);
            list.addView(row);
            if (pkg.equals(focusedPkg)) focusTarget = row;
        }
        if (list.getChildCount() == 0) {
            View row = actionRow("Add apps to Home", new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, HomeEditorActivity.class)); }
            });
            list.addView(row);
        }

        View first = list.getChildAt(0);
        first.setNextFocusUpId(artButton.getId());
        for (ImageView b : new ImageView[]{artButton, appsButton, settingsButton}) b.setNextFocusDownId(first.getId());
        View last = list.getChildAt(list.getChildCount() - 1);
        last.setNextFocusDownId(last.getId());
        (focusTarget != null ? focusTarget : first).requestFocus();
    }

    private View appRow(final AppCatalog.AppEntry app) {
        LinearLayout row = baseRow();
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon(getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = Ui.dp(this, 46);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(s, s);
        iconLp.rightMargin = Ui.dp(this, 22);
        row.addView(icon, iconLp);
        TextView name = rowLabel(app.label);
        row.addView(name);
        row.setContentDescription(app.label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                focusedPkg = app.pkg;
                AppCatalog.launch(HomeActivity.this, app.pkg, app.label);
            }
        });
        styleFocus(row, false);
        return row;
    }

    private View actionRow(String label, View.OnClickListener click) {
        LinearLayout row = baseRow();
        row.addView(rowLabel(label));
        row.setOnClickListener(click);
        styleFocus(row, false);
        return row;
    }

    private LinearLayout baseRow() {
        final LinearLayout row = new LinearLayout(this);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 32), Ui.dp(this, 2));
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) { styleFocus(row, hasFocus); }
        });
        return row;
    }

    private TextView rowLabel(String label) {
        TextView name = Ui.text(this, label, 54, Ui.DISPLAY, Color.WHITE);
        name.setLetterSpacing(-0.02f);
        name.setSingleLine(true);
        name.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        Ui.shadow(name);
        return name;
    }

    /** The "type" focus style: the focused name is bright and nudged right, the rest recede. */
    private void styleFocus(View row, boolean focused) {
        row.animate()
                .alpha(focused ? 1f : 0.55f)
                .translationX(focused ? Ui.dp(this, 14) : 0)
                .setDuration(160)
                .start();
    }

    private void refreshBackground(boolean animate) {
        if (!Prefs.homeBackgroundArt(this)) {
            background.setArtwork(null, false);
            credit.setText("");
            return;
        }
        ArtRepository.Artwork art = repo.find(Prefs.homeArtId(this));
        long age = System.currentTimeMillis() - Prefs.homeArtSince(this);
        boolean stale = age > Prefs.artIntervalMinutes(this) * 60_000L;
        if (art == null || stale) {
            ArtRepository.Artwork next = repo.pickNext();
            if (next != null) art = next;
        } else if (background.hasArtwork()) {
            return;  // Still current and already on screen.
        }
        if (art == null) return;
        final ArtRepository.Artwork shown = art;
        Prefs.setHomeArt(this, shown.id);
        repo.loadBitmap(shown, new ArtRepository.BitmapCallback() {
            @Override public void onBitmap(Bitmap b) {
                if (b == null) return;
                background.setArtwork(b, animate || background.hasArtwork());
                credit.setText(shown.title + (shown.artist.isEmpty() ? "" : " — " + shown.artist) + "\n" + shown.museum);
            }
        });
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }
}
