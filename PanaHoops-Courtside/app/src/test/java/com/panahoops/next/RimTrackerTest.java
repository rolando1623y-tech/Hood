package com.panahoops.next;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Random;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RimTrackerTest {
 @Test public void followsSmallTranslation(){int w=320,h=240;int[] data=new int[w*h],shift=new int[w*h];Random r=new Random(4);for(int i=0;i<data.length;i++){int v=r.nextInt(256);data[i]=0xff000000|(v<<16)|(v<<8)|v;}for(int y=0;y<h;y++)for(int x=0;x<w;x++)shift[y*w+x]=data[Math.max(0,y-3)*w+Math.max(0,x-3)];Bitmap a=Bitmap.createBitmap(data,w,h,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(shift,w,h,Bitmap.Config.ARGB_8888);RimTracker tracker=new RimTracker();float[] calibration={.4f,.6f,.3f,0};tracker.update(a,calibration);RimTracker.Result result=tracker.update(b,calibration);assertTrue(result.locked);assertTrue(result.confidence>.95f);assertEquals(3f/w,result.dx,.001);assertEquals(3f/h,result.dy,.001);}
 @Test public void rejectsFeaturelessTemplate(){Bitmap a=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);a.eraseColor(0xff888888);RimTracker tracker=new RimTracker();float[] calibration={.4f,.6f,.3f,0};tracker.update(a,calibration);assertFalse(tracker.update(a,calibration).locked);}
}
