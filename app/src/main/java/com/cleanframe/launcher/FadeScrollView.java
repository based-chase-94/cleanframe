package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.widget.ScrollView;

/**
 * A ScrollView whose edge row fades out much harder than the stock fading edge: the row is
 * barely visible by its middle. Like the stock edge, each fade only shows when there are
 * rows hidden beyond it, and ScrollView still keeps the focused row clear of it.
 */
final class FadeScrollView extends ScrollView {
    /** Opacity across the fading row, from its inner edge to the screen edge. */
    private static final float[] STOPS = {0f, 0.5f, 1f};
    private static final float[] ALPHAS = {0.42f, 0.12f, 0f};

    private final Paint paint = new Paint();
    private int fade;

    FadeScrollView(Context c) {
        super(c);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        // Left enabled so ScrollView reserves the space when scrolling to the focused row;
        // the stock drawing is switched off below and replaced in draw().
        setVerticalFadingEdgeEnabled(true);
    }

    /** How deep each fade is, in px. One row. */
    void setFade(int px) {
        fade = px;
        setFadingEdgeLength(px);
    }

    @Override protected float getTopFadingEdgeStrength() { return 0; }
    @Override protected float getBottomFadingEdgeStrength() { return 0; }

    @Override public void draw(Canvas canvas) {
        float top = edgeStrength(getScrollY());
        float bottom = getChildCount() == 0 ? 0
                : edgeStrength(getChildAt(0).getBottom() - getScrollY() - getHeight());
        if (fade <= 0 || (top == 0 && bottom == 0)) {
            super.draw(canvas);
            return;
        }
        int x = getScrollX(), y = getScrollY(), w = getWidth(), h = getHeight();
        int layer = canvas.saveLayer(x, y, x + w, y + h, null);
        super.draw(canvas);
        if (top > 0) {
            paint.setShader(gradient(y + fade, y, top));
            canvas.drawRect(x, y, x + w, y + fade, paint);
        }
        if (bottom > 0) {
            paint.setShader(gradient(y + h - fade, y + h, bottom));
            canvas.drawRect(x, y + h - fade, x + w, y + h, paint);
        }
        canvas.restoreToCount(layer);
    }

    /** 0 when nothing is hidden past this edge, rising to 1 once a full row is. */
    private float edgeStrength(int hiddenPx) {
        if (fade <= 0 || hiddenPx <= 0) return 0;
        return Math.min(1f, hiddenPx / (float) fade);
    }

    private LinearGradient gradient(float from, float to, float strength) {
        int[] colors = new int[ALPHAS.length];
        for (int i = 0; i < ALPHAS.length; i++) {
            float a = 1f - strength * (1f - ALPHAS[i]);
            colors[i] = Math.round(a * 255) << 24;
        }
        return new LinearGradient(0, from, 0, to, colors, STOPS, Shader.TileMode.CLAMP);
    }
}
