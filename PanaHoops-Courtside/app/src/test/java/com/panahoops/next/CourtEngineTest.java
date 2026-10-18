package com.panahoops.next;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CourtEngineTest {
 CourtEngine e(){CourtEngine e=new CourtEngine();e.calibrate(.4f,.3f,.6f,.3f);return e;}
 void ball(CourtEngine e,float x,float y,long t){e.update(Arrays.asList(new CourtEngine.Box("sports ball",x-.01f,y-.01f,.02f,.02f,.9f)),t);}
 @Test public void descendingCrossNeedsTwoBelow(){CourtEngine e=e();ball(e,.5f,.20f,0);ball(e,.5f,.28f,100);ball(e,.5f,.32f,200);assertEquals(0,e.shots.size());ball(e,.5f,.36f,300);ball(e,.5f,.40f,400);assertEquals("Enceste probable",e.shots.get(0).result);}
 @Test public void lostBallIsUncertain(){CourtEngine e=e();ball(e,.5f,.20f,0);e.update(Collections.emptyList(),500);assertEquals("Dudoso",e.shots.get(0).result);}
 @Test public void outsideCrossIsMiss(){CourtEngine e=e();ball(e,.67f,.20f,0);ball(e,.67f,.32f,100);assertEquals("Fallo probable",e.shots.get(0).result);}
 @Test public void noCalibrationNoShot(){CourtEngine e=new CourtEngine();ball(e,.5f,.20f,0);ball(e,.5f,.4f,100);assertTrue(e.shots.isEmpty());}
 @Test public void upwardPassNotMake(){CourtEngine e=e();ball(e,.5f,.45f,0);ball(e,.5f,.35f,100);ball(e,.5f,.2f,200);assertTrue(e.shots.isEmpty());}
 @Test public void discontinuousTrackIsUncertain(){CourtEngine e=e();ball(e,.5f,.2f,0);ball(e,.5f,.4f,600);assertEquals("Dudoso",e.shots.get(0).result);}
 @Test public void edgeIsUncertain(){CourtEngine e=e();ball(e,.4f,.2f,0);ball(e,.4f,.33f,100);assertEquals("Dudoso",e.shots.get(0).result);}
 @Test public void cooldownAvoidsDoubleCount(){CourtEngine e=e();ball(e,.5f,.2f,0);ball(e,.5f,.32f,100);ball(e,.5f,.36f,200);ball(e,.5f,.4f,300);ball(e,.5f,.2f,400);ball(e,.5f,.4f,500);assertEquals(1,e.shots.size());}
 @Test public void distinctPlayersGetDistinctIds(){CourtEngine e=e();e.update(Arrays.asList(new CourtEngine.Box("person",.1f,.2f,.1f,.5f,.8f),new CourtEngine.Box("person",.7f,.2f,.1f,.5f,.8f)),0);assertEquals(2,e.players.size());assertNotEquals(e.players.get(0).id,e.players.get(1).id);}
 @Test public void expiredTrackNeverReusesId(){CourtEngine e=e();e.update(Arrays.asList(new CourtEngine.Box("person",.1f,.2f,.1f,.5f,.8f)),0);e.update(Arrays.asList(new CourtEngine.Box("person",.1f,.2f,.1f,.5f,.8f)),1200);assertEquals(2,e.players.size());}
 @Test public void pauseCreatesUncertainAttempt(){CourtEngine e=e();ball(e,.5f,.2f,0);e.interrupt(100);assertEquals("Dudoso",e.shots.get(0).result);assertNull(e.ball);}
 @Test(expected=IllegalArgumentException.class) public void rejectTinyRim(){e().calibrate(.5f,.3f,.51f,.3f);}
 @Test public void reversedEdgesWork(){CourtEngine e=e();e.calibrate(.6f,.32f,.4f,.28f);assertEquals(.3f,e.line(.5f),.001f);}
}
