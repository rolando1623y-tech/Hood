package com.panahoops.next;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.os.*;
import android.provider.Settings;
import java.time.LocalDate;

/** Explicitly-started, visible step-sensor recording service. No GPS or health uploads. */
public final class FitService extends Service implements SensorEventListener {
  public static final String STOP = "com.panahoops.next.STOP_FIT";
  private SensorManager manager;
  private Sensor stepSensor;
  private TrainingStore store;
  private android.content.SharedPreferences tracker;
  private boolean registered;
  private HandlerThread sensorThread;

  @Override public void onCreate() {
    super.onCreate();
    manager = (SensorManager) getSystemService(SENSOR_SERVICE);
    stepSensor = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    store = new TrainingStore(this);
    tracker = getSharedPreferences("fit.sensor", MODE_PRIVATE);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && STOP.equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (android.os.Build.VERSION.SDK_INT >= 29
        && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
      tracker.edit().putString("status", "Falta el permiso de actividad física").commit();
      stopSelf(); return START_NOT_STICKY;
    }
    if (stepSensor == null) {
      tracker.edit().putString("status", "No hay contador de pasos compatible").commit();
      stopSelf(); return START_NOT_STICKY;
    }
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel("fit_tracking", "Seguimiento FIT", NotificationManager.IMPORTANCE_LOW);
      ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Intent stop = new Intent(this, FitService.class).setAction(STOP);
    PendingIntent stopTap = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder n = Build.VERSION.SDK_INT >= 26
        ? new Notification.Builder(this, "fit_tracking") : new Notification.Builder(this);
    n.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("PanaHoops FIT · pasos")
        .setContentText("Seguimiento activo con el sensor de pasos")
        .setContentIntent(tap).setOngoing(true)
        .addAction(android.R.drawable.ic_media_pause, "Detener", stopTap);
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(418, n.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
    } else startForeground(418, n.build());
    tracker.edit().putBoolean("suspended",false).commit();
    if (!registered) {
      sensorThread=new HandlerThread("PanaHoops-step-counter", android.os.Process.THREAD_PRIORITY_BACKGROUND);
      sensorThread.start();
      String today=LocalDate.now().toString();
      tracker.edit().putLong("sessionStarted",System.currentTimeMillis())
          .putString("sessionDay",today).putLong("sessionBase",store.fitDay(today)[0]).commit();
      registered = manager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL,
          new Handler(sensorThread.getLooper()));
    }
    tracker.edit().putBoolean("active", registered)
        .putString("status", registered ? "Seguimiento activo" : "No se pudo activar el sensor").commit();
    if (!registered) stopSelf();
    return START_NOT_STICKY;
  }

  @Override public void onSensorChanged(SensorEvent event) {
    if (tracker.getBoolean("suspended",false) || event.sensor.getType() != Sensor.TYPE_STEP_COUNTER || event.values.length == 0) return;
    float f = event.values[0];
    if (!Float.isFinite(f) || f < 0 || f > Long.MAX_VALUE) return;
    long raw = (long) f;
    long boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    String day = LocalDate.now().toString();
    long timestamp = System.currentTimeMillis();
    String priorDay = tracker.getString("day", "");
    FitSampleState old = priorDay.isEmpty() ? null : new FitSampleState(
        tracker.getLong("boot", -1), priorDay, tracker.getLong("raw", 0),
        tracker.getLong("steps", 0), tracker.getLong("sampleTime", 0));
    FitSampleState next = FitCounter.accept(old, boot, day, raw, timestamp);
    int cadence=0;
    if(old!=null && day.equals(old.getDay()) && boot==old.getBoot()) {
      long dt=timestamp-old.getTimestampMillis();
      long ds=next.getSteps()-old.getSteps();
      if(dt>=5000 && dt<=90000 && ds>=0 && ds<=180) cadence=(int)(ds*60000L/dt);
    }
    if(!day.equals(tracker.getString("sessionDay",""))) {
      tracker.edit().putString("sessionDay",day).putLong("sessionBase",0).commit();
    }
    long sessionSteps=Math.max(0,next.getSteps()-tracker.getLong("sessionBase",0));
    // Shared preferences are committed synchronously so an abrupt process death
    // doesn't re-apply a previously received cumulative sensor event.
    if (!tracker.edit().putLong("boot", next.getBoot()).putString("day", next.getDay())
        .putLong("raw", next.getRaw()).putLong("steps", next.getSteps())
        .putLong("sampleTime", next.getTimestampMillis()).putInt("cadence",cadence)
        .putLong("sessionSteps",sessionSteps).commit()) return;
    store.saveFitSteps(day, next.getSteps());
  }
  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
  @Override public void onDestroy() {
    if (manager != null) manager.unregisterListener(this);
    if(sensorThread != null){sensorThread.quitSafely();sensorThread=null;}
    tracker.edit().putBoolean("active", false).putString("status", "Seguimiento detenido").commit();
    if (store != null) store.close();
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent) { return null; }
}
