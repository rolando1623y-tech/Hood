package com.panahoops.next;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.*;
import androidx.core.content.ContextCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.*;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** CameraX + bundled ML Kit, single in-flight inference and lifecycle cleanup. */
@androidx.annotation.OptIn(
    markerClass = {
      androidx.camera.core.ExperimentalGetImage.class,
      androidx.camera.view.TransformExperimental.class
    })
public final class VisionActivity extends ComponentActivity {
  Store store;
  Engine engine;
  PreviewView preview;
  Overlay overlay;
  ImageView clipImage;
  LinearLayout empty;
  Spinner modes;
  Switch hand;
  TextView counter, unit, phase, cue, metrics, clockText, fps;
  Button start, save, activate;
  SeekBar seek;
  PoseDetector detector;
  ProcessCameraProvider provider;
  MediaMetadataRetriever retriever;
  TextToSpeech voice;
  ToneGenerator tone;
  Bitmap displayed;
  final ExecutorService cameraWorker = Executors.newSingleThreadExecutor(),
      decoder = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  final AtomicBoolean busy = new AtomicBoolean(false);
  boolean active = false,
      session = false,
      front = false,
      activated = false,
      requested = false,
      destroyed = false,
      ttsReady = false,
      clip = false,
      clipLoaded = false;
  volatile int generation = 0;
  int bindEpoch = 0;
  long lastInfer = 0, lastResult = 0, lastTick = 0, elapsed = 0, cursor = 0, duration = 0;
  float realFps = 0, deliveredHz = 0;
  long lastDeliveredNs = 0;
  String cameraFrequencyInfo = "Modo automático";
  boolean cameraFallbackTried = false;
  int observedWidth=0,observedHeight=0;
  static final long STEP = 200;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(this);
    engine = new Engine(Engine.Mode.from(getIntent().getStringExtra("mode")), false);
    ui();
    detector =
        PoseDetection.getClient(
            new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build());
    voice =
        new TextToSpeech(
            this,
            status -> {
              ttsReady = status == TextToSpeech.SUCCESS;
              if (ttsReady) voice.setLanguage(new Locale("es", "DO"));
            });
    lastTick = SystemClock.elapsedRealtime();
    handler.post(ticker);
  }

  void ui() {
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.DARK);
    setContentView(root);
    Ui.insets(this, root, true);
    LinearLayout header = Ui.row(this);
    Ui.pad(header, 16, 10, 16, 12);
    header.addView(
        Ui.iconButton(this, "back", "Volver al entrenamiento", 0xFF202B42, Ui.WHITE, this::finish),
        new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
    TextView brand = Ui.label(this, "VISION STUDIO / EN VIVO", Ui.WHITE);
    Ui.pad(brand, 13, 0, 0, 0);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        Ui.iconButton(
            this, "settings", "Guía de cámara y colocación", 0xFF202B42, Ui.WHITE, this::help),
        new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
    Ui.add(root, header);
    LinearLayout selectors = Ui.row(this);
    Ui.pad(selectors, 18, 0, 18, 12);
    modes =
        Ui.spinner(
            this,
            Arrays.stream(Engine.Mode.values()).map(m -> m.title).toArray(String[]::new),
            engine.mode.ordinal());
    selectors.addView(modes, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
    hand = new Switch(this);
    hand.setText(" Izq.");
    hand.setTextColor(Ui.WHITE);
    Ui.pad(hand, 12, 0, 0, 0);
    selectors.addView(hand);
    Ui.add(root, selectors);
    FrameLayout stage = new FrameLayout(this);
    stage.setBackground(Ui.shape(this, 0xFF111B2D, 25));
    stage.setClipToOutline(true);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
    sp.setMargins(Ui.dp(this, 13), 0, Ui.dp(this, 13), 0);
    root.addView(stage, sp);
    preview = new PreviewView(this);
    preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    clipImage = new ImageView(this);
    clipImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
    clipImage.setVisibility(View.GONE);
    stage.addView(clipImage, new FrameLayout.LayoutParams(-1, -1));
    overlay = new Overlay(this);
    stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    empty = Ui.col(this);
    empty.setGravity(Gravity.CENTER);
    Ui.pad(empty, 24, 25, 24, 25);
    TextView title = Ui.title(this, "TU SIGUIENTE\nMOVIMIENTO.", 31);
    title.setTextColor(Ui.WHITE);
    title.setGravity(Gravity.CENTER);
    Ui.add(empty, title);
    Ui.gap(empty, 15);
    TextView intro =
        Ui.text(
            this,
            "Apoya el celular y deja espacio para que se vea tu cuerpo.",
            13,
            0xFF9EADC6,
            false);
    intro.setGravity(Gravity.CENTER);
    Ui.add(empty, intro);
    Ui.gap(empty, 22);
    activate = Ui.button(this, "Activar cámara", Ui.BLUE, Ui.WHITE, this::requestCamera);
    Ui.add(empty, activate);
    stage.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    fps = Ui.label(this, "MODELO LOCAL INCLUIDO", 0xFFB9C9E3);
    fps.setBackground(Ui.shape(this, 0xCC111B2D, 8));
    Ui.pad(fps, 9, 7, 9, 7);
    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
    fp.setMargins(Ui.dp(this, 14), Ui.dp(this, 14), 0, 0);
    stage.addView(fps, fp);
    cue = Ui.text(this, engine.mode.view, 12, Ui.WHITE, false);
    cue.setGravity(Gravity.CENTER);
    cue.setBackground(Ui.shape(this, 0xE0111B2D, 12));
    Ui.pad(cue, 10, 9, 10, 9);
    FrameLayout.LayoutParams qp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    qp.setMargins(Ui.dp(this, 13), 0, Ui.dp(this, 13), Ui.dp(this, 13));
    stage.addView(cue, qp);
    LinearLayout tools = Ui.row(this);
    Ui.pad(tools, 18, 10, 18, 5);
    Button camera = Ui.button(this, "Cámara", 0xFF202B42, Ui.WHITE, this::requestCamera);
    tools.addView(camera, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
    Button video = Ui.button(this, "Abrir clip", 0xFF202B42, Ui.WHITE, this::chooseClip);
    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
    vp.setMargins(Ui.dp(this, 7), 0, Ui.dp(this, 7), 0);
    tools.addView(video, vp);
    tools.addView(
        Ui.iconButton(
            this,
            "flip",
            "Cambiar cámara",
            0xFF202B42,
            Ui.WHITE,
            () -> {
              if (clip) {
                requestCamera();
                return;
              }
              if (session) finishSession(false);
              front = !front;
              if (activated) bindCamera();
              else
                Ui.toast(
                    this, front ? "Se usará la cámara frontal." : "Se usará la cámara trasera.");
            }),
        new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
    View skel =
        Ui.iconButton(
            this,
            "lens",
            "Mostrar u ocultar esqueleto",
            0xFF202B42,
            Ui.WHITE,
            () -> {
              overlay.skeleton = !overlay.skeleton;
              overlay.invalidate();
            });
    LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
    kp.leftMargin = Ui.dp(this, 7);
    tools.addView(skel, kp);
    View center =
        Ui.iconButton(
            this,
            "court",
            "Recentrar referencia de movimiento",
            0xFF202B42,
            Ui.WHITE,
            () -> {
              engine.recenter();
              Ui.toast(this, "Referencia reiniciada. El conteo se conserva.");
            });
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
    cp.leftMargin = Ui.dp(this, 7);
    tools.addView(center, cp);
    Ui.add(root, tools);
    seek = new SeekBar(this);
    seek.setMax(1000);
    seek.setVisibility(View.GONE);
    Ui.add(root, seek);
    LinearLayout panel = Ui.col(this);
    Ui.pad(panel, 23, 7, 23, 17);
    LinearLayout values = Ui.row(this);
    counter = Ui.title(this, "0", 55);
    counter.setTextColor(Ui.WHITE);
    values.addView(counter);
    unit = Ui.label(this, engine.mode.unit.toUpperCase(Locale.ROOT), 0xFF94A7C6);
    Ui.pad(unit, 12, 14, 0, 0);
    values.addView(unit, new LinearLayout.LayoutParams(0, -2, 1));
    clockText = Ui.text(this, "00:00", 19, Ui.WHITE, true);
    values.addView(clockText);
    Ui.add(panel, values);
    phase = Ui.text(this, "Selecciona un ejercicio y activa la cámara.", 11, 0xFF94A7C6, false);
    Ui.add(panel, phase);
    Ui.gap(panel, 8);
    metrics = Ui.text(this, "Codo 2D  —    Rodilla 2D  —    Visibilidad  —", 10, 0xFF94A7C6, false);
    Ui.add(panel, metrics);
    Ui.gap(panel, 14);
    LinearLayout actions = Ui.row(this);
    start = Ui.button(this, "Empezar serie", Ui.BLUE, Ui.WHITE, this::toggle);
    start.setEnabled(false);
    actions.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
    save = Ui.button(this, "Guardar", 0xFF202B42, Ui.WHITE, () -> finishSession(true));
    save.setEnabled(false);
    LinearLayout.LayoutParams saveP = new LinearLayout.LayoutParams(Ui.dp(this, 100), -2);
    saveP.leftMargin = Ui.dp(this, 9);
    actions.addView(save, saveP);
    Ui.add(panel, actions);
    Ui.add(root, panel);
    modes.setOnItemSelectedListener(
        MainActivity.selected(
            i -> {
              Engine.Mode m = Engine.Mode.values()[i];
              if (m != engine.mode) {
                if (session) finishSession(false);
                engine = new Engine(m, hand.isChecked());
                generation++;
                resetMetrics();
              }
              hand.setEnabled(m == Engine.Mode.SHOT && !session);
              cue.setText(m.view);
            }));
    hand.setOnCheckedChangeListener(
        (b, checked) -> {
          if (session) finishSession(false);
          engine = new Engine(engine.mode, checked);
          generation++;
          resetMetrics();
        });
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {
            pause();
          }

          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user && clipLoaded) {
              cursor = duration * p / 1000;
              clockText.setText(Ui.time(cursor / 1000));
            }
          }

          public void onStopTrackingTouch(SeekBar s) {
            if (clipLoaded) {
              session = false;
              elapsed = 0;
              engine = new Engine(engine.mode, hand.isChecked());
              resetMetrics();
              save.setEnabled(false);
              cue.setText("Revisión reiniciada. Pulsa Analizar clip.");
              previewFrame();
            }
          }
        });
  }

  void help() {
    if (active) pause();
    new AlertDialog.Builder(this)
        .setTitle("Mejor imagen. Mejor referencia.")
        .setMessage(
            "Apoya el celular en un lugar fijo, con buena luz y espacio libre.\n\n"
                + "Usa la vista indicada y muestra las articulaciones del ejercicio. Empieza desde"
                + " la posición inicial, completa el ciclo y vuelve con control.\n\n"
                + "La cámara actualiza postura, ángulos 2D y fases en vivo. Guarda la serie para"
                + " llevarla al historial.\n\n"
                + "Los clips se revisan con un muestreo de 5 cuadros por segundo del video, hasta 5"
                + " minutos. Mover el cursor reinicia la revisión. El muestreo puede omitir gestos"
                + " rápidos.\n\n"
                + "El gesto de tiro no confirma encestes. El resultado es aproximado; la"
                + " visibilidad no es un porcentaje de precisión. Detente si aparece dolor o"
                + " mareo.")
        .setPositiveButton("Entendido", null)
        .show();
  }

  void requestCamera() {
    if (session) finishSession(false);
    generation++;
    clip = false;
    clipLoaded = false;
    clipImage.setImageDrawable(null);
    displayed = null;
    clipImage.setVisibility(View.GONE);
    preview.setVisibility(View.VISIBLE);
    seek.setVisibility(View.GONE);
    overlay.clear();
    requested = true;
    cameraFallbackTried = false;
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      activated = true;
      bindCamera();
    } else requestPermissions(new String[] {Manifest.permission.CAMERA}, 71);
  }

  public void onRequestPermissionsResult(int code, String[] p, int[] r) {
    super.onRequestPermissionsResult(code, p, r);
    if (code == 71 && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
      activated = true;
      bindCamera();
    } else {
      cue.setText("Permiso bloqueado. Puedes abrir Ajustes o analizar un clip local.");
      activate.setText("Abrir permisos");
      activate.setOnClickListener(
          v ->
              startActivity(
                  new Intent(
                      android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + getPackageName()))));
    }
  }

  void bindCamera() {
    int binding = ++bindEpoch;
    generation++;
    lastResult = 0;
    realFps = 0;
    deliveredHz = 0;
    lastDeliveredNs = 0;
    empty.setVisibility(View.VISIBLE);
    activate.setEnabled(false);
    cue.setText("Preparando cámara y modelo local…");
    var future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          if (destroyed || clip || binding != bindEpoch) return;
          try {
            provider = future.get();
            provider.unbindAll();
            CameraSelector selector =
                front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            if (!provider.hasCamera(selector)) {
              front = !front;
              selector =
                  front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
              if (!provider.hasCamera(selector)) throw new IllegalStateException();
            }
            int q = store.prefs.getInt("quality", 0);
            Size size = q == 1 || q == 3 ? new Size(640, 480) : new Size(960, 540);
            CameraPerformance.Choice chosen=cameraFallbackTried
                ?new CameraPerformance.Choice(null,"Frecuencia automática de recuperación")
                :CameraPerformance.choose(this,front,q);
            cameraFrequencyInfo=chosen.explanation;
            Preview.Builder pb = new Preview.Builder();
            if (chosen.request != null) pb.setTargetFrameRate(chosen.request);
            Preview pv = pb.build();
            pv.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis analysis =
                new ImageAnalysis.Builder()
                    .setTargetResolution(size)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(cameraWorker, this::analyze);
            preview.post(
                () -> {
                  if (destroyed || clip || binding != bindEpoch) return;
                  try {
                    ViewPort port = preview.getViewPort();
                    CameraSelector current =
                        front
                            ? CameraSelector.DEFAULT_FRONT_CAMERA
                            : CameraSelector.DEFAULT_BACK_CAMERA;
                    if (port != null)
                      provider.bindToLifecycle(
                          this,
                          current,
                          new UseCaseGroup.Builder()
                              .setViewPort(port)
                              .addUseCase(pv)
                              .addUseCase(analysis)
                              .build());
                    else provider.bindToLifecycle(this, current, pv, analysis);
                    empty.setVisibility(View.GONE);
                    activate.setEnabled(true);
                    start.setEnabled(true);
                    start.setText("Empezar serie");
                    cue.setText(cameraFrequencyInfo + ". Vista previa lista. Pulsa Empezar.");
                  } catch (Exception e) {
                    cameraError();
                  }
                });
          } catch (Exception e) {
            cameraError();
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  void cameraError() {
    if(!cameraFallbackTried && store.prefs.getInt("quality",0)>1 && !destroyed){
      cameraFallbackTried=true;
      cue.setText("Frecuencia alta no disponible en esta sesión. Reintentando en automático…");
      bindCamera();
      return;
    }
    empty.setVisibility(View.VISIBLE);
    activate.setEnabled(true);
    activate.setText("Intentar de nuevo");
    start.setEnabled(false);
    cue.setText("No se pudo abrir la cámara. Cierra otras apps que la usen o prueba un clip.");
  }

  void analyze(ImageProxy image) {
    long now = SystemClock.elapsedRealtime();
    if (lastDeliveredNs > 0) {
      long interval=image.getImageInfo().getTimestamp()-lastDeliveredNs;
      if(interval>0){float current=1_000_000_000f/interval;
        deliveredHz=deliveredHz==0?current:deliveredHz*.88f+current*.12f;}
    }
    lastDeliveredNs=image.getImageInfo().getTimestamp();
    observedWidth=image.getWidth();observedHeight=image.getHeight();
    if (destroyed
        || clip
        || image.getImage() == null
        || !busy.compareAndSet(false, true)) {
      image.close();
      return;
    }
    lastInfer = now;
    int token = generation,
        rotation = image.getImageInfo().getRotationDegrees(),
        w = rotation == 90 || rotation == 270 ? image.getHeight() : image.getWidth(),
        h = rotation == 90 || rotation == 270 ? image.getWidth() : image.getHeight();
    ImageProxyTransformFactory factory = new ImageProxyTransformFactory();
    factory.setUsingCropRect(false);
    factory.setUsingRotationDegrees(true);
    OutputTransform source = factory.getOutputTransform(image);
    try {
      detector
          .process(InputImage.fromMediaImage(image.getImage(), rotation))
          .addOnSuccessListener(
              pose -> {
                if (destroyed || clip || token != generation) return;
                Matrix matrix = null;
                try {
                  OutputTransform to = preview.getOutputTransform();
                  if (to != null) {
                    matrix = new Matrix();
                    new CoordinateTransform(source, to).transform(matrix);
                  }
                } catch (Exception ignored) {
                }
                overlay.display(pose, w, h, matrix);
                long done = SystemClock.elapsedRealtime();
                if (lastResult > 0) {
                  float current = 1000f / Math.max(1, done - lastResult);
                  realFps = realFps == 0 ? current : realFps * .7f + current * .3f;
                  fps.setText(String.format(java.util.Locale.ROOT,
                      "ANÁLISIS %.1f/s · ENTRADA %.1f/s · %d ms · %d×%d · %.0f Hz pantalla",
                      realFps, deliveredHz, done-now, observedWidth, observedHeight,
                      getWindowManager().getDefaultDisplay().getRefreshRate()));
                }
                lastResult = done;
                if (active) result(engine.update(Overlay.points(pose, w, h), now, w / (double) h));
                else
                  phase.setText(
                      session ? "Serie en pausa" : "Vista previa · el conteo aún no ha empezado");
              })
          .addOnFailureListener(
              e -> {
                if (!destroyed && !clip && token == generation) {
                  cue.setText(
                      "No se pudo analizar. Prueba el modo Ahorro y vuelve a activar la cámara.");
                  pause();
                }
              })
          .addOnCompleteListener(
              task -> {
                image.close();
                busy.set(false);
              });
    } catch (Exception e) {
      image.close();
      busy.set(false);
    }
  }

  void accrue() {
    long now = SystemClock.elapsedRealtime();
    if (active && !clip) elapsed += Math.max(0, now - lastTick);
    lastTick = now;
  }

  void toggle() {
    if (active) {
      pause();
      return;
    }
    if (clip && !clipLoaded) return;
    if (!clip && !activated) return;
    if (!session) {
      generation++;
      engine = new Engine(engine.mode, hand.isChecked());
      elapsed = 0;
      session = true;
    }
    if (clip && cursor >= duration) {
      cursor = 0;
      elapsed = 0;
      engine = new Engine(engine.mode, hand.isChecked());
    }
    active = true;
    lastTick = SystemClock.elapsedRealtime();
    modes.setEnabled(false);
    hand.setEnabled(false);
    save.setEnabled(true);
    seek.setEnabled(false);
    start.setText("Pausar");
    cue.setText("Buscando postura. " + engine.mode.view);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (clip) nextClip();
  }

  void pause() {
    handler.removeCallbacks(clipTick);
    accrue();
    active = false;
    generation++;
    engine.recenter();
    start.setText(session ? "Continuar" : clip ? "Analizar clip" : "Empezar serie");
    phase.setText("Serie en pausa");
    seek.setEnabled(clipLoaded);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  void result(Engine.Result r) {
    counter.setText("" + (engine.mode.timed ? (int) r.hold : r.count));
    unit.setText(engine.mode.unit.toUpperCase(Locale.ROOT));
    phase.setText(r.visible ? r.phase : "Sin seguimiento");
    cue.setText(r.cue);
    metrics.setText(
        "Codo 2D  "
            + (Double.isFinite(r.elbow) ? Math.round(r.elbow) + "°" : "—")
            + "    Rodilla 2D  "
            + (Double.isFinite(r.knee) ? Math.round(r.knee) + "°" : "—")
            + "    Visibilidad  "
            + (r.visible ? Math.round(r.confidence * 100) + "%" : "—"));
    if (r.rep && !clip) {
      if (store.prefs.getBoolean("voice", false) && ttsReady)
        voice.speak("" + r.count, TextToSpeech.QUEUE_FLUSH, null, "rep");
      else if (store.prefs.getBoolean("sound", true))
        try {
          if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 40);
          tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
        } catch (RuntimeException ignored) {
        }
    }
  }

  void resetMetrics() {
    counter.setText("0");
    unit.setText(engine.mode.unit.toUpperCase(Locale.ROOT));
    clockText.setText("00:00");
    metrics.setText("Codo 2D  —    Rodilla 2D  —    Visibilidad  —");
    overlay.clear();
  }

  void finishSession(boolean show) {
    if (!session) return;
    accrue();
    int reps = engine.count;
    double held = engine.hold;
    String mode = engine.mode.id(), name = engine.mode.title;
    boolean time = engine.mode.timed;
    session = active = false;
    generation++;
    handler.removeCallbacks(clipTick);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (elapsed >= 1000 || reps > 0 || held >= 1) {
      store.addSession(
          name + (clip ? " · clip" : ""),
          mode,
          clip ? "video" : "camera",
          (int) (elapsed / 1000),
          reps,
          held);
      if (show)
        new AlertDialog.Builder(this)
            .setTitle("Serie guardada.")
            .setMessage(
                (time ? (int) held + " segundos en postura" : reps + " " + engine.mode.unit)
                    + "\nTiempo "
                    + (clip ? "analizado" : "de sesión")
                    + ": "
                    + Ui.time(elapsed / 1000)
                    + "\n\nEstimación de postura, sin confirmar encestes.")
            .setPositiveButton("Seguir mejorando", null)
            .show();
    }
    engine = new Engine(engine.mode, hand.isChecked());
    elapsed = 0;
    modes.setEnabled(true);
    hand.setEnabled(engine.mode == Engine.Mode.SHOT);
    seek.setEnabled(clipLoaded);
    start.setText(clip ? "Analizar clip" : "Empezar serie");
    save.setEnabled(false);
    resetMetrics();
  }

  final Runnable ticker =
      new Runnable() {
        public void run() {
          if (destroyed) return;
          accrue();
          clockText.setText(Ui.time(elapsed / 1000));
          handler.postDelayed(this, 130);
        }
      };

  void chooseClip() {
    if (session) finishSession(false);
    pause();
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/*"),
        81);
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 81 && result == RESULT_OK && data != null && data.getData() != null)
      loadClip(data.getData());
  }

  void loadClip(Uri uri) {
    bindEpoch++;
    pause();
    clip = true;
    clipLoaded = false;
    activated = false;
    if (provider != null) provider.unbindAll();
    preview.setVisibility(View.GONE);
    clipImage.setVisibility(View.VISIBLE);
    empty.setVisibility(View.GONE);
    seek.setVisibility(View.VISIBLE);
    start.setEnabled(false);
    save.setEnabled(false);
    cursor = duration = elapsed = 0;
    seek.setProgress(0);
    cue.setText("Abriendo video local…");
    engine = new Engine(engine.mode, hand.isChecked());
    resetMetrics();
    int token = ++generation;
    decoder.execute(
        () -> {
          try {
            if (retriever != null) retriever.release();
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            long length =
                Long.parseLong(
                    Objects.requireNonNull(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
            if (length < 1000 || length > 300000) throw new IllegalArgumentException();
            runOnUiThread(
                () -> {
                  if (destroyed || token != generation) return;
                  duration = length;
                  clipLoaded = true;
                  seek.setEnabled(true);
                  start.setEnabled(true);
                  start.setText("Analizar clip");
                  cue.setText(
                      "Video listo · muestras de 5 cuadros/s del video. Pulsa Analizar clip.");
                  fps.setText("CLIP LOCAL · MUESTREO 5/S");
                  previewFrame();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (!destroyed && token == generation)
                    cue.setText("No se pudo abrir. Usa un MP4 compatible, de hasta 5 minutos.");
                });
          }
        });
  }

  Bitmap frame(long t) {
    Bitmap b;
    if (Build.VERSION.SDK_INT >= 27)
      b = retriever.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 720, 720);
    else {
      b = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
      if (b != null && Math.max(b.getWidth(), b.getHeight()) > 720) {
        float scale = 720f / Math.max(b.getWidth(), b.getHeight());
        Bitmap smaller =
            Bitmap.createScaledBitmap(
                b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true);
        if (b != smaller) b.recycle();
        b = smaller;
      }
    }
    return b;
  }

  void showBitmap(Bitmap b) {
    displayed = b;
    clipImage.setImageBitmap(b);
    overlay.clear();
  }

  void previewFrame() {
    if (!clipLoaded || destroyed) return;
    int token = generation;
    long t = Math.min(cursor, Math.max(0, duration - 50));
    decoder.execute(
        () -> {
          try {
            Bitmap b = frame(t);
            runOnUiThread(
                () -> {
                  if (b == null) return;
                  if (destroyed || token != generation) {
                    b.recycle();
                    return;
                  }
                  showBitmap(b);
                });
          } catch (Exception ignored) {
          }
        });
  }

  final Runnable clipTick = this::nextClip;

  void nextClip() {
    if (!active || !clip || destroyed) return;
    if (cursor >= duration) {
      pause();
      cue.setText("Revisión terminada. Guarda tus resultados.");
      return;
    }
    if (!busy.compareAndSet(false, true)) {
      handler.postDelayed(clipTick, 90);
      return;
    }
    int token = generation;
    long t = cursor;
    decoder.execute(
        () -> {
          try {
            Bitmap b = frame(t);
            runOnUiThread(
                () -> {
                  if (b == null) {
                    busy.set(false);
                    if (token == generation) {
                      pause();
                      cue.setText("No se pudo leer este fotograma.");
                    }
                    return;
                  }
                  if (destroyed || token != generation) {
                    b.recycle();
                    busy.set(false);
                    return;
                  }
                  showBitmap(b);
                  detector
                      .process(InputImage.fromBitmap(b, 0))
                      .addOnSuccessListener(
                          pose -> {
                            if (destroyed || token != generation) return;
                            overlay.display(pose, b.getWidth(), b.getHeight(), null);
                            result(
                                engine.update(
                                    Overlay.points(pose, b.getWidth(), b.getHeight()),
                                    t,
                                    b.getWidth() / (double) b.getHeight()));
                          })
                      .addOnFailureListener(
                          e -> {
                            if (!destroyed && token == generation) {
                              pause();
                              cue.setText("No se pudo analizar el fotograma. Puedes reintentar.");
                            }
                          })
                      .addOnCompleteListener(
                          task -> {
                            busy.set(false);
                            if (destroyed || token != generation) return;
                            elapsed += Math.min(STEP, duration - t);
                            cursor = t + STEP;
                            seek.setProgress((int) Math.min(1000, cursor * 1000 / duration));
                            save.setEnabled(true);
                            if (active) handler.postDelayed(clipTick, 30);
                          });
                });
          } catch (Exception e) {
            busy.set(false);
            runOnUiThread(
                () -> {
                  if (!destroyed && token == generation) {
                    pause();
                    cue.setText("Error al leer el clip. Abre otro archivo compatible.");
                  }
                });
          }
        });
  }

  protected void onResume() {
    super.onResume();
    if (requested
        && !clip
        && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      activated = true;
      bindCamera();
    }
  }

  protected void onStop() {
    bindEpoch++;
    if (session) finishSession(false);
    else pause();
    if (provider != null) provider.unbindAll();
    if (voice != null) voice.stop();
    super.onStop();
  }

  protected void onDestroy() {
    destroyed = true;
    bindEpoch++;
    generation++;
    handler.removeCallbacksAndMessages(null);
    if (provider != null) provider.unbindAll();
    cameraWorker.shutdown();
    detector.close();
    decoder.execute(
        () -> {
          try {
            if (retriever != null) retriever.release();
          } catch (Exception ignored) {
          }
        });
    decoder.shutdown();
    clipImage.setImageDrawable(null);
    displayed = null;
    if (voice != null) voice.shutdown();
    if (tone != null) tone.release();
    store.close();
    super.onDestroy();
  }
}
