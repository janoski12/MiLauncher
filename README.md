# Mi Launcher — pantalla de inicio para la Bowmann DD-7000A

Launcher propio para un **Mitsubishi Lancer GLX 2010 blanco**: oscuro, con rojo y detalles plateados, pensado para una pantalla de 9" horizontal (1280x720) y una radio con 2 GB de RAM. No usa librerías externas, así que es liviano.

**Al encender la radio** los tres diamantes de Mitsubishi llegan uno por uno, aparece "MITSUBISHI" y luego "LANCER GLX". Dura 3 segundos (tocar la pantalla lo salta).

Detalles del Lancer en la pantalla: una foto del auto entre el velocímetro y la música (con "LANCER GLX 2010" debajo), los tres diamantes rojos junto al nombre del auto y abajo del velocímetro, y el logo con la foto arriba del cajón de apps.

**Al dar contacto**, cuando se va el logo, el velocímetro hace un barrido hasta 200 y vuelve, como el tablero.

**Velocidad máxima del viaje:** aparece como "MÁX" dentro del velocímetro y vuelve a cero cada vez que se enciende la radio. Ignora los saltos falsos del GPS.

**Computador de viaje:** bajo la foto del auto se ven los kilómetros recorridos y el tiempo desde que se encendió la radio. También vuelven a cero al encenderla.

**Brújula:** en movimiento, el rumbo (N, NE, E, SE, S, SO, O, NO) aparece arriba del número del velocímetro.

**Alerta de velocidad:** una marca blanca en el arco indica el límite (por defecto 120 km/h). Si lo pasas, el número se pone rojo y el aro parpadea. **Mantén presionado el velocímetro** para cambiar el límite (60, 80, 100, 120) o quitar la alerta.

**Música:** la carátula del álbum se ve difuminada de fondo, y una barra muestra cuánto lleva la canción y cuánto le falta (si la app de música lo informa).

**Carátula con Bluetooth:** el Bluetooth de la radio normalmente no envía la carátula. Si no llega, el launcher la busca en internet (catálogo de iTunes) con el título y el artista, y la guarda para no volver a buscarla. Necesita internet en la radio (por ejemplo, el hotspot del teléfono). Para que el teléfono Android intente enviarla por Bluetooth: activa las *Opciones de desarrollador* (toca 7 veces *Número de compilación* en *Ajustes > Acerca del teléfono*), busca **Versión de AVRCP de Bluetooth**, elige **1.6** y vuelve a conectar el Bluetooth.

**Saludo:** arriba del auto dice "Buenos días", "Buenas tardes" o "Buenas noches" con tu nombre.

**Modo noche automático:** desde la puesta del sol hasta el amanecer, la pantalla se pone más tenue. La hora del sol se calcula con tu ubicación GPS (sin señal, usa la de Santiago).

**Pantalla principal**
- Reloj y fecha grandes
- Velocímetro circular tipo tablero con el GPS de la radio (km/h)
- Música: canción, artista, carátula y botones anterior / play-pausa / siguiente (Spotify, YouTube Music o cualquier app que use los controles estándar de Android)
- 6 accesos rápidos: Radio FM, CarPlay/Android Auto, Waze, Música, Teléfono, Ajustes, más un botón **Apps** con todas las apps
- **Desliza el dedo de derecha a izquierda** en cualquier parte de la pantalla para abrir todas las apps; de izquierda a derecha (o *Atrás*) para cerrarlas

---

## 1. Compilar con Claude Code

Necesitas en el PC:
- **JDK 17** (o el que trae Android Studio)
- **Android SDK** con *Android SDK Platform 34* y *Build-Tools 34*. Lo más fácil es instalar Android Studio una vez (trae el SDK); después no hace falta abrirlo.

Abre una terminal en esta carpeta, ejecuta `claude` y pégale esto:

