package com.cleanframe.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Every setting the launcher keeps, in one SharedPreferences file. */
public final class Prefs {
    private static final String FILE = "cleanframe";

    private static final String HOME_ORDER = "home_order";
    private static final String HOME_INITIALIZED = "home_initialized";
    private static final String RECENTLY_REMOVED = "recently_removed";
    private static final String HOME_DEFAULTS_SEEN = "home_defaults_seen";
    private static final String IDLE_MINUTES = "idle_minutes";
    private static final String ART_INTERVAL_MINUTES = "art_interval_minutes";
    private static final String ART_DISPLAY = "art_display";
    private static final String PLAQUE = "plaque";
    private static final String ART_SLEEP_HOURS = "art_sleep_hours";
    private static final String HOME_BACKGROUND_ART = "home_background_art";
    private static final String HOME_CAPTURE = "home_capture";
    private static final String HOME_KEY_INTERCEPT = "home_key_intercept";
    private static final String HOME_CAPTURE_ALL_SCREENS = "home_capture_all_screens";
    private static final String LAST_AMAZON_SCREEN = "last_amazon_screen";
    private static final String HOME_KEY_SEEN = "home_key_seen";
    private static final String HOME_ART_ID = "home_art_id";
    private static final String HOME_ART_SINCE = "home_art_since";
    private static final String SOURCE_PREFIX = "source_";
    private static final String WEATHER_ON = "weather_on";
    private static final String WEATHER_TEMP = "weather_temp";
    private static final String WEATHER_CODE = "weather_code";
    private static final String WEATHER_DAY = "weather_day";
    private static final String WEATHER_AT = "weather_at";
    private static final String LOCATION_LAT = "location_lat";
    private static final String LOCATION_LON = "location_lon";
    private static final String LOCATION_AT = "location_at";

    public static final String DISPLAY_FILL = "fill";
    public static final String DISPLAY_MATTE_LIGHT = "matte_light";
    public static final String DISPLAY_MATTE_DARK = "matte_dark";

    public static final String PLAQUE_BRIEF = "brief";
    public static final String PLAQUE_ALWAYS = "always";
    public static final String PLAQUE_HIDDEN = "hidden";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static List<String> getList(Context c, String key) {
        List<String> out = new ArrayList<>();
        String raw = sp(c).getString(key, "");
        for (String s : raw.split("\n")) {
            if (!s.trim().isEmpty() && !out.contains(s.trim())) out.add(s.trim());
        }
        return out;
    }

    private static void putList(Context c, String key, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        sp(c).edit().putString(key, sb.toString()).apply();
    }

    public static List<String> homeOrder(Context c) { return getList(c, HOME_ORDER); }

    public static void setHomeOrder(Context c, List<String> pkgs) {
        putList(c, HOME_ORDER, pkgs);
        sp(c).edit().putBoolean(HOME_INITIALIZED, true).apply();
    }

    public static boolean homeInitialized(Context c) { return sp(c).getBoolean(HOME_INITIALIZED, false); }

    /** Default Home apps (by their label prefix) already offered once, so a removed one stays removed. */
    public static List<String> homeDefaultsSeen(Context c) { return getList(c, HOME_DEFAULTS_SEEN); }
    public static void setHomeDefaultsSeen(Context c, List<String> keys) { putList(c, HOME_DEFAULTS_SEEN, keys); }

    public static List<String> recentlyRemoved(Context c) { return getList(c, RECENTLY_REMOVED); }

    public static void setRecentlyRemoved(Context c, List<String> pkgs) {
        // Only the last few are worth remembering.
        putList(c, RECENTLY_REMOVED, pkgs.size() > 8 ? pkgs.subList(0, 8) : pkgs);
    }

    public static int idleMinutes(Context c) { return sp(c).getInt(IDLE_MINUTES, 3); }
    public static void setIdleMinutes(Context c, int v) { sp(c).edit().putInt(IDLE_MINUTES, v).apply(); }

    public static int artIntervalMinutes(Context c) { return sp(c).getInt(ART_INTERVAL_MINUTES, 15); }
    public static void setArtIntervalMinutes(Context c, int v) { sp(c).edit().putInt(ART_INTERVAL_MINUTES, v).apply(); }

    public static String artDisplay(Context c) { return sp(c).getString(ART_DISPLAY, DISPLAY_FILL); }
    public static void setArtDisplay(Context c, String v) { sp(c).edit().putString(ART_DISPLAY, v).apply(); }

    public static String plaque(Context c) { return sp(c).getString(PLAQUE, PLAQUE_BRIEF); }
    public static void setPlaque(Context c, String v) { sp(c).edit().putString(PLAQUE, v).apply(); }

