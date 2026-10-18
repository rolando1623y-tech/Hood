package com.panahoops.next;

import android.content.*;
import android.hardware.camera2.*;
import android.util.Range;
import java.util.*;
import kotlin.Pair;

/** Capability negotiation for ordinary CameraX sessions; not a high-speed video mode. */
final class CameraPerformance {
  static final class Choice {
    final Range<Integer> request;
    final String explanation;
    Choice(Range<Integer> request,String explanation){this.request=request;this.explanation=explanation;}
  }
  static Choice choose(Context ctx,boolean front,int preset){
    if(preset==0)return new Choice(null,"Automático · cámara elige frecuencia");
    int desired=preset==1?30:preset==2?60:120;
    try {
      CameraManager mgr=(CameraManager)ctx.getSystemService(Context.CAMERA_SERVICE);
      if(mgr==null)throw new IllegalStateException("Sin CameraManager");
      for(String id:mgr.getCameraIdList()){
        CameraCharacteristics ch=mgr.getCameraCharacteristics(id);
        Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
        if(facing==null||facing!=(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK))continue;
        Range<Integer>[] ranges=ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if(ranges==null||ranges.length==0)break;
        ArrayList<Pair<Integer,Integer>> list=new ArrayList<>();
        for(Range<Integer> r:ranges)list.add(new Pair<>(r.getLower(),r.getUpper()));
        Pair<Integer,Integer> picked=FpsPolicy.best(list,desired);
        if(picked==null)return new Choice(null,"Frecuencia no anunciada · cámara automática");
        Range<Integer> requested=new Range<>(picked.getFirst(),picked.getSecond());
        String note=picked.getSecond()<desired?
            "Objetivo "+desired+" no anunciado en sesión normal; solicitados "+requested+" FPS" :
            "Solicitados "+requested+" FPS; medir el resultado real";
        if(preset==3 && picked.getSecond()<120)note += " · 120 podría exigir sesión especial no implementada";
        return new Choice(requested,note);
      }
    }catch(Exception ignored){}
    return new Choice(null,"No se pudieron comprobar rangos de cámara · modo automático");
  }
}
