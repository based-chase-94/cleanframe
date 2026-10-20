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
            {"wow presents", "wow"},
            {"hulu", "com.hulu.livingroomplus", "com.hulu.plus"},
            {"paramount", "com.cbs.ott", "com.cbs.app"},
    };

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
        Map<String, AppEntry> byPkg = index(apps);
        for (String[] want : DEFAULT_HOME) {
            AppEntry best = null;
            String prefix = want[0];
            for (AppEntry a : apps) {
                if (out.contains(a.pkg)) continue;
                if (a.label.toLowerCase(Locale.US).startsWith(prefix)
                        && (best == null || a.label.length() < best.label.length())) {
                    best = a;  // Shortest match wins, so "YouTube" beats "YouTube Kids".
                }
            }
            for (int i = 1; best == null && i < want.length; i++) {
                AppEntry a = byPkg.get(want[i]);
                if (a != null && !out.contains(a.pkg)) best = a;
            }
            if (best != null) out.add(best.pkg);
        }
        return out;
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
