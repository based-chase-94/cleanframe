package com.cleanframe.launcher;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Every launchable app as a grid. OK opens; Menu (or holding OK) adds or removes it from Home. */
public class AllAppsActivity extends IdleActivity {
    private static final int COLUMNS = 6;
    private LinearLayout grid;
    private String focusedPkg;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 14, 16));
        root.setPadding(Ui.dp(this, 64), Ui.dp(this, 40), Ui.dp(this, 64), 0);

        root.addView(Ui.text(this, "All apps", 34, Ui.DISPLAY, Color.WHITE));
        TextView hint = Ui.text(this, "OK opens · Menu ☰ or hold OK to add to Home or remove from it",
                14, Ui.REGULAR, Color.argb(150, 255, 255, 255));
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 40));
        scroll.setVerticalScrollBarEnabled(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        grid.removeAllViews();
        List<AppCatalog.AppEntry> apps = AppCatalog.load(this);
        List<String> home = Prefs.homeOrder(this);
        LinearLayout row = null;
        View focusTarget = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            View tile = tile(apps.get(i), home.contains(apps.get(i).pkg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 104));
            lp.setMargins(0, 0, Ui.dp(this, 10), Ui.dp(this, 12));
            row.addView(tile, lp);
            if (apps.get(i).pkg.equals(focusedPkg)) focusTarget = tile;
        }
        if (focusTarget != null) focusTarget.requestFocus();
        else if (grid.getChildCount() > 0) ((ViewGroup) grid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    private View tile(final AppCatalog.AppEntry app, boolean onHome) {
        PackageManager pm = getPackageManager();
        final FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.tile_bg);
        tile.setContentDescription(app.label);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        Drawable banner = app.banner(pm);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (banner != null) {
            img.setImageDrawable(banner);
            inner.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 62)));
        } else {
            img.setImageDrawable(app.icon(pm));
            inner.addView(img, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        }
        TextView label = Ui.text(this, app.label, 13, Ui.REGULAR, Color.WHITE);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(this, 8), 0, 0);
        inner.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tile.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (onHome) {
            // A small white dot marks apps that are already on Home.
            View dot = new View(this);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(Color.WHITE);
            dot.setBackground(circle);
            int d = Ui.dp(this, 8);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(d, d, Gravity.TOP | Gravity.END);
            dotLp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
            tile.addView(dot, dotLp);
        }

        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                tile.animate().scaleX(hasFocus ? 1.08f : 1f).scaleY(hasFocus ? 1.08f : 1f).setDuration(140).start();
                if (hasFocus) focusedPkg = app.pkg;
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AppCatalog.launch(AllAppsActivity.this, app.pkg, app.label); }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                toggleHome(app);
                return true;
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (keyCode == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                    toggleHome(app);
                    return true;
                }
                return keyCode == KeyEvent.KEYCODE_MENU;
            }
        });
        return tile;
    }

    private void toggleHome(AppCatalog.AppEntry app) {
        List<String> home = Prefs.homeOrder(this);
        List<String> recent = Prefs.recentlyRemoved(this);
        if (home.remove(app.pkg)) {
            recent.remove(app.pkg);
            recent.add(0, app.pkg);
            Toast.makeText(this, app.label + " removed from Home", Toast.LENGTH_SHORT).show();
        } else {
            home.add(app.pkg);
            recent.remove(app.pkg);
            Toast.makeText(this, app.label + " added to Home", Toast.LENGTH_SHORT).show();
        }
        Prefs.setHomeOrder(this, home);
        Prefs.setRecentlyRemoved(this, recent);
        focusedPkg = app.pkg;
        build();
    }
}
