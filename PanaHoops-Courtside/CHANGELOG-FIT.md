# Cambios del código 3.1 respecto de Courtside 3.0 beta

- Agregado módulo FIT con sensor Android de pasos, registro local de hidratación y pulso manual.
- Añadido servicio en primer plano con notificación y controles explícitos para iniciar/detener.
- Migración aditiva de SQLite DB v1 a v2; `fit_days` y marcadores de borrado `deleted_records`.
- JSON v4 con datos de pasos e hidratación, compatible con importaciones v1-v3.
- Nueva pantalla FIT en los cinco destinos de navegación; acceso a Cancha desde Inicio.
- Diálogo de restablecimiento con nueve alcances, recuento previo, exportación y confirmación.
- Rangos de Camera2 verificados antes de solicitar objetivos de FPS en CameraX; fallback automático.
- Quitado límite fijo de diez inferencias/s de la cámara de cancha mientras está activa.
- Comprobaciones sintéticas 22/22 pasadas; tres pruebas Robolectric añadidas y pendientes de ejecución.
- Paleta nueva crema/grafito/verde; tema oscuro global, rediseño Compose y accesibilidad exhaustiva pendientes.

**No se corrigieron ni probaron exhaustivamente las opciones antiguas de red, grupos, detección del aro o clips.** Requieren validación adicional. No se declara una release lista para producción.

## 3.1.1 · Ajuste de FIT y entrega reproducible

- La longitud de zancada se puede guardar entre 40 y 150 cm desde la pantalla FIT.
- La distancia usa la zancada realmente configurada en vez de un valor fijo de 70 cm.
- Añadidas seis pruebas Kotlin puras para estimación y validación de entrada.
- Nombre visible actualizado a PanaHoops FIT; el applicationId no cambia.
- Código versión 311; la APK nueva sigue pendiente de compilación y pruebas Android.
