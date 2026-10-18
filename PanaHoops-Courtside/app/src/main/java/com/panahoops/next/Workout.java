package com.panahoops.next;

import android.app.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

final class Workout extends Dialog {
  final MainActivity owner;
  final Store store;
  final Catalog catalog;
  final Catalog.Routine routine;
  final List<Catalog.Step> steps;
  final int rounds;
  final Handler handler = new Handler(Looper.getMainLooper());
  int index = 0, completed = 0;
  long remaining, elapsed = 0, last;
  boolean paused = false, finished = false;
  TextView title, time, cue, phase, position;
  Button pause;
  ProgressBar bar;
  ToneGenerator tone;

  Workout(MainActivity a, Catalog.Routine r, int rounds) {
    super(a, android.R.style.Theme_Material_Light_NoActionBar);
    owner = a;
    store = a.store;
    catalog = a.catalog;
    routine = r;
    this.rounds = rounds;
    steps = catalog.timeline(r, rounds);
    remaining = steps.get(0).seconds * 1000L;
  }

  void restore(JSONObject j) {
    index = Math.max(0, Math.min(j.optInt("index"), steps.size() - 1));
    remaining = Math.max(1, Math.min(j.optLong("remaining"), steps.get(index).seconds * 1000L));
    elapsed = Math.max(0, j.optLong("elapsed"));
    completed = Math.max(0, j.optInt("completed"));
    paused = true;
  }

  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    LinearLayout root = Ui.col(owner);
    root.setBackgroundColor(Ui.BG);
    Ui.pad(root, 24, 18, 24, 20);
    setContentView(root);
    Ui.insets(getWindow(), root, false);
    LinearLayout top = Ui.row(owner);
    top.addView(
        Ui.label(owner, "PANAHOOPS / SESSION", Ui.BLUE), new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        Ui.iconButton(
            owner,
            "close",
            "Terminar y guardar",
            Ui.WHITE,
            Ui.INK,
            () ->
                new AlertDialog.Builder(owner)
                    .setTitle("¿Terminar esta sesión?")
                    .setMessage("Se guardará el tiempo realizado.")
                    .setPositiveButton("Guardar y terminar", (d, w) -> finishSession())
                    .setNegativeButton("Seguir", null)
                    .show()),
        new LinearLayout.LayoutParams(Ui.dp(owner, 44), Ui.dp(owner, 44)));
    Ui.add(root, top);
    Ui.gap(root, 25);
    position = Ui.label(owner, "", Ui.MUTED);
    Ui.add(root, position);
    Ui.gap(root, 25);
    phase = Ui.label(owner, "", Ui.BLUE);
    Ui.add(root, phase);
    Ui.gap(root, 12);
    title = Ui.title(owner, "", 38);
    Ui.add(root, title);
    LinearLayout center = Ui.col(owner);
    center.setGravity(Gravity.CENTER);
    root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));
    time = Ui.title(owner, "", 85);
    time.setGravity(Gravity.CENTER);
    time.setTextColor(Ui.BLUE);
    Ui.add(center, time);
    Ui.gap(center, 22);
    cue = Ui.text(owner, "", 15, Ui.MUTED, false);
    cue.setGravity(Gravity.CENTER);
    Ui.add(center, cue);
    Ui.gap(center, 28);
    bar = new ProgressBar(owner, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(1000);
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
    center.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(owner, 5)));
    LinearLayout actions = Ui.row(owner);
    pause = Ui.button(owner, "Pausar", Ui.BLUE, Ui.WHITE, this::toggle);
    actions.addView(pause, new LinearLayout.LayoutParams(0, -2, 1));
    Button skip = Ui.button(owner, "Siguiente →", Ui.WHITE, Ui.INK, () -> advance(false));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = Ui.dp(owner, 10);
    actions.addView(skip, lp);
    Ui.add(root, actions);
    Ui.gap(root, 20);
    Ui.add(
        root,
        Ui.text(
            owner, "Rutina por tiempo. El conteo con cámara está en Visión.", 11, Ui.MUTED, false));
    setOnCancelListener(d -> finishSession());
    last = SystemClock.elapsedRealtime();
    render();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    handler.post(ticker);
  }

  final Runnable ticker =
      new Runnable() {
        public void run() {
          if (finished) return;
          long now = SystemClock.elapsedRealtime(), dt = Math.max(0, now - last);
          last = now;
          if (!paused) {
            long total = remaining;
            for (int i = index + 1; i < steps.size(); i++) total += steps.get(i).seconds * 1000L;
            long consumed = Math.min(total, dt);
            remaining -= consumed;
            elapsed += consumed;
            while (!finished && remaining <= 0) advance(true);
            if (!finished) render();
          }
          if (!finished) handler.postDelayed(this, 80);
        }
      };

  void toggle() {
    paused = !paused;
    last = SystemClock.elapsedRealtime();
    if (paused) {
      checkpoint();
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    } else getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    render();
  }

  void background() {
    if (finished) return;
    paused = true;
    last = SystemClock.elapsedRealtime();
    checkpoint();
    render();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  void checkpoint() {
    try {
      store
          .prefs
          .edit()
          .putString(
              "draft",
              new JSONObject()
                  .put("routine", routine.json())
                  .put("rounds", rounds)
                  .put("index", index)
                  .put("remaining", remaining)
                  .put("elapsed", elapsed)
                  .put("completed", completed)
                  .toString())
          .apply();
    } catch (JSONException ignored) {
    }
  }

  void advance(boolean auto) {
    if (finished) return;
    long over = auto ? Math.max(0, -remaining) : 0;
    if (auto && !steps.get(index).rest) completed++;
    index++;
    if (index >= steps.size()) {
      finishSession();
      return;
    }
    remaining = steps.get(index).seconds * 1000L - over;
    last = SystemClock.elapsedRealtime();
    if (store.prefs.getBoolean("sound", true))
      try {
        if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 110);
      } catch (RuntimeException ignored) {
      }
    checkpoint();
    render();
  }

  void render() {
    if (finished || title == null) return;
    Catalog.Step s = steps.get(index);
    title.setText(s.name);
    phase.setText(
        paused
            ? "SESIÓN EN PAUSA"
            : s.rest ? "RECUPERA Y PREPARA" : "VUELTA " + s.round + " / " + rounds);
    position.setText(
        routine.name.toUpperCase(Locale.ROOT) + "  ·  " + (index + 1) + " / " + steps.size());
    time.setText(Ui.time((remaining + 999) / 1000));
    cue.setText(s.cue);
    bar.setProgress(
        (int) Math.max(0, Math.min(1000, 1000 - remaining / (s.seconds * 1000f) * 1000)));
    pause.setText(paused ? "Continuar" : "Pausar");
  }

  void finishSession() {
    if (finished) return;
    finished = true;
    handler.removeCallbacks(ticker);
    if (tone != null) tone.release();
    store.prefs.edit().remove("draft").apply();
    if (elapsed >= 1000)
      store.addSession(routine.name, "", "routine", (int) (elapsed / 1000), 0, 0);
    dismiss();
    owner.showPage(owner.page);
    if (elapsed >= 1000)
      new AlertDialog.Builder(owner)
          .setTitle("Una sesión más contigo.")
          .setMessage(
              "Tiempo guardado: "
                  + Ui.time(elapsed / 1000)
                  + "\nIntervalos completos: "
                  + completed)
          .setPositiveButton("Listo", null)
          .show();
  }

  void destroy() {
    handler.removeCallbacks(ticker);
    if (tone != null) {
      tone.release();
      tone = null;
    }
    if (!finished) checkpoint();
    dismiss();
  }
}
