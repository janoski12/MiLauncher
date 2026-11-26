package cl.alejandro.milauncher

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computador de viaje: kilómetros, tiempo y velocidad máxima desde que se encendió la radio.
 *
 * No usa nada de Android (así se puede probar en el PC): recibe las lecturas del GPS ya
 * convertidas en números.
 *
 * El tiempo se mide con el reloj "desde que arrancó la radio" (SystemClock.elapsedRealtime),
 * NO con la hora del día: las radios no tienen pila para el reloj y al arrancar tienen una hora
 * cualquiera, que después se corrige sola por GPS o internet. Con la hora del día, ese salto
 * dejaba el tiempo del viaje en un número absurdo.
 */
class TripComputer {

    /** Metros recorridos. */
    var meters = 0f
        private set

    /** Velocidad máxima confirmada (km/h). */
    var maxKmh = 0f
        private set

    /** Inicio del viaje, en milisegundos desde que arrancó la radio. */
    var startElapsed = 0L
        private set

    private var prevKmh = 0f          // lectura anterior, para ignorar saltos sueltos del GPS
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastTime = -1L        // -1 = sin lectura anterior

    /** Empieza un viaje nuevo. */
    fun reset(nowElapsed: Long) {
        meters = 0f
        maxKmh = 0f
        prevKmh = 0f
        lastTime = -1L
        startElapsed = nowElapsed
    }

    /**
     * Retoma un viaje guardado (la app se cerró y se volvió a abrir sin que la radio se reiniciara).
     * Si el inicio guardado es imposible (mayor que el reloj actual: la radio se reinició), empieza de cero.
     */
    fun restore(meters: Float, maxKmh: Float, startElapsed: Long, nowElapsed: Long) {
        if (startElapsed < 0L || startElapsed > nowElapsed) {
            reset(nowElapsed)
            return
        }
        this.meters = meters.coerceAtLeast(0f)
        this.maxKmh = maxKmh.coerceAtLeast(0f)
        this.startElapsed = startElapsed
        prevKmh = 0f
        lastTime = -1L
    }

    /** Minutos de viaje (nunca negativo). */
    fun elapsedMinutes(nowElapsed: Long): Long = ((nowElapsed - startElapsed) / 60_000L).coerceAtLeast(0L)

    /** Se perdió la señal: la próxima velocidad no se puede confirmar con la anterior. */
    fun signalLost() {
        prevKmh = 0f
    }

    /**
     * Una lectura del GPS. [accuracy] en metros (null = el GPS no la informa), [timeElapsed] en ms
     * desde que arrancó la radio. Devuelve true si cambió la velocidad máxima.
     */
    fun onFix(lat: Double, lon: Double, accuracy: Float?, kmh: Float, timeElapsed: Long): Boolean {
        val good = accuracy == null || accuracy <= MAX_ACCURACY_M
        val maxChanged = updateMax(good, kmh)
        if (good) updateDistance(lat, lon, kmh, timeElapsed)
        return maxChanged
    }

    /**
     * Solo cuenta una velocidad si se repite en dos lecturas seguidas (se usa la menor de las dos)
     * y la señal es buena: así un salto falso del GPS no deja marcado "180".
     */
    private fun updateMax(good: Boolean, kmh: Float): Boolean {
        val confirmed = if (good) minOf(kmh, prevKmh) else 0f
        prevKmh = if (good) kmh else 0f
        if (confirmed >= maxKmh + 1f) {
            maxKmh = confirmed
            return true
        }
        return false
    }

    /** Suma la distancia entre lecturas buenas, solo en movimiento (detenido el GPS "camina" solo). */
    private fun updateDistance(lat: Double, lon: Double, kmh: Float, time: Long) {
        if (kmh >= MIN_MOVING_KMH && lastTime >= 0L && time > lastTime) {
            val d = distanceMeters(lastLat, lastLon, lat, lon)
            // Un salto más rápido que un auto (250 km/h) es un error del GPS, no un trayecto.
            // Se compara con el tiempo entre lecturas: si el GPS estuvo apagado un rato
            // (por ejemplo, con otra app en pantalla), se suma el tramo en línea recta.
            val seconds = ((time - lastTime) / 1000f).coerceAtLeast(1f)
            if (d <= seconds * MAX_PLAUSIBLE_MPS) meters += d
        }
        lastLat = lat
        lastLon = lon
        lastTime = time
    }

    companion object {
        const val MAX_ACCURACY_M = 25f
        const val MIN_MOVING_KMH = 3f
        const val MAX_PLAUSIBLE_MPS = 70f   // ~250 km/h

        /** Distancia en metros entre dos puntos (fórmula del haversine, error < 0,5 %). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return (2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))).toFloat()
        }

        /** "1:05" = una hora y cinco minutos. */
        fun formatMinutes(minutes: Long): String {
            val m = minutes.coerceAtLeast(0L)
            return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
        }

        /** Grados (0 = norte) a punto cardinal en español: N, NE, E, SE, S, SO, O, NO. */
        fun compassPoint(bearing: Float): String {
            val points = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
            return points[(((bearing % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8]
        }
    }
}
