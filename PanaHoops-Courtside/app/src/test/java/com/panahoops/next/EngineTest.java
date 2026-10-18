package com.panahoops.next;

import static org.junit.Assert.*;

import com.panahoops.next.Engine.*;
import org.junit.Test;

public class EngineTest {
  static P[] pose() {
    P[] p = new P[33];
    for (int i = 0; i < 33; i++) p[i] = new P(.5, .5, .99);
    int[] ids = {11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28};
    double[][] xy = {
      {.42, .25}, {.58, .25}, {.35, .45}, {.65, .45}, {.3, .6}, {.7, .6}, {.45, .5}, {.55, .5},
      {.45, .7}, {.55, .7}, {.45, .92}, {.55, .92}
    };
    for (int i = 0; i < ids.length; i++) put(p, ids[i], xy[i][0], xy[i][1]);
    return p;
  }

  static void put(P[] p, int i, double x, double y) {
    p[i] = new P(x, y, .99);
  }

  static P[] squat(boolean down) {
    P[] p = pose();
    put(p, 12, down ? .35 : .4, down ? .25 : .2);
    put(p, 24, down ? .35 : .4, down ? .5 : .45);
    put(p, 26, down ? .55 : .4, down ? .65 : .7);
    put(p, 28, .4, down ? .85 : .93);
    return p;
  }

  static P[] shot(boolean extended) {
    P[] p = pose();
    put(p, 12, .55, .35);
    put(p, 24, .55, .65);
    put(p, 14, .55, extended ? .22 : .5);
    put(p, 16, extended ? .55 : .65, extended ? .06 : .38);
    return p;
  }

  static P[] pushup(boolean down) {
    P[] p = pose();
    put(p, 12, .25, .55);
    put(p, 24, .55, .55);
    put(p, 28, .85, .58);
    put(p, 14, down ? .35 : .25, .7);
    put(p, 16, down ? .48 : .25, down ? .55 : .88);
    return p;
  }

  static P[] jack(boolean open) {
    P[] p = pose();
    put(p, 11, .42, .3);
    put(p, 12, .58, .3);
    put(p, 15, open ? .25 : .35, open ? .12 : .65);
    put(p, 16, open ? .75 : .65, open ? .12 : .65);
    put(p, 27, open ? .23 : .46, .9);
    put(p, 28, open ? .77 : .54, .9);
    return p;
  }

  static class Rig {
    Engine e;
    long t = 0;

    Rig(Mode m) {
      e = new Engine(m, false);
    }

    Result hold(P[] p) {
      return hold(p, 8);
    }

    Result hold(P[] p, int n) {
      Result r = null;
      for (int i = 0; i < n; i++) {
        r = e.update(p, t, 1);
        t += 100;
      }
      return r;
    }
  }

  @Test
  public void stillnessAndMissingFramesNeverCount() {
    for (Mode mode : Mode.values()) {
      Rig r = new Rig(mode);
      r.hold(null, 20);
      r.hold(pose(), 40);
      assertEquals(mode.title, 0, r.e.count);
    }
  }

  @Test
  public void squatNeedsCompleteCycle() {
    Rig r = new Rig(Mode.SQUAT);
    r.hold(squat(true));
    r.hold(squat(false));
    assertEquals(0, r.e.count);
    r.hold(squat(true));
    r.hold(squat(false));
    assertEquals(1, r.e.count);
    r.hold(squat(false), 30);
    assertEquals(1, r.e.count);
  }

  @Test
  public void occlusionCancelsPartialCycle() {
    Rig r = new Rig(Mode.SQUAT);
    r.hold(squat(false));
    r.hold(squat(true));
    r.hold(null, 20);
    r.hold(squat(false));
    assertEquals(0, r.e.count);
  }

  @Test
  public void lowConfidenceDoesNotProduceReps() {
    Rig r = new Rig(Mode.SQUAT);
    r.hold(squat(false));
    P[] bad = squat(true);
    bad[26] = new P(.5, .6, .1);
    bad[25] = new P(.5, .6, .1);
    r.hold(bad);
    r.hold(squat(false));
    assertEquals(0, r.e.count);
  }

  @Test
  public void croppedFeetCannotCount() {
    Rig r = new Rig(Mode.SQUAT);
    r.hold(squat(false));
    P[] bad = squat(true);
    put(bad, 27, .5, 1.2);
    put(bad, 28, .5, 1.2);
    r.hold(bad);
    r.hold(squat(false));
    assertEquals(0, r.e.count);
  }

