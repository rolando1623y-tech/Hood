package com.panahoops.next;

import java.util.*;

/** Normalized image geometry, not 3D or a validated scoring referee. */
final class CourtEngine {
  static final class Box {
    final float x, y, w, h, score;
    final String label;

    Box(String l, float x, float y, float w, float h, float s) {
      label = l;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      score = s;
    }

    float cx() {
      return x + w / 2;
    }

    float cy() {
      return y + h / 2;
    }
  }

  static final class Player {
    final int id;
    Box box;
    long seen;
    String name, role = "Tirador";

    Player(int id, Box b, long t) {
      this.id = id;
      box = b;
      seen = t;
      name = "Jugador " + id;
    }
  }

  static final class Shot {
    final long time;
    int player;
    String result, source;

    Shot(long t, int p, String r, String s) {
      time = t;
      player = p;
      result = r;
      source = s;
    }
  }

  final List<Player> players = new ArrayList<>();
  final List<Shot> shots = new ArrayList<>();
  final ArrayDeque<Box> trail = new ArrayDeque<>();
  float left = .4f, right = .6f, rimY = .3f, slope = 0;
  boolean calibrated = false, above = false, crossed = false;
  int below = 0, nextId = 1, owner = 0, shotOwner = 0, pinned = 0;
  long lastBall = -1, armedAt = -1, coolUntil = 0, ownerAt = -1;
  Box ball;
  String status = "Calibra los dos extremos del aro";

  void calibrate(float x1, float y1, float x2, float y2) {
    if (x2 < x1) {
      float a = x1;
      x1 = x2;
      x2 = a;
      a = y1;
      y1 = y2;
      y2 = a;
    }
    if (x2 - x1 < .025f)
      throw new IllegalArgumentException("El aro es demasiado pequeño: acerca la cámara");
    left = x1;
    right = x2;
    rimY = (y1 + y2) / 2;
    slope = (y2 - y1) / (x2 - x1);
    calibrated = true;
    resetMotion();
  }

  float line(float x) {
    return rimY + slope * (x - (left + right) / 2);
  }

  void resetMotion() {
    above = false;
    crossed = false;
    below = 0;
    armedAt = -1;
    ball = null;
    lastBall = -1;
    owner = 0;
    shotOwner = 0;
    trail.clear();
  }

  void interrupt(long t) {
    if (above) finish(t, "Dudoso", "Pérdida de seguimiento");
    resetMotion();
  }

  void finish(long t, String r, String why) {
    shots.add(new Shot(System.currentTimeMillis(), shotOwner, r, why));
    coolUntil = t + 1600;
    above = false;
    crossed = false;
    below = 0;
    armedAt = -1;
    status = r + " · revisa y confirma";
  }

  void update(List<Box> boxes, long t) {
    Set<Integer> used = new HashSet<>();
    for (Box b : boxes)
      if (b.label.equals("person") && b.score >= .45f) {
        Player best = null;
        float dist = .15f;
        for (Player p : players)
          if (!used.contains(p.id) && t - p.seen < 800) {
            float d = (float) Math.hypot(b.cx() - p.box.cx(), b.cy() - p.box.cy());
            if (d < dist) {
              dist = d;
              best = p;
            }
          }
        if (best == null) {
          best = new Player(nextId++, b, t);
          players.add(best);
        } else {
          best.box = b;
          best.seen = t;
        }
        used.add(best.id);
      }
    // Expired IDs are never silently reused after an occlusion.
    players.removeIf(p -> t - p.seen > 30000);
    if (boxes.stream().filter(b -> b.label.equals("sports ball") && b.score >= .5f).count() > 1) {
      if (above) finish(t, "Dudoso", "Varios balones visibles");
      resetMotion();
      status = "Varios balones: usa uno por serie";
      return;
    }
    Box selected = null;
    float bestScore = -100;
    for (Box b : boxes)
      if (b.label.equals("sports ball") && b.score >= .3f) {
        float d = ball == null ? 0 : (float) Math.hypot(b.cx() - ball.cx(), b.cy() - ball.cy());
        if (ball != null && t - lastBall < 400 && d > .28f) continue;
        float rank = b.score - 2 * d;
        if (rank > bestScore) {
          selected = b;
          bestScore = rank;
        }
      }
    if (selected == null) {
      if (lastBall >= 0 && t - lastBall > 450) {
        if (above) finish(t, "Dudoso", "Balón oculto");
        ball = null;
        trail.clear();
        owner = 0;
      }
      if (t >= coolUntil) status = "Buscando balón · mantén el aro visible";
      return;
    }
    Box previous = ball;
    long gap = lastBall < 0 ? 0 : t - lastBall;
    if (gap > 450) {
      if (above) finish(t, "Dudoso", "Trayectoria incompleta");
      previous = null;
      owner = 0;
    }
    ball = selected;
    lastBall = t;
    trail.addLast(selected);
    while (trail.size() > 18) trail.removeFirst();
    if (!above) {
      float near = .18f;
      int holder = 0;
      for (Player p : players)
        if (t - p.seen < 400 && p.role.equals("Tirador")) {
          float d =
              (float) Math.hypot(ball.cx() - p.box.cx(), ball.cy() - (p.box.y + p.box.h * .28f));
          if (d < near) {
            near = d;
            holder = p.id;
          }
        }
      if (holder > 0) {
        owner = holder;
        ownerAt = t;
      } else if (ownerAt < 0 || t - ownerAt > 1500) owner = 0;
    }
    if (!calibrated || t < coolUntil) return;
    float width = right - left, signed = ball.cy() - line(ball.cx());
    if (!above && signed < -.25f * width && Math.abs(ball.cx() - (left + right) / 2) < 2 * width) {
      above = true;
      armedAt = t;
      shotOwner = pinned > 0 ? pinned : owner;
      crossed = false;
      below = 0;
      status = "Trayectoria sobre el aro";
    }
    if (!above) return;
    if (t - armedAt > 3500) {
      finish(t, "Dudoso", "Sin cruce visible");
      return;
    }
    if (previous != null) {
      float a = previous.cy() - line(previous.cx());
      if (a < 0 && signed >= 0 && gap <= 250) {
        float f = -a / (signed - a), x = previous.cx() + f * (ball.cx() - previous.cx());
        float margin = ball.w * .35f;
        if (x > left + margin && x < right - margin) {
          crossed = true;
          status = "Cruce aparente · verificando salida";
        } else if (x < left - ball.w || x > right + ball.w) {
          finish(t, "Fallo probable", "Cruce fuera del aro");
          return;
        } else {
          finish(t, "Dudoso", "Contacto con el borde");
          return;
        }
      }
    }
    if (crossed) {
      boolean under =
          signed > width * .22f
              && ball.cx() > left - ball.w * .2f
              && ball.cx() < right + ball.w * .2f;
      if (under) below++;
      else if (signed > width * .6f) {
        finish(t, "Dudoso", "Salida lateral u oclusión");
        return;
      }
      if (below >= 2)
        finish(t, "Enceste probable", "Cruce descendente y dos observaciones bajo el aro");
    }
  }
}
