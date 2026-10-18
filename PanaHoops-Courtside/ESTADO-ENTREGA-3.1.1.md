# Estado de entrega: PanaHoops FIT 3.1.1

## Confirmado en este entorno
- Fuente extendida de Courtside 3.0 beta, mismo `applicationId` (`com.panahoops.courtside`).
- Servicio FIT, contador de pasos Android, anotaciones manuales, opciones de FPS, restauración y restablecimiento disponibles en el código.
- Configuración persistente de longitud de zancada (40–150 cm), distancia estimada basada en ese valor y seis nuevas aserciones.
- 28 aserciones Kotlin puras ejecutadas sin error (`verification/run-core.sh`).
- Verificación estática de inventario de código, manifiesto y modelo: pasó (`verification/check-source.py`).
- Archivo GitHub Actions con `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `apksigner verify` y verificación de paquete y versión.

## NO confirmado
- No se ha generado ninguna APK nueva 3.1.1.
- No se dispone de Android SDK, Gradle instalado ni caché Android en este entorno, y DNS de descargas externas no resuelve.
- No se ha ejecutado el workflow remoto: la conexión GitHub disponible no devuelve repositorios utilizables.
- No se han ejecutado Robolectric, pruebas en emulador, pruebas en teléfono real o mediciones de FPS.
- No están terminados detector de aro especializado, pulso BLE, Health Connect ni sincronización de reseteo entre usuarios.

## Instalar y migrar
El antiguo archivo APK 3.0 NO ha sido modificado. Tras subir este código a un repositorio GitHub privado, ejecutar el workflow y confirmar compilación y firma, se descargará un APK debug de prueba. Ese APK podrá tener certificado distinto del original: antes de desinstalar o intentar sustituir la versión 3.0, exporta una copia de datos JSON y conserva el original. El APK de prueba no es una versión comercial ni garantiza actualización en sitio.
