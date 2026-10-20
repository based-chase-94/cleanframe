package com.cleanframe.launcher;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shows bookmarked on For You, and how to open each one in its app. A show is a title,
 * the app it lives in, and optionally the show's web address. Opening tries the address in
 * the app, then the app's own search for the title, then just the app.
 */
final class Shows {
    static final class Show {
        String id;
        String title;
        /** One of {@link #APPS}' keys. */
        String app;
        /** The show's page on the service's website, or empty. */
        String link;
        /** Poster image address, or empty to look one up by title. */
        String poster;

        Show(String title, String app, String link) {
            this.id = UUID.randomUUID().toString();
            this.title = title;
            this.app = app;
            this.link = link == null ? "" : link;
            this.poster = "";
        }
    }

    /** What opening a show will actually do on this TV. */
    static final int OPENS_SHOW = 0;
    static final int OPENS_SEARCH = 1;
    static final int OPENS_APP = 2;
    static final int NOT_INSTALLED = 3;

    /** App keys (the label prefixes {@link AppCatalog} uses), display names and web hosts. */
    static final String[][] APPS = {
            {"youtube", "YouTube", "youtube.com", "youtu.be"},
            {"prime video", "Prime Video", "amazon.com", "primevideo.com", "watch.amazon.com"},
            {"wow", "WOW Presents Plus", "wowpresentsplus.com"},
            {"hulu", "Hulu", "hulu.com"},
            {"paramount", "Paramount+", "paramountplus.com"},
            {"tubi", "Tubi", "tubitv.com"},
            {"crunchyroll", "Crunchyroll", "crunchyroll.com"},
    };

    /** Shows For You starts with, until they're edited in Settings. */
    private static final String[][] STARTER = {
            {"RuPaul's Drag Race", "paramount", "https://www.paramountplus.com/shows/rupauls-drag-race/"},
            {"RuPaul's Drag Race UK", "wow", "https://www.wowpresentsplus.com/rupaul-s-drag-race-uk"},
            {"The Bear", "hulu", "https://www.hulu.com/series/the-bear-05eb6a8e-90ed-4947-8c0b-e6536cbddd5f"},
            {"One Piece", "crunchyroll", "https://www.crunchyroll.com/series/GRMG8ZQZR/one-piece"},
            {"The Boys", "prime video", ""},
    };

    private static final Pattern ASIN = Pattern.compile("/(?:dp|detail)/([A-Z0-9]{10})");
    private static final Pattern UUID_TAIL = Pattern.compile("-?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private Shows() {}

    static List<Show> load(Context c) {
        String raw = Prefs.forYouShows(c);
        List<Show> out = new ArrayList<>();
        if (raw == null) {
            for (String[] s : STARTER) out.add(new Show(s[0], s[1], s[2]));
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Show s = new Show(o.optString("title"), o.optString("app"), o.optString("link"));
                s.id = o.optString("id", s.id);
                s.poster = o.optString("poster");
                out.add(s);
            }
        } catch (Exception ignored) {
            // A broken list starts over empty rather than taking the screen down.
        }
        return out;
    }

    static void save(Context c, List<Show> shows) {
        JSONArray a = new JSONArray();
        try {
            for (Show s : shows) {
                a.put(new JSONObject().put("id", s.id).put("title", s.title).put("app", s.app)
                        .put("link", s.link).put("poster", s.poster));
            }
        } catch (Exception ignored) {
            // JSONObject.put only throws for non-finite numbers.
        }
        Prefs.setForYouShows(c, a.toString());
    }

    static String appName(String key) {
        for (String[] a : APPS) if (a[0].equals(key)) return a[1];
        return key;
    }

    /** The app a web address belongs to, or null. */
    static String appForLink(String link) {
        String host = Uri.parse(link).getHost();
        if (host == null) return null;
        host = host.toLowerCase(Locale.US);
        for (String[] a : APPS) {
            for (int i = 2; i < a.length; i++) {
                if (host.equals(a[i]) || host.endsWith("." + a[i])) return a[0];
            }
        }
        return null;
    }

    static boolean looksLikeLink(String s) {
        String t = s.trim().toLowerCase(Locale.US);
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.");
    }

    static String normalizeLink(String s) {
        String t = s.trim();
        return t.toLowerCase(Locale.US).startsWith("www.") ? "https://" + t : t;
    }

