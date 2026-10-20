package com.cleanframe.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A small web server on the home network so the For You shows can be edited from a computer:
 * open http://(TV address):8765 in a browser. The TV keeps the list; the page reads and writes
 * it here, so there is nothing to sync and nothing to install on the computer.
 *
 * Only devices on the local network are answered, and changes need a header a plain web form
 * can't send, so another website open in the browser can't edit the list.
 */
final class BookmarkServer {
    private static final String TAG = "CleanFrame.Server";
    static final int PORT = 8765;
    private static final String TVMAZE_SEARCH = "https://api.tvmaze.com/search/shows?q=";
    private static final int MAX_BODY = 512 * 1024;

    private static BookmarkServer instance;

    private final Context app;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    private BookmarkServer(Context c) { app = c.getApplicationContext(); }

    /** Starts the server once per process. Safe to call often. */
    static synchronized void start(Context c) {
        if (instance != null) return;
        instance = new BookmarkServer(c);
        Thread t = new Thread(new Runnable() {
            @Override public void run() { instance.listen(); }
        }, "bookmark-server");
        t.setDaemon(true);
        t.start();
    }

    /** "http://192.168.0.52:8765", or null when the TV has no network address. */
    static String address() {
        String ip = localIp();
        return ip == null ? null : "http://" + ip + ":" + PORT;
    }

