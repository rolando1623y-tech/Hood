package com.panahoops.next;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;
import org.json.*;

class TrainingStore extends SQLiteOpenHelper {
  final SharedPreferences prefs;
  static final String[] ZONES = {
    "Tiro libre",
    "Esquina izquierda",
    "Ala izquierda",
    "Frontal",
    "Ala derecha",
    "Esquina derecha",
    "Cerca del aro"
  };

  static final class Session {
    String id, name, mode, source, note;
    long date;
    int seconds, reps, effort;
    double hold;
  }

  static final class Shot {
    String id;
    long date;
    int zone;
    boolean made;
  }

  TrainingStore(Context c) {
    super(c, "panahoops-next.db", null, 2);
    prefs = c.getSharedPreferences("next.profile", Context.MODE_PRIVATE);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE sessions(id TEXT PRIMARY KEY,name TEXT NOT NULL,mode TEXT NOT NULL,source"
            + " TEXT NOT NULL,date INTEGER NOT NULL,seconds INTEGER NOT NULL,reps INTEGER NOT"
            + " NULL,hold REAL NOT NULL,note TEXT NOT NULL,effort INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX sessions_date ON sessions(date)");
    db.execSQL(
        "CREATE TABLE shots(id TEXT PRIMARY KEY,date INTEGER NOT NULL,zone INTEGER NOT NULL,made"
            + " INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX shots_date ON shots(date)");
    db.execSQL("CREATE TABLE routines(id TEXT PRIMARY KEY,json TEXT NOT NULL)");
    createFitTables(db);
  }

  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    if (old < 2) createFitTables(db);
  }

  private static void createFitTables(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE IF NOT EXISTS fit_days(day TEXT PRIMARY KEY, steps INTEGER NOT NULL DEFAULT 0, water INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE TABLE IF NOT EXISTS deleted_records(kind TEXT NOT NULL, id TEXT NOT NULL, deletedAt INTEGER NOT NULL, PRIMARY KEY(kind,id))");
  }

  void saveFitSteps(String day, long steps) {
    SQLiteDatabase db = getWritableDatabase();
    db.execSQL("INSERT OR IGNORE INTO fit_days(day,steps,water) VALUES(?,0,0)", new Object[]{day});
    db.execSQL("UPDATE fit_days SET steps=? WHERE day=?", new Object[]{Math.max(0,steps),day});
  }
  void addWater(String day, int ml) {
    if(ml < 0 || ml > 2000) throw new IllegalArgumentException("Cantidad de agua no válida");
    SQLiteDatabase db = getWritableDatabase();
    db.execSQL("INSERT OR IGNORE INTO fit_days(day,steps,water) VALUES(?,0,0)", new Object[]{day});
    db.execSQL("UPDATE fit_days SET water=MIN(20000,water+?) WHERE day=?",new Object[]{ml,day});
  }
  long[] fitDay(String day) {
    try(Cursor c=getReadableDatabase().rawQuery("SELECT steps,water FROM fit_days WHERE day=?",new String[]{day})) {
      return c.moveToFirst()?new long[]{c.getLong(0),c.getLong(1)}:new long[]{0,0};
    }
  }
  JSONArray fitBackup() throws JSONException {
    JSONArray out = new JSONArray();
    try(Cursor c=getReadableDatabase().rawQuery("SELECT day,steps,water FROM fit_days ORDER BY day",null)) {
      while(c.moveToNext())out.put(new JSONObject().put("day",c.getString(0)).put("steps",c.getLong(1)).put("water",c.getLong(2)));
    }
    return out;
  }
  boolean isDeleted(SQLiteDatabase db, String kind, String id) {
    try(Cursor c=db.rawQuery("SELECT 1 FROM deleted_records WHERE kind=? AND id=? LIMIT 1",new String[]{kind,id})) {return c.moveToFirst();}
  }

  void addSession(String name, String mode, String source, int seconds, int reps, double hold) {
    Session s = new Session();
    s.id = UUID.randomUUID().toString();
    s.name = name;
    s.mode = mode;
    s.source = source;
    s.seconds = Math.max(0, seconds);
    s.reps = Math.max(0, reps);
    s.hold = Math.max(0, hold);
    s.note = "";
    s.effort = 0;
    s.date = System.currentTimeMillis();
    insertSession(getWritableDatabase(), s);
  }

  void insertSession(SQLiteDatabase db, Session s) {
    ContentValues v = new ContentValues();
    v.put("id", s.id);
    v.put("name", s.name);
    v.put("mode", s.mode);
    v.put("source", s.source);
    v.put("date", s.date);
    v.put("seconds", s.seconds);
    v.put("reps", s.reps);
    v.put("hold", s.hold);
    v.put("note", s.note);
    v.put("effort", s.effort);
    db.insertWithOnConflict("sessions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  List<Session> sessions() {
    List<Session> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query("sessions", null, null, null, null, null, "date DESC", "10000")) {
      while (c.moveToNext()) {
        Session s = new Session();
        s.id = c.getString(0);
        s.name = c.getString(1);
        s.mode = c.getString(2);
        s.source = c.getString(3);
        s.date = c.getLong(4);
        s.seconds = c.getInt(5);
        s.reps = c.getInt(6);
        s.hold = c.getDouble(7);
        s.note = c.getString(8);
        s.effort = c.getInt(9);
        out.add(s);
      }
    }
    return out;
  }

  void annotate(String id, String note, int effort) {
    ContentValues v = new ContentValues();
    v.put("note", note.substring(0, Math.min(note.length(), 500)));
    v.put("effort", Math.max(0, Math.min(5, effort)));
    getWritableDatabase().update("sessions", v, "id=?", new String[] {id});
  }

  void deleteSession(String id) {
    getWritableDatabase().delete("sessions", "id=?", new String[] {id});
  }

  String addShot(int zone, boolean made) {
    if (zone < 0 || zone >= 7) throw new IllegalArgumentException("Zona");
    Shot s = new Shot();
    s.id = UUID.randomUUID().toString();
    s.date = System.currentTimeMillis();
    s.zone = zone;
    s.made = made;
    insertShot(getWritableDatabase(), s);
    return s.id;
  }

  void insertShot(SQLiteDatabase db, Shot s) {
    ContentValues v = new ContentValues();
    v.put("id", s.id);
    v.put("date", s.date);
    v.put("zone", s.zone);
    v.put("made", s.made ? 1 : 0);
    db.insertWithOnConflict("shots", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  void undoShot(String id) {
    getWritableDatabase().delete("shots", "id=?", new String[] {id});
  }

  List<Shot> shots(long from) {
    List<Shot> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "shots",
                null,
                "date>=?",
                new String[] {"" + from},
                null,
                null,
                "date ASC",
                "50000")) {
      while (c.moveToNext()) {
        Shot s = new Shot();
        s.id = c.getString(0);
        s.date = c.getLong(1);
        s.zone = c.getInt(2);
        s.made = c.getInt(3) == 1;
        out.add(s);
      }
    }
    return out;
  }

  void saveRoutine(Catalog.Routine r) throws JSONException {
    ContentValues v = new ContentValues();
    v.put("id", r.id);
    v.put("json", r.json().toString());
    getWritableDatabase()
        .insertWithOnConflict("routines", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  void deleteRoutine(String id) {
    getWritableDatabase().delete("routines", "id=?", new String[] {id});
  }

  List<Catalog.Routine> custom(Catalog cat) {
    List<Catalog.Routine> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "routines", new String[] {"json"}, null, null, null, null, "rowid DESC", "100")) {
      while (c.moveToNext())
        try {
          out.add(cat.parse(new JSONObject(c.getString(0))));
        } catch (JSONException ignored) {
        }
    }
    return out;
  }

  JSONObject backup(Catalog cat) throws JSONException {
    JSONArray ss = new JSONArray(), shots = new JSONArray(), custom = new JSONArray();
    for (Session s : sessions())
      ss.put(
          new JSONObject()
              .put("id", s.id)
              .put("name", s.name)
              .put("mode", s.mode)
              .put("source", s.source)
              .put("date", Instant.ofEpochMilli(s.date).toString())
              .put("seconds", s.seconds)
              .put("reps", s.reps)
              .put("hold", s.hold)
              .put("note", s.note)
              .put("effort", s.effort));
    for (Shot s : shots(0))
      shots.put(
          new JSONObject()
              .put("id", s.id)
              .put("date", Instant.ofEpochMilli(s.date).toString())
              .put("zone", s.zone)
              .put("made", s.made));
    for (Catalog.Routine r : custom(cat)) custom.put(r.json());
    return new JSONObject()
        .put("app", "PanaHoops")
        .put("version", 4)
        .put("matchShots", new JSONArray(prefs.getString("matchShots", "[]")))
        .put("fitDays",fitBackup())
        .put("sessions", ss)
        .put("shots", shots)
        .put("custom", custom)
        .put(
            "profile",
            new JSONObject()
                .put("name", prefs.getString("name", ""))
                .put("goal", prefs.getInt("goal", 3))
                .put("focus", prefs.getString("focus", "basket")));
  }

  static String csvCell(String s) {
    boolean formula =
        java.util.regex.Pattern.compile("^[\\s]*[=+@\\-]|^[\\t\\r\\n]").matcher(s).find();
    return "\"" + (formula ? "'" : "") + s.replace("\"", "\"\"") + "\"";
  }

  String csv() {
    StringBuilder out =
        new StringBuilder(
            "\uFEFFtipo,fecha,nombre,segundos,movimientos,segundos_postura,zona,enceste\r\n");
    for (Session s : sessions())
      out.append("sesion,")
          .append(csvCell(Instant.ofEpochMilli(s.date).toString()))
          .append(',')
          .append(csvCell(s.name))
          .append(',')
          .append(s.seconds)
          .append(',')
          .append(s.reps)
          .append(',')
          .append(Math.round(s.hold))
          .append(",,\r\n");
    for (Shot s : shots(0))
      out.append("tiro,")
          .append(csvCell(Instant.ofEpochMilli(s.date).toString()))
          .append(",,,,,")
          .append(csvCell(ZONES[s.zone]))
          .append(',')
          .append(s.made ? 1 : 0)
          .append("\r\n");
    return out.toString();
  }

  static String text(JSONObject j, String k, int max) throws JSONException {
    String s = j.getString(k);
    if (s.length() > max) throw new JSONException("Texto demasiado largo");
    return s;
  }

  static long date(JSONObject j) throws JSONException {
    try {
      Object d = j.get("date");
      long n =
          d instanceof Number
              ? ((Number) d).longValue()
              : Instant.parse(d.toString()).toEpochMilli();
      if (n < 0 || n > System.currentTimeMillis() + 86400000L) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new JSONException("Fecha no válida");
    }
  }

  void importBackup(JSONObject root, Catalog cat) throws JSONException {
    int version = root.optInt("version");
    if (!root.optString("app").equals("PanaHoops") || (version != 1 && version != 2 && version != 3 && version != 4))
      throw new JSONException("Copia no reconocida");
    JSONArray ss = root.getJSONArray("sessions"),
        shots = root.getJSONArray("shots"),
        custom = root.optJSONArray("custom");
    if (ss.length() > 10000 || shots.length() > 50000 || (custom != null && custom.length() > 100))
      throw new JSONException("La copia supera los límites");
    List<Session> sessions = new ArrayList<>();
    List<Shot> vs = new ArrayList<>();
    List<Catalog.Routine> vr = new ArrayList<>();
    JSONArray match=root.optJSONArray("matchShots"), existing=new JSONArray(prefs.getString("matchShots","[]"));
    LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
    for(int i=0;i<existing.length();i++){JSONObject j=existing.getJSONObject(i);merged.put(j.optString("id",j.optLong("time")+"_"+j.optString("player")),j);}
    if(match!=null){
      if(match.length()>10000)throw new JSONException("Demasiados tiros de cámara");
      for(int i=0;i<match.length();i++){
        JSONObject j=match.getJSONObject(i);String player=text(j,"player",40),result=text(j,"result",40),source=text(j,"source",120);long time=j.getLong("time");
        if(time<0||time>System.currentTimeMillis()+86400000L||!Arrays.asList("Enceste probable","Fallo probable","Dudoso","Enceste confirmado","Fallo confirmado").contains(result))throw new JSONException("Tiro de cámara no válido");
        String id=j.optString("id",time+"_"+player);if(id.length()>100)throw new JSONException("ID no válido");
        if(!isDeleted(getReadableDatabase(),"match",id)) merged.putIfAbsent(id,new JSONObject().put("id",id).put("time",time).put("player",player).put("result",result).put("source",source));
      }
      if(merged.size()>10000)throw new JSONException("Límite de tiros de cámara alcanzado");
    }
    for (int i = 0; i < ss.length(); i++) {
      JSONObject j = ss.getJSONObject(i);
      Session s = new Session();
      s.id = text(j, "id", 100);
      s.name = text(j, "name", 120);
      s.mode = j.optString("mode", "");
      s.source = j.optString("source", "routine");
      if (!Arrays.asList("camera", "video", "routine").contains(s.source)) s.source = "routine";
      s.date = date(j);
      s.seconds = j.getInt("seconds");
      s.reps = j.getInt("reps");
      s.hold = j.optDouble("hold", 0);
      s.note = j.optString("note", "");
      s.effort = j.optInt("effort", 0);
      if (s.seconds < 0
          || s.seconds > 86400
          || s.reps < 0
          || s.reps > 100000
          || !Double.isFinite(s.hold)
          || s.hold < 0
          || s.hold > 86400
          || s.note.length() > 500
          || s.effort < 0
          || s.effort > 5) throw new JSONException("Sesión no válida");
      sessions.add(s);
    }
    for (int i = 0; i < shots.length(); i++) {
      JSONObject j = shots.getJSONObject(i);
      Shot s = new Shot();
      s.id = text(j, "id", 100);
      s.date = date(j);
      s.zone = j.getInt("zone");
      Object made = j.get("made");
      if (s.zone < 0 || s.zone >= 7 || !(made instanceof Boolean))
        throw new JSONException("Tiro no válido");
      s.made = (boolean) made;
      vs.add(s);
    }
    if (custom != null)
      for (int i = 0; i < custom.length(); i++) {
        Catalog.Routine r = cat.parse(custom.getJSONObject(i));
        r.category = "custom";
        vr.add(r);
      }
    JSONArray importedFit = root.optJSONArray("fitDays");
    List<Object[]> fitRows = new ArrayList<>();
    if(importedFit != null) {
      if(importedFit.length() > 20000) throw new JSONException("Historial FIT demasiado grande");
      for(int i=0;i<importedFit.length();i++) {
        JSONObject f=importedFit.getJSONObject(i);
        String day=text(f,"day",10);
        try { LocalDate.parse(day); } catch(Exception ex) { throw new JSONException("Fecha FIT no válida"); }
        long steps=f.getLong("steps"),water=f.getLong("water");
        if(steps<0||steps>1000000||water<0||water>20000)throw new JSONException("FIT inválido");
        fitRows.add(new Object[]{day,steps,water});
      }
    }
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (Session s : sessions) if(!isDeleted(db,"session",s.id)) insertSession(db, s);
      for (Shot s : vs) if(!isDeleted(db,"shot",s.id)) insertShot(db, s);
      for(Object[] fit : fitRows)
        if(!isDeleted(db,"fit",(String)fit[0]))
          db.execSQL("INSERT OR IGNORE INTO fit_days(day,steps,water) VALUES(?,?,?)",fit);
      for (Catalog.Routine r : vr) {
        ContentValues v = new ContentValues();
        v.put("id", r.id);
        v.put("json", r.json().toString());
        db.insertWithOnConflict("routines", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    if(match!=null){JSONArray result=new JSONArray();for(JSONObject j:merged.values())result.put(j);prefs.edit().putString("matchShots",result.toString()).apply();}
  }

  static long today() {
    return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
