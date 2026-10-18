package com.panahoops.next;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FitAndResetTest {
  private Context ctx;
  private TrainingStore store;
  @Before public void before(){
    ctx=RuntimeEnvironment.getApplication();
    ctx.deleteDatabase("panahoops-next.db");
    ctx.getSharedPreferences("next.profile",0).edit().clear().commit();
    ctx.getSharedPreferences("fit.sensor",0).edit().clear().commit();
    store=new TrainingStore(ctx);
  }
  @After public void after(){store.close();ctx.deleteDatabase("panahoops-next.db");}

  @Test public void preservesRoutinesAndProfileWhenClearingStats() throws Exception {
    store.prefs.edit().putString("name","Atleta").commit();
    store.addSession("Sentadilla","squat","camera",45,12,0);
    store.addShot(2,true);
    store.addWater(LocalDate.now().toString(),250);
    store.saveFitSteps(LocalDate.now().toString(),150);
    String snap=store.backup(new Catalog()).toString();
    ResetManager reset=new ResetManager(store,ctx);
    assertEquals(3,reset.preview(ResetManager.Selection.all(ResetManager.Scope.ALL)).total());
    reset.execute(ResetManager.Selection.all(ResetManager.Scope.ALL));
    assertEquals(0,store.sessions().size());
    assertEquals(0,store.shots(0).size());
    assertArrayEquals(new long[]{0,0},store.fitDay(LocalDate.now().toString()));
    assertEquals("Atleta",store.prefs.getString("name",null));
    store.importBackup(new JSONObject(snap),new Catalog());
    assertEquals(0,store.sessions().size());
    assertEquals(0,store.shots(0).size());
    assertArrayEquals(new long[]{0,0},store.fitDay(LocalDate.now().toString()));
  }
  @Test public void resetPlayerDoesNotTouchOtherPlayers() throws Exception {
    JSONArray arr=new JSONArray();
    arr.put(new JSONObject().put("id","p1").put("time",System.currentTimeMillis())
        .put("player","Uno").put("result","Dudoso").put("source","cam"));
    arr.put(new JSONObject().put("id","p2").put("time",System.currentTimeMillis())
        .put("player","Dos").put("result","Enceste confirmado").put("source","cam"));
    store.prefs.edit().putString("matchShots",arr.toString()).commit();
    new ResetManager(store,ctx).execute(new ResetManager.Selection(ResetManager.Scope.PLAYER,
        0,Long.MAX_VALUE,"Uno"));
    JSONArray left=new JSONArray(store.prefs.getString("matchShots","[]"));
    assertEquals(1,left.length());assertEquals("Dos",left.getJSONObject(0).getString("player"));
  }
  @Test public void databaseVersionOneUpgradesWithoutDroppingData(){
    store.close();ctx.deleteDatabase("panahoops-next.db");
    SQLiteDatabase db=ctx.openOrCreateDatabase("panahoops-next.db",0,null);
    db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,name TEXT,mode TEXT,source TEXT,date INTEGER,seconds INTEGER,reps INTEGER,hold REAL,note TEXT,effort INTEGER)");
    db.execSQL("CREATE TABLE shots(id TEXT PRIMARY KEY,date INTEGER,zone INTEGER,made INTEGER)");
    db.execSQL("CREATE TABLE routines(id TEXT PRIMARY KEY,json TEXT)");
    db.execSQL("INSERT INTO sessions VALUES('legacy','Antiguo','shot','camera',1,12,2,0,'',0)");
    db.setVersion(1);db.close();
    store=new TrainingStore(ctx);
    assertEquals("legacy",store.sessions().get(0).id);
    store.saveFitSteps("2026-10-18",23);
    assertEquals(23L,store.fitDay("2026-10-18")[0]);
  }
}
