# PanaHoops Courtside · 3.0 beta

## Instalar y empezar

1. Descarga el APK y ábrelo en Android. Permite instalar desde la aplicación que abre el archivo cuando Android lo pida.
2. Esta edición requiere Android 8 o posterior y procesador ARM de 64 bits. Está dirigida a teléfonos como el Redmi 14C; no es un APK para emuladores x86.
3. Se instala como **PanaHoops Courtside**, separada de PanaHoops NEXT. No borres la anterior si conserva entrenamientos.
4. Para traer tu historial anterior: en NEXT exporta la copia JSON desde Perfil; en Courtside importa esa copia. Las dos apps tienen datos locales independientes.

## Seguir balón, aro y jugadores

1. En Inicio pulsa **Courtside BETA**, o entra desde Cancha.
2. Coloca el teléfono fijo a un lado o en diagonal, con los jugadores, el balón y el aro dentro de la imagen. Usa buena luz. Un aro o un balón de pocos píxeles no ofrece información suficiente.
3. Activa la cámara trasera y permite el acceso. Toca los dos extremos del aro para calibrar la perspectiva. Pulsa Empezar.
4. El detector incluido reconoce personas y la categoría genérica «sports ball». No es un modelo especializado y validado en baloncesto.
5. El aro marcado se sigue por su textura visual para intentar compensar pequeños movimientos. Si se pierde la referencia se solicita calibración. No reconoce automáticamente un aro nuevo ni permite mover libremente el teléfono.
6. En Jugadores puedes poner nombres, cambiar los roles Tirador/Defensor y fijar el tirador de la serie. Los IDs son temporales: cruzarse, taparse o salir de cámara puede crear otro ID. No hay identificación facial ni reconocimiento persistente de camisetas.
7. Con asignación automática se usa la proximidad reciente del balón al torso superior de un tirador. Esta asignación también puede equivocarse: revisa el jugador y el resultado.
8. Usa **un balón activo por serie**. No se analizan varios tiros simultáneos con distintos balones. Si el detector ve varios balones con suficiente confianza, se suspende esa trayectoria.
9. En **Revisar tiros**, confirma enceste o fallo, cambia el nombre del jugador o elimina el registro. Los controles de revisión pausan el análisis para no registrar una serie mientras editas.

### Qué significan los resultados

- **Enceste probable:** cruce descendente dentro de la proyección del aro y dos observaciones posteriores debajo de él.
- **Fallo probable:** cruce descendente fuera del aro en una trayectoria cercana.
- **Dudoso:** balón perdido, borde ambiguo, trayectoria incompleta, interrupción o falta de salida visible.

Una cámara 2D puede confundir un balón que pasa delante o detrás del aro con un enceste. Estos resultados **no son un arbitraje automático ni una verificación infalible**. Un tiro que nunca se detecta no aparece como intento. La cámara no calcula distancias reales ni puntos de dos/tres automáticamente.

El análisis omite fotogramas acumulados para priorizar imágenes recientes. La vista previa no espera a que termine el detector. Las cajas de los jugadores se suavizan visualmente. Se muestra la tasa observada de análisis, independiente de los FPS de la vista previa. La fluidez y precisión en tu teléfono siguen pendientes de una prueba real.

## Ejercicios y rutinas

En Entrena, toca cualquiera de los 24 ejercicios. Encontrarás una demostración esquemática, pasos escritos y un ejemplo de práctica. El dibujo es una referencia simple, no un video técnico ni una captura de movimiento real. La descripción escrita es la guía específica.

Se mantienen las 12 rutinas, la creación de entrenamientos, los temporizadores, los ocho modos de postura, la revisión de clips de postura y el historial de la edición anterior. La cámara de partido y la cámara de postura son modos distintos; no se ejecutan simultáneamente.

En Jugadores puedes consultar los aciertos/intentos confirmados por nombre y la cantidad de tiros pendientes de revisar. La copia JSON incluye los registros Courtside y admite importar copias anteriores. El CSV general conserva su formato anterior y no incluye los tiros experimentales; usa JSON para respaldarlos.

