package com.panahoops.next;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Ui {
  static final int BG = 0xFFF3F1E9,
      INK = 0xFF1B271F,
      BLUE = 0xFF41632B,
      ORANGE = 0xFFC7F76A,
      MUTED = 0xFF687568,
      LINE = 0xFFD8E1D3,
      PALE = 0xFFE9F5DA,
      WHITE = -1,
      DARK = 0xFF161F19;

  static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static LinearLayout col(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  static LinearLayout row(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  static void pad(View v, int l, int t, int r, int b) {
    Context c = v.getContext();
    v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
  }

  static TextView text(Context c, String s, float sp, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(s);
    v.setTextColor(color);
    v.setTextSize(sp);
    v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(c, 2), 1);
    v.setFontFeatureSettings("tnum");
    return v;
  }

  static TextView title(Context c, String s, float sp) {
    TextView v = text(c, s, sp, INK, true);
    v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    v.setLineSpacing(0, .98f);
    return v;
  }

  static TextView label(Context c, String s, int color) {
    TextView v = text(c, s, 10, color, true);
    v.setLetterSpacing(.12f);
    return v;
  }

  static GradientDrawable shape(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static void surface(View v, int bg, int radius) {
    v.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(bg == BLUE ? 0x33FFFFFF : 0x142456F5),
            shape(v.getContext(), bg, radius),
            null));
    v.setClickable(true);
    v.setFocusable(true);
  }

  static LinearLayout card(Context c) {
    LinearLayout v = col(c);
    GradientDrawable d = shape(c, WHITE, 24);
    d.setStroke(dp(c, 1), LINE);
    v.setBackground(d);
    pad(v, 20, 20, 20, 20);
    return v;
  }

  static Button button(Context c, String s, int bg, int fg, Runnable action) {
    Button b = new Button(c);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(fg);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setMinHeight(dp(c, 50));
    b.setMinimumHeight(dp(c, 50));
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setStateListAnimator(null);
    pad(b, 15, 11, 15, 11);
    surface(b, bg, 16);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  static void add(LinearLayout root, View v) {
    root.addView(v, new LinearLayout.LayoutParams(-1, -2));
  }

  static void gap(LinearLayout root, int n) {
    View v = new View(root.getContext());
    root.addView(v, new LinearLayout.LayoutParams(1, dp(root.getContext(), n)));
  }

  static EditText edit(Context c, String hint) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setTextSize(14);
    e.setSingleLine(true);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setBackground(shape(c, WHITE, 14));
    pad(e, 14, 13, 14, 13);
    return e;
  }

  static Spinner spinner(Context c, String[] values, int index) {
    Spinner s = new Spinner(c);
    s.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, values));
    s.setSelection(Math.max(0, index));
    s.setBackground(shape(c, WHITE, 14));
    s.setMinimumHeight(dp(c, 48));
    pad(s, 8, 5, 8, 5);
    return s;
  }

  static View iconButton(Context c, String icon, String description, int bg, int fg, Runnable run) {
    FrameLayout f = new FrameLayout(c);
    surface(f, bg, 100);
    f.setContentDescription(description);
    Icon v = new Icon(c, icon, fg);
    f.addView(v, new FrameLayout.LayoutParams(dp(c, 23), dp(c, 23), Gravity.CENTER));
    f.setOnClickListener(w -> run.run());
    return f;
  }

  static void insets(Activity a, View root, boolean dark) {
    insets(a.getWindow(), root, dark);
  }

  static void insets(Window window, View root, boolean dark) {
    window.setStatusBarColor(dark ? DARK : BG);
    window.setNavigationBarColor(dark ? DARK : BG);
    int flags =
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    if (!dark) {
      flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
      if (android.os.Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    }
    window.getDecorView().setSystemUiVisibility(flags);
    int l = root.getPaddingLeft(),
        t = root.getPaddingTop(),
        r = root.getPaddingRight(),
        b = root.getPaddingBottom();
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              l + i.getSystemWindowInsetLeft(),
              t + i.getSystemWindowInsetTop(),
              r + i.getSystemWindowInsetRight(),
              b + i.getSystemWindowInsetBottom());
          return i.consumeSystemWindowInsets();
        });
    root.requestApplyInsets();
  }

  static String time(long s) {
    return String.format(Locale.ROOT, "%02d:%02d", Math.max(0, s) / 60, Math.max(0, s) % 60);
  }

  static void toast(Context c, String s) {
    Toast.makeText(c, s, Toast.LENGTH_LONG).show();
  }

  static String read(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) {
      if (out.size() + n > max) throw new IOException("Archivo demasiado grande");
      out.write(b, 0, n);
    }
    return out.toString(StandardCharsets.UTF_8.name());
  }

  static Dialog sheet(Activity a, String title, LinearLayout inner) {
    Dialog d = new Dialog(a);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    ScrollView scroll = new ScrollView(a);
    LinearLayout root = col(a);
    pad(root, 24, 23, 24, 35);
    root.setBackground(shape(a, BG, 28));
    LinearLayout header = row(a);
    header.addView(title(a, title, 30), new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        iconButton(a, "close", "Cerrar", WHITE, INK, d::dismiss),
        new LinearLayout.LayoutParams(dp(a, 43), dp(a, 43)));
    add(root, header);
    gap(root, 23);
    add(root, inner);
    scroll.addView(root);
    d.setContentView(scroll);
    d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    d.getWindow().setGravity(Gravity.BOTTOM);
    d.show();
    d.getWindow().setLayout(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * .87));
    return d;
  }

  static final class Icon extends View {
    final String type;
    final int color;
    final Paint p = new Paint(3);

    Icon(Context c, String t, int color) {
      super(c);
      type = t;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      c.save();
      c.scale(getWidth() / 24f, getHeight() / 24f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.6f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      Path q = new Path();
      switch (type) {
        case "home":
          q.moveTo(3, 10);
          q.lineTo(12, 3);
          q.lineTo(21, 10);
          q.lineTo(21, 21);
          q.lineTo(3, 21);
          q.close();
          c.drawPath(q, p);
          c.drawRect(9, 13, 15, 21, p);
          break;
        case "train":
          c.drawLine(5, 5, 19, 19, p);
          c.drawLine(3, 7, 7, 3, p);
          c.drawLine(1, 9, 9, 1, p);
          c.drawLine(15, 21, 21, 15, p);
          c.drawLine(17, 23, 23, 17, p);
          break;
        case "lens":
          c.drawRoundRect(2, 5, 22, 21, 4, 4, p);
          c.drawCircle(12, 13, 4.5f, p);
          c.drawLine(8, 2, 16, 2, p);
          break;
        case "court":
          c.drawRoundRect(3, 2, 21, 22, 2, 2, p);
          c.drawLine(3, 12, 21, 12, p);
          c.drawCircle(12, 12, 3, p);
          c.drawRect(8, 2, 16, 6, p);
          c.drawRect(8, 18, 16, 22, p);
          break;
        case "chart":
          c.drawLine(3, 3, 3, 21, p);
          c.drawLine(3, 21, 22, 21, p);
          q.moveTo(7, 16);
          q.lineTo(12, 9);
          q.lineTo(16, 12);
          q.lineTo(21, 4);
          c.drawPath(q, p);
          break;
        case "settings":
          c.drawCircle(12, 12, 4, p);
          for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, 12, 12);
            c.drawLine(12, 2, 12, 5, p);
            c.restore();
          }
          break;
        case "close":
          c.drawLine(5, 5, 19, 19, p);
          c.drawLine(19, 5, 5, 19, p);
          break;
        case "back":
          q.moveTo(14, 4);
          q.lineTo(6, 12);
          q.lineTo(14, 20);
          c.drawPath(q, p);
          c.drawLine(6, 12, 22, 12, p);
          break;
        case "flip":
          c.drawArc(3, 3, 21, 21, 220, 260, false, p);
          q.moveTo(4, 3);
          q.lineTo(3, 9);
          q.lineTo(9, 8);
          c.drawPath(q, p);
          break;
        default:
          q.moveTo(4, 12);
          q.lineTo(20, 12);
          q.moveTo(13, 5);
          q.lineTo(20, 12);
          q.lineTo(13, 19);
          c.drawPath(q, p);
      }
      c.restore();
    }
  }
}
