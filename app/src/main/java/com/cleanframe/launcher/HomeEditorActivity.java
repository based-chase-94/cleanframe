package com.cleanframe.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-zone Home editor. Left: On Home, in order. Right: Not on Home (recently removed first,
 * then every other installed app).
 *
 * OK on a name picks it up; Up/Down move it; OK places it. Right reaches Remove. Removing
 * moves the app to Recently removed and focus follows it, so a second OK puts it back where it
 * was. Every finished change saves immediately; Undo steps back.
 */
public class HomeEditorActivity extends Activity {
    private static final int LIFTED_BG = Color.argb(70, 255, 255, 255);

    private Map<String, AppCatalog.AppEntry> apps;
    private List<String> home;
    private List<String> recent;
    /** Where a removed app used to sit, so putting it back restores its place. */
    private final Map<String, Integer> removedFrom = new HashMap<>();
    private final ArrayDeque<List<List<String>>> undo = new ArrayDeque<>();

    private String lifted;
    private List<String> beforeLift;
    /** A key whose press was used while lifted; its release must not reach the views. */
    private int swallowUp = -1;

    private LinearLayout onHomeColumn;
    private LinearLayout notOnHomeColumn;
    private TextView undoButton;
    private TextView hint;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apps = AppCatalog.index(AppCatalog.load(this));
        home = Prefs.homeOrder(this);
        recent = Prefs.recentlyRemoved(this);
        if (!Prefs.homeInitialized(this)) home = AppCatalog.defaultHome(new ArrayList<>(apps.values()));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(14, 14, 16));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 64), Ui.dp(this, 40), Ui.dp(this, 64), 0);
        page.addView(Ui.text(this, "Apps on Home", 34, Ui.DISPLAY, Color.WHITE));
        hint = Ui.text(this, "", 14, Ui.REGULAR, Color.argb(150, 255, 255, 255));
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        page.addView(hint);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        onHomeColumn = column();
        notOnHomeColumn = column();
        columns.addView(wrapColumn("On Home", onHomeColumn), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f));
        View gap = new View(this);
        columns.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 40), 1));
        columns.addView(wrapColumn("Not on Home", notOnHomeColumn), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        page.addView(columns, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actions = new LinearLayout(this);
        undoButton = Ui.pill(this, "Undo");
        TextView done = Ui.pill(this, "Done");
        actions.addView(undoButton);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLp.leftMargin = Ui.dp(this, 12);
        actions.addView(done, doneLp);
        FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        actionsLp.setMargins(0, Ui.dp(this, 40), Ui.dp(this, 64), 0);
        root.addView(actions, actionsLp);
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { undo(); }
        });
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        setContentView(root);
        render(home.isEmpty() ? null : "home:" + home.get(0));
        if (home.isEmpty() && notOnHomeColumn.getChildCount() > 1) notOnHomeColumn.getChildAt(1).requestFocus();
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View wrapColumn(String title, LinearLayout content) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, title.toUpperCase(), 13, Ui.DISPLAY, Color.argb(140, 255, 255, 255));
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(this, 16), 0, 0, Ui.dp(this, 8));
        box.addView(t);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(this, 40));
        scroll.addView(content);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    /** Rebuilds both columns and focuses the view tagged {@code focusTag} ("home:pkg", "remove:pkg", "other:pkg"). */
    private void render(String focusTag) {
        onHomeColumn.removeAllViews();
        notOnHomeColumn.removeAllViews();

        for (final String pkg : home) {
            AppCatalog.AppEntry app = apps.get(pkg);
            if (app == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = item(app.label, "home:" + pkg);
            if (pkg.equals(lifted)) {
                name.setText("↕  " + app.label);
                name.setBackgroundColor(LIFTED_BG);
            }
            name.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { lift(pkg); }
            });
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = Ui.pill(this, "Remove");
            remove.setTag("remove:" + pkg);
            remove.setTextSize(14);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { remove(pkg); }
            });
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            removeLp.leftMargin = Ui.dp(this, 10);
            row.addView(remove, removeLp);
            // Only reachable sideways from its own row, never by scrolling the column.
            remove.setFocusable(lifted == null);
            name.setNextFocusRightId(remove.getId());
            onHomeColumn.addView(row);
        }
        if (onHomeColumn.getChildCount() == 0) {
            TextView empty = Ui.text(this, "Nothing on Home yet. Pick apps on the right.", 16, Ui.REGULAR, Color.argb(150, 255, 255, 255));
            empty.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), 0, 0);
            onHomeColumn.addView(empty);
        }

        List<String> recentShown = new ArrayList<>();
        for (String pkg : recent) if (!home.contains(pkg) && apps.containsKey(pkg)) recentShown.add(pkg);
        if (!recentShown.isEmpty()) {
            notOnHomeColumn.addView(subhead("Recently removed"));
            for (String pkg : recentShown) notOnHomeColumn.addView(otherItem(pkg));
        }
        notOnHomeColumn.addView(subhead(recentShown.isEmpty() ? "Installed apps" : "Other installed apps"));
        for (String pkg : apps.keySet()) {
            if (!home.contains(pkg) && !recentShown.contains(pkg)) notOnHomeColumn.addView(otherItem(pkg));
        }

        undoButton.setAlpha(undo.isEmpty() ? 0.4f : 1f);
        hint.setText(lifted != null
                ? "▲▼ move · OK places it · Back cancels"
                : "OK picks an app up to move it · → Remove · OK on the right adds an app · Changes save as you go");

        if (focusTag != null) {
            View v = getWindow().getDecorView().findViewWithTag(focusTag);
            if (v != null) v.requestFocus();
        }
    }

    private TextView subhead(String s) {
        TextView t = Ui.text(this, s, 13, Ui.REGULAR, Color.argb(120, 255, 255, 255));
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), 0, Ui.dp(this, 4));
        return t;
    }

    private View otherItem(final String pkg) {
        TextView t = item(apps.get(pkg).label, "other:" + pkg);
        t.setTextColor(Color.argb(200, 255, 255, 255));
        t.setFocusable(lifted == null);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { add(pkg); }
        });
        return t;
    }

    private TextView item(String label, String tag) {
        TextView t = Ui.text(this, label, 22, Ui.DISPLAY, Color.WHITE);
        t.setId(View.generateViewId());
        t.setTag(tag);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setBackgroundResource(R.drawable.row_bg);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 11), Ui.dp(this, 16), Ui.dp(this, 11));
        t.setFocusable(true);
        t.setClickable(true);
        return t;
    }

    private void lift(String pkg) {
        lifted = pkg;
        beforeLift = new ArrayList<>(home);
        render("home:" + pkg);
    }

    private void moveLifted(int delta) {
        int i = home.indexOf(lifted);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= home.size()) return;
        home.remove(i);
        home.add(j, lifted);
        render("home:" + lifted);
    }

    private void place() {
        String pkg = lifted;
        lifted = null;
        if (!beforeLift.equals(home)) {
            pushUndo(beforeLift, recent);
            save();
        }
        render("home:" + pkg);
    }

    private void cancelLift() {
        String pkg = lifted;
        lifted = null;
        home = beforeLift;
        render("home:" + pkg);
    }

    private void remove(String pkg) {
        pushUndo(home, recent);
        removedFrom.put(pkg, home.indexOf(pkg));
        home.remove(pkg);
        recent.remove(pkg);
        recent.add(0, pkg);
        save();
        render("other:" + pkg);
    }

    private void add(String pkg) {
        pushUndo(home, recent);
        Integer at = removedFrom.remove(pkg);
        int index = at == null ? home.size() : Math.min(at, home.size());
        home.add(index, pkg);
        recent.remove(pkg);
        save();
        render("home:" + pkg);
    }

    private void pushUndo(List<String> h, List<String> r) {
        List<List<String>> snap = new ArrayList<>();
        snap.add(new ArrayList<>(h));
        snap.add(new ArrayList<>(r));
        undo.push(snap);
        while (undo.size() > 30) undo.removeLast();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        List<List<String>> snap = undo.pop();
        home = snap.get(0);
        recent = snap.get(1);
        save();
        render("home:" + (home.isEmpty() ? "" : home.get(0)));
        undoButton.requestFocus();
    }

    private void save() {
        Prefs.setHomeOrder(this, home);
        Prefs.setRecentlyRemoved(this, recent);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP && e.getKeyCode() == swallowUp) {
            // Otherwise the OK that places an app would pick it straight back up, and the
            // Back that cancels a move would also leave the editor.
            swallowUp = -1;
            return true;
        }
        if (lifted == null) return super.dispatchKeyEvent(e);
        // While an app is picked up, the remote moves it instead of moving focus.
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            swallowUp = e.getKeyCode();
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP: moveLifted(-1); break;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveLifted(1); break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER: place(); break;
                case KeyEvent.KEYCODE_BACK: cancelLift(); break;
                default: break;
            }
        }
        return true;
    }
}
