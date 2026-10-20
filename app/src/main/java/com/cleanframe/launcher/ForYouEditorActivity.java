package com.cleanframe.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The shows on For You. "Add a show" takes a name or the show's web address (the Fire TV
 * phone app's keyboard can paste one); OK on a show offers moving, renaming, changing its
 * link or app, and removing. Every change saves straight away.
 */
public class ForYouEditorActivity extends Activity {
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;

    private List<Shows.Show> shows;
    private List<AppCatalog.AppEntry> apps;
    private LinearLayout listView;

    private interface TextResult { void onText(String text); }
    private interface AppResult { void onApp(String key); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 14, 16));
        root.setPadding(Ui.dp(this, 64), Ui.dp(this, 40), Ui.dp(this, 64), 0);
        root.addView(Ui.text(this, "For You shows", 44, Ui.serif(this), Color.WHITE));
        TextView hint = Ui.text(this, "Add a show by name, or by its web address for the surest link."
                + " The Fire TV app on your phone can paste one.", 14, Ui.REGULAR, Color.argb(150, 255, 255, 255));
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 14));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 40));
        listView = new LinearLayout(this);
        listView.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listView);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        shows = Shows.load(this);
        apps = AppCatalog.load(this);
        render(null);
    }

    /** Rebuilds the rows and focuses the show with {@code focusId}, or "Add a show". */
    private void render(String focusId) {
        listView.removeAllViews();
        View focus = row("Add a show", "", new Runnable() {
            @Override public void run() { addShow(); }
        });
        listView.addView(focus);
        for (final Shows.Show s : shows) {
            String value = Shows.appName(s.app) + " · " + Shows.describe(Shows.opens(this, s, apps));
            View r = row(s.title, value, new Runnable() {
                @Override public void run() { showActions(s); }
            });
            listView.addView(r);
            if (s.id.equals(focusId)) focus = r;
        }
        focus.requestFocus();
    }

    private View row(String label, String value, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 13), Ui.dp(this, 18), Ui.dp(this, 13));
        row.setFocusable(true);
        row.setClickable(true);
        TextView l = Ui.text(this, label, 19, Ui.REGULAR, Color.WHITE);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.text(this, value, 17, Ui.REGULAR, Color.argb(170, 255, 255, 255));
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        row.addView(v);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        return row;
    }

    private void addShow() {
        askText("Add a show", "Show name or web address", "", new TextResult() {
            @Override public void onText(String text) {
                if (Shows.looksLikeLink(text)) {
                    final String link = Shows.normalizeLink(text);
                    final String app = Shows.appForLink(link);
                    askText("Show name", "As it should appear on For You", Shows.titleFromLink(link), new TextResult() {
                        @Override public void onText(final String title) {
                            if (app != null) {
                                add(new Shows.Show(title, app, link));
                                return;
                            }
                            Toast.makeText(ForYouEditorActivity.this,
                                    "That address isn't from one of your apps. Pick the app it opens in.", Toast.LENGTH_LONG).show();
                            askApp(null, new AppResult() {
                                @Override public void onApp(String key) { add(new Shows.Show(title, key, link)); }
                            });
                        }
                    });
                } else {
                    final String title = text;
                    askApp(null, new AppResult() {
                        @Override public void onApp(String key) { add(new Shows.Show(title, key, "")); }
                    });
                }
            }
        });
    }

    private void add(Shows.Show s) {
        shows.add(s);
        save(s.id);
    }

    private void showActions(final Shows.Show s) {
        final int index = shows.indexOf(s);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Open it now");
        actions.add(new Runnable() {
            @Override public void run() { Shows.open(ForYouEditorActivity.this, s, apps); }
        });
        if (index > 0) {
            labels.add("Move earlier");
            actions.add(new Runnable() {
                @Override public void run() { move(s, -1); }
            });
        }
        if (index < shows.size() - 1) {
            labels.add("Move later");
            actions.add(new Runnable() {
                @Override public void run() { move(s, 1); }
            });
        }
        labels.add("Rename");
        actions.add(new Runnable() {
            @Override public void run() {
                askText("Rename", "Show name", s.title, new TextResult() {
                    @Override public void onText(String text) {
                        Posters.forget(ForYouEditorActivity.this, s);
                        s.title = text;
                        save(s.id);
                    }
                });
            }
        });
        labels.add(s.link.isEmpty() ? "Add its web address" : "Change its web address");
        actions.add(new Runnable() {
            @Override public void run() {
                askText("Web address", "The show's page, e.g. hulu.com/series/… (empty to clear)", s.link, true, new TextResult() {
                    @Override public void onText(String text) {
                        s.link = text.isEmpty() ? "" : Shows.normalizeLink(text);
                        String app = s.link.isEmpty() ? null : Shows.appForLink(s.link);
                        if (app != null) s.app = app;
                        save(s.id);
                    }
                });
            }
        });
        labels.add("Change app (" + Shows.appName(s.app) + ")");
        actions.add(new Runnable() {
            @Override public void run() {
                askApp(s.app, new AppResult() {
                    @Override public void onApp(String key) {
                        s.app = key;
                        save(s.id);
                    }
                });
            }
        });
        labels.add("Use a different poster");
        actions.add(new Runnable() {
            @Override public void run() {
                askText("Poster", "An image address (empty to look it up by name)", s.poster, true, new TextResult() {
                    @Override public void onText(String text) {
                        Posters.forget(ForYouEditorActivity.this, s);
                        s.poster = text.isEmpty() ? "" : Shows.normalizeLink(text);
                        save(s.id);
                    }
                });
            }
        });
        labels.add("Look up the poster again");
        actions.add(new Runnable() {
            @Override public void run() {
                Posters.forget(ForYouEditorActivity.this, s);
                Toast.makeText(ForYouEditorActivity.this, "The poster will refresh on For You", Toast.LENGTH_SHORT).show();
            }
        });
        labels.add("Remove");
        actions.add(new Runnable() {
            @Override public void run() {
                shows.remove(s);
                int next = Math.min(index, shows.size() - 1);
                save(next >= 0 ? shows.get(next).id : null);
            }
        });
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(s.title)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { actions.get(which).run(); }
                })
                .show();
    }

    private void move(Shows.Show s, int by) {
        int i = shows.indexOf(s);
        shows.remove(i);
        shows.add(i + by, s);
        save(s.id);
        // Keep the menu open on the same show, so moving several places is OK, OK, OK.
        showActions(s);
    }

    private void save(String focusId) {
        Shows.save(this, shows);
        render(focusId);
    }

    /** Picks one of the streaming apps; installed ones are listed first. */
    private void askApp(String current, final AppResult result) {
        final List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            for (String[] a : Shows.APPS) {
                boolean installed = Shows.installedApp(apps, a[0]) != null;
                if (installed != (pass == 0)) continue;
                keys.add(a[0]);
                names.add(a[1] + (installed ? "" : " (not installed)"));
            }
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Which app is it in?")
                .setSingleChoiceItems(names.toArray(new String[0]), keys.indexOf(current), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        result.onApp(keys.get(which));
                    }
                })
                .show();
    }

    private void askText(String title, String hint, String start, TextResult result) {
        askText(title, hint, start, false, result);
    }

    /** One line of text. Empty input is only passed on when {@code allowEmpty}. */
    private void askText(String title, String hint, String start, final boolean allowEmpty, final TextResult result) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(start);
        input.setSelection(input.getText().length());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AlertDialog d = new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null)
                .create();
        final Runnable submit = new Runnable() {
            @Override public void run() {
                String text = input.getText().toString().trim();
                if (text.isEmpty() && !allowEmpty) return;
                d.dismiss();
                result.onText(text);
            }
        };
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                submit.run();
                return true;
            }
        });
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface di) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { submit.run(); }
                });
            }
        });
        d.show();
    }
}