    private static String localIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // No network.
        }
        return null;
    }

    private void listen() {
        while (true) {
            try (ServerSocket server = new ServerSocket(PORT)) {
                while (true) {
                    final Socket s = server.accept();
                    pool.execute(new Runnable() {
                        @Override public void run() { handle(s); }
                    });
                }
            } catch (IOException e) {
                Log.w(TAG, "server stopped, retrying", e);
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private static final class Request {
        String method;
        String path;
        Map<String, String> query = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(15_000);
            InetAddress from = s.getInetAddress();
            if (!(from.isSiteLocalAddress() || from.isLoopbackAddress() || from.isLinkLocalAddress())) {
                s.close();
                return;
            }
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            Request r = read(in);
            if (r == null) {
                s.close();
                return;
            }
            route(r, out);
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "request failed", e);
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private static Request read(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split(" ");
        if (parts.length < 2) return null;
        Request r = new Request();
        r.method = parts[0];
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int eq = kv.indexOf('=');
                if (eq > 0) r.query.put(kv.substring(0, eq), URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
            }
        }
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            int colon = h.indexOf(':');
            if (colon > 0) r.headers.put(h.substring(0, colon).trim().toLowerCase(Locale.US), h.substring(colon + 1).trim());
        }
        String len = r.headers.get("content-length");
        if (len != null) {
            int n = Integer.parseInt(len);
            if (n < 0 || n > MAX_BODY) throw new IOException("body too large");
            r.body = new byte[n];
            int got = 0;
            while (got < n) {
                int k = in.read(r.body, got, n - got);
                if (k < 0) throw new IOException("short body");
                got += k;
            }
        }
        return r;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) throw new IOException("line too long");
        }
        if (c < 0 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private void route(Request r, OutputStream out) throws Exception {
        String p = r.path;
        boolean get = "GET".equals(r.method) || "HEAD".equals(r.method);
        if (get && (p.equals("/") || p.equals("/index.html"))) {
            asset(out, "web/foryou.html", "text/html; charset=utf-8");
        } else if (get && p.equals("/manifest.json")) {
            asset(out, "web/manifest.json", "application/manifest+json");
        } else if (get && p.equals("/icon.png")) {
            asset(out, "web/icon.png", "image/png");
        } else if (get && p.startsWith("/fonts/") && p.endsWith(".ttf") && !p.contains("..")) {
            asset(out, p.substring(1), "font/ttf");
        } else if (get && p.equals("/api/shows")) {
            json(out, 200, state().toString());
        } else if (get && p.startsWith("/poster/")) {
            poster(out, p.substring("/poster/".length()));
        } else if (get && p.equals("/api/search")) {
            json(out, 200, search(r.query.get("q")).toString());
        } else if (!get && !"1".equals(r.headers.get("x-cleanframe"))) {
            // Browsers won't send a custom header cross-site without asking first, and we never say yes.
            json(out, 403, "{\"error\":\"missing header\"}");
        } else if ("PUT".equals(r.method) && p.equals("/api/shows")) {
            saveShows(new JSONArray(new String(r.body, "UTF-8")));
            json(out, 200, state().toString());
        } else if ("POST".equals(r.method) && p.equals("/api/open")) {
            json(out, openOnTv(r.query.get("id")) ? 200 : 404, "{}");
        } else {
            send(out, 404, "text/plain", "Not found".getBytes("UTF-8"));
        }
    }

    /** The shows, the apps they can open in, and what OK will do for each on this TV. */
    private JSONObject state() throws Exception {
        List<AppCatalog.AppEntry> apps = AppCatalog.load(app);
        JSONArray shows = new JSONArray();
        for (Shows.Show s : Shows.load(app)) {
            shows.put(new JSONObject().put("id", s.id).put("title", s.title).put("app", s.app)
                    .put("link", s.link).put("poster", s.poster)
                    .put("opens", Shows.describe(Shows.opens(app, s, apps)))
                    .put("hasPoster", Posters.cached(app, s) != null));
        }
        JSONArray appList = new JSONArray();
        for (String[] a : Shows.APPS) {
            appList.put(new JSONObject().put("key", a[0]).put("name", a[1])
                    .put("installed", Shows.installedApp(apps, a[0]) != null)
                    .put("search", Shows.webSearch(a[0], "")));
        }
        return new JSONObject().put("shows", shows).put("apps", appList).put("version", versionName());
    }

    private void saveShows(JSONArray a) throws Exception {
        final List<Shows.Show> shows = new java.util.ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String title = o.optString("title", "").trim();
            if (title.isEmpty()) continue;
            String link = o.optString("link", "").trim();
            Shows.Show s = new Shows.Show(title, o.optString("app", ""), link.isEmpty() ? "" : Shows.normalizeLink(link));
            String id = o.optString("id", "");
            if (!id.isEmpty()) s.id = id;
            String poster = o.optString("poster", "").trim();
            s.poster = poster.isEmpty() ? "" : Shows.normalizeLink(poster);
            shows.add(s);
        }
        // Saved on the main thread, where For You listens for changes.
        final CountDownLatch done = new CountDownLatch(1);
        main.post(new Runnable() {
            @Override public void run() {
                Shows.save(app, shows);
                done.countDown();
                // Fetch new posters now, so they're ready on the TV and on the page's next load.
                for (Shows.Show s : shows) {
                    Posters.load(app, s, new Posters.Callback() {
                        @Override public void onPoster(android.graphics.Bitmap b) {}
                    });
                }
            }
        });
        done.await(5, TimeUnit.SECONDS);
    }

    private boolean openOnTv(String id) {
        for (final Shows.Show s : Shows.load(app)) {
            if (!s.id.equals(id)) continue;
            main.post(new Runnable() {
                @Override public void run() { Shows.open(app, s, AppCatalog.load(app)); }
            });
            return true;
        }
        return false;
    }

    /** TVmaze's matches for a show name, trimmed to what the page shows. */
    private static JSONArray search(String q) {
        JSONArray out = new JSONArray();
        if (q == null || q.trim().isEmpty()) return out;
        try {
            JSONArray hits = new JSONArray(ArtSources.getString(TVMAZE_SEARCH + android.net.Uri.encode(q.trim())));
            for (int i = 0; i < hits.length() && out.length() < 12; i++) {
                JSONObject show = hits.getJSONObject(i).optJSONObject("show");
                if (show == null) continue;
                JSONObject img = show.optJSONObject("image");
                String premiered = show.optString("premiered", "");
                JSONObject net = show.optJSONObject("webChannel");
                if (net == null) net = show.optJSONObject("network");
                out.put(new JSONObject()
                        .put("title", show.optString("name", ""))
                        .put("year", premiered.length() >= 4 ? premiered.substring(0, 4) : "")
                        .put("network", net == null ? "" : net.optString("name", ""))
                        .put("poster", img == null ? "" : img.optString("original", img.optString("medium", "")))
                        .put("thumb", img == null ? "" : img.optString("medium", "")));
            }
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
        }
        return out;
    }

    private void poster(OutputStream out, String id) throws IOException {
        for (Shows.Show s : Shows.load(app)) {
            if (!s.id.equals(id)) continue;
            File f = Posters.cached(app, s);
            if (f != null) {
                send(out, 200, "image/jpeg", readAll(new FileInputStream(f)));
                return;
            }
        }
        send(out, 404, "text/plain", new byte[0]);
    }

    private void asset(OutputStream out, String name, String type) throws IOException {
        try {
            send(out, 200, type, readAll(app.getAssets().open(name)));
        } catch (IOException e) {
            send(out, 404, "text/plain", new byte[0]);
        }
    }

    private static void json(OutputStream out, int code, String body) throws IOException {
        send(out, code, "application/json; charset=utf-8", body.getBytes("UTF-8"));
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 403 ? "Forbidden" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(body);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally {
            in.close();
        }
    }

    private String versionName() {
        try {
            return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
