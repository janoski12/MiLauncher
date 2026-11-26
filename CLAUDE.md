# Mi Launcher — instrucciones para Claude

Launcher Android para una radio Bowmann DD-7000A (pantalla 9" horizontal, 1280x720, 2 GB de RAM)
en un Mitsubishi Lancer GLX 2010 blanco. El dueño habla español: responde, comenta el código y
escribe los commits en español.

## Reglas del proyecto
- **Sin dependencias externas.** Solo el SDK de Android (Kotlin, `Activity`, vistas clásicas). No agregues
  AndroidX ni librerías: la radio tiene poca RAM.
- **minSdk 24.** Muchas radios chinas dicen "Android 11/12" pero corren una versión más antigua.
  No uses APIs nuevas sin comprobar `Build.VERSION.SDK_INT`.
- **Mantén el diseño:** fondo oscuro, rojo (`accent`) como la iluminación del tablero, blanco perla y plata.
  Colores en `res/values/colors.xml`.
- Pensado para usarse manejando: botones grandes, textos cortos, nada que exija precisión.
- Si cambias cómo se usa o configura la app, actualiza el `README.md` (está escrito para el dueño, no para desarrolladores).

## Estructura
- `MainActivity.kt`: pantalla principal (reloj, velocímetro GPS, música, accesos rápidos).
- `SpeedGaugeView.kt`: velocímetro dibujado a mano.
- `Slots.kt`: accesos rápidos y su detección automática por paquete / palabra clave.
- `Apps.kt`: lista de apps e íconos en caché (se invalida con avisos de instalación/desinstalación).
- `AppsActivity.kt`: cajón con todas las apps.
- `SwipeDetector.kt`: gesto de deslizar (derecha→izquierda abre el cajón; al revés lo cierra).
- `Splash.kt`: logo de la marca (`res/drawable-nodpi/logo_marca.png`) al arrancar o despertar la radio.
- `TripComputer.kt`: km, tiempo y máxima del viaje (sin Android, para poder probarlo). El tiempo usa
  `SystemClock.elapsedRealtime()`, nunca la hora del día: la radio arranca con el reloj equivocado.
- `MediaListenerService.kt`: servicio vacío para tener acceso a notificaciones (leer la canción).

## Compilar en una sesión en la nube
1. Si no hay Android SDK, descarga las command-line tools de
   `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip`, instala
   `platforms;android-34` y `build-tools;34.0.0` en el directorio scratchpad, y crea `local.properties`
   con `sdk.dir=...` (está en `.gitignore`; bórralo al terminar si quieres).
2. `gradlew` no tiene permiso de ejecución en git: usa `bash ./gradlew assembleRelease`.
3. El APK queda en `app/build/outputs/apk/release/app-release.apk`. Entrégalo al usuario como
   `MiLauncher.apk`.

## Pruebas
- `bash ./gradlew testReleaseUnitTest` (en `app/src/test`). junit es solo para las pruebas: no va en el APK.
- Deja la lógica pura (sin `Context`, `Location`, etc.) en funciones o clases aparte para poder probarla.

## Firma del APK (importante)
Android solo instala un APK encima del anterior si viene firmado con **la misma llave**.
- La llave del dueño son `milauncher.jks` + `keystore.properties` en la raíz del proyecto. **Nunca** se
  suben a git (están en `.gitignore`) y no se crea una nueva.
- **Antes de compilar un APK para instalar, pide al usuario que suba esos dos archivos** si no están en
  la raíz. No compiles para entregar sin ellos: saldría firmado con la llave de debug y tendría que
  desinstalar la app (perdiendo la configuración).
- Después de compilar, verifica la firma con `apksigner verify --print-certs`: debe decir `CN=Mi Launcher`.
- No ejecutes `./gradlew crearLlave`: es solo para crear una llave nueva y el dueño ya tiene una.

## Versión
En cada APK que se entregue, sube `versionCode` en `app/build.gradle.kts` (y `versionName`, por
ejemplo 1.1, 1.2…). Android no instala una versión con `versionCode` menor que la instalada.

## Git
- El repositorio es `janoski12/MiLauncher`, rama principal `master`.
- GitGuardian marca como "Generic Password" la tarea `crearLlave` de `app/build.gradle.kts`: es un falso
  positivo (la contraseña se genera al azar en el PC y nunca se escribe en el código).