## Google, correo, grupos y mensajes

**Esta entrega no tiene un servidor Firebase conectado.** La app incluye la integración y las pantallas, pero el acceso real entre teléfonos necesita un proyecto del propietario. No se simulan cuentas ni chats como si estuvieran conectados.

### Activar el servicio una vez

1. Crea un proyecto en https://console.firebase.google.com/ .
2. Añade una app Android con paquete **com.panahoops.courtside**.
3. Añade la huella SHA-1 del APK entregado, indicada en `VALIDACION.md` dentro del código.
4. En Authentication activa **Google** y **Correo/contraseña**. Completa el correo de asistencia y la configuración OAuth solicitada por Google. Si está en pruebas, añade los usuarios de prueba que corresponda.
5. Crea Cloud Firestore en modo nativo. Publica el archivo `backend/firestore.rules` del proyecto. No habilites reglas abiertas de prueba.
6. Descarga el `google-services.json` actualizado después de activar Google.
7. En la app: **Mi equipo → Importar google-services.json**. El archivo debe corresponder al paquete y firma de esta edición. Es configuración pública del cliente; no importes un archivo de cuenta de servicio ni claves privadas.
8. Inicia sesión con Google o crea una cuenta de correo. Para correo también hay verificación y recuperación de contraseña.
9. Crea un grupo y comparte su código de 32 caracteres con las personas que quieras invitar. Cada teléfono debe usar la misma configuración del proyecto y las mismas reglas.
10. Los miembros pueden leer los mensajes mientras el chat está abierto, enviar texto y borrar sus propios mensajes. No hay notificaciones push ni moderación automática en esta beta.
11. En Comparar estadísticas elige tu nombre de jugador de Courtside. Se comparten solo sus tiros confirmados y los tiros manuales del dispositivo. Pulsa Compartir explícitamente. Los resultados probables y dudosos quedan fuera del porcentaje publicado.

Los porcentajes son datos declarados por el jugador, sin protección contra la manipulación de estadísticas. El código privado funciona como invitación: quien lo recibe puede unirse, hasta 50 miembros. Los mensajes se consultan solo dentro del grupo. Se puede salir del grupo y retirar las propias estadísticas; esta beta no incluye expulsiones, rotación de invitaciones ni eliminación completa de cuentas desde la interfaz.

No se envían fotogramas de cámara al servidor. Al usar grupos se envían los mensajes, el nombre de perfil y las estadísticas que eliges compartir. El funcionamiento y los costes del servicio dependen del proyecto Firebase que se configure.

## Compilar con GitHub

El código incluye `.github/workflows/android.yml`. Requiere JDK 17, Android SDK 35 y Gradle 8.9. GitHub no mostró repositorios accesibles durante esta tarea; por eso se usa compilación local.

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

El APK se genera en `app/build/outputs/apk/release/`. La firma de desarrollo sirve para pruebas personales. Para que futuras versiones actualicen la misma instalación debe conservarse la clave de firma; una nueva clave de desarrollo exige otra instalación o desinstalar antes, con copia JSON previa. No se incluye una clave de producción.

## Lo que falta para una versión comercial

Un detector de aro y balón especializado, entrenado y evaluado con grabaciones de distintas canchas y ángulos; validación de encestes y fallos frente a anotaciones humanas; seguimiento persistente de identidades; varios balones simultáneos; mediciones de rendimiento en teléfonos reales; configuración y pruebas entre usuarios del servicio; controles de administración, privacidad y moderación completos; firma y distribución de producción.

Esta beta amplía el proyecto y permite probar sus funciones locales. No se afirma que sea la app más completa del mercado ni que el seguimiento funcione correctamente en cualquier escena.

## Continuidad del trabajo

El código y esta guía permiten retomar la versión. ChatGPT no dispone de un disparador que detecte la renovación de tu cuota y reactive automáticamente esta conversación. Si se interrumpe por límite de uso, vuelve a escribir «continúa PanaHoops Courtside» cuando puedas.
