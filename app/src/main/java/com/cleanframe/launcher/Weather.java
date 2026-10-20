package com.cleanframe.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current conditions for the clock, from Open-Meteo (no key). The TV has no location of its
 * own, so it's located roughly by its internet address; the place is never shown.
 */
final class Weather {
    private static final long REFRESH_MS = 30 * 60_000L;
    /** Older readings are hidden rather than shown as current. */
    private static final long STALE_MS = 3 * 60 * 60_000L;
    private static final long LOCATION_MS = 24 * 60 * 60_000L;
    /** Minimum wait between attempts, so a dead connection isn't retried on every Home. */
    private static final long RETRY_MS = 5 * 60_000L;

    /** IP geolocation services, tried in order. Each answers with "latitude" and "longitude". */
    private static final String[] LOCATORS = {
            "https://ipwho.is/",
            "https://ipapi.co/json/",
            "https://get.geojs.io/v1/ip/geo.json",
    };

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean busy;
    private static long lastAttempt;

    static final class Now {
        final int fahrenheit;
        final int icon;

        Now(int fahrenheit, int icon) {
            this.fahrenheit = fahrenheit;
            this.icon = icon;
        }
    }

    private Weather() {}

    /** The latest reading, or null if weather is off or there's no recent reading. */
    static Now current(Context c) {
        long at = Prefs.weatherAt(c);
        if (!Prefs.weatherOn(c) || at == 0 || System.currentTimeMillis() - at > STALE_MS) return null;
        return new Now(Math.round(Prefs.weatherTemp(c)), icon(Prefs.weatherCode(c), Prefs.weatherDay(c)));
    }

    /** Fetches new conditions if the last ones are due for a refresh; calls {@code done} on the main thread if they changed. */
    static void refresh(Context context, final Runnable done) {
        final Context c = context.getApplicationContext();
        long now = System.currentTimeMillis();
        if (!Prefs.weatherOn(c) || now - Prefs.weatherAt(c) < REFRESH_MS) return;
        synchronized (Weather.class) {
            if (busy || now - lastAttempt < RETRY_MS) return;
            busy = true;
            lastAttempt = now;
        }
        worker.execute(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    ok = fetch(c);
                } finally {
                    synchronized (Weather.class) { busy = false; }
                }
                if (ok && done != null) main.post(done);
            }
        });
    }

    private static boolean fetch(Context c) {
        if (!locate(c)) return false;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                            + "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit",
                    Prefs.locationLat(c), Prefs.locationLon(c));
            JSONObject cur = new JSONObject(ArtSources.getString(url)).getJSONObject("current");
            Prefs.setWeather(c, (float) cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Makes sure a location less than a day old is saved. Keeps an older one if every service fails. */
    private static boolean locate(Context c) {
        boolean have = Prefs.locationAt(c) != 0;
        if (have && System.currentTimeMillis() - Prefs.locationAt(c) < LOCATION_MS) return true;
        for (String url : LOCATORS) {
            try {
                JSONObject o = new JSONObject(ArtSources.getString(url));
                double lat = o.optDouble("latitude", Double.NaN);
                double lon = o.optDouble("longitude", Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                Prefs.setLocation(c, (float) lat, (float) lon);
                return true;
            } catch (Exception ignored) {
                // Try the next service.
            }
        }
        return have;
    }

    /** The icon for a WMO weather code, as Open-Meteo reports it. */
    static int icon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code == 2) return day ? R.drawable.wx_cloud_sun : R.drawable.wx_cloud_moon;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 95) return R.drawable.wx_thunder;
        if (code >= 51 && code <= 82) return R.drawable.wx_rain;
        return R.drawable.wx_cloud;
    }
}
