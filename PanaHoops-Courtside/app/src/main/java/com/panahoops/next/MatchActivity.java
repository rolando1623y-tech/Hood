package com.panahoops.next;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.*;
import androidx.core.content.ContextCompat;
import com.google.mediapipe.framework.image.*;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.objectdetector.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@androidx.annotation.OptIn(markerClass = {androidx.camera.view.TransformExperimental.class})
public final class MatchActivity extends ComponentActivity {
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final CourtEngine engine = new CourtEngine();
  PreviewView preview;
  MatchOverlay overlay;
  TextView status, stats;
  Button toggle;
  ProcessCameraProvider provider;
  ObjectDetector detector;
  TrainingStore store;
  volatile boolean running = false, dead = false;
  volatile int epoch = 0;
  boolean cameraReady = false;
  int tap = 0, stored = 0;
  float x1, y1;
  long last = 0, lastRendered = 0;
  float analysisHz = 0;
  Matrix mapping = new Matrix();
  final RimTracker rimTracker = new RimTracker();
  volatile float[] calibration;
  volatile int rimEpoch = 0;
  int workerRimEpoch = -1;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new TrainingStore(this);
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.DARK);
    Ui.insets(this, root, true);
    setContentView(root);
    LinearLayout h = Ui.row(this);
    Ui.pad(h, 16, 8, 16, 8);
    h.addView(Ui.button(this, "‹", 0xFF202B42, Ui.WHITE, this::finish));
    h.addView(Ui.text(this, "COURTSIDE / BETA", 16, Ui.WHITE, true));
    Ui.add(root, h);
    status =
        Ui.text(this, "1. Activa la cámara. 2. Marca los extremos del aro.", 13, Ui.WHITE, false);
    Ui.pad(status, 16, 8, 16, 8);
    Ui.add(root, status);
    FrameLayout stage = new FrameLayout(this);
    preview = new PreviewView(this);
    preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    overlay = new MatchOverlay();
    stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
    stats = Ui.text(this, "0 tiros · 0 jugadores", 12, Ui.WHITE, false);
    Ui.pad(stats, 16, 8, 16, 8);
    Ui.add(root, stats);
    LinearLayout buttons = Ui.row(this);
    toggle = Ui.button(this, "Activar", Ui.BLUE, Ui.WHITE, this::activate);
    buttons.addView(toggle, new LinearLayout.LayoutParams(0, -2, 1));
    buttons.addView(
        Ui.button(
            this,
            "Calibrar aro",
            0xFF202B42,
            Ui.WHITE,
            () -> {
              running = false;
              epoch++;
              engine.interrupt(SystemClock.elapsedRealtime());
              tap = 1;
              status.setText("Toca el extremo izquierdo y después el derecho del aro");
              toggle.setText("Continuar");
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(root, buttons);
    LinearLayout more = Ui.row(this);
    more.addView(
        Ui.button(this, "Jugadores", 0xFF202B42, Ui.WHITE, this::players),
        new LinearLayout.LayoutParams(0, -2, 1));
    more.addView(
        Ui.button(this, "Revisar tiros", 0xFF202B42, Ui.WHITE, this::review),
        new LinearLayout.LayoutParams(0, -2, 1));
    more.addView(
        Ui.button(this, "Guía", 0xFF202B42, Ui.WHITE, this::help),
        new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(root, more);
    worker.execute(
        () -> {
          try {
            detector =
                ObjectDetector.createFromOptions(
                    this,
                    ObjectDetector.ObjectDetectorOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath("court_detector.tflite")
                                .build())
                        .setScoreThreshold(.3f)
                        .setMaxResults(12)
                        .setCategoryAllowlist(Arrays.asList("person", "sports ball"))
                        .build());
          } catch (Exception e) {
            runOnUiThread(
                () -> status.setText("No se pudo cargar el detector. Cierra y vuelve a abrir."));
          }
        });
  }

  void activate() {
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 40);
      return;
    }
    if (!cameraReady) {
      bind();
      return;
    }
    if (!engine.calibrated) {
      tap = 1;
      status.setText("Toca los dos extremos del aro visible");
      return;
    }
    epoch++;
    running = !running;
    if (!running) engine.interrupt(SystemClock.elapsedRealtime());
    toggle.setText(running ? "Pausar" : "Continuar");
    status.setText(running ? "Seguimiento experimental activo" : "Pausado");
    persist();
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] v) {
    super.onRequestPermissionsResult(r, p, v);
    if (r == 40 && v.length > 0 && v[0] == PackageManager.PERMISSION_GRANTED) bind();
  }

  void bind() {
    var f = ProcessCameraProvider.getInstance(this);
    f.addListener(
        () -> {
          if (dead) return;
          try {
            provider = f.get();
            provider.unbindAll();
            Preview p = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis a =
                new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(1280, 720))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            a.setAnalyzer(worker, this::analyze);
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, a);
            cameraReady = true;
            tap = 1;
            status.setText("Marca los dos extremos del aro. Mantén fijo el teléfono.");
            toggle.setText("Empezar");
          } catch (Exception e) {
            status.setText("No se pudo abrir la cámara trasera. Comprueba el permiso.");
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  void analyze(ImageProxy image) {
    Bitmap raw = null, frame = null;
    MPImage input = null;
    try {
      long now = SystemClock.elapsedRealtime();
      if (dead || detector == null || (!running && now - last < 200)) return;
      last = now;
      int token = epoch;
      if (!running) {
        ImageProxyTransformFactory f = new ImageProxyTransformFactory();
        f.setUsingRotationDegrees(true);
        f.setUsingCropRect(false);
        OutputTransform src = f.getOutputTransform(image);
        int rot = image.getImageInfo().getRotationDegrees(),
            fw = rot == 90 || rot == 270 ? image.getHeight() : image.getWidth(),
            fh = rot == 90 || rot == 270 ? image.getWidth() : image.getHeight();
        runOnUiThread(
            () -> {
              if (dead) return;
              OutputTransform dst = preview.getOutputTransform();
              if (dst == null) return;
              Matrix m = new Matrix();
              new CoordinateTransform(src, dst).transform(m);
              Matrix scale = new Matrix();
              scale.setScale(fw, fh);
              m.preConcat(scale);
              mapping = m;
            });
        return;
      }
      int width = image.getWidth(), height = image.getHeight();
      raw = image.toBitmap();
      Bitmap crop = raw;
      Matrix rotation = new Matrix();
      rotation.postRotate(image.getImageInfo().getRotationDegrees());
      frame = Bitmap.createBitmap(crop, 0, 0, width, height, rotation, true);
      if (crop != raw && crop != frame) crop.recycle();
      if (workerRimEpoch != rimEpoch) {
        rimTracker.reset();
        workerRimEpoch = rimEpoch;
      }
      float[] calibrated = calibration;
      final RimTracker.Result rim =
          calibrated == null ? null : rimTracker.update(frame, calibrated);
      ImageProxyTransformFactory factory = new ImageProxyTransformFactory();
      factory.setUsingRotationDegrees(true);
      factory.setUsingCropRect(false);
      OutputTransform source = factory.getOutputTransform(image);
      input = new BitmapImageBuilder(frame).build();
      ObjectDetectorResult result = detector.detect(input);
      int fw = frame.getWidth(), fh = frame.getHeight();
      ArrayList<CourtEngine.Box> boxes = new ArrayList<>();
      for (Detection d : result.detections()) {
        if (d.categories().isEmpty()) continue;
        RectF b = d.boundingBox();
        var c = d.categories().get(0);
        boxes.add(
            new CourtEngine.Box(
                c.categoryName(),
                b.left / fw,
                b.top / fh,
                b.width() / fw,
                b.height() / fh,
                c.score()));
      }
      long latency = SystemClock.elapsedRealtime() - now;
      runOnUiThread(
          () -> {
            if (dead || !running || token != epoch) return;
            OutputTransform dest = preview.getOutputTransform();
            if (dest == null) return;
            Matrix m = new Matrix();
            new CoordinateTransform(source, dest).transform(m);
            Matrix scale = new Matrix();
            scale.setScale(fw, fh);
            m.preConcat(scale);
            mapping = m;
            if (rim != null) {
              if (!rim.locked) {
                running = false;
                epoch++;
                engine.interrupt(now);
                persist();
                toggle.setText("Continuar");
                tap = 1;
                engine.calibrated = false;
                status.setText("Aro perdido o sin textura. Marca de nuevo sus extremos.");
                return;
              }
              if (rim.confidence < .78f) {
                status.setText("Aro parcialmente oculto · esperando una vista clara");
                return;
              }
              engine.left += rim.dx;
              engine.right += rim.dx;
              engine.rimY += rim.dy;
            }
            engine.update(boxes, now);
            overlay.invalidate();
            status.setText(engine.status + " · " + latency + " ms");
            long visible = engine.players.stream().filter(v -> now - v.seen < 600).count();
            long stamp = SystemClock.elapsedRealtime();
            if (lastRendered > 0) {
              float hz = 1000f / Math.max(1, stamp - lastRendered);
              analysisHz = analysisHz == 0 ? hz : analysisHz * .8f + hz * .2f;
            }
            lastRendered = stamp;
            stats.setText(
                engine.shots.size()
                    + " tiros · "
                    + visible
                    + " jugadores · "
                    + String.format(Locale.US, "%.1f", analysisHz)
                    + " análisis/s");
            persist();
          });
    } catch (Exception e) {
      runOnUiThread(
          () -> {
            if (!dead) {
              running = false;
              epoch++;
              engine.interrupt(SystemClock.elapsedRealtime());
              persist();
              toggle.setText("Continuar");
              status.setText("Análisis interrumpido. Pulsa Continuar para reintentar.");
            }
          });
    } finally {
      if (input != null) input.close();
      if (frame != null && frame != raw) frame.recycle();
      if (raw != null) raw.recycle();
      image.close();
    }
  }

  void persist() {
    if (stored >= engine.shots.size()) return;
    try {
      JSONArray arr = new JSONArray(store.prefs.getString("matchShots", "[]"));
      while (stored < engine.shots.size()) {
        CourtEngine.Shot s = engine.shots.get(stored++);
        String name = "Sin asignar";
        for (CourtEngine.Player p : engine.players) if (p.id == s.player) name = p.name;
        if (arr.length() >= 10000) arr.remove(0);
        arr.put(
            new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("time", s.time)
                .put("player", name)
                .put("result", s.result)
                .put("source", s.source));
      }
      store.prefs.edit().putString("matchShots", arr.toString()).apply();
    } catch (JSONException ignored) {
    }
  }

  void pauseReview() {
    running = false;
    epoch++;
    engine.interrupt(SystemClock.elapsedRealtime());
    persist();
    toggle.setText("Continuar");
  }

  void players() {
    pauseReview();
    LinearLayout b = Ui.col(this);
    Ui.pad(b, 18, 10, 18, 10);
    Ui.add(
        b,
        Ui.text(
            this,
            "IDs temporales: una oclusión puede cambiar el ID. Revisa la asignación antes de"
                + " confirmar estadísticas.",
            13,
            Ui.MUTED,
            false));
    for (CourtEngine.Player p : engine.players) {
      EditText name = new EditText(this);
      name.setText(p.name);
      Ui.add(b, name);
      Spinner role =
          Ui.spinner(this, new String[] {"Tirador", "Defensor"}, p.role.equals("Defensor") ? 1 : 0);
      Ui.add(b, role);
      Ui.add(
          b,
          Ui.button(
              this,
              "Guardar #" + p.id,
              Ui.PALE,
              Ui.BLUE,
              () -> {
                p.name =
                    name.getText()
                        .toString()
                        .trim()
                        .substring(0, Math.min(40, name.getText().toString().trim().length()));
                p.role = role.getSelectedItem().toString();
                if (p.role.equals("Defensor") && engine.pinned == p.id) engine.pinned = 0;
                Ui.toast(this, "Jugador actualizado");
              }));
      Ui.add(
          b,
          Ui.button(
              this,
              "Asignar próximos tiros a #" + p.id,
              Ui.WHITE,
              Ui.BLUE,
              () -> {
                if (p.role.equals("Defensor")) {
                  Ui.toast(this, "Cambia el rol a tirador primero");
                  return;
                }
                engine.pinned = p.id;
                Ui.toast(this, "Tirador fijado para esta sesión");
              }));
    }
    Ui.add(b, Ui.button(this, "Asignación automática", Ui.WHITE, Ui.BLUE, () -> engine.pinned = 0));
    try {
      JSONArray a = new JSONArray(store.prefs.getString("matchShots", "[]"));
      Map<String, int[]> totals = new LinkedHashMap<>();
      for (int i = 0; i < a.length(); i++) {
        JSONObject j = a.getJSONObject(i);
        int[] v = totals.computeIfAbsent(j.optString("player"), key -> new int[3]);
        String r = j.optString("result");
        if (r.equals("Enceste confirmado")) {
          v[0]++;
          v[1]++;
        } else if (r.equals("Fallo confirmado")) v[1]++;
        else v[2]++;
      }
      Ui.add(b, Ui.title(this, "Por jugador", 22));
      for (var entry : totals.entrySet()) {
        int[] v = entry.getValue();
        Ui.add(
            b,
            Ui.text(
                this,
                entry.getKey()
                    + ": "
                    + v[0]
                    + "/"
                    + v[1]
                    + " confirmados · "
                    + v[2]
                    + " por revisar",
                13,
                Ui.INK,
                false));
      }
    } catch (JSONException ignored) {
    }
    ScrollView s = new ScrollView(this);
    s.addView(b);
    new android.app.AlertDialog.Builder(this)
        .setTitle("Jugadores y roles")
        .setView(s)
        .setPositiveButton("Listo", null)
        .show();
  }

  void review() {
    pauseReview();
    persist();
    try {
      JSONArray a = new JSONArray(store.prefs.getString("matchShots", "[]"));
      String[] rows = new String[a.length()];
      for (int i = 0; i < a.length(); i++) {
        JSONObject s = a.getJSONObject(a.length() - 1 - i);
        rows[i] = s.optString("player") + " · " + s.optString("result");
      }
      new android.app.AlertDialog.Builder(this)
          .setTitle("Tiros: toca para confirmar o corregir")
          .setItems(
              rows,
              (d, n) -> {
                int ix = a.length() - 1 - n;
                new android.app.AlertDialog.Builder(this)
                    .setItems(
                        new String[] {
                          "Enceste confirmado",
                          "Fallo confirmado",
                          "Dudoso",
                          "Eliminar",
                          "Cambiar jugador"
                        },
                        (dd, k) -> {
                          try {
                            if (k == 4) {
                              EditText player = new EditText(this);
                              player.setText(a.getJSONObject(ix).optString("player"));
                              new android.app.AlertDialog.Builder(this)
                                  .setTitle("Asignar tiro a jugador")
                                  .setView(player)
                                  .setPositiveButton(
                                      "Guardar",
                                      (dlg, num) -> {
                                        try {
                                          String playerName = player.getText().toString().trim();
                                          if (playerName.isEmpty() || playerName.length() > 40)
                                            return;
                                          a.getJSONObject(ix).put("player", playerName);
                                          store
                                              .prefs
                                              .edit()
                                              .putString("matchShots", a.toString())
                                              .apply();
                                        } catch (JSONException ignored) {
                                        }
                                      })
                                  .setNegativeButton("Cancelar", null)
                                  .show();
                              return;
                            }
                            if (k == 3) a.remove(ix);
                            else
                              a.getJSONObject(ix)
                                  .put(
                                      "result",
                                      k == 0
                                          ? "Enceste confirmado"
                                          : k == 1 ? "Fallo confirmado" : "Dudoso");
                            store.prefs.edit().putString("matchShots", a.toString()).apply();
                          } catch (JSONException ignored) {
                          }
                        })
                    .show();
              })
          .setPositiveButton("Cerrar", null)
          .show();
    } catch (JSONException ignored) {
    }
  }

  void help() {
    new android.app.AlertDialog.Builder(this)
        .setTitle("Cómo grabar un partido")
        .setMessage(
            "Coloca el teléfono fijo a un lado o en diagonal, con el balón, jugadores y aro"
                + " completos. Evita grabar justo bajo el aro.\n\n"
                + "Marca los dos extremos del aro para adaptar la geometría a esta vista. Recalibra"
                + " si mueves el teléfono. No hay reconocimiento automático del aro ni"
                + " reconstrucción 3D.\n\n"
                + "El detector local reconoce personas y balones deportivos. Los jugadores tienen"
                + " IDs temporales, no identidad facial. En Jugadores puedes nombrarlos, marcar"
                + " defensores y fijar un tirador.\n\n"
                + "Un cruce en 2D solo indica enceste probable. Una oclusión queda dudosa; confirma"
                + " los resultados en Revisar tiros. No distingue con certeza un balón que pase"
                + " delante del aro.\n\n"
                + "La vista previa y el análisis tienen velocidades distintas. El modelo puede"
                + " perder un balón pequeño o rápido; acerca la cámara y usa buena luz. No envía"
                + " imágenes a internet.")
        .setPositiveButton("Entendido", null)
        .show();
  }

  @Override
  protected void onStop() {
    running = false;
    epoch++;
    engine.interrupt(SystemClock.elapsedRealtime());
    persist();
    toggle.setText("Continuar");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    dead = true;
    epoch++;
    if (provider != null) provider.unbindAll();
    worker.execute(
        () -> {
          if (detector != null) detector.close();
        });
    worker.shutdown();
    store.close();
    super.onDestroy();
  }

  final class MatchOverlay extends View {
    final Paint p = new Paint(3);
    final Map<Integer, RectF> smooth = new HashMap<>();
    long drawnAt = 0;

    MatchOverlay() {
      super(MatchActivity.this);
    }

    float[] xy(float x, float y) {
      float[] q = {x, y};
      mapping.mapPoints(q);
      return q;
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      p.setStrokeWidth(Ui.dp(getContext(), 2));
      p.setStyle(Paint.Style.STROKE);
      long now = SystemClock.elapsedRealtime();
      float blend = drawnAt == 0 ? 1 : Math.min(1, (now - drawnAt) / 70f);
      drawnAt = now;
      for (CourtEngine.Player v : engine.players)
        if (now - v.seen < 600) {
          RectF r = smooth.get(v.id);
          if (r == null) {
            r = new RectF(v.box.x, v.box.y, v.box.x + v.box.w, v.box.y + v.box.h);
            smooth.put(v.id, r);
          } else {
            r.left += (v.box.x - r.left) * blend;
            r.top += (v.box.y - r.top) * blend;
            r.right += (v.box.x + v.box.w - r.right) * blend;
            r.bottom += (v.box.y + v.box.h - r.bottom) * blend;
          }
          float[] a = xy(r.left, r.top), b = xy(r.right, r.bottom);
          p.setColor(v.role.equals("Defensor") ? 0xFFFFA766 : 0xFF58D7DF);
          c.drawRect(a[0], a[1], b[0], b[1], p);
          p.setStyle(Paint.Style.FILL);
          p.setTextSize(Ui.dp(getContext(), 12));
          c.drawText("#" + v.id + " " + v.name, a[0], Math.max(20, a[1] - 6), p);
          p.setStyle(Paint.Style.STROKE);
        }
      if (engine.calibrated) {
        p.setColor(0xFFFFA766);
        float[] a = xy(engine.left, engine.line(engine.left)),
            b = xy(engine.right, engine.line(engine.right));
        c.drawLine(a[0], a[1], b[0], b[1], p);
      }
      p.setColor(0xFFEAFB63);
      float[] prev = null;
      for (CourtEngine.Box b : engine.trail) {
        float[] q = xy(b.cx(), b.cy());
        if (prev != null) c.drawLine(prev[0], prev[1], q[0], q[1], p);
        prev = q;
      }
      if (engine.ball != null) {
        float[] q = xy(engine.ball.cx(), engine.ball.cy());
        c.drawCircle(q[0], q[1], Ui.dp(getContext(), 9), p);
      }
      if (running && isShown()) postInvalidateOnAnimation();
    }

    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (event.getAction() != MotionEvent.ACTION_UP || tap == 0 || !cameraReady) return true;
      Matrix inv = new Matrix();
      if (!mapping.invert(inv)) return true;
      float[] q = {event.getX(), event.getY()};
      inv.mapPoints(q);
      if (q[0] < 0 || q[0] > 1 || q[1] < 0 || q[1] > 1) {
        Ui.toast(MatchActivity.this, "Toca dentro de la imagen");
        return true;
      }
      if (tap == 1) {
        x1 = q[0];
        y1 = q[1];
        tap = 2;
        status.setText("Ahora toca el otro extremo del aro");
      } else {
        try {
          engine.calibrate(x1, y1, q[0], q[1]);
          calibration = new float[] {engine.left, engine.right, engine.rimY, engine.slope};
          rimEpoch++;
          tap = 0;
          status.setText("Aro calibrado. Pulsa Empezar.");
          invalidate();
        } catch (IllegalArgumentException e) {
          tap = 1;
          status.setText(e.getMessage());
        }
      }
      return true;
    }
  }
}
