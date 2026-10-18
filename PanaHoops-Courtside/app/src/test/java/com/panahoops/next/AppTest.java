package com.panahoops.next;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppTest {
  ActivityController<MainActivity> controller;
  MainActivity app;

  @Before
  public void start() {
    RuntimeEnvironment.getApplication().deleteDatabase("panahoops-next.db");
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("next.profile", 0)
        .edit()
        .clear()
        .commit();
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    app = controller.get();
  }

  @After
  public void finish() {
    controller.pause().stop().destroy();
  }

  @Test
  public void allNativePagesOpenWithoutFakeStats() {
    for (int p : new int[] {0, 1, 3, 4, 5}) {
      app.showPage(p);
      assertNotNull(app.scroll);
      assertEquals(p, app.page);
    }
    assertTrue(app.store.sessions().isEmpty());
    assertEquals(5, app.nav.getChildCount());
  }

  @Test
  public void catalogAndTimelinesAreComplete() {
    assertEquals(24, app.catalog.exercises.size());
    assertEquals(12, app.catalog.routines.size());
    for (Catalog.Routine r : app.catalog.routines) {
      int total = 0;
      for (Catalog.Step s : app.catalog.timeline(r, r.rounds)) {
        total += s.seconds;
        assertTrue(s.seconds > 0);
      }
      assertEquals(total, app.catalog.seconds(r, r.rounds));
    }
  }

  @Test
  public void shotsCanBeRecordedAndUndone() {
    String id = app.store.addShot(2, true);
    app.store.addShot(2, false);
    assertEquals(2, app.store.shots(0).size());
    app.store.undoShot(id);
    assertEquals(1, app.store.shots(0).size());
    assertFalse(app.store.shots(0).get(0).made);
    app.showPage(3);
  }

  @Test
  public void sessionNotesAndHistoryPersist() {
    app.store.addSession("Sentadilla", "squat", "camera", 60, 8, 0);
    Store.Session s = app.store.sessions().get(0);
    app.store.annotate(s.id, "Más control", 3);
    Store.Session back = app.store.sessions().get(0);
    assertEquals(8, back.reps);
    assertEquals("Más control", back.note);
    assertEquals(3, back.effort);
    app.showPage(4);
  }

  @Test
  public void importsDeduplicateAndValidateBeforeWriting() throws Exception {
    app.store.addSession("Plancha", "plank", "camera", 40, 0, 20);
    app.store.addShot(0, true);
    JSONObject backup = app.store.backup(app.catalog);
    app.store.importBackup(backup, app.catalog);
    assertEquals(1, app.store.sessions().size());
    assertEquals(1, app.store.shots(0).size());
    JSONObject bad = new JSONObject(backup.toString());
    bad.getJSONArray("sessions").getJSONObject(0).put("id", "new");
    bad.getJSONArray("shots").getJSONObject(0).put("zone", 99);
    try {
      app.store.importBackup(bad, app.catalog);
      fail("Bad backup accepted");
    } catch (JSONException expected) {
    }
    assertEquals(1, app.store.sessions().size());
    assertEquals(1, app.store.shots(0).size());
  }

  @Test
  public void customRoutineRoundTripAndBounds() throws Exception {
    JSONObject j =
        new JSONObject()
            .put("id", "test-custom")
            .put("name", "Mi rutina")
            .put("rounds", 2)
            .put("rest", 20)
            .put("steps", new JSONArray().put(new JSONArray().put("squat").put(30)));
    Catalog.Routine r = app.catalog.parse(j);
    app.store.saveRoutine(r);
    assertEquals(1, app.store.custom(app.catalog).size());
    j.put("rounds", 99);
    try {
      app.catalog.parse(j);
      fail();
    } catch (JSONException expected) {
    }
    app.store.deleteRoutine(r.id);
    assertTrue(app.store.custom(app.catalog).isEmpty());
  }

  @Test
  public void csvDoesNotExecuteImportedFormulaStrings() {
    assertEquals("\"' =x\"", Store.csvCell(" =x"));
    assertEquals("\"'=HYPERLINK(1)\"", Store.csvCell("=HYPERLINK(1)"));
    assertEquals("\"hello\"\"world\"", Store.csvCell("hello\"world"));
  }
}