    /** A readable guess at the show's name from its web address, e.g. ".../the-bear-05eb..." gives "The Bear". */
    static String titleFromLink(String link) {
        List<String> parts = Uri.parse(link).getPathSegments();
        String best = "";
        for (String p : parts) {
            String s = UUID_TAIL.matcher(p.toLowerCase(Locale.US)).replaceAll("");
            // Skip ids and path words; keep the last segment that reads like a name.
            if (s.contains("-") && !s.matches(".*\\d{3,}.*")) best = s;
            else if (best.isEmpty() && s.matches("[a-z]{4,}") && !isPathWord(s)) best = s;
        }
        if (best.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : best.split("-")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static boolean isPathWord(String s) {
        return s.equals("series") || s.equals("shows") || s.equals("videos") || s.equals("watch")
                || s.equals("detail") || s.equals("playlist") || s.equals("movies") || s.equals("video");
    }

    /** The installed app for {@code key}, or null. */
    static AppCatalog.AppEntry installedApp(List<AppCatalog.AppEntry> apps, String key) {
        return AppCatalog.findDefault(apps, key);
    }

    /** What {@link #open} would do for {@code s} right now. */
    static int opens(Context c, Show s, List<AppCatalog.AppEntry> apps) {
        AppCatalog.AppEntry app = installedApp(apps, s.app);
        if (app == null) return NOT_INSTALLED;
        if (showIntent(c, s, app.pkg) != null) return OPENS_SHOW;
        if (searchIntent(c, s, app.pkg) != null) return OPENS_SEARCH;
        return OPENS_APP;
    }

    static String describe(int opens) {
        switch (opens) {
            case OPENS_SHOW: return "Opens the show";
            case OPENS_SEARCH: return "Opens a search for it";
            case OPENS_APP: return "Opens the app";
            default: return "App not installed";
        }
    }

    /** Opens the show's page if its app takes the link, else a search in the app, else the app. */
    static void open(Context c, Show s, List<AppCatalog.AppEntry> apps) {
        AppCatalog.AppEntry app = installedApp(apps, s.app);
        if (app == null) {
            Toast.makeText(c, appName(s.app) + " isn't installed", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent[] tries = {showIntent(c, s, app.pkg), searchIntent(c, s, app.pkg)};
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
                // Try the next way in.
            }
        }
        AppCatalog.launch(c, app.pkg, app.label);
    }

    /** The first of the show's addresses that the app says it handles, or null. */
    private static Intent showIntent(Context c, Show s, String pkg) {
        for (String uri : candidateLinks(s)) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg);
            if (resolves(c, i)) return i;
        }
        return null;
    }

    /**
     * The web address first, then the app's own link styles where they're known. Each is only
     * used if the app declares it, so a wrong guess costs nothing.
     */
    private static List<String> candidateLinks(Show s) {
        List<String> out = new ArrayList<>();
        if (s.link.isEmpty()) return out;
        out.add(s.link);
        Uri u = Uri.parse(s.link);
        String path = u.getPath() == null ? "" : u.getPath();
        if ("prime video".equals(s.app)) {
            Matcher m = ASIN.matcher(path);
            if (m.find()) {
                out.add("https://watch.amazon.com/detail?asin=" + m.group(1));
                out.add("https://app.primevideo.com/detail?asin=" + m.group(1));
                out.add("aiv://aiv/detail?asin=" + m.group(1));
            }
        } else if ("hulu".equals(s.app)) {
            out.add("hulu:/" + path);
        } else if ("crunchyroll".equals(s.app)) {
            out.add("crunchyroll:/" + path);
        } else if ("tubi".equals(s.app)) {
            out.add("tubitv:/" + path);
        } else if ("paramount".equals(s.app)) {
            out.add("pplus:/" + path);
        }
        if (!"https".equals(u.getScheme())) out.add(s.link.replaceFirst("^http:", "https:"));
        return out;
    }

    private static Intent searchIntent(Context c, Show s, String pkg) {
        Intent i = new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, s.title);
        if (resolves(c, i)) return i;
        if ("youtube".equals(s.app)) {
            Intent y = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(s.title))).setPackage(pkg);
            if (resolves(c, y)) return y;
        }
        return null;
    }

    private static boolean resolves(Context c, Intent i) {
        return !c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
    }
}
