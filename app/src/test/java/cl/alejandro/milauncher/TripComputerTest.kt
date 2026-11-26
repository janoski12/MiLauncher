package cl.alejandro.milauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripComputerTest {

    // Un grado de latitud son ~111,2 km: 0,0001° ≈ 11 m.
    private val lat0 = -33.45
    private val lon0 = -70.66
    private fun north(meters: Double) = lat0 + meters / 111_195.0

    @Test
    fun `el tiempo cuenta desde el reinicio y no depende de la hora del dia`() {
        val t = TripComputer()
        t.reset(nowElapsed = 10_000L)
        assertEquals(0L, t.elapsedMinutes(10_000L))
        assertEquals(0L, t.elapsedMinutes(69_999L))
        assertEquals(1L, t.elapsedMinutes(70_000L))
        assertEquals(90L, t.elapsedMinutes(10_000L + 90 * 60_000L))
    }

    @Test
    fun `el tiempo nunca es negativo`() {
        val t = TripComputer()
        t.reset(nowElapsed = 100_000L)
        assertEquals(0L, t.elapsedMinutes(5_000L))
    }

    @Test
    fun `restaurar un viaje de la misma sesion lo conserva`() {
        val t = TripComputer()
        t.restore(meters = 1234f, maxKmh = 88f, startElapsed = 60_000L, nowElapsed = 600_000L)
        assertEquals(1234f, t.meters, 0f)
        assertEquals(88f, t.maxKmh, 0f)
        assertEquals(9L, t.elapsedMinutes(600_000L))
    }

    @Test
    fun `restaurar despues de reiniciar la radio empieza de cero`() {
        val t = TripComputer()
        // Inicio guardado mayor que el reloj actual: la radio se reinició.
        t.restore(meters = 5000f, maxKmh = 120f, startElapsed = 9_000_000L, nowElapsed = 30_000L)
        assertEquals(0f, t.meters, 0f)
        assertEquals(0f, t.maxKmh, 0f)
        assertEquals(30_000L, t.startElapsed)
    }

    @Test
    fun `restaurar sin datos guardados empieza ahora`() {
        val t = TripComputer()
        t.restore(meters = 0f, maxKmh = 0f, startElapsed = -1L, nowElapsed = 42_000L)
        assertEquals(42_000L, t.startElapsed)
    }

    @Test
    fun `suma la distancia en movimiento`() {
        val t = TripComputer()
        t.reset(0L)
        // 50 km/h ≈ 13,9 m/s, una lectura por segundo durante 60 s
        for (i in 0..60) t.onFix(north(i * 13.9), lon0, 5f, 50f, i * 1000L)
        assertEquals(60 * 13.9f, t.meters, 3f)
    }

    @Test
    fun `detenido no suma aunque el GPS se mueva solo`() {
        val t = TripComputer()
        t.reset(0L)
        for (i in 0..30) t.onFix(north((i % 2) * 8.0), lon0, 5f, 1f, i * 1000L)
        assertEquals(0f, t.meters, 0f)
    }

    @Test
    fun `ignora lecturas con mala precision`() {
        val t = TripComputer()
        t.reset(0L)
        t.onFix(north(0.0), lon0, 5f, 50f, 0L)
        t.onFix(north(14.0), lon0, 80f, 50f, 1000L)   // mala: no cuenta
        assertEquals(0f, t.meters, 0f)
        t.onFix(north(28.0), lon0, 5f, 50f, 2000L)
        assertEquals(28f, t.meters, 0.5f)
    }

    @Test
    fun `un salto imposible del GPS no se suma`() {
        val t = TripComputer()
        t.reset(0L)
        t.onFix(north(0.0), lon0, 5f, 50f, 0L)
        t.onFix(north(400.0), lon0, 5f, 50f, 1000L)   // 400 m en 1 s = 1440 km/h
        assertEquals(0f, t.meters, 0f)
    }

    @Test
    fun `si el GPS estuvo sin lecturas un rato suma el tramo en linea recta`() {
        val t = TripComputer()
        t.reset(0L)
        t.onFix(north(0.0), lon0, 5f, 60f, 0L)
        // 10 minutos después (otra app en pantalla), 8 km más al norte: 48 km/h, posible.
        t.onFix(north(8000.0), lon0, 5f, 60f, 600_000L)
        assertEquals(8000f, t.meters, 10f)
    }

    @Test
    fun `reiniciar el viaje borra km y maxima`() {
        val t = TripComputer()
        t.reset(0L)
        for (i in 0..10) t.onFix(north(i * 20.0), lon0, 5f, 72f, i * 1000L)
        assertTrue(t.meters > 0f)
        assertTrue(t.maxKmh > 0f)
        t.reset(20_000L)
        assertEquals(0f, t.meters, 0f)
        assertEquals(0f, t.maxKmh, 0f)
        // La primera lectura después del reinicio no suma desde el punto del viaje anterior.
        t.onFix(north(5000.0), lon0, 5f, 72f, 21_000L)
        assertEquals(0f, t.meters, 0f)
    }

    @Test
    fun `la maxima necesita dos lecturas seguidas`() {
        val t = TripComputer()
        t.reset(0L)
        t.onFix(lat0, lon0, 5f, 60f, 0L)
        t.onFix(lat0, lon0, 5f, 60f, 1000L)
        assertEquals(60f, t.maxKmh, 0f)
        // Un pico suelto de 180 no queda marcado
        assertFalse(t.onFix(lat0, lon0, 5f, 180f, 2000L))
        t.onFix(lat0, lon0, 5f, 62f, 3000L)
        assertEquals(62f, t.maxKmh, 0f)
    }

    @Test
    fun `la maxima no se confirma a traves de una perdida de senal`() {
        val t = TripComputer()
        t.reset(0L)
        t.onFix(lat0, lon0, 5f, 150f, 0L)
        t.signalLost()
        t.onFix(lat0, lon0, 5f, 150f, 1000L)
        assertEquals(0f, t.maxKmh, 0f)
    }

    @Test
    fun `formato del tiempo`() {
        assertEquals("0:00", TripComputer.formatMinutes(0))
        assertEquals("0:07", TripComputer.formatMinutes(7))
        assertEquals("1:05", TripComputer.formatMinutes(65))
        assertEquals("25:00", TripComputer.formatMinutes(1500))
        assertEquals("0:00", TripComputer.formatMinutes(-3))
    }

    @Test
    fun `distancia conocida`() {
        // Un grado de latitud ≈ 111,2 km
        assertEquals(111_195f, TripComputer.distanceMeters(0.0, 0.0, 1.0, 0.0), 50f)
        assertEquals(0f, TripComputer.distanceMeters(lat0, lon0, lat0, lon0), 0f)
    }

    @Test
    fun `puntos cardinales`() {
        assertEquals("N", TripComputer.compassPoint(0f))
        assertEquals("N", TripComputer.compassPoint(359f))
        assertEquals("N", TripComputer.compassPoint(-10f))
        assertEquals("NE", TripComputer.compassPoint(45f))
        assertEquals("E", TripComputer.compassPoint(90f))
        assertEquals("S", TripComputer.compassPoint(180f))
        assertEquals("SO", TripComputer.compassPoint(225f))
        assertEquals("O", TripComputer.compassPoint(270f))
        assertEquals("NO", TripComputer.compassPoint(315f))
        assertEquals("N", TripComputer.compassPoint(720f))
    }
}
