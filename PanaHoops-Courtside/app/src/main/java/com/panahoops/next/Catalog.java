package com.panahoops.next;

import java.util.*;
import org.json.*;

final class Catalog {
  static final class Exercise {
    String id, name, category, mode, cue;
    int seconds;

    Exercise(String i, String n, String c, int s, String m, String q) {
      id = i;
      name = n;
      category = c;
      seconds = s;
      mode = m;
      cue = q;
    }
  }

  static final class Step {
    String id, name, cue;
    int seconds, round;
    boolean rest;

    Step(String id, String name, String cue, int s, int round, boolean rest) {
      this.id = id;
      this.name = name;
      this.cue = cue;
      seconds = s;
      this.round = round;
      this.rest = rest;
    }
  }

  static final class Routine {
    String id, name, category, description;
    int rounds = 2, rest = 20;
    List<String> ids = new ArrayList<>();
    List<Integer> times = new ArrayList<>();

    JSONObject json() throws JSONException {
      JSONObject j =
          new JSONObject()
              .put("id", id)
              .put("name", name)
              .put("category", category)
              .put("description", description)
              .put("rounds", rounds)
              .put("rest", rest);
      JSONArray a = new JSONArray();
      for (int i = 0; i < ids.size(); i++) a.put(new JSONArray().put(ids.get(i)).put(times.get(i)));
      return j.put("steps", a);
    }
  }

  final LinkedHashMap<String, Exercise> exercises = new LinkedHashMap<>();
  final List<Routine> routines = new ArrayList<>();

