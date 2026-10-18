package com.panahoops.next;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.View;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w393dp-h851dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignTest {
  @Test
  public void renderActualNativeScreens() throws Exception {
    RuntimeEnvironment.getApplication().deleteDatabase("panahoops-next.db");
    ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity app = controller.get();
    for (int page : new int[] {0, 1, 3, 4, 5}) {
      app.showPage(page);
      View root = app.findViewById(android.R.id.content);
      int w = Ui.dp(app, 393), h = Ui.dp(app, 851);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, w, h);
      assertTrue(app.nav.getHeight() >= Ui.dp(app, 48));
      assertTrue(app.scroll.getHeight() > Ui.dp(app, 500));
      Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      root.draw(new Canvas(image));
      File dir = new File("build/qa");
      assertTrue(dir.exists() || dir.mkdirs());
      try (OutputStream out = new FileOutputStream(new File(dir, "screen-" + page + ".png"))) {
        image.compress(Bitmap.CompressFormat.PNG, 95, out);
      }
      image.recycle();
    }
    controller.pause().stop().destroy();
  }
}
