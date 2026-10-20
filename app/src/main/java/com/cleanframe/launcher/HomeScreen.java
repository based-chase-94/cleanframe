package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The home screen's views: artwork behind a short list of big app names, clock top-left,
 * and art mode / all apps / settings top-right. Used by {@link HomeActivity} and, while
 * Fire OS holds back app launches after a Home press, by {@link HomeWatcherService}'s cover.
 */
final class HomeScreen extends FrameLayout {
    interface Actions {
        void onApp(AppCatalog.AppEntry app);
        void onArtMode();
        void onAllApps();
        void onSettings();
        void onAddApps();
    }

    /** Where the app list starts and stops, in dp, so up to seven names fit without scrolling. */
    private static final int LIST_TOP = 112;
    private static final int LIST_PAD_TOP = 12;
    private static final int LIST_PAD_BOTTOM = 36;
    private static final float MAX_NAME_SP = 60;

    final ArtCanvasView background;
    private final TextView credit;
    private final LinearLayout list;
    private final ImageView artButton;
    private final ImageView appsButton;
    private final ImageView settingsButton;
    private final TextView weatherDot;
    private final ImageView weatherIcon;
    private final TextView weatherTemp;
    /** Re-checks the weather while this screen is up; the fetch itself only runs when it's due. */
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            showWeather();
            Weather.refresh(getContext(), new Runnable() {
                @Override public void run() { showWeather(); }
            });
            postDelayed(this, 5 * 60_000L);
        }
    };
    private final Actions actions;
    /**
     * The last painting either copy of the home screen decoded. The Home-button cover decodes
     * the new painting first, and the home screen reuses it so it never draws the old one
     * underneath as the cover lifts.
     */
    private static String lastArtId;
    private static Bitmap lastBitmap;

    /** The painting on screen now, or null. */
    private String shownArtId;
    /** App name size for the current list, in sp. */
    private float nameSp = MAX_NAME_SP;

    HomeScreen(Context c, Actions a) {
        super(c);
        actions = a;
        background = new ArtCanvasView(c);
        addView(background, match());

        // Darken the left side and the top so white type stays readable on any painting.
        View sideScrim = new View(c);
        sideScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(170, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        addView(sideScrim, match());
        View topScrim = new View(c);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        addView(topScrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 140)));

        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(48);
        clock.setTypeface(Ui.serif(c));
        clock.setTextColor(Color.WHITE);
        Ui.shadow(clock);

        // Clock · weather icon · temperature, on one line.
        LinearLayout timeLine = new LinearLayout(c);
        timeLine.setOrientation(LinearLayout.HORIZONTAL);
        timeLine.setGravity(Gravity.CENTER_VERTICAL);
        timeLine.addView(clock);
        weatherDot = Ui.text(c, "\u00b7", 48, Ui.serif(c), Color.argb(170, 255, 255, 255));
        Ui.shadow(weatherDot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotLp.leftMargin = Ui.dp(c, 14);
        dotLp.rightMargin = Ui.dp(c, 14);
        timeLine.addView(weatherDot, dotLp);
        weatherIcon = new ImageView(c);
        weatherIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = Ui.dp(c, 42);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = Ui.dp(c, 8);
        timeLine.addView(weatherIcon, iconLp);
        weatherTemp = Ui.text(c, "", 48, Ui.serif(c), Color.WHITE);
        Ui.shadow(weatherTemp);
        timeLine.addView(weatherTemp);
        LayoutParams timeLp = wrap(Gravity.TOP | Gravity.START);
        timeLp.setMargins(Ui.dp(c, 64), Ui.dp(c, 34), 0, 0);
        addView(timeLine, timeLp);
        showWeather();

        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        artButton = Ui.iconButton(c, R.drawable.ic_art, "Art mode");
        appsButton = Ui.iconButton(c, R.drawable.ic_apps, "All apps");
        settingsButton = Ui.iconButton(c, R.drawable.ic_settings, "Settings");
        int size = Ui.dp(c, 52);
        for (ImageView b : new ImageView[]{artButton, appsButton, settingsButton}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = Ui.dp(c, 10);
            controls.addView(b, lp);
        }
        artButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { actions.onArtMode(); }
        });
        appsButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { actions.onAllApps(); }
        });
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { actions.onSettings(); }
        });
        LayoutParams controlsLp = wrap(Gravity.TOP | Gravity.END);
        controlsLp.setMargins(0, Ui.dp(c, 32), Ui.dp(c, 56), 0);
        addView(controls, controlsLp);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(c, LIST_PAD_TOP), 0, Ui.dp(c, LIST_PAD_BOTTOM));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams scrollLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollLp.setMargins(Ui.dp(c, 48), Ui.dp(c, LIST_TOP), 0, 0);
        addView(scroll, scrollLp);

        credit = Ui.text(c, "", 16, Ui.serifItalic(c), Color.argb(190, 255, 255, 255));
        Ui.shadow(credit);
        credit.setGravity(Gravity.END);
        credit.setMaxWidth(Ui.dp(c, 420));
        LayoutParams creditLp = wrap(Gravity.BOTTOM | Gravity.END);
        creditLp.setMargins(0, 0, Ui.dp(c, 56), Ui.dp(c, 28));
        addView(credit, creditLp);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshWeather();
    }

    /** Shows the saved weather now and fetches new conditions if they're due. */
    void refreshWeather() {
        removeCallbacks(weatherTick);
        weatherTick.run();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(weatherTick);
        super.onDetachedFromWindow();
    }

    /** Shows the latest weather beside the clock, or nothing if there's no recent reading. */
    void showWeather() {
        Weather.Now w = Weather.current(getContext());
        int v = w == null ? GONE : VISIBLE;
        weatherDot.setVisibility(v);
        weatherIcon.setVisibility(v);
        weatherTemp.setVisibility(v);
        if (w == null) return;
        weatherIcon.setImageResource(w.icon);
        weatherTemp.setText(w.fahrenheit + "\u00b0");
    }

    /** The apps on Home, in order, skipping any that are no longer installed. */
    static List<AppCatalog.AppEntry> homeApps(Context c) {
        List<AppCatalog.AppEntry> apps = AppCatalog.load(c);
        Map<String, AppCatalog.AppEntry> byPkg = AppCatalog.index(apps);
        if (!Prefs.homeInitialized(c)) Prefs.setHomeOrder(c, AppCatalog.defaultHome(apps));
        List<String> order = Prefs.homeOrder(c);
        if (AppCatalog.addNewDefaults(c, apps, order)) Prefs.setHomeOrder(c, order);
        List<AppCatalog.AppEntry> out = new ArrayList<>();
        for (String pkg : order) {
            AppCatalog.AppEntry app = byPkg.get(pkg);
            // Uninstalled apps keep their slot in case they come back.
            if (app != null && out.size() < AppCatalog.HOME_MAX) out.add(app);
        }
        return out;
    }

    /**
     * The painting Home should show. A new one is picked after the TV restarts, when Home is
     * pressed, and when the art interval runs out; otherwise the current one stays. Home
     * presses within a few seconds of a pick count as the same press, since the Home-button
     * cover and the home screen both see it.
     */
    static ArtRepository.Artwork homeArtwork(Context c, ArtRepository repo, boolean homePressed) {
        ArtRepository.Artwork art = repo.find(Prefs.homeArtId(c));
        long now = System.currentTimeMillis();
        long since = Prefs.homeArtSince(c);
        long bootedAt = now - SystemClock.elapsedRealtime();
        boolean due = art == null
                || since < bootedAt
                || now - since > Prefs.artIntervalMinutes(c) * 60_000L
                || (homePressed && now - since > 15_000L);
        if (due) {
            ArtRepository.Artwork next = repo.pickNext();
            if (next != null) {
                art = next;
                Prefs.setHomeArt(c, art.id);
            }
        }
        return art;
    }

    /** {@code a}'s bitmap if it was the last one decoded for Home, else null. */
    static synchronized Bitmap decoded(ArtRepository.Artwork a) {
        return a.id.equals(lastArtId) ? lastBitmap : null;
    }

    /** True if {@code a} is already the painting on screen. */
    boolean isShowing(ArtRepository.Artwork a) {
        return a != null && a.id.equals(shownArtId) && background.hasArtwork();
    }

    /** Rebuilds the list and focuses {@code focusPkg}, or the first row. */
    void bindApps(List<AppCatalog.AppEntry> apps, String focusPkg) {
        list.removeAllViews();
        nameSp = nameSize(Math.max(apps.size(), 4));
        View focusTarget = null;
        for (AppCatalog.AppEntry app : apps) {
            View row = appRow(app);
            list.addView(row);
            if (app.pkg.equals(focusPkg)) focusTarget = row;
        }
        if (list.getChildCount() == 0) {
            View row = baseRow();
            ((LinearLayout) row).addView(rowLabel("Add apps to Home"));
            row.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { actions.onAddApps(); }
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

    void focusFirst() {
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    void showArtwork(ArtRepository.Artwork a, Bitmap b, boolean animate) {
        shownArtId = a.id;
        synchronized (HomeScreen.class) {
            lastArtId = a.id;
            lastBitmap = b;
        }
        background.setArtwork(b, animate);
        credit.setText(a.title + (a.artist.isEmpty() ? "" : " — " + a.artist) + "\n" + a.museum);
    }

    void clearArtwork() {
        shownArtId = null;
        background.setArtwork(null, false);
        credit.setText("");
    }

    private View appRow(final AppCatalog.AppEntry app) {
        LinearLayout row = baseRow();
        // Logos sit in one fixed box so the names line up: square marks fill its height,
        // wordmarks its width.
        ImageView logo = new ImageView(getContext());
        logo.setImageDrawable(AppLogos.logo(getContext(), app));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAlpha(0.95f);
        int h = Ui.dp(getContext(), nameSp * 0.68f);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Math.round(h * 2.1f), h);
        logoLp.rightMargin = Ui.dp(getContext(), nameSp * 0.45f);
        row.addView(logo, logoLp);
        row.addView(rowLabel(app.label));
        row.setContentDescription(app.label);
        row.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { actions.onApp(app); }
        });
        return row;
    }

    private LinearLayout baseRow() {
        final LinearLayout row = new LinearLayout(getContext());
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(Ui.dp(getContext(), 16), 0, Ui.dp(getContext(), 32), 0);
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) { styleFocus(row, hasFocus); }
        });
        styleFocus(row, false);
        return row;
    }

    private TextView rowLabel(String label) {
        TextView name = Ui.text(getContext(), label, nameSp, Ui.serif(getContext()), Color.WHITE);
        name.setSingleLine(true);
        int pad = Ui.dp(getContext(), nameSp * NAME_PAD);
        name.setPadding(0, pad, 0, pad);
        Ui.shadow(name);
        return name;
    }

    /** Space above and below each name, as a fraction of its size; Instrument Serif lines are 1.3 tall. */
    private static final float NAME_PAD = 0.06f;

    /** The largest name size at which {@code rows} names fit between the clock and the bottom edge. */
    private float nameSize(int rows) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenDp = dm.heightPixels / dm.density;
        float rowDp = (screenDp - LIST_TOP - LIST_PAD_TOP - LIST_PAD_BOTTOM) / rows;
        float sp = rowDp / (1.3f + 2 * NAME_PAD) * dm.density / dm.scaledDensity;
        return Math.min(MAX_NAME_SP, sp);
    }

    /** The "type" focus style: the focused name is bright and nudged right, the rest recede. */
    private void styleFocus(View row, boolean focused) {
        row.animate()
                .alpha(focused ? 1f : 0.55f)
                .translationX(focused ? Ui.dp(getContext(), 14) : 0)
                .setDuration(160)
                .start();
    }

    private static LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LayoutParams wrap(int gravity) {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }
}
