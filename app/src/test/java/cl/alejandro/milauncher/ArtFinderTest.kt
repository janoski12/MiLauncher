package cl.alejandro.milauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtFinderTest {

    @Test
    fun `limpia el titulo`() {
        assertEquals("Tusa", ArtFinder.cleanTitle("Tusa (feat. Nicki Minaj)"))
        assertEquals("Yellow", ArtFinder.cleanTitle("Yellow [Remastered 2021]"))
        assertEquals("Lamento Boliviano", ArtFinder.cleanTitle("Lamento Boliviano - En Vivo"))
        assertEquals("Hola Chao", ArtFinder.cleanTitle("Hola | Chao"))
    }

    @Test
    fun `primer artista`() {
        assertEquals("Big One", ArtFinder.firstArtist("Big One, FMK, Ke Personajes"))
        assertEquals("Shakira", ArtFinder.firstArtist("Shakira & Bizarrap"))
        assertEquals("Karol G", ArtFinder.firstArtist("Karol G feat. Nicki Minaj"))
        assertEquals("Los Prisioneros", ArtFinder.firstArtist("Los Prisioneros"))
    }

    @Test
    fun `normaliza tildes y simbolos`() {
        assertEquals("beyonce", ArtFinder.normalize("Beyoncé"))
        assertEquals("losjaivas", ArtFinder.normalize("Los Jaivas"))
    }

    @Test
    fun `el artista tiene que coincidir`() {
        assertTrue(ArtFinder.sameArtist("Los Bunkers", "Los Bunkers"))
        assertTrue(ArtFinder.sameArtist("Bad Bunny", "Bad Bunny & Drake"))
        assertFalse(ArtFinder.sameArtist("Mon Laferte", "Myriam Hernández"))
    }

    @Test
    fun `nombres sin letras latinas no calzan con cualquier cosa`() {
        // "방탄소년단" queda vacío al normalizar: antes calzaba con cualquier resultado.
        assertFalse(ArtFinder.sameArtist("방탄소년단", "Myriam Hernández"))
        assertFalse(ArtFinder.sameArtist("Myriam Hernández", "米津玄師"))
    }
}