  @Test
  public void shotNeedsPreparationExtensionAndReload() {
    Rig r = new Rig(Mode.SHOT);
    r.hold(shot(true));
    assertEquals(0, r.e.count);
    r.hold(shot(false));
    r.hold(shot(true));
    assertEquals(1, r.e.count);
    r.hold(shot(true), 30);
    assertEquals(1, r.e.count);
    r.hold(shot(false), 16);
    r.hold(shot(true));
    assertEquals(2, r.e.count);
  }

  static P[] swap(P[] p) {
    for (int[] pair : new int[][] {{11, 12}, {13, 14}, {15, 16}, {23, 24}}) {
      P temp = p[pair[0]];
      p[pair[0]] = p[pair[1]];
      p[pair[1]] = temp;
    }
    return p;
  }

  @Test
  public void selectedArmIsRespected() {
    Rig r = new Rig(Mode.SHOT);
    r.e = new Engine(Mode.SHOT, true);
    r.hold(swap(shot(false)));
    r.hold(swap(shot(true)));
    assertEquals(1, r.e.count);
  }

  @Test
  public void standingArmMotionIsNotAPushup() {
    Rig r = new Rig(Mode.PUSHUP);
    r.hold(shot(false));
    r.hold(shot(true));
    assertEquals(0, r.e.count);
    r.hold(pushup(false));
    r.hold(pushup(true));
    r.hold(pushup(false));
    assertEquals(1, r.e.count);
  }

  @Test
  public void jackCountsTheReturn() {
    Rig r = new Rig(Mode.JACK);
    r.hold(jack(false));
    r.hold(jack(true));
    assertEquals(0, r.e.count);
    r.hold(jack(false));
    assertEquals(1, r.e.count);
  }

  static P[] shift(double x) {
    P[] p = pose();
    for (int i = 0; i < 33; i++) p[i] = new P(p[i].x + x, p[i].y, p[i].v);
    return p;
  }

  @Test
  public void lateralRequiresBothSidesAndIgnoresNoise() {
    Rig r = new Rig(Mode.LATERAL);
    r.hold(pose());
    r.hold(shift(-.02));
    r.hold(shift(.02));
    assertEquals(0, r.e.count);
    r.hold(shift(-.14));
    r.hold(shift(.14));
    assertEquals(1, r.e.count);
    r.e.recenter();
    r.hold(shift(-.14));
    assertEquals(1, r.e.count);
  }

  @Test
  public void stanceTimeStopsWhenBodyDisappears() {
    Rig r = new Rig(Mode.STANCE);
    P[] p = pose();
    put(p, 23, .38, .5);
    put(p, 24, .62, .5);
    put(p, 25, .48, .65);
    put(p, 26, .52, .65);
    put(p, 27, .35, .85);
    put(p, 28, .65, .85);
    r.hold(p, 20);
    assertTrue(r.e.hold > 1);
    double held = r.e.hold;
    r.hold(null, 30);
    assertEquals(held, r.e.hold, .001);
  }

  @Test
  public void plankRequiresHorizontalAlignment() {
    Rig r = new Rig(Mode.PLANK);
    r.hold(pose(), 20);
    assertEquals(0, r.e.hold, .001);
    r.hold(pushup(false), 20);
    assertTrue(r.e.hold > 1);
  }

  @Test
  public void lungeIsNotASquat() {
    Rig r = new Rig(Mode.LUNGE);
    r.hold(pose());
    r.hold(squat(true));
    r.hold(pose());
    assertEquals(0, r.e.count);
    P[] l = pose();
    put(l, 23, .35, .45);
    put(l, 24, .55, .5);
    put(l, 25, .45, .65);
    put(l, 26, .65, .68);
    put(l, 27, .25, .85);
    put(l, 28, .82, .88);
    r.hold(l);
    r.hold(pose());
    assertEquals(1, r.e.count);
  }

  @Test
  public void longGapsRequireNewPreparation() {
    Rig r = new Rig(Mode.SQUAT);
    r.hold(squat(false));
    r.hold(squat(true));
    r.t += 2000;
    r.hold(squat(false));
    assertEquals(0, r.e.count);
  }

  @Test
  public void geometryHandlesAspectAndDegenerateBones() {
    P a = new P(0, 0, 1), b = new P(0, 1, 1), c = new P(1, 1, 1);
    assertEquals(90, Engine.angle(a, b, c, 2), .001);
    assertTrue(Double.isNaN(Engine.angle(a, a, c, 1)));
  }
}
