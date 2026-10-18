# PanaHoops FIT 3.1.1 · Entrega de código fuente

**Estado: versión fuente sin compilación Android verificada.** Este proyecto es una ampliación de **PanaHoops Courtside 3.0 beta**, no una aplicación distinta. Conserva `applicationId=com.panahoops.courtside`, la estructura original, los módulos Java y la base `panahoops-next.db`. Se agrega Kotlin a módulos nuevos sin reescribir el legado.

## Funciones que incorpora esta revisión

- **FIT local:** seguimiento opcional desde el sensor Android `TYPE_STEP_COUNTER`, con permiso `ACTIVITY_RECOGNITION` en Android 10+, servicio en primer plano `health` y notificación visible para detenerlo. Registra total observado por día, meta, distancia **estimada** a zancada personalizable entre 40 y 150 cm (valor inicial 70 cm), cadencia cuando existe muestra reciente, tiempo y pasos observados en la sesión. Sin sensor físico compatible muestra un estado de indisponibilidad; no inventa pasos ni usa acelerómetro como contador ficticio.
- **FIT manual:** hidratación (+250 ml) y anotación de pulso (identificada como MANUAL, nunca en vivo). El pulso BLE y Health Connect no están conectados y no se presentan como mediciones activas. No se envían mediciones FIT al servidor.
- **Reseteo local:** Progreso → Administrar datos → Restablecer estadísticas. Alcances: última sesión, día, período personalizado, jugador de tiros de cámara, modalidad, tiros, ejercicios, FIT, todo. Vista previa del número de registros, exportación JSON, confirmación y conservación de rutinas, cuentas y preferencias. Conserva marcadores de eliminación para que una copia antigua no restaure accidentalmente los mismos IDs de sesiones/tiros o días FIT.
- **Copia JSON:** formato de copia versión 4 incorpora días FIT; importación de v1, v2 y v3 preservada. La migración SQLite de v1 a v2 añade tablas y no elimina filas antiguas. No se cambió el paquete Android. Evita duplicados mediante claves primarias y marcadores de borrado.
- **Cámara postura:** opciones Automático/Ahorro/objetivo 60/objetivo 120. Consulta rangos normales anunciados por Camera2 y solicita una frecuencia de vista previa mediante CameraX **solo si está anunciada**; recupera el modo automático si falla el enlace solicitado. Presenta análisis/s, imágenes entregadas/s, latencia, resolución y tasa de refresco anunciada por pantalla. Esas métricas no equivalen a FPS de captura exactos ni garantizan 120 FPS.
- **Cámara Courtside:** se retira un límite fijo de 100 ms entre inferencias activas. El procesamiento sigue serializado y CameraX descarta fotogramas viejos mediante `KEEP_ONLY_LATEST`. La tasa puede bajar por carga real y deberá medirse en un teléfono físico.
- **Navegación:** cinco opciones: Inicio, Entrenar, Visión, FIT y Progreso. La pantalla Cancha Courtside y Mi equipo siguen disponibles desde Inicio; Perfil desde la esquina superior. Paleta crema, grafito y verde; el tema oscuro completo aún es pendiente.

## Limitaciones deliberadas

Esta versión **no incorpora detector especializado de aro entrenado y evaluado**, varios balones independientes, clasificación fiable de encestes desde cualquier ángulo, reconocimiento facial, calorías clínicas, pasos históricos no observados, pulso en directo, BLE, Health Connect, GPS, notificaciones de chat ni moderación avanzada. Las funciones previas de grupos necesitan un proyecto Firebase del propietario y configuración controlada. El motor existente de canchas detecta la categoría COCO `sports ball`, personas y aro calibrado manualmente. Los resultados de tiro probables siguen necesitando revisión.

No se ha probado ninguna nueva interfaz en dispositivo físico o emulador Android en el entorno de esta edición. Se ejecutaron **28 casos sintéticos** de reglas Kotlin (pasos, reinicios, día, FPS y selección de borrado). Se añadieron tres casos Robolectric de borrado/migración al código, **aún pendientes de ejecución con Android SDK y Gradle**.

## Herramientas y versiones fijadas

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle Wrapper y CI | 8.9 |
| Kotlin Gradle Plugin | 2.3.20 |
| JDK requerido | 17 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| CameraX | 1.4.2 |
| MediaPipe Tasks Vision | 0.10.21 |
| Firebase BoM | 33.10.0 |
| Robolectric | 4.14.1 |

## Compilar en GitHub desde Android

1. Extrae el ZIP. Sube **todos los archivos, incluidas las carpetas `.github/` y `gradle/`**, a un repositorio GitHub privado nuevo. No incluyas tus contraseñas, claves privadas o tokens.
2. Abre ese repositorio en GitHub → **Actions** → **PanaHoops FIT - compilar APK de prueba** → **Run workflow**.
3. Si pasa el paso de pruebas/lint/compilación, abre **Artifacts** y descarga **PanaHoops-FIT-debug-APK**. Extrae `app-debug.apk` del ZIP descargado.
4. En Android abre el APK y concede el permiso de instalación para el gestor de archivos solo si confías en el contenido. Para usar pasos, abre FIT, concede actividad física y pulsa iniciar seguimiento. Se mostrará una notificación permanente mientras esté activo.
5. Si recibes **«La aplicación no se instaló»** sobre Courtside 3.0: la firma de la APK existente es distinta de la nueva firma temporal de GitHub. **No desinstales sin antes exportar tu copia JSON**. Para actualización sin borrar datos necesitas firmar con **la misma clave de la instalación previa**. Esa clave no se entregó en la base; no puede recuperarse del APK.

La prueba local sin Android SDK usa `./verification/run-core.sh` (requiere Kotlin CLI y JDK). La compilación Android real usa `gradle --no-daemon testDebugUnitTest lintDebug assembleDebug` en GitHub Actions. El flujo deja la firma temporal de Android Gradle Plugin para pruebas; no es una release comercial y **el certificado puede variar entre ejecuciones**.

## Protección de datos y migración

Mantén la instalación original hasta importar y comprobar el respaldo. La migración de DB1 a DB2 es aditiva. El sensor físico de pasos no se reinicia; un reseteo establece una nueva referencia de lectura. El reseteo local **no elimina publicaciones de estadísticas ya compartidas en Firebase** ni los registros originales de Health Connect. Esas eliminaciones deben ejecutarse en su origen con autorización específica. No se importan secretos de Firebase: utiliza el proceso de configuración descrito en `README-Courtside.md`, con reglas cerradas por defecto de `backend/firestore.rules`.

## Continuidad del trabajo

Este ZIP es el punto de recuperación. Revisa `VALIDACION-FIT.md` para comprobaciones pendientes, y el código de `ResetManager`, `FitService`, `TrainingStore`, `CameraPerformance` y modelos Kotlin para retomar. No existe un mecanismo para que una conversación reanude automáticamente cuando se regenere una cuota.

## Cambio en 3.1.1

La longitud de zancada se configura desde FIT. La distancia mostrada es una estimación que depende de los pasos observados y de esa longitud. Se añadieron seis aserciones locales; no se ha compilado en Android.
