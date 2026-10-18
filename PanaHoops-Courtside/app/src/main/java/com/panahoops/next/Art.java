package com.panahoops.next;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.IntConsumer;

final class Art extends View {
  final Paint p = new Paint(3);
  final String type;

  Art(Context c, String type) {
    super(c);
    this.type = type;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    c.scale(getWidth() / 360f, getHeight() / 240f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1);
    p.setColor(0x20FFFFFF);
    for (int i = 0; i < 9; i++) {
      c.drawLine(0, 30 * i, 360, 30 * i, p);
      c.drawLine(50 * i - 100, 240, 50 * i + 40, 0, p);
    }
    c.drawCircle(275, 95, 100, p);
    c.drawCircle(275, 95, 74, p);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Ui.ORANGE);
    c.drawCircle(242, 37, 23, p);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0x55311B07);
    p.setStrokeWidth(1.5f);
    c.drawLine(220, 33, 264, 42, p);
    c.drawLine(247, 15, 237, 59, p);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Ui.WHITE);
    c.drawCircle(198, 84, 17, p);
    Path b = new Path();
    b.moveTo(184, 100);
    b.lineTo(208, 99);
    b.lineTo(223, 146);
    b.lineTo(196, 157);
    b.lineTo(174, 131);
    b.close();
    c.drawPath(b, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeWidth(14);
    Path arms = new Path();
    arms.moveTo(186, 110);
    arms.lineTo(170, 79);
    arms.lineTo(208, 43);
    arms.moveTo(207, 106);
    arms.lineTo(226, 78);
    arms.lineTo(249, 41);
    c.drawPath(arms, p);
    p.setColor(Ui.ORANGE);
    p.setStrokeWidth(16);
    Path legs = new Path();
    legs.moveTo(199, 151);
    legs.lineTo(159, 176);
    legs.lineTo(134, 218);
    legs.moveTo(214, 149);
    legs.lineTo(227, 183);
    legs.lineTo(267, 207);
    c.drawPath(legs, p);
    p.setColor(0xFFBBD2FF);
    p.setStrokeWidth(9);
    c.drawLine(127, 222, 145, 222, p);
    c.drawLine(265, 211, 281, 211, p);
    c.restore();
  }

  static final class Court extends View {
    final Paint p = new Paint(3);
    final float[][] xy = {
      {180, 148}, {40, 72}, {83, 203}, {180, 251}, {277, 203}, {320, 72}, {180, 87}
    };
    int[] attempts = new int[7], made = new int[7];
    int selected = 0;
    IntConsumer action;

    Court(Context c) {
      super(c);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(List<Store.Shot> shots, int selected) {
      Arrays.fill(attempts, 0);
      Arrays.fill(made, 0);
      for (Store.Shot s : shots) {
        attempts[s.zone]++;
        if (s.made) made[s.zone]++;
      }
      this.selected = selected;
      invalidate();
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      c.save();
      float scale = Math.min(getWidth() / 360f, getHeight() / 295f);
      c.translate((getWidth() - 360 * scale) / 2, (getHeight() - 295 * scale) / 2);
      c.scale(scale, scale);
      p.setStyle(Paint.Style.FILL);
      p.setColor(Ui.PALE);
      c.drawRoundRect(0, 0, 360, 295, 23, 23, p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.3f);
      p.setColor(0xFFB2C1F8);
      c.drawRect(14, 16, 346, 279, p);
      c.drawRect(126, 16, 234, 138, p);
      c.drawRect(147, 16, 213, 74, p);
      c.drawArc(68, -87, 292, 199, 0, 180, false, p);
      c.drawArc(126, 84, 234, 192, 0, 180, false, p);
      c.drawCircle(180, 49, 8, p);
      c.drawLine(163, 31, 197, 31, p);
      c.drawLine(180, 31, 180, 41, p);
      for (int i = 0; i < 7; i++) {
        float x = xy[i][0], y = xy[i][1];
        p.setStyle(Paint.Style.FILL);
        p.setColor(i == selected ? Ui.BLUE : Ui.WHITE);
        c.drawCircle(x, y, 23, p);
        p.setColor(i == selected ? Ui.WHITE : Ui.BLUE);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(13);
        c.drawText(
            attempts[i] == 0 ? "—" : Math.round(made[i] * 100f / attempts[i]) + "%", x, y, p);
        p.setTextSize(7);
        c.drawText(made[i] + "/" + attempts[i], x, y + 12, p);
      }
      c.restore();
    }

    public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_UP) {
        float scale = Math.min(getWidth() / 360f, getHeight() / 295f),
            x = (e.getX() - (getWidth() - 360 * scale) / 2) / scale,
            y = (e.getY() - (getHeight() - 295 * scale) / 2) / scale;
        for (int i = 0; i < 7; i++)
          if (Math.hypot(x - xy[i][0], y - xy[i][1]) < 33) {
            performClick();
            if (action != null) action.accept(i);
            break;
          }
      }
      return true;
    }

    public boolean performClick() {
      super.performClick();
      return true;
    }
  }

  static final class Chart extends View {
    final int[] minutes = new int[7];
    final String[] days = new String[7];
    final Paint p = new Paint(3);

    Chart(Context c, List<Store.Session> sessions) {
      super(c);
      LocalDate now = LocalDate.now();
      StringBuilder description = new StringBuilder("Minutos por día: ");
      for (int i = 0; i < 7; i++) {
        LocalDate d = now.minusDays(6 - i);
        long seconds = 0;
        for (Store.Session s : sessions)
          if (Instant.ofEpochMilli(s.date).atZone(ZoneId.systemDefault()).toLocalDate().equals(d))
            seconds += s.seconds;
        minutes[i] = Math.round(seconds / 60f);
        days[i] =
            d.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("es")).replace(".", "");
        description.append(days[i]).append(' ').append(minutes[i]).append(". ");
      }
      setContentDescription(description);
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float slot = getWidth() / 7f,
          bottom = getHeight() - Ui.dp(getContext(), 28),
          top = Ui.dp(getContext(), 25);
      int max = 1;
      for (int n : minutes) max = Math.max(max, n);
      p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
      p.setTextAlign(Paint.Align.CENTER);
      for (int i = 0; i < 7; i++) {
        float x = slot * (i + .5f), h = Math.max(3, (bottom - top) * minutes[i] / max);
        p.setColor(i == 6 ? Ui.BLUE : 0xFF9DB6FF);
        c.drawRoundRect(x - slot * .24f, bottom - h, x + slot * .24f, bottom, 8, 8, p);
        p.setTextSize(Ui.dp(getContext(), 10));
        p.setColor(Ui.MUTED);
        c.drawText(days[i], x, getHeight() - Ui.dp(getContext(), 5), p);
        p.setColor(Ui.INK);
        c.drawText("" + minutes[i], x, bottom - h - Ui.dp(getContext(), 8), p);
      }
    }
  }
}
