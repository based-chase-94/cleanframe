package com.cleanframe.launcher;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The small museum label: title, artist and date, collection. */
public class PlaqueView extends LinearLayout {
    private final TextView title;
    private final TextView byline;
    private final TextView museum;

    public PlaqueView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setBackgroundResource(R.drawable.plaque_bg);
        int p = Ui.dp(c, 16);
        setPadding(p, Ui.dp(c, 12), p, Ui.dp(c, 12));

        title = Ui.text(c, "", 22, Ui.serif(c), Color.WHITE);
        title.setMaxLines(2);
        title.setMaxWidth(Ui.dp(c, 360));
        title.setGravity(Gravity.END);
        byline = Ui.text(c, "", 13, Ui.REGULAR, Color.argb(220, 255, 255, 255));
        byline.setPadding(0, Ui.dp(c, 6), 0, 0);
        byline.setMaxWidth(Ui.dp(c, 360));
        byline.setGravity(Gravity.END);
        museum = Ui.text(c, "", 11, Ui.light(c), Color.argb(170, 255, 255, 255));
        museum.setAllCaps(true);
        museum.setLetterSpacing(0.14f);
        museum.setPadding(0, Ui.dp(c, 6), 0, 0);
        museum.setGravity(Gravity.END);

        addView(title);
        addView(byline);
        addView(museum);
    }

    public void bind(ArtRepository.Artwork a) {
        if (a == null) return;
        title.setText(a.title);
        StringBuilder sb = new StringBuilder();
        if (a.artist != null && !a.artist.isEmpty()) sb.append(a.artist);
        if (a.date != null && !a.date.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(a.date);
        }
        byline.setText(sb.length() > 0 ? sb.toString() : "Artist unknown");
        museum.setText(a.museum + " · Public domain");
    }
}
