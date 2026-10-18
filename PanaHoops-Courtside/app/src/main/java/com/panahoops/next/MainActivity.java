package com.panahoops.next;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public final class MainActivity extends ComponentActivity {
  Store store;
  Catalog catalog;
  FrameLayout content;
  LinearLayout nav;
  ScrollView scroll;
  int page = 0, zone = 0, period = 0;
  String filter = "all", lastShot, pendingExport;
  Workout workout;
  TextView fitStepValue, fitWaterValue, fitStatusValue;
  final Handler fitHandler = new Handler(Looper.getMainLooper());
  final Runnable fitRefresh = new Runnable() {
    @Override public void run() {
      if (page == 3 && !isFinishing()) {
        updateFitValues();
        fitHandler.postDelayed(this, 2500);
      }
    }
  };
  final java.util.concurrent.ExecutorService io =
      java.util.concurrent.Executors.newSingleThreadExecutor();

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    store = new Store(this);
    if(!store.prefs.getBoolean("qualityMigratedV31",false)) {
      int old=store.prefs.getInt("quality",0);
      store.prefs.edit().putInt("quality",old==0?1:0).putBoolean("qualityMigratedV31",true).commit();
    }
    catalog = new Catalog();
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = Ui.row(this);
    Ui.pad(nav, 15, 9, 15, 8);
    root.addView(nav, new LinearLayout.LayoutParams(-1, Ui.dp(this, 80)));
    setContentView(root);
    Ui.insets(this, root, false);
    showPage(0);
  }

  protected void onResume() {
    super.onResume();
    if (store != null) showPage(page);
    fitHandler.removeCallbacks(fitRefresh);
    if(page == 3) fitHandler.postDelayed(fitRefresh, 2500);
  }

  protected void onStop() {
    fitHandler.removeCallbacks(fitRefresh);
    if (workout != null && workout.isShowing()) workout.background();
    super.onStop();
  }

  protected void onDestroy() {
    fitHandler.removeCallbacks(fitRefresh);
    if (workout != null) workout.destroy();
    io.execute(store::close);
    io.shutdown();
    super.onDestroy();
  }

  void vision(String mode) {
    startActivity(new Intent(this, VisionActivity.class).putExtra("mode", mode));
  }

  void showPage(int n) {
    page = n;
    fitHandler.removeCallbacks(fitRefresh);
    content.removeAllViews();
    scroll = new ScrollView(this);
    scroll.setClipToPadding(false);
    LinearLayout b = Ui.col(this);
    Ui.pad(b, 22, 13, 22, 28);
    scroll.addView(b);
    content.addView(scroll);
    navigation();
    switch (n) {
      case 1 -> training(b);
      case 3 -> fit(b);
      case 4 -> progress(b);
      case 5 -> settings(b);
      default -> home(b);
    }
    if (n == 3) fitHandler.postDelayed(fitRefresh, 2500);
  }

  void navigation() {
    nav.removeAllViews();
    String[] labels = {"Inicio", "Entrenar", "Visión", "FIT", "Progreso"},
        icons = {"home", "train", "lens", "train", "chart"};
    for (int i = 0; i < 5; i++) {
      final int index = i;
      int color = i == 2 ? Ui.WHITE : page == i ? Ui.BLUE : Ui.MUTED;
      LinearLayout cell = Ui.col(this);
      cell.setGravity(Gravity.CENTER);
      Ui.surface(cell, i == 2 ? Ui.BLUE : Ui.BG, 23);
      cell.addView(
          new Ui.Icon(this, icons[i], color),
          new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
      Ui.gap(cell, 6);
      TextView label = Ui.text(this, labels[i], 9, color, page == i);
      label.setGravity(Gravity.CENTER);
      cell.addView(label);
      cell.setContentDescription(labels[i]);
      cell.setOnClickListener(
          v -> {
            if (index == 2) vision("shot");
            else showPage(index);
          });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
      p.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
      nav.addView(cell, p);
    }
  }

  void top(LinearLayout b, String kicker, String title, String sub) {
    LinearLayout row = Ui.row(this);
    row.addView(Ui.label(this, kicker, Ui.BLUE), new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(
        Ui.iconButton(
            this, "settings", "Perfil y preferencias", Ui.WHITE, Ui.INK, () -> showPage(5)),
        new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
    Ui.add(b, row);
    Ui.gap(b, 18);
    Ui.add(b, Ui.title(this, title, 31));
    if (!sub.isEmpty()) {
      Ui.gap(b, 12);
      Ui.add(b, Ui.text(this, sub, 13, Ui.MUTED, false));
    }
    Ui.gap(b, 24);
  }

  void heading(LinearLayout b, String title, String link, Runnable run) {
    LinearLayout row = Ui.row(this);
    row.addView(Ui.title(this, title, 25), new LinearLayout.LayoutParams(0, -2, 1));
    if (!link.isEmpty()) {
      TextView t = Ui.text(this, link, 12, Ui.BLUE, true);
      Ui.pad(t, 10, 10, 0, 10);
      t.setOnClickListener(v -> run.run());
      t.setFocusable(true);
      row.addView(t);
    }
    Ui.add(b, row);
    Ui.gap(b, 15);
  }

  LinearLayout metric(String value, String label) {
    LinearLayout m = Ui.col(this);
    Ui.add(m, Ui.title(this, value, 36));
    Ui.gap(m, 7);
    Ui.add(m, Ui.label(this, label, Ui.MUTED));
    return m;
  }

  void home(LinearLayout b) {
    LinearLayout advanced = Ui.row(this);
    advanced.addView(
        Ui.button(
            this,
            "Courtside BETA",
            Ui.DARK,
            Ui.WHITE,
            () -> startActivity(new Intent(this, MatchActivity.class))),
        new LinearLayout.LayoutParams(0, -2, 1));
    advanced.addView(
        Ui.button(
            this,
            "Mi equipo",
            Ui.PALE,
            Ui.BLUE,
            () -> startActivity(new Intent(this, SocialActivity.class))),
        new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(b, advanced);
    Ui.gap(b, 14);
    String name = store.prefs.getString("name", "");
    top(
        b,
        "PANAHOOPS  /  COURTSIDE",
        "Hoy se juega\nmejor.",
        name.isEmpty()
            ? "Tu entrenamiento empieza con un movimiento."
            : name + ", este es tu espacio para mejorar.");
    FrameLayout hero = new FrameLayout(this);
    hero.setBackground(Ui.shape(this, Ui.DARK, 28));
    hero.setClipToOutline(true);
    FrameLayout.LayoutParams a =
        new FrameLayout.LayoutParams(
            Ui.dp(this, 290), Ui.dp(this, 235), Gravity.END | Gravity.BOTTOM);
    a.rightMargin = -Ui.dp(this, 18);
    hero.addView(new Art(this, "basket"), a);
    LinearLayout words = Ui.col(this);
    Ui.pad(words, 23, 24, 12, 22);
    Ui.add(words, Ui.label(this, "VISION STUDIO", 0xCCFFFFFF));
    Ui.gap(words, 19);
    TextView t = Ui.title(this, "MIRA TU\nSIGUIENTE\nNIVEL.", 30);
    t.setTextColor(Ui.WHITE);
    Ui.add(words, t);
    Ui.gap(words, 17);
    Ui.add(words, Ui.text(this, "Tu cámara. Cada movimiento.", 11, 0xDDFFFFFF, false));
    hero.addView(words, new FrameLayout.LayoutParams(-1, -1));
    View arrow =
        Ui.iconButton(this, "arrow", "Analizar en vivo", Ui.WHITE, Ui.BLUE, () -> vision("shot"));
    FrameLayout.LayoutParams arrowP =
        new FrameLayout.LayoutParams(Ui.dp(this, 49), Ui.dp(this, 49), Gravity.TOP | Gravity.END);
    arrowP.setMargins(0, Ui.dp(this, 20), Ui.dp(this, 20), 0);
    hero.addView(arrow, arrowP);
    hero.setOnClickListener(v -> vision("shot"));
    b.addView(hero, new LinearLayout.LayoutParams(-1, Ui.dp(this, 275)));
    Ui.gap(b, 22);
    List<Store.Session> ss = store.sessions();
    long start =
        LocalDate.now()
            .with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    int weekly = 0, seconds = 0;
    for (Store.Session s : ss) {
      seconds += s.seconds;
      if (s.date >= start) weekly++;
    }
    LinearLayout stats = Ui.row(this);
    stats.addView(
        metric("" + weekly, "SESIONES ESTA SEMANA"), new LinearLayout.LayoutParams(0, -2, 1));
    stats.addView(
        metric("" + Math.round(seconds / 60f), "MINUTOS CONTIGO"),
        new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(b, stats);
    Ui.gap(b, 26);
    if (store.prefs.contains("draft")) {
      Ui.add(
          b, Ui.button(this, "Retomar sesión en pausa  →", Ui.PALE, Ui.BLUE, this::restoreWorkout));
      Ui.gap(b, 22);
    }
    heading(b, "Tu siguiente paso", "Ver todo ↗", () -> showPage(1));
    String focus = store.prefs.getString("focus", "basket");
    Catalog.Routine rec = catalog.routines.get(0);
    for (Catalog.Routine r : catalog.routines)
      if (r.category.equals(focus)) {
        rec = r;
        break;
      }
    if (focus.equals("strength") && store.prefs.getInt("level", 0) == 0)
      rec = catalog.routines.get(6);
    routineRow(b, rec);
    Ui.gap(b, 20);
    LinearLayout box = Ui.col(this);
    box.setBackground(Ui.shape(this, 0xFFFFE8DC, 23));
    Ui.pad(box, 21, 20, 21, 20);
    Ui.add(box, Ui.label(this, "UN INTENTO MÁS. UNA RAZÓN MÁS.", 0xFF9A4A2E));
    Ui.gap(box, 12);
    Ui.add(box, Ui.title(this, "Haz tuya la cancha.", 27));
    Ui.gap(box, 10);
    Ui.add(
        box,
        Ui.text(
            this,
            "Registra tus aciertos por zona y descubre dónde seguir practicando.",
            12,
            0xFF8B6656,
            false));
    Ui.gap(box, 16);
    Ui.add(box, Ui.button(this, "Abrir mi cancha  ↗", Ui.WHITE, Ui.INK, () -> showPage(3)));
    Ui.add(b, box);
  }

  void routineRow(LinearLayout b, Catalog.Routine r) {
    LinearLayout row = Ui.row(this);
    Ui.pad(row, 14, 17, 15, 17);
    row.setBackground(Ui.shape(this, Ui.WHITE, 20));
    FrameLayout mark = new FrameLayout(this);
    int color =
        r.category.equals("basket")
            ? Ui.BLUE
            : r.category.equals("mobility") ? 0xFF7858CB : 0xFF173D47;
    mark.setBackground(Ui.shape(this, color, 17));
    mark.addView(
        new Ui.Icon(this, r.category.equals("basket") ? "court" : "train", Ui.WHITE),
        new FrameLayout.LayoutParams(Ui.dp(this, 29), Ui.dp(this, 29), Gravity.CENTER));
    row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 78)));
    LinearLayout text = Ui.col(this);
    Ui.pad(text, 14, 0, 8, 0);
    Ui.add(text, Ui.label(this, Catalog.category(r.category), Ui.BLUE));
    Ui.gap(text, 8);
    Ui.add(text, Ui.text(this, r.name, 16, Ui.INK, true));
    Ui.gap(text, 7);
    Ui.add(
        text,
        Ui.text(
            this,
            ((catalog.seconds(r, r.rounds) + 59) / 60)
                + " min   ·   "
                + r.ids.size()
                + " movimientos",
            11,
            Ui.MUTED,
            false));
    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    row.addView(
        new Ui.Icon(this, "arrow", Ui.BLUE),
        new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
    row.setOnClickListener(v -> routineDetails(r));
    row.setFocusable(true);
    row.setContentDescription(r.name);
    Ui.add(b, row);
    Ui.gap(b, 12);
  }

  void training(LinearLayout b) {
    top(
        b,
        "ENTRENA  /  TU RITMO",
        "Muévete\na tu manera.",
        "12 rutinas, 24 movimientos y todo el espacio para crear los tuyos.");
    Ui.add(b, Ui.button(this, "＋  Crear mi propia rutina", Ui.BLUE, Ui.WHITE, this::createRoutine));
    Ui.gap(b, 20);
    HorizontalScrollView hsv = new HorizontalScrollView(this);
    hsv.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = Ui.row(this);
    String[] ids = {"all", "basket", "strength", "mobility", "custom"},
        labels = {"Todo", "Baloncesto", "Fuerza", "Movilidad", "Mías"};
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      Button chip =
          Ui.button(
              this,
              labels[i],
              filter.equals(id) ? Ui.INK : Ui.WHITE,
              filter.equals(id) ? Ui.WHITE : Ui.MUTED,
              () -> {
                filter = id;
                showPage(1);
              });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, Ui.dp(this, 44));
      p.rightMargin = Ui.dp(this, 8);
      chips.addView(chip, p);
    }
    hsv.addView(chips);
    Ui.add(b, hsv);
    Ui.gap(b, 20);
    List<Catalog.Routine> all = new ArrayList<>(catalog.routines);
    all.addAll(store.custom(catalog));
    int found = 0;
    for (Catalog.Routine r : all)
      if (filter.equals("all") || r.category.equals(filter)) {
        routineRow(b, r);
        found++;
      }
    if (found == 0)
      empty(
          b,
          "Aquí empieza tu propia rutina.",
          "Combina movimientos y elige el tiempo de cada intervalo.");
    Ui.gap(b, 25);
    heading(b, "Explora movimientos", "", () -> {});
    EditText search = Ui.edit(this, "Buscar un movimiento");
    Ui.add(b, search);
    Ui.gap(b, 15);
    LinearLayout list = Ui.col(this);
    Ui.add(b, list);
    exercises(list, "");
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int before, int count) {
            exercises(list, s.toString());
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  static String normal(String s) {
    return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  void exercises(LinearLayout list, String search) {
    list.removeAllViews();
    for (Catalog.Exercise e : catalog.exercises.values())
      if (normal(e.name).contains(normal(search))) {
        LinearLayout row = Ui.col(this);
        Ui.pad(row, 0, 14, 0, 14);
        Ui.add(row, Ui.text(this, e.name, 15, Ui.INK, true));
        Ui.gap(row, 6);
        Ui.add(
            row,
            Ui.text(
                this,
                Catalog.category(e.category)
                    + (e.mode.isEmpty() ? "  ·  Por tiempo" : "  ·  Visión disponible"),
                10,
                Ui.MUTED,
                false));
        row.setOnClickListener(v -> exerciseDetails(e));
        row.setFocusable(true);
        Ui.add(list, row);
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        list.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
      }
  }

  void exerciseDetails(Catalog.Exercise e) {
    LinearLayout body = Ui.col(this);
    body.addView(new ExerciseDemo(this, e.id), new LinearLayout.LayoutParams(-1, Ui.dp(this, 205)));
    Ui.gap(body, 12);
    Ui.add(body, Ui.text(this, ExerciseDemo.guide(e.id), 14, Ui.INK, false));
    Ui.add(body, Ui.label(this, Catalog.category(e.category), Ui.BLUE));
    Ui.gap(body, 18);
    Ui.add(body, Ui.text(this, e.cue, 16, Ui.INK, false));
    Ui.gap(body, 20);
    Ui.add(
        body,
        Ui.text(
            this,
            "Toma pausas cuando lo necesites. Si aparece dolor o mareo, detén el ejercicio.",
            12,
            Ui.MUTED,
            false));
    Ui.gap(body, 25);
    Dialog[] d = new Dialog[1];
    if (!e.mode.isEmpty()) {
      Ui.add(
          body,
          Ui.button(
              this,
              "Analizar en Visión",
              Ui.BLUE,
              Ui.WHITE,
              () -> {
                d[0].dismiss();
                vision(e.mode);
              }));
      Ui.gap(body, 12);
    }
    Ui.add(
        body,
        Ui.button(
            this,
            "Entrenar " + e.seconds + " segundos",
            Ui.WHITE,
            Ui.INK,
            () -> {
              d[0].dismiss();
              Catalog.Routine r = new Catalog.Routine();
              r.id = "single";
              r.name = e.name;
              r.category = e.category;
              r.description = e.cue;
              r.rounds = 1;
              r.rest = 0;
              r.ids.add(e.id);
              r.times.add(e.seconds);
              startWorkout(r, 1);
            }));
    d[0] = Ui.sheet(this, e.name, body);
  }

  void routineDetails(Catalog.Routine r) {
    LinearLayout body = Ui.col(this);
    Ui.add(body, Ui.label(this, Catalog.category(r.category), Ui.BLUE));
    Ui.gap(body, 14);
    Ui.add(body, Ui.text(this, r.description, 14, Ui.MUTED, false));
    Ui.gap(body, 22);
    for (int i = 0; i < r.ids.size(); i++) {
      LinearLayout row = Ui.row(this);
      row.addView(
          Ui.text(
              this, (i + 1) + ". " + catalog.exercises.get(r.ids.get(i)).name, 14, Ui.INK, false),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(Ui.text(this, r.times.get(i) + " s", 13, Ui.BLUE, true));
      Ui.add(body, row);
      Ui.gap(body, 17);
    }
    Ui.add(body, Ui.label(this, "VUELTAS · AJUSTA A TU ENERGÍA", Ui.MUTED));
    Ui.gap(body, 12);
    String[] options = new String[5];
    for (int i = 0; i < 5; i++)
      options[i] =
          (i + 1) + " vueltas  ·  " + ((catalog.seconds(r, i + 1) + 59) / 60) + " min aprox.";
    Spinner rounds = Ui.spinner(this, options, r.rounds - 1);
    Ui.add(body, rounds);
    Ui.gap(body, 18);
    Ui.add(
        body,
        Ui.text(
            this,
            "Descanso: "
                + r.rest
                + " s entre movimientos. Esta rutina usa tiempo; el análisis con cámara se abre"
                + " desde Visión.",
            12,
            Ui.MUTED,
            false));
    Ui.gap(body, 25);
    Dialog[] d = new Dialog[1];
    Ui.add(
        body,
        Ui.button(
            this,
            "Empezar entrenamiento  →",
            Ui.BLUE,
            Ui.WHITE,
            () -> {
              d[0].dismiss();
              startWorkout(r, rounds.getSelectedItemPosition() + 1);
            }));
    if (r.category.equals("custom")) {
      Ui.gap(body, 14);
      Ui.add(
          body,
          Ui.button(
              this,
              "Eliminar rutina",
              Ui.WHITE,
              Ui.MUTED,
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("¿Eliminar esta rutina?")
                      .setPositiveButton(
                          "Eliminar",
                          (dialog, which) -> {
                            store.deleteRoutine(r.id);
                            d[0].dismiss();
                            showPage(1);
                          })
                      .setNegativeButton("Conservar", null)
                      .show()));
    }
    d[0] = Ui.sheet(this, r.name, body);
  }

  void startWorkout(Catalog.Routine r, int rounds) {
    if (workout != null && workout.isShowing()) return;
    workout = new Workout(this, r, rounds);
    workout.show();
  }

  void restoreWorkout() {
    try {
      JSONObject j = new JSONObject(store.prefs.getString("draft", ""));
      Catalog.Routine r = catalog.parse(j.getJSONObject("routine"));
      workout = new Workout(this, r, Math.max(1, Math.min(5, j.getInt("rounds"))));
      workout.restore(j);
      workout.show();
    } catch (Exception e) {
      store.prefs.edit().remove("draft").apply();
      Ui.toast(this, "No se pudo recuperar esa sesión.");
      showPage(0);
    }
  }

  void createRoutine() {
    LinearLayout body = Ui.col(this);
    EditText name = Ui.edit(this, "Nombre de tu rutina");
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(60)});
    Ui.add(body, name);
    Ui.gap(body, 18);
    Ui.add(
        body,
        Ui.text(
            this, "Elige hasta 12 movimientos y la duración en segundos.", 13, Ui.MUTED, false));
    Ui.gap(body, 17);
    LinearLayout rows = Ui.col(this);
    Ui.add(body, rows);
    List<String> ids = new ArrayList<>(catalog.exercises.keySet());
    String[] names = ids.stream().map(id -> catalog.exercises.get(id).name).toArray(String[]::new);
    Runnable add =
        () -> {
          if (rows.getChildCount() >= 12) {
            Ui.toast(this, "Máximo: 12 movimientos.");
            return;
          }
          LinearLayout row = Ui.row(this);
          Spinner ex = Ui.spinner(this, names, 0);
          row.addView(ex, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
          EditText seconds = Ui.edit(this, "s");
          seconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
          seconds.setText("30");
          LinearLayout.LayoutParams p =
              new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 52));
          p.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
          row.addView(seconds, p);
          row.addView(
              Ui.iconButton(
                  this,
                  "close",
                  "Quitar movimiento",
                  Ui.WHITE,
                  Ui.MUTED,
                  () -> rows.removeView(row)),
              new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 44)));
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
          lp.bottomMargin = Ui.dp(this, 10);
          rows.addView(row, lp);
        };
    add.run();
    add.run();
    Ui.add(body, Ui.button(this, "＋ Añadir movimiento", Ui.WHITE, Ui.BLUE, add));
    Ui.gap(body, 20);
    Spinner rounds =
        Ui.spinner(
            this, new String[] {"1 vuelta", "2 vueltas", "3 vueltas", "4 vueltas", "5 vueltas"}, 1);
    Ui.add(body, rounds);
    Ui.gap(body, 13);
    EditText rest = Ui.edit(this, "Descanso entre ejercicios (0–180 s)");
    rest.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    rest.setText("20");
    Ui.add(body, rest);
    Ui.gap(body, 24);
    Dialog[] d = new Dialog[1];
    Ui.add(
        body,
        Ui.button(
            this,
            "Guardar mi rutina",
            Ui.BLUE,
            Ui.WHITE,
            () -> {
              try {
                JSONArray steps = new JSONArray();
                for (int i = 0; i < rows.getChildCount(); i++) {
                  LinearLayout row = (LinearLayout) rows.getChildAt(i);
                  steps.put(
                      new JSONArray()
                          .put(ids.get(((Spinner) row.getChildAt(0)).getSelectedItemPosition()))
                          .put(
                              Integer.parseInt(
                                  ((EditText) row.getChildAt(1)).getText().toString())));
                }
                JSONObject j =
                    new JSONObject()
                        .put("id", UUID.randomUUID().toString())
                        .put("name", name.getText().toString())
                        .put("category", "custom")
                        .put("rounds", rounds.getSelectedItemPosition() + 1)
                        .put("rest", Integer.parseInt(rest.getText().toString()))
                        .put("steps", steps);
                store.saveRoutine(catalog.parse(j));
                d[0].dismiss();
                filter = "custom";
                showPage(1);
              } catch (Exception e) {
                Ui.toast(this, "Revisa nombre, segundos (5–600) y descanso (0–180).");
              }
            }));
    d[0] = Ui.sheet(this, "Hecha por ti.", body);
  }

  static AdapterView.OnItemSelectedListener selected(java.util.function.IntConsumer run) {
    return new AdapterView.OnItemSelectedListener() {
      public void onNothingSelected(AdapterView<?> p) {}

      public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
        run.accept(i);
      }
    };
  }

  void court(LinearLayout b) {
    Ui.add(
        b,
        Ui.button(
            this,
            "Cámara de partido · balón y jugadores",
            Ui.BLUE,
            Ui.WHITE,
            () -> startActivity(new Intent(this, MatchActivity.class))));
    Ui.gap(b, 14);
    top(
        b,
        "MI CANCHA  /  SHOT TRACKER",
        "Cada tiro cuenta.",
        "Registro manual. Elige una zona y anota el resultado.");
    Spinner periods =
        Ui.spinner(this, new String[] {"Hoy", "Últimos 7 días", "Todo el historial"}, period);
    Ui.add(b, periods);
    Ui.gap(b, 15);
    Art.Court court = new Art.Court(this);
    b.addView(court, new LinearLayout.LayoutParams(-1, Ui.dp(this, 250)));
    Ui.gap(b, 12);
    Spinner zones = Ui.spinner(this, Store.ZONES, zone);
    Ui.add(b, zones);
    Ui.gap(b, 12);
    LinearLayout values = Ui.row(this);
    TextView accuracy = Ui.title(this, "—", 58);
    accuracy.setTextColor(Ui.BLUE);
    values.addView(accuracy, new LinearLayout.LayoutParams(0, -2, 1));
    TextView detail = Ui.text(this, "", 12, Ui.MUTED, false);
    values.addView(detail);
    Ui.add(b, values);
    Ui.gap(b, 12);
    LinearLayout actions = Ui.row(this);
    Button made = Ui.button(this, "Encesté  +1", Ui.BLUE, Ui.WHITE, () -> {}),
        miss = Ui.button(this, "Fallé", Ui.WHITE, Ui.INK, () -> {});
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.rightMargin = Ui.dp(this, 10);
    actions.addView(made, p);
    actions.addView(miss, new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(b, actions);
    Ui.gap(b, 12);
    Button undo = Ui.button(this, "↶  Deshacer último registro", Ui.BG, Ui.MUTED, () -> {});
    Ui.add(b, undo);
    Ui.gap(b, 12);
    TextView totals = Ui.text(this, "", 12, Ui.MUTED, false);
    Ui.add(b, totals);
    Runnable update =
        () -> {
          long from =
              period == 0
                  ? Store.today()
                  : period == 1
                      ? LocalDate.now()
                          .minusDays(6)
                          .atStartOfDay(ZoneId.systemDefault())
                          .toInstant()
                          .toEpochMilli()
                      : 0;
          List<Store.Shot> all = store.shots(from);
          court.update(all, zone);
          int attempts = 0, yes = 0, allMade = 0;
          for (Store.Shot s : all) {
            if (s.made) allMade++;
            if (s.zone == zone) {
              attempts++;
              if (s.made) yes++;
            }
          }
          accuracy.setText(attempts == 0 ? "—" : Math.round(yes * 100f / attempts) + "%");
          detail.setText(yes + " encestes\n" + attempts + " intentos en esta zona");
          totals.setText(
              "TOTAL DEL PERIODO  ·  "
                  + allMade
                  + " / "
                  + all.size()
                  + "  ·  "
                  + (all.isEmpty()
                      ? "Sin intentos"
                      : Math.round(allMade * 100f / all.size()) + "%"));
          undo.setEnabled(lastShot != null);
        };
    court.action =
        i -> {
          zone = i;
          zones.setSelection(i);
          update.run();
        };
    zones.setOnItemSelectedListener(
        selected(
            i -> {
              zone = i;
              update.run();
            }));
    periods.setOnItemSelectedListener(
        selected(
            i -> {
              period = i;
              update.run();
            }));
    made.setOnClickListener(
        v -> {
          lastShot = store.addShot(zone, true);
          update.run();
        });
    miss.setOnClickListener(
        v -> {
          lastShot = store.addShot(zone, false);
          update.run();
        });
    undo.setOnClickListener(
        v -> {
          if (lastShot != null) {
            store.undoShot(lastShot);
            lastShot = null;
            update.run();
          }
        });
    update.run();
  }

  void progress(LinearLayout b) {
    top(
        b,
        "PROGRESO  /  TU HISTORIA",
        "La constancia\nse ve.",
        "Tu esfuerzo, sin comparaciones. Guardado en tu dispositivo.");
    List<Store.Session> all = store.sessions();
    int seconds = 0, reps = 0;
    for (Store.Session s : all) {
      seconds += s.seconds;
      reps += s.reps;
    }
    LinearLayout metrics = Ui.row(this);
    metrics.addView(metric("" + all.size(), "SESIONES"), new LinearLayout.LayoutParams(0, -2, 1));
    metrics.addView(
        metric("" + Math.round(seconds / 60f), "MINUTOS"), new LinearLayout.LayoutParams(0, -2, 1));
    metrics.addView(metric("" + reps, "MOVIMIENTOS"), new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(b, metrics);
    Ui.gap(b, 17);
    LinearLayout rec=Ui.card(this);
    Ui.add(rec,Ui.label(this,"TIROS Y FIT · DATOS REALES",Ui.BLUE));
    Ui.gap(rec,12);
    List<Store.Shot> localShots=store.shots(0);
    long hits=localShots.stream().filter(x->x.made).count();
    Ui.add(rec,Ui.text(this,"Registro manual: "+hits+" / "+localShots.size()+" encestes"+
        (localShots.isEmpty()?"":String.format(Locale.forLanguageTag("es-DO")," (%.1f%%)",100.0*hits/localShots.size())),
        14,Ui.INK,false));
    int cameraMade=0,cameraMiss=0,pending=0;
    try{
      JSONArray raw=new JSONArray(store.prefs.getString("matchShots","[]"));
      for(int i=0;i<raw.length();i++){
        String outcome=raw.getJSONObject(i).optString("result","");
        if(outcome.equals("Enceste confirmado"))cameraMade++;
        else if(outcome.equals("Fallo confirmado"))cameraMiss++;
        else pending++;
      }
    }catch(JSONException ignored){}
    Ui.gap(rec,8);
    Ui.add(rec,Ui.text(this,"Cámara revisada: "+cameraMade+" / "+(cameraMade+cameraMiss)+
        " encestes · "+pending+" pendientes (sin mezclar predicciones)",14,Ui.INK,false));
    long[] fit=store.fitDay(LocalDate.now().toString());
    Ui.gap(rec,8);
    Ui.add(rec,Ui.text(this,"FIT hoy: "+fit[0]+" pasos registrados, "+fit[1]+" ml anotados",14,Ui.INK,false));
    Ui.gap(rec,12);
    Ui.add(rec,Ui.button(this,"Abrir revisión de cancha",Ui.PALE,Ui.BLUE,
        ()->startActivity(new Intent(this,MatchActivity.class))));
    Ui.add(b,rec);
    Ui.gap(b, 15);
    Ui.add(b, Ui.button(this, "Administrar datos · restablecer estadísticas", Ui.WHITE, Ui.INK, this::resetDialog));
    Ui.gap(b, 22);
    LinearLayout chart = Ui.card(this);
    Ui.add(chart, Ui.label(this, "TU SEMANA EN MINUTOS", Ui.BLUE));
    Ui.gap(chart, 17);
    chart.addView(new Art.Chart(this, all), new LinearLayout.LayoutParams(-1, Ui.dp(this, 186)));
    Ui.add(b, chart);
    Ui.gap(b, 18);
    long monday =
        LocalDate.now()
            .with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    int weekly = 0;
    for (Store.Session s : all) if (s.date >= monday) weekly++;
    int goal = store.prefs.getInt("goal", 3);
    LinearLayout target = Ui.card(this);
    Ui.add(target, Ui.label(this, "A TU PROPIO RITMO", Ui.MUTED));
    Ui.gap(target, 12);
    Ui.add(target, Ui.title(this, weekly + " de " + goal + " sesiones", 28));
    Ui.gap(target, 14);
    ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(goal);
    bar.setProgress(Math.min(goal, weekly));
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
    target.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 5)));
    Ui.gap(target, 12);
    Ui.add(
        target, Ui.text(this, "Objetivo semanal ajustable en Preferencias.", 11, Ui.MUTED, false));
    Ui.add(b, target);
    Ui.gap(b, 26);
    heading(b, "Tu historial", "Exportar ↗", () -> exportFile(false));
    if (all.isEmpty())
      empty(
          b,
          "El primer paso cuenta.",
          "Completa una rutina o guarda una serie de Visión para ver tu progreso.");
    for (Store.Session s : all.subList(0, Math.min(60, all.size()))) {
      LinearLayout row = Ui.col(this);
      Ui.pad(row, 0, 17, 0, 17);
      Ui.add(row, Ui.text(this, s.name, 16, Ui.INK, true));
      Ui.gap(row, 8);
      String origin =
          s.source.equals("routine")
              ? "Rutina guiada"
              : s.source.equals("video") ? "Análisis de clip" : "Cámara";
      String measures =
          (s.mode.equals("plank") || s.mode.equals("stance"))
              ? Math.round(s.hold) + " s en postura"
              : s.reps + " movimientos";
      String date =
          DateTimeFormatter.ofPattern("d MMM · HH:mm", new Locale("es"))
              .format(Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()));
      Ui.add(
          row,
          Ui.text(
              this,
              date
                  + "   ·   "
                  + Ui.time(s.seconds)
                  + "\n"
                  + origin
                  + (s.source.equals("routine") ? "" : "   ·   " + measures),
              11,
              Ui.MUTED,
              false));
      row.setOnClickListener(v -> sessionDetails(s));
      row.setFocusable(true);
      Ui.add(b, row);
      View line = new View(this);
      line.setBackgroundColor(Ui.LINE);
      b.addView(line, new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)));
    }
  }

  void sessionDetails(Store.Session s) {
    LinearLayout body = Ui.col(this);
    Ui.add(
        body,
        Ui.text(
            this,
            "Tiempo: "
                + Ui.time(s.seconds)
                + "\nMovimientos: "
                + s.reps
                + (s.hold > 0 ? "\nTiempo en postura: " + Math.round(s.hold) + " s" : ""),
            15,
            Ui.MUTED,
            false));
    Ui.gap(body, 22);
    Ui.add(body, Ui.label(this, "CÓMO LO SENTISTE", Ui.BLUE));
    Ui.gap(body, 12);
    Spinner effort =
        Ui.spinner(
            this,
            new String[] {
              "Sin valorar", "Muy suave", "Cómodo", "Moderado", "Intenso", "Muy intenso"
            },
            s.effort);
    Ui.add(body, effort);
    Ui.gap(body, 15);
    EditText note = Ui.edit(this, "Tu nota de esta sesión");
    note.setSingleLine(false);
    note.setMinLines(3);
    note.setText(s.note);
    note.setFilters(new InputFilter[] {new InputFilter.LengthFilter(500)});
    Ui.add(body, note);
    Ui.gap(body, 22);
    Dialog[] d = new Dialog[1];
    Ui.add(
        body,
        Ui.button(
            this,
            "Guardar nota",
            Ui.BLUE,
            Ui.WHITE,
            () -> {
              store.annotate(s.id, note.getText().toString(), effort.getSelectedItemPosition());
              d[0].dismiss();
              showPage(4);
            }));
    Ui.gap(body, 12);
    Ui.add(
        body,
        Ui.button(
            this,
            "Eliminar del historial",
            Ui.WHITE,
            Ui.MUTED,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("¿Eliminar esta sesión?")
                    .setPositiveButton(
                        "Eliminar",
                        (dialog, which) -> {
                          store.deleteSession(s.id);
                          d[0].dismiss();
                          showPage(4);
                        })
                    .setNegativeButton("Conservar", null)
                    .show()));
    d[0] = Ui.sheet(this, s.name, body);
  }

  void empty(LinearLayout b, String title, String sub) {
    LinearLayout c = Ui.card(this);
    Ui.add(c, Ui.title(this, title, 24));
    Ui.gap(c, 12);
    Ui.add(c, Ui.text(this, sub, 13, Ui.MUTED, false));
    Ui.add(b, c);
  }

  void settings(LinearLayout b) {
    Ui.add(
        b,
        Ui.button(
            this,
            "Mi cuenta y mis grupos",
            Ui.BLUE,
            Ui.WHITE,
            () -> startActivity(new Intent(this, SocialActivity.class))));
    Ui.gap(b, 14);
    top(
        b,
        "PERFIL  /  PREFERENCIAS",
        "Tu versión.\nTus reglas.",
        "Personaliza el entrenamiento y conserva tus datos.");
    Ui.add(b, Ui.label(this, "CÓMO TE LLAMAS", Ui.MUTED));
    Ui.gap(b, 10);
    EditText name = Ui.edit(this, "Atleta");
    name.setText(store.prefs.getString("name", ""));
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(35)});
    Ui.add(b, name);
    Ui.gap(b, 20);
    Ui.add(b, Ui.label(this, "TU EXPERIENCIA", Ui.MUTED));
    Ui.gap(b, 10);
    Spinner level =
        Ui.spinner(
            this,
            new String[] {"Estoy empezando", "Ya entreno con frecuencia", "Tengo experiencia"},
            store.prefs.getInt("level", 0));
    Ui.add(b, level);
    Ui.gap(b, 20);
    Ui.add(b, Ui.label(this, "TU ENFOQUE", Ui.MUTED));
    Ui.gap(b, 10);
    String[] f = {"basket", "strength", "mobility"};
    Spinner focus =
        Ui.spinner(
            this,
            new String[] {"Baloncesto", "Condición física", "Movilidad y constancia"},
            Arrays.asList(f).indexOf(store.prefs.getString("focus", "basket")));
    Ui.add(b, focus);
    Ui.gap(b, 20);
    Ui.add(b, Ui.label(this, "SESIONES POR SEMANA", Ui.MUTED));
    Ui.gap(b, 10);
    Spinner goal =
        Ui.spinner(
            this,
            new String[] {
              "1 sesión",
              "2 sesiones",
              "3 sesiones",
              "4 sesiones",
              "5 sesiones",
              "6 sesiones",
              "7 sesiones"
            },
            store.prefs.getInt("goal", 3) - 1);
    Ui.add(b, goal);
    Ui.gap(b, 20);
    Ui.add(b, Ui.label(this, "RENDIMIENTO DE VISIÓN", Ui.MUTED));
    Ui.gap(b, 10);
    Spinner quality =
        Ui.spinner(
            this,
            new String[] {
              "Automático · frecuencia adaptable",
              "Ahorro · 640 × 480",
              "Objetivo 60 FPS · según cámara",
              "Objetivo 120 FPS · si es compatible"
            },
            Math.max(0,Math.min(3,store.prefs.getInt("quality", 0))));
    Ui.add(b, quality);
    Ui.gap(b, 18);
    Switch voice = new Switch(this);
    voice.setText("Conteo por voz");
    voice.setTextColor(Ui.INK);
    voice.setChecked(store.prefs.getBoolean("voice", false));
    Ui.pad(voice, 0, 12, 0, 12);
    Ui.add(b, voice);
    Switch sound = new Switch(this);
    sound.setText("Sonidos de intervalos");
    sound.setTextColor(Ui.INK);
    sound.setChecked(store.prefs.getBoolean("sound", true));
    Ui.pad(sound, 0, 12, 0, 12);
    Ui.add(b, sound);
    Ui.gap(b, 15);
    Ui.add(
        b,
        Ui.button(
            this,
            "Guardar preferencias",
            Ui.BLUE,
            Ui.WHITE,
            () -> {
              store
                  .prefs
                  .edit()
                  .putString("name", name.getText().toString().trim())
                  .putInt("level", level.getSelectedItemPosition())
                  .putString("focus", f[focus.getSelectedItemPosition()])
                  .putInt("goal", goal.getSelectedItemPosition() + 1)
                  .putInt("quality", quality.getSelectedItemPosition())
                  .putBoolean("voice", voice.isChecked())
                  .putBoolean("sound", sound.isChecked())
                  .apply();
              Ui.toast(this, "Tu espacio está actualizado.");
            }));
    Ui.gap(b, 31);
    heading(b, "Tus datos, contigo.", "", () -> {});
    Ui.add(
        b,
        Ui.text(
            this,
            "La postura se analiza en el teléfono. El modelo está incluido y la aplicación no tiene"
                + " permiso de internet. Haz una copia para trasladar tu historial y tus rutinas.",
            13,
            Ui.MUTED,
            false));
    Ui.gap(b, 18);
    Ui.add(b, Ui.button(this, "Guardar copia JSON", Ui.WHITE, Ui.BLUE, () -> exportFile(true)));
    Ui.gap(b, 10);
    Ui.add(
        b,
        Ui.button(
            this,
            "Importar una copia",
            Ui.WHITE,
            Ui.INK,
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    92)));
    Ui.gap(b, 10);
    Ui.add(b, Ui.button(this, "Exportar CSV", Ui.WHITE, Ui.INK, () -> exportFile(false)));
    Ui.gap(b, 30);
    heading(b, "Hecha para entrenar.", "", () -> {});
    Ui.add(
        b,
        Ui.text(
            this,
            "PanaHoops FIT · código 3.1.1 · Android nativo\n"
                + "24 ejercicios · 12 rutinas · 8 modos de postura\n\n"
                + "Visión actualiza ángulos 2D, conteo y fase del ejercicio mientras te mueves. La"
                + " luz, la perspectiva y las articulaciones fuera de cuadro afectan al resultado."
                + " La visibilidad de puntos no es una nota de técnica ni un porcentaje de"
                + " precisión.\n\n"
                + "La cámara de cancha usa detección genérica de balones y personas y calibración manual del aro. "
                + "Los tiros probables exigen revisión; no son arbitraje automático. "
                + "Las indicaciones de postura son referencias generales, no diagnóstico.\n\n"
                + "Tecnología: CameraX y ML Kit de Google. Modelo local incluido. Dependencias bajo"
                + " sus licencias de Google y Apache 2.0.\n\n"
                + "Los datos FIT proceden del sensor cuando existe; pulso manual no es pulso en vivo. "
                + "Los servicios en línea requieren Firebase configurado. No es una publicación en Google Play.",
            12,
            Ui.MUTED,
            false));
  }

  void fit(LinearLayout b) {
    top(b, "PANAHOOPS  /  FIT", "Tu actividad.",
        "Pasos desde un sensor del teléfono. No se generan mediciones de demostración.");
    Ui.add(b, Ui.label(this, "PASOS REGISTRADOS HOY", Ui.MUTED));
    fitStepValue = Ui.title(this, "0", 38);
    Ui.add(b, fitStepValue);
    Ui.gap(b, 7);
    fitStatusValue = Ui.text(this, "Comprobando sensor…", 12, Ui.MUTED, false);
    Ui.add(b, fitStatusValue);
    Ui.gap(b, 15);
    LinearLayout controls = Ui.row(this);
    controls.addView(Ui.button(this,"Iniciar seguimiento",Ui.BLUE,Ui.WHITE,this::startFit),
        new LinearLayout.LayoutParams(0,-2,1));
    controls.addView(Ui.button(this,"Detener",Ui.WHITE,Ui.INK,this::stopFit),
        new LinearLayout.LayoutParams(0,-2,1));
    Ui.add(b, controls);
    Ui.gap(b, 14);
    Ui.add(b, Ui.text(this,
        "Mientras esté activo aparece una notificación con la opción Detener. " +
        "Los pasos contabilizados empiezan en la primera lectura del día; el sensor no permite reconstruir " +
        "pasos anteriores ni reiniciarse físicamente.",12,Ui.MUTED,false));
    Ui.gap(b, 24);
    LinearLayout activity = Ui.card(this);
    Ui.add(activity,Ui.label(this,"METAS Y DISTANCIA",Ui.BLUE));
    Ui.gap(activity, 10);
    EditText goal = Ui.edit(this,"Meta de pasos");
    goal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    goal.setText(""+store.prefs.getInt("fitGoal",6000));
    Ui.add(activity,goal);
    Ui.gap(activity, 10);
    Ui.add(activity,Ui.button(this,"Guardar meta",Ui.PALE,Ui.BLUE,()->{
      try {int n=Integer.parseInt(goal.getText().toString());if(n<100||n>100000)throw new Exception();
        store.prefs.edit().putInt("fitGoal",n).apply();Ui.toast(this,"Meta guardada");updateFitValues();}
      catch(Exception ex){goal.setError("Introduce entre 100 y 100 000 pasos");}
    }));
    Ui.gap(activity, 10);
    Ui.add(activity,Ui.label(this,"LONGITUD DE ZANCADA ESTIMADA (CM)",Ui.MUTED));
    Ui.gap(activity,7);
    EditText stride = Ui.edit(this,"Longitud de zancada en centímetros");
    stride.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    stride.setText(""+store.prefs.getInt("strideCm",70));
    stride.setContentDescription("Longitud de zancada estimada en centímetros, de 40 a 150");
    Ui.add(activity,stride);
    Ui.gap(activity,8);
    Ui.add(activity,Ui.button(this,"Guardar zancada",Ui.PALE,Ui.BLUE,()->{
      try {int cm=Integer.parseInt(stride.getText().toString());
        if(cm<40||cm>150)throw new IllegalArgumentException();
        store.prefs.edit().putInt("strideCm",cm).apply();
        Ui.toast(this,"Zancada actualizada · distancia estimada recalculada");updateFitValues();
      }catch(Exception ex){stride.setError("Introduce de 40 a 150 cm");}
    }));
    Ui.gap(activity,10);
    Ui.add(activity,Ui.text(this,"Distancia aproximada = pasos × longitud de zancada indicada. " +
        "La distancia no se mide con GPS; cambia el ajuste para mejorar tu estimación.",12,Ui.MUTED,false));
    Ui.add(b,activity);
    Ui.gap(b,18);
    LinearLayout water = Ui.card(this);
    Ui.add(water,Ui.label(this,"HIDRATACIÓN MANUAL",Ui.BLUE));
    fitWaterValue = Ui.text(this,"0 ml",24,Ui.INK,true);
    Ui.add(water,fitWaterValue);
    Ui.gap(water,12);
    Ui.add(water,Ui.button(this,"Añadir vaso · 250 ml",Ui.BLUE,Ui.WHITE,()->{
      store.addWater(LocalDate.now().toString(),250);updateFitValues();
    }));
    Ui.add(b,water);
    Ui.gap(b,18);
    LinearLayout heart=Ui.card(this);
    Ui.add(heart,Ui.label(this,"PULSO · REGISTRO MANUAL",Ui.BLUE));
    long at=store.prefs.getLong("manualHeartAt",0);
    int bpm=store.prefs.getInt("manualHeart",0);
    Ui.gap(heart,10);
    Ui.add(heart,Ui.text(this,at==0?"Sin mediciones de pulso":
        (bpm+" lpm · registrado manualmente · "+
        DateTimeFormatter.ofPattern("dd/MM HH:mm").format(
          Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))),13,Ui.INK,false));
    Ui.gap(heart,12);
    Ui.add(heart,Ui.button(this,"Añadir lectura manual",Ui.PALE,Ui.BLUE,this::enterHeart));
    Ui.gap(heart,12);
    Ui.add(heart,Ui.text(this,"Banda Bluetooth y Health Connect: no conectados en esta edición. " +
        "Nunca se presenta una anotación manual como lectura en vivo.",12,Ui.MUTED,false));
    Ui.add(b,heart);
    Ui.gap(b, 20);
    Ui.add(b,Ui.button(this,"Guardar copia de actividad JSON",Ui.WHITE,Ui.INK,()->exportFile(true)));
    updateFitValues();
  }

  void updateFitValues(){
    if (fitStepValue == null || page != 3) return;
    long[] today=store.fitDay(LocalDate.now().toString());
    fitStepValue.setText(java.text.NumberFormat.getIntegerInstance(new Locale("es","DO")).format(today[0])+
        " / "+store.prefs.getInt("fitGoal",6000));
    if(fitWaterValue!=null)fitWaterValue.setText(today[1]+" ml");
    android.content.SharedPreferences sensor=getSharedPreferences("fit.sensor",MODE_PRIVATE);
    android.hardware.SensorManager m=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
    boolean capable=m!=null&&m.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)!=null;
    String status= !capable ? "Sin sensor de pasos compatible · lectura no disponible" :
        sensor.getString("status","Pulsa iniciar para registrar desde el sensor");
    int strideCm=Math.max(40,Math.min(150,store.prefs.getInt("strideCm",70)));
    double meters=FitDistance.meters(today[0],strideCm);
    long latest=sensor.getLong("sampleTime",0);
    String pace=(latest>0&&System.currentTimeMillis()-latest<120000 && sensor.getInt("cadence",0)>0)
        ? sensor.getInt("cadence",0)+" pasos/min (última muestra)":"Sin cadencia reciente";
    long started=sensor.getLong("sessionStarted",0);
    String session=sensor.getBoolean("active",false)&&started>0 ?
        (" · sesión: "+sensor.getLong("sessionSteps",0)+" pasos, "+((System.currentTimeMillis()-started)/60000)+" min") : "";
    fitStatusValue.setText(status+session+"\nDistancia estimada: "+
        String.format(Locale.forLanguageTag("es-DO"),"%.2f km",meters/1000.0)+" · "+pace);
  }

  void startFit(){
    android.hardware.SensorManager m=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
    if(m==null || m.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)==null){
      Ui.toast(this,"Este teléfono no ofrece un contador de pasos compatible.");return;
    }
    if(android.os.Build.VERSION.SDK_INT>=29&&
        checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{android.Manifest.permission.ACTIVITY_RECOGNITION},511);return;
    }
    try{androidx.core.content.ContextCompat.startForegroundService(this,new Intent(this,FitService.class));
      Ui.toast(this,"Solicitado seguimiento con notificación visible.");}
    catch(Exception ex){Ui.toast(this,"No se pudo iniciar. Abre FIT con la aplicación visible.");}
  }
  void stopFit(){
    stopService(new Intent(this,FitService.class));
    Ui.toast(this,"Seguimiento detenido");
    fitHandler.postDelayed(this::updateFitValues,500);
  }
  @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){
    super.onRequestPermissionsResult(request,perms,grants);
    if(request==511&&grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) startFit();
    else if(request==511)Ui.toast(this,"Sin permiso no se pueden registrar pasos en segundo plano.");
  }
  void enterHeart(){
    EditText field=Ui.edit(this,"Pulsaciones por minuto (lpm)");
    field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Anotación de pulso, NO en vivo")
      .setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
    dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
      try{int bpm=Integer.parseInt(field.getText().toString());
        if(bpm<30||bpm>250){field.setError("Indica entre 30 y 250 lpm");return;}
        store.prefs.edit().putInt("manualHeart",bpm)
          .putLong("manualHeartAt",System.currentTimeMillis()).apply();
        dialog.dismiss();showPage(3);
      }catch(Exception ex){field.setError("Escribe un número válido");}
    }));
    dialog.show();
  }

  void resetDialog(){
    LinearLayout body=Ui.col(this);
    Dialog[] resetSheet=new Dialog[1];
    Ui.add(body,Ui.text(this,"El borrado afecta al historial LOCAL. Las estadísticas ya compartidas " +
        "en un grupo remoto deben retirarse desde ese grupo. No se borrarán rutinas, perfil ni cuenta.",13,Ui.MUTED,false));
    Ui.gap(body,16);
    String[] labels={"Última sesión", "Día elegido", "Periodo personalizado", "Jugador de cámara",
        "Modalidad", "Todos los tiros", "Todos los ejercicios", "Registros FIT", "Todas las estadísticas"};
    Spinner scope=Ui.spinner(this,labels,8);Ui.add(body,scope);
    Ui.gap(body,12);
    LocalDate[] dates={LocalDate.now(),LocalDate.now()};
    Button begin=Ui.button(this,"Desde: "+dates[0],Ui.WHITE,Ui.INK,()->{});
    Button end=Ui.button(this,"Hasta: "+dates[1]+" (inclusive)",Ui.WHITE,Ui.INK,()->{});
    begin.setOnClickListener(v->pickResetDate(dates,0,begin));
    end.setOnClickListener(v->pickResetDate(dates,1,end));
    Ui.add(body,begin);Ui.gap(body,8);Ui.add(body,end);
    Ui.gap(body,12);
    EditText player=Ui.edit(this,"Nombre exacto del jugador (si aplica)");Ui.add(body,player);
    Ui.gap(body,10);
    Spinner mode=Ui.spinner(this,new String[]{"Rutinas","Cámara","Clips de video"},0);Ui.add(body,mode);
    Ui.gap(body,17);
    Ui.add(body,Ui.button(this,"Revisar cantidad antes de borrar",Ui.BLUE,Ui.WHITE,()->{
      ResetManager.Scope choice=ResetManager.Scope.values()[scope.getSelectedItemPosition()];
      String val="";
      if(choice==ResetManager.Scope.PLAYER){val=player.getText().toString().trim();if(val.isEmpty()){
        player.setError("Escribe el jugador");return;}}
      if(choice==ResetManager.Scope.MODE)val=new String[]{"routine","camera","video"}[mode.getSelectedItemPosition()];
      if(choice==ResetManager.Scope.LAST_SESSION){
        List<Store.Session> all=store.sessions();if(all.isEmpty()){
          Ui.toast(this,"No hay sesiones para eliminar");return;}val=all.get(0).id;
      }
      if(dates[1].isBefore(dates[0])){Ui.toast(this,"El fin es anterior al inicio");return;}
      long from=ResetManager.startDay(dates[0]);
      long until=ResetManager.startDay(dates[1].plusDays(1));
      if(choice!=ResetManager.Scope.DAY && choice!=ResetManager.Scope.RANGE){from=0;until=Long.MAX_VALUE;}
      if(choice==ResetManager.Scope.DAY)until=ResetManager.startDay(dates[0].plusDays(1));
      ResetManager.Selection selected=new ResetManager.Selection(choice,from,until,val);
      try{
        ResetManager.Count count=new ResetManager(store,this).preview(selected);
        new AlertDialog.Builder(this).setTitle("Confirmar restablecimiento")
          .setMessage("Se eliminarán "+count.total()+" registros:\n"+count+
            "\n\nNo se eliminan cuentas ni rutinas. El sensor físico no se reinicia. " +
            "Si estás entrenando, termina la sesión antes de borrar.")
          .setNeutralButton("Exportar copia",(d,w)->exportFile(true))
          .setNegativeButton("Conservar",null)
          .setPositiveButton("Eliminar "+count.total(),(d,w)->{
            if(resetSheet[0]!=null)resetSheet[0].dismiss();
            getSharedPreferences("fit.sensor",MODE_PRIVATE).edit().putBoolean("suspended",true).commit();
            stopService(new Intent(this,FitService.class));
            io.execute(()->{
              try{ResetManager.Count deleted=new ResetManager(store,this).execute(selected);
                runOnUiThread(()->{if(!isFinishing()){showPage(4);Ui.toast(this,"Se restablecieron "+deleted.total()+" registros locales");}});
              }catch(Exception error){runOnUiThread(()->Ui.toast(this,"No se pudo restablecer. Conserva tu copia y reintenta."));}
            });
          }).show();
      }catch(Exception ex){Ui.toast(this,"No se pudo calcular el alcance del borrado");}
    }));
    resetSheet[0]=Ui.sheet(this,"Restablecer estadísticas",body);
  }
  void pickResetDate(LocalDate[] dates,int index,Button button){
    LocalDate current=dates[index];
    new DatePickerDialog(this,(v,y,m,d)->{
      dates[index]=LocalDate.of(y,m+1,d);
      button.setText((index==0?"Desde: ":"Hasta: ")+dates[index]+(index==1?" (inclusive)":""));
    },current.getYear(),current.getMonthValue()-1,current.getDayOfMonth()).show();
  }

  void exportFile(boolean json) {
    try {
      pendingExport = json ? store.backup(catalog).toString(2) : store.csv();
      startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType(json ? "application/json" : "text/csv")
              .putExtra(
                  Intent.EXTRA_TITLE,
                  "PanaHoops-NEXT-" + LocalDate.now() + (json ? ".json" : ".csv")),
          91);
    } catch (Exception e) {
      Ui.toast(this, "No se pudo preparar el archivo.");
    }
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    android.net.Uri uri = data.getData();
    if (request == 91 && pendingExport != null) {
      String text = pendingExport;
      pendingExport = null;
      io.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
              if (out == null) throw new IOException();
              out.write(text.getBytes(StandardCharsets.UTF_8));
              runOnUiThread(() -> Ui.toast(this, "Archivo guardado."));
            } catch (Exception e) {
              runOnUiThread(() -> Ui.toast(this, "No se pudo guardar el archivo."));
            }
          });
    } else if (request == 92)
      io.execute(
          () -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IOException();
              store.importBackup(new JSONObject(Ui.read(in, 8 * 1024 * 1024)), catalog);
              runOnUiThread(
                  () -> {
                    if (!isFinishing()) {
                      showPage(page);
                      Ui.toast(this, "Importado sin duplicar registros.");
                    }
                  });
            } catch (Exception e) {
              runOnUiThread(() -> Ui.toast(this, "Copia no válida. No se importaron registros."));
            }
          });
  }

  @Override
  public void onBackPressed() {
    if (page != 0) showPage(0);
    else super.onBackPressed();
  }
}
