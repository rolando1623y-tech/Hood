# Procedencia y dependencias

El código nuevo de reglas de sensores, selección de FPS y reseteo se generó para este proyecto. El detector TFLite existente se conserva sin modificación: EfficientDet Lite0 int8, Google MediaPipe; categorías genéricas `person` y `sports ball` del conjunto COCO. No es un detector de aro especializado.

Dependencias y documentación de las bibliotecas: AndroidX CameraX (licencia Apache 2.0), AndroidX Activity/Core, ML Kit Pose Detection (términos de Google), MediaPipe Tasks Vision (Apache 2.0), Firebase Android SDK (términos de Google), Kotlin (Apache 2.0), JUnit (EPL 1.0), Robolectric (MIT).

No se agregaron fotos, audios ni videos externos en la revisión 3.1. Los esquemas ilustrados de la base Courtside continúan siendo demostraciones simplificadas. Para distribución comercial, revisar los avisos de licencia exactos de cada artefacto y el uso de los servicios Google.
