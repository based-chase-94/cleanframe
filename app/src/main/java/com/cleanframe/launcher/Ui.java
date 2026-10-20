package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/** Small view helpers. The UI is built in code so every screen stays in one readable file. */
final class Ui {
    static final Typeface DISPLAY = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    private static Typeface serif;
    private static Typeface serifItalic;

    private Ui() {}

    /** Instrument Serif, for app names, the clock and page titles. */
    static synchronized Typeface serif(Context c) {
        if (serif == null) serif = font(c, "fonts/InstrumentSerif-Regular.ttf", Typeface.SERIF);
        return serif;
    }

    static synchronized Typeface serifItalic(Context c) {
        if (serifItalic == null) serifItalic = font(c, "fonts/InstrumentSerif-Italic.ttf", Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        return serifItalic;
    }

    private static Typeface font(Context c, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getApplicationContext().getAssets(), asset);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void shadow(TextView t) {
        t.setShadowLayer(dp(t.getContext(), 6), 0, dp(t.getContext(), 1), Color.argb(140, 0, 0, 0));
    }

    static ImageView iconButton(Context c, int drawable, String description) {
        ImageView b = new ImageView(c);
        b.setId(View.generateViewId());
        b.setImageResource(drawable);
        b.setContentDescription(description);
        b.setBackgroundResource(R.drawable.icon_button_bg);
        int p = dp(c, 14);
        b.setPadding(p, p, p, p);
        b.setFocusable(true);
        b.setClickable(true);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return b;
    }

    static TextView pill(Context c, String label) {
        final TextView t = text(c, label, 16, DISPLAY, Color.WHITE);
        t.setId(View.generateViewId());
        t.setBackgroundResource(R.drawable.pill_bg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 20), dp(c, 9), dp(c, 20), dp(c, 9));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                t.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
            }
        });
        return t;
    }
}
