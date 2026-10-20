package com.cleanframe.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finds launchable apps and opens them. Apps are keyed by package name everywhere else. */
public final class AppCatalog {

    public static final class AppEntry {
        public final String pkg;
        public final String label;
        public final ComponentName component;
        private final ActivityInfo info;
        private Drawable icon;
        private Drawable banner;
        private boolean bannerLoaded;

        AppEntry(ResolveInfo ri, PackageManager pm) {
            info = ri.activityInfo;
            pkg = info.packageName;
            CharSequence l = ri.loadLabel(pm);
            label = l == null ? pkg : l.toString().trim();
            component = new ComponentName(info.packageName, info.name);
        }

        public Drawable icon(PackageManager pm) {
            if (icon == null) icon = info.loadIcon(pm);
            return icon;
        }

        /** The 16:9 TV banner if the app ships one, else null. */
        public Drawable banner(PackageManager pm) {
            if (!bannerLoaded) {
                bannerLoaded = true;
                banner = info.loadBanner(pm);
                if (banner == null) banner = info.applicationInfo.loadBanner(pm);
            }
            return banner;
        }
    }

    /**
     * Default Home, in this order. Each entry is a set of label prefixes, then package names
     * to try if the label does not match (Fire OS builds name some of these differently).
     */
    private static final String[][] DEFAULT_HOME = {
            {"youtube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv"},
            {"prime video", "com.amazon.avod", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"},
            {"wow", "com.wowpresentsplus.firetv", "com.wowpresentsplus.androidtv"},
            {"hulu", "com.hulu.livingroomplus", "com.hulu.plus"},
            {"paramount", "com.cbs.ott", "com.cbs.app"},
            {"tubi", "com.tubitv.ott", "com.tubitv"},
            {"crunchyroll", "com.crunchyroll.crunchyroid"},
    };

    /** Home holds at most this many apps; five show at a time and the rest scroll. */
    public static final int HOME_MAX = 12;

    /** Defaults that Home already had before Tubi and Crunchyroll joined, see {@link #addNewDefaults}. */
    private static final String[] FIRST_DEFAULTS = {"youtube", "prime video", "hulu", "paramount"};

    private AppCatalog() {}

    public static List<AppEntry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> byPkg = new LinkedHashMap<>();
        // Leanback activities first: on a TV they are the right entry point when both exist.
        addAll(byPkg, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, c.getPackageName());
        addAll(byPkg, pm, Intent.CATEGORY_LAUNCHER, c.getPackageName());
        List<AppEntry> out = new ArrayList<>(byPkg.values());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return a.label.compareToIgnoreCase(b.label); }
        });
        return out;
    }

    private static void addAll(Map<String, AppEntry> into, PackageManager pm, String category, String self) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || into.containsKey(pkg)) continue;
            into.put(pkg, new AppEntry(ri, pm));
        }
    }

    public static Map<String, AppEntry> index(List<AppEntry> apps) {
        Map<String, AppEntry> m = new LinkedHashMap<>();
        for (AppEntry a : apps) m.put(a.pkg, a);
        return m;
    }

    public static List<String> defaultHome(List<AppEntry> apps) {
        List<String> out = new ArrayList<>();
        for (String[] want : DEFAULT_HOME) {
            AppEntry best = find(apps, want, out);
            if (best != null && out.size() < HOME_MAX) out.add(best.pkg);
        }
        return out;
    }

    /**
     * Puts default apps on Home the first time they are installed, for Homes set up before
     * those apps were defaults (or before the app was installed). Each default is offered
     * once; after that, removing it in the Home editor is respected. Returns true if
     * {@code home} changed.
     */
    public static boolean addNewDefaults(Context c, List<AppEntry> apps, List<String> home) {
        List<String> seen = Prefs.homeDefaultsSeen(c);
        boolean seenChanged = false;
        if (seen.isEmpty()) {
            // Homes made by v1.0.4 or earlier already had these, or had them removed.
            for (String key : FIRST_DEFAULTS) seen.add(key);
            seenChanged = true;
        }
        Map<String, AppEntry> byPkg = index(apps);
        int shown = 0;
        for (String pkg : home) if (byPkg.containsKey(pkg)) shown++;
        List<String> removed = Prefs.recentlyRemoved(c);
        boolean homeChanged = false;
        for (String[] want : DEFAULT_HOME) {
            if (seen.contains(want[0])) continue;
            AppEntry app = find(apps, want, new ArrayList<String>());
            if (app == null) continue;  // Not installed yet; offer it when it is.
            seen.add(want[0]);
            seenChanged = true;
            if (home.contains(app.pkg) || removed.contains(app.pkg) || shown >= HOME_MAX) continue;
            home.add(app.pkg);
            shown++;
            homeChanged = true;
        }
        if (seenChanged) Prefs.setHomeDefaultsSeen(c, seen);
        return homeChanged;
    }

    /** The installed app for one {@link #DEFAULT_HOME} entry, skipping {@code taken}. */
    private static AppEntry find(List<AppEntry> apps, String[] want, List<String> taken) {
        AppEntry best = null;
        String prefix = want[0];
        for (AppEntry a : apps) {
            if (taken.contains(a.pkg)) continue;
            if (a.label.toLowerCase(Locale.US).startsWith(prefix)
                    && (best == null || a.label.length() < best.label.length())) {
                best = a;  // Shortest match wins, so "YouTube" beats "YouTube Kids".
            }
        }
        Map<String, AppEntry> byPkg = index(apps);
        for (int i = 1; best == null && i < want.length; i++) {
            AppEntry a = byPkg.get(want[i]);
            if (a != null && !taken.contains(a.pkg)) best = a;
        }
        return best;
    }

    public static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    public static boolean launch(Context c, String pkg, String label) {
        Intent i = launchIntent(c, pkg);
        try {
            if (i != null) {
                c.startActivity(i);
                return true;
            }
        } catch (Exception ignored) {
            // Fall through to the toast.
        }
        Toast.makeText(c, "Couldn't open " + (label == null ? pkg : label), Toast.LENGTH_SHORT).show();
        return false;
    }
}
