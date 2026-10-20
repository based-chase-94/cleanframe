package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * White, cut-out logos for the Home list. Known streaming apps use bundled vector logos; any
 * other app gets a white silhouette lifted out of its own icon.
 */
final class AppLogos {
    /** Label prefixes, then package names, for each bundled logo. */
    private static final String[][] KNOWN = {
            {"youtube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv"},
            {"prime video", "com.amazon.avod", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"},
            {"hulu", "com.hulu.livingroomplus", "com.hulu.plus"},
            {"paramount", "com.cbs.ott", "com.cbs.app"},
            {"crunchyroll", "com.crunchyroll.crunchyroid"},
            {"tubi", "com.tubitv.ott", "com.tubitv"},
    };
    private static final int[] KNOWN_LOGOS = {
            R.drawable.logo_youtube,
            R.drawable.logo_prime_video,
            R.drawable.logo_hulu,
            R.drawable.logo_paramount,
            R.drawable.logo_crunchyroll,
            R.drawable.logo_tubi,
    };

    private static final int SIZE = 160;
    /** Silhouettes by package. A null value means the icon had no usable shape. */
    private static final Map<String, Bitmap> silhouettes = new HashMap<>();

    private AppLogos() {}

    /** The app's white logo, or null if none could be made. */
    static Drawable logo(Context c, AppCatalog.AppEntry app) {
        String label = app.label.toLowerCase(Locale.US);
        for (int i = 0; i < KNOWN.length; i++) {
            boolean match = label.startsWith(KNOWN[i][0]);
            for (int j = 1; !match && j < KNOWN[i].length; j++) match = app.pkg.equals(KNOWN[i][j]);
            if (match) return c.getDrawable(KNOWN_LOGOS[i]);
        }
        Bitmap b;
        synchronized (silhouettes) {
            if (silhouettes.containsKey(app.pkg)) {
                b = silhouettes.get(app.pkg);
            } else {
                b = silhouette(app.icon(c.getPackageManager()));
                silhouettes.put(app.pkg, b);
            }
        }
        return b == null ? null : new BitmapDrawable(c.getResources(), b);
    }

    /**
     * Lifts the logo off an app icon: drops a solid backplate if there is one, otherwise keeps
     * the icon's own shape, or failing that whatever stands out from the icon's edge. Returns
     * a white bitmap trimmed to the logo, or null if nothing logo-like is left.
     */
    static Bitmap silhouette(Drawable icon) {
        if (icon == null) return null;
        Bitmap src = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(new Canvas(src));
        int n = SIZE * SIZE;
        int[] px = new int[n];
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        src.recycle();

        // The most common colour among opaque pixels, at 4 bits a channel.
        int[] counts = new int[4096];
        int opaque = 0;
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            opaque++;
            counts[((Color.red(p) >> 4) << 8) | ((Color.green(p) >> 4) << 4) | (Color.blue(p) >> 4)]++;
        }
        if (opaque == 0) return null;
        int mode = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[mode]) mode = i;
        int plateR = 0, plateG = 0, plateB = 0, plateN = 0;
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            if ((((Color.red(p) >> 4) << 8) | ((Color.green(p) >> 4) << 4) | (Color.blue(p) >> 4)) != mode) continue;
            plateR += Color.red(p); plateG += Color.green(p); plateB += Color.blue(p); plateN++;
        }
        plateR /= plateN; plateG /= plateN; plateB /= plateN;
        boolean plate = counts[mode] >= opaque * 0.35f;
        boolean shaped = opaque < n * 0.6f;

        // Average brightness around the edge of the opaque area, for gradient backplates.
        float edge = 0;
        int edgeN = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = px[y * SIZE + x];
                if (Color.alpha(p) < 128) continue;
                if (x > 6 && y > 6 && x < SIZE - 7 && y < SIZE - 7 && !nearTransparent(px, x, y)) continue;
                edge += luma(p);
                edgeN++;
            }
        }
        edge = edgeN == 0 ? 0 : edge / edgeN;

        int[] out = new int[n];
        int kept = 0, minX = SIZE, minY = SIZE, maxX = -1, maxY = -1;
        for (int i = 0; i < n; i++) {
            int p = px[i];
            float a;
            if (plate) {
                float dr = Color.red(p) - plateR, dg = Color.green(p) - plateG, db = Color.blue(p) - plateB;
                a = ramp((float) Math.sqrt(dr * dr + dg * dg + db * db), 48, 112);
            } else if (shaped) {
                a = 1f;
            } else {
                a = ramp(Math.abs(luma(p) - edge), 40, 96);
            }
            int alpha = Math.round(a * Color.alpha(p));
            if (alpha < 16) continue;
            out[i] = Color.argb(alpha, 255, 255, 255);
            kept++;
            int x = i % SIZE, y = i / SIZE;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        // An empty result, or one that is just the icon's square again, is not a logo.
        if (kept < n * 0.01f || kept > n * 0.7f) return null;
        Bitmap full = Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Bitmap trimmed = Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1);
        if (trimmed != full) full.recycle();
        return trimmed;
    }

    private static boolean nearTransparent(int[] px, int x, int y) {
        return Color.alpha(px[y * SIZE + x - 3]) < 128 || Color.alpha(px[y * SIZE + x + 3]) < 128
                || Color.alpha(px[(y - 3) * SIZE + x]) < 128 || Color.alpha(px[(y + 3) * SIZE + x]) < 128;
    }

    private static float luma(int p) {
        return 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p);
    }

    /** 0 below {@code lo}, 1 above {@code hi}, linear between. */
    private static float ramp(float v, float lo, float hi) {
        return v <= lo ? 0f : v >= hi ? 1f : (v - lo) / (hi - lo);
    }
}
