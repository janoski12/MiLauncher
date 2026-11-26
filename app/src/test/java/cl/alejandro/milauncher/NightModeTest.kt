package cl.alejandro.milauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NightModeTest {

    private val lat = -33.45   // Santiago
    private val lon = -70.66

    /** Momento en UTC. Santiago está entre UTC-3 (verano) y UTC-4 (invierno). */
    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun `mediodia en Santiago es de dia`() {
        assertFalse(NightMode.isNightNow(lat, lon, utc(2026, 1, 15, 16)))   // 13:00 local, verano
        assertFalse(NightMode.isNightNow(lat, lon, utc(2026, 7, 15, 16)))   // 12:00 local, invierno
    }

    @Test
    fun `medianoche en Santiago es de noche`() {
        assertTrue(NightMode.isNightNow(lat, lon, utc(2026, 1, 15, 3)))     // 00:00 local
        assertTrue(NightMode.isNightNow(lat, lon, utc(2026, 7, 15, 4)))
    }

    @Test
    fun `invierno anochece antes que verano`() {
        // 19:30 local: en julio ya es de noche (el sol se pone ~18:00), en enero no (~20:50).
        assertTrue(NightMode.isNightNow(lat, lon, utc(2026, 7, 15, 23, 30)))
        assertFalse(NightMode.isNightNow(lat, lon, utc(2026, 1, 15, 22, 30)))
    }

    @Test
    fun `cerca de los polos no falla`() {
        // Junio en el polo norte: el sol no se pone. Junio en el polo sur: no sale.
        assertFalse(NightMode.isNightNow(85.0, 0.0, utc(2026, 6, 21, 0)))
        assertTrue(NightMode.isNightNow(-85.0, 0.0, utc(2026, 6, 21, 12)))
    }
}
