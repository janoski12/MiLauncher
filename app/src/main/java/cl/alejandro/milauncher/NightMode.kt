package cl.alejandro.milauncher

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Modo noche automático: desde la puesta del sol hasta el amanecer, una capa oscura
 * (con un toque rojo, como el tablero de noche) cubre la pantalla y baja el brillo de todo.
 *
 * La hora del sol se calcula con la última ubicación GPS conocida (sin GPS: Santiago).
 * La capa no recibe toques: los botones siguen funcionando igual.
 *
 * Para cambiar qué tan oscuro queda, edita NIGHT_COLOR (los dos primeros dígitos son la opacidad).
 * Para desactivarlo, pon ENABLED = false.
 */
class NightMode(private val activity: Activity) {

    private val prefs = activity.getSharedPreferences("general", Context.MODE_PRIVATE)
    private var lat = prefs.getFloat("night_lat", DEFAULT_LAT).toDouble()
    private var lon = prefs.getFloat("night_lon", DEFAULT_LON).toDouble()
    private var isNight: Boolean? = null

    private val overlay = View(activity).apply {
        setBackgroundColor(NIGHT_COLOR)
        alpha = 0f
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        // Encima de toda la pantalla principal (el logo de encendido va aún más arriba).
        activity.findViewById<ViewGroup>(android.R.id.content).addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    /** Guarda la ubicación para calcular el sol (solo si cambió harto, para no escribir a cada rato). */
    fun setLocation(latitude: Double, longitude: Double) {
        if (abs(latitude - lat) < 0.05 && abs(longitude - lon) < 0.05) return
        lat = latitude
        lon = longitude
        prefs.edit().putFloat("night_lat", lat.toFloat()).putFloat("night_lon", lon.toFloat()).apply()
        update()
    }

    /** Revisa si es de noche y aplica el cambio (con fundido, salvo la primera vez). */
    fun update() {
        val night = ENABLED && isNightNow(lat, lon, System.currentTimeMillis())
        if (night == isNight) return
        val first = isNight == null
        isNight = night
        val target = if (night) 1f else 0f
        overlay.animate().cancel()
        if (first) overlay.alpha = target
        else overlay.animate().alpha(target).setDuration(FADE_MS).start()
    }

    companion object {
        const val ENABLED = true
        /** Negro con un poco de rojo, al 45 % de opacidad. */
        private const val NIGHT_COLOR = 0x73050000
        private const val FADE_MS = 1500L
        private const val DEFAULT_LAT = -33.45f   // Santiago
        private const val DEFAULT_LON = -70.66f
        private const val NEVER_RISES = -1.0
        private const val NEVER_SETS = -2.0

        /** ¿El sol está bajo el horizonte en ese lugar y momento? */
        fun isNightNow(lat: Double, lon: Double, nowMillis: Long): Boolean {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMillis }
            val day = cal.get(Calendar.DAY_OF_YEAR)
            val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
            val rise = sunHourUtc(day, lat, lon, rising = true)
            val set = sunHourUtc(day, lat, lon, rising = false)
            // Casos extremos (cerca de los polos): ese día el sol no sale, o no se pone.
            if (rise == NEVER_RISES || set == NEVER_RISES) return true
            if (rise == NEVER_SETS || set == NEVER_SETS) return false
            val isDay = if (rise < set) hour >= rise && hour < set else hour >= rise || hour < set
            return !isDay
        }

        /**
         * Hora UTC (0–24) de la salida o puesta del sol. Algoritmo clásico del "Almanac for Computers"
         * (error de un par de minutos, de sobra para esto). Si ese día el sol no sale o no se pone,
         * devuelve NEVER_RISES o NEVER_SETS.
         */
        private fun sunHourUtc(dayOfYear: Int, lat: Double, lon: Double, rising: Boolean): Double {
            val rad = Math.PI / 180.0
            val lngHour = lon / 15.0
            val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
            val m = 0.9856 * t - 3.289
            val l = norm(m + 1.916 * sin(m * rad) + 0.020 * sin(2 * m * rad) + 282.634, 360.0)
            var ra = norm(atan(0.91764 * tan(l * rad)) / rad, 360.0)
            ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
            ra /= 15.0
            val sinDec = 0.39782 * sin(l * rad)
            val cosDec = cos(asin(sinDec))
            val zenith = 90.833   // borde del sol + refracción
            val cosH = (cos(zenith * rad) - sinDec * sin(lat * rad)) / (cosDec * cos(lat * rad))
            if (cosH > 1) return NEVER_RISES
            if (cosH < -1) return NEVER_SETS
            val h = (if (rising) 360.0 - acos(cosH) / rad else acos(cosH) / rad) / 15.0
            val localT = h + ra - 0.06571 * t - 6.622
            return norm(localT - lngHour, 24.0)
        }

        private fun norm(v: Double, range: Double): Double {
            var r = v % range
            if (r < 0) r += range
            return r
        }
    }
}
