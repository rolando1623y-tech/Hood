package com.panahoops.next;

import android.graphics.Bitmap;

/** Local template tracking of a manually calibrated hoop. Never a hoop recognizer. */
final class RimTracker {
  private int[] pixels;
  private float[] reference;
  private int w, h;
  private float cx, cy, pw, ph, energy;
  private int failures;

  static final class Result {
    final float dx, dy, confidence;
    final boolean locked;

    Result(float x, float y, float c, boolean l) {
      dx = x;
      dy = y;
      confidence = c;
      locked = l;
    }
  }

  void reset() {
    reference = null;
    failures = 0;
  }

  private float gray(int x, int y) {
    int rgb = pixels[Math.max(0, Math.min(h - 1, y)) * w + Math.max(0, Math.min(w - 1, x))];
    return ((rgb >> 16) & 255) * .299f + ((rgb >> 8) & 255) * .587f + (rgb & 255) * .114f;
  }

  float[] sample(float x, float y) {
    float[] a = new float[192];
    float mean = 0;
    for (int j = 0; j < 12; j++)
      for (int i = 0; i < 16; i++) {
        float v = gray(Math.round(x + (i / 15f - .5f) * pw), Math.round(y + (j / 11f - .5f) * ph));
        a[j * 16 + i] = v;
        mean += v;
      }
    mean /= a.length;
    for (int i = 0; i < a.length; i++) a[i] -= mean;
    return a;
  }

  Result update(Bitmap frame, float[] calibration) {
    w = frame.getWidth();
    h = frame.getHeight();
    if (pixels == null || pixels.length != w * h) pixels = new int[w * h];
    frame.getPixels(pixels, 0, w, 0, 0, w, h);
    if (reference == null) {
      cx = (calibration[0] + calibration[1]) * .5f * w;
      cy = calibration[2] * h;
      pw = (calibration[1] - calibration[0]) * w * 1.35f;
      ph = Math.max(16, pw * .55f);
      reference = sample(cx, cy);
      energy = 0;
      for (float a : reference) energy += a * a;
      return new Result(0, 0, 1, true);
    }
    if (energy < 192 * 15) return new Result(0, 0, 0, false);
    float best = -1, bx = cx, by = cy;
    int step = Math.max(1, Math.round(pw / 30));
    for (int y = -4; y <= 4; y++)
      for (int x = -4; x <= 4; x++) {
        float[] a = sample(cx + x * step, cy + y * step);
        float dot = 0, norm = 0;
        for (int i = 0; i < a.length; i++) {
          dot += a[i] * reference[i];
          norm += a[i] * a[i];
        }
        float score = norm < 1 ? 0 : dot / (float) Math.sqrt(norm * energy);
        if (score > best) {
          best = score;
          bx = cx + x * step;
          by = cy + y * step;
        }
      }
    if (best < .78f) {
      failures++;
      return new Result(0, 0, best, failures < 3);
    }
    failures = 0;
    float dx = (bx - cx) / w, dy = (by - cy) / h;
    cx = bx;
    cy = by;
    return new Result(dx, dy, best, true);
  }
}