    /** Hours art mode keeps the screen awake. 0 means it never lets the TV sleep. */
    public static int artSleepHours(Context c) { return sp(c).getInt(ART_SLEEP_HOURS, 4); }
    public static void setArtSleepHours(Context c, int v) { sp(c).edit().putInt(ART_SLEEP_HOURS, v).apply(); }

    public static boolean homeBackgroundArt(Context c) { return sp(c).getBoolean(HOME_BACKGROUND_ART, true); }
    public static void setHomeBackgroundArt(Context c, boolean v) { sp(c).edit().putBoolean(HOME_BACKGROUND_ART, v).apply(); }

    public static boolean homeCapture(Context c) { return sp(c).getBoolean(HOME_CAPTURE, true); }
    public static void setHomeCapture(Context c, boolean v) { sp(c).edit().putBoolean(HOME_CAPTURE, v).apply(); }

    public static boolean homeKeyIntercept(Context c) { return sp(c).getBoolean(HOME_KEY_INTERCEPT, true); }
    public static void setHomeKeyIntercept(Context c, boolean v) { sp(c).edit().putBoolean(HOME_KEY_INTERCEPT, v).apply(); }

    public static boolean homeCaptureAllScreens(Context c) { return sp(c).getBoolean(HOME_CAPTURE_ALL_SCREENS, false); }
    public static void setHomeCaptureAllScreens(Context c, boolean v) { sp(c).edit().putBoolean(HOME_CAPTURE_ALL_SCREENS, v).apply(); }

    public static String lastAmazonScreen(Context c) { return sp(c).getString(LAST_AMAZON_SCREEN, ""); }
    public static void setLastAmazonScreen(Context c, String v) { sp(c).edit().putString(LAST_AMAZON_SCREEN, v).apply(); }

    /** Wall-clock time the Home key last reached the accessibility service, or 0. */
    public static long homeKeySeen(Context c) { return sp(c).getLong(HOME_KEY_SEEN, 0); }
    public static void setHomeKeySeen(Context c) { sp(c).edit().putLong(HOME_KEY_SEEN, System.currentTimeMillis()).apply(); }

    public static String homeArtId(Context c) { return sp(c).getString(HOME_ART_ID, ""); }
    public static long homeArtSince(Context c) { return sp(c).getLong(HOME_ART_SINCE, 0); }

    public static void setHomeArt(Context c, String id) {
        sp(c).edit().putString(HOME_ART_ID, id).putLong(HOME_ART_SINCE, System.currentTimeMillis()).apply();
    }

    public static boolean sourceEnabled(Context c, String source) { return sp(c).getBoolean(SOURCE_PREFIX + source, true); }
    public static void setSourceEnabled(Context c, String source, boolean v) { sp(c).edit().putBoolean(SOURCE_PREFIX + source, v).apply(); }

    public static boolean weatherOn(Context c) { return sp(c).getBoolean(WEATHER_ON, true); }
    public static void setWeatherOn(Context c, boolean v) { sp(c).edit().putBoolean(WEATHER_ON, v).apply(); }

    public static float weatherTemp(Context c) { return sp(c).getFloat(WEATHER_TEMP, 0); }
    public static int weatherCode(Context c) { return sp(c).getInt(WEATHER_CODE, 0); }
    public static boolean weatherDay(Context c) { return sp(c).getBoolean(WEATHER_DAY, true); }
    /** Wall-clock time of the last successful weather fetch, or 0. */
    public static long weatherAt(Context c) { return sp(c).getLong(WEATHER_AT, 0); }

    public static void setWeather(Context c, float temp, int code, boolean day) {
        sp(c).edit().putFloat(WEATHER_TEMP, temp).putInt(WEATHER_CODE, code).putBoolean(WEATHER_DAY, day)
                .putLong(WEATHER_AT, System.currentTimeMillis()).apply();
    }

    /** Approximate location from the TV's internet address, used only for the weather. */
    public static float locationLat(Context c) { return sp(c).getFloat(LOCATION_LAT, 0); }
    public static float locationLon(Context c) { return sp(c).getFloat(LOCATION_LON, 0); }
    public static long locationAt(Context c) { return sp(c).getLong(LOCATION_AT, 0); }

    public static void setLocation(Context c, float lat, float lon) {
        sp(c).edit().putFloat(LOCATION_LAT, lat).putFloat(LOCATION_LON, lon)
                .putLong(LOCATION_AT, System.currentTimeMillis()).apply();
    }
}
