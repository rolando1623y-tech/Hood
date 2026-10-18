package com.panahoops.next;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AdvancedAppTest {
 @Test public void cameraBackupRoundTripDeduplicates() throws Exception {TrainingStore s=new TrainingStore(RuntimeEnvironment.getApplication());s.prefs.edit().putString("matchShots","[]").commit();JSONArray a=new JSONArray().put(new JSONObject().put("id","test-shot").put("time",System.currentTimeMillis()).put("player","Ana").put("result","Enceste confirmado").put("source","Confirmación manual"));s.prefs.edit().putString("matchShots",a.toString()).commit();JSONObject backup=s.backup(new Catalog());assertEquals(1,backup.getJSONArray("matchShots").length());s.prefs.edit().putString("matchShots","[]").commit();s.importBackup(backup,new Catalog());s.importBackup(backup,new Catalog());assertEquals(1,new JSONArray(s.prefs.getString("matchShots","[]")).length());s.close();}
 @Test public void socialShowsHonestUnconfiguredState(){RuntimeEnvironment.getApplication().getSharedPreferences("next.profile",0).edit().remove("cloudConfig").commit();var c=Robolectric.buildActivity(SocialActivity.class).setup();assertNull(c.get().cloud);assertNotNull(c.get().body);c.pause().stop().destroy();}
 @Test public void everyExerciseHasInstructionsAndExample(){Catalog cat=new Catalog();for(Catalog.Exercise ex:cat.exercises.values()){String guide=ExerciseDemo.guide(ex.id);assertTrue(ex.id,guide.contains("1."));assertFalse(ex.id,guide.contains("descripción escrita"));}}
}