> Este es un proyecto Android (Kotlin, sin dependencias externas). Compílalo con `./gradlew assembleRelease` (en Windows `gradlew.bat assembleRelease`). Si falta `local.properties`, créalo con `sdk.dir` apuntando a mi Android SDK. Si hay errores de compilación, corrígelos sin cambiar el diseño ni las funciones. Al final dime la ruta del APK.

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

### Firma del APK (hacer una sola vez)
Android solo deja **actualizar** una app si el APK nuevo viene firmado con la **misma llave** que el instalado. Por eso el proyecto usa una llave propia en vez de la de debug, que es distinta en cada PC.

1. Antes de compilar por primera vez, crea la llave:
   `./gradlew crearLlave` (en Windows `gradlew.bat crearLlave`)
2. Se crean dos archivos en esta carpeta: `milauncher.jks` y `keystore.properties`. **Respáldalos** (pendrive, nube). Si los pierdes o cambias de PC sin ellos, tendrás que desinstalar la app para instalar una versión nueva, y se pierde la configuración de los botones.
3. Para compilar en otro PC, copia esos dos archivos a la carpeta del proyecto.

No se suben a git (están en `.gitignore`). Si no existe `keystore.properties`, el APK se firma con la llave de debug de ese PC y Gradle muestra un aviso.

