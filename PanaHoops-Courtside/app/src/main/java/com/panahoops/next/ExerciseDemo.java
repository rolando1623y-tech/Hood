package com.panahoops.next;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Offline vector demonstrations. Schematic motion, not recorded coaching footage. */
final class ExerciseDemo extends View {
  final String id;
  final Paint p = new Paint(3);
  boolean paused = false;
  long started = SystemClock.elapsedRealtime();

  ExerciseDemo(Context c, String id) {
    super(c);
    this.id = id;
    setContentDescription("Demostración esquemática animada. Toca para pausar o continuar.");
    setOnClickListener(
        v -> {
          paused = !paused;
          invalidate();
        });
  }

  void line(Canvas c, float a, float b, float x, float y) {
    c.drawLine(a, b, x, y, p);
  }

  protected void onDraw(Canvas out) {
    super.onDraw(out);
    Canvas c = out;
    c.save();
    c.scale(getWidth() / 300f, getHeight() / 205f);
    c.drawColor(0xFFEAF0FF);
    float t = (SystemClock.elapsedRealtime() - started) % 3200 / 3200f,
        s = (float) (.5 - .5 * Math.cos(t * Math.PI * 2));
    if (paused) s = .5f;
    p.setColor(0xFFCCD7EE);
    p.setStrokeWidth(2);
    line(c, 25, 174, 275, 174);
    p.setColor(Ui.BLUE);
    p.setStrokeWidth(5);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStyle(Paint.Style.STROKE);
    boolean floor = id.equals("plank") || id.equals("pushup") || id.equals("bridge");
    if (floor) {
      float h = id.equals("pushup") ? 115 + 25 * s : id.equals("bridge") ? 150 - 25 * s : 125;
      c.drawCircle(80, h - 6, 10, p);
      line(c, 92, h, 165, id.equals("bridge") ? h : 135);
      line(c, 165, id.equals("bridge") ? h : 135, 225, 168);
      line(c, 105, h + 4, 98 + (id.equals("pushup") ? 14 * s : 0), 170);
      if (id.equals("bridge")) {
        line(c, 165, h, 200, 135);
        line(c, 200, 135, 225, 168);
      }
    } else {
      float shift = id.equals("lateral") || id.equals("footwork") ? (s - .5f) * 65 : 0;
      float drop =
          id.equals("squat") ? 30 * s : id.equals("lunge") ? 15 * s : id.equals("stance") ? 22 : 0;
      float heel = id.equals("calf") ? 8 * s : 0;
      float x = 145 + shift, hip = 126 + drop - heel, head = 59 + drop - heel;
      c.drawCircle(x, head, 11, p);
      line(c, x, head + 13, x, hip);
      float spread = id.equals("jack") ? 10 + 30 * s : id.equals("lunge") ? 12 + 35 * s : 20;
      float lift =
          id.equals("march") || id.equals("kneelift") || id.equals("breathing")
              ? 20 * s
              : id.equals("balance") ? 20 : 0;
      if (id.equals("hips")) {
        line(c, x, hip, x + 30 * s, head + 15);
      }
      line(c, x, hip, x - spread, 148 + drop * .2f);
      line(c, x - spread, 148 + drop * .2f, x - spread - 6, 172);
      line(c, x, hip, x + spread, 148 - lift);
      line(c, x + spread, 148 - lift, x + spread + 6, 172 - lift);
      if (id.equals("shot") || id.equals("closefinish") || id.equals("layup")) {
        line(c, x, head + 24, x + 22, head + 10 - 20 * s);
        line(c, x + 22, head + 10 - 20 * s, x + 20, head - 3 - 25 * s);
        line(c, x, head + 24, x - 17, head + 25 - 22 * s);
        p.setColor(Ui.ORANGE);
        c.drawCircle(x + 20 + 55 * s, head - 12 - 45 * s, 9, p);
      } else if (id.equals("dribble") || id.equals("crossover")) {
        line(c, x, head + 24, x + 28, 112);
        line(c, x, head + 24, x - 25, 109);
        p.setColor(Ui.ORANGE);
        c.drawCircle(x + 35 * (id.equals("crossover") ? (2 * s - 1) : 1), 125 + 36 * s, 10, p);
      } else if (id.equals("chestpass")) {
        line(c, x, head + 24, x + 35 * s, head + 25);
        line(c, x, head + 24, x + 40 * s, head + 25);
        p.setColor(Ui.ORANGE);
        c.drawCircle(x + 20 + 85 * s, head + 25, 10, p);
      } else {
        float raise =
            id.equals("jack")
                ? 75 * s
                : id.equals("shoulders") ? 35 * s : id.equals("wallpush") ? 25 : 0;
        line(c, x, head + 24, x - 28, head + 52 - raise);
        line(c, x, head + 24, x + 28, head + 52 - raise);
        if (id.equals("wallpush")) {
          p.setColor(Ui.MUTED);
          line(c, x + 34, 50, x + 34, 174);
        }
      }
    }
    p.setColor(Ui.MUTED);
    p.setStyle(Paint.Style.FILL);
    p.setTextSize(10);
    c.drawText(
        paused ? "PAUSA · toca para continuar" : "EJEMPLO ESQUEMÁTICO · toca para pausar",
        20,
        195,
        p);
    c.restore();
    if (!paused && isShown()) postInvalidateOnAnimation();
  }