  Catalog() {
    ex(
        "march",
        "Marcha activa",
        "mobility",
        45,
        "",
        "Marcha en el sitio y aumenta el ritmo poco a poco. Mueve los brazos con naturalidad.");
    ex(
        "shoulders",
        "Movilidad de hombros",
        "mobility",
        30,
        "",
        "Haz círculos suaves con los hombros, sin forzar. Cambia el sentido a la mitad.");
    ex(
        "ankles",
        "Movilidad de tobillos",
        "mobility",
        40,
        "",
        "Sujétate a un apoyo firme y haz círculos con un tobillo. Cambia de pie a la mitad.");
    ex(
        "hips",
        "Bisagra de cadera",
        "mobility",
        40,
        "",
        "Con rodillas ligeramente flexionadas, lleva la cadera atrás y vuelve despacio.");
    ex(
        "breathing",
        "Vuelta a la calma",
        "mobility",
        45,
        "",
        "Camina despacio, relaja hombros y recupera una respiración tranquila.");
    ex(
        "shot",
        "Gesto de tiro",
        "basket",
        45,
        "shot",
        "De perfil: mano junto al hombro, codo flexionado. Extiende el brazo hacia arriba y vuelve"
            + " a preparar. Cuenta gestos, no encestes.");
    ex(
        "dribble",
        "Bote estacionario",
        "basket",
        40,
        "",
        "Con un balón y espacio libre, bota a un lado del cuerpo. Cambia de mano a la mitad. El"
            + " bote no se cuenta automáticamente.");
    ex(
        "crossover",
        "Cambio de mano",
        "basket",
        40,
        "",
        "Cambia el balón de mano por delante del cuerpo con control. Aumenta el ritmo solo cuando"
            + " controles el movimiento.");
    ex(
        "lateral",
        "Desplazamiento lateral",
        "basket",
        30,
        "lateral",
        "Cámara fija y de frente. Deslízate a ambos lados de tu posición inicial con pasos cortos,"
            + " sin cruzar los pies.");
    ex(
        "stance",
        "Posición defensiva",
        "basket",
        30,
        "stance",
        "De frente y con ambas piernas visibles, flexiona las rodillas en un rango cómodo y mantén"
            + " el peso repartido.");
    ex(
        "footwork",
        "Pivote y salida",
        "basket",
        40,
        "",
        "Establece un pie de pivote, gira con control y ensaya una salida corta. Alterna el pie de"
            + " apoyo a la mitad.");
    ex(
        "layup",
        "Pasos de entrada",
        "basket",
        40,
        "",
        "Ensaya la secuencia de dos pasos caminando. Coordina el brazo de tiro y alterna el lado."
            + " Evita saltar si no hay espacio.");
    ex(
        "squat",
        "Sentadilla",
        "strength",
        35,
        "squat",
        "De perfil: empieza de pie, lleva la cadera atrás y flexiona las rodillas. Baja solo hasta"
            + " un rango cómodo y vuelve de pie.");
    ex(
        "pushup",
        "Flexión",
        "strength",
        25,
        "pushup",
        "De perfil y en el suelo: empieza con brazos extendidos, flexiona los codos y vuelve a"
            + " extenderlos. Descansa cuando lo necesites.");
    ex(
        "jack",
        "Jumping jack",
        "strength",
        25,
        "jack",
        "De frente: empieza con pies juntos y brazos abajo. Abre pies y sube brazos sobre los"
            + " hombros; vuelve a cerrar con suavidad.");
    ex(
        "plank",
        "Plancha corta",
        "strength",
        20,
        "plank",
        "De perfil: apoya antebrazos y puntas de los pies; mantén alineados hombros, cadera y pies."
            + " Respira y apoya las rodillas si necesitas descansar.");
    ex(
        "bridge",
        "Puente de glúteos",
        "strength",
        30,
        "",
        "Tumbado boca arriba y con las rodillas flexionadas, eleva la cadera suavemente y vuelve a"
            + " apoyarla con control.");
    ex(
        "calf",
        "Elevación de talones",
        "strength",
        30,
        "",
        "Usa un apoyo firme. Eleva los talones sin balancearte y vuelve a apoyarlos despacio.");
    ex(
        "lunge",
        "Zancada con control",
        "strength",
        30,
        "lunge",
        "De perfil y con ambas piernas visibles: da un paso cómodo, flexiona las piernas y regresa"
            + " de pie. Alterna el lado sin forzar el recorrido.");
    ex(
        "balance",
        "Equilibrio a una pierna",
        "mobility",
        25,
        "",
        "Colócate cerca de un apoyo firme. Despega un pie unos centímetros y mantén el equilibrio."
            + " Cambia de pie a la mitad.");
    ex(
        "wallpush",
        "Flexión en pared",
        "strength",
        35,
        "",
        "Apoya las manos en una pared estable. Acerca el pecho flexionando los codos y vuelve"
            + " despacio. Sin conteo automático.");
    ex(
        "kneelift",
        "Rodillas alternas",
        "strength",
        30,
        "",
        "Eleva las rodillas alternándolas a una altura cómoda. Hazlo caminando y sin inclinarte"
            + " hacia atrás.");
    ex(
        "chestpass",
        "Pase de pecho",
        "basket",
        40,
        "",
        "En una zona apropiada, practica pases suaves con un compañero o una pared. Prepara las"
            + " manos para recibir. Sin conteo automático.");
    ex(
        "closefinish",
        "Finalización cerca del aro",
        "basket",
        40,
        "",
        "Practica cerca del aro desde ambos lados, priorizando el control. Anota aciertos en Mi"
            + " cancha; la cámara no verifica encestes.");
    routine(
        "shooting",
        "Fundamentos de tiro",
        "basket",
        "Coordinación y repetición del gesto, con o sin balón.",
        2,
        20,
        "march,shoulders,shot,footwork,shot,breathing");
    routine(
        "strength",
        "Fuerza en movimiento",
        "strength",
        "Piernas, empuje y estabilidad con tu propio peso.",
        2,
        20,
        "march,squat,pushup,bridge,plank,breathing");
    routine(
        "handles",
        "Controla el balón",
        "basket",
        "Bote y cambios de mano. Necesitas un balón.",
        2,
        20,
        "march,dribble,crossover,footwork,dribble,breathing");
    routine(
        "defense",
        "Defiende tu espacio",
        "basket",
        "Postura defensiva y desplazamientos controlados.",
        2,
        25,
        "march,ankles,stance,lateral,footwork,breathing");
    routine(
        "fullbody",
        "Activa todo tu cuerpo",
        "strength",
        "Un circuito breve para moverte y fortalecer.",
        2,
        25,
        "march,jack,squat,pushup,bridge,calf,breathing");
    routine(
        "mobility",
        "Tu pausa activa",
        "mobility",
        "Recupera espacio para moverte sin prisa.",
        2,
        10,
        "march,shoulders,ankles,hips,breathing");
    routine(
        "start",
        "Tu primer entrenamiento",
        "strength",
        "Un comienzo sin saltos, con apoyo y pausas.",
        1,
        25,
        "march,wallpush,calf,balance,breathing");
    routine(
        "feet",
        "Pies rápidos, control total",
        "basket",
        "Pivotes y coordinación de apoyos.",
        2,
        25,
        "march,ankles,footwork,lateral,stance,breathing");
    routine(
        "shotplus",
        "Construye tu lanzamiento",
        "basket",
        "Repite el gesto y ensaya entradas y finalizaciones.",
        2,
        25,
        "march,shoulders,shot,layup,closefinish,breathing");
    routine(
        "core",
        "Centro fuerte",
        "strength",
        "Estabilidad y control con intervalos cortos.",
        2,
        25,
        "march,hips,plank,bridge,balance,breathing");
    routine(
        "nojump",
        "Muévete sin saltar",
        "strength",
        "Movimiento suave para activar el cuerpo.",
        2,
        25,
        "march,wallpush,calf,kneelift,breathing");
    routine(
        "reset",
        "Reset de 5 minutos",
        "mobility",
        "Una pausa breve para volver a moverte.",
        1,
        10,
        "march,shoulders,ankles,hips,balance,breathing");
  }

