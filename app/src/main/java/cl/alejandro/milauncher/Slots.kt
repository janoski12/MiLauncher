package cl.alejandro.milauncher

import android.content.ComponentName
import android.content.Context
import java.text.Normalizer

/**
 * Un botón de acceso rápido de la fila inferior.
 * Si el usuario no le asignó una app, se intenta detectar sola:
 * primero por nombre de paquete conocido y después por palabras en el nombre de la app.
 *
 * - keywords: se buscan al COMIENZO de cada palabra del nombre (sin importar mayúsculas
 *   ni tildes). "fm" encuentra "Radio FM" pero no "Platform"; "carlink" encuentra
 *   "Carlinkit". Pueden ser de varias palabras, como "android auto".
 * - exclude: paquetes que nunca se eligen solos aunque su nombre calce (por ejemplo, Last.fm).
 * - showAppName: si es true, el botón muestra el nombre de la app encontrada
 *   (así "Waze" pasa a decir "Maps" si lo que hay es Google Maps).
 */
data class Slot(
    val key: String,
    val label: String,
    val packages: List<String>,
    val keywords: List<String>,
    val exclude: List<String> = emptyList(),
    val showAppName: Boolean = false
)

object Slots {

    // ---- Edita esta lista para cambiar los botones de abajo ----
    val all = listOf(
        Slot(
            "radio", "Radio FM",
            listOf("com.android.fmradio", "com.syu.radio", "com.microntek.radio", "com.ts.radio"),
            listOf("radio", "fm"),
            exclude = listOf("fm.last.android", "com.spotify.music")
        ),
        Slot(
            "carplay", "CarPlay / AA",
            emptyList(),
            listOf("zlink", "carlink", "carplay", "autokit", "android auto", "tlink", "easyconnect", "phonelink")
        ),
        Slot(
            "nav", "Waze",
            listOf("com.waze", "com.google.android.apps.maps"),
            listOf("waze", "maps", "mapas", "navi", "navegador"),
            showAppName = true
        ),
        Slot(
            "music", "Música",
            listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
            listOf("spotify", "music", "musica")
        ),
        Slot(
            "phone", "Teléfono",
            emptyList(),
            listOf("bluetooth", "telefono", "phone", "dialer")
        ),
        Slot(
            "settings", "Ajustes",
            listOf("com.android.settings"),
            listOf("ajustes", "settings", "configuracion")
        )
    )

    private const val PREFS = "slots"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App elegida a mano por el usuario, o null si está en automático. */
    fun manual(context: Context, slot: Slot): ComponentName? =
        prefs(context).getString("slot_${slot.key}", null)?.let { ComponentName.unflattenFromString(it) }

    fun setManual(context: Context, slot: Slot, component: ComponentName?) {
        prefs(context).edit().apply {
            if (component == null) remove("slot_${slot.key}")
            else putString("slot_${slot.key}", component.flattenToString())
        }.apply()
    }

    /** Texto bajo el botón: el nombre de la app si se eligió a mano (o si el botón lo pide), si no, el del botón. */
    fun labelFor(context: Context, slot: Slot, entry: AppEntry): String =
        if (slot.showAppName || manual(context, slot) != null) entry.label else slot.label

    /** Resuelve qué app abre el botón (manual si existe y sigue instalada, si no, detección automática). */
    fun resolve(context: Context, slot: Slot, apps: List<AppEntry>): AppEntry? {
        manual(context, slot)?.let { cn ->
            apps.firstOrNull { it.component == cn }?.let { return it }
        }
        for (pkg in slot.packages) {
            apps.firstOrNull { it.component.packageName == pkg }?.let { return it }
        }

        val candidates = apps.filter { it.component.packageName !in slot.exclude }
            .map { it to words(it.label) }
        // Primero las apps cuyo nombre EMPIEZA con la palabra ("Radio FM"),
        // después las que la tienen en cualquier parte ("Mi Radio").
        for (word in slot.keywords) {
            val kw = words(word)
            candidates.firstOrNull { (_, w) -> startsWith(w, kw, 0) }?.let { return it.first }
        }
        for (word in slot.keywords) {
            val kw = words(word)
            candidates.firstOrNull { (_, w) -> containsWords(w, kw) }?.let { return it.first }
        }
        return null
    }

    /** "Música  FM-Pro" -> [musica, fm, pro] (minúsculas, sin tildes, separado en palabras). */
    private fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }

    /** ¿Las palabras desde la posición "at" comienzan con las de la palabra clave? */
    private fun startsWith(words: List<String>, kw: List<String>, at: Int): Boolean =
        kw.isNotEmpty() && at + kw.size <= words.size && kw.indices.all { words[at + it].startsWith(kw[it]) }

    private fun containsWords(words: List<String>, kw: List<String>): Boolean =
        words.indices.any { startsWith(words, kw, it) }
}
