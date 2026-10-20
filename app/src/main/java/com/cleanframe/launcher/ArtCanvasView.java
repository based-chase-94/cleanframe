package com.cleanframe.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Draws one artwork either full-bleed (center-cropped) or inside a mat like a framed print,
 * and cross-fades between works.
 */
public class ArtCanvasView extends View {
    private static final int MATTE_LIGHT = Color.rgb(233, 229, 222);
    private static final int MATTE_DARK = Color.rgb(22, 22, 24);

    private Bitmap current;
    private Bitmap previous;
    private float fade = 1f;
    private String display = Prefs.DISPLAY_FILL;
    private ValueAnimator animator;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final RectF tmp = new RectF();

    public ArtCanvasView(Context c) {
        super(c);
        linePaint.setStyle(Paint.Style.STROKE);
    }

    public void setDisplay(String display) {
        this.display = display;
        invalidate();
    }

    public boolean hasArtwork() { return current != null; }

    public void setArtwork(Bitmap b, boolean animate) {
        if (animator != null) animator.cancel();
        previous = animate ? current : null;
        current = b;
        if (previous == null || b == null) {
            fade = 1f;
            invalidate();
            return;
        }
        fade = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                fade = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                previous = null;
                fade = 1f;
                invalidate();
            }
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        boolean matte = !Prefs.DISPLAY_FILL.equals(display);
        if (Prefs.DISPLAY_MATTE_LIGHT.equals(display)) canvas.drawColor(MATTE_LIGHT);
        else if (Prefs.DISPLAY_MATTE_DARK.equals(display)) canvas.drawColor(MATTE_DARK);
        else canvas.drawColor(Color.BLACK);

        if (previous != null && !previous.isRecycled()) draw(canvas, previous, 255, matte);
        if (current != null && !current.isRecycled()) draw(canvas, current, Math.round(255 * fade), matte);
    }

    private void draw(Canvas canvas, Bitmap b, int alpha, boolean matte) {
        int w = getWidth(), h = getHeight();
        int bw = b.getWidth(), bh = b.getHeight();
        if (w == 0 || h == 0 || bw == 0 || bh == 0) return;
        bitmapPaint.setAlpha(alpha);

        if (!matte) {
            // Center-crop to fill the screen.
            float scale = Math.max(w / (float) bw, h / (float) bh);
            int cw = Math.round(w / scale), ch = Math.round(h / scale);
            int left = (bw - cw) / 2, top = (bh - ch) / 2;
            src.set(left, top, left + cw, top + ch);
            dst.set(0, 0, w, h);
            canvas.drawBitmap(b, src, dst, bitmapPaint);
            return;
        }

        // Fit inside a generous mat, with a soft shadow and a hairline bevel like a cut mat board.
        float availW = w * 0.80f, availH = h * 0.78f;
        float scale = Math.min(availW / bw, availH / bh);
        float dw = bw * scale, dh = bh * scale;
        float left = (w - dw) / 2f, top = (h - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);

        boolean light = Prefs.DISPLAY_MATTE_LIGHT.equals(display);
        float step = Math.max(1f, w / 960f);
        for (int i = 6; i >= 1; i--) {
            tmp.set(dst);
            tmp.inset(-i * step, -i * step);
            tmp.offset(0, i * step * 0.6f);
            shadowPaint.setColor(Color.argb((int) (alpha * (light ? 0.05f : 0.09f)), 0, 0, 0));
            canvas.drawRect(tmp, shadowPaint);
        }
        tmp.set(dst);
        tmp.inset(-3 * step, -3 * step);
        linePaint.setStrokeWidth(step);
        linePaint.setColor(light ? Color.argb(alpha / 3, 255, 255, 255) : Color.argb(alpha / 5, 255, 255, 255));
        canvas.drawRect(tmp, linePaint);
        canvas.drawBitmap(b, null, dst, bitmapPaint);
    }
}
