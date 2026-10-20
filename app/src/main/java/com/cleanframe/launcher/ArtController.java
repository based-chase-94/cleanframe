package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the art slideshow on an {@link ArtCanvasView} and {@link PlaqueView}. Shared by the
 * art-mode activity and the screensaver so both behave the same.
 */
final class ArtController implements ArtRepository.Listener {
    private static final long PLAQUE_BRIEF_MS = 10_000;

    private final Context context;
    private final ArtCanvasView canvas;
    private final PlaqueView plaque;
    private final View status;
    private final ArtRepository repo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ArtRepository.Artwork> history = new ArrayList<>();
    private int position = -1;
    private boolean running;

    private final Runnable advance = new Runnable() {
        @Override public void run() {
            next();
        }
    };

    private final Runnable hidePlaque = new Runnable() {
        @Override public void run() {
            plaque.animate().alpha(0f).setDuration(800).start();
        }
    };

    /** {@code status} is shown while there is nothing to display yet; it may be null. */
    ArtController(Context c, ArtCanvasView canvas, PlaqueView plaque, View status) {
        this.context = c;
        this.canvas = canvas;
        this.plaque = plaque;
        this.status = status;
        this.repo = ArtRepository.get(c);
    }

    void start() {
        running = true;
        canvas.setDisplay(Prefs.artDisplay(context));
        plaque.setAlpha(0f);
        repo.addListener(this);
        repo.ensureStocked();
        if (position < 0) {
            // Open on whatever the home screen was showing, so entering art mode is seamless.
            ArtRepository.Artwork first = repo.find(Prefs.homeArtId(context));
            if (first != null) {
                repo.remember(first);
                show(first, false);
                history.add(first);
                position = 0;
            } else {
                next();
            }
        } else {
            schedule();
        }
        updateStatus();
    }

    void stop() {
        running = false;
        repo.removeListener(this);
        handler.removeCallbacksAndMessages(null);
    }

    void next() {
        if (position + 1 < history.size()) {
            position++;
            show(history.get(position), true);
            return;
        }
        ArtRepository.Artwork a = repo.pickNext();
        if (a == null) {
            updateStatus();
            return;
        }
        history.add(a);
        if (history.size() > 50) history.remove(0);
        position = history.size() - 1;
        show(a, true);
    }

    void previous() {
        if (position > 0) {
            position--;
            show(history.get(position), true);
        }
    }

    /** Shows the plaque for a while (or keeps it, if set to always). */
    void revealPlaque() {
        if (Prefs.PLAQUE_HIDDEN.equals(Prefs.plaque(context)) || !canvas.hasArtwork()) return;
        handler.removeCallbacks(hidePlaque);
        plaque.animate().alpha(1f).setDuration(500).start();
        if (!Prefs.PLAQUE_ALWAYS.equals(Prefs.plaque(context))) handler.postDelayed(hidePlaque, PLAQUE_BRIEF_MS);
    }

    private void show(final ArtRepository.Artwork a, final boolean animate) {
        repo.loadBitmap(a, new ArtRepository.BitmapCallback() {
            @Override public void onBitmap(Bitmap b) {
                if (!running) return;
                if (b == null) {
                    history.remove(a);
                    position = Math.min(position, history.size() - 1);
                    next();
                    return;
                }
                canvas.setArtwork(b, animate);
                plaque.bind(a);
                Prefs.setHomeArt(context, a.id);
                updateStatus();
                revealPlaque();
                schedule();
            }
        });
    }

    private void schedule() {
        handler.removeCallbacks(advance);
        if (running) handler.postDelayed(advance, Prefs.artIntervalMinutes(context) * 60_000L);
    }

    private void updateStatus() {
        if (status != null) status.setVisibility(canvas.hasArtwork() ? View.GONE : View.VISIBLE);
    }

    @Override public void onArtworkAdded() {
        if (running && !canvas.hasArtwork()) next();
    }
}
