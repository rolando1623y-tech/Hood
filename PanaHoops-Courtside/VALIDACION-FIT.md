# Estado de validación FIT 3.1.1

**Fecha de trabajo:** 2026-10-18.

## Comprobaciones realmente ejecutadas

- ✅ `verification/run-core.sh`: **28 casos sintéticos**, sin errores. Entorno de pruebas: `kotlinc-jvm 1.9.0`, OpenJDK 21. Casos: línea de base del sensor, suma de pasos, duplicados, descenso del contador por reinicio, cambio de ID de arranque, cambio de día, reseteo de referencia, muestra temporal antigua, saltos anómalos, rangos de cámara 30/60/120 y selección de borrado por fecha, jugador, modalidad y tipo. La ejecución local usa Kotlin 1.9 para probar reglas escritas con sintaxis compatible; **no valida el plugin Gradle 2.3.20**.
- ✅ El ZIP base Courtside fue inspeccionado: contiene 16 archivos Java fuente principales, casos JUnit, modelo TFLite, reglas Firestore y wrapper Gradle. No se ha sustituido por una app WebView.
- ✅ Migración en el código: DB SQLite v1 → v2 sin ejecutar `DROP TABLE`; ID Android conservado. **Inspección estática solamente**, falta instrumentación.

## Comprobaciones agregadas pero NO ejecutadas

- ⚪ `FitAndResetTest.java` (Robolectric): conservación de datos de perfil, eliminación controlada y recuperación desde copia bloqueada por tombstones, eliminación por jugador y migración desde DB v1.
- ⚪ `testDebugUnitTest`, `lintDebug`, `assembleDebug`, análisis manifest y verificación de firma con `apksigner`.
- ⚪ Arranque en Android 8, 10, 14 y 16; servicio `health` con pantalla apagada, permisos revocados y reinicios del teléfono.
- ⚪ FPS reales de pantalla, inferencia y vista previa en Redmi 14C.
- ⚪ Tiros de distintos ángulos anotados manualmente, detección del aro, seguimiento multiusuario y casos dudosos.
- ⚪ Firebase con más de un usuario real, reglas con emulador, autenticación Google/correo y mensajes.

## Motivo de bloqueo de APK actualizada

Este contenedor no dispone de un Android SDK, ni Gradle instalado, ni caché de dependencias Android. El acceso DNS a `dl.google.com` está bloqueado en este entorno. Se puede validar Kotlin puro con el compilador local; **no se puede crear ni probar una nueva APK aquí**. El artefacto APK Courtside 3.0 beta adjunto como referencia NO contiene la implementación FIT añadida al código 3.1.

## Restricciones actuales

- Formato SQLite original conserva `SharedPreferences` para tiros Courtside, lo que no aporta transacciones atómicas entre ambos repositorios. La selección de borrado es transaccional para SQLite y usa confirmación para las preferencias; sigue pendiente la consolidación Room.
- Reset local no sincroniza ni retira estadísticas de la nube. Una copia importada antigua no revive IDs eliminados del registro local, pero no garantiza consistencia multi-dispositivo.
- Se requiere evaluación real de varios días para comprobar que el contador del fabricante sigue produciendo pasos con pantalla apagada. No se presentan como pasos del día completo antes del primer registro.
- No hay salida Kotlin compilada en Android ni capturas de pantalla reales de la versión 3.1.

## Criterios para declarar «APK terminada»

1. Workflow Android verde con pruebas, lint y `assembleDebug`.
2. APK generada por ese flujo, instalada y verificada en dispositivo o emulador; versión 311 y paquete `com.panahoops.courtside`.
3. Tests de migración y reseteo correctos, FIT con permiso denegado y sensor ausente sin crash.
4. Firebase verificado con cuentas reales antes de asegurar funciones en línea.
5. Mismo certificado de firma que la instalación anterior si se pretende actualizar en sitio.

Documentación oficial relevante:
- https://developer.android.com/reference/android/hardware/Sensor#TYPE_STEP_COUNTER
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/reference/androidx/camera/core/Preview.Builder#setTargetFrameRate(android.util.Range)
- https://developer.android.com/health-and-fitness/health-connect/read-data
- https://kotlinlang.org/docs/gradle-configure-project.html
