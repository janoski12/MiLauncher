package cl.alejandro.milauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Busca la carátula cuando la app de música no la entrega como imagen (típico del Bluetooth):
 *
 * 1. Si la app entrega un enlace a la imagen (content://, file://, http…), la carga desde ahí.
 * 2. Si no hay nada, la busca en el catálogo de iTunes con el título y el artista
 *    (gratis, sin cuenta). Necesita internet (por ejemplo, el hotspot del teléfono).
 *
 * Las carátulas encontradas se guardan en la radio (caché), así una canción se busca una sola vez.
 * Para no buscar en internet, pon ONLINE_LOOKUP = false.
 */
class ArtFinder(context: Context) {

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val memory = LruCache<String, Bitmap>(12)
    // Canciones buscadas sin éxito y cuándo: se reintenta a los 5 minutos (quizás no había internet).
    private val notFound = HashMap<String, Long>()
    private val pending = HashSet<String>()
    private val diskDir = File(appContext.cacheDir, "caratulas").apply { mkdirs() }

    /** Carátula ya conocida para esta canción (sin esperar), o null. */
    fun cached(key: String): Bitmap? = memory.get(key)

    /**
     * Busca la carátula en segundo plano. [onFound] se llama en el hilo principal solo si se encontró.
     * [uris] son los enlaces que entregó la app de música (pueden estar vacíos).
     */
    fun find(key: String, uris: List<String>, title: String?, artist: String?, onFound: (Bitmap) -> Unit) {
        val failedAt = notFound[key]
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < RETRY_MS) return
        if (memory.get(key) != null || !pending.add(key)) return
        worker.execute {
            val bmp = try {
                fromUris(uris) ?: fromDisk(key) ?: fromItunes(key, title, artist)
            } catch (e: Throwable) {
                null
            }
            main.post {
                pending.remove(key)
                if (bmp != null) {
                    memory.put(key, bmp)
                    onFound(bmp)
                } else {
                    notFound[key] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    // ---------- 1. Enlaces entregados por la app ----------

    private fun fromUris(uris: List<String>): Bitmap? {
        for (u in uris) {
            val bmp = try {
                val uri = Uri.parse(u)
                when (uri.scheme) {
                    "http", "https" -> if (ONLINE_LOOKUP) decode(download(u)) else null
                    else -> appContext.contentResolver.openInputStream(uri)?.use { decode(it.readBytes()) }
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) return bmp
        }
        return null
    }

    // ---------- 2. Caché en la radio ----------

    private fun diskFile(key: String) = File(diskDir, Integer.toHexString(key.hashCode()) + ".jpg")

    private fun fromDisk(key: String): Bitmap? {
        val f = diskFile(key)
        return if (f.exists()) decode(f.readBytes()) else null
    }

    // ---------- 3. Catálogo de iTunes ----------

    private fun fromItunes(key: String, title: String?, artist: String?): Bitmap? {
        if (!ONLINE_LOOKUP) return null
        val cleanTitle = cleanTitle(title ?: return null)
        val mainArtist = firstArtist(artist ?: return null)
        if (cleanTitle.isBlank() || mainArtist.isBlank()) return null   // p. ej. radio FM: no se busca

        val url = searchArtwork("$mainArtist $cleanTitle", mainArtist)
            ?: searchArtwork(cleanTitle, mainArtist)
            ?: return null
        val bytes = download(url) ?: return null
        val bmp = decode(bytes) ?: return null
        trimDisk()
        diskFile(key).writeBytes(bytes)
        return bmp
    }

    /** Devuelve el enlace de la carátula (300x300) del primer resultado cuyo artista coincide. */
    private fun searchArtwork(term: String, mainArtist: String): String? {
        val q = URLEncoder.encode(term, "UTF-8")
        val json = download("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=5&country=CL")
            ?: return null
        val results = JSONObject(String(json, Charsets.UTF_8)).optJSONArray("results") ?: return null
        val wanted = normalize(mainArtist)
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val found = normalize(r.optString("artistName"))
            // El artista tiene que coincidir: mejor sin carátula que con la de otra canción.
            if (found.contains(wanted) || wanted.contains(found)) {
                val art = r.optString("artworkUrl100")
                if (art.isNotBlank()) return art.replace("100x100bb", "300x300bb")
            }
        }
        return null
    }

    /** Quita lo que confunde la búsqueda: "(feat. …)", "[Remastered]", " - Live", "|"… */
    private fun cleanTitle(t: String): String =
        t.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
            .replace(Regex("\\s-\\s.*$"), " ")
            .replace("|", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** "Big One, FMK, Ke Personajes" → "Big One". */
    private fun firstArtist(a: String): String =
        a.split(",", "&", " feat", " ft.", " x ", ";").first().trim()

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("[^a-z0-9]"), "")

    // ---------- Utilidades ----------

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) return null
            conn.inputStream.use { readLimited(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** Lee hasta 2 MB (una carátula pesa mucho menos; evita sorpresas). */
    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > 2 * 1024 * 1024) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Decodifica achicando a ~300 px para cuidar la RAM de la radio. */
    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 300 && bounds.outHeight / (sample * 2) >= 300) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Guarda como máximo 200 carátulas: borra las más antiguas. */
    private fun trimDisk() {
        val files = diskDir.listFiles() ?: return
        if (files.size < 200) return
        files.sortedBy { it.lastModified() }.take(files.size - 150).forEach { it.delete() }
    }

    companion object {
        const val ONLINE_LOOKUP = true
        private const val RETRY_MS = 5 * 60_000L
    }
}