  void ex(String id, String name, String cat, int sec, String mode, String cue) {
    exercises.put(id, new Exercise(id, name, cat, sec, mode, cue));
  }

  void routine(String id, String name, String cat, String desc, int rounds, int rest, String ids) {
    Routine r = new Routine();
    r.id = id;
    r.name = name;
    r.category = cat;
    r.description = desc;
    r.rounds = rounds;
    r.rest = rest;
    for (String s : ids.split(",")) {
      r.ids.add(s);
      r.times.add(exercises.get(s).seconds);
    }
    routines.add(r);
  }

  Routine parse(JSONObject j) throws JSONException {
    Routine r = new Routine();
    r.id = j.getString("id");
    r.name = j.getString("name").trim();
    r.category = j.optString("category", "custom");
    r.description = j.optString("description", "Diseñada por ti, a tu ritmo.");
    r.rounds = j.getInt("rounds");
    r.rest = j.getInt("rest");
    JSONArray a = j.getJSONArray("steps");
    if (r.id.length() > 100
        || r.name.isEmpty()
        || r.name.length() > 60
        || r.description.length() > 500
        || r.rounds < 1
        || r.rounds > 5
        || r.rest < 0
        || r.rest > 180
        || a.length() < 1
        || a.length() > 12) throw new JSONException("Rutina no válida");
    for (int i = 0; i < a.length(); i++) {
      JSONArray s = a.getJSONArray(i);
      String id = s.getString(0);
      int sec = s.getInt(1);
      if (!exercises.containsKey(id) || sec < 5 || sec > 600)
        throw new JSONException("Movimiento o duración no válidos");
      r.ids.add(id);
      r.times.add(sec);
    }
    return r;
  }

  List<Step> timeline(Routine r, int rounds) {
    List<Step> out = new ArrayList<>();
    out.add(
        new Step(
            "prepare",
            "Prepara tu espacio",
            "Respira. Ponte en una posición cómoda para comenzar.",
            5,
            0,
            true));
    for (int round = 1; round <= rounds; round++)
      for (int i = 0; i < r.ids.size(); i++) {
        Exercise e = exercises.get(r.ids.get(i));
        out.add(new Step(e.id, e.name, e.cue, r.times.get(i), round, false));
        if (r.rest > 0 && (round < rounds || i < r.ids.size() - 1))
          out.add(
              new Step(
                  "rest",
                  "Recupera el aire",
                  "Respira y toma una pausa antes del siguiente movimiento.",
                  r.rest,
                  round,
                  true));
      }
    return out;
  }

  int seconds(Routine r, int rounds) {
    return timeline(r, rounds).stream().mapToInt(s -> s.seconds).sum();
  }

  static String category(String s) {
    return switch (s) {
      case "basket" -> "BALONCESTO";
      case "strength" -> "FUERZA";
      case "mobility" -> "MOVILIDAD";
      default -> "TU RUTINA";
    };
  }
}
