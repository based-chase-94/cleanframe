package com.cleanframe.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** One list of settings. OK on a row changes its value or opens it. */
public class SettingsActivity extends IdleActivity {
    private interface Value { String get(); }

    private static final class Row {
        final String label;
        final Value value;
        final Runnable action;
        final boolean header;

        Row(String label, Value value, Runnable action) {
            this(label, value, action, false);
        }

        Row(String label, Value value, Runnable action, boolean header) {
            this.label = label;
            this.value = value;
            this.action = action;
            this.header = header;
        }
    }

    private LinearLayout listView;
    private final List<Row> rows = new ArrayList<>();
    private final List<TextView> values = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 14, 16));
        root.setPadding(Ui.dp(this, 64), Ui.dp(this, 40), Ui.dp(this, 64), 0);
        root.addView(Ui.text(this, "Settings", 34, Ui.DISPLAY, Color.WHITE));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 40));
        listView = new LinearLayout(this);
        listView.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        defineRows();
        for (Row r : rows) listView.addView(rowView(r));
        listView.getChildAt(1).requestFocus();  // Row 0 is the first section header.
    }

    @Override protected void onResume() {
        super.onResume();
        refreshValues();
    }

    private void defineRows() {
        section("Home");
        rows.add(new Row("Apps on Home", new Value() {
            @Override public String get() { return Prefs.homeOrder(SettingsActivity.this).size() + " apps"; }
        }, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, HomeEditorActivity.class)); }
        }));
        rows.add(new Row("Home background", new Value() {
            @Override public String get() { return Prefs.homeBackgroundArt(SettingsActivity.this) ? "Artwork" : "Plain black"; }
        }, new Runnable() {
            @Override public void run() { Prefs.setHomeBackgroundArt(SettingsActivity.this, !Prefs.homeBackgroundArt(SettingsActivity.this)); }
        }));

        section("Art mode");
        rows.add(new Row("Start art mode now", null, new Runnable() {
            @Override public void run() { startActivity(new Intent(SettingsActivity.this, ArtModeActivity.class)); }
        }));
        final int[] idle = {0, 1, 2, 3, 5, 10, 15, 30};
        rows.add(new Row("Start art mode when idle for", new Value() {
            @Override public String get() {
                int m = Prefs.idleMinutes(SettingsActivity.this);
                return m == 0 ? "Never" : minutes(m);
            }
        }, new Runnable() {
            @Override public void run() { Prefs.setIdleMinutes(SettingsActivity.this, cycle(idle, Prefs.idleMinutes(SettingsActivity.this))); }
        }));
        final int[] interval = {1, 5, 15, 30, 60, 180, 720};
        rows.add(new Row("Change artwork every", new Value() {
            @Override public String get() { return minutes(Prefs.artIntervalMinutes(SettingsActivity.this)); }
        }, new Runnable() {
            @Override public void run() { Prefs.setArtIntervalMinutes(SettingsActivity.this, cycle(interval, Prefs.artIntervalMinutes(SettingsActivity.this))); }
        }));
        final String[] displays = {Prefs.DISPLAY_FILL, Prefs.DISPLAY_MATTE_LIGHT, Prefs.DISPLAY_MATTE_DARK};
        rows.add(new Row("Artwork display", new Value() {
            @Override public String get() {
                String d = Prefs.artDisplay(SettingsActivity.this);
                if (Prefs.DISPLAY_MATTE_LIGHT.equals(d)) return "Light mat";
                if (Prefs.DISPLAY_MATTE_DARK.equals(d)) return "Dark mat";
                return "Full screen";
            }
        }, new Runnable() {
            @Override public void run() { Prefs.setArtDisplay(SettingsActivity.this, cycle(displays, Prefs.artDisplay(SettingsActivity.this))); }
        }));
        final String[] plaques = {Prefs.PLAQUE_BRIEF, Prefs.PLAQUE_ALWAYS, Prefs.PLAQUE_HIDDEN};
        rows.add(new Row("Artwork label", new Value() {
            @Override public String get() {
                String p = Prefs.plaque(SettingsActivity.this);
                if (Prefs.PLAQUE_ALWAYS.equals(p)) return "Always";
                if (Prefs.PLAQUE_HIDDEN.equals(p)) return "Hidden";
                return "For 10 seconds";
            }
        }, new Runnable() {
            @Override public void run() { Prefs.setPlaque(SettingsActivity.this, cycle(plaques, Prefs.plaque(SettingsActivity.this))); }
        }));
        final int[] sleep = {0, 1, 2, 4, 8, 12};
        rows.add(new Row("Let the TV sleep after", new Value() {
            @Override public String get() {
                int h = Prefs.artSleepHours(SettingsActivity.this);
                return h == 0 ? "Never (always on)" : h + (h == 1 ? " hour" : " hours") + " of art";
            }
        }, new Runnable() {
            @Override public void run() { Prefs.setArtSleepHours(SettingsActivity.this, cycle(sleep, Prefs.artSleepHours(SettingsActivity.this))); }
        }));

        section("Museums");
        for (final String source : ArtSources.ALL) {
            rows.add(new Row(ArtSources.museumName(source), new Value() {
                @Override public String get() { return Prefs.sourceEnabled(SettingsActivity.this, source) ? "On" : "Off"; }
            }, new Runnable() {
                @Override public void run() { Prefs.setSourceEnabled(SettingsActivity.this, source, !Prefs.sourceEnabled(SettingsActivity.this, source)); }
            }));
        }
        rows.add(new Row("Saved artwork", new Value() {
            @Override public String get() {
                ArtRepository repo = ArtRepository.get(SettingsActivity.this);
                String s = repo.count() + " works" + (repo.isFetching() ? " · downloading" : "");
                return repo.lastError().isEmpty() ? s : s + " · " + repo.lastError();
            }
        }, new Runnable() {
            @Override public void run() {
                ArtRepository.get(SettingsActivity.this).ensureStocked();
                Toast.makeText(SettingsActivity.this, "Checking museums for new artwork", Toast.LENGTH_SHORT).show();
            }
        }));
        rows.add(new Row("Replace all saved artwork", null, new Runnable() {
            @Override public void run() {
                ArtRepository.get(SettingsActivity.this).refresh();
                Toast.makeText(SettingsActivity.this, "Fetching a fresh set of artwork", Toast.LENGTH_SHORT).show();
            }
        }));

        section("Home button");
        rows.add(new Row("Home button opens CleanFrame", new Value() {
            @Override public String get() {
                if (!watcherEnabled()) return "Service off · run the install script";
                return Prefs.homeCapture(SettingsActivity.this) ? "On" : "Paused";
            }
        }, new Runnable() {
            @Override public void run() {
                if (!watcherEnabled()) {
                    openAccessibilitySettings();
                    return;
                }
                Prefs.setHomeCapture(SettingsActivity.this, !Prefs.homeCapture(SettingsActivity.this));
            }
        }));
        rows.add(new Row("Catch Home before Amazon shows", new Value() {
            @Override public String get() { return Prefs.homeKeyIntercept(SettingsActivity.this) ? "On (no Home long-press menu)" : "Off"; }
        }, new Runnable() {
            @Override public void run() {
                Prefs.setHomeKeyIntercept(SettingsActivity.this, !Prefs.homeKeyIntercept(SettingsActivity.this));
                HomeWatcherService.refresh();
            }
        }));
        rows.add(new Row("Leave every Amazon launcher screen", new Value() {
            @Override public String get() { return Prefs.homeCaptureAllScreens(SettingsActivity.this) ? "On" : "Off (home screen only)"; }
        }, new Runnable() {
            @Override public void run() { Prefs.setHomeCaptureAllScreens(SettingsActivity.this, !Prefs.homeCaptureAllScreens(SettingsActivity.this)); }
        }));
        rows.add(new Row("Last Amazon screen seen", new Value() {
            @Override public String get() {
                String s = Prefs.lastAmazonScreen(SettingsActivity.this);
                return s.isEmpty() ? "None yet" : s;
            }
        }, null));

        section("About");
        rows.add(new Row("CleanFrame", new Value() {
            @Override public String get() { return "Version " + versionName(); }
        }, null));
    }

    private void section(String title) {
        rows.add(new Row(title, null, null, true));
    }

    private View rowView(final Row r) {
        if (r.header) {
            int index = rows.indexOf(r);
            TextView header = Ui.text(this, r.label.toUpperCase(), 13, Ui.DISPLAY, Color.argb(140, 255, 255, 255));
            header.setLetterSpacing(0.08f);
            header.setPadding(Ui.dp(this, 18), Ui.dp(this, index == 0 ? 0 : 22), 0, Ui.dp(this, 6));
            values.add(null);
            return header;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 13));
        row.setFocusable(true);
        row.setClickable(r.action != null);
        TextView label = Ui.text(this, r.label, 19, Ui.REGULAR, Color.WHITE);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(this, "", 17, Ui.REGULAR, Color.argb(170, 255, 255, 255));
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.START);
        value.setMaxWidth(Ui.dp(this, 460));
        row.addView(value);
        values.add(value);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (r.action == null) return;
                r.action.run();
                refreshValues();
            }
        });
        return row;
    }

    private void refreshValues() {
        for (int i = 0; i < rows.size() && i < values.size(); i++) {
            TextView v = values.get(i);
            if (v != null) v.setText(rows.get(i).value == null ? "" : rows.get(i).value.get());
        }
    }

    private boolean watcherEnabled() {
        if (HomeWatcherService.isRunning()) return true;
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(this, HomeWatcherService.class).flattenToString();
        return enabled != null && enabled.contains(me);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Turn it on from the Mac with the install script", Toast.LENGTH_LONG).show();
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String minutes(int m) {
        if (m < 60) return m + (m == 1 ? " minute" : " minutes");
        int h = m / 60;
        return h + (h == 1 ? " hour" : " hours");
    }

    private static int cycle(int[] options, int current) {
        for (int i = 0; i < options.length; i++) if (options[i] == current) return options[(i + 1) % options.length];
        return options[0];
    }

    private static String cycle(String[] options, String current) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(current)) return options[(i + 1) % options.length];
        return options[0];
    }
}
