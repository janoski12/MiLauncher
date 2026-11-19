# Mi Launcher — pantalla de inicio para la Bowmann DD-7000A

Launcher propio para un **Mitsubishi Lancer GLX 2010 blanco**: oscuro, con rojo y detalles plateados, pensado para una pantalla de 9" horizontal (1024x600) y una radio con 2 GB de RAM. No usa librerías externas, así que es liviano.

**Pantalla principal**
- Reloj y fecha grandes
- Velocímetro circular tipo tablero con el GPS de la radio (km/h)
- Música: canción, artista, carátula y botones anterior / play-pausa / siguiente (Spotify, YouTube Music o cualquier app que use los controles estándar de Android)
- 6 accesos rápidos: Radio FM, CarPlay/Android Auto, Waze, Música, Teléfono, Ajustes, más un botón **Apps** con todas las apps

---

## 1. Compilar con Claude Code

Necesitas en el PC:
- **JDK 17** (o el que trae Android Studio)
- **Android SDK** con *Android SDK Platform 34* y *Build-Tools 34*. Lo más fácil es instalar Android Studio una vez (trae el SDK); después no hace falta abrirlo.

Abre una terminal en esta carpeta, ejecuta `claude` y pégale esto:

> Este es un proyecto Android (Kotlin, sin dependencias externas). Compílalo con `./gradlew assembleRelease` (en Windows `gradlew.bat assembleRelease`). Si falta `local.properties`, créalo con `sdk.dir` apuntando a mi Android SDK. Si hay errores de compilación, corrígelos sin cambiar el diseño ni las funciones. Al final dime la ruta del APK.

El APK queda en `app/build/outputs/apk/release/app-release.apk`. Viene firmado con la llave de debug, así que se instala directo.

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
2. **Ubicación:** acepta el permiso para el velocímetro. Si lo rechazaste, toca el cuadro del reloj y se abre la ficha de la app para activarlo.
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
| Fondo degradado | `app/src/main/res/drawable/bg_main.xml` |
| Botones de abajo (nombres, apps que detecta) | `app/src/main/java/cl/alejandro/milauncher/Slots.kt` |
| Tamaños y distribución | `app/src/main/res/layout/activity_main.xml` |
| Textos | `app/src/main/res/values/strings.xml` |

Después de cada cambio, vuelve a compilar e instalar (si el APK tiene la misma firma, se actualiza sin perder la configuración).

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
