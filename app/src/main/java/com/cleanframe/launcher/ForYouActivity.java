package com.cleanframe.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * For You: a grid of bookmarked shows over the home painting. OK opens the show in its app;
 * Left from the first column or Back returns home; Menu edits the shows. Like Home, focus is
 * shown by brightness alone and nothing moves.
 */
public class ForYouActivity extends IdleActivity {
    /**
     * A row and a bit show at once, so the next row peeks in under a fade. Posters stay big
     * enough to read from the sofa.
     */
    private static final float VISIBLE_ROWS = 1.6f;
    /** Each fade's depth as a share of a row; with the focused row that fills the view. */
    private static final float FADE = 0.3f;
    private static final float POSTER_RATIO = 1.5f;

    private ArtCanvasView background;
    private FadeScrollView scroll;
    private LinearLayout grid;
    private List<AppCatalog.AppEntry> apps;
    private String focusedId;

    private int columns;
    private int posterW;
    private int posterH;
    private int gap;
    private int labelH;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        background = new ArtCanvasView(this);
        root.addView(background, match());
        // Darker than Home: posters need a quieter backdrop than a list of names.
        View scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(150, 0, 0, 0));
        root.addView(scrim, match());
        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(90, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(topScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 140)));

        TextView title = Ui.text(this, "For You", 48, Ui.serif(this), Color.WHITE);
        Ui.shadow(title);
        FrameLayout.LayoutParams titleLp = wrap(Gravity.TOP | Gravity.START);
        titleLp.setMargins(Ui.dp(this, 64), Ui.dp(this, 34), 0, 0);
        root.addView(title, titleLp);

        TextView hint = Ui.text(this, "Menu ☰ to change shows", 18, Ui.serifItalic(this), Color.argb(150, 255, 255, 255));
        Ui.shadow(hint);
        FrameLayout.LayoutParams hintLp = wrap(Gravity.TOP | Gravity.END);
        hintLp.setMargins(0, Ui.dp(this, 52), Ui.dp(this, 64), 0);
        root.addView(hint, hintLp);

        measure();
        scroll = new FadeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFade(Math.round((posterH + labelH + gap) * FADE));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round((posterH + labelH + gap) * VISIBLE_ROWS), Gravity.TOP | Gravity.START);
        scrollLp.setMargins(Ui.dp(this, 64), Ui.dp(this, 124), Ui.dp(this, 64), 0);
        root.addView(scroll, scrollLp);

        setContentView(root);
        showPainting();
    }

    /** Sizes the posters so {@link #VISIBLE_ROWS} rows fill the space under the title. */
    private void measure() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        gap = Ui.dp(this, 22);
        labelH = Ui.dp(this, 44);
        int availH = dm.heightPixels - Ui.dp(this, 124);
        int rowH = Math.round(availH / VISIBLE_ROWS);
        posterH = rowH - labelH - gap;
        posterW = Math.round(posterH / POSTER_RATIO);
        int availW = dm.widthPixels - Ui.dp(this, 128);
        columns = Math.max(3, (availW + gap) / (posterW + gap));
    }

    @Override protected void onResume() {
        super.onResume();
        apps = AppCatalog.load(this);
        build();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, ForYouEditorActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showPainting() {
        if (!Prefs.homeBackgroundArt(this)) return;
        ArtRepository repo = ArtRepository.get(this);
        final ArtRepository.Artwork a = repo.find(Prefs.homeArtId(this));
        if (a == null) return;
        Bitmap ready = HomeScreen.decoded(a);
        if (ready != null) {
            background.setArtwork(ready, false);
            return;
        }
        repo.loadBitmap(a, new ArtRepository.BitmapCallback() {
            @Override public void onBitmap(Bitmap b) {
                if (b != null) background.setArtwork(b, false);
            }
        });
    }

    private void build() {
        grid.removeAllViews();
        List<Shows.Show> shows = Shows.load(this);
        LinearLayout row = null;
        View focusTarget = null;
        if (shows.isEmpty()) {
            row = newRow();
            View add = tile(null, 0);
            row.addView(add, tileParams(0));
            add.requestFocus();
            return;
        }
        for (int i = 0; i < shows.size(); i++) {
            if (i % columns == 0) row = newRow();
            View tile = tile(shows.get(i), i % columns);
            row.addView(tile, tileParams(i % columns));
            if (shows.get(i).id.equals(focusedId)) focusTarget = tile;
        }
        // Down from the last row stops there.
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setNextFocusDownId(row.getChildAt(i).getId());
        if (focusTarget == null) {
            scroll.scrollTo(0, 0);
            focusTarget = ((ViewGroup) grid.getChildAt(0)).getChildAt(0);
        }
        focusTarget.requestFocus();
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = gap;
        grid.addView(row, lp);
        return row;
    }

    private LinearLayout.LayoutParams tileParams(int column) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(posterW, posterH + labelH);
        if (column > 0) lp.leftMargin = gap;
        return lp;
    }

    /** One poster and its title. A null show is the "Add shows" tile. */
    private View tile(final Shows.Show show, int column) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setId(View.generateViewId());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);

        FrameLayout poster = new FrameLayout(this);
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(Ui.dp(this, 10));
        card.setColor(Color.argb(60, 255, 255, 255));
        poster.setBackground(card);
        poster.setClipToOutline(true);

        // Until (or unless) a poster arrives, the card shows the title in type.
        TextView name = Ui.text(this, show == null ? "Add shows" : show.title, 26, Ui.serif(this), Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        poster.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        final ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(art, match());
        tile.addView(poster, new LinearLayout.LayoutParams(posterW, posterH));

        LinearLayout label = new LinearLayout(this);
        label.setOrientation(LinearLayout.HORIZONTAL);
        label.setGravity(Gravity.CENTER_VERTICAL);
        AppCatalog.AppEntry app = show == null ? null : Shows.installedApp(apps, show.app);
        Drawable logo = app == null ? null : AppLogos.logo(this, app);
        if (logo != null) {
            ImageView mark = new ImageView(this);
            mark.setImageDrawable(logo);
            mark.setScaleType(ImageView.ScaleType.FIT_START);
            int h = Ui.dp(this, 14);
            LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(Math.round(h * 2.1f), h);
            markLp.rightMargin = Ui.dp(this, 8);
            label.addView(mark, markLp);
        }
        TextView title = Ui.text(this, show == null ? "Choose them in Settings" : show.title, 20,
                Ui.serif(this), Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Ui.shadow(title);
        label.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, labelH);
        tile.addView(label, labelLp);

        tile.setContentDescription(show == null ? "Add shows" : show.title);
        tile.setAlpha(0.5f);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                tile.animate().alpha(hasFocus ? 1f : 0.5f).setDuration(160).start();
                if (hasFocus && show != null) focusedId = show.id;
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (show == null) startActivity(new Intent(ForYouActivity.this, ForYouEditorActivity.class));
                else Shows.open(ForYouActivity.this, show, apps);
            }
        });
        if (column == 0) {
            // Right from Home comes here, so Left from the first column goes back.
            tile.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                    if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) return false;
                    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) finish();
                    return true;
                }
            });
        }
        if (show != null) {
            Posters.load(this, show, new Posters.Callback() {
                @Override public void onPoster(Bitmap b) {
                    if (b != null) art.setImageBitmap(b);
                }
            });
        }
        return tile;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }
}
