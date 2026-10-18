package com.panahoops.next;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import java.util.*;
import org.json.*;

/** Scoped local deletion with tombstones to prevent accidental reimport of old IDs. */
final class ResetManager {
  enum Scope { LAST_SESSION, DAY, RANGE, PLAYER, MODE, SHOTS, EXERCISES, FIT, ALL }
  static final class Selection {
    final Scope scope;
    final long from, until;
    final String value;
    Selection(Scope scope,long from,long until,String value) {
      if(from < 0 || until <= from)throw new IllegalArgumentException("Periodo inválido");
      this.scope=scope;this.from=from;this.until=until;this.value=value==null?"":value;
    }
    static Selection all(Scope s){return new Selection(s,0,Long.MAX_VALUE,"");}
  }
  static final class Count {
    final int sessions,shots,match,fitDays,manualHeart;
    Count(int a,int b,int c,int d,int e){sessions=a;shots=b;match=c;fitDays=d;manualHeart=e;}
    int total(){return sessions+shots+match+fitDays+manualHeart;}
    @Override public String toString(){return sessions+" sesiones, "+shots+" tiros manuales, "+match+" tiros de cámara, "+fitDays+" días FIT y "+manualHeart+" anotaciones de pulso";}
  }

  private final TrainingStore store;
  private final Context context;
  ResetManager(TrainingStore s,Context c){store=s;context=c.getApplicationContext();}
  static long startDay(LocalDate day){return day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}
  static String dayOf(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
  static boolean applies(Selection s,String type,long time,String id,String player,String mode) {
    return ResetPolicy.matches(s.scope.name(),s.from,s.until,s.value,type,time,id,player,mode);
  }
  private static final class Row {
    final String kind,id;
    Row(String kind,String id){this.kind=kind;this.id=id;}
  }
  private static final class Plan {
    final ArrayList<Row> rows = new ArrayList<>();
    final ArrayList<String> days = new ArrayList<>();
    JSONArray retainedMatch = new JSONArray();
    Count count;
    boolean removeHeart;
  }
  private Plan plan(Selection selection) throws JSONException {
    SQLiteDatabase db=store.getReadableDatabase();
    Plan p=new Plan();int a=0,b=0,c=0;
    try(Cursor cursor=db.rawQuery("SELECT id,date,source FROM sessions",null)) {
      while(cursor.moveToNext())if(applies(selection,"session",cursor.getLong(1),cursor.getString(0),"",cursor.getString(2))){
        p.rows.add(new Row("session",cursor.getString(0)));a++;
      }
    }
    try(Cursor cursor=db.rawQuery("SELECT id,date FROM shots",null)) {
      while(cursor.moveToNext())if(applies(selection,"shot",cursor.getLong(1),cursor.getString(0),"","")){
        p.rows.add(new Row("shot",cursor.getString(0)));b++;
      }
    }
    JSONArray shots=new JSONArray(store.prefs.getString("matchShots","[]"));
    for(int i=0;i<shots.length();i++){
      JSONObject o=shots.getJSONObject(i);
      String id=o.optString("id",o.optLong("time")+"_"+o.optString("player"));
      if(applies(selection,"match",o.optLong("time"),id,o.optString("player"),"")){
        p.rows.add(new Row("match",id));c++;
      }else p.retainedMatch.put(o);
    }
    try(Cursor cursor=db.rawQuery("SELECT day FROM fit_days",null)) {
      while(cursor.moveToNext()){
        String day=cursor.getString(0);
        long dayStart;
        try{dayStart=startDay(LocalDate.parse(day));}catch(Exception ex){continue;}
        if(applies(selection,"fit",dayStart,day,"",""))p.days.add(day);
      }
    }
    long at=store.prefs.getLong("manualHeartAt",0);
    p.removeHeart=at>0&&applies(selection,"fit",at,Long.toString(at),"","");
    p.count=new Count(a,b,c,p.days.size(),p.removeHeart?1:0);return p;
  }
  Count preview(Selection s) throws JSONException {return plan(s).count;}

  /** Must be called after an explicit user confirmation, and not from a running session. */
  Count execute(Selection s) throws JSONException {
    Plan p=plan(s);
    SQLiteDatabase db=store.getWritableDatabase();
    db.beginTransaction();
    try {
      for(Row r:p.rows){
        db.execSQL("INSERT OR IGNORE INTO deleted_records(kind,id,deletedAt) VALUES(?,?,?)",
          new Object[]{r.kind,r.id,System.currentTimeMillis()});
        if(r.kind.equals("session"))db.delete("sessions","id=?",new String[]{r.id});
        if(r.kind.equals("shot"))db.delete("shots","id=?",new String[]{r.id});
      }
      for(String day:p.days){
        db.execSQL("INSERT OR IGNORE INTO deleted_records(kind,id,deletedAt) VALUES('fit',?,?)",
            new Object[]{day,System.currentTimeMillis()});
        db.delete("fit_days","day=?",new String[]{day});
      }
      // Do not remove profiles, cloud configuration, routines or account credentials.
      // JSON match entries are written after the DB commit; tombstones protect against stale imports.
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    if(p.removeHeart){
      long at=store.prefs.getLong("manualHeartAt",0);
      db.execSQL("INSERT OR IGNORE INTO deleted_records(kind,id,deletedAt) VALUES('heart',?,?)",
          new Object[]{Long.toString(at),System.currentTimeMillis()});
      store.prefs.edit().remove("manualHeart").remove("manualHeartAt").commit();
    }
    if(p.count.match>0 && !store.prefs.edit().putString("matchShots",p.retainedMatch.toString()).commit())
      throw new JSONException("No fue posible actualizar los tiros de cámara; reintenta");
    if(s.scope==Scope.FIT || s.scope==Scope.ALL || (s.scope==Scope.DAY || s.scope==Scope.RANGE)
        && s.from<=System.currentTimeMillis() && System.currentTimeMillis()<s.until){
      android.content.SharedPreferences fit=context.getSharedPreferences("fit.sensor",Context.MODE_PRIVATE);
      String today=LocalDate.now().toString();
      if(s.scope==Scope.ALL||s.scope==Scope.FIT|| (s.from<=startDay(LocalDate.now())&&s.until>startDay(LocalDate.now()))) {
        fit.edit().putLong("steps",0).commit();
      }
    }
    return p.count;
  }
}
