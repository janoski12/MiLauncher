package cl.alejandro.milauncher

import android.content.ComponentName
import android.content.Context

/**
 * Un botón de acceso rápido de la fila inferior.
 * Si el usuario no le asignó una app, se intenta detectar sola:
 * primero por nombre de paquete conocido y después por palabras en el nombre de la app.
 */
data class Slot(
    val key: String,
    val label: String,
    val packages: List<String>,
    val keywords: List<String>
)

object Slots {

    // ---- Edita esta lista para cambiar los botones de abajo ----
    val all = listOf(
        Slot(
            "radio", "Radio FM",
            listOf("com.android.fmradio", "com.syu.radio", "com.microntek.radio", "com.ts.radio"),
            listOf("radio", "fm")
        ),
        Slot(
            "carplay", "CarPlay / AA",
            emptyList(),
            listOf("zlink", "carlink", "carplay", "autokit", "android auto", "tlink", "easyconnect", "phonelink")
        ),
        Slot(
            "nav", "Waze",
            listOf("com.waze", "com.google.android.apps.maps"),
            listOf("waze", "maps", "mapas", "navi")
        ),
        Slot(
            "music", "Música",
            listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
            listOf("spotify", "music", "música", "musica")
        ),
        Slot(
            "phone", "Teléfono",
            emptyList(),
            listOf("bluetooth", "teléfono", "telefono", "phone", "dialer")
        ),
        Slot(
            "settings", "Ajustes",
            listOf("com.android.settings"),
            listOf("ajustes", "settings", "configuración", "configuracion")
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

    /** Resuelve qué app abre el botón (manual si existe y sigue instalada, si no, detección automática). */
    fun resolve(context: Context, slot: Slot, apps: List<AppEntry>): AppEntry? {
        manual(context, slot)?.let { cn ->
            apps.firstOrNull { it.component == cn }?.let { return it }
        }
        for (pkg in slot.packages) {
            apps.firstOrNull { it.component.packageName == pkg }?.let { return it }
        }
        for (word in slot.keywords) {
            apps.firstOrNull { it.label.lowercase().contains(word) }?.let { return it }
        }
        return null
    }
}
