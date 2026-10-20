package com.cleanframe.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Open-access museum APIs. Each source returns public-domain paintings with a downloadable
 * image URL and enough metadata for the plaque. None of them needs an API key.
 */
final class ArtSources {
    static final String AIC = "aic";
    static final String MET = "met";
    static final String CMA = "cma";
    static final String[] ALL = {AIC, MET, CMA};

    static final String USER_AGENT = "CleanFrame/1.0 (personal TV art frame)";

    /** Search terms that tend to give calm, wide pictures that suit a TV. */
    private static final String[] TERMS = {
            "landscape", "seascape", "harbor", "river", "garden", "countryside", "snow",
            "sunset", "mountains", "village", "coast", "forest", "meadow", "lake", "boats",
    };

    static final class Candidate {
        String source;
        String id;
        String title;
        String artist;
        String date;
        String museum;
        String imageUrl;
    }

    private ArtSources() {}

    static String museumName(String source) {
        if (AIC.equals(source)) return "Art Institute of Chicago";
        if (MET.equals(source)) return "The Metropolitan Museum of Art";
        if (CMA.equals(source)) return "Cleveland Museum of Art";
        return source;
    }

    static List<Candidate> fetch(String source, Random rnd, int want) throws Exception {
        String term = TERMS[rnd.nextInt(TERMS.length)];
        if (AIC.equals(source)) return aic(term, rnd, want);
        if (MET.equals(source)) return met(term, rnd, want);
        if (CMA.equals(source)) return cma(term, rnd, want);
        return new ArrayList<>();
    }

    // Art Institute of Chicago: https://api.artic.edu/docs/
    private static List<Candidate> aic(String term, Random rnd, int want) throws Exception {
        String url = "https://api.artic.edu/api/v1/artworks/search?q=" + enc(term)
                + "&query%5Bterm%5D%5Bis_public_domain%5D=true"
                + "&fields=id,title,artist_title,artist_display,date_display,image_id,thumbnail,artwork_type_title"
                + "&limit=60&page=" + (1 + rnd.nextInt(3));
        JSONObject root = new JSONObject(getString(url));
        String iiif = root.optJSONObject("config") != null
                ? root.getJSONObject("config").optString("iiif_url", "https://www.artic.edu/iiif/2")
                : "https://www.artic.edu/iiif/2";
        JSONArray data = root.optJSONArray("data");
        List<Candidate> out = new ArrayList<>();
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            String imageId = str(o, "image_id");
            if (imageId.isEmpty()) continue;
            if (!"Painting".equalsIgnoreCase(str(o, "artwork_type_title"))) continue;
            JSONObject thumb = o.optJSONObject("thumbnail");
            if (thumb != null && !isWide(thumb.optDouble("width", 0), thumb.optDouble("height", 0))) continue;
            Candidate c = new Candidate();
            c.source = AIC;
            c.id = String.valueOf(o.optLong("id"));
            c.title = str(o, "title");
            c.artist = str(o, "artist_title");
            if (c.artist.isEmpty()) c.artist = firstLine(str(o, "artist_display"));
            c.date = str(o, "date_display");
            c.museum = museumName(AIC);
            // 1686 px wide is the largest size AIC's IIIF server serves to everyone.
            c.imageUrl = iiif + "/" + imageId + "/full/1686,/0/default.jpg";
            out.add(c);
        }
        Collections.shuffle(out, rnd);
        return out.size() > want ? new ArrayList<>(out.subList(0, want)) : out;
    }

    // The Met: https://metmuseum.github.io/
    private static List<Candidate> met(String term, Random rnd, int want) throws Exception {
        String url = "https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&medium=Paintings&q=" + enc(term);
        JSONArray ids = new JSONObject(getString(url)).optJSONArray("objectIDs");
        List<Candidate> out = new ArrayList<>();
        if (ids == null) return out;
        List<Long> pool = new ArrayList<>();
        for (int i = 0; i < ids.length(); i++) pool.add(ids.getLong(i));
        Collections.shuffle(pool, rnd);
        // The search can't filter by licence or shape, so look at a few objects per wanted image.
        int tries = Math.min(pool.size(), want * 4);
        for (int i = 0; i < tries && out.size() < want; i++) {
            JSONObject o;
            try {
                o = new JSONObject(getString("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + pool.get(i)));
            } catch (Exception e) {
                continue;
            }
            if (!o.optBoolean("isPublicDomain", false)) continue;
            String img = str(o, "primaryImage");
            if (img.isEmpty()) continue;
            Candidate c = new Candidate();
            c.source = MET;
            c.id = String.valueOf(o.optLong("objectID"));
            c.title = str(o, "title");
            c.artist = str(o, "artistDisplayName");
            c.date = str(o, "objectDate");
            c.museum = museumName(MET);
            c.imageUrl = img;
            out.add(c);
        }
        return out;
    }

    // Cleveland Museum of Art: https://openaccess-api.clevelandart.org/
    private static List<Candidate> cma(String term, Random rnd, int want) throws Exception {
        String url = "https://openaccess-api.clevelandart.org/api/artworks/?cc0&has_image=1&type=Painting&limit=100&q=" + enc(term);
        JSONArray data = new JSONObject(getString(url)).optJSONArray("data");
        List<Candidate> out = new ArrayList<>();
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            JSONObject images = o.optJSONObject("images");
            if (images == null) continue;
            JSONObject img = images.optJSONObject("print");
            if (img == null) img = images.optJSONObject("web");
            if (img == null || str(img, "url").isEmpty()) continue;
            if (!isWide(img.optDouble("width", 0), img.optDouble("height", 0))) continue;
            Candidate c = new Candidate();
            c.source = CMA;
            c.id = String.valueOf(o.optLong("id"));
            c.title = str(o, "title");
            JSONArray creators = o.optJSONArray("creators");
            c.artist = creators != null && creators.length() > 0
                    ? stripParen(str(creators.getJSONObject(0), "description")) : "";
            c.date = str(o, "creation_date");
            c.museum = museumName(CMA);
            c.imageUrl = str(img, "url");
            out.add(c);
        }
        Collections.shuffle(out, rnd);
        return out.size() > want ? new ArrayList<>(out.subList(0, want)) : out;
    }

    /** Landscape-shaped enough to fill a 16:9 screen without cropping away the subject. Unknown size passes. */
    static boolean isWide(double w, double h) {
        if (w <= 0 || h <= 0) return true;
        return w / h >= 1.15;
    }

    static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("AIC-User-Agent", USER_AGENT);
        int code = conn.getResponseCode();
        if (code / 100 != 2) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        return conn;
    }

    private static String getString(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i).trim();
    }

    private static String stripParen(String s) {
        int i = s.indexOf(" (");
        return i < 0 ? s : s.substring(0, i).trim();
    }
}