  static String guide(String id) {
    return switch (id) {
      case "shot", "closefinish" ->
          "1. Coloca los pies cómodos y prepara el balón.\n"
              + "2. Flexiona y extiende piernas y brazo en un gesto continuo.\n"
              + "3. Termina con la muñeca relajada y mantén el equilibrio.\n"
              + "Prueba: 3 series de 8 tiros cerca del aro, cambiando de lado.\n"
              + "Revisa: no empujes el balón con la mano de apoyo.";
      case "layup" ->
          "1. Empieza caminando sin balón.\n"
              + "2. Por la derecha: paso derecho, izquierdo y finaliza con la derecha; invierte por"
              + " la izquierda.\n"
              + "3. Practica después con balón y a baja velocidad.\n"
              + "Ejemplo: 5 entradas por cada lado. Revisa el control antes de aumentar el ritmo.";
      case "dribble", "crossover" ->
          "1. Separa los pies y flexiona ligeramente las rodillas.\n"
              + "2. Empuja el balón con los dedos y controla el rebote cerca de la cadera.\n"
              + "3. Alterna manos; levanta la mirada cuando controles el balón.\n"
              + "Ejemplo: 20 segundos por mano y 20 de descanso. Evita golpearlo con la palma.";
      case "lateral", "stance" ->
          "1. Mantén una base cómoda y las rodillas flexionadas.\n"
              + "2. Desplázate con pasos cortos, sin cruzar los pies.\n"
              + "3. Frena con control y cambia de dirección.\n"
              + "Ejemplo: 4 recorridos de 3 metros, descansando entre ellos.";
      case "squat" ->
          "1. Pies en una posición cómoda.\n"
              + "2. Lleva la cadera atrás y abajo, manteniendo apoyados los pies.\n"
              + "3. Vuelve de pie sin rebotes.\n"
              + "Ejemplo: 2 series de 8 repeticiones lentas. Usa una silla como referencia si lo"
              + " necesitas.";
      case "pushup", "wallpush" ->
          "1. Apoya las manos en suelo o pared estable.\n"
              + "2. Mantén el tronco alineado al flexionar los codos.\n"
              + "3. Empuja y vuelve con control.\n"
              + "Ejemplo: 2 series de 6; elige pared o rodillas si facilita un gesto cómodo.";
      case "plank" ->
          "1. Apoya antebrazos y puntas de los pies.\n"
              + "2. Mantén hombros, cadera y pies alineados.\n"
              + "3. Respira sin aguantar el aire.\n"
              + "Ejemplo: 3 intervalos de 15 segundos con 30 de descanso.";
      case "lunge" ->
          "1. Da un paso amplio pero cómodo.\n"
              + "2. Baja flexionando ambas piernas con control.\n"
              + "3. Empuja para volver y alterna el lado.\n"
              + "Ejemplo: 2 series de 6 por pierna. Usa un apoyo si pierdes el equilibrio.";
      case "jack" ->
          "1. Empieza con pies juntos y brazos abajo.\n"
              + "2. Abre pies y eleva los brazos.\n"
              + "3. Cierra con un apoyo suave.\n"
              + "Ejemplo: 3 intervalos de 20 segundos. Alternativa sin salto: abre un pie cada"
              + " vez.";
      case "chestpass" ->
          "1. Balón frente al pecho y compañero preparado.\n"
              + "2. Extiende los brazos hacia el objetivo con un paso cómodo.\n"
              + "3. Recibe amortiguando con los brazos.\n"
              + "Ejemplo: 3 series de 10 pases suaves a 2 metros.";
      case "march" ->
          "1. De pie, eleva un pie y después el otro a poca altura.\n"
              + "2. Acompaña con los brazos sin tensar hombros.\n"
              + "3. Aumenta el ritmo progresivamente.\n"
              + "Ejemplo: 45 segundos de marcha y 15 de pausa.";
      case "shoulders" ->
          "1. Deja los brazos relajados a los lados.\n"
              + "2. Lleva los hombros arriba, atrás y abajo en círculos pequeños.\n"
              + "3. Cambia de dirección a la mitad.\n"
              + "Ejemplo: 8 círculos en cada sentido, sin forzar.";
      case "ankles" ->
          "1. Sujétate a un apoyo firme.\n"
              + "2. Despega ligeramente un pie y dibuja círculos con el tobillo.\n"
              + "3. Cambia el sentido y después el pie.\n"
              + "Ejemplo: 8 círculos por sentido y por pie.";
      case "hips" ->
          "1. Pies cómodos y rodillas ligeramente flexionadas.\n"
              + "2. Lleva la cadera atrás inclinando el tronco desde la cadera.\n"
              + "3. Vuelve de pie despacio.\n"
              + "Ejemplo: 2 series de 8. Revisa que el movimiento salga de la cadera.";
      case "breathing" ->
          "1. Camina despacio y relaja los hombros.\n"
              + "2. Respira de forma cómoda y natural.\n"
              + "3. Reduce el paso progresivamente.\n"
              + "Ejemplo: 45 segundos después de la rutina.";
      case "footwork" ->
          "1. Elige un pie de pivote y mantenlo apoyado.\n"
              + "2. Gira con el otro pie sin perder el equilibrio.\n"
              + "3. Vuelve y cambia de lado.\n"
              + "Ejemplo: 5 pivotes en cada dirección antes de añadir una salida.";
      case "bridge" ->
          "1. Túmbate boca arriba con rodillas flexionadas y pies apoyados.\n"
              + "2. Eleva la cadera sin arquear de más la espalda.\n"
              + "3. Baja despacio hasta apoyar.\n"
              + "Ejemplo: 2 series de 10 repeticiones controladas.";
      case "calf" ->
          "1. Apóyate en una superficie firme.\n"
              + "2. Eleva ambos talones manteniendo el equilibrio.\n"
              + "3. Desciende lentamente.\n"
              + "Ejemplo: 2 series de 10. Evita balancearte hacia delante.";
      case "balance" ->
          "1. Colócate junto a un apoyo firme.\n"
              + "2. Despega un pie a poca altura.\n"
              + "3. Mantén la posición y cambia de lado.\n"
              + "Ejemplo: 15 segundos por pierna. Usa el apoyo si lo necesitas.";
      case "kneelift" ->
          "1. De pie, eleva una rodilla a una altura cómoda.\n"
              + "2. Apoya suavemente y cambia de pierna.\n"
              + "3. Mantén el tronco erguido.\n"
              + "Ejemplo: 20 elevaciones alternas sin saltar.";
      default ->
          "1. Lee la indicación del ejercicio.\n"
              + "2. Ensaya despacio y con un recorrido cómodo.\n"
              + "3. Mantén la respiración natural y toma pausas.\n"
              + "El dibujo es esquemático; la descripción escrita indica la ejecución específica.";
    };
  }
}
