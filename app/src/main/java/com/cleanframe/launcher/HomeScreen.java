package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

    final ArtCanvasView background;
    private final TextView credit;
    private final LinearLayout list;
    private final ImageView artButton;
    private final ImageView appsButton;
    private final ImageView settingsButton;
    private final Actions actions;

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
        clock.setTextSize(40);
        clock.setTypeface(Ui.REGULAR);
        clock.setTextColor(Color.WHITE);
        Ui.shadow(clock);
        LayoutParams clockLp = wrap(Gravity.TOP | Gravity.START);
        clockLp.setMargins(Ui.dp(c, 64), Ui.dp(c, 40), 0, 0);
        addView(clock, clockLp);

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
        scroll.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 48));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams scrollLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollLp.setMargins(Ui.dp(c, 48), Ui.dp(c, 120), 0, 0);
        addView(scroll, scrollLp);

        credit = Ui.text(c, "", 12, Ui.REGULAR, Color.argb(170, 255, 255, 255));
        Ui.shadow(credit);
        credit.setGravity(Gravity.END);
        credit.setMaxWidth(Ui.dp(c, 420));
        LayoutParams creditLp = wrap(Gravity.BOTTOM | Gravity.END);
        creditLp.setMargins(0, 0, Ui.dp(c, 56), Ui.dp(c, 28));
        addView(credit, creditLp);
    }

    /** The apps on Home, in order, skipping any that are no longer installed. */
    static List<AppCatalog.AppEntry> homeApps(Context c) {
        List<AppCatalog.AppEntry> apps = AppCatalog.load(c);
        Map<String, AppCatalog.AppEntry> byPkg = AppCatalog.index(apps);
        if (!Prefs.homeInitialized(c)) Prefs.setHomeOrder(c, AppCatalog.defaultHome(apps));
        List<AppCatalog.AppEntry> out = new ArrayList<>();
        for (String pkg : Prefs.homeOrder(c)) {
            AppCatalog.AppEntry app = byPkg.get(pkg);
            if (app != null) out.add(app);  // Uninstalled apps keep their slot in case they come back.
        }
        return out;
    }

    /** Rebuilds the list and focuses {@code focusPkg}, or the first row. */
    void bindApps(List<AppCatalog.AppEntry> apps, String focusPkg) {
        list.removeAllViews();
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
        background.setArtwork(b, animate);
        credit.setText(a.title + (a.artist.isEmpty() ? "" : " — " + a.artist) + "\n" + a.museum);
    }

    void clearArtwork() {
        background.setArtwork(null, false);
        credit.setText("");
    }

    private View appRow(final AppCatalog.AppEntry app) {
        LinearLayout row = baseRow();
        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(app.icon(getContext().getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = Ui.dp(getContext(), 46);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(s, s);
        iconLp.rightMargin = Ui.dp(getContext(), 22);
        row.addView(icon, iconLp);
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
        row.setPadding(Ui.dp(getContext(), 16), Ui.dp(getContext(), 2), Ui.dp(getContext(), 32), Ui.dp(getContext(), 2));
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) { styleFocus(row, hasFocus); }
        });
        styleFocus(row, false);
        return row;
    }

    private TextView rowLabel(String label) {
        TextView name = Ui.text(getContext(), label, 54, Ui.DISPLAY, Color.WHITE);
        name.setLetterSpacing(-0.02f);
        name.setSingleLine(true);
        name.setPadding(0, Ui.dp(getContext(), 6), 0, Ui.dp(getContext(), 10));
        Ui.shadow(name);
        return name;
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
