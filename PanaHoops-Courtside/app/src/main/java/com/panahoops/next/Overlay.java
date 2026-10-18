package com.panahoops.next;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.google.mlkit.vision.pose.*;

final class Overlay extends View {
  static final int[][] EDGES = {
    {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16}, {11, 23}, {12, 24}, {23, 24}, {23, 25},
    {25, 27}, {24, 26}, {26, 28}, {27, 29}, {29, 31}, {28, 30}, {30, 32}
  };
  final Paint p = new Paint(3);
  float[] xy, confidence;
  boolean skeleton = true;

  Overlay(Context c) {
    super(c);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  static Engine.P[] points(Pose pose, int w, int h) {
    if (pose.getAllPoseLandmarks().isEmpty()) return null;
    Engine.P[] out = new Engine.P[33];
    for (PoseLandmark l : pose.getAllPoseLandmarks())
      out[l.getLandmarkType()] =
          new Engine.P(l.getPosition().x / w, l.getPosition().y / h, l.getInFrameLikelihood());
    return out;
  }

  void clear() {
    xy = confidence = null;
    invalidate();
  }

  void display(Pose pose, int w, int h, Matrix transform) {
    if (pose.getAllPoseLandmarks().isEmpty()) {
      clear();
      return;
    }
    xy = new float[66];
    confidence = new float[33];
    for (PoseLandmark l : pose.getAllPoseLandmarks()) {
      int i = l.getLandmarkType();
      xy[2 * i] = l.getPosition().x;
      xy[2 * i + 1] = l.getPosition().y;
      confidence[i] = l.getInFrameLikelihood();
    }
    if (transform != null) transform.mapPoints(xy);
    else {
      float scale = Math.min(getWidth() / (float) w, getHeight() / (float) h),
          ox = (getWidth() - w * scale) / 2,
          oy = (getHeight() - h * scale) / 2;
      for (int i = 0; i < 33; i++) {
        xy[2 * i] = xy[2 * i] * scale + ox;
        xy[2 * i + 1] = xy[2 * i + 1] * scale + oy;
      }
    }
    invalidate();
  }

  boolean shown(int i) {
    return confidence != null
        && confidence[i] >= .55
        && Float.isFinite(xy[2 * i])
        && xy[2 * i] >= 0
        && xy[2 * i] <= getWidth()
        && xy[2 * i + 1] >= 0
        && xy[2 * i + 1] <= getHeight();
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (!skeleton || xy == null) return;
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0xFF81B4FF);
    p.setStrokeWidth(Ui.dp(getContext(), 2.5f));
    p.setStrokeCap(Paint.Cap.ROUND);
    for (int[] e : EDGES)
      if (shown(e[0]) && shown(e[1]))
        c.drawLine(xy[2 * e[0]], xy[2 * e[0] + 1], xy[2 * e[1]], xy[2 * e[1] + 1], p);
    p.setStyle(Paint.Style.FILL);
    for (int i = 11; i < 33; i++)
      if (shown(i)) {
        p.setColor(i == 15 || i == 16 || i == 25 || i == 26 ? Ui.ORANGE : Ui.WHITE);
        c.drawCircle(xy[2 * i], xy[2 * i + 1], Ui.dp(getContext(), 3.4f), p);
      }
  }
}
