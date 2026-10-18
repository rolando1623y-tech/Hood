# Validación · PanaHoops Courtside 3.0 beta

- APK: 48039366 bytes, ARM64, Android 8+, paquete `com.panahoops.courtside`, versión 300.
- `testDebugUnitTest`: 41 pruebas, cero errores y cero fallos.
- Firestore Emulator: 17 pruebas de permisos aprobadas; archivo `backend/rules.test.cjs`.
- Android Lint: cero errores; 98 avisos, principalmente textos localizables, actualizaciones de dependencias y estilo de interfaz.
- Compilación `assembleRelease` completada; R8 y reducción de recursos habilitados.
- Firma APK v2 verificada y alineación de empaquetado a 16 KiB comprobada.
- Detectores de postura y de personas/balones incluidos en el APK; solo bibliotecas nativas ARM64.
- Cinco pantallas nativas renderizadas con Robolectric; revisión visual de Inicio completada.

## Firma para configurar Google

SHA-1 del certificado de ESTA entrega:

`BC:BF:7B:E0:05:8B:8A:79:22:65:1F:AF:53:6E:0C:8E:9E:07:4D:82`

SHA-256 del certificado:

`DC:39:0C:F9:EE:0C:38:B5:2F:E8:D3:81:C8:FA:F8:53:43:C1:8F:01:98:9C:CD:30:53:38:A1:45:71:88:B0:EB`

SHA-256 del archivo APK:

`2126ab1f84787f7ba920cacfb04c91eebdcf61e973661cef43ac802a96175173`

La clave privada de desarrollo no se incluye. Una compilación con otro certificado necesita registrar su propia huella en Firebase/Google y no actualizará directamente esta instalación.

## Límites de estas comprobaciones

Las pruebas del contador y seguimiento del aro usan datos sintéticos. No miden precisión sobre grabaciones reales, reconocimiento de personas en una cancha ni rendimiento en un Redmi 14C. No hubo cámara física disponible. Google Sign-In, cuentas de correo y chat entre teléfonos no se probaron contra un proyecto real, porque esta entrega no tiene configuración de servicio. Las reglas se probaron en el emulador, sin publicar un servidor.

## Modelo y documentación

Detección: EfficientDet Lite0 int8, modelo de Google MediaPipe, categorías COCO `person` y `sports ball`; no detecta aros automáticamente.

- https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite
- https://developers.google.com/edge/mediapipe/solutions/vision/object_detector/android
- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/identity/sign-in/credential-manager-siwg

Se excluye el procesador `com.google.auto.value:auto-value` de las dependencias de ejecución; las anotaciones necesarias se conservan. El procesador no forma parte de una aplicación Android en ejecución.
