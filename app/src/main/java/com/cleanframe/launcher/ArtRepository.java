package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps a local stash of artwork so art mode works offline and never waits on the network.
 * Images are resized to at most 1920 px wide and stored as JPEG in the app's private files.
 */
public final class ArtRepository {
    private static final String TAG = "CleanFrameArt";
    /** Top the stash up when it falls below this many works. */
    private static final int TARGET = 40;
    /** Hard cap; the oldest works are dropped past this (about 25 MB on disk). */
    private static final int MAX = 60;
    private static final int MAX_WIDTH = 1920;

    public static final class Artwork {
        public String id;
        public String source;
        public String title;
        public String artist;
        public String date;
        public String museum;
        public String file;
        public int width;
        public int height;

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("source", source).put("title", title)
                    .put("artist", artist).put("date", date).put("museum", museum)
                    .put("file", file).put("width", width).put("height", height);
        }

        static Artwork fromJson(JSONObject o) {
            Artwork a = new Artwork();
            a.id = o.optString("id");
            a.source = o.optString("source");
            a.title = o.optString("title");
            a.artist = o.optString("artist");
            a.date = o.optString("date");
            a.museum = o.optString("museum");
            a.file = o.optString("file");
            a.width = o.optInt("width");
            a.height = o.optInt("height");
            return a;
        }
    }

    public interface Listener {
        /** Called on the main thread when a new work lands in the stash. */
        void onArtworkAdded();
    }

    private static ArtRepository instance;

    private final Context context;
    private final File dir;
    private final List<Artwork> items = new ArrayList<>();
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private final Random rnd = new Random();
    private final ExecutorService fetcher = Executors.newSingleThreadExecutor();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean fetching;
    private volatile String lastError = "";

    public static synchronized ArtRepository get(Context c) {
        if (instance == null) instance = new ArtRepository(c.getApplicationContext());
        return instance;
    }

    private ArtRepository(Context c) {
        context = c;
        dir = new File(c.getFilesDir(), "art");
        if (!dir.exists()) dir.mkdirs();
        loadIndex();
    }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public synchronized int count() { return items.size(); }

    public String lastError() { return lastError; }

    public boolean isFetching() { return fetching; }

    public synchronized Artwork find(String id) {
        for (Artwork a : items) if (a.id.equals(id)) return a;
        return null;
    }

    /** A random work that has not been shown recently, or null when the stash is empty. */
    public synchronized Artwork pickNext() {
        if (items.isEmpty()) return null;
        List<Artwork> fresh = new ArrayList<>();
        for (Artwork a : items) if (!recent.contains(a.id)) fresh.add(a);
        List<Artwork> pool = fresh.isEmpty() ? items : fresh;
        Artwork pick = pool.get(rnd.nextInt(pool.size()));
        remember(pick);
        return pick;
    }

    public synchronized void remember(Artwork a) {
        recent.remove(a.id);
        recent.addLast(a.id);
        while (recent.size() > Math.max(1, Math.min(20, items.size() - 1))) recent.removeFirst();
    }

    public interface BitmapCallback { void onBitmap(Bitmap b); }

    /** Decodes off the main thread and delivers on it. Delivers null if the file is unreadable. */
    public void loadBitmap(final Artwork a, final BitmapCallback cb) {
        decoder.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    b = BitmapFactory.decodeFile(new File(dir, a.file).getAbsolutePath());
                } catch (Throwable t) {
                    Log.w(TAG, "decode failed " + a.file, t);
                }
                if (b == null) drop(a);
                final Bitmap result = b;
                main.post(new Runnable() {
                    @Override public void run() { cb.onBitmap(result); }
                });
            }
        });
    }

    /** Every work in the stash, oldest first. */
    public synchronized List<Artwork> all() { return new ArrayList<>(items); }

    /** The work's image file. */
    public File file(Artwork a) { return new File(dir, a.file); }

    /**
     * A copy about {@code width} px wide, made once and kept beside the stash, for the
     * bookmarks page's grid. Null if the image can't be read.
     */
    public synchronized File thumbnail(Artwork a, int width) {
        File thumbs = new File(dir, "thumbs");
        File t = new File(thumbs, width + "_" + a.file);
        if (t.exists()) return t;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            int w = a.width > 0 ? a.width : MAX_WIDTH;
            while (w / (o.inSampleSize * 2) >= width) o.inSampleSize *= 2;
            Bitmap b = BitmapFactory.decodeFile(file(a).getAbsolutePath(), o);
            if (b == null) return null;
            thumbs.mkdirs();
            try (OutputStream out = new FileOutputStream(t)) {
                b.compress(Bitmap.CompressFormat.JPEG, 82, out);
            }
            b.recycle();
            return t;
        } catch (Exception e) {
            Log.w(TAG, "thumbnail failed " + a.file, e);
            return null;
        }
    }

    /**
     * Deletes a work and makes sure it never comes back, then fetches a replacement so the
     * stash stays the same size.
     */
    public void remove(String id) {
        int want;
        synchronized (this) {
            Artwork a = find(id);
            if (a == null) return;
            want = Math.max(TARGET, items.size());
            List<String> blocked = Prefs.artBlocked(context);
            if (!blocked.contains(id)) blocked.add(id);
            Prefs.setArtBlocked(context, blocked);
            recent.remove(id);
            deleteFiles(a);
            items.remove(a);
            saveIndex();
        }
        topUp(want);
    }

    /** Starts a background top-up if the stash is short. Safe to call often. */
    public void ensureStocked() {
        topUp(TARGET);
    }

    private void topUp(final int want) {
        if (fetching || count() >= want) return;
        fetching = true;
        fetcher.execute(new Runnable() {
            @Override public void run() {
                try {
                    fetchRound(want);
                } finally {
                    fetching = false;
                }
            }
        });
    }

    /** Forgets everything and fetches a fresh set. */
    public void refresh() {
        fetcher.execute(new Runnable() {
            @Override public void run() {
                synchronized (ArtRepository.this) {
                    for (Artwork a : items) deleteFiles(a);
                    items.clear();
                    recent.clear();
                    saveIndex();
                }
                notifyAdded();
            }
        });
        ensureStocked();
    }

    private void fetchRound(int want) {
        List<String> sources = new ArrayList<>();
        for (String s : ArtSources.ALL) if (Prefs.sourceEnabled(context, s)) sources.add(s);
        if (sources.isEmpty()) {
            lastError = "No museums are turned on";
            return;
        }
        int failures = 0;
        // Round-robin across museums so the stash is a mix, a handful at a time.
        List<String> blocked = Prefs.artBlocked(context);
        while (count() < want && failures < sources.size() * 2) {
            Collections.shuffle(sources, rnd);
            boolean gotAny = false;
            for (String source : sources) {
                if (count() >= want) break;
                try {
                    for (ArtSources.Candidate c : ArtSources.fetch(source, rnd, 4)) {
                        String id = c.source + ":" + c.id;
                        if (find(id) != null || blocked.contains(id)) continue;
                        if (download(c)) gotAny = true;
                    }
                } catch (Throwable t) {
                    failures++;
                    lastError = ArtSources.museumName(source) + ": " + t.getMessage();
                    Log.w(TAG, "fetch failed for " + source, t);
                }
            }
            if (!gotAny) failures++;
            else lastError = "";
        }
        trim();
    }

    private boolean download(ArtSources.Candidate c) {
        File tmp = new File(dir, "download.tmp");
        HttpURLConnection conn = null;
        try {
            conn = ArtSources.open(c.imageUrl);
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getAbsolutePath(), bounds);
            if (bounds.outWidth < 800 || !ArtSources.isWide(bounds.outWidth, bounds.outHeight)) return false;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= MAX_WIDTH) opts.inSampleSize *= 2;
            Bitmap b = BitmapFactory.decodeFile(tmp.getAbsolutePath(), opts);
            if (b == null) return false;
            if (b.getWidth() > MAX_WIDTH) {
                Bitmap scaled = Bitmap.createScaledBitmap(b, MAX_WIDTH,
                        Math.round(b.getHeight() * (MAX_WIDTH / (float) b.getWidth())), true);
                b.recycle();
                b = scaled;
            }

            Artwork a = new Artwork();
            a.id = c.source + ":" + c.id;
            a.source = c.source;
            a.title = c.title.isEmpty() ? "Untitled" : c.title;
            a.artist = c.artist;
            a.date = c.date;
            a.museum = c.museum;
            a.file = c.source + "_" + c.id.replaceAll("[^A-Za-z0-9]", "") + ".jpg";
            a.width = b.getWidth();
            a.height = b.getHeight();
            try (OutputStream out = new FileOutputStream(new File(dir, a.file))) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            b.recycle();

            synchronized (this) {
                items.add(a);
                saveIndex();
            }
            notifyAdded();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "download failed " + c.imageUrl, t);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
            tmp.delete();
        }
    }

    private synchronized void trim() {
        while (items.size() > MAX) {
            deleteFiles(items.remove(0));
        }
        saveIndex();
    }

    private void deleteFiles(Artwork a) {
        new File(dir, a.file).delete();
        File[] thumbs = new File(dir, "thumbs").listFiles();
        if (thumbs != null) for (File t : thumbs) if (t.getName().endsWith("_" + a.file)) t.delete();
    }

    private synchronized void drop(Artwork a) {
        items.remove(a);
        deleteFiles(a);
        saveIndex();
    }

    private void notifyAdded() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onArtworkAdded();
            }
        });
    }

    private synchronized void loadIndex() {
        items.clear();
        File f = new File(dir, "index.json");
        if (!f.exists()) return;
        try {
            byte[] data = new byte[(int) f.length()];
            try (InputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < data.length) {
                    int n = in.read(data, off, data.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            JSONArray arr = new JSONArray(new String(data, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                Artwork a = Artwork.fromJson(arr.getJSONObject(i));
                if (new File(dir, a.file).exists()) items.add(a);
            }
        } catch (Exception e) {
            Log.w(TAG, "index unreadable, starting fresh", e);
        }
    }

    private synchronized void saveIndex() {
        try {
            JSONArray arr = new JSONArray();
            for (Artwork a : items) arr.put(a.toJson());
            File tmp = new File(dir, "index.json.tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes("UTF-8"));
            }
            tmp.renameTo(new File(dir, "index.json"));
        } catch (Exception e) {
            Log.w(TAG, "could not save index", e);
        }
    }
}
