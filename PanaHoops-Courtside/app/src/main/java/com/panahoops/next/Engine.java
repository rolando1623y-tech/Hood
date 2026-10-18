package com.panahoops.next;

import java.util.Locale;

/** Deterministic movement state machines. No Android, storage or network dependency. */
public final class Engine {
  public enum Mode {
    SHOT("Gesto de tiro", "gestos", "De perfil · brazo dominante visible", false),
    SQUAT("Sentadilla", "repeticiones", "De perfil · cuerpo completo", false),
    PUSHUP("Flexión", "repeticiones", "De perfil · hombros, cadera y pies", false),
    JACK("Jumping jack", "repeticiones", "De frente · manos y pies visibles", false),
    LATERAL("Desplazamiento lateral", "cambios", "De frente · cámara fija", false),
    STANCE("Posición defensiva", "segundos", "De frente · ambas piernas visibles", true),
    LUNGE("Zancada", "repeticiones", "De perfil · ambas piernas visibles", false),
    PLANK("Plancha", "segundos", "De perfil · hombros, cadera y pies", true);
    public final String title, unit, view;
    public final boolean timed;

    Mode(String t, String u, String v, boolean b) {
      title = t;
      unit = u;
      view = v;
      timed = b;
    }

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Mode from(String s) {
      try {
        return valueOf(s.toUpperCase(Locale.ROOT));
      } catch (Exception e) {
        return SHOT;
      }
    }
  }

  public static final class P {
    public final double x, y, v;

    public P(double x, double y, double v) {
      this.x = x;
      this.y = y;
      this.v = v;
    }
  }

  public static final class Result {
    public final boolean visible, rep;
    public final int count;
    public final double hold, knee, elbow, confidence;
    public final String phase, cue;

    Result(
        boolean v, boolean r, int n, double h, double k, double e, double c, String p, String q) {
      visible = v;
      rep = r;
      count = n;
      hold = h;
      knee = k;
      elbow = e;
      confidence = c;
      phase = p;
      cue = q;
    }
  }

  public final Mode mode;
  public final boolean left;
  public int count = 0;
  public double hold = 0, anchor = Double.NaN;
  private String phase = "waiting", key = "";
  private long since = 0, last = -1, visibleAt = -1, repAt = -10000;
  private double sk = Double.NaN, se = Double.NaN;
  private int side = 0, lastShoulder = -1;
  private boolean lost = true;

  public Engine(Mode m, boolean l) {
    mode = m;
    left = l;
  }

  public void recenter() {
    phase = "waiting";
    key = "";
    sk = se = Double.NaN;
    anchor = Double.NaN;
    side = 0;
    last = -1;
    lost = true;
  }

  static boolean ok(P p) {
    return p != null
        && Double.isFinite(p.x)
        && Double.isFinite(p.y)
        && p.v >= .55
        && p.x >= 0
        && p.x <= 1
        && p.y >= 0
        && p.y <= 1;
  }

  static boolean all(P[] p, int... ids) {
    if (p == null || p.length != 33) return false;
    for (int i : ids) if (!ok(p[i])) return false;
    return true;
  }

