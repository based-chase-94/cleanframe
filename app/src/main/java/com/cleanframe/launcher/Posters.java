package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Poster art for For You. A show's own poster address wins; otherwise the poster is looked up
 * by title on TVmaze (free, no key). Downloads are kept in app files so each is fetched once.
 */
final class Posters {
    private static final String TAG = "CleanFrame.Posters";
    private static final String TVMAZE = "https://api.tvmaze.com/singlesearch/shows?q=";
    /** Posters are decoded down to about this width, plenty for a tile on a 1080p screen. */
    private static final int WIDTH = 420;

    interface Callback { void onPoster(Bitmap b); }

    private static final ExecutorService worker = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> memory = new HashMap<>();
    /** Lookups that found nothing this run, so a missing poster isn't retried on every visit. */
    private static final Set<String> missing = new HashSet<>();

    private Posters() {}

    /** Delivers the show's poster on the main thread, or null if there isn't one. */
    static void load(Context c, final Shows.Show s, final Callback cb) {
        final String key = key(s);
        synchronized (memory) {
            if (memory.containsKey(key)) {
                cb.onPoster(memory.get(key));
                return;
            }
        }
        final File dir = new File(c.getFilesDir(), "posters");
        worker.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    b = fetch(dir, s, key);
                } catch (Throwable t) {
                    Log.w(TAG, "poster failed for " + s.title, t);
                }
                final Bitmap result = b;
                synchronized (memory) {
                    if (result != null) memory.put(key, result);
                    else missing.add(key);
                }
                main.post(new Runnable() {
                    @Override public void run() { cb.onPoster(result); }
                });
            }
        });
    }

    /** Forgets the saved poster so the next visit fetches it again. */
    static void forget(Context c, Shows.Show s) {
        String key = key(s);
        synchronized (memory) {
            memory.remove(key);
            missing.remove(key);
        }
        new File(new File(c.getFilesDir(), "posters"), key + ".img").delete();
    }

    private static Bitmap fetch(File dir, Shows.Show s, String key) throws Exception {
        File f = new File(dir, key + ".img");
        if (!f.exists()) {
            synchronized (memory) {
                if (missing.contains(key)) return null;
            }
            String url = s.poster.isEmpty() ? lookup(s) : s.poster;
            if (url == null || url.isEmpty()) return null;
            dir.mkdirs();
            download(url, f);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        while (bounds.outWidth / (o.inSampleSize * 2) >= WIDTH) o.inSampleSize *= 2;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) f.delete();
        return b;
    }

    /** A YouTube video's thumbnail, or the TV poster TVmaze has for the title. */
    private static String lookup(Shows.Show s) throws Exception {
        if ("youtube".equals(s.app) && !s.link.isEmpty()) {
            Uri u = Uri.parse(s.link);
            String v = u.getQueryParameter("v");
            if (v == null && "youtu.be".equals(u.getHost()) && !u.getPathSegments().isEmpty()) v = u.getPathSegments().get(0);
            if (v != null) return "https://i.ytimg.com/vi/" + v + "/hqdefault.jpg";
        }
        if (s.title.trim().isEmpty()) return null;
        String json;
        try {
            json = ArtSources.getString(TVMAZE + Uri.encode(s.title.trim()));
        } catch (java.io.IOException e) {
            return null;  // 404 means no match.
        }
        JSONObject image = new JSONObject(json).optJSONObject("image");
        if (image == null) return null;
        String original = image.optString("original", "");
        return original.isEmpty() ? image.optString("medium", "") : original;
    }

    private static void download(String url, File to) throws Exception {
        File tmp = new File(to.getPath() + ".part");
        HttpURLConnection conn = ArtSources.open(url);
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        if (!tmp.renameTo(to)) tmp.delete();
    }

    private static String key(Shows.Show s) {
        String src = s.poster.isEmpty() ? s.app + "|" + s.title.trim().toLowerCase() + "|" + s.link : s.poster;
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(src.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(src.hashCode());
        }
    }
}