**Si ya tienes instalada una versión firmada con la llave de debug**, tienes dos opciones:
- **Desinstalarla** una vez e instalar la nueva (tendrás que volver a configurar los botones y permisos).
- **Seguir usando esa llave**: en el PC donde compilaste esa versión, copia `debug.keystore` (está en `C:\Users\TU_USUARIO\.android\` o en `~/.android/`) a esta carpeta y crea `keystore.properties` a mano con:
  ```
  storeFile=debug.keystore
  storePassword=android
  keyAlias=androiddebugkey
  keyPassword=android
  ```
  Así las actualizaciones se instalan encima sin desinstalar.

## 2. Instalar en la radio

**Opción A, pendrive:**
1. Copia `app-release.apk` a un pendrive y conéctalo a la radio.
2. En la radio, abre el explorador de archivos y toca el APK.
3. Si te pide permitir "orígenes desconocidos" / "instalar apps desconocidas", acéptalo para el explorador de archivos.

**Opción B, WiFi con adb** (si la radio tiene depuración USB/ADB en las opciones de desarrollador):
`adb connect IP_DE_LA_RADIO:5555` y luego `adb install -r app-release.apk`.

## 3. Primera configuración

1. Presiona **Home**, elige **Mi Launcher** y toca **Siempre**.
   Si no aparece la pregunta: *Ajustes > Aplicaciones > Apps predeterminadas > App de inicio*.
2. **Ubicación:** acepta el permiso para el velocímetro y elige **ubicación precisa**. Con la "aproximada" Android no entrega la velocidad, así que el velocímetro se queda en "--" y aparece *"Toca y elige ubicación precisa"*.
   Si rechazaste el permiso o elegiste la aproximada, toca el cuadro del reloj: vuelve a preguntar o, si Android ya no deja preguntar, abre la ficha de la app (*Permisos > Ubicación > Permitir* y activa *Usar ubicación precisa*).
3. **Música:** toca la tarjeta de música y activa **Mi Launcher** en "Acceso a notificaciones". Es lo que permite ver la canción y la carátula.
   - En Android 13 o superior puede salir "Configuración restringida". Ve a *Ajustes > Apps > Mi Launcher*, toca el menú ⋮ > **Permitir configuración restringida** y vuelve a intentarlo.
   - Si tu radio no tiene ese ajuste, los botones de música funcionan igual. Solo no se verá el nombre de la canción.
4. **Accesos rápidos:** se detectan solos. Si alguno abre la app equivocada o muestra "+", **mantenlo presionado** y elige la app (por ejemplo, la app de CarPlay de tu radio, que suele llamarse ZLink o Carlink).

## Si la radio vuelve al launcher de fábrica al encender
Pasa en algunas radios chinas. Instala **MacroDroid** y crea una macro:
- Disparador: *Inicio del dispositivo*
- Acción: *Abrir aplicación > Mi Launcher*

## Volver al launcher original
*Ajustes > Aplicaciones > Apps predeterminadas > App de inicio* y elige el original. O desinstala Mi Launcher desde *Ajustes > Aplicaciones*.

---

## Ajustes para el Lancer GLX 2010 (blanco)

El launcher ya viene con la paleta del auto: **rojo como la iluminación del tablero**, más **blanco perla y plata** por la carrocería, y un **velocímetro circular** tipo tablero (escala 0–200 km/h). Para que la radio completa combine:

1. **Color de los botones de la radio:** en los ajustes de la radio, busca "Luz de botones" o "Color de iluminación" y elige **rojo**, igual que el tablero.
2. **Logo de encendido:** en *Ajustes de fábrica > Logo* (suele pedir clave: 8888, 1234, 0000 o 3368) elige **Mitsubishi** de la lista que trae la radio.
3. **Controles del volante:** si tu GLX tiene botones de audio en el volante, ve a *Ajustes > Control del volante*, toca cada botón y asígnale su función. Si no responde, puede faltar el cable/adaptador CAN-BUS del arnés para Lancer.
4. **Instalación física:** para una pantalla de 9" en el Lancer 2008–2016 se usa un **marco (bisel) de 9" específico para Lancer**. Si la radio está instalada con un marco genérico, ese marco es el que la hace verse "de fábrica".

Para cambiar el nombre sobre el reloj, edita `car_name` en `strings.xml`. Para cambiar el máximo del velocímetro, edita `maxSpeed` en `SpeedGaugeView.kt`.

## Personalizar

| Quiero cambiar… | Archivo |
|---|---|
| Colores (rojo, fondo, tarjetas) | `app/src/main/res/values/colors.xml` |
| Fondo (fibra de carbono + degradado rojo) | `app/src/main/res/drawable/bg_main.xml` y `res/drawable-nodpi/carbono.png` |
| Botones de abajo (nombres, apps que detecta) | `app/src/main/java/cl/alejandro/milauncher/Slots.kt` |
| Tamaños y distribución | `app/src/main/res/layout/activity_main.xml` |
| Textos | `app/src/main/res/values/strings.xml` |
| Logo al encender (animación, duración o desactivarlo) | `Splash.kt` y `res/drawable-nodpi/logo_texto.png` |
| Foto del auto (PNG sin fondo, unos 700 px de ancho) | `res/drawable-nodpi/auto_lancer.png` |
| Año bajo la foto | `car_year` en `app/src/main/res/values/strings.xml` |
| Modo noche (qué tan oscuro, o desactivarlo) | `NIGHT_COLOR` y `ENABLED` en `NightMode.kt` |
| Nombre del saludo | `driver_name` en `app/src/main/res/values/strings.xml` |
| Límite de velocidad por defecto | `DEFAULT_SPEED_LIMIT` en `MainActivity.kt` |
| Buscar carátulas en internet (o desactivarlo) | `ONLINE_LOOKUP` en `ArtFinder.kt` |
| Duración del barrido del velocímetro | `SWEEP_MS` en `SpeedGaugeView.kt` |
| Diamantes rojos | `res/drawable/ic_diamantes.xml` |

Después de cada cambio, vuelve a compilar e instalar. Mientras uses la misma llave (ver *Firma del APK*), se actualiza sin perder la configuración.

## Estructura
```
app/src/main/
├── AndroidManifest.xml          ← se registra como pantalla de inicio (HOME)
├── java/cl/alejandro/milauncher/
│   ├── MainActivity.kt          ← pantalla principal: reloj, GPS, música, accesos
│   ├── AppsActivity.kt          ← cajón con todas las apps
│   ├── Apps.kt                  ← listar / abrir apps
│   ├── Slots.kt                 ← accesos rápidos y su detección automática
│   ├── SpeedGaugeView.kt        ← velocímetro circular
│   └── MediaListenerService.kt  ← necesario para leer qué canción suena
└── res/                         ← diseños, colores, íconos
```