  public static double angle(P a, P b, P c, double ratio) {
    if (a == null || b == null || c == null) return Double.NaN;
    double x = (a.x - b.x) * ratio,
        y = a.y - b.y,
        u = (c.x - b.x) * ratio,
        v = c.y - b.y,
        d = Math.hypot(x, y) * Math.hypot(u, v);
    return d < 1e-7
        ? Double.NaN
        : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (x * u + y * v) / d))));
  }

  private boolean stable(String k, boolean condition, long t, int ms) {
    if (!condition) {
      if (key.equals(k)) key = "";
      return false;
    }
    if (!key.equals(k)) {
      key = k;
      since = t;
      return false;
    }
    return t - since >= ms;
  }

  private boolean award(long t) {
    key = "";
    if (t - repAt < 650) return false;
    repAt = t;
    count++;
    return true;
  }

  private double smooth(double old, double value, long dt) {
    return !Double.isFinite(value)
        ? Double.NaN
        : Double.isNaN(old) ? value : old + (1 - Math.exp(-Math.max(1, dt) / 90.0)) * (value - old);
  }

  public Result update(P[] p, long t, double ratio) {
    long gap = last < 0 ? 0 : t - last, dt = Math.max(0, Math.min(gap, 250));
    if (gap > 700 || gap < 0) recenter();
    last = t;
    int s = left ? 11 : 12,
        e = left ? 13 : 14,
        w = left ? 15 : 16,
        h = left ? 23 : 24,
        k = left ? 25 : 26,
        a = left ? 27 : 28;
    if (mode != Mode.SHOT
        && !all(p, s, h, k, a)
        && all(p, s == 11 ? 12 : 11, h == 23 ? 24 : 23, k == 25 ? 26 : 25, a == 27 ? 28 : 27)) {
      s = s == 11 ? 12 : 11;
      e = e == 13 ? 14 : 13;
      w = w == 15 ? 16 : 15;
      h = h == 23 ? 24 : 23;
      k = k == 25 ? 26 : 25;
      a = a == 27 ? 28 : 27;
    }
    int[] ids =
        switch (mode) {
          case SHOT -> new int[] {s, e, w, h};
          case PUSHUP -> new int[] {s, e, w, h, a};
          case SQUAT -> new int[] {s, h, k, a};
          case PLANK -> new int[] {s, h, a};
          case JACK -> new int[] {11, 12, 15, 16, 23, 24, 27, 28};
          default -> new int[] {11, 12, 23, 24, 25, 26, 27, 28};
        };
    if (!all(p, ids)) {
      if (visibleAt < 0 || t - visibleAt > 400) recenter();
      return new Result(
          false,
          false,
          count,
          hold,
          Double.NaN,
          Double.NaN,
          0,
          "Sin seguimiento",
          "Muestra las articulaciones del ejercicio y busca buena luz.");
    }
    if (lost || (mode != Mode.SHOT && lastShoulder != s)) {
      phase = "waiting";
      key = "";
      sk = se = Double.NaN;
      anchor = Double.NaN;
      side = 0;
      lost = false;
    }
    lastShoulder = s;
    visibleAt = t;
    double confidence = 0;
    for (int i : ids) confidence += p[i].v;
    confidence /= ids.length;
    sk = smooth(sk, all(p, h, k, a) ? angle(p[h], p[k], p[a], ratio) : Double.NaN, dt);
    se = smooth(se, all(p, s, e, w) ? angle(p[s], p[e], p[w], ratio) : Double.NaN, dt);
    double torso = Math.max(.08, Math.hypot((p[s].x - p[h].x) * ratio, p[s].y - p[h].y));
    boolean horizontal = Math.abs(p[s].x - p[h].x) * ratio > Math.abs(p[s].y - p[h].y) * 1.15,
        rep = false;
    String cue = "Muévete con control.";
    switch (mode) {
      case SHOT:
        boolean prep = se < 120 && p[w].y > p[s].y - torso * .25 && p[w].y < p[h].y,
            extension = se > 145 && p[w].y < p[s].y - torso * .25;
        if (phase.equals("waiting") && stable("ready", prep, t, 150)) phase = "ready";
        else if (phase.equals("ready") && stable("release", extension, t, 100)) {
          rep = award(t);
          phase = "released";
        } else if (phase.equals("released")
            && stable("reload", p[w].y > p[s].y - torso * .1, t, 150)) phase = "waiting";
        cue =
            phase.equals("ready")
                ? "Extiende el brazo de tiro hacia arriba."
                : phase.equals("released")
                    ? "Gesto registrado. Baja el brazo y prepara otro."
                    : "Mano cerca del hombro y codo flexionado para preparar el gesto.";
        break;
      case SQUAT:
        if (p[s].y >= p[h].y - .04) {
          recenter();
          cue = "Ponte de perfil y muestra el torso.";
          break;
        }
        if (phase.equals("waiting") && stable("stand", sk > 155, t, 150)) phase = "up";
        else if (phase.equals("up") && stable("down", sk < 110, t, 150)) phase = "down";
        else if (phase.equals("down") && stable("rise", sk > 155, t, 150)) {
          rep = award(t);
          phase = "up";
        }
        cue =
            phase.equals("down")
                ? "Regresa de pie con control."
                : "Empieza de pie, baja a un rango cómodo y vuelve.";
        break;
      case PUSHUP:
        if (!horizontal) {
          recenter();
          cue = "Colócate de perfil, en posición de flexión.";
          break;
        }
        if (phase.equals("waiting") && stable("pressStart", se > 150, t, 150)) phase = "up";
        else if (phase.equals("up") && stable("pressDown", se < 100, t, 150)) phase = "down";
        else if (phase.equals("down") && stable("pressEnd", se > 150, t, 150)) {
          rep = award(t);
          phase = "up";
        }
        cue =
            phase.equals("down")
                ? "Empuja y vuelve a extender los brazos."
                : "Baja con control y mantén el cuerpo alineado.";
        break;
      case JACK:
        double spread = Math.abs(p[27].x - p[28].x) / Math.max(.06, Math.abs(p[11].x - p[12].x));
        boolean closed = spread < 1.35 && p[15].y > p[11].y && p[16].y > p[12].y,
            open = spread > 1.7 && p[15].y < p[11].y - .06 && p[16].y < p[12].y - .06;
        if (phase.equals("waiting") && stable("startJack", closed, t, 150)) phase = "closed";
        else if (phase.equals("closed") && stable("openJack", open, t, 150)) phase = "open";
        else if (phase.equals("open") && stable("closeJack", closed, t, 150)) {
          rep = award(t);
          phase = "closed";
        }
        cue =
            phase.equals("open")
                ? "Junta los pies y baja los brazos."
                : "Abre los pies y eleva los brazos.";
        break;
      case LATERAL:
        double x = (p[23].x + p[24].x) / 2;
        if (Double.isNaN(anchor)) anchor = x;
        int next = x < anchor - .1 ? -1 : x > anchor + .1 ? 1 : 0;
        if (next != 0 && stable("side" + next, true, t, 150)) {
          if (side != 0 && next != side) rep = award(t);
          side = next;
          phase = next < 0 ? "left" : "right";
        } else if (next == 0) key = "";
        cue = "Cruza a ambos lados de la posición inicial. Mantén la cámara fija.";
        break;
      case STANCE:
        double kl = angle(p[23], p[25], p[27], ratio), kr = angle(p[24], p[26], p[28], ratio);
        boolean bent = kl > 85 && kl < 155 && kr > 85 && kr < 155;
        if (stable("hold", bent, t, 350)) {
          hold += dt / 1000.0;
          phase = "holding";
        } else phase = "waiting";
        cue =
            bent
                ? "Posición detectada. Respira y mantén el control."
                : "Flexiona las rodillas en una posición cómoda.";
        break;
      case LUNGE:
        double l = angle(p[23], p[25], p[27], ratio), r = angle(p[24], p[26], p[28], ratio);
        boolean up = l > 155 && r > 155,
            down = Math.min(l, r) < 115 && Math.abs(p[27].x - p[28].x) * ratio > torso * .65;
        if (phase.equals("waiting") && stable("lungeStart", up, t, 150)) phase = "up";
        else if (phase.equals("up") && stable("lungeDown", down, t, 150)) phase = "down";
        else if (phase.equals("down") && stable("lungeEnd", up, t, 150)) {
          rep = award(t);
          phase = "up";
        }
        cue =
            phase.equals("down")
                ? "Vuelve a la posición de pie con control."
                : "Separa los apoyos, flexiona una pierna y vuelve de pie.";
        break;
      case PLANK:
        boolean aligned = horizontal && angle(p[s], p[h], p[a], ratio) > 155;
        if (stable("plank", aligned, t, 350)) {
          hold += dt / 1000.0;
          phase = "holding";
        } else phase = "waiting";
        cue =
            aligned
                ? "Alineación detectada. Respira con normalidad."
                : "De perfil: alinea hombros, cadera y pies.";
        break;
    }
    String display =
        switch (phase) {
          case "up" -> "Arriba";
          case "down" -> "Abajo";
          case "ready" -> "Preparado";
          case "released" -> "Extensión";
          case "open" -> "Abierto";
          case "closed" -> "Cerrado";
          case "left" -> "Lado izquierdo";
          case "right" -> "Lado derecho";
          case "holding" -> "Manteniendo";
          default -> "Preparación";
        };
    return new Result(
        true, rep, count, hold, sk, se, Math.max(0, Math.min(1, confidence)), display, cue);
  }
}
